/* Memos:
 * - How to not badger the server with keep-alive or expired session requests:
 *
 *   From the server's perspective, a new session is valid for quite a few days. The client's 'Keep Me Signed In' (persistent session) option simply means that the client
 *      won't automatically send a Sign Out request to the server when the client shuts down. For a persistent session which has been stored
 *      client-side after the client has closed, the timestamp must be examined when the client is restarted to see whether the session is still usable. If it's even close to
 *      expiring, it should be abandoned. If it's still usable, a ResumeSession request will be sent which renews the session for a long period - long enough that it would
 *      be extremely rare for the app to be left open for so long that the session expiry is about to occur. We still need to check for that possibility upon each request,
 *      but if it happens we don't make an effort to gracefully handle it for the user, eg. prompt for their password again; instead they are forcefully signed out as soon as
 *      they try to make a new request after being idle for so long.
 * 
 *  - Down the track we may choose to be more friendly and prompt the user for their password, to cover that rare expiry case. It would probably be a matter of
 *      consuming the response to the initial request and then palming the session timeout event to the op manager..?
 *
 *  - There's some lazy buffer allocation here, specifically the code that's allocating payload buffers of size FeedbactoryClientNetworkConstants.MaximumRequestSizeBytes
 *    where the payload is known to be considerably smaller. If we wanted to be pedantic we could enforce tighter estimates of the required buffer size, but in the scheme
 *    of things even FeedbactoryClientNetworkConstants.MaximumRequestSizeBytes is a drop in the ocean with our miserly network protocol, and also the allocations are
 *    rarely performed and available to be reclaimed almost immediately.
 */


package com.feedbactory.client.core.useraccount;


import com.feedbactory.client.core.FeedbactoryClientConstants;
import com.feedbactory.client.core.OperationsManager;
import com.feedbactory.client.core.network.ClientNetworkConstants;
import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.client.core.network.NetworkServiceManager;
import com.feedbactory.client.core.network.NetworkUtilities;
import com.feedbactory.client.core.network.ProcessedRequestBasicResponse;
import com.feedbactory.client.core.network.ProcessedRequestBufferResponse;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.Message;
import com.feedbactory.shared.MessageType;
import com.feedbactory.shared.network.AuthenticationStatus;
import com.feedbactory.shared.network.BasicOperationStatus;
import com.feedbactory.shared.network.FeedbactorySessionConstants;
import com.feedbactory.shared.network.RequestGatewayIdentifier;
import com.feedbactory.shared.network.SessionInitiationType;
import com.feedbactory.shared.network.SessionRequestType;
import com.feedbactory.shared.useraccount.AccountOperationType;
import com.feedbactory.shared.useraccount.Gender;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


final public class AccountSessionManager
{
   static final private String SessionDataClientVersionPreferencesKey = "SessionDataClientVersion";

   static final private String SessionIDPreferencesKey = "SessionID";
   static final private String SessionExpiryTimePreferencesKey = "SessionExpiryTime";
   static final private String SessionEncryptionKeyPreferencesKey = "SessionEncryptionKey";
   static final private String SessionEncryptionCounterPreferencesKey = "SessionEncryptionCounter";

   /* Session timeouts on the client of slightly less than that on the server should ensure that the client will expire a session rather than potentially hit up the
    * server with requests using sessions that have already expired.
    */
   static final private long PersistentSessionExpiryTimeMilliseconds = TimeUnit.DAYS.toMillis(7);

   final private OperationsManager operationsManager;

   final private NetworkServiceManager networkServiceManager;

   final private Object sessionLock = new Object();

   private Session session;

   final private Set<AccountEventListener> accountEventCoreListeners = new CopyOnWriteArraySet<AccountEventListener>();
   final private Set<AccountEventListener> accountEventUIListeners = new CopyOnWriteArraySet<AccountEventListener>();
   final private Set<AccountMessageListener> accountMessageListeners = new CopyOnWriteArraySet<AccountMessageListener>();


   public AccountSessionManager(final OperationsManager operationsManager, final NetworkServiceManager networkServiceManager)
   {
      this.operationsManager = operationsManager;
      this.networkServiceManager = networkServiceManager;

      initialise();
   }


   private void initialise()
   {
      restorePersistentSession();
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class Session
   {
      final private byte[] sessionID;
      final private SecretKeySpec sessionEncryptionKey;
      final private boolean isPersistentSession;

      private long sessionExpiryTime;
      private int sessionEncryptionCounter;

      private FeedbactoryUserAccount userAccount;


      // Session initiation constructor.
      private Session(final byte[] sessionID, final long sessionExpiryTime, final SecretKeySpec sessionEncryptionKey, final boolean isPersistentSession)
      {
         this(sessionID, sessionExpiryTime, sessionEncryptionKey, isPersistentSession, 0);
      }


      // Restore from persistence constructor.
      private Session(final byte[] sessionID, final long sessionExpiryTime, final SecretKeySpec sessionEncryptionKey, final boolean isPersistentSession,
                      final int sessionEncryptionCounter)
      {
         this.sessionID = sessionID;
         this.sessionExpiryTime = sessionExpiryTime;
         this.sessionEncryptionKey = sessionEncryptionKey;
         this.sessionEncryptionCounter = sessionEncryptionCounter;
         this.isPersistentSession = isPersistentSession;
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private void setExpiryTime(final long sessionExpiryTime)
      {
         this.sessionExpiryTime = sessionExpiryTime;
      }


      private void setEncryptionCounter(final int sessionEncryptionCounter)
      {
         this.sessionEncryptionCounter = sessionEncryptionCounter;
      }


      private void setUserAccount(final FeedbactoryUserAccount userAccount)
      {
         this.userAccount = userAccount;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class SessionInitiationEncryptionAttributes
   {
      final private SecretKeySpec sessionEncryptionKey;
      final private IvParameterSpec responseIV;


      private SessionInitiationEncryptionAttributes(final SecretKeySpec sessionEncryptionKey, final IvParameterSpec responseIV)
      {
         this.sessionEncryptionKey = sessionEncryptionKey;
         this.responseIV = responseIV;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void restorePersistentSession()
   {
      synchronized (sessionLock)
      {
         final Preferences feedbactorySettings = Preferences.userNodeForPackage(getClass());

         // For now ignore the session data client version, but in future releases I may need to check it for compatibility during restoration.
         // I'm a little unsure about this, since checks below are being performed before even allowing the restored session.
         final long sessionDataClientVersion = feedbactorySettings.getLong(SessionDataClientVersionPreferencesKey, FeedbactoryConstants.NoTime);

         final byte[] sessionIDBytes = feedbactorySettings.getByteArray(SessionIDPreferencesKey, null);
         final byte[] sessionEncryptionKeyBytes = feedbactorySettings.getByteArray(SessionEncryptionKeyPreferencesKey, null);
         final long sessionExpiryTime = feedbactorySettings.getLong(SessionExpiryTimePreferencesKey, FeedbactoryConstants.NoTime);
         final int sessionEncryptionCounter = feedbactorySettings.getInt(SessionEncryptionCounterPreferencesKey, -1);

         if ((sessionExpiryTime != FeedbactoryConstants.NoTime) && (sessionExpiryTime > System.currentTimeMillis()) &&
             (sessionIDBytes != null) && (sessionIDBytes.length == FeedbactorySessionConstants.SessionIDLengthBytes) &&
             (sessionEncryptionKeyBytes != null) && (sessionEncryptionKeyBytes.length == FeedbactorySessionConstants.SecretKeyEncryptionKeyLengthBytes) &&
             (sessionEncryptionCounter >= 0))
         {
            session = new Session(sessionIDBytes, sessionExpiryTime, new SecretKeySpec(sessionEncryptionKeyBytes, FeedbactorySessionConstants.SecretKeyEncryptionType), true, sessionEncryptionCounter);
         }
      }
   }


   private void savePersistentSession()
   {
      synchronized (sessionLock)
      {
         final Preferences feedbactorySettings = Preferences.userNodeForPackage(getClass());

         feedbactorySettings.putLong(SessionDataClientVersionPreferencesKey, FeedbactoryClientConstants.VersionID);

         feedbactorySettings.putByteArray(SessionIDPreferencesKey, session.sessionID);
         feedbactorySettings.putByteArray(SessionEncryptionKeyPreferencesKey, session.sessionEncryptionKey.getEncoded());
         feedbactorySettings.putLong(SessionExpiryTimePreferencesKey, session.sessionExpiryTime);
         feedbactorySettings.putInt(SessionEncryptionCounterPreferencesKey, session.sessionEncryptionCounter);

         // Flush not required if the JVM terminates normally.
         // feedbactorySettings.flush();
      }
   }


   private void clearPersistentSession()
   {
      synchronized (sessionLock)
      {
         final Preferences feedbactorySettings = Preferences.userNodeForPackage(getClass());

         feedbactorySettings.remove(SessionDataClientVersionPreferencesKey);

         feedbactorySettings.remove(SessionIDPreferencesKey);
         feedbactorySettings.remove(SessionEncryptionKeyPreferencesKey);
         feedbactorySettings.remove(SessionExpiryTimePreferencesKey);
         feedbactorySettings.remove(SessionEncryptionCounterPreferencesKey);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processSignedInToUserAccountSession(final FeedbactoryUserAccount signedInUserAccount)
   {
      session.setUserAccount(signedInUserAccount);

      notifySignedInToUserAccount(signedInUserAccount, accountEventCoreListeners);
      notifySignedInToUserAccount(signedInUserAccount, accountEventUIListeners);
   }


   private void notifySignedInToUserAccount(final FeedbactoryUserAccount signedInUserAccount, final Set<AccountEventListener> listeners)
   {
      for (final AccountEventListener listener : listeners)
         listener.signedInToUserAccount(signedInUserAccount);
   }


   private void processUpdatedUserAccountDetails(final FeedbactoryUserAccount updatedUserAccount)
   {
      session.setUserAccount(updatedUserAccount);

      notifyUpdatedUserAccountDetails(updatedUserAccount, accountEventCoreListeners);
      notifyUpdatedUserAccountDetails(updatedUserAccount, accountEventUIListeners);
   }


   private void notifyUpdatedUserAccountDetails(final FeedbactoryUserAccount updatedUserAccount, final Set<AccountEventListener> listeners)
   {
      for (final AccountEventListener listener : listeners)
         listener.userAccountDetailsUpdated(updatedUserAccount);
   }


   private void processSignedOutOfUserAccountSession()
   {
      if (hasPersistentSession())
         clearPersistentSession();

      final FeedbactoryUserAccount signingOutUserAccount = session.userAccount;

      session = null;

      /* When there is a failure to resolve a persistent session, the attached user account will be null and as far as the rest of the
       * app is concerned, the user is not signed in. In this instance there should be no sign out notification to the listeners.
       */
      if (signingOutUserAccount != null)
      {
         notifySignedOutOfUserAccount(signingOutUserAccount, accountEventCoreListeners);
         notifySignedOutOfUserAccount(signingOutUserAccount, accountEventUIListeners);
      }
   }


   private void notifySignedOutOfUserAccount(final FeedbactoryUserAccount signingOutUserAccount, final Set<AccountEventListener> listeners)
   {
      for (final AccountEventListener listener : listeners)
         listener.signedOutOfUserAccount(signingOutUserAccount);
   }


   private void processNewUserAccountMessage(final Message message)
   {
      for (final AccountMessageListener listener : accountMessageListeners)
         listener.userAccountMessageReceived(message);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private SessionInitiationEncryptionAttributes writeSessionInitiationRequest(final ByteBuffer requestBuffer, final ByteBuffer sessionInitiationPayloadBuffer)
   {
      try
      {
         requestBuffer.put(SessionRequestType.InitiateSession.value);

         // Secure random used for generating the random secret key, request IV, response IV, and nonce.
         final SecureRandom secureRandom = new SecureRandom();

         final KeyGenerator keyGenerator = KeyGenerator.getInstance(FeedbactorySessionConstants.SecretKeyEncryptionType);
         keyGenerator.init(FeedbactorySessionConstants.SecretKeyEncryptionKeyLengthBytes * 8, secureRandom);
         final SecretKey encryptionKey = keyGenerator.generateKey();

         final byte[] encryptionKeyBytes = encryptionKey.getEncoded();
         final ByteBuffer encryptionKeyBuffer = ByteBuffer.wrap(encryptionKeyBytes);
         final SecretKeySpec encryptionKeySpec = new SecretKeySpec(encryptionKeyBytes, FeedbactorySessionConstants.SecretKeyEncryptionType);

         final Cipher encryptSecretKeyCipher = Cipher.getInstance(FeedbactorySessionConstants.PublicKeyEncryptionAlgorithm);
         encryptSecretKeyCipher.init(Cipher.ENCRYPT_MODE, FeedbactorySessionConstants.FeedbactoryPublicKey);

         encryptSecretKeyCipher.doFinal(encryptionKeyBuffer, requestBuffer);

         final byte[] requestIVBytes = new byte[FeedbactorySessionConstants.SecretKeyEncryptionBlockSizeBytes];
         secureRandom.nextBytes(requestIVBytes);
         requestBuffer.put(requestIVBytes);
         final IvParameterSpec requestIV = new IvParameterSpec(requestIVBytes);

         final byte[] responseIVBytes = new byte[FeedbactorySessionConstants.SecretKeyEncryptionBlockSizeBytes];
         secureRandom.nextBytes(responseIVBytes);
         requestBuffer.put(responseIVBytes);
         final IvParameterSpec responseIV = new IvParameterSpec(responseIVBytes);

         // Server timestamp of 8 bytes (long), plus random nonce of 16 bytes.
         final ByteBuffer encryptionHeaderBuffer = ByteBuffer.allocate(24);

         final byte[] nonceBytes = new byte[FeedbactorySessionConstants.SecretKeyEncryptionNonceLengthBytes];
         secureRandom.nextBytes(nonceBytes);

         // Handshake must have been performed before the call to getApproximateServerTime().
         encryptionHeaderBuffer.putLong(networkServiceManager.getApproximateServerTime());
         encryptionHeaderBuffer.put(nonceBytes);
         encryptionHeaderBuffer.flip();

         final Cipher encryptRequestDataCipher = Cipher.getInstance(FeedbactorySessionConstants.SecretKeyEncryptionAlgorithm);

         encryptRequestDataCipher.init(Cipher.ENCRYPT_MODE, encryptionKey, requestIV);

         // First encrypt the encryption header..
         encryptRequestDataCipher.update(encryptionHeaderBuffer, requestBuffer);

         // ..and then the payload.
         encryptRequestDataCipher.doFinal(sessionInitiationPayloadBuffer, requestBuffer);

         return new SessionInitiationEncryptionAttributes(encryptionKeySpec, responseIV);
      }
      catch (final Exception anyException)
      {
         throw new RuntimeException(anyException);
      }
   }


   private void writeNoSessionRequest(final ByteBuffer noSessionRequestBuffer, final ByteBuffer noSessionPayloadBuffer)
   {
      noSessionRequestBuffer.put(SessionRequestType.None.value);
      noSessionRequestBuffer.put(noSessionPayloadBuffer);
   }


   private void writeRegularSessionRequest(final ByteBuffer regularSessionRequestBuffer, final ByteBuffer regularSessionPayloadBuffer)
   {
      regularSessionRequestBuffer.put(SessionRequestType.RegularSessionRequest.value);
      regularSessionRequestBuffer.put(session.sessionID);
      regularSessionRequestBuffer.put(regularSessionPayloadBuffer);
   }


   private IvParameterSpec writeEncryptedSessionRequest(final SessionRequestType encryptedRequestType, final ByteBuffer requestBuffer, final ByteBuffer payloadBuffer)
   {
      try
      {
         requestBuffer.put(encryptedRequestType.value);

         requestBuffer.put(session.sessionID);

         // Secure random used for generating the request IV and response IV.
         final SecureRandom secureRandom = new SecureRandom();

         final byte[] requestIVBytes = new byte[FeedbactorySessionConstants.SecretKeyEncryptionBlockSizeBytes];
         secureRandom.nextBytes(requestIVBytes);
         requestBuffer.put(requestIVBytes);
         final IvParameterSpec requestIV = new IvParameterSpec(requestIVBytes);

         final byte[] responseIVBytes = new byte[FeedbactorySessionConstants.SecretKeyEncryptionBlockSizeBytes];
         secureRandom.nextBytes(responseIVBytes);
         requestBuffer.put(responseIVBytes);
         final IvParameterSpec responseIV = new IvParameterSpec(responseIVBytes);

         // Prepend the session encryption counter.
         final ByteBuffer encryptionHeaderBuffer = ByteBuffer.allocate(4);

         encryptionHeaderBuffer.putInt(session.sessionEncryptionCounter);
         encryptionHeaderBuffer.flip();

         final Cipher encryptRequestDataCipher = Cipher.getInstance(FeedbactorySessionConstants.SecretKeyEncryptionAlgorithm);

         encryptRequestDataCipher.init(Cipher.ENCRYPT_MODE, session.sessionEncryptionKey, requestIV);

         // First encrypt the encryption header containing the counter..
         encryptRequestDataCipher.update(encryptionHeaderBuffer, requestBuffer);

         // ..and then the payload.
         encryptRequestDataCipher.doFinal(payloadBuffer, requestBuffer);

         return responseIV;
      }
      catch (final Exception anyException)
      {
         throw new RuntimeException(anyException);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private AuthenticationRequestResponse processSessionInitiationRequest(final SessionInitiationEncryptionAttributes sessionEncryptionAttributes,
                                                                         final ByteBuffer requestBuffer, final boolean isPersistentSession)
   {
      final ProcessedRequestBufferResponse response = networkServiceManager.sendRequest(requestBuffer.asReadOnlyBuffer());

      if (response.requestStatus == NetworkRequestStatus.OK)
         return processOKSessionInitiationRequest(sessionEncryptionAttributes, response.data, isPersistentSession);
      else
         return toFailedAuthenticationStatus(response.requestStatus);
   }


   private AuthenticationRequestResponse toFailedAuthenticationStatus(final NetworkRequestStatus networkRequestStatus)
   {
      switch (networkRequestStatus)
      {
         case FailedTimeout:
            return new AuthenticationRequestResponse(NetworkRequestStatus.FailedTimeout);
         case FailedNetworkOther:
            return new AuthenticationRequestResponse(NetworkRequestStatus.FailedNetworkOther);
         case Consumed:
            return new AuthenticationRequestResponse(NetworkRequestStatus.Consumed);
         default:
            throw new IllegalArgumentException("Invalid failed network request status: " + networkRequestStatus);
      }
   }


   private AuthenticationRequestResponse processOKSessionInitiationRequest(final SessionInitiationEncryptionAttributes sessionEncryptionAttributes,
                                                                           final ByteBuffer encryptedResponseBuffer, final boolean isPersistentSession)
   {
      final ByteBuffer decryptedPayloadBuffer = decryptResponsePayload(sessionEncryptionAttributes.sessionEncryptionKey, sessionEncryptionAttributes.responseIV, encryptedResponseBuffer);

      final byte authenticationResultValue = decryptedPayloadBuffer.get();
      final AuthenticationStatus authenticationResult = AuthenticationStatus.fromValue(authenticationResultValue);

      if (authenticationResult == AuthenticationStatus.Success)
         return processSuccessfulAuthentication(sessionEncryptionAttributes.sessionEncryptionKey, decryptedPayloadBuffer, isPersistentSession);
      else if (authenticationResult == null)
         throw new IllegalArgumentException("Invalid session initiation authentication result value: " + authenticationResultValue);
      else
         return new AuthenticationRequestResponse(NetworkRequestStatus.OK, authenticationResult);
   }


   private ByteBuffer decryptResponsePayload(final SecretKeySpec secretKeySpec, final IvParameterSpec responseIV, final ByteBuffer encryptedResponseBuffer)
   {
      final ByteBuffer decryptedPayloadBuffer;

      try
      {
         decryptedPayloadBuffer = ByteBuffer.allocate(encryptedResponseBuffer.remaining());

         final Cipher decryptResponseCipher = Cipher.getInstance(FeedbactorySessionConstants.SecretKeyEncryptionAlgorithm);

         decryptResponseCipher.init(Cipher.DECRYPT_MODE, secretKeySpec, responseIV);

         decryptResponseCipher.doFinal(encryptedResponseBuffer, decryptedPayloadBuffer);

         decryptedPayloadBuffer.flip();

         return decryptedPayloadBuffer;
      }
      catch (final Exception anyException)
      {
         throw new RuntimeException(anyException);
      }
   }


   private AuthenticationRequestResponse processSuccessfulAuthentication(final SecretKeySpec sessionEncryptionKey,
                                                                         final ByteBuffer decryptedPayloadBuffer, final boolean isPersistentSession)
   {
      final byte[] sessionID = new byte[FeedbactorySessionConstants.SessionIDLengthBytes];
      decryptedPayloadBuffer.get(sessionID);

      processUserMessage(decryptedPayloadBuffer);

      final FeedbactoryUserAccount userAccount = readUserAccountDetails(decryptedPayloadBuffer);

      final long sessionExpiryTime = System.currentTimeMillis() + PersistentSessionExpiryTimeMilliseconds;
      session = new Session(sessionID, sessionExpiryTime, sessionEncryptionKey, isPersistentSession);

      processSignedInToUserAccountSession(userAccount);

      return new AuthenticationRequestResponse(NetworkRequestStatus.OK, AuthenticationStatus.Success);
   }


   private void processUserMessage(final ByteBuffer responseBuffer)
   {
      final byte messageTypeValue = responseBuffer.get();
      final MessageType messageType = MessageType.fromValue(messageTypeValue);
      if (messageType == null)
         throw new IllegalArgumentException("Invalid message type value: " + messageTypeValue);

      if (messageType != MessageType.NoMessage)
      {
         final String message = NetworkUtilities.getUTF8EncodedString(responseBuffer);
         processNewUserAccountMessage(new Message(messageType, message));
      }
   }


   private FeedbactoryUserAccount readUserAccountDetails(final ByteBuffer buffer)
   {
      final String email = NetworkUtilities.getUTF8EncodedString(buffer);
      final String pendingEmail = NetworkUtilities.getUTF8EncodedString(buffer);

      final byte genderValue = buffer.get();
      final Gender gender = Gender.fromValue(genderValue);
      if (gender == null)
         throw new IllegalArgumentException("Invalid gender value: " + genderValue);

      final long dateOfBirth = buffer.getLong();
      final boolean sendAnnouncements = NetworkUtilities.getBoolean(buffer);

      return new FeedbactoryUserAccount(email, pendingEmail, gender, dateOfBirth, sendAnnouncements);
   }


   private ProcessedRequestBufferResponse processRegularSessionRequest(final ByteBuffer regularSessionRequestBuffer)
   {
      final ProcessedRequestBufferResponse response = networkServiceManager.sendRequest(regularSessionRequestBuffer);

      if (response.requestStatus == NetworkRequestStatus.OK)
      {
         final byte authenticationResultValue = response.data.get();
         final AuthenticationStatus authenticationResult = AuthenticationStatus.fromValue(authenticationResultValue);

         if (authenticationResult == AuthenticationStatus.Success)
         {
            processUserMessage(response.data);
            return response;
         }
         else if (authenticationResult == null)
            throw new IllegalArgumentException("Invalid regular session authentication result value: " + authenticationResultValue);
         else
         {
            processSignedOutOfUserAccountSession();

            operationsManager.reportNetworkSessionError();

            return new ProcessedRequestBufferResponse(NetworkRequestStatus.Consumed);
         }
      }
      else
         return response;
   }


   private ProcessedRequestBufferResponse processEncryptedSessionRequest(final IvParameterSpec responseIV, final ByteBuffer requestBuffer)
   {
      return processEncryptedSessionRequest(responseIV, requestBuffer, NetworkServiceManager.DefaultConnectionTimeoutMilliseconds, false, true);
   }


   private ProcessedRequestBufferResponse processEncryptedSessionRequest(final IvParameterSpec responseIV, final ByteBuffer requestBuffer, final int timeoutMilliseconds,
                                                                         final boolean isPriorityRequest, final boolean notifyOnError)
   {
      final ProcessedRequestBufferResponse response = networkServiceManager.sendRequest(requestBuffer.asReadOnlyBuffer(), timeoutMilliseconds, isPriorityRequest);

      if (response.requestStatus == NetworkRequestStatus.OK)
      {
         final byte authenticationResultValue = response.data.get();
         final AuthenticationStatus authenticationResult = AuthenticationStatus.fromValue(authenticationResultValue);

         if (authenticationResult == AuthenticationStatus.Success)
         {
            final ByteBuffer decryptedPayload = decryptResponsePayload(session.sessionEncryptionKey, responseIV, response.data);

            if (processSessionEncryptionCounter(decryptedPayload))
            {
               processUserMessage(decryptedPayload);

               return new ProcessedRequestBufferResponse(NetworkRequestStatus.OK, decryptedPayload);
            }
            else
            {
               if (notifyOnError)
                  operationsManager.reportNetworkSessionError();

               // The session need not necessarily be abandoned at this point. What if the request had been spoofed by a third party?

               return new ProcessedRequestBufferResponse(NetworkRequestStatus.Consumed);
            }
         }
         else if (authenticationResult == null)
            throw new IllegalArgumentException("Invalid encrypted session authentication result value: " + authenticationResultValue);
         else
         {
            /* The session request has failed. Possible reasons are:
             * - The server has lost the session data.
             * - The server has forcefully expired the session due to the user resetting their password, or changing their password via another session.
             * - A previous request has been processed by the server, and its encryption counter has been incremented, however the response either never made it
             *   back to the client or failed to process, leading to the encryption counters being out of step. The next encrypted request sent will result in
             *   a failure response.
             * - A third party has spoofed the failed authentication response.
             *
             * All of these are fairly exceptional circumstances, leading to a dropped session no matter who the caller is, so it's fair enough to not want
             * to put the burden of dealing with a failed authentication session on every caller of this method. We take the reasonable step of
             * consuming the request result, and (if the caller has requested it) informing the op manager that a network session error has occurred.
             */
            processSignedOutOfUserAccountSession();

            if (isPriorityRequest)
               operationsManager.reportNetworkSessionError();

            return new ProcessedRequestBufferResponse(NetworkRequestStatus.Consumed);
         }
      }
      else
         return response;
   }


   private boolean processSessionEncryptionCounter(final ByteBuffer responseBuffer)
   {
      final int requestCounter = responseBuffer.getInt();

      final int expectedResponse = session.sessionEncryptionCounter + 1;

      if (requestCounter != expectedResponse)
      {
         // A replayed message - log it.
         final Object[] errorParameters = new Integer[] {requestCounter, expectedResponse};
         Logger.getLogger(getClass().getName()).log(Level.WARNING, "Invalid server response security state: session counter is {0}, expected {1}.", errorParameters);

         return false;
      }

      /* Increment our version of the session encryption counter to match the server's next expected request counter.
       * We perform this after the request and responses have been exchanged and validated, to hopefully handle the case where the response may have been spoofed.
       * In the event of other cases, eg. server down, busy or unavailable, we will not be processing the counter anyway.
       */
      session.setEncryptionCounter(requestCounter + 1);

      return true;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private ProcessedRequestBasicResponse handleSignInToPersistentSession()
   {
      synchronized (sessionLock)
      {
         if (! hasPersistentSession())
            throw new IllegalAccountSessionRequestState("Cannot process a persistent sign in request without a saved persistent session.");

         // Send a resume session request to the server and update the account details, clear our existing session and return false if it's rejected.
         final ByteBuffer requestPayloadBuffer = ByteBuffer.allocate(0);

         final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         final IvParameterSpec responseIV = writeEncryptedSessionRequest(SessionRequestType.ResumeSession, requestBuffer, requestPayloadBuffer);

         requestBuffer.flip();

         final ProcessedRequestBufferResponse response = processEncryptedSessionRequest(responseIV, requestBuffer, NetworkServiceManager.DefaultConnectionTimeoutMilliseconds, false, false);

         if (response.requestStatus == NetworkRequestStatus.OK)
         {
            session.setExpiryTime(System.currentTimeMillis() + PersistentSessionExpiryTimeMilliseconds);

            processSignedInToUserAccountSession(readUserAccountDetails(response.data));

            return ProcessedRequestBasicResponse.Success;
         }
         else
            return new ProcessedRequestBasicResponse(response.requestStatus);
      }
   }


   private AuthenticationRequestResponse handleSignUp(final String email, final Gender gender, final long dateOfBirth, final boolean sendEmailAlerts)
   {
      synchronized (sessionLock)
      {
         /* Unlike other requests, session initiation requests cannot rely on the network service manager to send a separate handshake request
          * if it hasn't already been done. This request needs to know the value of the approximate server time immediately in order
          * to construct the session initiation request buffer - a call to getApproximateServerTime() is made. getApproximateServerTime()
          * will throw an IllegalStateException if the server handshake hasn't already been performed.
          */
         final NetworkRequestStatus checkHandshakeRequestStatus = networkServiceManager.checkSendHandshakeRequest();
         if (checkHandshakeRequestStatus != NetworkRequestStatus.OK)
            return new AuthenticationRequestResponse(checkHandshakeRequestStatus);
         else if (hasSession())
            throw new IllegalAccountSessionRequestState("Cannot process a user account sign up while an existing account session is active.");

         final ByteBuffer requestPayloadBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         requestPayloadBuffer.put(SessionInitiationType.SignUp.value);

         NetworkUtilities.putUTF8EncodedString(email, requestPayloadBuffer);
         requestPayloadBuffer.put(gender.value);
         requestPayloadBuffer.putLong(dateOfBirth);
         NetworkUtilities.putBoolean(sendEmailAlerts, requestPayloadBuffer);

         requestPayloadBuffer.flip();

         final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         final SessionInitiationEncryptionAttributes sessionEncryptionAttributes = writeSessionInitiationRequest(requestBuffer, requestPayloadBuffer.asReadOnlyBuffer());

         requestBuffer.flip();

         return processSessionInitiationRequest(sessionEncryptionAttributes, requestBuffer, false);
      }
   }


   private AuthenticationRequestResponse handleActivateAccount(final String email, final String activationCode, final byte[] initialPasswordHash)
   {
      synchronized (sessionLock)
      {
         /* Unlike other requests, session initiation requests cannot rely on the network service manager to send a separate handshake request
          * if it hasn't already been done. This request needs to know the value of the approximate server time immediately in order
          * to construct the session initiation request buffer - a call to getApproximateServerTime() is made. getApproximateServerTime()
          * will throw an IllegalStateException if the server handshake hasn't already been performed.
          */
         final NetworkRequestStatus checkHandshakeRequestStatus = networkServiceManager.checkSendHandshakeRequest();
         if (checkHandshakeRequestStatus != NetworkRequestStatus.OK)
            return new AuthenticationRequestResponse(checkHandshakeRequestStatus);
         else if (hasSession())
            throw new IllegalAccountSessionRequestState("Cannot process a user account activation while an existing account session is active.");

         final ByteBuffer requestPayloadBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         requestPayloadBuffer.put(SessionInitiationType.ActivateAccount.value);

         NetworkUtilities.putUTF8EncodedString(email, requestPayloadBuffer);
         NetworkUtilities.putUTF8EncodedString(activationCode, requestPayloadBuffer);
         requestPayloadBuffer.put(initialPasswordHash);

         requestPayloadBuffer.flip();

         final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         final SessionInitiationEncryptionAttributes sessionEncryptionAttributes = writeSessionInitiationRequest(requestBuffer, requestPayloadBuffer.asReadOnlyBuffer());

         requestBuffer.flip();

         return processSessionInitiationRequest(sessionEncryptionAttributes, requestBuffer, false);
      }
   }


   private AuthenticationRequestResponse handleSignIn(final String email, final byte[] passwordHash, final boolean isPersistentSession)
   {
      synchronized (sessionLock)
      {
         /* Unlike other requests, session initiation requests cannot rely on the network service manager to send a separate handshake request
          * if it hasn't already been done. This request needs to know the value of the approximate server time immediately in order
          * to construct the session initiation request buffer - a call to getApproximateServerTime() is made. getApproximateServerTime()
          * will throw an IllegalStateException if the server handshake hasn't already been performed.
          */
         final NetworkRequestStatus checkHandshakeRequestStatus = networkServiceManager.checkSendHandshakeRequest();
         if (checkHandshakeRequestStatus != NetworkRequestStatus.OK)
            return new AuthenticationRequestResponse(checkHandshakeRequestStatus);
         else if (hasSession())
            throw new IllegalAccountSessionRequestState("Cannot process a user account sign in while an existing account session is active.");

         final ByteBuffer requestPayloadBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         requestPayloadBuffer.put(SessionInitiationType.EmailSignIn.value);

         NetworkUtilities.putUTF8EncodedString(email, requestPayloadBuffer);
         requestPayloadBuffer.put(passwordHash);

         requestPayloadBuffer.flip();

         final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         final SessionInitiationEncryptionAttributes sessionEncryptionAttributes = writeSessionInitiationRequest(requestBuffer, requestPayloadBuffer.asReadOnlyBuffer());

         requestBuffer.flip();

         return processSessionInitiationRequest(sessionEncryptionAttributes, requestBuffer, isPersistentSession);
      }
   }


   private AuthenticationRequestResponse handleResetPassword(final String email, final String passwordResetCode, final byte[] newPasswordHash)
   {
      synchronized (sessionLock)
      {
         /* Unlike other requests, session initiation requests cannot rely on the network service manager to send a separate handshake request
          * if it hasn't already been done. This request needs to know the value of the approximate server time immediately in order
          * to construct the session initiation request buffer - a call to getApproximateServerTime() is made. getApproximateServerTime()
          * will throw an IllegalStateException if the server handshake hasn't already been performed.
          */
         final NetworkRequestStatus checkHandshakeRequestStatus = networkServiceManager.checkSendHandshakeRequest();
         if (checkHandshakeRequestStatus != NetworkRequestStatus.OK)
            return new AuthenticationRequestResponse(checkHandshakeRequestStatus);
         else if (hasSession())
            throw new IllegalAccountSessionRequestState("Cannot process a user account password reset while an existing account session is active.");

         final ByteBuffer requestPayloadBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         requestPayloadBuffer.put(SessionInitiationType.ResetPassword.value);

         NetworkUtilities.putUTF8EncodedString(email, requestPayloadBuffer);
         NetworkUtilities.putUTF8EncodedString(passwordResetCode, requestPayloadBuffer);
         requestPayloadBuffer.put(newPasswordHash);

         requestPayloadBuffer.flip();

         final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         final SessionInitiationEncryptionAttributes sessionEncryptionAttributes = writeSessionInitiationRequest(requestBuffer, requestPayloadBuffer.asReadOnlyBuffer());

         requestBuffer.flip();

         return processSessionInitiationRequest(sessionEncryptionAttributes, requestBuffer, false);
      }
   }


   // No session or locking required.
   private ProcessedRequestBasicResponse sendCodeToEmail(final String email, final AccountOperationType codeTypeToEmail)
   {
      final ByteBuffer requestPayloadBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

      requestPayloadBuffer.put(RequestGatewayIdentifier.Account.value);
      requestPayloadBuffer.put(codeTypeToEmail.value);

      NetworkUtilities.putUTF8EncodedString(email, requestPayloadBuffer);

      requestPayloadBuffer.flip();

      final ProcessedRequestBufferResponse response = sendNoSessionRequest(requestPayloadBuffer);

      if (response.requestStatus == NetworkRequestStatus.OK)
      {
         final byte basicOperationStatusValue = response.data.get();
         final BasicOperationStatus basicOperationStatus = BasicOperationStatus.fromValue(basicOperationStatusValue);
         if (basicOperationStatus != null)
            return new ProcessedRequestBasicResponse(NetworkRequestStatus.OK, basicOperationStatus);
         else
            throw new IllegalArgumentException("Invalid operation status value for send email code: " + basicOperationStatusValue);
      }
      else
         return new ProcessedRequestBasicResponse(response.requestStatus);
   }


   private ProcessedRequestBasicResponse handleUpdateEmail(final String newEmail)
   {
      synchronized (sessionLock)
      {
         if (! hasResolvedSession())
            throw new IllegalAccountSessionRequestState("Cannot process a user account email update without an existing account session.");

         final ByteBuffer requestPayloadBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         requestPayloadBuffer.put(RequestGatewayIdentifier.Account.value);
         requestPayloadBuffer.put(AccountOperationType.UpdateEmail.value);

         NetworkUtilities.putUTF8EncodedString(newEmail, requestPayloadBuffer);

         requestPayloadBuffer.flip();

         final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         final IvParameterSpec responseIV = writeEncryptedSessionRequest(SessionRequestType.EncryptedSessionRequest, requestBuffer, requestPayloadBuffer);

         requestBuffer.flip();

         final ProcessedRequestBufferResponse response = processEncryptedSessionRequest(responseIV, requestBuffer);

         if (response.requestStatus == NetworkRequestStatus.OK)
         {
            final byte operationStatusValue = response.data.get();
            final BasicOperationStatus operationStatus = BasicOperationStatus.fromValue(operationStatusValue);

            if (operationStatus == BasicOperationStatus.OK)
            {
               final String email = NetworkUtilities.getUTF8EncodedString(response.data);
               final String pendingEmail = NetworkUtilities.getUTF8EncodedString(response.data);

               final FeedbactoryUserAccount updatedUserAccount = new FeedbactoryUserAccount(session.userAccount, email, pendingEmail);
               processUpdatedUserAccountDetails(updatedUserAccount);
            }
            else if (operationStatus == null)
               throw new IllegalArgumentException("Invalid operation status value for change of email: " + operationStatusValue);

            return new ProcessedRequestBasicResponse(response.requestStatus, operationStatus);
         }

         return new ProcessedRequestBasicResponse(response.requestStatus);
      }
   }


   private AuthenticationRequestResponse handleUpdatePasswordHash(final byte[] existingPasswordHash, final byte[] newPasswordHash)
   {
      synchronized (sessionLock)
      {
         if (! hasResolvedSession())
            throw new IllegalAccountSessionRequestState("Cannot process a user account password hash update without an existing account session.");

         final ByteBuffer requestPayloadBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         requestPayloadBuffer.put(RequestGatewayIdentifier.Account.value);
         requestPayloadBuffer.put(AccountOperationType.UpdatePasswordHash.value);

         requestPayloadBuffer.put(existingPasswordHash);
         requestPayloadBuffer.put(newPasswordHash);

         requestPayloadBuffer.flip();

         final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         final IvParameterSpec responseIV = writeEncryptedSessionRequest(SessionRequestType.EncryptedSessionRequest, requestBuffer, requestPayloadBuffer);

         requestBuffer.flip();

         final ProcessedRequestBufferResponse response = processEncryptedSessionRequest(responseIV, requestBuffer);

         if (response.requestStatus == NetworkRequestStatus.OK)
         {
            final byte passwordAuthenticationResultValue = response.data.get();
            final AuthenticationStatus passwordAuthenticationResult = AuthenticationStatus.fromValue(passwordAuthenticationResultValue);

            if (passwordAuthenticationResult == AuthenticationStatus.Success)
               return new AuthenticationRequestResponse(NetworkRequestStatus.OK, AuthenticationStatus.Success);
            else if (passwordAuthenticationResult == null)
               throw new IllegalArgumentException("Invalid password change authentication response value: " + passwordAuthenticationResultValue);
            else
               return new AuthenticationRequestResponse(NetworkRequestStatus.OK, AuthenticationStatus.FailedAuthentication);
         }
         else
            return toFailedAuthenticationStatus(response.requestStatus);
      }
   }


   private AuthenticationRequestResponse handleConfirmChangeOfEmail(final String changeOfEmailConfirmationCode, final byte[] passwordHash, final byte[] newEmailPasswordHash)
   {
      synchronized (sessionLock)
      {
         if (! hasResolvedSession())
            throw new IllegalAccountSessionRequestState("Cannot process a user account change of email confirmation without an existing account session.");

         final ByteBuffer requestPayloadBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         requestPayloadBuffer.put(RequestGatewayIdentifier.Account.value);
         requestPayloadBuffer.put(AccountOperationType.ConfirmNewEmail.value);

         NetworkUtilities.putUTF8EncodedString(changeOfEmailConfirmationCode, requestPayloadBuffer);
         requestPayloadBuffer.put(passwordHash);
         requestPayloadBuffer.put(newEmailPasswordHash);

         requestPayloadBuffer.flip();

         final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         final IvParameterSpec responseIV = writeEncryptedSessionRequest(SessionRequestType.EncryptedSessionRequest, requestBuffer, requestPayloadBuffer);

         requestBuffer.flip();

         final ProcessedRequestBufferResponse response = processEncryptedSessionRequest(responseIV, requestBuffer);

         if (response.requestStatus == NetworkRequestStatus.OK)
         {
            final byte authenticationResultValue = response.data.get();
            final AuthenticationStatus authenticationResult = AuthenticationStatus.fromValue(authenticationResultValue);

            if (authenticationResult == AuthenticationStatus.Success)
            {
               final String email = NetworkUtilities.getUTF8EncodedString(response.data);
               final String pendingEmail = NetworkUtilities.getUTF8EncodedString(response.data);

               final FeedbactoryUserAccount updatedUserAccount = new FeedbactoryUserAccount(session.userAccount, email, pendingEmail);
               processUpdatedUserAccountDetails(updatedUserAccount);

               return new AuthenticationRequestResponse(NetworkRequestStatus.OK, AuthenticationStatus.Success);
            }
            else if (authenticationResult == null)
               throw new IllegalArgumentException("Invalid change of email authentication response value: " + authenticationResultValue);
            else
               return new AuthenticationRequestResponse(NetworkRequestStatus.OK, AuthenticationStatus.FailedAuthentication);
         }
         else
            return toFailedAuthenticationStatus(response.requestStatus);
      }
   }


   private ProcessedRequestBasicResponse handleResendChangeOfEmailConfirmationCode()
   {
      synchronized (sessionLock)
      {
         if (! hasResolvedSession())
            throw new IllegalAccountSessionRequestState("Cannot process a user account change of email confirmation code resend without an existing account session.");

         final ByteBuffer requestPayloadBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         requestPayloadBuffer.put(RequestGatewayIdentifier.Account.value);
         requestPayloadBuffer.put(AccountOperationType.ResendNewEmailConfirmationCode.value);

         requestPayloadBuffer.flip();

         final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         final IvParameterSpec responseIV = writeEncryptedSessionRequest(SessionRequestType.EncryptedSessionRequest, requestBuffer, requestPayloadBuffer);

         requestBuffer.flip();

         final ProcessedRequestBufferResponse response = processEncryptedSessionRequest(responseIV, requestBuffer);

         if (response.requestStatus == NetworkRequestStatus.OK)
         {
            final byte operationStatusValue = response.data.get();
            final BasicOperationStatus operationStatus = BasicOperationStatus.fromValue(operationStatusValue);

            if (operationStatus == BasicOperationStatus.OK)
               return ProcessedRequestBasicResponse.Success;
            else if (operationStatus == null)
               throw new IllegalArgumentException("Invalid operation status value for resend change of email code: " + operationStatusValue);
            else
               return ProcessedRequestBasicResponse.Failed;
         }

         return new ProcessedRequestBasicResponse(response.requestStatus);
      }
   }


   private ProcessedRequestBasicResponse handleUpdateSendEmailAlerts(final boolean sendEmailAlerts)
   {
      synchronized (sessionLock)
      {
         if (! hasResolvedSession())
            throw new IllegalAccountSessionRequestState("Cannot process a user account change of send email alerts without an existing account session.");

         final ByteBuffer requestPayloadBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         requestPayloadBuffer.put(RequestGatewayIdentifier.Account.value);
         requestPayloadBuffer.put(AccountOperationType.UpdateSendEmailAlerts.value);
         NetworkUtilities.putBoolean(sendEmailAlerts, requestPayloadBuffer);

         requestPayloadBuffer.flip();

         final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         final IvParameterSpec responseIV = writeEncryptedSessionRequest(SessionRequestType.EncryptedSessionRequest, requestBuffer, requestPayloadBuffer);

         requestBuffer.flip();

         final ProcessedRequestBufferResponse response = processEncryptedSessionRequest(responseIV, requestBuffer);

         if (response.requestStatus == NetworkRequestStatus.OK)
         {
            final byte operationStatusValue = response.data.get();
            final BasicOperationStatus operationStatus = BasicOperationStatus.fromValue(operationStatusValue);

            if (operationStatus == BasicOperationStatus.OK)
            {
               final FeedbactoryUserAccount updatedUserAccount = new FeedbactoryUserAccount(session.userAccount, sendEmailAlerts);
               processUpdatedUserAccountDetails(updatedUserAccount);
            }
            else if (operationStatus == null)
               throw new IllegalArgumentException("Invalid operation status value for change of send email alerts: " + operationStatusValue);

            return new ProcessedRequestBasicResponse(response.requestStatus, operationStatus);
         }

         return new ProcessedRequestBasicResponse(response.requestStatus);
      }
   }


   private ProcessedRequestBasicResponse handleSignOut(final int timeoutMilliseconds, final boolean isPriorityRequest, final boolean notifyOnError)
   {
      synchronized (sessionLock)
      {
         if (! hasResolvedSession())
            throw new IllegalAccountSessionRequestState("Cannot process a user account sign out without an existing account session.");

         final ByteBuffer requestPayloadBuffer = ByteBuffer.allocate(0);

         final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         final IvParameterSpec responseIV = writeEncryptedSessionRequest(SessionRequestType.EndSession, requestBuffer, requestPayloadBuffer);

         requestBuffer.flip();

         final ProcessedRequestBufferResponse response = processEncryptedSessionRequest(responseIV, requestBuffer, timeoutMilliseconds, isPriorityRequest, notifyOnError);

         // Clear the session on the client end irrespective of the network result.
         processSignedOutOfUserAccountSession();

         return new ProcessedRequestBasicResponse(response.requestStatus, BasicOperationStatus.OK);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private ProcessedRequestBufferResponse handleSendCurrentSessionStateRequest(final ByteBuffer requestPayloadBuffer)
   {
      synchronized (sessionLock)
      {
         if (hasSession())
            return sendRegularSessionRequest(requestPayloadBuffer);
         else
            return sendNoSessionRequest(requestPayloadBuffer);
      }
   }


   private ProcessedRequestBufferResponse handleSendNoSessionRequest(final ByteBuffer requestPayloadBuffer)
   {
      final ByteBuffer noSessionRequestBuffer = ByteBuffer.allocate(requestPayloadBuffer.remaining() + 1);

      writeNoSessionRequest(noSessionRequestBuffer, requestPayloadBuffer);

      noSessionRequestBuffer.flip();

      return networkServiceManager.sendRequest(noSessionRequestBuffer);
   }


   private ProcessedRequestBufferResponse handleSendRegularSessionRequest(final ByteBuffer requestPayloadBuffer)
   {
      synchronized (sessionLock)
      {
         if (! hasSession())
            throw new IllegalAccountSessionRequestState("Cannot process a regular session request without an existing account session.");

         final ByteBuffer regularSessionRequestBuffer = ByteBuffer.allocate(requestPayloadBuffer.remaining() + FeedbactorySessionConstants.SessionIDLengthBytes + 1);

         writeRegularSessionRequest(regularSessionRequestBuffer, requestPayloadBuffer);

         regularSessionRequestBuffer.flip();

         return processRegularSessionRequest(regularSessionRequestBuffer);
      }
   }


   private ProcessedRequestBufferResponse handleSendEncryptedSessionRequest(final ByteBuffer requestPayloadBuffer)
   {
      synchronized (sessionLock)
      {
         if (! hasSession())
            throw new IllegalAccountSessionRequestState("Cannot process an encrypted session request without an existing account session.");

         final ByteBuffer encryptedSessionRequestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

         final IvParameterSpec responseIV = writeEncryptedSessionRequest(SessionRequestType.EncryptedSessionRequest, encryptedSessionRequestBuffer, requestPayloadBuffer);

         encryptedSessionRequestBuffer.flip();

         return processEncryptedSessionRequest(responseIV, encryptedSessionRequestBuffer);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleShutdown()
   {
      synchronized (sessionLock)
      {
         if (hasPersistentSession())
            savePersistentSession();
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public boolean hasSession()
   {
      synchronized (sessionLock)
      {
         return (session != null);
      }
   }


   final public boolean hasResolvedSession()
   {
      synchronized (sessionLock)
      {
         return (hasSession() && (session.userAccount != null));
      }
   }


   final public boolean hasPersistentSession()
   {
      synchronized (sessionLock)
      {
         return (hasSession() && session.isPersistentSession);
      }
   }


   final public FeedbactoryUserAccount getSignedInUserAccount()
   {
      synchronized (sessionLock)
      {
         return (hasSession() ? session.userAccount : null);
      }
   }


   final public ProcessedRequestBasicResponse signInToPersistentSession()
   {
      return handleSignInToPersistentSession();
   }


   final public ProcessedRequestBasicResponse signOutOfPersistentSession(final int timeoutMilliseconds)
   {
      return handleSignOut(timeoutMilliseconds, true, false);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public AuthenticationRequestResponse signUp(final String email, final Gender gender, final long dateOfBirth, final boolean sendEmailAlerts)
   {
      return handleSignUp(email, gender, dateOfBirth, sendEmailAlerts);
   }


   final public AuthenticationRequestResponse activateAccount(final String email, final String activationCode, final byte[] initialPasswordHash)
   {
      return handleActivateAccount(email, activationCode, initialPasswordHash);
   }


   final public ProcessedRequestBasicResponse resendActivationCode(final String email)
   {
      return sendCodeToEmail(email, AccountOperationType.ResendActivationCode);
   }


   final public AuthenticationRequestResponse signIn(final String email, final byte[] passwordHash, final boolean isPersistentSession)
   {
      return handleSignIn(email, passwordHash, isPersistentSession);
   }


   final public ProcessedRequestBasicResponse sendResetPasswordEmail(final String email)
   {
      return sendCodeToEmail(email, AccountOperationType.SendPasswordResetCode);
   }


   final public AuthenticationRequestResponse resetPassword(final String email, final String passwordResetCode, final byte[] newPasswordHash)
   {
      return handleResetPassword(email, passwordResetCode, newPasswordHash);
   }


   final public ProcessedRequestBasicResponse updateEmail(final String newEmail)
   {
      return handleUpdateEmail(newEmail);
   }


   final public AuthenticationRequestResponse updatePasswordHash(final byte[] existingPasswordHash, final byte[] newPasswordHash)
   {
      return handleUpdatePasswordHash(existingPasswordHash, newPasswordHash);
   }


   final public AuthenticationRequestResponse confirmChangeOfEmail(final String changeOfEmailConfirmationCode, final byte[] passwordHash, final byte[] newEmailPasswordHash)
   {
      return handleConfirmChangeOfEmail(changeOfEmailConfirmationCode, passwordHash, newEmailPasswordHash);
   }


   final public ProcessedRequestBasicResponse resendChangeOfEmailConfirmationCode()
   {
      return handleResendChangeOfEmailConfirmationCode();
   }


   final public ProcessedRequestBasicResponse updateSendEmailAlerts(final boolean sendEmailAlerts)
   {
      return handleUpdateSendEmailAlerts(sendEmailAlerts);
   }


   final public ProcessedRequestBasicResponse signOut()
   {
      return handleSignOut(NetworkServiceManager.DefaultConnectionTimeoutMilliseconds, false, true);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public ProcessedRequestBufferResponse sendCurrentSessionStateRequest(final ByteBuffer requestPayloadBuffer)
   {
      return handleSendCurrentSessionStateRequest(requestPayloadBuffer);
   }


   final public ProcessedRequestBufferResponse sendNoSessionRequest(final ByteBuffer requestPayloadBuffer)
   {
      return handleSendNoSessionRequest(requestPayloadBuffer);
   }


   final public ProcessedRequestBufferResponse sendRegularSessionRequest(final ByteBuffer requestPayloadBuffer)
   {
      return handleSendRegularSessionRequest(requestPayloadBuffer);
   }


   final public ProcessedRequestBufferResponse sendEncryptedSessionRequest(final ByteBuffer requestPayloadBuffer)
   {
      return handleSendEncryptedSessionRequest(requestPayloadBuffer);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void addUserAccountEventListener(final AccountEventListener userAccountListener)
   {
      accountEventCoreListeners.add(userAccountListener);
   }


   final public void removeUserAccountEventListener(final AccountEventListener userAccountListener)
   {
      accountEventCoreListeners.remove(userAccountListener);
   }


   final public void addUserAccountEventUIListener(final AccountEventListener userAccountUIListener)
   {
      accountEventUIListeners.add(userAccountUIListener);
   }


   final public void removeUserAccountEventUIListener(final AccountEventListener userAccountUIListener)
   {
      accountEventUIListeners.remove(userAccountUIListener);
   }


   final public void addUserAccountMessageListener(final AccountMessageListener userAccountMessageListener)
   {
      accountMessageListeners.add(userAccountMessageListener);
   }


   final public void removeUserAccountMessageListener(final AccountMessageListener userAccountMessageListener)
   {
      accountMessageListeners.remove(userAccountMessageListener);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void shutdown()
   {
      handleShutdown();
   }
}