/* Memos:
 * - getUTF8EncodedString() and putUTF8EncodedString() ripped almost verbatim from the server's WrappedByteBuffer and GrowableByteBuffer methods of the same name.
 *   We otherwise don't need any of the functionality that those components provide, and don't wish to hide our client ByteBuffers behind an extra layer of abstraction.
 *
 * - The encoded strings are prepended with the encoded length of the string following.
 */

package com.feedbactory.client.core.network;


import com.feedbactory.shared.network.BasicOperationStatus;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;


abstract public class NetworkUtilities
{
   static final private Charset UTF8Charset = Charset.forName("utf-8");


   private NetworkUtilities()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private boolean handleGetBoolean(final ByteBuffer buffer)
   {
      final byte booleanValue = buffer.get();

      switch (booleanValue)
      {
         case 0:
            return false;
         case 1:
            return true;
         default:
            throw new IllegalArgumentException("Invalid boolean byte value: " + booleanValue);
      }
   }


   static private String handleGetUTF8EncodedString(final ByteBuffer buffer)
   {
      int bufferBytesUntilEndOfString = buffer.getInt();

      /* Before we allocate space, check for the null case, otherwise ensure that the advertised string-byte length is (crucially from a malicious attack POV) no
       * longer than the remaining byte buffer. The less than -1 case is handled by the CharBuffer allocation which will throw an IllegalArgumentException,
       * which is probably more appropriate than a BufferUnderflowException, since the advertised String length is invalid.
       */
      if (bufferBytesUntilEndOfString == -1)
         return null;
      else if (bufferBytesUntilEndOfString > buffer.remaining())
         throw new BufferUnderflowException();

      final CharBuffer stringBuffer = CharBuffer.allocate(bufferBytesUntilEndOfString);
      final CharsetDecoder decoder = UTF8Charset.newDecoder();

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


   static private void handleDecoderResult(final CoderResult decoderResult, final CharBuffer stringBuffer)
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


   static private void handlePutUTF8EncodedString(final String string, final ByteBuffer buffer)
   {
      if (string == null)
         buffer.putInt(-1);
      else if (buffer.remaining() < 4)
         throw new BufferOverflowException();
      else
      {
         final CharsetEncoder encoder = UTF8Charset.newEncoder();

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


   static private void handleEncoderResult(final CoderResult encoderResult, final String string)
   {
      if (! encoderResult.isUnderflow())
      {
         if (encoderResult.isOverflow())
            throw new BufferOverflowException();
         else
            throw new IllegalStateException("Error during UTF8 encoding: " + string);
      }
   }


   static private BasicOperationStatus handleGetBasicOperationStatus(final ByteBuffer responseBuffer)
   {
      final byte operationStatusValue = responseBuffer.get();
      final BasicOperationStatus operationStatus = BasicOperationStatus.fromValue(operationStatusValue);
      if (operationStatus == null)
         throw new IllegalArgumentException("Invalid basic operation status: " + operationStatusValue);

      return operationStatus;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public boolean getBoolean(final ByteBuffer buffer)
   {
      return handleGetBoolean(buffer);
   }


   static public void putBoolean(final boolean booleanValue, final ByteBuffer buffer)
   {
      buffer.put(booleanValue ? ((byte) 1) : ((byte) 0));
   }


   static public String getUTF8EncodedString(final ByteBuffer buffer)
   {
      return handleGetUTF8EncodedString(buffer);
   }


   static public void putUTF8EncodedString(final String string, final ByteBuffer buffer)
   {
      handlePutUTF8EncodedString(string, buffer);
   }


   static public BasicOperationStatus getBasicOperationStatus(final ByteBuffer responseBuffer)
   {
      return handleGetBasicOperationStatus(responseBuffer);
   }
}