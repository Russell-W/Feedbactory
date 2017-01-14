/* Memos:
 * - Generally the WritableByteBuffer view of a buffer will be invalid after the flipToReadableByteBuffer() has been invoked, however the 'final' write state immediately
 *   prior to the flipToReadableByteBuffer() call may be restored by saving and restoring the original position using getPosition() and setPosition(). At this point, the only
 *   valid mutating call on the WritableByteBuffer is another call to flipToReadableByteBuffer(). This functionality is handy in that it will allow a written buffer to
 *   be rewound and read more than once, eg. for logging.
 *
 * - The original auto-expanding byte buffers implemented actually used chained ByteBuffers - refer to the drawer.
 *
 * - This class also provides auto-expanding byte buffers, however without using chained buffers. I think that for Feedbactory server purposes, which will rarely involve
 *   buffers exceeding a reasonable capacity, this implementation provides a good balance between efficiency and reduced complexity.
 */

package com.feedbactory.server.network.component.buffer;


import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;


final public class GrowableByteBuffer implements WritableByteBuffer
{
   final private ByteBufferPool regularBufferPool;
   final private ByteBufferPool oversizeBufferPool;

   private ByteBuffer buffer;


   public GrowableByteBuffer(final ByteBufferPool bufferPool, final ByteBufferPool oversizeBufferPool)
   {
      this.regularBufferPool = bufferPool;
      this.oversizeBufferPool = oversizeBufferPool;

      buffer = regularBufferPool.take();
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class ReadableByteBufferView extends WrappedByteBuffer
   {
      private ReadableByteBufferView()
      {
         super(buffer);
      }


      @Override
      final public void reclaim()
      {
         handleReclaim();
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private void grow(final int growthAmount)
   {
      final int newSize = buffer.position() + growthAmount;

      final ByteBuffer newByteBuffer;
      if (newSize <= oversizeBufferPool.getAllocationSize())
         newByteBuffer = oversizeBufferPool.take();
      else
         newByteBuffer = buffer.isDirect() ? ByteBuffer.allocateDirect(newSize) : ByteBuffer.allocate(newSize);

      buffer.flip();
      newByteBuffer.put(buffer);

      reclaim();

      buffer = newByteBuffer;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleEnsureRemainingCapacity(final int minimumRemainingCapacity)
   {
      if (buffer.remaining() < minimumRemainingCapacity)
         grow(Math.max(minimumRemainingCapacity, regularBufferPool.getAllocationSize()));
   }


   private void handlePut(final byte byteToPut)
   {
      ensureRemainingCapacity(1);
      buffer.put(byteToPut);
   }


   private void handlePut(final byte[] bytes)
   {
      ensureRemainingCapacity(bytes.length);
      buffer.put(bytes);
   }


   private void handlePut(final ByteBuffer byteBuffer)
   {
      ensureRemainingCapacity(byteBuffer.remaining());
      buffer.put(byteBuffer);
   }


   private void handlePutBoolean(final boolean booleanValue)
   {
      ensureRemainingCapacity(1);
      buffer.put((byte) (booleanValue ? 1 : 0));
   }


   private void handlePutShort(final short shortValue)
   {
      ensureRemainingCapacity(2);
      buffer.putShort(shortValue);
   }


   private void handlePutInteger(final int integer)
   {
      ensureRemainingCapacity(4);
      buffer.putInt(integer);
   }


   private void handlePutLong(final long longValue)
   {
      ensureRemainingCapacity(8);
      buffer.putLong(longValue);
   }


   private void handlePutUTF8EncodedString(final String string)
   {
      if (string == null)
         putInteger(-1);
      else
      {
         final CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();

         final int maximumBytesRequired = (int) (encoder.maxBytesPerChar() * string.length()) + 4;
         ensureRemainingCapacity(maximumBytesRequired);

         final int stringLengthPosition = buffer.position();
         buffer.position(buffer.position() + 4);

         handleEncoderResult(encoder.encode(CharBuffer.wrap(string), buffer, true), string);
         handleEncoderResult(encoder.flush(buffer), string);

         final int resumeWritePosition = buffer.position();
         buffer.position(stringLengthPosition);

         buffer.putInt(resumeWritePosition - stringLengthPosition - 4);

         buffer.position(resumeWritePosition);
      }
   }


   private void handleEncoderResult(final CoderResult coderResult, final String string)
   {
      if (! coderResult.isUnderflow())
      {
         if (coderResult.isOverflow())
            throw new BufferOverflowException();
         else
            throw new IllegalStateException("Error during UTF8 encoding: " + string);
      }
   }


   private void handleAdvanceWritePosition()
   {
      if (! buffer.hasRemaining())
         grow(regularBufferPool.getAllocationSize());
   }


   private ReadableByteBuffer handleFlipToReadableByteBuffer()
   {
      buffer.flip();
      return new ReadableByteBufferView();
   }


   private void handleReclaim()
   {
      if (buffer != null)
      {
         // Buffers that have been manually allocated (ie. not matching the buffer pool profiles) will be silently rejected.
         if (! regularBufferPool.reclaim(buffer))
            oversizeBufferPool.reclaim(buffer);

         buffer = null;
      }
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
      return buffer.position();
   }


   @Override
   final public void setPosition(final int newPosition)
   {
      buffer.position(newPosition);
   }


   @Override
   final public void ensureRemainingCapacity(final int capacity)
   {
      handleEnsureRemainingCapacity(capacity);
   }


   @Override
   final public void put(final byte byteToPut)
   {
      handlePut(byteToPut);
   }


   @Override
   final public void put(final byte[] bytes)
   {
      handlePut(bytes);
   }


   @Override
   final public void put(final ByteBuffer byteBuffer)
   {
      handlePut(byteBuffer);
   }


   @Override
   final public void putBoolean(final boolean booleanValue)
   {
      handlePutBoolean(booleanValue);
   }


   @Override
   final public void putShort(final short shortValue)
   {
      handlePutShort(shortValue);
   }


   @Override
   final public void putInteger(final int integer)
   {
      handlePutInteger(integer);
   }


   @Override
   final public void putLong(final long longValue)
   {
      handlePutLong(longValue);
   }


   @Override
   final public void putUTF8EncodedString(final String string)
   {
      handlePutUTF8EncodedString(string);
   }


   @Override
   final public void advanceWritePosition()
   {
      handleAdvanceWritePosition();
   }


   @Override
   final public ReadableByteBuffer flipToReadableByteBuffer()
   {
      return handleFlipToReadableByteBuffer();
   }


   @Override
   final public void reclaim()
   {
      handleReclaim();
   }
}