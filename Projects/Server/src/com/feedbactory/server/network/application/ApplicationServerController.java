/* Memos:
 * - I'm unsure about the best value for the server socket backlog. Most underlying implementation will apparently enforce a sensible value in any case. But what values
 *   would be too small? Need to revisit this after some extensive load testing. It probably needs to be tuned alongside the busy and maximum connection
 *   thresholds.
 *
 * - The bunch of volatile variables are tied to the network server component, which may be shutdown and renewed. They're volatile rather than synchronized on because
 *   they don't need to be updated atomically as one unit. However they do need to be visible to callbacks on the IO threads. Tagging the thread pool as volatile is
 *   maybe being a bit pedantic since I know that it will only be changed and accessed by the one thread, but since it's openly accessible to the network server at
 *   any time in theory it's probably sound practice to also make it volatile.
 *
 * - Related to the above point, the public accessor methods are not threadsafe, the caller must ensure the thread safety by locking or single threading.
 *
 * - Lazily creating an initial instance of the network server is something that I deliberated over for some time, finally deciding on it because I like
 *   the idea of no thread overhead on startup. To outside clients it provides no additional complexity, since they really only care whether the server is inactive
 *   or active - the public isServerStarted() method can be used for this, and it sits well logically alongside the startServer() and shutdownServer(). The other
 *   server state methods are private, with the exception of getServerState(), which is used for reporting.
 */

package com.feedbactory.server.network.application;


import com.feedbactory.server.core.FeedbactoryServerConstants;
import com.feedbactory.server.core.TimeCache;
import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.log.SystemLogLevel;
import com.feedbactory.server.network.application.NetworkServiceManager.ApplicationRequestManagerInterface;
import com.feedbactory.server.network.component.AsynchronousNetworkServer;
import static com.feedbactory.server.network.component.AsynchronousNetworkServer.ServerState.Bound;
import static com.feedbactory.server.network.component.AsynchronousNetworkServer.ServerState.Initialised;
import static com.feedbactory.server.network.component.AsynchronousNetworkServer.ServerState.Shutdown;
import static com.feedbactory.server.network.component.AsynchronousNetworkServer.ServerState.Terminated;
import com.feedbactory.server.network.component.NetworkServerController;
import com.feedbactory.shared.FeedbactoryConstants;
import java.io.IOException;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


final public class ApplicationServerController
{
   static final public int MinimumConnectionBacklogSize = 4;
   static final public int MinimumReceiveBufferSize = 512;

   static final private int DefaultConnectionBacklogSize = 1024;
   static final private int DefaultReceiveBufferSize = 8192;

   static final private int DefaultActiveConnectionsBusyThreshold = 5000;
   static final private int DefaultMaximumConnectionsPermitted = 7000;

   final private NetworkServiceManager networkServiceManager;

   final private ServerControllerDelegate serverControllerDelegate = new ServerControllerDelegate();

   volatile private AsynchronousNetworkServer networkServer;
   volatile private ThreadPoolExecutor ioHandlerThreadPool;
   volatile private int connectionBacklogSize = DefaultConnectionBacklogSize;
   volatile private int receiveBufferSize = DefaultReceiveBufferSize;
   volatile private int activeConnectionsBusyThreshold = DefaultActiveConnectionsBusyThreshold;
   volatile private int maximumConnectionsPermitted = DefaultMaximumConnectionsPermitted;

   private long startTime = FeedbactoryConstants.NoTime;
   private long shutdownTime = FeedbactoryConstants.NoTime;

   final private AtomicInteger activeConnections = new AtomicInteger();
   final private AtomicInteger highestRecordedConnections = new AtomicInteger();
   volatile private long highestRecordedConnectionsTime = FeedbactoryConstants.NoTime;

   final private ApplicationRequestManagerInterface applicationRequestManager;


   ApplicationServerController(final NetworkServiceManager networkServiceManager,
                               final ApplicationRequestManagerInterface applicationRequestManager) throws IOException
   {
      this.networkServiceManager = networkServiceManager;
      this.applicationRequestManager = applicationRequestManager;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static public enum ServerState
   {
      NotCreated,
      Initialised,
      Started,
      Shutdown;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class ServerMetrics
   {
      final public int connectionBacklogSize;
      final public int receiveBufferSize;
      final public int connectionsBusyThreshold;
      final public int maximumConnectionsPermitted;

      final public int activeConnections;
      final public int highestRecordedConnections;
      final public long highestRecordConnectionsTime;

      final public int ioThreadPoolQueueSize;
      final public int ioThreadPoolCoreThreadCount;
      final public int ioThreadPoolMaximumThreadCount;
      final public int ioThreadPoolHotThreadCount;
      final public int ioThreadPoolActiveTaskCount;
      final public long ioThreadPoolTasksSubmitted;
      final public long ioThreadPoolTasksCompleted;


      private ServerMetrics(final int connectionBacklogSize, final int serverReceiveBufferSize,
                            final int activeConnectionsBusyThreshold, final int maximumConnectionsPermitted,
                            final int activeConnections, final int highestRecordedConnections, final long highestRecordConnectionsTime,
                            final int ioThreadPoolQueueSize, final int ioThreadPoolCoreThreadCount,
                            final int ioThreadPoolMaximumThreadCount, final int ioThreadPoolHotThreadCount, final int ioThreadPoolActiveTaskCount,
                            final long ioThreadPoolTasksSubmitted, final long ioThreadPoolTasksCompleted)
      {
         this.connectionBacklogSize = connectionBacklogSize;
         this.receiveBufferSize = serverReceiveBufferSize;
         this.connectionsBusyThreshold = activeConnectionsBusyThreshold;
         this.maximumConnectionsPermitted = maximumConnectionsPermitted;

         this.activeConnections = activeConnections;
         this.highestRecordedConnections = highestRecordedConnections;
         this.highestRecordConnectionsTime = highestRecordConnectionsTime;

         this.ioThreadPoolQueueSize = ioThreadPoolQueueSize;
         this.ioThreadPoolCoreThreadCount = ioThreadPoolCoreThreadCount;
         this.ioThreadPoolMaximumThreadCount = ioThreadPoolMaximumThreadCount;
         this.ioThreadPoolHotThreadCount = ioThreadPoolHotThreadCount;
         this.ioThreadPoolActiveTaskCount = ioThreadPoolActiveTaskCount;
         this.ioThreadPoolTasksSubmitted = ioThreadPoolTasksSubmitted;
         this.ioThreadPoolTasksCompleted = ioThreadPoolTasksCompleted;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class ServerControllerDelegate implements NetworkServerController
   {
      @Override
      final public ExecutorService getChannelGroupThreadPool()
      {
         return ioHandlerThreadPool;
      }


      @Override
      final public int getReceiveBufferSize()
      {
         return receiveBufferSize;
      }


      @Override
      final public int getBacklogSize()
      {
         return connectionBacklogSize;
      }


      @Override
      final public boolean canAcceptNewConnection()
      {
         return (activeConnections.get() < maximumConnectionsPermitted);
      }


      @Override
      final public void incrementActiveConnections()
      {
         checkUpdateHighestRecordedConnections(activeConnections.incrementAndGet());
      }


      private void checkUpdateHighestRecordedConnections(final int connectionCount)
      {
         int currentHighestRecordedConnections;

         for (;;)
         {
            currentHighestRecordedConnections = highestRecordedConnections.get();

            if (connectionCount <= currentHighestRecordedConnections)
               return;
            else if (highestRecordedConnections.compareAndSet(currentHighestRecordedConnections, connectionCount))
            {
               /* There's no locking so it's possible that the highestRecordedConnectionsTime is written by multiple threads
                * concurrently but it's of no concern since a) the approximate timestamp of peak traffic is the goal, and
                * b) the TimeCache'd times will almost certainly be identical anyway.
                */
               highestRecordedConnectionsTime = TimeCache.getCurrentTimeMilliseconds();
               return;
            }
         }
      }


      @Override
      final public void newConnectionAccepted(final AsynchronousSocketChannel clientChannel)
      {
         applicationRequestManager.newConnectionAccepted(clientChannel);
      }


      @Override
      final public void reportNetworkServerEvent(final SystemLogLevel logLevel, final String eventMessage)
      {
         FeedbactoryLogger.logSystemEvent(logLevel, AsynchronousNetworkServer.class, eventMessage);
      }


      @Override
      final public void reportNetworkServerException(final Throwable throwable)
      {
         FeedbactoryLogger.logSystemEvent(SystemLogLevel.ApplicationError, AsynchronousNetworkServer.class, "Asynchronous server exception", throwable);
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private boolean isServerCreated()
   {
      return (networkServer != null);
   }


   private boolean isServerShutdown()
   {
      return (isServerCreated() && networkServer.isShutdown());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleClientConnectionFinished(final AsynchronousSocketChannel channel)
   {
      closeChannel(channel);

      activeConnections.decrementAndGet();
      networkServer.renewAccept();
   }


   private void closeChannel(final AsynchronousSocketChannel channel)
   {
      try
      {
         channel.close();
      }
      catch (final IOException ioException)
      {
         FeedbactoryLogger.logSystemEvent(SystemLogLevel.ClientStateError, getClass(), "Error closing client channel", ioException);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSetConnectionBacklogSize(final int connectionBacklogSize)
   {
      if (connectionBacklogSize < MinimumConnectionBacklogSize)
         throw new IllegalArgumentException("Connection backlog must be greater than " + MinimumConnectionBacklogSize + " connections.");
      else if (isServerStarted())
         throw new IllegalStateException("Cannot update the connection backlog size while the server is active.");

      this.connectionBacklogSize = connectionBacklogSize;
   }


   private void handleSetReceiveBufferSize(final int receiveBufferSize)
   {
      if (receiveBufferSize < MinimumReceiveBufferSize)
         throw new IllegalArgumentException("Receive buffer size must be greater than " + MinimumReceiveBufferSize + " bytes.");
      else if (isServerStarted())
         throw new IllegalStateException("Cannot update the receive buffer size while the server is active.");

      this.receiveBufferSize = receiveBufferSize;
   }


   private void handleSetActiveConnectionLimits(final int activeConnectionsBusyThreshold, final int maximumConnectionsPermitted)
   {
      if (activeConnectionsBusyThreshold < 0)
         throw new IllegalArgumentException("Busy threshold cannot be less than zero.");
      else if (maximumConnectionsPermitted < 0)
         throw new IllegalArgumentException("Maximum connections permitted cannot be less than zero.");
      else if (maximumConnectionsPermitted < activeConnectionsBusyThreshold)
         throw new IllegalArgumentException("Maximum connections permitted cannot be less than the busy threshold.");

      this.activeConnectionsBusyThreshold = activeConnectionsBusyThreshold;
      this.maximumConnectionsPermitted = maximumConnectionsPermitted;

      if (isServerStarted())
         networkServer.renewAccept();
   }


   private void handleStartServer(final int portNumber) throws IOException
   {
      if ((portNumber < 0) || (portNumber > 65535))
         throw new IllegalArgumentException("Invalid server port number: " + portNumber);
      else if (isServerStarted())
         throw new IllegalStateException("Server is already running.");
      else
      {
         // Re-initialise the server if required. If parameters have been changed, they will now take effect.
         if ((! isServerCreated()) || isServerShutdown())
            createNetworkServer();

         networkServer.start(portNumber);

         startTime = TimeCache.getCurrentTimeMilliseconds();
      }
   }


   private void createNetworkServer() throws IOException
   {
      createIOHandlerThreadPool();
      networkServer = new AsynchronousNetworkServer(serverControllerDelegate);
   }


   private void createIOHandlerThreadPool()
   {
      final ThreadFactory threadFactory = new ThreadFactory()
      {
         @Override
         final public Thread newThread(final Runnable runnable)
         {
            return new Thread(runnable, "Server request handler thread");
         }
      };

      final RejectedExecutionHandler rejectedExecutionHandler = new RejectedExecutionHandler()
      {
         @Override
         final public void rejectedExecution(final Runnable runnable, final ThreadPoolExecutor executor)
         {
            handleIOThreadPoolRejectedExecutionException(runnable);
         }
      };

      ioHandlerThreadPool = new ThreadPoolExecutor(FeedbactoryServerConstants.ServerConcurrency, FeedbactoryServerConstants.ServerConcurrency, 10, TimeUnit.SECONDS,
                                                   new LinkedBlockingQueue<>(), threadFactory, rejectedExecutionHandler);

      ioHandlerThreadPool.allowCoreThreadTimeOut(true);
   }


   private void handleIOThreadPoolRejectedExecutionException(final Runnable runnable)
   {
      /* Depending on the timing of the shutdown, the NetworkServer may cause a RejectedExecutionException when an accept() is pending and the channel group
       * is shut down. This seems to vary depending on whether or not at least one connection has been accepted during the session, as well as the timing of the
       * thread making the shutdownAcceptor() and shutdown() calls; if there is enough of a lag between them, the shutdown appears to occur more cleanly.
       * In any case there needs to be a check on the state of the server before attempting to renew any accept connections.
       */
      if (! networkServer.isShutdown())
         FeedbactoryLogger.logSystemEvent(SystemLogLevel.ApplicationError, getClass(), "IO task rejected, from runnable class: " + runnable.getClass().getName());
   }


   private void handleShutdownServer(final long shutdownTimeoutMilliseconds) throws IOException, InterruptedException
   {
      if (isServerCreated() && (! isServerShutdown()))
      {
         // Attempt to gracefully shutdown the server, blocking new incoming connections and allowing time for existing requests to be processed.
         networkServer.shutdownAcceptor();

         networkServer.shutdown(shutdownTimeoutMilliseconds);

         /* The shutdown call will attempt to gracefully shutdown the server connection, waiting for all
          * active IO worker thread tasks to complete. This will mean that there will be no more callbacks
          * from end-of-request handlers to clientConnectionFinished() by the time that the method returns.
          * So it will be safe to reset the count of active connections to zero.
          */
         activeConnections.set(0);

         shutdownTime = TimeCache.getCurrentTimeMilliseconds();
      }
   }


   private ServerState handleGetServerState()
   {
      if (isServerCreated())
      {
         final AsynchronousNetworkServer.ServerState rawServerState = networkServer.getState();

         switch (rawServerState)
         {
            case Initialised:
               return ServerState.Initialised;
            case Bound:
               return ServerState.Started;
            case Shutdown:
            case Terminated:
               return ServerState.Shutdown;

            default:
               throw new AssertionError("Unhandled server state: " + rawServerState);
         }
      }
      else
         return ServerState.NotCreated;
   }


   private ServerMetrics handleGetServerMetrics()
   {
      if (isServerCreated())
      {
         // Ensure that the reported activeConnections can't be higher than highestRecordedConnections.
         final int connectionsActive = this.activeConnections.get();
         final int mostConnections = Math.max(highestRecordedConnections.get(), connectionsActive);

         return new ServerMetrics(connectionBacklogSize, receiveBufferSize,
                                  activeConnectionsBusyThreshold, maximumConnectionsPermitted,
                                  connectionsActive, mostConnections, highestRecordedConnectionsTime,
                                  ioHandlerThreadPool.getQueue().size(), ioHandlerThreadPool.getCorePoolSize(),
                                  ioHandlerThreadPool.getMaximumPoolSize(), ioHandlerThreadPool.getPoolSize(), ioHandlerThreadPool.getActiveCount(),
                                  ioHandlerThreadPool.getTaskCount(), ioHandlerThreadPool.getCompletedTaskCount());
      }
      else
      {
         return new ServerMetrics(connectionBacklogSize, receiveBufferSize,
                                  activeConnectionsBusyThreshold, maximumConnectionsPermitted,
                                  0, highestRecordedConnections.get(), highestRecordedConnectionsTime,
                                  0, 0, 0, 0, 0, 0, 0);
      }
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final boolean isBusy()
   {
      return (activeConnections.get() >= activeConnectionsBusyThreshold);
   }


   final void clientConnectionFinished(final AsynchronousSocketChannel channel)
   {
      handleClientConnectionFinished(channel);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public int getConnectionBacklogSize()
   {
      return connectionBacklogSize;
   }


   final public void setConnectionBacklogSize(final int connectionBacklogSize)
   {
      handleSetConnectionBacklogSize(connectionBacklogSize);
   }


   final public int getReceiveBufferSize()
   {
      return receiveBufferSize;
   }


   final public void setReceiveBufferSize(final int receiveBufferSize)
   {
      handleSetReceiveBufferSize(receiveBufferSize);
   }


   final public int getActiveConnectionsBusyThreshold()
   {
      return activeConnectionsBusyThreshold;
   }


   final public int getMaximumConnectionsPermitted()
   {
      return maximumConnectionsPermitted;
   }


   final public void setActiveConnectionLimits(final int activeConnectionsBusyThreshold, final int maximumConnectionsPermitted)
   {
      handleSetActiveConnectionLimits(activeConnectionsBusyThreshold, maximumConnectionsPermitted);
   }


   final public boolean isServerStarted()
   {
      return (isServerCreated() && networkServer.isBound());
   }


   final public void startServer(final int portNumber) throws IOException
   {
      handleStartServer(portNumber);
   }


   final public void shutdownServer(final long shutdownTimeoutMilliseconds) throws IOException, InterruptedException
   {
      handleShutdownServer(shutdownTimeoutMilliseconds);
   }


   final public ServerState getServerState()
   {
      return handleGetServerState();
   }


   final public long getServerStartTime()
   {
      return startTime;
   }


   final public long getServerShutdownTime()
   {
      return shutdownTime;
   }


   final public ServerMetrics getMetrics()
   {
      return handleGetServerMetrics();
   }
}