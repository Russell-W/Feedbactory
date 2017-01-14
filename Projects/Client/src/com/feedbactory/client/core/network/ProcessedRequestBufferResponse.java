
package com.feedbactory.client.core.network;


import java.nio.ByteBuffer;


final public class ProcessedRequestBufferResponse extends ProcessedRequestResult<ByteBuffer>
{
   public ProcessedRequestBufferResponse(final NetworkRequestStatus requestStatus)
   {
      super(requestStatus);
   }


   public ProcessedRequestBufferResponse(final NetworkRequestStatus requestStatus, final ByteBuffer responseBuffer)
   {
      super(requestStatus, responseBuffer);
   }
}