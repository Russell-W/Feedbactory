
package com.feedbactory.server.network.component;


import com.feedbactory.server.network.component.buffer.ReadableByteBuffer;
import com.feedbactory.server.network.component.buffer.WritableByteBuffer;


final public class ClientIO
{
   final public ClientNetworkID networkID;
   final public ReadableByteBuffer requestBuffer;
   final public WritableByteBuffer responseBuffer;


   public ClientIO(final ClientNetworkID networkID, final ReadableByteBuffer requestBuffer, final WritableByteBuffer responseBuffer)
   {
      this.networkID = networkID;
      this.requestBuffer = requestBuffer;
      this.responseBuffer = responseBuffer;
   }
}