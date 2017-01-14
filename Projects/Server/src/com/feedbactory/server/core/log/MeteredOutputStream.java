/* Memos:
 * - Without using a buffered stream (either inherited or chained as it is here) between the logger and the file, there is a lot of small traffic passing through.
 *   I think the chained approach with a buffered stream above this metered output stream is slightly more optimal as it prevents the unnecessary updates of
 *   bytesWritten by frequent small increments.
 */

package com.feedbactory.server.core.log;


import java.io.IOException;
import java.io.OutputStream;


final public class MeteredOutputStream extends OutputStream
{
   final private OutputStream outputStream;
   private int bytesWritten;


   public MeteredOutputStream(final OutputStream outputStream, final int bytesWritten)
   {
      this.outputStream = outputStream;
      this.bytesWritten = bytesWritten;
   }


   @Override
   final public void write(final int value) throws IOException
   {
      outputStream.write(value);
      bytesWritten ++;
   }


   @Override
   final public void write(final byte[] bytes) throws IOException
   {
      outputStream.write(bytes);
      bytesWritten += bytes.length;
   }


   @Override
   final public void write(final byte[] bytes, final int offset, final int length) throws IOException
   {
      outputStream.write(bytes, offset, length);
      bytesWritten += length;
   }


   @Override
   final public void flush() throws IOException
   {
      outputStream.flush();
   }


   @Override
   final public void close() throws IOException
   {
      outputStream.close();
   }


   final int getBytesWritten()
   {
      return bytesWritten;
   }
}