
package com.feedbactory.server.network.component.buffer;


import java.nio.ByteBuffer;


public class DiscardByteBuffer implements WritableByteBuffer
{
   final private ByteBuffer buffer;


   public DiscardByteBuffer(final ByteBuffer buffer)
   {
      this.buffer = buffer;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public ByteBuffer getActiveBuffer()
   {
      return buffer;
   }


   @Override
   final public int getPosition()
   {
      return 0;
   }


   @Override
   final public void setPosition(final int newPosition)
   {
   }


   @Override
   final public void ensureRemainingCapacity(final int remainingCapacity)
   {
   }


   @Override
   final public void put(final byte byteToPut)
   {
   }


   @Override
   final public void put(final byte[] bytes)
   {
   }


   @Override
   final public void put(final ByteBuffer byteBuffer)
   {
   }


   @Override
   final public void putBoolean(final boolean booleanValue)
   {
   }


   @Override
   final public void putShort(final short shortValue)
   {
   }


   @Override
   final public void putInteger(final int integer)
   {
   }


   @Override
   final public void putLong(final long longToPut)
   {
   }


   @Override
   final public void putUTF8EncodedString(final String string)
   {
   }


   @Override
   final public void advanceWritePosition()
   {
      buffer.position(0);
   }


   @Override
   final public ReadableByteBuffer flipToReadableByteBuffer()
   {
      buffer.position(0);
      return new WrappedByteBuffer(buffer);
   }


   @Override
   final public void reclaim()
   {
   }
}