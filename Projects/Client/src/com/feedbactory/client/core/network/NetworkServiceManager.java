/* Memos:
 * - On deciding whether or not to consume general responses when there is a network timeout or transmission error (note: not coding error): I'm inclined to report the
 *   error to the operations manager and in turn UI manager so that there is consistency with the display of those errors. However I also think it's a good idea to NOT consume
 *   the event in this instance, since it's handy for the caller to be able to attach an error or successful status to its data elements. Visually this may be a simple
 *   'error while retrieving feedback' type notice for an individual data element. Since the top level network error notice has been displayed to the user, they have already
 *   been made aware that there was an error for that item.
 * 
 *   Consuming events is probably more appropriate for events that impose an immediate app-wide effect, such as server unavailable or busy, or a superceded client version.
 *
 * - This class limits requests to one at a time, which might be handy given the server constraints.
 */


package com.feedbactory.client.core.network;


import com.feedbactory.client.core.FeedbactoryClientConstants;
import com.feedbactory.client.core.OperationsManager;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.Message;
import com.feedbactory.shared.MessageType;
import com.feedbactory.shared.network.ClientCompatibilityStatus;
import com.feedbactory.shared.network.FeedbactoryApplicationServerStatus;
import com.feedbactory.shared.network.FeedbactoryNetworkConstants;
import com.feedbactory.shared.network.IPAddressStanding;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;


final public class NetworkServiceManager
{
   static final private InetSocketAddress ApplicationServerAddress = new InetSocketAddress(ClientNetworkConstants.FeedbactoryApplicationServer, FeedbactoryNetworkConstants.DefaultPortNumber);

   static final public int DefaultConnectionTimeoutMilliseconds = (FeedbactoryClientConstants.IsDebugMode ? 0 : 12000);

   static final private int GeneralRequestHeaderSizeBytes = 20;

   static final private int ResponseReadBufferSizeBytes = 8192;

   final private OperationsManager operationsManager;

   // Ensure only one request at a time.
   final private Object requestLock = new Object();
   volatile private Socket activeRequestSocket;

   private FeedbactoryApplicationServerStatus serverAvailability;
   private ClientCompatibilityStatus lastClientCompatibilityStatus;
   private long lastRequestServerTime;
   private long serverTimeDifference;

   volatile private boolean isShutdown;


   public NetworkServiceManager(final OperationsManager operationsManager)
   {
      this.operationsManager = operationsManager;

      initialise();
   }


   private void initialise()
   {
      synchronized (requestLock)
      {
         lastRequestServerTime = FeedbactoryConstants.NoTime;
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private boolean handleHasSentHandshakeRequest()
   {
      synchronized (requestLock)
      {
         return (lastRequestServerTime != FeedbactoryConstants.NoTime);
      }
   }


   private NetworkRequestStatus handleCheckSendHandshakeRequest(final boolean isPriorityRequest)
   {
      synchronized (requestLock)
      {
         if (! hasSentHandshakeRequest())
         {
            final ProcessedRequestBufferResponse response = checkSendRequest(ByteBuffer.allocate(0), DefaultConnectionTimeoutMilliseconds, isPriorityRequest);
            if (response.requestStatus == NetworkRequestStatus.OK)
               operationsManager.processFeedbackHandshake(response.data);

            return response.requestStatus;
         }
         else
            return NetworkRequestStatus.OK;
      }
   }


   private long handleGetApproximateServerTimeDifference()
   {
      synchronized (requestLock)
      {
         if (hasSentHandshakeRequest())
            return serverTimeDifference;
         else
            throw new IllegalStateException("Cannot calculate server time difference until after handshake request has been sent.");
      }
   }


   private long handleGetApproximateServerTime()
   {
      synchronized (requestLock)
      {
         if (hasSentHandshakeRequest())
            return System.currentTimeMillis() + serverTimeDifference;
         else
            throw new IllegalStateException("Cannot calculate server time until after handshake request has been sent.");
      }
   }


   private ProcessedRequestBufferResponse handleSendRequest(final ByteBuffer requestPayloadBuffer, final int timeoutMilliseconds, final boolean isPriorityRequest)
   {
      synchronized (requestLock)
      {
         // Send through a separate handshake request if it hasn't been performed already.
         final NetworkRequestStatus checkHandshakeRequestStatus = handleCheckSendHandshakeRequest(isPriorityRequest);
         if (checkHandshakeRequestStatus != NetworkRequestStatus.OK)
            return new ProcessedRequestBufferResponse(checkHandshakeRequestStatus);

         return checkSendRequest(requestPayloadBuffer, timeoutMilliseconds, isPriorityRequest);
      }
   }


   private ProcessedRequestBufferResponse checkSendRequest(final ByteBuffer requestPayloadBuffer, final int timeoutMilliseconds, final boolean isPriorityRequest)
   {
      try
      {
         // Save a reference to the active network request socket, so that it may be closed by a shutdown task.
         activeRequestSocket = new Socket();

         /* Bail out if the service has been flagged as shutdown and the request is not high priority. This seems an unusual implementation to allow
          * requests after shutdown, but it allows the shutdown task to disconnect and disallow further requests before sending one or more final
          * important system requests, eg. sign out of non-persistent session.
          * It is assumed that no high priority requests will be sent before shutdown has been initiated.
          */
         if ((! isPriorityRequest) && isShutdown)
            return new ProcessedRequestBufferResponse(NetworkRequestStatus.Consumed);

         return processSendRequest(requestPayloadBuffer, timeoutMilliseconds);
      }
      finally
      {
         activeRequestSocket = null;
      }
   }


   private ProcessedRequestBufferResponse processSendRequest(final ByteBuffer requestPayloadBuffer, final int timeoutMilliseconds)
   {
      final ByteBuffer requestBuffer = ByteBuffer.allocate(requestPayloadBuffer.remaining() + GeneralRequestHeaderSizeBytes);

      writeGeneralRequestHeader(requestBuffer);
      requestBuffer.put(requestPayloadBuffer);

      requestBuffer.flip();

      final ByteBuffer responseBuffer;

      try
      {
         responseBuffer = sendRequestToServer(requestBuffer, timeoutMilliseconds);

         /* If the request is erroneous or otherwise somehow leads to the server abruptly ending the processing,
          * the server response will be empty - try to gracefully handle it here.
          */
         if (! responseBuffer.hasRemaining())
         {
            operationsManager.reportRequestError();
            return new ProcessedRequestBufferResponse(NetworkRequestStatus.Consumed);
         }
      }
      catch (final SocketTimeoutException socketTimeoutException)
      {
         operationsManager.reportNetworkFailure(NetworkRequestStatus.FailedTimeout);
         return new ProcessedRequestBufferResponse(NetworkRequestStatus.FailedTimeout);
      }
      catch (final ConnectException connectException)
      {
         operationsManager.reportNetworkFailure(NetworkRequestStatus.FailedNetworkOther);
         return new ProcessedRequestBufferResponse(NetworkRequestStatus.FailedNetworkOther);
      }
      catch (final IOException ioException)
      {
         if (! isShutdown)
         {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Network service manager send request exception.", ioException);

            operationsManager.reportNetworkFailure(NetworkRequestStatus.FailedNetworkOther);
            return new ProcessedRequestBufferResponse(NetworkRequestStatus.FailedNetworkOther);
         }
         else
            return new ProcessedRequestBufferResponse(NetworkRequestStatus.Consumed);
      }

      return processResponse(responseBuffer);
   }


   private void writeGeneralRequestHeader(final ByteBuffer requestBuffer)
   {
      requestBuffer.putInt(FeedbactoryNetworkConstants.FeedbactoryRequestIdentifier);
      requestBuffer.putLong(FeedbactoryClientConstants.VersionID);
      requestBuffer.putLong(lastRequestServerTime);
   }


   private ProcessedRequestBufferResponse processResponse(final ByteBuffer responseBuffer)
   {
      final IPAddressStanding ipAddressStanding = readIPAddressStanding(responseBuffer);
      if (ipAddressStanding == IPAddressStanding.OK)
      {
         final ServerAvailability serverAvailabilityResponse = readApplicationServerStatus(responseBuffer);

         if (serverAvailabilityResponse.serverStatus == FeedbactoryApplicationServerStatus.Available)
         {
            // If the server status was available before, there's no need to inform the op manager of any change.
            if (serverAvailability != FeedbactoryApplicationServerStatus.Available)
            {
               this.serverAvailability = FeedbactoryApplicationServerStatus.Available;
               operationsManager.reportApplicationServerStatusUpdated(serverAvailabilityResponse);
            }

            return processGeneralResponseHeader(responseBuffer);
         }
         else
         {
            this.serverAvailability = serverAvailabilityResponse.serverStatus;

            /* The availability status may not have changed but the associated message may have. In any case the UI may have dismissed any unavailable or busy notice
             * since the last signal was passed to it, so here is a reminder that the server is still either unavailable or busy.
             */
            operationsManager.reportApplicationServerStatusUpdated(serverAvailabilityResponse);
            return new ProcessedRequestBufferResponse(NetworkRequestStatus.Consumed);
         }
      }
      else
      {
         operationsManager.reportIPAddressTemporarilyBlocked();
         return new ProcessedRequestBufferResponse(NetworkRequestStatus.Consumed);
      }
   }


   private IPAddressStanding readIPAddressStanding(final ByteBuffer responseBuffer)
   {
      final byte ipAddressStandingValue = responseBuffer.get();
      final IPAddressStanding ipAddressStanding = IPAddressStanding.fromValue(ipAddressStandingValue);
      if (ipAddressStanding == null)
         throw new IllegalArgumentException("Invalid IP address standing value: " + ipAddressStandingValue);

      return ipAddressStanding;
   }


   private ServerAvailability readApplicationServerStatus(final ByteBuffer responseBuffer)
   {
      final byte serverStatusValue = responseBuffer.get();
      final FeedbactoryApplicationServerStatus serverStatus = FeedbactoryApplicationServerStatus.fromValue(serverStatusValue);

      if (serverStatus == null)
         throw new IllegalArgumentException("Invalid Feedbactory application server status value: " + serverStatusValue);
      else if (serverStatus == FeedbactoryApplicationServerStatus.NotAvailable)
      {
         final Message message;

         final byte messageTypeValue = responseBuffer.get();
         final MessageType messageType = MessageType.fromValue(messageTypeValue);
         if (messageType == null)
            throw new IllegalArgumentException("Invalid message type value: " + messageTypeValue);

         if (messageType != MessageType.NoMessage)
            message = new Message(messageType, NetworkUtilities.getUTF8EncodedString(responseBuffer));
         else
            message = Message.NoMessage;

         return new ServerAvailability(serverStatus, message);
      }
      else
         return new ServerAvailability(serverStatus);
   }


   private ProcessedRequestBufferResponse processGeneralResponseHeader(final ByteBuffer responseBuffer)
   {
      processClientCompatibilityStatus(responseBuffer);
      if ((lastClientCompatibilityStatus == ClientCompatibilityStatus.UpToDate) || (lastClientCompatibilityStatus == ClientCompatibilityStatus.UpdateAvailable))
      {
         lastRequestServerTime = responseBuffer.getLong();
         serverTimeDifference = (lastRequestServerTime - System.currentTimeMillis());

         processBroadcastMessage(responseBuffer);

         return new ProcessedRequestBufferResponse(NetworkRequestStatus.OK, responseBuffer);
      }
      else
         return new ProcessedRequestBufferResponse(NetworkRequestStatus.Consumed);
   }


   private void processClientCompatibilityStatus(final ByteBuffer responseBuffer)
   {
      final byte clientCompatibilityStatusValue = responseBuffer.get();
      final ClientCompatibilityStatus clientCompatibilityStatus = ClientCompatibilityStatus.fromValue(clientCompatibilityStatusValue);
      if (clientCompatibilityStatus != null)
      {
         if (clientCompatibilityStatus != lastClientCompatibilityStatus)
         {
            lastClientCompatibilityStatus = clientCompatibilityStatus;
            operationsManager.reportClientCompatibilityUpdated(clientCompatibilityStatus);
         }
      }
      else
         throw new IllegalArgumentException("Invalid client compatibility status value: " + clientCompatibilityStatusValue);
   }


   private void processBroadcastMessage(final ByteBuffer responseBuffer)
   {
      final byte messageTypeValue = responseBuffer.get();
      final MessageType messageType = MessageType.fromValue(messageTypeValue);
      if (messageType == null)
         throw new IllegalArgumentException("Invalid message type value: " + messageTypeValue);

      if (messageType != MessageType.NoMessage)
         operationsManager.reportGeneralBroadcastMessage(new Message(messageType, NetworkUtilities.getUTF8EncodedString(responseBuffer)));
   }


   private ByteBuffer sendRequestToServer(final ByteBuffer requestBuffer, final int timeoutMilliseconds) throws IOException
   {
      try
      {
         /* Using raw Sockets rather than SocketChannel, as SO_TIMEOUT on the underlying socket is not supported by
          * reads on a SocketChannel. The advantage of using SocketChannels is that they can be interrupted (eg. on shutdown)
          * rather than just closed.
          */
         activeRequestSocket.setSoTimeout(timeoutMilliseconds);
         activeRequestSocket.connect(ApplicationServerAddress, timeoutMilliseconds);

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
      finally
      {
         activeRequestSocket.close();
      }
   }


   private void handleShutdown()
   {
      isShutdown = true;

      final Socket activeRequestSocketCopy = activeRequestSocket;
      if (activeRequestSocketCopy != null)
      {
         try
         {
            activeRequestSocketCopy.close();
         }
         catch (final IOException ioException)
         {
            // Ignore the exception during shutdown.
         }
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public boolean hasSentHandshakeRequest()
   {
      return handleHasSentHandshakeRequest();
   }


   final public NetworkRequestStatus checkSendHandshakeRequest()
   {
      return handleCheckSendHandshakeRequest(false);
   }


   final public long getApproximateServerTimeDifference()
   {
      return handleGetApproximateServerTimeDifference();
   }


   final public long getApproximateServerTime()
   {
      return handleGetApproximateServerTime();
   }


   final public ProcessedRequestBufferResponse sendRequest(final ByteBuffer requestPayloadBuffer)
   {
      return sendRequest(requestPayloadBuffer, DefaultConnectionTimeoutMilliseconds, false);
   }


   final public ProcessedRequestBufferResponse sendRequest(final ByteBuffer requestPayloadBuffer, final int timeoutMilliseconds, final boolean isPriorityRequest)
   {
      return handleSendRequest(requestPayloadBuffer, timeoutMilliseconds, isPriorityRequest);
   }


   final public void shutdown()
   {
      handleShutdown();
   }
}