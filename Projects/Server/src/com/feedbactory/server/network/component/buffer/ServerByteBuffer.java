
package com.feedbactory.server.network.component.buffer;


import java.nio.ByteBuffer;


public interface ServerByteBuffer
{
   public ByteBuffer getActiveBuffer();

   public int getPosition();
   public void setPosition(final int newPosition);

   public void reclaim();
}