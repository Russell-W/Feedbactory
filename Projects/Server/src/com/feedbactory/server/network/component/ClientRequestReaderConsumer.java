package com.feedbactory.server.network.component;


import com.feedbactory.server.network.component.buffer.ReadableByteBuffer;


public interface ClientRequestReaderConsumer
{
   public void clientRequestRead(final ClientNetworkID clientNetworkID, final ReadableByteBuffer requestBuffer);

   public void reportClientRequestReadTimeout(final ClientNetworkID clientNetworkID, final ReadableByteBuffer requestBuffer);
   public void reportClientRequestReadFailed(final ClientNetworkID clientNetworkID, final ReadableByteBuffer requestBuffer, final Throwable throwable);
   public void reportClientRequestReadOverflow(final ClientNetworkID clientNetworkID, final ReadableByteBuffer requestBuffer);
}