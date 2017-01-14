/* Memos:
 * - All of the mutating methods produce undefined results once the flipToReadableByteBuffer() method has been invoked.
 */

package com.feedbactory.server.network.component.buffer;


import java.nio.ByteBuffer;


public interface WritableByteBuffer extends ServerByteBuffer
{
   public void ensureRemainingCapacity(final int capacity);

   public void put(final byte byteToPut);
   public void put(final byte[] bytes);
   public void put(final ByteBuffer byteBuffer);
   public void putBoolean(final boolean booleanValue);
   public void putShort(final short shortValue);
   public void putInteger(final int integer);
   public void putLong(final long longValue);
   public void putUTF8EncodedString(final String string);
   public void advanceWritePosition();

   public ReadableByteBuffer flipToReadableByteBuffer();
}