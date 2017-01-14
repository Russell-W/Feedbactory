
package com.feedbactory.server.network.component;


import com.feedbactory.server.core.TimeCache;
import com.feedbactory.server.network.component.buffer.WritableByteBuffer;
import java.io.IOException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.util.concurrent.TimeUnit;


final public class ClientRequestReader
{
   final private int maximumAllowableReadSize;
   final private long clientRequestReadTimeoutMilliseconds;


   final private RequestReaderDelegate requestReaderDelegate = new RequestReaderDelegate();


   public ClientRequestReader(final int maximumAllowableReadSize, final long clientRequestReadTimeoutMilliseconds)
   {
      validate(maximumAllowableReadSize, clientRequestReadTimeoutMilliseconds);

      this.maximumAllowableReadSize = maximumAllowableReadSize;
      this.clientRequestReadTimeoutMilliseconds = clientRequestReadTimeoutMilliseconds;
   }


   private void validate(final int maximumAllowableReadSize, final long clientRequestReadTimeoutMilliseconds)
   {
      if (maximumAllowableReadSize < 0)
         throw new IllegalArgumentException("Maximum allowable read size cannot be less than zero.");
      else if (clientRequestReadTimeoutMilliseconds < 0)
         throw new IllegalArgumentException("Read timeout cannot be less than zero.");
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final private class RequestReaderAttachment
   {
      final private ClientNetworkID clientNetworkID;
      final private WritableByteBuffer requestBuffer;
      final private ClientRequestReaderConsumer readRequestConsumer;
      final private long readRequestStartTime = TimeCache.getCurrentTimeMilliseconds();


      private RequestReaderAttachment(final ClientNetworkID clientSocket, final WritableByteBuffer requestBuffer,
                                      final ClientRequestReaderConsumer readRequestConsumer)
      {
         this.clientNetworkID = clientSocket;
         this.requestBuffer = requestBuffer;
         this.readRequestConsumer = readRequestConsumer;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class RequestReaderDelegate implements CompletionHandler<Integer, RequestReaderAttachment>
   {
      @Override
      final public void completed(final Integer bytesRead, final RequestReaderAttachment client)
      {
         /* With different IO worker threads calling this method for the same client, we can assume visibility of our attachment's non-volatile variables across
          * successive calls - see ExecutorService's doc regarding memory consistency effects.
          *
          * Another important thing to note here is that by not enclosing this method in a try/catch, I'm effectively guaranteeing that there won't
          * be an application (ie. non-io) error and that there will eventually be a callback to the read request consumer. For Feedbactory this is a very
          * important callback since the consumer needs to propagate the call to update the number of active server connections.
          */
         if (bytesRead == -1)
            client.readRequestConsumer.clientRequestRead(client.clientNetworkID, client.requestBuffer.flipToReadableByteBuffer());
         else if ((maximumAllowableReadSize == 0) || (client.requestBuffer.getPosition() <= maximumAllowableReadSize))
         {
            final long remainingTime;

            if (clientRequestReadTimeoutMilliseconds != 0)
               remainingTime = clientRequestReadTimeoutMilliseconds - (TimeCache.getCurrentTimeMilliseconds() - client.readRequestStartTime);
            else
               remainingTime = 0;

            if (remainingTime >= 0)
            {
               client.requestBuffer.advanceWritePosition();
               client.clientNetworkID.clientChannel.read(client.requestBuffer.getActiveBuffer(), remainingTime, TimeUnit.MILLISECONDS, client, this);
            }
            else
               processClientRequestReadTimeout(client);
         }
         else
            processClientRequestReadOverflow(client);
      }


      @Override
      final public void failed(final Throwable throwable, final RequestReaderAttachment client)
      {
         if (throwable instanceof InterruptedByTimeoutException)
            processClientRequestReadTimeout(client);
         else
            processClientRequestReadFailed(client, throwable);
      }


      private void processClientRequestReadTimeout(final RequestReaderAttachment client)
      {
         closeErrorStateChannelInput(client.clientNetworkID);
         client.readRequestConsumer.reportClientRequestReadTimeout(client.clientNetworkID, client.requestBuffer.flipToReadableByteBuffer());
      }


      private void processClientRequestReadFailed(final RequestReaderAttachment client, final Throwable throwable)
      {
         closeErrorStateChannelInput(client.clientNetworkID);
         client.readRequestConsumer.reportClientRequestReadFailed(client.clientNetworkID, client.requestBuffer.flipToReadableByteBuffer(), throwable);
      }


      private void processClientRequestReadOverflow(final RequestReaderAttachment client)
      {
         closeErrorStateChannelInput(client.clientNetworkID);
         client.readRequestConsumer.reportClientRequestReadOverflow(client.clientNetworkID, client.requestBuffer.flipToReadableByteBuffer());
      }


      private void closeErrorStateChannelInput(final ClientNetworkID clientNetworkID)
      {
         try
         {
            clientNetworkID.clientChannel.shutdownInput();
         }
         catch (final IOException ioException)
         {
            // Take no further action, since the error state of the connection is already being reported.
         }
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final public void readClientRequest(final ClientNetworkID clientNetworkID, final WritableByteBuffer requestBuffer, final ClientRequestReaderConsumer readRequestConsumer)
   {
      final RequestReaderAttachment requestReaderAttachment = new RequestReaderAttachment(clientNetworkID, requestBuffer, readRequestConsumer);
      clientNetworkID.clientChannel.read(requestBuffer.getActiveBuffer(), clientRequestReadTimeoutMilliseconds, TimeUnit.MILLISECONDS, requestReaderAttachment, requestReaderDelegate);
   }
}