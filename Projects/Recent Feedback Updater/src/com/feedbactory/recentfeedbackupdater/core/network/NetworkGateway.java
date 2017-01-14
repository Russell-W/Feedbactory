/* Memos:
 * - Some code snippets taken almost verbatim or closely adapted from the Feedbactory client's NetworkUtilities and NetworkServiceManager.
 */

package com.feedbactory.recentfeedbackupdater.core.network;


import com.feedbactory.recentfeedbackupdater.RecentFeedbackUpdater;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.MessageType;
import com.feedbactory.shared.network.ClientCompatibilityStatus;
import com.feedbactory.shared.network.FeedbactoryApplicationServerStatus;
import com.feedbactory.shared.network.FeedbactoryNetworkConstants;
import com.feedbactory.shared.network.IPAddressStanding;
import com.feedbactory.shared.network.SessionRequestType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;


final public class NetworkGateway
{
   static final private InetSocketAddress ApplicationServerAddress;

   static final public int DefaultConnectionTimeoutMilliseconds = (RecentFeedbackUpdater.isDebugMode ? 0 : 12000);

   static final public int MaximumRequestSizeBytes = 8192;
   static final private int ResponseReadBufferSizeBytes = 8192;

   static final private int GeneralRequestHeaderSizeBytes = 20;
   static final private int SessionRequestHeaderSizeBytes = 1;

   static
   {
      // The test server and test recent feedback updater reside on the same machine.
      if (RecentFeedbackUpdater.isDevelopmentProfile || RecentFeedbackUpdater.isTestProfile)
         ApplicationServerAddress = new InetSocketAddress("127.0.0.1", FeedbactoryNetworkConstants.DefaultPortNumber);
      else if (RecentFeedbackUpdater.isProductionProfile)
         ApplicationServerAddress = new InetSocketAddress("127.0.0.1", FeedbactoryNetworkConstants.DefaultPortNumber);
      else
         throw new AssertionError("Unknown or misconfigured execution profile.");
   }


   private String handleGetUTF8EncodedString(final ByteBuffer buffer)
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


   private ProcessedRequestResult<ByteBuffer> handleSendNoSessionRequest(final ByteBuffer requestPayloadBuffer)
   {
      final ByteBuffer requestBuffer = ByteBuffer.allocate(requestPayloadBuffer.remaining() + GeneralRequestHeaderSizeBytes + SessionRequestHeaderSizeBytes);

      writeGeneralRequestHeader(requestBuffer);
      writeNoSessionRequestHeader(requestBuffer);
      requestBuffer.put(requestPayloadBuffer);

      requestBuffer.flip();

      final ByteBuffer responseBuffer;

      try
      {
         responseBuffer = sendRequestToServer(requestBuffer);

         /* If the request is erroneous or otherwise somehow leads to the server abruptly ending the processing,
          * the server response will be empty - try to gracefully handle it here.
          */
         if (! responseBuffer.hasRemaining())
            return ProcessedRequestResult.resultForNetworkRequestStatus(NetworkRequestStatus.BadRequest);
      }
      catch (final SocketTimeoutException socketTimeoutException)
      {
         return ProcessedRequestResult.resultForNetworkRequestStatus(NetworkRequestStatus.FailedTimeout);
      }
      catch (final ConnectException connectException)
      {
         return ProcessedRequestResult.resultForNetworkRequestStatus(NetworkRequestStatus.FailedNetworkOther);
      }
      catch (final IOException ioException)
      {
         return ProcessedRequestResult.resultForNetworkRequestStatus(NetworkRequestStatus.FailedNetworkOther);
      }

      return processResponse(responseBuffer);
   }


   private void writeGeneralRequestHeader(final ByteBuffer requestBuffer)
   {
      requestBuffer.putInt(FeedbactoryNetworkConstants.FeedbactoryRequestIdentifier);
      requestBuffer.putLong(RecentFeedbackUpdater.VersionID);
      requestBuffer.putLong(FeedbactoryConstants.NoTime);
   }


   private void writeNoSessionRequestHeader(final ByteBuffer requestBuffer)
   {
      requestBuffer.put(SessionRequestType.None.value);
   }


   private ByteBuffer sendRequestToServer(final ByteBuffer requestBuffer) throws IOException
   {
      try
      (
         final Socket activeRequestSocket = new Socket();
      )
      {
         /* Using raw Sockets rather than SocketChannel, as SO_TIMEOUT on the underlying socket is not supported by
          * reads on a SocketChannel. The advantage of using SocketChannels is that they can be interrupted (eg. on shutdown)
          * rather than just closed.
          */
         activeRequestSocket.setSoTimeout(DefaultConnectionTimeoutMilliseconds);
         activeRequestSocket.connect(ApplicationServerAddress, DefaultConnectionTimeoutMilliseconds);

         activeRequestSocket.getOutputStream().write(requestBuffer.array(), 0, requestBuffer.remaining());
         activeRequestSocket.shutdownOutput();

         final InputStream inputStream = activeRequestSocket.getInputStream();
         final byte[] readBuffer = new byte[ResponseReadBufferSizeBytes];
         int bytesRead;
         final ByteArrayOutputStream serverResponse = new ByteArrayOutputStream(ResponseReadBufferSizeBytes);

         while ((bytesRead = inputStream.read(readBuffer)) != -1)
            serverResponse.write(readBuffer, 0, bytesRead);

         activeRequestSocket.shutdownInput();

         return ByteBuffer.wrap(serverResponse.toByteArray()).asReadOnlyBuffer();
      }
   }


   private ProcessedRequestResult<ByteBuffer> processResponse(final ByteBuffer responseBuffer)
   {
      if (readIPAddressStanding(responseBuffer))
      {
         if (readApplicationServerStatus(responseBuffer))
         {
            if (processGeneralResponseHeader(responseBuffer))
               return new ProcessedRequestResult(NetworkRequestStatus.OK, responseBuffer);
            else
               return ProcessedRequestResult.resultForNetworkRequestStatus(NetworkRequestStatus.SupersededClientVersion);
         }
         else
         {
            // Application server is not available or busy.
            return ProcessedRequestResult.resultForNetworkRequestStatus(NetworkRequestStatus.ServerNotAvailable);
         }
      }
      else
      {
         // IP Address is temp banned or blacklisted, should never happen to this task but it's a possible response from the server.
         return ProcessedRequestResult.resultForNetworkRequestStatus(NetworkRequestStatus.IPBlocked);
      }
   }


   private boolean readIPAddressStanding(final ByteBuffer responseBuffer)
   {
      final byte ipAddressStandingValue = responseBuffer.get();
      final IPAddressStanding ipAddressStanding = IPAddressStanding.fromValue(ipAddressStandingValue);
      if (ipAddressStanding == null)
         throw new IllegalArgumentException("Invalid IP address standing value: " + ipAddressStandingValue);

      return (ipAddressStanding == IPAddressStanding.OK);
   }


   private boolean readApplicationServerStatus(final ByteBuffer responseBuffer)
   {
      final byte serverStatusValue = responseBuffer.get();
      final FeedbactoryApplicationServerStatus serverStatus = FeedbactoryApplicationServerStatus.fromValue(serverStatusValue);

      if (serverStatus == null)
         throw new IllegalArgumentException("Invalid Feedbactory application server status value: " + serverStatusValue);
      else
         return (serverStatus == FeedbactoryApplicationServerStatus.Available);
   }


   private boolean processGeneralResponseHeader(final ByteBuffer responseBuffer)
   {
      if (processClientCompatibilityStatus(responseBuffer))
      {
         // Read server time.
         responseBuffer.getLong();

         final byte messageTypeValue = responseBuffer.get();
         final MessageType messageType = MessageType.fromValue(messageTypeValue);
         if (messageType == null)
            throw new IllegalArgumentException("Invalid message type value: " + messageTypeValue);

         if (messageType != MessageType.NoMessage)
            getUTF8EncodedString(responseBuffer);

         return true;
      }

      return false;
   }


   private boolean processClientCompatibilityStatus(final ByteBuffer responseBuffer)
   {
      final byte clientCompatibilityStatusValue = responseBuffer.get();
      final ClientCompatibilityStatus clientCompatibilityStatus = ClientCompatibilityStatus.fromValue(clientCompatibilityStatusValue);
      return ((clientCompatibilityStatus == ClientCompatibilityStatus.UpToDate) || (clientCompatibilityStatus == ClientCompatibilityStatus.UpdateAvailable));
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public String getUTF8EncodedString(final ByteBuffer buffer)
   {
      return handleGetUTF8EncodedString(buffer);
   }


   final public ProcessedRequestResult<ByteBuffer> sendNoSessionRequest(final ByteBuffer requestPayloadBuffer)
   {
      return handleSendNoSessionRequest(requestPayloadBuffer);
   }
}