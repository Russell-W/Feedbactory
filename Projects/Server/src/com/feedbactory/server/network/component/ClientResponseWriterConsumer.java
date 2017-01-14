
package com.feedbactory.server.network.component;


import com.feedbactory.server.network.component.buffer.ReadableByteBuffer;


public interface ClientResponseWriterConsumer
{
   public void clientResponseWritten(final ClientNetworkID clientNetworkID, final ReadableByteBuffer responseBuffer);
   public void reportClientResponseWriteFailed(final ClientNetworkID clientNetworkID, final ReadableByteBuffer responseBuffer, final Throwable throwable);
}