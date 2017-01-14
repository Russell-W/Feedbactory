
package com.feedbactory.server.network.component.buffer;


public interface ReadableByteBuffer extends ServerByteBuffer
{
   public int getLimit();
   public void setLimit(final int newLimit);
   public int getRemaining();

   public byte get();
   public void get(final byte[] bytes);
   public boolean getBoolean();
   public short getShort();
   public int getInteger();
   public long getLong();
   public String getUTF8EncodedString();
   public boolean advanceReadPosition();
}