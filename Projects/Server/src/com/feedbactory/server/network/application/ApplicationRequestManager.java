/* Memos:
 * - I originally had in place a request worker thread pool, separate to the server's IO thread pool, for the handoff of requests. I'm still not sure that this isn't
 *   the right way to go but for now I'd like to see how the app performs when using the IO threads directly to carry out the requests. There are no heavyweight
 *   requests, no disk IO, minimal locking; I think it may be beneficial to not have the extra expense of the thread context switch from IO to worker threads. If testing
 *   proves otherwise, it will be easy enough to push the initial request onto a worker thread pool, and then do the server controller callback after the work is finished.
 *   The callback is in place now as it is because async IO is being used.
 */

package com.feedbactory.server.network.application;


import com.feedbactory.server.FeedbactoryServer;
import com.feedbactory.server.core.FeedbactorySecurityException;
import com.feedbactory.server.core.TimeCache;
import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.log.SystemEvent;
import com.feedbactory.server.core.log.SystemLogLevel;
import com.feedbactory.server.network.application.NetworkServiceManager.ServerControllerInterface;
import com.feedbactory.server.network.component.ClientIO;
import com.feedbactory.server.network.component.ClientNetworkID;
import com.feedbactory.server.network.component.ClientRequestReader;
import com.feedbactory.server.network.component.ClientRequestReaderConsumer;
import com.feedbactory.server.network.component.ClientResponseWriter;
import com.feedbactory.server.network.component.ClientResponseWriterConsumer;
import com.feedbactory.server.network.component.IPAddressRequestMonitor;
import com.feedbactory.server.network.component.buffer.ByteBufferPool;
import com.feedbactory.server.network.component.buffer.DiscardByteBuffer;
import com.feedbactory.server.network.component.buffer.GrowableByteBuffer;
import com.feedbactory.server.network.component.buffer.ReadableByteBuffer;
import com.feedbactory.server.network.component.buffer.ServerByteBuffer;
import com.feedbactory.server.network.component.buffer.WrappedByteBuffer;
import com.feedbactory.server.network.component.buffer.WritableByteBuffer;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.Message;
import com.feedbactory.shared.MessageType;
import com.feedbactory.shared.network.FeedbactoryApplicationServerStatus;
import com.feedbactory.shared.network.IPAddressStanding;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


final public class ApplicationRequestManager
{
   static final private int BufferPoolCapacity = 10000;
   static final private int AllocationSizePerPoolBuffer = 512;

   static final private int OversizeBufferPoolCapacity = 1000;
   static final private int OversizeAllocationSizePerPoolBuffer = 10240;

   static final private ByteBuffer RequestDrainerByteBuffer = ByteBuffer.allocateDirect(AllocationSizePerPoolBuffer);

   static final private long RequestReadTimeoutMilliseconds = FeedbactoryServer.IsDebugMode ? 0 : 13309;
   static final private int MaximumAllowableClientRequestReadSize = 1691;

   final private ServerControllerInterface serverController;

   final private IPAddressRequestMonitor requestMonitor;

   final private NetworkToApplicationGateway networkServiceGateway;

   final private ByteBufferPool bufferPool = new ByteBufferPool(BufferPoolCapacity, AllocationSizePerPoolBuffer, false);
   final private ByteBufferPool oversizeBufferPool = new ByteBufferPool(OversizeBufferPoolCapacity, OversizeAllocationSizePerPoolBuffer, false);

   final private ClientRequestReader requestReader = new ClientRequestReader(MaximumAllowableClientRequestReadSize, RequestReadTimeoutMilliseconds);
   final private ClientResponseWriter responseWriter = new ClientResponseWriter();

   final private ClientIOEventConsumer temporarilyBlockedIPAddressRequestHandler = initialiseTemporarilyBlockedIPAddressHandler();

   final private ClientIOEventConsumer applicationRequestHandler = new ApplicationRequestHandler();

   final private ClientIOEventConsumer busyHandler = initialiseBusyHandler();

   volatile private boolean isApplicationAvailable = true;
   volatile private Message applicationNotAvailableMessage = Message.NoMessage;
   volatile private ClientIOEventConsumer applicationNotAvailableHandler = initialiseApplicationNotAvailableHandler();
   volatile private long applicationAvailabilityLastUpdated = FeedbactoryConstants.NoTime;

   final private AtomicLong totalLegitimateRequests = new AtomicLong();
   final private AtomicLong totalDeniedRequests = new AtomicLong();
   final private AtomicLong totalErroneousRequests = new AtomicLong();
   final private AtomicLong totalOverflowRequests = new AtomicLong();
   final private AtomicLong totalTimeoutRequests = new AtomicLong();
   final private AtomicLong totalReadFailureRequests = new AtomicLong();

   final private AtomicLong totalLegitimateRequestBytes = new AtomicLong();
   // Read as: total bytes responded to legitimate requests.
   final private AtomicLong totalLegitimateResponseBytes = new AtomicLong();

   final private AtomicInteger largestLegitimateRequestSize = new AtomicInteger();
   final private AtomicInteger largestLegitimateResponseSize = new AtomicInteger();


   ApplicationRequestManager(final ServerControllerInterface serverController,
                             final IPAddressRequestMonitor requestMonitor,
                             final NetworkToApplicationGateway networkServiceGateway)
   {
      this.serverController = serverController;
      this.requestMonitor = requestMonitor;
      this.networkServiceGateway = networkServiceGateway;
   }


   private StaticResponseRequestHandler initialiseTemporarilyBlockedIPAddressHandler()
   {
      final ByteBuffer blockedResponseByteBufferBuilder = ByteBuffer.allocate(1);
      blockedResponseByteBufferBuilder.put(IPAddressStanding.TemporarilyBlocked.value);
      blockedResponseByteBufferBuilder.position(0);

      return new TemporarilyBlockedIPAddressRequestHandler(blockedResponseByteBufferBuilder.asReadOnlyBuffer());
   }


   private StaticResponseRequestHandler initialiseBusyHandler()
   {
      final ByteBuffer busyResponseByteBufferBuilder = ByteBuffer.allocate(2);
      busyResponseByteBufferBuilder.put(IPAddressStanding.OK.value);
      busyResponseByteBufferBuilder.put(FeedbactoryApplicationServerStatus.Busy.value);
      busyResponseByteBufferBuilder.position(0);

      return new StaticResponseRequestHandler(busyResponseByteBufferBuilder.asReadOnlyBuffer());
   }


   private StaticResponseRequestHandler initialiseApplicationNotAvailableHandler()
   {
      final ByteBuffer notAvailableResponseByteBufferBuilder;

      if (applicationNotAvailableMessage.messageType != MessageType.NoMessage)
      {
         final byte[] messageBytes = applicationNotAvailableMessage.message.getBytes(StandardCharsets.UTF_8);

         notAvailableResponseByteBufferBuilder = ByteBuffer.allocate(messageBytes.length + 7);

         notAvailableResponseByteBufferBuilder.put(IPAddressStanding.OK.value);
         notAvailableResponseByteBufferBuilder.put(FeedbactoryApplicationServerStatus.NotAvailable.value);
         notAvailableResponseByteBufferBuilder.put(applicationNotAvailableMessage.messageType.value);
         notAvailableResponseByteBufferBuilder.putInt(messageBytes.length);
         notAvailableResponseByteBufferBuilder.put(messageBytes);

         notAvailableResponseByteBufferBuilder.position(0);
      }
      else
      {
         notAvailableResponseByteBufferBuilder = ByteBuffer.allocate(3);
         notAvailableResponseByteBufferBuilder.put(IPAddressStanding.OK.value);
         notAvailableResponseByteBufferBuilder.put(FeedbactoryApplicationServerStatus.NotAvailable.value);

         notAvailableResponseByteBufferBuilder.put(MessageType.NoMessage.value);

         notAvailableResponseByteBufferBuilder.position(0);
      }

      return new StaticResponseRequestHandler(notAvailableResponseByteBufferBuilder.asReadOnlyBuffer());
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class BufferPoolMetrics
   {
      final public int bufferPoolCapacity;
      final public int bufferPoolAllocationSizePerBuffer;
      final public int bufferPoolBuffersAvailable;
      final public int bufferPoolPooledTakeRequests;
      final public int bufferPoolAllocatedTakeRequests;
      final public int bufferPoolAcceptedReclamations;
      final public int bufferPoolRejectedReclamations;

      final public int oversizeBufferPoolCapacity;
      final public int oversizeBufferPoolAllocationSizePerBuffer;
      final public int oversizeBufferPoolBuffersAvailable;
      final public int oversizeBufferPoolPooledTakeRequests;
      final public int oversizeBufferPoolAllocatedTakeRequests;
      final public int oversizeBufferPoolAcceptedReclamations;
      final public int oversizeBufferPoolRejectedReclamations;


      private BufferPoolMetrics(final int bufferPoolBuffersAvailable, final int bufferPoolPooledTakeRequests, final int bufferPoolAllocatedTakeRequests,
                                final int bufferPoolAcceptedReclamations, final int bufferPoolRejectedReclamations,
                                final int oversizeBufferPoolBuffersAvailable, final int oversizeBufferPoolPooledTakeRequests, final int oversizeBufferPoolAllocatedTakeRequests,
                                final int oversizeBufferPoolAcceptedReclamations, final int oversizeBufferPoolRejectedReclamations)
      {
         this.bufferPoolCapacity = BufferPoolCapacity;
         this.bufferPoolAllocationSizePerBuffer = AllocationSizePerPoolBuffer;
         this.bufferPoolBuffersAvailable = bufferPoolBuffersAvailable;
         this.bufferPoolPooledTakeRequests = bufferPoolPooledTakeRequests;
         this.bufferPoolAllocatedTakeRequests = bufferPoolAllocatedTakeRequests;
         this.bufferPoolAcceptedReclamations = bufferPoolAcceptedReclamations;
         this.bufferPoolRejectedReclamations = bufferPoolRejectedReclamations;

         this.oversizeBufferPoolCapacity = OversizeBufferPoolCapacity;
         this.oversizeBufferPoolAllocationSizePerBuffer = OversizeAllocationSizePerPoolBuffer;
         this.oversizeBufferPoolBuffersAvailable = oversizeBufferPoolBuffersAvailable;
         this.oversizeBufferPoolPooledTakeRequests = oversizeBufferPoolPooledTakeRequests;
         this.oversizeBufferPoolAllocatedTakeRequests = oversizeBufferPoolAllocatedTakeRequests;
         this.oversizeBufferPoolAcceptedReclamations = oversizeBufferPoolAcceptedReclamations;
         this.oversizeBufferPoolRejectedReclamations = oversizeBufferPoolRejectedReclamations;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class RequestMetrics
   {
      final public long requestReadTimeoutMilliseconds;
      final public int maximumAllowableClientRequestSize;

      final public long totalLegitimateRequests;
      final public long totalDeniedRequests;
      final public long totalErroneousRequests;
      final public long totalOverflowRequests;
      final public long totalTimeoutRequests;
      final public long totalReadRequestFailures;

      final public long totalLegitimateRequestBytes;
      final public long totalLegitimateResponseBytes;

      final public int largestLegitimateRequestSize;
      final public int largestResponseSize;


      private RequestMetrics(final long totalLegitimateRequests, final long totalDeniedRequests, final long totalErroneousRequests,
                             final long totalTimeoutRequests, final long totalReadRequestFailures, final long totalOverflowRequests,
                             final long totalLegitimateRequestBytes, final long totalLegitimateResponseBytes,
                             final int largestLegitimateRequestSize, final int largestResponseSize)
      {
         this.requestReadTimeoutMilliseconds = RequestReadTimeoutMilliseconds;
         this.maximumAllowableClientRequestSize = MaximumAllowableClientRequestReadSize;

         this.totalLegitimateRequests = totalLegitimateRequests;
         this.totalDeniedRequests = totalDeniedRequests;
         this.totalErroneousRequests = totalErroneousRequests;
         this.totalOverflowRequests = totalOverflowRequests;
         this.totalTimeoutRequests = totalTimeoutRequests;
         this.totalReadRequestFailures = totalReadRequestFailures;

         this.totalLegitimateRequestBytes = totalLegitimateRequestBytes;
         this.totalLegitimateResponseBytes = totalLegitimateResponseBytes;

         this.largestLegitimateRequestSize = largestLegitimateRequestSize;
         this.largestResponseSize = largestResponseSize;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   abstract private class ClientIOEventConsumer implements ClientRequestReaderConsumer, ClientResponseWriterConsumer
   {
      @Override
      final public void reportClientRequestReadTimeout(final ClientNetworkID clientNetworkID, final ReadableByteBuffer requestBuffer)
      {
         try
         {
            if (FeedbactoryLogger.isLoggingSystemEventsAtLevel(SystemLogLevel.ClientStateError))
            {
               final SystemEvent systemEvent = new SystemEvent(SystemLogLevel.ClientStateError, getClass(), "Client request read timeout");
               systemEvent.setClientNetworkID(clientNetworkID);
               systemEvent.setRequestBuffer(requestBuffer);
               FeedbactoryLogger.logSystemEvent(systemEvent);
            }

            requestMonitor.reportErroneousClientRequest(clientNetworkID.inetSocketAddress.getAddress());
            totalTimeoutRequests.incrementAndGet();
         }
         finally
         {
            clientIOFinished(clientNetworkID, requestBuffer);
         }
      }


      @Override
      final public void reportClientRequestReadFailed(final ClientNetworkID clientNetworkID, final ReadableByteBuffer requestBuffer, final Throwable throwable)
      {
         try
         {
            if (FeedbactoryLogger.isLoggingSystemEventsAtLevel(SystemLogLevel.ClientStateError))
            {
               final SystemEvent systemEvent = new SystemEvent(SystemLogLevel.ClientStateError, getClass(), "Failed client request read", throwable);
               systemEvent.setClientNetworkID(clientNetworkID);
               systemEvent.setRequestBuffer(requestBuffer);
               FeedbactoryLogger.logSystemEvent(systemEvent);
            }

            requestMonitor.reportErroneousClientRequest(clientNetworkID.inetSocketAddress.getAddress());
            totalReadFailureRequests.incrementAndGet();
         }
         finally
         {
            clientIOFinished(clientNetworkID, requestBuffer);
         }
      }


      @Override
      final public void reportClientRequestReadOverflow(final ClientNetworkID clientNetworkID, final ReadableByteBuffer requestBuffer)
      {
         try
         {
            if (FeedbactoryLogger.isLoggingSystemEventsAtLevel(SystemLogLevel.ErroneousClientRequest))
            {
               final SystemEvent systemEvent = new SystemEvent(SystemLogLevel.ErroneousClientRequest, getClass(), "Client request read overflow");
               systemEvent.setClientNetworkID(clientNetworkID);
               systemEvent.setRequestBuffer(requestBuffer);
               FeedbactoryLogger.logSystemEvent(systemEvent);
            }

            requestMonitor.reportErroneousClientRequest(clientNetworkID.inetSocketAddress.getAddress());
            totalOverflowRequests.incrementAndGet();
         }
         finally
         {
            clientIOFinished(clientNetworkID, requestBuffer);
         }
      }


      /****************************************************************************
       *
       ***************************************************************************/


      @Override
      public void clientResponseWritten(final ClientNetworkID clientNetworkID, final ReadableByteBuffer responseBuffer)
      {
         try
         {
            /* Unfortunately at this point there's no reference to either the size of the original request or the pre-written size of the response,
             * so for now here is not the place to update the legit request & response metrics. Those have to be updated at the point after the
             * request has been processed but before throwing to the async IO to write the response.
             * Down the track it might be worthwhile either a) modifying the ClientResponseWriter to accept an attachment that it could then use for
             * this callback, or b) for each client instantiate a separate ClientResponseWriterConsumer which contains a reference to the original
             * request. But for now the stats would only be affected if the request fails at the final hurdle of writing the response.
             */
            requestMonitor.reportLegitimateClientRequest(clientNetworkID.inetSocketAddress.getAddress());
         }
         finally
         {
            clientIOFinished(clientNetworkID, responseBuffer);
         }
      }


      @Override
      final public void reportClientResponseWriteFailed(final ClientNetworkID clientNetworkID, final ReadableByteBuffer responseBuffer, final Throwable throwable)
      {
         try
         {
            if (FeedbactoryLogger.isLoggingSystemEventsAtLevel(SystemLogLevel.ClientStateError))
            {
               final SystemEvent generalEvent = new SystemEvent(SystemLogLevel.ClientStateError, getClass(), "Failed client response write", throwable);
               generalEvent.setClientNetworkID(clientNetworkID);
               FeedbactoryLogger.logSystemEvent(generalEvent);
            }

            /* At the moment there are no metrics kept for the number of failed response writes. This is because by this point this request will
             * have already been added to the 'legitimate' request tally. See the comment in clientResponseWritten().
             */
            requestMonitor.reportErroneousClientRequest(clientNetworkID.inetSocketAddress.getAddress());
         }
         finally
         {
            clientIOFinished(clientNetworkID, responseBuffer);
         }
      }


      /****************************************************************************
       *
       ***************************************************************************/


      abstract void newClientRequest(final ClientNetworkID clientNetworkID);


      final void clientIOFinished(final ClientNetworkID clientNetworkID, final ServerByteBuffer buffer)
      {
         buffer.reclaim();
         clientConnectionFinished(clientNetworkID.clientChannel);
      }
   }


   /****************************************************************************
    * 
    * Start regular client IO handler.
    * 
    ***************************************************************************/


   final private class ApplicationRequestHandler extends ClientIOEventConsumer
   {
      private void handleClientRequestRead(final ClientNetworkID clientNetworkID, final ReadableByteBuffer requestBuffer)
      {
         final ClientIO clientIO = new ClientIO(clientNetworkID, requestBuffer, takePoolBuffer());

         boolean endConnection = false;

         try
         {
            clientIO.responseBuffer.put(IPAddressStanding.OK.value);
            clientIO.responseBuffer.put(FeedbactoryApplicationServerStatus.Available.value);

            final int requestSize = clientIO.requestBuffer.getRemaining();

            final ProcessedOperationStatus requestResult = networkServiceGateway.processClientRequest(clientIO);

            if (requestResult == ProcessedOperationStatus.OK)
            {
               totalLegitimateRequests.incrementAndGet();
               totalLegitimateRequestBytes.addAndGet(requestSize);
               checkUpdateLargestLegitimateRequestSize(requestSize);
               checkUpdateLargestResponseSize(clientIO.responseBuffer.getPosition());

               totalLegitimateResponseBytes.addAndGet(clientIO.responseBuffer.getPosition());

               responseWriter.writeResponse(clientIO.networkID, clientIO.responseBuffer.flipToReadableByteBuffer(), this);

               // The report to the request monitor will occur once the response write completes, either successfully or not.
            }
            else if (requestResult == ProcessedOperationStatus.ErroneousRequest)
            {
               endConnection = true;
               requestMonitor.reportErroneousClientRequest(clientIO.networkID.inetSocketAddress.getAddress());
               totalErroneousRequests.incrementAndGet();
            }
            else
            {
               endConnection = true;
               throw new AssertionError("Unhandled processed operation status result: " + requestResult);
            }
         }
         catch (final FeedbactorySecurityException securityException)
         {
            endConnection = true;

            FeedbactoryLogger.logSecurityEvent(securityException.securityEventLevel, getClass(), securityException.getMessage(), clientIO);

            requestMonitor.reportErroneousClientRequest(clientIO.networkID.inetSocketAddress.getAddress());
            totalErroneousRequests.incrementAndGet();
         }
         catch (final Exception anyOtherException)
         {
            endConnection = true;

            if (FeedbactoryLogger.isLoggingSystemEventsAtLevel(SystemLogLevel.ErroneousClientRequest))
            {
               final SystemEvent generalEvent = new SystemEvent(SystemLogLevel.ErroneousClientRequest, getClass(), "Exception while processing request", anyOtherException);
               generalEvent.setClientIO(clientIO);
               FeedbactoryLogger.logSystemEvent(generalEvent);
            }

            requestMonitor.reportErroneousClientRequest(clientIO.networkID.inetSocketAddress.getAddress());
            totalErroneousRequests.incrementAndGet();
         }
         finally
         {
            // Unconditionally reclaim the request buffer, its work is done.
            clientIO.requestBuffer.reclaim();

            /* Only reclaim the response buffer and close the connection if the request result indicates a failure or erroneous state.
             * If the request result is OK, the response writer (using async IO) will clean up the response buffer and close the connection once
             * it's finished or if there is an error.
             */
            if (endConnection)
               clientIOFinished(clientIO.networkID, clientIO.responseBuffer);
         }
      }


      private void checkUpdateLargestLegitimateRequestSize(final int requestSize)
      {
         int currentLargestRequestSize;

         for (;;)
         {
            currentLargestRequestSize = largestLegitimateRequestSize.get();

            if ((requestSize <= currentLargestRequestSize) ||
                (largestLegitimateRequestSize.compareAndSet(currentLargestRequestSize, requestSize)))
               break;
         }
      }


      private void checkUpdateLargestResponseSize(final int responseSize)
      {
         int currentLargestResponseSize;

         for (;;)
         {
            currentLargestResponseSize = largestLegitimateResponseSize.get();

            if ((responseSize <= currentLargestResponseSize) ||
                (largestLegitimateResponseSize.compareAndSet(currentLargestResponseSize, responseSize)))
               break;
         }
      }


      /****************************************************************************
       * 
       ***************************************************************************/


      @Override
      final void newClientRequest(final ClientNetworkID clientNetworkID)
      {
         requestReader.readClientRequest(clientNetworkID, new GrowableByteBuffer(bufferPool, oversizeBufferPool), this);
      }


      @Override
      final public void clientRequestRead(final ClientNetworkID clientNetworkID, final ReadableByteBuffer requestBuffer)
      {
         handleClientRequestRead(clientNetworkID, requestBuffer);
      }
   }


   /****************************************************************************
    * 
    * End regular client IO handler.
    * 
    ***************************************************************************/


   private class StaticResponseRequestHandler extends ClientIOEventConsumer
   {
      final private ByteBuffer staticResponseByteBuffer;


      private StaticResponseRequestHandler(final ByteBuffer staticResponseByteBuffer)
      {
         this.staticResponseByteBuffer = staticResponseByteBuffer;
      }


      /****************************************************************************
       * 
       ***************************************************************************/


      @Override
      final void newClientRequest(final ClientNetworkID clientNetworkID)
      {
         /* The default action of reclaiming buffers when the read has finished is OK for DiscardByteBuffers, since its reclaim
          * implementation is a NOP.
          */
         requestReader.readClientRequest(clientNetworkID, new DiscardByteBuffer(RequestDrainerByteBuffer.duplicate()), this);
      }


      @Override
      final public void clientRequestRead(final ClientNetworkID clientNetworkID, final ReadableByteBuffer requestBuffer)
      {
         /* A static response is going to be sent to the client because the server is either unavailable or busy.
          * So although the server has received the client's request, it has discarded its contents (via the DiscardByteBuffer).
          * The result is that at this point there's no way of knowing from a metrics point of view how large the request was or
          * whether or not it's legal. The default post-handling of the response write won't update the metrics, so my gut
          * feeling is to not touch them here either.
          * Effectively the metrics, at least for this class, are left untouched when the server is busy or unavailable.
          *
          * As above the default action after the client response has been written - clientResponseWritten - attempts to reclaim the response buffer but
          * again the reclaim implementation (this time for WrappedByteBuffer) is a NOP.
          */
         responseWriter.writeResponse(clientNetworkID, new WrappedByteBuffer(staticResponseByteBuffer.duplicate()), this);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class TemporarilyBlockedIPAddressRequestHandler extends StaticResponseRequestHandler
   {
      private TemporarilyBlockedIPAddressRequestHandler(final ByteBuffer staticResponseByteBuffer)
      {
         super(staticResponseByteBuffer);
      }


      @Override
      final public void clientResponseWritten(final ClientNetworkID clientNetworkID, final ReadableByteBuffer responseBuffer)
      {
         // See the corresponding method in AbstractClientIOEventConsumer regarding the (non) handling of the request size metrics here.
         requestMonitor.reportDeniedClientRequest(clientNetworkID.inetSocketAddress.getAddress());
         clientIOFinished(clientNetworkID, responseBuffer);
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private void handleNewConnectionAccepted(final AsynchronousSocketChannel channel)
   {
      ClientNetworkID clientNetworkID = null;
      boolean endConnection = false;

      try
      {
         clientNetworkID = new ClientNetworkID(channel);

         final IPAddressStanding ipAddressStanding = requestMonitor.getIPAddressStanding(clientNetworkID.inetSocketAddress.getAddress());

         switch (ipAddressStanding)
         {
            case OK:
               if (! isApplicationAvailable)
                  applicationNotAvailableHandler.newClientRequest(clientNetworkID);
               else if (serverController.isBusy())
                  busyHandler.newClientRequest(clientNetworkID);
               else
                  applicationRequestHandler.newClientRequest(clientNetworkID);

               break;

            case TemporarilyBlocked:
               temporarilyBlockedIPAddressRequestHandler.newClientRequest(clientNetworkID);
               break;

            case Blacklisted:
               endConnection = true;
               requestMonitor.reportDeniedClientRequest(clientNetworkID.inetSocketAddress.getAddress());
               totalDeniedRequests.incrementAndGet();
               break;

            default:
               endConnection = true;
               throw new AssertionError("Unhandled IP address standing value: " + ipAddressStanding);
         }
      }
      catch (final IOException ioException)
      {
         endConnection = true;

         if (FeedbactoryLogger.isLoggingSystemEventsAtLevel(SystemLogLevel.ClientStateError))
         {
            final SystemEvent generalEvent = new SystemEvent(SystemLogLevel.ClientStateError, getClass(), "Exception while initialising client channel", ioException);
            if (clientNetworkID != null)
               generalEvent.setClientNetworkID(clientNetworkID);

            FeedbactoryLogger.logSystemEvent(generalEvent);
         }
      }
      catch (final Exception anyOtherException)
      {
         endConnection = true;

         if (FeedbactoryLogger.isLoggingSystemEventsAtLevel(SystemLogLevel.ApplicationError))
         {
            final SystemEvent generalEvent = new SystemEvent(SystemLogLevel.ApplicationError, getClass(), "Exception while accepting new connection", anyOtherException);
            if (clientNetworkID != null)
               generalEvent.setClientNetworkID(clientNetworkID);

            FeedbactoryLogger.logSystemEvent(generalEvent);
         }
      }
      finally
      {
         if (endConnection)
            clientConnectionFinished(channel);
      }
   }


   private void clientConnectionFinished(final AsynchronousSocketChannel channel)
   {
      serverController.clientConnectionFinished(channel);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private BufferPoolMetrics handleGetBufferPoolMetrics()
   {
      return new BufferPoolMetrics(bufferPool.getBuffersAvailable(), bufferPool.getPooledTakeRequests(), bufferPool.getAllocatedTakeRequests(),
                                   bufferPool.getAcceptedReclamations(), bufferPool.getRejectedReclamations(),
                                   oversizeBufferPool.getBuffersAvailable(), oversizeBufferPool.getPooledTakeRequests(), oversizeBufferPool.getAllocatedTakeRequests(),
                                   oversizeBufferPool.getAcceptedReclamations(), oversizeBufferPool.getRejectedReclamations());
   }


   private RequestMetrics handleGetRequestMetrics()
   {
      return new RequestMetrics(totalLegitimateRequests.get(), totalDeniedRequests.get(), totalErroneousRequests.get(),
                                totalOverflowRequests.get(), totalTimeoutRequests.get(), totalReadFailureRequests.get(),
                                totalLegitimateRequestBytes.get(), totalLegitimateResponseBytes.get(),
                                largestLegitimateRequestSize.get(), largestLegitimateResponseSize.get());
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final WritableByteBuffer takePoolBuffer()
   {
      return new GrowableByteBuffer(bufferPool, oversizeBufferPool);
   }


   final void newConnectionAccepted(final AsynchronousSocketChannel channel)
   {
      handleNewConnectionAccepted(channel);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public BufferPoolMetrics getBufferPoolMetrics()
   {
      return handleGetBufferPoolMetrics();
   }


   final public RequestMetrics getRequestMetrics()
   {
      return handleGetRequestMetrics();
   }


   final public Message getApplicationNotAvailableMessage()
   {
      return applicationNotAvailableMessage;
   }


   final public void setApplicationNotAvailableMessage(final Message applicationNotAvailableMessage)
   {
      this.applicationNotAvailableMessage = applicationNotAvailableMessage;
      applicationNotAvailableHandler = initialiseApplicationNotAvailableHandler();
   }


   final public boolean isApplicationAvailable()
   {
      return isApplicationAvailable;
   }


   final public void setApplicationAvailable(final boolean isApplicationAvailable)
   {
      this.isApplicationAvailable = isApplicationAvailable;
      this.applicationAvailabilityLastUpdated = TimeCache.getCurrentTimeMilliseconds();
   }


   final public long getApplicationAvailabilityLastUpdated()
   {
      return applicationAvailabilityLastUpdated;
   }
}