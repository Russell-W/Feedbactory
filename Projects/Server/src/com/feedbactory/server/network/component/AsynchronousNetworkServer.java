/* Memos:
 * - The methods start(), shutdownAcceptor() and shutdown() are not threadsafe and their access must be coordinated by the caller(s), either using the same thread or
 *   synchronized if called via multiple threads.
 *
 * - I can't find on any of the built-in async classes a method that would notify whether or not a channel is bound, so I'm using a basic enum to represent the
 *   possible states of the server. Callers may for example use isBound() to detect whether or not the server has already been started.
 *
 *   - This checkable state variable is also useful in that it can potentially prevent a continuous accept fail-renew-fail cycle during regular shutdown,
 *     for more details check the handleShutdownAcceptor() method.
 *
 * - Connection accepts are performed sequentially, with each accept resulting in a call to either the CompletionHandler's completed or failed method.
 *   Those are the primary points from which the channel renews the acceptance of incoming connections. The other possibilities are:
 *
 * a) The server starts and begins accepting connections for the first time.
 * b) The number of concurrent connections reduces below the maximum allowed, eg. after a client has disconnected, hence the channel may again be
 *    available to accept new connections.
 * c) The number of allowable connections increases at a time at which the server was previously not already accepting connections.
 *
 * - As stipulated in the Java docs for AsynchronousChannelGroup.withThreadPool(), the thread pool provided by the server controller must either support direct handoff
 *   or unbounded queuing of submitted tasks. In other words, a RejectecExecutionException should never be a possibility in practise, which is a very good thing because
 *   there's no way for either server controller or this class to know whether that rejected task was for an accept or a read or write. This would potentially throw out
 *   the usefulness of the isAccepting flag, since in the event of a RejectecExecutionException being thrown during an attempted accept(), the completion handler will
 *   not be called and the flag won't be reset; yet the server will still be flagged as being in the accept state, without actually having an accept pending.
 */

package com.feedbactory.server.network.component;


import com.feedbactory.server.core.log.SystemLogLevel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ShutdownChannelGroupException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


final public class AsynchronousNetworkServer
{
   final private NetworkServerController serverController;

   final private AsynchronousChannelGroup serverChannelGroup;

   final private AsynchronousServerSocketChannel serverSocketChannel;

   final private ServerSocketAcceptHandler serverSocketAcceptHandler = new ServerSocketAcceptHandler();

   final private AtomicBoolean isAccepting = new AtomicBoolean();

   volatile private ServerState serverState;


   public AsynchronousNetworkServer(final NetworkServerController serverController) throws IOException
   {
      final int receiveBufferSize = serverController.getReceiveBufferSize();
      validate(receiveBufferSize);

      this.serverController = serverController;

      serverChannelGroup = initialiseServerChannelGroup();

      serverSocketChannel = initialiseServerSocketChannel(receiveBufferSize);

      serverState = ServerState.Initialised;
   }


   private void validate(final int receiveBufferSize)
   {
      if (receiveBufferSize < 1)
         throw new IllegalStateException("Receive buffer size cannot be less than 1 byte.");
   }


   private AsynchronousChannelGroup initialiseServerChannelGroup() throws IOException
   {
      return AsynchronousChannelGroup.withThreadPool(serverController.getChannelGroupThreadPool());
   }


   private AsynchronousServerSocketChannel initialiseServerSocketChannel(final int receiveBufferSize) throws IOException
   {
      final AsynchronousServerSocketChannel serverSocketChannelBuilder = AsynchronousServerSocketChannel.open(serverChannelGroup);
      serverSocketChannelBuilder.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);

      return serverSocketChannelBuilder;
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static public enum ServerState
   {
      Initialised,
      Bound,
      Shutdown,
      Terminated;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class ServerSocketAcceptHandler implements CompletionHandler<AsynchronousSocketChannel, Void>
   {
      @Override
      final public void completed(final AsynchronousSocketChannel channel, final Void attachment)
      {
         isAccepting.set(false);

         serverController.incrementActiveConnections();

         // Kick off the async accept() renewal before allowing the server controller to process the new connection.
         renewAccept();

         serverController.newConnectionAccepted(channel);
      }


      @Override
      final public void failed(final Throwable throwable, final Void attachment)
      {
         isAccepting.set(false);

         renewAccept();

         /* Don't process the accept() failure when a server shutdown is underway - the failure will more than likely have occurred as a result of the
          * close() on the server channel while an accept is pending.
          */
         if (isBound())
            serverController.reportNetworkServerException(throwable);
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private void handleStartServer(final int portNumber) throws IOException
   {
      if (! isInitialised())
         throw new IllegalStateException("The server may only be started from an initialised state.");
      else if (portNumber < 1024)
         throw new IllegalArgumentException("Port number must be greater than 1023.");

      serverSocketChannel.bind(new InetSocketAddress(portNumber), serverController.getBacklogSize());

      serverState = ServerState.Bound;

      renewAccept();
   }


   private void handleRenewAccept()
   {
      /* Connection accepts must be done one at a time, hence the use of the isAccepting AtomicBoolean. At the same time, I don't want to enforce that policy by
       * using blocking (eg. synchronized blocks); if a thread is already accepting, subsequent threads should be free to check the state and then go back to the ready pool.
       * Note that the compareAndSet() on the atomic flag is the last check performed.
       */
      if (isBound() && serverController.canAcceptNewConnection() && isAccepting.compareAndSet(false, true))
      {
         try
         {
            serverSocketChannel.accept(null, serverSocketAcceptHandler);
         }
         catch (final ShutdownChannelGroupException shutdownChannelGroupException)
         {
            serverController.reportNetworkServerException(shutdownChannelGroupException);
         }
      }
   }


   private void handleShutdownAcceptor() throws IOException
   {
      if (isInitialised() || isBound())
      {
         /* Set the server state flag before closing the channel to prevent the possibility of a continuous (or very lengthy) fail-renew-fail loop if there is some problem
          * with closing the channel. I've once seen this happen on JDK8.0 x64 on Windows, when a previous server version had not updated the shutdown flag until after the
          * channel had been closed. I'm not sure what the error was, if any - there was no mention of the serverSocketChannel.close() within the huge output stack trace.
          * It's possible that there was no error during the close operation, but rather there were one or more incoming requests between the time that
          * the channel had closed and the server state variable had been updated. At the time I'd been running the server in development and testing with only one
          * client, however I had left the server port open on Windows firewall...
          *
          * Note that during regular shutdown there's still the possibility of a renewAccept thread reaching the .accept() method while isBound() etc is still true,
          * only to fail if the shutdown thread executes this method immediately before the renewAccept executes the .accept() method. In this instance the failure is
          * guaranteed to only happen once (since a return call to renewAccept() will fail on the check to isBound()), and even then no genuine error will be
          * logged as the accept failure method also checks isBound() before logging.
          */
         serverState = ServerState.Shutdown;

         /* Existing client connections should still function (and complete) after the server socket has been closed.
          * New connections will however be denied.
          */
         serverSocketChannel.close();
      }
   }


   private void handleShutdown(final long shutdownTimeoutMilliseconds) throws IOException, InterruptedException
   {
      /* It's worth noting how to piece together a clean shutdown of the async channel framework using AsynchronousServerSocketChannel.close(),
       * and either AsynchronousChannelGroup.shutdown() or shutdownNow().
       *
       * close() on the server socket channel will cause any existing accept() to bail out to the completion handler's failed() method with AsynchronousCloseException.
       * Existing client connections can still function (and complete) after the shutdown of the server socket, however new connections will be denied
       * at the client end; after the existing accept() has bailed out with its AsynchronousCloseException I don't have to worry about handling any more
       * completions coming through. Note that after closure, the server socket won't go back to the accept() state because of the isBound() check.
       *
       * Meanwhile existing connection requests are still active, and in fact may still be in the process of reading, processing the request, or writing the response.
       * Calling shutdown() or shutdownNow() on the channel group followed by awaitTermination() ensures that all of the request work has completed. Actually
       * there's an important caveat to ensure that that holds true: the IO threads must also be used to process the requests and invoke the calls to write
       * the responses, rather than hand off the tasks to a separate worker pool. After some investigation with AsynchronousChannelGroup it seems that even
       * if shutdown on the group is invoked, the group (and likewise its IO pool) will not be terminated until there are no active IO threads. But what happens
       * if an asynchronous read or a write request is invoked after shutdown() has been called? The task will actually be permitted without throwing a
       * ShutdownChannelGroupException. The docs for AsynchronousSocketChannel.write() state that a ShutdownChannelGroupException will be thrown
       * "if the channel group has terminated." Terminated and not merely shutdown being the key. And as long as the read or write has been invoked by one of the
       * IO threads, the channel group's thread pool will of course not be terminated, only marked as shutdown. Essentially it seems that the termination of the
       * channel group can be postponed indefinitely so long as one of the IO threads is still busy, including making repeated calls to a channel's read() or write().
       * I've verified that this holds true even when stacking up multiple writes for the one connection, eg. for a large response that must be written in successive
       * chunks. Refer to AsynchronousServerTest in the scratchpad package.
       *
       * So on the one hand this seems dangerous since if the caller supplies an infinite timeout on the shutdown, it really could hang forever. It puts the onus
       * on the application to ensure that requests are never blocked or performing heavy work for too long. On the other hand it provides a convenient way
       * to gracefully shutdown the server using the channel group's awaitTermination(); once that method returns it's certain that all requests have been
       * served AND responses written.
       *
       * A separate but related note regarding shutdown() and shutdownNow(): on Windows at least it seems that calling either shutdown() or shutdownNow() may not
       * treat pending client read & writes so gracefully, instead bombing out to the IO thread pool's RejectedExecutionException handler. In fact the same thing
       * may happen to pending accept()'s, particularly if no connection has yet been accepted during the session. Refer to:
       * http://stackoverflow.com/questions/14073554/correct-behavior-from-nio-2-asynchronousserversocketchannel-accept-on-windows
       * I can confirm that the accept() issue occurs on Windows, hence the check of the isShutdown() on this class required by the server controller's
       * rejected execution handler. One feasible hack workaround is to ensure a sufficient delay between closing the server channel, and invoking shutdown()
       * on the channel group.
       *
       * shutdownNow() performs the same operations as shutdown() except that it will try to interrupt any active IO, forcing an early completion.
       */

      if (! isShutdown())
         shutdownAcceptor();

      if (! isTerminated())
      {
         serverChannelGroup.shutdown();

         if (! serverChannelGroup.awaitTermination(shutdownTimeoutMilliseconds, TimeUnit.MILLISECONDS))
         {
            serverController.reportNetworkServerEvent(SystemLogLevel.Warning, "Asynchronous server channel group was not terminated within the timeout period.");
            serverChannelGroup.shutdownNow();
            if (! serverChannelGroup.awaitTermination(shutdownTimeoutMilliseconds, TimeUnit.MILLISECONDS))
               serverController.reportNetworkServerEvent(SystemLogLevel.ApplicationError, "Could not terminate asynchronous server channel group.");
         }

         serverState = ServerState.Terminated;
      }
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public ServerState getState()
   {
      return serverState;
   }


   final public boolean isInitialised()
   {
      return (serverState == ServerState.Initialised);
   }


   final public boolean isBound()
   {
      return (serverState == ServerState.Bound);
   }


   final public boolean isShutdown()
   {
      return (serverState == ServerState.Shutdown) || isTerminated();
   }


   final public boolean isTerminated()
   {
      return (serverState == ServerState.Terminated);
   }


   final public void start(final int portNumber) throws IOException
   {
      handleStartServer(portNumber);
   }


   final public void renewAccept()
   {
      handleRenewAccept();
   }


   final public void shutdownAcceptor() throws IOException
   {
      handleShutdownAcceptor();
   }


   final public void shutdown(final long shutdownTimeoutMilliseconds) throws IOException, InterruptedException
   {
      handleShutdown(shutdownTimeoutMilliseconds);
   }
}