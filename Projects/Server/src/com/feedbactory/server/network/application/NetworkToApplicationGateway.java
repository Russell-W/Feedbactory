
package com.feedbactory.server.network.application;


import com.feedbactory.server.network.component.ClientIO;
import com.feedbactory.server.network.component.EntityID;
import com.feedbactory.server.core.TimestampedMessage;
import com.feedbactory.server.useraccount.UserAccountNetworkGateway;
import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.FeedbactorySecurityException;
import com.feedbactory.server.core.log.SystemLogLevel;
import com.feedbactory.server.core.log.SystemEvent;
import com.feedbactory.server.core.log.SecurityLogLevel;
import com.feedbactory.server.network.component.buffer.ReadableByteBuffer;
import com.feedbactory.server.feedback.FeedbackManager;
import com.feedbactory.server.feedback.FeedbackNetworkGatewayManager;
import com.feedbactory.server.network.application.UserAccountSessionManager.SessionManagerMetrics;
import com.feedbactory.server.network.application.UserAccountSessionManager.AccountSessionMetrics;
import com.feedbactory.server.network.application.NetworkServiceManager.BufferProviderInterface;
import com.feedbactory.server.useraccount.*;
import com.feedbactory.server.useraccount.UserAccountNetworkGateway.IPAddressFailedAuthenticationMetrics;
import com.feedbactory.server.useraccount.UserAccountNetworkGateway.EmailBlockedIPAddressMetrics;
import com.feedbactory.server.useraccount.UserAccountNetworkGateway.IPAuthenticationMetrics;
import com.feedbactory.shared.network.FeedbactorySessionConstants;
import com.feedbactory.shared.network.RequestGatewayIdentifier;
import com.feedbactory.shared.network.AuthenticationStatus;
import com.feedbactory.shared.Message;
import com.feedbactory.shared.network.SessionRequestType;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import javax.crypto.*;


final public class NetworkToApplicationGateway
{
   final private ClientRequestHeaderHandler headerHandler = new ClientRequestHeaderHandler();

   final private UserAccountManager accountManager;
   final private UserAccountNetworkGateway accountNetworkGateway;

   final private UserAccountSessionManager sessionManager;
   final private SessionManagerInterface sessionManagerInterface = new SessionManagerInterface();

   final private FeedbackNetworkGatewayManager feedbackNetworkGateway;


   NetworkToApplicationGateway(final UserAccountManager accountManager, final FeedbackManager feedbackManager, final BufferProviderInterface bufferProvider)
   {
      this.accountManager = accountManager;

      accountNetworkGateway = new UserAccountNetworkGateway(accountManager, sessionManagerInterface);

      feedbackNetworkGateway = new FeedbackNetworkGatewayManager(feedbackManager);

      sessionManager = new UserAccountSessionManager(accountNetworkGateway, bufferProvider);
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final public class SessionManagerInterface
   {
      final public void clearAccountSessions(final FeedbactoryUserAccount account)
      {
         sessionManager.clearAccountSessions(account);
      }


      final public void clearAccountSessions(final FeedbactoryUserAccount account, final EntityID exceptedSessionID)
      {
         sessionManager.clearAccountSessions(account, exceptedSessionID);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private ProcessedOperationStatus handleProcessClientRequest(final ClientIO clientIO)
   {
      final RequestHeaderResult headerResult = headerHandler.processRequestHeader(clientIO);

      if (headerResult == RequestHeaderResult.BadHeader)
         return ProcessedOperationStatus.ErroneousRequest;
      else if (headerResult == RequestHeaderResult.SupersededClient)
         return ProcessedOperationStatus.OK;
      else if (clientIO.requestBuffer.getRemaining() == 0) // It's possible for a non-handshake header to arrive here too...
         return processHandshakeRequest(clientIO);

      final RequestSessionResult requestSessionResult = sessionManager.processSession(clientIO);

      switch (requestSessionResult.requestType)
      {
         case None:
            return processGatewayRequest(new RequestUserSession(clientIO, clientIO.requestBuffer, clientIO.responseBuffer, SessionRequestType.None));

         case InitiateSession:
            return processInitiateSessionRequest(clientIO, requestSessionResult.encryption);

         case EndSession:
         case ResumeSession:
            return processEndOfSessionRequest(clientIO, requestSessionResult);

         case RegularSessionRequest:
            return processRegularSessionRequest(clientIO, requestSessionResult);

         case EncryptedSessionRequest:
            return processEncryptedSessionGatewayRequest(clientIO, requestSessionResult);

         default:
            throw new AssertionError("Unhandled session request type in network to application gateway: " + requestSessionResult.requestType);
      }
   }


   private ProcessedOperationStatus processHandshakeRequest(final ClientIO clientIO)
   {
      /* Write the feedback handshake data, since that's all there is for now.
       * In the future I may wish to revise this format to precede each handshake segment by a RequestGatewayIdentifier byte,
       * eg. one for feedback, one for accounts, one for core, etc.
       */
      feedbackNetworkGateway.processHandshakeRequest(clientIO.responseBuffer);

      return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus processGatewayRequest(final RequestUserSession userSession)
   {
      final byte gatewayIdentifierValue = userSession.requestBuffer.get();
      final RequestGatewayIdentifier gatewayIdentifier = RequestGatewayIdentifier.fromValue(gatewayIdentifierValue);

      if (gatewayIdentifier == null)
      {
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), "Invalid request gateway identifier value: " + gatewayIdentifierValue, userSession);
         return ProcessedOperationStatus.ErroneousRequest;
      }

      try
      {
         switch (gatewayIdentifier)
         {
            case Account:
               return accountNetworkGateway.processAccountRequest(userSession);

            case Feedback:
               return feedbackNetworkGateway.processFeedbackRequest(userSession);

            default:
               throw new AssertionError("Unhandled request gateway identifier in network to application gateway: " + gatewayIdentifier);
         }
      }
      catch (final FeedbactorySecurityException securityException)
      {
         // Any security exception is caught and processed here, as this marks the top level handler where the RequestUserSession object is available for logging.
         FeedbactoryLogger.logSecurityEvent(securityException.securityEventLevel, securityException.originatingClass, securityException.getMessage(), userSession);
         return ProcessedOperationStatus.ErroneousRequest;
      }
   }


   private ProcessedOperationStatus processInitiateSessionRequest(final ClientIO clientIO, final SessionEncryption sessionEncryption)
   {
      try
      {
         return transferEncryptedBufferToResponse(clientIO, sessionEncryption);
      }
      finally
      {
         sessionEncryption.decryptedRequestBuffer.reclaim();
         sessionEncryption.toBeEncryptedResponseBuffer.reclaim();
      }
   }


   private ProcessedOperationStatus transferEncryptedBufferToResponse(final ClientIO clientIO, final SessionEncryption sessionEncryption)
   {
      final int writeEndPosition = sessionEncryption.toBeEncryptedResponseBuffer.getPosition();

      try
      {
         if (writeEndPosition != 0)
         {
            final Cipher cipher = Cipher.getInstance(FeedbactorySessionConstants.SecretKeyEncryptionAlgorithm);

            cipher.init(Cipher.ENCRYPT_MODE, sessionEncryption.secretKeySpec, sessionEncryption.encryptedResponseInitialisationVector);

            final ReadableByteBuffer toBeEncryptedResponseBuffer = sessionEncryption.toBeEncryptedResponseBuffer.flipToReadableByteBuffer();

            clientIO.responseBuffer.ensureRemainingCapacity(cipher.getOutputSize(toBeEncryptedResponseBuffer.getRemaining()));

            cipher.doFinal(toBeEncryptedResponseBuffer.getActiveBuffer(), clientIO.responseBuffer.getActiveBuffer());
         }

         return ProcessedOperationStatus.OK;
      }
      catch (final GeneralSecurityException generalSecurityException)
      {
         /* Unlike errors occurring during the decryption phase of a user request which we classify as erroneous client requests, errors occurring here
          * at the encryption phase of the response data are almost certainly due to a bug in the server code.
          * It's probably unfair that we return an erroneous request tag, but we also can't let the failed response be sent.
          *
          * The SessionEncryption object is attached to the log as it will be required to replicate and diagnose the encryption problem.
          */
         if (FeedbactoryLogger.isLoggingSystemEventsAtLevel(SystemLogLevel.ApplicationError))
         {
            final SystemEvent systemEvent = new SystemEvent(SystemLogLevel.ApplicationError, getClass(), "Exception while encrypting the response", generalSecurityException);
            systemEvent.setClientIO(clientIO);

            // Restore the pre-read position so that the buffer is again ready to be flipped to a read buffer.
            sessionEncryption.toBeEncryptedResponseBuffer.setPosition(writeEndPosition);
            systemEvent.setSessionEncryption(sessionEncryption);
            FeedbactoryLogger.logSystemEvent(systemEvent);
         }

         return ProcessedOperationStatus.ErroneousRequest;
      }
   }


   private ProcessedOperationStatus processEndOfSessionRequest(final ClientIO clientIO, final RequestSessionResult requestSessionResult)
   {
      if (requestSessionResult.authentication.result == AuthenticationStatus.Success)
      {
         try
         {
            return transferEncryptedBufferToResponse(clientIO, requestSessionResult.encryption);
         }
         finally
         {
            requestSessionResult.encryption.decryptedRequestBuffer.reclaim();
            requestSessionResult.encryption.toBeEncryptedResponseBuffer.reclaim();
         }
      }
      else
         return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus processRegularSessionRequest(final ClientIO clientIO, final RequestSessionResult requestSessionResult)
   {
      if (requestSessionResult.authentication.result == AuthenticationStatus.Success)
      {
         final RequestUserSession clientSession = new RequestUserSession(clientIO, clientIO.requestBuffer, clientIO.responseBuffer, SessionRequestType.RegularSessionRequest,
                                                                         requestSessionResult.sessionID, requestSessionResult.authentication.account);

         return processGatewayRequest(clientSession);
      }
      else
         return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus processEncryptedSessionGatewayRequest(final ClientIO clientIO, final RequestSessionResult requestSessionResult)
   {
      if (requestSessionResult.authentication.result == AuthenticationStatus.Success)
      {
         try
         {
            final RequestUserSession clientSession = new RequestUserSession(clientIO, requestSessionResult.encryption.decryptedRequestBuffer,
                                                                            requestSessionResult.encryption.toBeEncryptedResponseBuffer,
                                                                            requestSessionResult.requestType, requestSessionResult.sessionID,
                                                                            requestSessionResult.authentication.account);

            final ProcessedOperationStatus processedOperationStatus = processGatewayRequest(clientSession);
            if (processedOperationStatus == ProcessedOperationStatus.OK)
               return transferEncryptedBufferToResponse(clientIO, requestSessionResult.encryption);
            else
               return processedOperationStatus;
         }
         finally
         {
            requestSessionResult.encryption.decryptedRequestBuffer.reclaim();
            requestSessionResult.encryption.toBeEncryptedResponseBuffer.reclaim();
         }
      }
      else
         return ProcessedOperationStatus.OK;
   }


   private void handleSaveCheckpoint(final Path checkpointPath) throws IOException
   {
      sessionManager.saveCheckpoint(checkpointPath);
      accountNetworkGateway.saveCheckpoint(checkpointPath);
   }


   private void handleRestoreFromCheckpoint(final Path checkpointPath) throws IOException
   {
      sessionManager.restoreFromCheckpoint(checkpointPath);
      accountNetworkGateway.restoreFromCheckpoint(checkpointPath);
   }


   private void handleStartHousekeeping()
   {
      // Will bail out with an exception if already started.
      sessionManager.startHousekeeping();
      accountNetworkGateway.startHousekeeping();
   }


   private void handleShutdownHousekeeping() throws InterruptedException
   {
      sessionManager.shutdownHousekeeping();
      accountNetworkGateway.shutdownHousekeeping();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private List<AccountSessionMetrics> handleGetAccountSessionMetrics(final int accountID)
   {
      final FeedbactoryUserAccount account = accountManager.getAccountByID(accountID);
      return (account != null) ? sessionManager.getUserAccountSessionMetrics(account) : null;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final ProcessedOperationStatus processClientRequest(final ClientIO clientIO)
   {
      return handleProcessClientRequest(clientIO);
   }


   final void saveCheckpoint(final Path checkpointPath) throws IOException
   {
      handleSaveCheckpoint(checkpointPath);
   }


   final void restoreFromCheckpoint(final Path checkpointPath) throws IOException
   {
      handleRestoreFromCheckpoint(checkpointPath);
   }


   final void startHousekeeping()
   {
      handleStartHousekeeping();
   }


   final void shutdownHousekeeping() throws InterruptedException
   {
      handleShutdownHousekeeping();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public long getMinimumCompatibleClientVersion()
   {
      return ClientVersionCompatibilityManager.MinimumCompatibleClientVersion;
   }


   final public long getMinimumAcceptedClientVersion()
   {
      return headerHandler.getMinimumAcceptedClientVersion();
   }


   final public long getLatestClientVersion()
   {
      return headerHandler.getLatestClientVersion();
   }


   final public void setLatestClientVersion(final long latestClientVersion, final boolean forceMinimumVersion)
   {
      headerHandler.setLatestClientVersion(latestClientVersion, forceMinimumVersion);
   }


   final public TimestampedMessage getBroadcastMessage()
   {
      return headerHandler.getBroadcastMessage();
   }


   final public void setBroadcastMessage(final Message broadcastMessage)
   {
      headerHandler.setBroadcastMessage(broadcastMessage);
   }


   final public SessionManagerMetrics getSessionMetrics()
   {
      return sessionManager.getMetrics();
   }


   final public List<AccountSessionMetrics> getAccountSessionMetrics(final int accountID)
   {
      return handleGetAccountSessionMetrics(accountID);
   }


   final public IPAuthenticationMetrics getIPAuthenticationMetrics()
   {
      return accountNetworkGateway.getNetworkFailedAuthenticationMetrics();
   }


   final public List<EmailBlockedIPAddressMetrics> getEmailBlockedIPAddressMetrics()
   {
      return accountNetworkGateway.getLockedOutIPAddressMetrics();
   }


   final public IPAddressFailedAuthenticationMetrics getIPAddressFailedAuthenticationMetrics(final InetAddress ipAddress)
   {
      return accountNetworkGateway.getIPAddressFailedAuthenticationMetrics(ipAddress);
   }
}