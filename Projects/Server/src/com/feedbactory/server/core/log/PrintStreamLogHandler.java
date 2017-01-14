
package com.feedbactory.server.core.log;


import com.feedbactory.server.network.application.RequestUserSession;
import com.feedbactory.server.network.application.SessionEncryption;
import com.feedbactory.server.network.component.ClientIO;
import com.feedbactory.server.network.component.ClientNetworkID;
import com.feedbactory.server.network.component.buffer.ReadableByteBuffer;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;


abstract class PrintStreamLogHandler implements LogOutputHandler
{
   // Must be cleanly divisible by 3 for the encoded buffer size calculation to ensure that enough space is allocated to contain the Base64 encoded buffer output.
   static final private int MaximumSingleByteBufferWriteSizeBytes = 512;

   final private byte[] encodedBuffer = new byte[(MaximumSingleByteBufferWriteSizeBytes * 4) / 3];


   abstract protected PrintStream getPrintStream();


   private String encodeBytes(final byte[] bytes)
   {
      final int encodingLength = Base64.getEncoder().encode(bytes, encodedBuffer);
      return new String(encodedBuffer, 0, encodingLength, StandardCharsets.UTF_8);
   }


   private void handleLogSystemEvent(final SystemEvent event)
   {
      final PrintStream printStream = getPrintStream();

      printStream.format("%-20.20s%tA %<td/%<tm/%<tY %<tH:%<tM:%<tS%<tp%n", "Date:", new Date());
      printStream.format("%-20.20s%s%n", "Event type:", "System");
      printStream.format("%-20.20s%s%n", "Level:", event.level);
      printStream.format("%-20.20s%s%n", "Originating class:", event.originatingClass.getName());
      printStream.format("%-20.20s%s%n", "Message:", event.message);

      if (event.throwable != null)
      {
         printStream.println("Stack trace:");
         event.throwable.printStackTrace(printStream);
         printStream.println();
      }

      writeClientNetworkID(event.getClientNetworkID());
      writeReadableByteBuffer("Raw request", event.getRequestBuffer());

      // Suppress the output of the raw request buffer to the log if the decrypted request buffer is being written.
      writeClientIO(event.getClientIO(), event.getSessionEncryption() == null);
      writeSecretKeySpec(event.getSecretKeySpec());
      writeSessionEncryption(event.getSessionEncryption());
      printStream.println("--------------------------------------------------------------------");
   }


   final void writeClientNetworkID(final ClientNetworkID clientNetworkID)
   {
      if ((clientNetworkID != null) && (clientNetworkID.inetSocketAddress != null))
         getPrintStream().format("%-20.20s%s%n", "IP Address:", clientNetworkID.inetSocketAddress.getAddress().toString());
   }


   final void writeReadableByteBuffer(final String label, final ReadableByteBuffer byteBuffer)
   {
      if (byteBuffer != null)
      {
         final PrintStream printStream = getPrintStream();

         final int originalPosition = byteBuffer.getPosition();
         byteBuffer.setPosition(0);

         if (byteBuffer.getRemaining() <= MaximumSingleByteBufferWriteSizeBytes)
         {
            final byte[] byteBufferOutput = new byte[byteBuffer.getRemaining()];
            byteBuffer.get(byteBufferOutput);

            printStream.format("%-20.20s%s%n", label + ':', encodeBytes(byteBufferOutput));
            printStream.format("(%d bytes logged)%n", byteBufferOutput.length);

            byteBuffer.setPosition(originalPosition);
         }
         else
         {
            // Limit the output to the first MaximumSingleByteBufferWriteSizeBytes in the buffer.
            final int originalLimit = byteBuffer.getLimit();
            byteBuffer.setLimit(MaximumSingleByteBufferWriteSizeBytes);

            final byte[] byteBufferOutput = new byte[byteBuffer.getRemaining()];
            byteBuffer.get(byteBufferOutput);

            printStream.format("%-20.20s%s%n", label + ':', encodeBytes(byteBufferOutput));
            printStream.format("(first %d bytes logged, %d bytes total)%n", byteBufferOutput.length, originalLimit);

            byteBuffer.setPosition(originalPosition);
            byteBuffer.setLimit(originalLimit);
         }
      }
   }


   final void writeClientIO(final ClientIO clientIO, final boolean writeByteBufferOutput)
   {
      if (clientIO != null)
      {
         writeClientNetworkID(clientIO.networkID);
         writeReadableByteBuffer("Raw request", writeByteBufferOutput ? clientIO.requestBuffer : null);
      }
   }


   private void writeSecretKeySpec(final SecretKeySpec secretKeySpec)
   {
      if (secretKeySpec != null)
         getPrintStream().format("%-20.20s%s%n", "Secret key:", encodeBytes(secretKeySpec.getEncoded()));
   }


   private void writeSessionEncryption(final SessionEncryption sessionEncryption)
   {
      if (sessionEncryption != null)
      {
         writeReadableByteBuffer("Decrypted request", sessionEncryption.decryptedRequestBuffer);
         getPrintStream().format("%-20.20s%s%n", "Response IV:", encodeBytes(sessionEncryption.encryptedResponseInitialisationVector.getIV()));
         writeSecretKeySpec(sessionEncryption.secretKeySpec);
         writeReadableByteBuffer("Response", sessionEncryption.toBeEncryptedResponseBuffer.flipToReadableByteBuffer());
      }
   }


   private void handleLogSecurityEvent(final SecurityEvent event)
   {
      final PrintStream printStream = getPrintStream();

      printStream.format("%-20.20s%tA %<td/%<tm/%<tY %<tH:%<tM:%<tS%<tp%n", "Date:", new Date());
      printStream.format("%-20.20s%s%n", "Event type:", "Security");
      printStream.format("%-20.20s%s%n", "Level:", event.level);
      printStream.format("%-20.20s%s%n", "Originating class:", event.originatingClass.getName());
      printStream.format("%-20.20s%s%n", "Message:", event.message);

      /* A SecurityEvent object will have one but not both the standalone ClientIO or RequestUserSession object (which also contains a reference to the ClientIO).
       * This means that if the standalone ClientIO object is present, its attached request buffer should be written.
       */
      writeClientIO(event.clientIO, true);
      writeRequestUserSession(event.userSession);
      printStream.println();
   }


   private void writeRequestUserSession(final RequestUserSession userSession)
   {
      if (userSession != null)
      {
         final PrintStream printStream = getPrintStream();

         // Don't write the raw request buffer attached to the ClientIO, since the decrypted version is available.
         writeClientIO(userSession.clientIO, false);
         writeReadableByteBuffer("Decrypted request", userSession.requestBuffer);
         writeReadableByteBuffer("Response", userSession.responseBuffer.flipToReadableByteBuffer());
         printStream.format("%-20.20s%s%n", "Session request:", userSession.sessionRequestType.toString());
         printStream.format("%-20.20s%s%n", "Session ID:", encodeBytes(userSession.sessionID.asByteArray()));
         printStream.format("%-20.20s%s%n", "Account ID:", userSession.account.getID().toString());
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   synchronized public void logSystemEvent(final SystemEvent event)
   {
      handleLogSystemEvent(event);
   }


   @Override
   synchronized public void logSecurityEvent(final SecurityEvent event)
   {
      handleLogSecurityEvent(event);
   }


   @Override
   public void shutdown()
   {
   }
}