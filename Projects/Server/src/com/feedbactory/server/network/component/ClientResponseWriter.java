
package com.feedbactory.server.network.component;


import com.feedbactory.server.network.component.buffer.ReadableByteBuffer;
import java.io.IOException;
import java.nio.channels.CompletionHandler;


final public class ClientResponseWriter
{
   final private ResponseWriterDelegate writerDelegate = new ResponseWriterDelegate();


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final private class ResponseWriterAttachment
   {
      final private ClientNetworkID clientNetworkID;
      final private ReadableByteBuffer responseBuffer;
      final private ClientResponseWriterConsumer responseWriterConsumer;


      private ResponseWriterAttachment(final ClientNetworkID clientNetworkID, final ReadableByteBuffer responseBuffer, final ClientResponseWriterConsumer responseWriterConsumer)
      {
         this.clientNetworkID = clientNetworkID;
         this.responseBuffer = responseBuffer;
         this.responseWriterConsumer = responseWriterConsumer;
      }
   }



   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class ResponseWriterDelegate implements CompletionHandler<Integer, ResponseWriterAttachment>
   {
      @Override
      final public void completed(final Integer bytesWritten, final ResponseWriterAttachment client)
      {
         if (client.responseBuffer.advanceReadPosition())
            client.clientNetworkID.clientChannel.write(client.responseBuffer.getActiveBuffer(), client, this);
         else
         {
            IOException channelOutputShutdownException = null;

            try
            {
               client.clientNetworkID.clientChannel.shutdownOutput();
            }
            catch (final IOException ioException)
            {
               channelOutputShutdownException = ioException;
            }

            if (channelOutputShutdownException == null)
               client.responseWriterConsumer.clientResponseWritten(client.clientNetworkID, client.responseBuffer);
            else
               client.responseWriterConsumer.reportClientResponseWriteFailed(client.clientNetworkID, client.responseBuffer, channelOutputShutdownException);
         }
      }


      @Override
      final public void failed(final Throwable throwable, final ResponseWriterAttachment client)
      {
         client.responseWriterConsumer.reportClientResponseWriteFailed(client.clientNetworkID, client.responseBuffer, throwable);
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final public void writeResponse(final ClientNetworkID clientNetworkID, final ReadableByteBuffer responseBuffer, final ClientResponseWriterConsumer responseWriterConsumer)
   {
      final ResponseWriterAttachment responseWriterAttachment = new ResponseWriterAttachment(clientNetworkID, responseBuffer, responseWriterConsumer);
      clientNetworkID.clientChannel.write(responseBuffer.getActiveBuffer(), responseWriterAttachment, writerDelegate);
   }
}