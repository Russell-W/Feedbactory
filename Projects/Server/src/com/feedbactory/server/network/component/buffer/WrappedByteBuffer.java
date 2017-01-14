
package com.feedbactory.server.network.component.buffer;


import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;


public class WrappedByteBuffer implements ReadableByteBuffer
{
   final private ByteBuffer buffer;


   public WrappedByteBuffer(final ByteBuffer buffer)
   {
      this.buffer = buffer;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private String handleGetUTF8EncodedString()
   {
      int bufferBytesUntilEndOfString = getInteger();

      /* Before we allocate space, check for the null case, otherwise ensure that the advertised string-byte length is (crucially from a malicious attack POV) no
       * longer than the remaining byte buffer. The less than -1 case is handled by the CharBuffer allocation which will throw an IllegalArgumentException,
       * which is probably more appropriate than a BufferUnderflowException, since the advertised String length is invalid.
       */
      if (bufferBytesUntilEndOfString == -1)
         return null;
      else if (bufferBytesUntilEndOfString > getRemaining())
         throw new BufferUnderflowException();

      final CharBuffer stringBuffer = CharBuffer.allocate(bufferBytesUntilEndOfString);
      final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();

      /* We need to impose a temporary limit on the buffer to ensure that the read doesn't overshoot
       * the end of the string into the data following. Once finished we can restore the original buffer limit.
       */
      final int originalLimit = buffer.limit();
      buffer.limit(buffer.position() + bufferBytesUntilEndOfString);

      handleDecoderResult(decoder.decode(buffer, stringBuffer, true), stringBuffer);
      handleDecoderResult(decoder.flush(stringBuffer), stringBuffer);

      buffer.limit(originalLimit);

      stringBuffer.flip();

      return stringBuffer.toString();
   }


   private void handleDecoderResult(final CoderResult decoderResult, final CharBuffer stringBuffer)
   {
      if (! decoderResult.isUnderflow())
      {
         stringBuffer.position(0);

         if (decoderResult.isOverflow())
            throw new BufferOverflowException();
         else
            throw new IllegalStateException("Error during UTF8 string decoding, error occurred after: " + stringBuffer);
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
   final public int getLimit()
   {
      return buffer.limit();
   }


   @Override
   final public void setLimit(final int newLimit)
   {
      buffer.limit(newLimit);
   }


   @Override
   final public int getRemaining()
   {
      return buffer.remaining();
   }


   @Override
   final public byte get()
   {
      return buffer.get();
   }


   @Override
   final public void get(final byte[] bytes)
   {
      buffer.get(bytes);
   }


   @Override
   final public boolean getBoolean()
   {
      return (buffer.get() != 0);
   }


   @Override
   final public short getShort()
   {
      return buffer.getShort();
   }


   @Override
   final public int getInteger()
   {
      return buffer.getInt();
   }


   @Override
   final public long getLong()
   {
      return buffer.getLong();
   }


   @Override
   final public String getUTF8EncodedString()
   {
      return handleGetUTF8EncodedString();
   }


   @Override
   final public boolean advanceReadPosition()
   {
      return buffer.hasRemaining();
   }


   @Override
   public void reclaim()
   {
   }
}