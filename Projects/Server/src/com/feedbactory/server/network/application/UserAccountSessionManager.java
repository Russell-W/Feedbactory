/* Memos:
 * - From the server perspective, there is no difference between persistent and temporary client sessions. A temporary session has the same expiry, however the client
 *   will send a sign out signal at the end of the session.
 *
 * - The encrypted session request ResumeSession is sent by the client at its startup if it has kept a persistent session. This request will reset the clock for the
 *   session's expiry. This implementation provides the assurance that the only sessions that can expire are those which either the client forcefully expires (manually, or
 *   automatically at client shutdown), or for which the client has not been active for a very long time. I'm not completely sold on the idea of the user being able to
 *   effectively renew their session indefinitely - if they are a regular user of the app - but given that the ResumeSession request must be encrypted and there's no
 *   unencrypted means to extend a session (eg. via spoofing), I'm also not convinced that there's a genuine security risk. If a user is concerned that their persistent
 *   session has been hijacked on a different machine, they can force the server to drop all of their account's other sessions by either resetting or changing their password.
 *
 * - If I enforce a policy whereby the client forcefully expires or abandons its sessions (both persistent and temporary) long before they are naturally due to expire on the
 *   server, eg. 48 hours earlier, then I'm not left having to deal with keep-alive requests and requests for expired sessions, unless a) the server has lost its
 *   session information, or b) a user has forcefully expired all of their sessions (eg. by resetting their password), and one of their expired sessions saved on another
 *   device tries to make a request. In the rare case where a client is left idle for a very long time on a user's PC, long enough to expire, the client is in a position
 *   to alert the user of a session expiry if they do happen to attempt a fresh request after being idle. The case would be rare enough that no other special handling
 *   should be required on the client (see below). The sum result is that from the server perspective, requests using expired sessions should be a rare event.
 *
 * - Previously I'd considered using a policy similar to the above but never allowing users to extend their session via ResumeSession. Their session could however be
 *   automatically extended (by minutes or maybe an hour) for each request, encrypted or regular. This had the drawbacks that firstly it allowed unencrypted requests
 *   (eg. spoofed by a man-in-the-middle) to keep the session alive indefinitely, even though they still would need to crack the encryption to actually do anything of
 *   harm. Secondly it would always result in putting the session in a strange state leading up to and after the time that the session was due to expire. If the client
 *   happened to be open during this time window - I couldn't assume that this would be a rare occurrence, since this scheme would rely on a fixed time expiry barring
 *   no activity - there is the very real possibility of the user being timed out when they least expect it, eg. if they'd been idle network-wise but been filling in
 *   feedback for some time and then tried to submit the feedback, it would be an annoying experience. Preventing this would require the client to silently trigger a
 *   timer once the session is almost due to expire, and either send a keep-alive request (yuck) or alert the user that the time out is imminent unless they
 *   make further requests (even worse). It would also have to do this repeatedly once the session is only in that unusual state of being kept alive by periodic requests.
 *
 * - A couple of possible variations:
 *
 *   a) Resume requests extend the session for a much shorter time, eg. 48 hours. Of course this should only apply if the session to be resumed happens to have less than
 *      that remaining in the first place.
 *
 *   b) Allow any encrypted session request, not just ResumeSession, to automatically reset the time out clock.
 *
 * - The public checkpointing and housekeeping management methods aren't threadsafe, the caller must carefully coordinate calls to them. For example it's
 *   unsafe to overlap calls to startHousekeeping() and shutdownHousekeeping(), or startHousekeeping() and restoreFromCheckpoint().
 *   It's OK though for a checkpoint to be saved (NOT restored), either periodically or manually, while a housekeeping run is active.
 */


package com.feedbactory.server.network.application;


import com.feedbactory.server.core.FeedbactorySecurityException;
import com.feedbactory.server.core.FeedbactoryServerConstants;
import com.feedbactory.server.core.TimeCache;
import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.log.SecurityLogLevel;
import com.feedbactory.server.core.log.SystemEvent;
import com.feedbactory.server.core.log.SystemLogLevel;
import com.feedbactory.server.network.application.NetworkServiceManager.BufferProviderInterface;
import com.feedbactory.server.network.component.ClientIO;
import com.feedbactory.server.network.component.EntityID;
import com.feedbactory.server.network.component.buffer.ReadableByteBuffer;
import com.feedbactory.server.network.component.buffer.WritableByteBuffer;
import com.feedbactory.server.useraccount.FeedbactoryUserAccount;
import com.feedbactory.server.useraccount.UserAccountNetworkGateway;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.network.AuthenticationStatus;
import com.feedbactory.shared.network.FeedbactorySessionConstants;
import com.feedbactory.shared.network.SessionInitiationType;
import com.feedbactory.shared.network.SessionRequestType;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


final public class UserAccountSessionManager
{
   static final private String AccountSessionStateFilename = "UserAccountSessionState" + FeedbactoryServerConstants.DataFileExtension;
   static final private String EncryptedRequestNonceStateFilename = "EncryptedRequestNonceState" + FeedbactoryServerConstants.DataFileExtension;

   static final private String FeedbactoryEncryptionKeyPairSerializedFilename = "FeedbactoryEncryptionKeyPair.ser";

   static final private int InitialSessionIDMapCapacity = 10000;
   static final private int InitialNonceMapCapacity = 10000;

   static final private long DormantSessionExpiryTimeMilliseconds = TimeUnit.DAYS.toMillis(8);
   static final private int SessionsPermittedPerAccount = 4;

   /* A client's request to initiate an encrypted session will include a timestamp, which is the client's approximation (at point B) of the server's time, based on
    * an earlier request (point A). In conjunction with a random nonce, both encrypted, this timestamp can prevent replay attacks. There needs to be some amount of
    * leniency in the timestamp though to allow for things such as machines (either server or client) switching to & from daylight savings between point A and B.
    * I'm not sure whether the largest time-shift worldwide would be an hour, and also whether or not I would have to cater for the case where both client and server
    * switch times simultaneously, potentially resulting in a 2 hour difference of the server time since the previous request...? The longer the largest permitted nonce
    * expiry, the longer that they must stay in the nonce collection before housekeeping can clean them up; that should never be a huge amount though, and I should be able
    * to err on the side of caution by enforcing a slightly larger than possible window of time for the nonces to expire.
    */
   static final private long RequestTimeLeniencyMilliseconds = TimeUnit.MINUTES.toMillis(125);

   static final private int HousekeepingTaskFrequencyMinutes = 5;

   static final private RequestSessionResult RegularSessionRequestFailed = new RequestSessionResult(SessionRequestType.RegularSessionRequest, SessionAuthentication.Failed);
   static final private RequestSessionResult EncryptedSessionRequestFailed = new RequestSessionResult(SessionRequestType.EncryptedSessionRequest, SessionAuthentication.Failed);
   static final private RequestSessionResult ResumeSessionRequestFailed = new RequestSessionResult(SessionRequestType.ResumeSession, SessionAuthentication.Failed);
   static final private RequestSessionResult EndSessionRequestFailed = new RequestSessionResult(SessionRequestType.EndSession, SessionAuthentication.Failed);

   final private UserAccountNetworkGateway accountNetworkGateway;

   final private BufferProviderInterface bufferProvider;

   final private KeyPair feedbactoryEncryptionKeyPair = restoreFeedbactoryEncryptionKeyPair();

   final private Map<EntityID, Session> accountsBySessionID = new ConcurrentHashMap<>(InitialSessionIDMapCapacity, 0.75f, FeedbactoryServerConstants.ServerConcurrency);
   final private Map<FeedbactoryUserAccount, List<Session>> sessionIDsByAccount = new ConcurrentHashMap<>(InitialSessionIDMapCapacity, 0.75f, FeedbactoryServerConstants.ServerConcurrency);

   final private ConcurrentHashMap<EntityID, Long> nonceExpiryTimes = new ConcurrentHashMap<>(InitialNonceMapCapacity, 0.75f, FeedbactoryServerConstants.ServerConcurrency);

   final private HousekeepingTask housekeepingTask = new HousekeepingTask();


   public UserAccountSessionManager(final UserAccountNetworkGateway accountNetworkGateway, final BufferProviderInterface bufferProvider)
   {
      this.accountNetworkGateway = accountNetworkGateway;
      this.bufferProvider = bufferProvider;
   }


   private KeyPair restoreFeedbactoryEncryptionKeyPair()
   {
      // It's not the real private key.
      final File encryptionPairFile = FeedbactoryServerConstants.ConfigurationPath.resolve(FeedbactoryEncryptionKeyPairSerializedFilename).toFile();
      try (final ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(encryptionPairFile)))
      {
         return (KeyPair) objectInputStream.readObject();
      }
      catch (final IOException | ClassNotFoundException exception)
      {
         FeedbactoryLogger.logSystemEvent(SystemLogLevel.ApplicationError, getClass(), "Could not restore the Feedbactory encryption key pair", exception);

         throw new RuntimeException(exception);
      }
   }


   // Do not delete - this will be needed if I have to update the encryption protocol key pair.
   static private KeyPair initialiseKeyPair()
   {
      try
      {
         final SecureRandom secureRandom = new SecureRandom();
         final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(FeedbactorySessionConstants.PublicKeyEncryptionType);
         keyPairGenerator.initialize(FeedbactorySessionConstants.PublicKeyEncryptionKeyLengthBytes * 8, secureRandom);

         return keyPairGenerator.generateKeyPair();
      }
      catch (final NoSuchAlgorithmException noSuchAlgorithmException)
      {
         throw new RuntimeException(noSuchAlgorithmException);
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final public class SessionManagerMetrics
   {
      final public boolean isHousekeepingEnabled;
      final public int housekeepingFrequencyMinutes;
      final public long housekeepingLastRunStartTime;

      final public int dormantSessionExpiryTimeDays;
      final public int sessionsPermittedPerAccount;
      final public int numberOfSessions;
      final public int spreadOfAccounts;

      final public int nonceEncryptionExpiryTimeMinutes;
      final public int numberOfEncryptionNonces;


      private SessionManagerMetrics(final boolean isHousekeepingEnabled, final long housekeepingLastRunStartTime,
                                    final int numberOfSessions, final int spreadOfAccounts,
                                    final int numberOfEncryptionNonces)
      {
         this.isHousekeepingEnabled = isHousekeepingEnabled;
         this.housekeepingFrequencyMinutes = HousekeepingTaskFrequencyMinutes;
         this.housekeepingLastRunStartTime = housekeepingLastRunStartTime;

         this.dormantSessionExpiryTimeDays = (int) TimeUnit.MILLISECONDS.toDays(DormantSessionExpiryTimeMilliseconds);
         this.sessionsPermittedPerAccount = SessionsPermittedPerAccount;
         this.numberOfSessions = numberOfSessions;
         this.spreadOfAccounts = spreadOfAccounts;

         this.nonceEncryptionExpiryTimeMinutes = (int) TimeUnit.MILLISECONDS.toMinutes(RequestTimeLeniencyMilliseconds);
         this.numberOfEncryptionNonces = numberOfEncryptionNonces;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class AccountSessionMetrics
   {
      final public long sessionCreationTime;
      final public long sessionLastResumedTime;


      private AccountSessionMetrics(final long sessionCreationTime, final long sessionLastResumedTime)
      {
         this.sessionCreationTime = sessionCreationTime;
         this.sessionLastResumedTime = sessionLastResumedTime;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class Session
   {
      final private FeedbactoryUserAccount account;
      final private EntityID sessionID;
      final private SecretKeySpec encryptionSecretKeySpec;
      final private long sessionCreationTime;

      private long sessionLastResumedTime;
      private int encryptedRequestCount;
      private boolean hasExpired;


      // Restore from storage constructor.
      private Session(final FeedbactoryUserAccount account, final EntityID sessionID, final SecretKeySpec encryptionSecretKeySpec,
                      final long sessionCreationTime, final long sessionLastResumedTime, final int encryptedRequestCount)
      {
         this.account = account;
         this.sessionID = sessionID;
         this.encryptionSecretKeySpec = encryptionSecretKeySpec;
         this.sessionCreationTime = sessionCreationTime;
         this.sessionLastResumedTime = sessionLastResumedTime;
         this.encryptedRequestCount = encryptedRequestCount;
      }


      // Operational constructor.
      private Session(final FeedbactoryUserAccount account, final EntityID sessionID, final SecretKeySpec encryptionSecretKeySpec)
      {
         this.account = account;
         this.sessionID = sessionID;
         this.encryptionSecretKeySpec = encryptionSecretKeySpec;

         sessionLastResumedTime = sessionCreationTime = TimeCache.getCurrentTimeMilliseconds();
      }


      private void markLastResumedTime()
      {
         sessionLastResumedTime = TimeCache.getCurrentTimeMilliseconds();
      }


      private void incrementEncryptedRequestCount()
      {
         encryptedRequestCount ++;
      }


      private void expire()
      {
         hasExpired = true;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class HousekeepingTask implements Runnable, ThreadFactory
   {
      private ScheduledThreadPoolExecutor executor;

      /* This variable is written by the housekeeping thread, and read by the owner thread of the session manager.
       * At the moment there are no other metrics variables with which the housekeeping start time needs to be written atomically,
       * so marking it as volatile rather than using locking is fine.
       */
      volatile private long lastRunStartTime = FeedbactoryConstants.NoTime;


      @Override
      final public Thread newThread(final Runnable runnable)
      {
         final Thread thread = new Thread(runnable, "User account session manager housekeeping task");
         thread.setDaemon(true);
         return thread;
      }


      @Override
      final public void run()
      {
         try
         {
            lastRunStartTime = TimeCache.getCurrentTimeMilliseconds();

            purgeExpiredSessions();
            purgeExpiredNonces();
         }
         catch (final Exception anyException)
         {
            /* Exception handling provided for -any- exception, since any exceptions will otherwise be captured
             * by the enclosing FutureTask that is generated when this Runnable is submitted to ScheduledExecutorService.scheduleAtFixedRate().
             * Unhandled exceptions would also prevent further scheduleAtFixedRate() invocations from running.
             */
            FeedbactoryLogger.logSystemEvent(SystemLogLevel.ApplicationError, getClass(), "User account session manager housekeeping task failed", anyException);
         }
      }


      private void purgeExpiredSessions()
      {
         final Iterator<Entry<FeedbactoryUserAccount, List<Session>>> accountSessionsIterator = sessionIDsByAccount.entrySet().iterator();
         Entry<FeedbactoryUserAccount, List<Session>> accountSessionsEntry;

         while (accountSessionsIterator.hasNext())
         {
            accountSessionsEntry = accountSessionsIterator.next();

            synchronized (accountSessionsEntry.getKey())
            {
               final Iterator<Session> sessionsIterator = accountSessionsEntry.getValue().iterator();
               Session session;

               while (sessionsIterator.hasNext())
               {
                  session = sessionsIterator.next();

                  if ((TimeCache.getCurrentTimeMilliseconds() - session.sessionLastResumedTime) > DormantSessionExpiryTimeMilliseconds)
                  {
                     session.expire();
                     accountsBySessionID.remove(session.sessionID);
                     sessionsIterator.remove();
                  }
               }

               // If all sessions for a user account have expired, remove the list from the sessionIDsByUserAccount collection.
               if (accountSessionsEntry.getValue().isEmpty())
                  accountSessionsIterator.remove();
            }
         }
      }


      private void purgeExpiredNonces()
      {
         final Iterator<Entry<EntityID, Long>> nonceIterator = nonceExpiryTimes.entrySet().iterator();
         Entry<EntityID, Long> nonceEntry;

         while (nonceIterator.hasNext())
         {
            nonceEntry = nonceIterator.next();

            if ((TimeCache.getCurrentTimeMilliseconds() - nonceEntry.getValue().longValue()) > RequestTimeLeniencyMilliseconds)
               nonceIterator.remove();
         }
      }


      /****************************************************************************
       * 
       ***************************************************************************/


      private boolean isStarted()
      {
         return (executor != null);
      }


      private void start()
      {
         if (isStarted())
            throw new IllegalStateException("Housekeeping task has already been started.");

         executor = new ScheduledThreadPoolExecutor(1, this);
         executor.setKeepAliveTime(10, TimeUnit.SECONDS);
         executor.allowCoreThreadTimeOut(true);

         /* Kicking off the task immediately ensures that any items persisted between separate runs of the server can be immediately cleaned up
          * if enough time has elapsed in between.
          */
         executor.scheduleAtFixedRate(this, 0, HousekeepingTaskFrequencyMinutes, TimeUnit.MINUTES);
      }


      private void shutdown() throws InterruptedException
      {
         if (executor != null)
         {
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            executor = null;
         }
      }
   }


   /****************************************************************************
    *
    *
    * 
    ***************************************************************************/


   private RequestSessionResult handleProcessSession(final ClientIO clientIO)
   {
      final byte sessionRequestTypeValue = clientIO.requestBuffer.get();
      final SessionRequestType sessionRequestType = SessionRequestType.fromValue(sessionRequestTypeValue);
      if (sessionRequestType == null)
         throw new FeedbactorySecurityException(getClass(), "Invalid session request type value: " + sessionRequestTypeValue);

      switch (sessionRequestType)
      {
         case None:
            return RequestSessionResult.NoSession;
         case InitiateSession:
            return handleSessionInitiation(clientIO);
         case RegularSessionRequest:
         case EncryptedSessionRequest:
         case ResumeSession:
         case EndSession:
            return handleProcessSessionRequest(clientIO, sessionRequestType);

         default:
            throw new AssertionError("Unhandled session request type: " + sessionRequestType);
      }
   }


   private RequestSessionResult handleSessionInitiation(final ClientIO clientIO)
   {
      WritableByteBuffer decryptedDataWriteBuffer = null;
      RequestSessionResult result = null;

      try
      {
         // Decrypt the secret key, encrypted using our public key.
         final SecretKeySpec secretKeySpec = decryptSecretKey(clientIO);

         // The secret key IV used for the request.
         final byte[] requestInitializationVectorBytes = new byte[FeedbactorySessionConstants.SecretKeyEncryptionBlockSizeBytes];
         clientIO.requestBuffer.get(requestInitializationVectorBytes);
         final IvParameterSpec requestInitializationVector = new IvParameterSpec(requestInitializationVectorBytes);

         // The secret key IV to be used for our response.
         final byte[] responseInitializationVectorBytes = new byte[FeedbactorySessionConstants.SecretKeyEncryptionBlockSizeBytes];
         clientIO.requestBuffer.get(responseInitializationVectorBytes);
         final IvParameterSpec responseInitialisationVector = new IvParameterSpec(responseInitializationVectorBytes);

         // Prepare the secret key encrypted data for decryption.
         final Cipher cipher = Cipher.getInstance(FeedbactorySessionConstants.SecretKeyEncryptionAlgorithm);

         cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, requestInitializationVector);

         /* The remainder of the request data is encrypted using the secret key. Allocate a new byte buffer, ensuring that it
          * has enough capacity to contain the unencrypted contents.
          */
         decryptedDataWriteBuffer = bufferProvider.allocateByteBuffer();
         decryptedDataWriteBuffer.ensureRemainingCapacity(cipher.getOutputSize(clientIO.requestBuffer.getRemaining()));

         // Decrypt.
         cipher.doFinal(clientIO.requestBuffer.getActiveBuffer(), decryptedDataWriteBuffer.getActiveBuffer());

         // Prepare the decrypted data for reading.
         final ReadableByteBuffer decryptedDataReadBuffer = decryptedDataWriteBuffer.flipToReadableByteBuffer();

         // The client's approximation of the server's time, based on an earlier request. May be off by some margin (see notes above).
         final long clientReportedServerTime = decryptedDataReadBuffer.getLong();

         if (Math.abs(TimeCache.getCurrentTimeMilliseconds() - clientReportedServerTime) > RequestTimeLeniencyMilliseconds)
         {
            // The logger output will include the current time, no need to provide it as part of the message.
            throw new FeedbactorySecurityException(SecurityLogLevel.High, getClass(), "Bad client request timestamp: " + clientReportedServerTime);
         }

         // A nonce, which along with the timestamp will help prevent replay attacks.
         final byte[] nonce = new byte[FeedbactorySessionConstants.SecretKeyEncryptionNonceLengthBytes];
         decryptedDataReadBuffer.get(nonce);
         final EntityID nonceID = new EntityID(nonce);

         if (nonceExpiryTimes.putIfAbsent(nonceID, TimeCache.getCurrentTimeMilliseconds()) != null)
         {
            /* There's probably not much value in logging the nonce value or its expiry time if the message has been replayed.
             * An exception might be if the nonce value indicates that it clearly hasn't been randomly generated, for the purpose of
             * replaying the message and testing the server. Logging this would be more for interest's sake though, and wouldn't change
             * the simple fact that the message has been replayed.
             */
            throw new FeedbactorySecurityException(SecurityLogLevel.High, getClass(), "Client request nonce is already present.");
         }

         result = handleAuthenticateForSessionInitiation(clientIO, secretKeySpec, decryptedDataReadBuffer, responseInitialisationVector);
      }
      catch (final GeneralSecurityException generalSecurityException)
      {
         if (FeedbactoryLogger.isLoggingSystemEventsAtLevel(SystemLogLevel.ErroneousClientRequest))
         {
            final SystemEvent event = new SystemEvent(SystemLogLevel.ErroneousClientRequest, getClass(), "Exception during session initiation", generalSecurityException);
            event.setClientIO(clientIO);
            FeedbactoryLogger.logSystemEvent(event);
         }
      }
      finally
      {
         // Clean up the decrypted buffer if the session initiation op fails for any reason.
         if ((result == null) && (decryptedDataWriteBuffer != null))
            decryptedDataWriteBuffer.reclaim();
      }

      return result;
   }


   /* Return a SecretKeySpec rather than a SecretKey; on Windows platform SecretKeyFactory.getInstance("AES") is known to report a "AES SecretKeyFactory not available"
    * exception. The returned SecretKeySpec can be used directly as a key to Cipher objects.
    */
   private SecretKeySpec decryptSecretKey(final ClientIO clientIO) throws GeneralSecurityException
   {
      final WritableByteBuffer encryptionKeyBuffer = bufferProvider.allocateByteBuffer();

      try
      {
         final int originalRequestLimit = clientIO.requestBuffer.getLimit();
         clientIO.requestBuffer.setLimit(clientIO.requestBuffer.getPosition() + FeedbactorySessionConstants.PublicKeyEncryptionKeyLengthBytes);

         final Cipher cipher = Cipher.getInstance(FeedbactorySessionConstants.PublicKeyEncryptionAlgorithm);
         cipher.init(Cipher.DECRYPT_MODE, feedbactoryEncryptionKeyPair.getPrivate());

         cipher.doFinal(clientIO.requestBuffer.getActiveBuffer(), encryptionKeyBuffer.getActiveBuffer());

         clientIO.requestBuffer.setLimit(originalRequestLimit);

         final ReadableByteBuffer decryptedKeyBuffer = encryptionKeyBuffer.flipToReadableByteBuffer();

         final byte[] encryptionKeyBytes = new byte[FeedbactorySessionConstants.SecretKeyEncryptionKeyLengthBytes];
         decryptedKeyBuffer.get(encryptionKeyBytes);

         return new SecretKeySpec(encryptionKeyBytes, FeedbactorySessionConstants.SecretKeyEncryptionType);
      }
      finally
      {
         encryptionKeyBuffer.reclaim();
      }
   }


   private RequestSessionResult handleAuthenticateForSessionInitiation(final ClientIO clientIO, final SecretKeySpec secretKeySpec,
                                                                       final ReadableByteBuffer decryptedDataReadBuffer,
                                                                       final IvParameterSpec responseInitialisationVector)
   {
      WritableByteBuffer toBeEncryptedResponseBuffer = null;
      RequestSessionResult result = null;

      try
      {
         SessionAuthentication authenticationResult;

         final byte sessionInitiationTypeValue = decryptedDataReadBuffer.get();
         final SessionInitiationType sessionInitiationType = SessionInitiationType.fromValue(sessionInitiationTypeValue);
         if (sessionInitiationType == null)
            throw new FeedbactorySecurityException(getClass(), "Invalid session initiation type value: " + sessionInitiationTypeValue);

         switch (sessionInitiationType)
         {
            case SignUp:
               authenticationResult = accountNetworkGateway.processSignUpRequest(clientIO, decryptedDataReadBuffer);
               break;

            case ActivateAccount:
               authenticationResult = accountNetworkGateway.processAccountActivationRequest(clientIO, decryptedDataReadBuffer);
               break;

            case EmailSignIn:
               authenticationResult = accountNetworkGateway.processSignInRequest(clientIO, decryptedDataReadBuffer);
               break;

            case ResetPassword:
               authenticationResult = accountNetworkGateway.processResetPasswordRequest(clientIO, decryptedDataReadBuffer);
               break;

            default:
               throw new AssertionError("Unhandled session initiation type: " + sessionInitiationType);
         }

         toBeEncryptedResponseBuffer = bufferProvider.allocateByteBuffer();

         toBeEncryptedResponseBuffer.put(authenticationResult.result.value);

         final SessionEncryption sessionEncryption = new SessionEncryption(secretKeySpec, decryptedDataReadBuffer, responseInitialisationVector, toBeEncryptedResponseBuffer);

         /* To allow our finally block to do its cleanup in the event of any exception (in which case the result variable is unset), we defer the assignment 
          * of the result variable until the final operation.
          */
         if (authenticationResult.result == AuthenticationStatus.Success)
            result = handleSuccessfulSessionInitiation(clientIO, authenticationResult, sessionEncryption);
         else
            result = new RequestSessionResult(SessionRequestType.InitiateSession, authenticationResult, sessionEncryption, null);
      }
      finally
      {
         if ((result == null) && (toBeEncryptedResponseBuffer != null))
            toBeEncryptedResponseBuffer.reclaim();
      }

      return result;
   }


   private RequestSessionResult handleSuccessfulSessionInitiation(final ClientIO clientIO, final SessionAuthentication sessionAuthentication,
                                                                  final SessionEncryption sessionEncryption)
   {
      /* We would like to insert the new session record with a minimum amount of locking.
       * It's possible to use a lockless putIfAbsent sequence (ie. checking for existing) on our ConcurrentHashMaps, however there is the issue of
       * what happens when our housekeeping task removes a (sessionless) user account node immediately after our putIfAbsent sequence has got a handle to the existing
       * record. The record is now obsolete, even before we could even get a lock on it from our new session thread (this method). If we were to lock on something else,
       * what would we lock on? A ReentrantReadWriteLock on the housekeeping task, which would always suspend any requests for the duration of the entire housekeeping?
       * I think the best bet is to lock on the user account itself, while utilising concurrent maps which will then support operations concurrently for different users.
       * In any case we would have to lock on -something- for each user, since we need to update their sessions/check that they don't have too many active sessions.
       * So the interesting outcome is that here we are using concurrent hash maps, without utilising the putIfAbsent method..
       */
      synchronized (sessionAuthentication.account)
      {
         List<Session> accountSessions = sessionIDsByAccount.get(sessionAuthentication.account);

         if (accountSessions == null)
         {
            accountSessions = new ArrayList<>(1);
            sessionIDsByAccount.put(sessionAuthentication.account, accountSessions);
         }
         else if (accountSessions.size() >= SessionsPermittedPerAccount)
         {
            final String message = "User ID has exceeded the maximum concurrent sessions: " + sessionAuthentication.account.getID().toString();
            FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Low, getClass(), message, clientIO);

            /* Unconditionally trash the user's existing sessions if there are too many.
             * An alternative to consider is to simply remove the oldest or next expiring, but is it worth the effort for the rare or malicious use case
             * of a user having too many sessions?
             */
            handleClearAccountSessions(accountSessions);
         }

         final SecureRandom secureRandom = new SecureRandom();
         final byte[] sessionIDBytes = new byte[FeedbactorySessionConstants.SessionIDLengthBytes];
         secureRandom.nextBytes(sessionIDBytes);

         sessionEncryption.toBeEncryptedResponseBuffer.put(sessionIDBytes);

         final Session session = new Session(sessionAuthentication.account, new EntityID(sessionIDBytes), sessionEncryption.secretKeySpec);
         accountSessions.add(session);

         accountsBySessionID.put(session.sessionID, session);

         // These methods lock on the user account.
         accountNetworkGateway.flushAccountMessagesToBuffer(sessionAuthentication.account, sessionEncryption.toBeEncryptedResponseBuffer);
         accountNetworkGateway.populateBufferWithAccountDetails(sessionAuthentication.account, sessionEncryption.toBeEncryptedResponseBuffer);

         return new RequestSessionResult(SessionRequestType.InitiateSession, sessionAuthentication, sessionEncryption, session.sessionID);
      }
   }


   private RequestSessionResult handleProcessSessionRequest(final ClientIO clientIO, final SessionRequestType sessionRequestType)
   {
      final byte[] sessionIDBytes = new byte[FeedbactorySessionConstants.SessionIDLengthBytes];
      clientIO.requestBuffer.get(sessionIDBytes);
      final EntityID sessionID = new EntityID(sessionIDBytes);

      final Session session = accountsBySessionID.get(sessionID);

      if (session != null)
      {
         synchronized (session.account)
         {
            /* If the housekeeping task discards the session in between our retrieval op above and our sync block, we have just missed the boat and
             * should treat the session request attempt as failed.
             */
            if (session.hasExpired)
            {
               clientIO.responseBuffer.put(AuthenticationStatus.FailedAuthentication.value);
               return resultOnFailureForRequestType(sessionRequestType);
            }

            switch (sessionRequestType)
            {
               case RegularSessionRequest:
                  return handleProcessRegularSessionRequest(clientIO, session);

               case ResumeSession:
               case EncryptedSessionRequest:
               case EndSession:
                  return handleProcessEncryptedSessionRequest(clientIO, session, sessionRequestType);
               default:
                  throw new AssertionError("Invalid session request type for session handler: " + sessionRequestType);
            }
         }
      }

      clientIO.responseBuffer.put(AuthenticationStatus.FailedAuthentication.value);

      return resultOnFailureForRequestType(sessionRequestType);
   }


   static private RequestSessionResult resultOnFailureForRequestType(final SessionRequestType sessionRequestType)
   {
      switch (sessionRequestType)
      {
         case RegularSessionRequest:
            return RegularSessionRequestFailed;
         case EncryptedSessionRequest:
            return EncryptedSessionRequestFailed;
         case ResumeSession:
            return ResumeSessionRequestFailed;
         case EndSession:
            return EndSessionRequestFailed;
         default:
            throw new IllegalArgumentException("No failure object for session request type: " + sessionRequestType);
      }
   }


   private RequestSessionResult handleProcessRegularSessionRequest(final ClientIO clientIO, final Session session)
   {
      clientIO.responseBuffer.put(AuthenticationStatus.Success.value);
      accountNetworkGateway.flushAccountMessagesToBuffer(session.account, clientIO.responseBuffer);

      final SessionAuthentication sessionAuthentication = new SessionAuthentication(AuthenticationStatus.Success, session.account);
      return new RequestSessionResult(SessionRequestType.RegularSessionRequest, sessionAuthentication, null, session.sessionID);
   }


   private RequestSessionResult handleProcessEncryptedSessionRequest(final ClientIO clientIO, final Session session, final SessionRequestType sessionRequestType)
   {
      WritableByteBuffer decryptedDataWriteBuffer = null;
      WritableByteBuffer toBeEncryptedResponseBuffer = null;
      RequestSessionResult result = null;

      try
      {
         // The secret key IV used for the request.
         final byte[] requestInitializationVectorBytes = new byte[FeedbactorySessionConstants.SecretKeyEncryptionBlockSizeBytes];
         clientIO.requestBuffer.get(requestInitializationVectorBytes);
         final IvParameterSpec requestInitializationVector = new IvParameterSpec(requestInitializationVectorBytes);

         // The secret key IV to be used for our response.
         final byte[] responseInitializationVectorBytes = new byte[FeedbactorySessionConstants.SecretKeyEncryptionBlockSizeBytes];
         clientIO.requestBuffer.get(responseInitializationVectorBytes);
         final IvParameterSpec responseInitialisationVector = new IvParameterSpec(responseInitializationVectorBytes);

         // Prepare the secret key encrypted data for decryption.
         final Cipher cipher = Cipher.getInstance(FeedbactorySessionConstants.SecretKeyEncryptionAlgorithm);

         cipher.init(Cipher.DECRYPT_MODE, session.encryptionSecretKeySpec, requestInitializationVector);

         /* The remainder of the request data is encrypted using the secret key. Allocate a new byte buffer, ensuring that it
          * has enough capacity to contain the unencrypted contents.
          */
         decryptedDataWriteBuffer = bufferProvider.allocateByteBuffer();
         decryptedDataWriteBuffer.ensureRemainingCapacity(cipher.getOutputSize(clientIO.requestBuffer.getRemaining()));

         // Decrypt.
         cipher.doFinal(clientIO.requestBuffer.getActiveBuffer(), decryptedDataWriteBuffer.getActiveBuffer());

         // Prepare the decrypted data for reading.
         final ReadableByteBuffer decryptedDataReadBuffer = decryptedDataWriteBuffer.flipToReadableByteBuffer();

         // The client's reported value for the encrypted session counter. If it doesn't match our version, it needs to be treated as a replayed (invalid) message.
         final int clientReportedEncryptedSessionCounter = decryptedDataReadBuffer.getInteger();

         if (clientReportedEncryptedSessionCounter == session.encryptedRequestCount)
         {
            clientIO.responseBuffer.put(AuthenticationStatus.Success.value);

            toBeEncryptedResponseBuffer = bufferProvider.allocateByteBuffer();

            // Increment the encryption counter once for our response..
            session.incrementEncryptedRequestCount();
            toBeEncryptedResponseBuffer.putInteger(session.encryptedRequestCount);
            // .. and once more to match up with the next valid request.
            session.incrementEncryptedRequestCount();

            switch (sessionRequestType)
            {
               case ResumeSession:
                  accountNetworkGateway.flushAccountMessagesToBuffer(session.account, toBeEncryptedResponseBuffer);
                  processResumeSession(session, toBeEncryptedResponseBuffer);
                  break;

               case EndSession:
                  /* It's very likely that the user is shutting down the client when they send this request, so they would have little opportunity
                   * to read any messages incoming at the last moment; defer them until the next opportunity.
                   */
                  accountNetworkGateway.writeNoAccountMessagesToBuffer(toBeEncryptedResponseBuffer);
                  processEndSession(session);
                  break;

               case EncryptedSessionRequest:
                  accountNetworkGateway.flushAccountMessagesToBuffer(session.account, toBeEncryptedResponseBuffer);
                  break;

               default:
                  throw new AssertionError("Invalid session request type for encrypted session request handler: " + sessionRequestType);
            }

            final SessionAuthentication sessionAuthentication = new SessionAuthentication(AuthenticationStatus.Success, session.account);
            final SessionEncryption sessionEncryption = new SessionEncryption(session.encryptionSecretKeySpec, decryptedDataReadBuffer, responseInitialisationVector,
                                                                              toBeEncryptedResponseBuffer);

            result = new RequestSessionResult(sessionRequestType, sessionAuthentication, sessionEncryption, session.sessionID);
         }
         else
         {
            /* It's possible that a previous request from the client has been processed by the server without the response returning to the client and being successfully
             * processed there, either due to a network issue or client bug. In either instance the session's encryption counter will have been incremented by the server
             * but not on the client, leading to any further requests being invalid. It's also possible that the requests are replayed by a third party, producing the
             * same result. We don't drop the session or the connection, but we do signal a possible security event.
             */
            if (FeedbactoryLogger.isLoggingSecurityEventsAtLevel(SecurityLogLevel.High))
            {
               final StringBuilder messageBuilder = new StringBuilder(100);
               messageBuilder.append("Incorrect encrypted session request counter. Expected: ");
               messageBuilder.append(session.encryptedRequestCount);
               messageBuilder.append(", received: ");
               messageBuilder.append(clientReportedEncryptedSessionCounter);
               messageBuilder.append(", requesting account ID: ");
               messageBuilder.append(session.account.getID().toString());
               messageBuilder.append(", session creation time: ");
               messageBuilder.append(session.sessionCreationTime);

               FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.High, getClass(), messageBuilder.toString(), clientIO);
            }

            clientIO.responseBuffer.put(AuthenticationStatus.FailedAuthentication.value);

            result = resultOnFailureForRequestType(sessionRequestType);
         }
      }
      catch (final GeneralSecurityException generalSecurityException)
      {
         if (FeedbactoryLogger.isLoggingSystemEventsAtLevel(SystemLogLevel.ErroneousClientRequest))
         {
            // A GeneralSecurityException is directly related to the decryption process, so only attach the session data necessary to replicate and diagnose that operation.
            final SystemEvent event = new SystemEvent(SystemLogLevel.ErroneousClientRequest, getClass(), "Exception while decrypting request", generalSecurityException);
            event.setClientIO(clientIO);
            event.setSecretKeySpec(session.encryptionSecretKeySpec);
            FeedbactoryLogger.logSystemEvent(event);
         }
      }
      finally
      {
         // Clean up both our decrypted buffer and unencrypted response buffer if the session initiation op fails for any reason.
         if ((result == null) || (result.authentication.result != AuthenticationStatus.Success))
         {
            if (decryptedDataWriteBuffer != null)
            {
               decryptedDataWriteBuffer.reclaim();

               if (toBeEncryptedResponseBuffer != null)
                  toBeEncryptedResponseBuffer.reclaim();
            }
         }
      }

      return result;
   }


   private void processResumeSession(final Session session, final WritableByteBuffer responseBuffer)
   {
      session.markLastResumedTime();

      accountNetworkGateway.populateBufferWithAccountDetails(session.account, responseBuffer);
   }


   private void processEndSession(final Session session)
   {
      session.expire();

      accountsBySessionID.remove(session.sessionID);

      final List<Session> accountSessions = sessionIDsByAccount.get(session.account);
      accountSessions.remove(session);
      if (accountSessions.isEmpty())
         sessionIDsByAccount.remove(session.account);
   }


   private void handleClearAccountSessions(final FeedbactoryUserAccount account)
   {
      synchronized (account)
      {
         final List<Session> accountSessions = sessionIDsByAccount.get(account);

         if (accountSessions != null)
         {
            handleClearAccountSessions(accountSessions);
            sessionIDsByAccount.remove(account);
         }
      }
   }


   private void handleClearAccountSessions(final List<Session> accountSessions)
   {
      for (final Session session : accountSessions)
      {
         session.expire();
         accountsBySessionID.remove(session.sessionID);
      }

      accountSessions.clear();

      /* This method stops short of removing the (now empty) list of sessions from the sessionIDsByUserAccount collection.
       * One of the callers is handleSuccessfulSessionInitiation, which happens to place a fresh session in the list
       * immediately, and it'd be a needless op to remove and then immediately replace the list in the sessionIDsByUserAccount collection.
       * It's not an elegant solution, I may wish to change this if I decide to change the policy of how to treat excess
       * sessions per user.
       */
   }


   private void handleClearAccountSessions(final FeedbactoryUserAccount account, final EntityID exceptedSessionID)
   {
      synchronized (account)
      {
         final List<Session> accountSessions = sessionIDsByAccount.get(account);

         if (accountSessions != null)
         {
            final Iterator<Session> sessionsIterator = accountSessions.iterator();
            Session nextSession;

            while (sessionsIterator.hasNext())
            {
               nextSession = sessionsIterator.next();

               if (! nextSession.sessionID.equals(exceptedSessionID))
               {
                  nextSession.expire();
                  accountsBySessionID.remove(nextSession.sessionID);
                  sessionsIterator.remove();
               }
            }
         }
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSaveCheckpoint(final Path checkpointPath) throws IOException
   {
      saveSessionState(checkpointPath);
      saveNonceState(checkpointPath);
   }


   private void saveSessionState(final Path checkpointPath) throws IOException
   {
      final File file = checkpointPath.resolve(AccountSessionStateFilename).toFile();

      try
      (
         final DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
      )
      {
         for (final Entry<FeedbactoryUserAccount, List<Session>> accountSessionsEntry : sessionIDsByAccount.entrySet())
         {
            synchronized (accountSessionsEntry.getKey())
            {
               // The user account ID.
               dataOutputStream.writeInt(accountSessionsEntry.getKey().getID().intValue());

               // The number of sessions attached to the user account.
               dataOutputStream.writeInt(accountSessionsEntry.getValue().size());

               for (final Session accountSession : accountSessionsEntry.getValue())
               {
                  dataOutputStream.write(accountSession.sessionID.asByteArray());
                  dataOutputStream.write(accountSession.encryptionSecretKeySpec.getEncoded());
                  dataOutputStream.writeLong(accountSession.sessionCreationTime);
                  dataOutputStream.writeLong(accountSession.sessionLastResumedTime);
                  dataOutputStream.writeInt(accountSession.encryptedRequestCount);
               }
            }
         }

         // Finalise the output with a -1 for the EOF, which is not a possible user account ID.
         dataOutputStream.writeInt(-1);
      }
   }


   private void saveNonceState(final Path checkpointPath) throws IOException
   {
      final File file = checkpointPath.resolve(EncryptedRequestNonceStateFilename).toFile();

      try
      (
         final DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
      )
      {
         for (final Entry<EntityID, Long> nonceEntry : nonceExpiryTimes.entrySet())
         {
            dataOutputStream.writeBoolean(true);
            dataOutputStream.write(nonceEntry.getKey().asByteArray());
            dataOutputStream.writeLong(nonceEntry.getValue());
         }

         dataOutputStream.writeBoolean(false);
      }
   }


   private void handleRestoreFromCheckpoint(final Path checkpointPath) throws IOException
   {
      if (isHousekeepingStarted())
         throw new IllegalStateException("Cannot restore from checkpoint while housekeeping task is active.");

      restoreSessionState(checkpointPath);
      restoreNonceState(checkpointPath);
   }


   private void restoreSessionState(final Path checkpointPath) throws IOException
   {
      accountsBySessionID.clear();
      sessionIDsByAccount.clear();

      final File file = checkpointPath.resolve(AccountSessionStateFilename).toFile();

      try
      (
         final DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
      )
      {
         int accountID;
         FeedbactoryUserAccount account;
         int numberOfAccountSessions;
         final byte[] sessionIDBytes = new byte[FeedbactorySessionConstants.SessionIDLengthBytes];
         EntityID sessionID;
         final byte[] encryptionSecretKey = new byte[FeedbactorySessionConstants.SecretKeyEncryptionKeyLengthBytes];
         long sessionCreationTime;
         long sessionLastResumedTime;
         int encryptedRequestCount;

         Session accountSession;
         List<Session> accountSessionsList;

         while ((accountID = dataInputStream.readInt()) != -1)
         {
            /* Will the user account ever be null? No, assuming that the user account manager's data has already been restored.
             * But what if the account manager's housekeeping task runs and expires the user? No, it won't because we have engineered our
             * session management scheme to only permit sessions for activated user accounts, which won't expire. Session IDs are not even
             * generated for requests for new user account creations, since the accounts are initially in a non-activated state.
             */
            account = accountNetworkGateway.getAccountManager().getAccountByID(accountID);

            // Ensure the visibility of the session data to subsequent threads.
            synchronized (account)
            {
               numberOfAccountSessions = dataInputStream.readInt();
               accountSessionsList = new ArrayList<>(numberOfAccountSessions);

               for (int sessionNumber = 0; sessionNumber < numberOfAccountSessions; sessionNumber ++)
               {
                  dataInputStream.readFully(sessionIDBytes);
                  dataInputStream.readFully(encryptionSecretKey);
                  sessionCreationTime = dataInputStream.readLong();
                  sessionLastResumedTime = dataInputStream.readLong();
                  encryptedRequestCount = dataInputStream.readInt();

                  sessionID = new EntityID(sessionIDBytes);

                  accountSession = new Session(account, sessionID, new SecretKeySpec(encryptionSecretKey, FeedbactorySessionConstants.SecretKeyEncryptionType),
                                               sessionCreationTime, sessionLastResumedTime, encryptedRequestCount);

                  accountSessionsList.add(accountSession);

                  accountsBySessionID.put(sessionID, accountSession);
               }
            }

            sessionIDsByAccount.put(account, accountSessionsList);
         }
      }
   }


   private void restoreNonceState(final Path checkpointPath) throws IOException
   {
      nonceExpiryTimes.clear();

      final File file = checkpointPath.resolve(EncryptedRequestNonceStateFilename).toFile();

      try
      (
         final DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
      )
      {
         byte[] nonce = new byte[FeedbactorySessionConstants.SecretKeyEncryptionNonceLengthBytes];
         long nonceTimestamp;

         while (dataInputStream.readBoolean())
         {
            dataInputStream.readFully(nonce);
            nonceTimestamp = dataInputStream.readLong();

            nonceExpiryTimes.put(new EntityID(nonce), nonceTimestamp);
         }
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private SessionManagerMetrics handleGetMetrics()
   {
      /* Manually count the number of user accounts rather than simply call sessionIDsByUserAccount.size() will ensure that when calculating the average
       * sessions per user account, I'm dividing the numberOfUserAccountSessions by the right number of user accounts; sessionIDsByUserAccount.size()
       * is at the mercy of sessions constantly being added and expired by incoming user requests, not to mention the housekeeping.
       */
      int numberOfAccountSessions = 0;
      int spreadOfAccounts = 0;

      for (final Entry<FeedbactoryUserAccount, List<Session>> accountSessions : sessionIDsByAccount.entrySet())
      {
         spreadOfAccounts ++;

         synchronized (accountSessions.getKey())
         {
            numberOfAccountSessions += accountSessions.getValue().size();
         }
      }

      return new SessionManagerMetrics(isHousekeepingStarted(), housekeepingTask.lastRunStartTime,
                                numberOfAccountSessions, spreadOfAccounts,
                                nonceExpiryTimes.size());
   }


   private List<AccountSessionMetrics> handleGetAccountSessionMetrics(final FeedbactoryUserAccount account)
   {
      synchronized (account)
      {
         final List<Session> sessions = sessionIDsByAccount.get(account);

         if (sessions != null)
         {
            final List<AccountSessionMetrics> accountSessions = new ArrayList<>(sessions.size());

            for (final Session session : sessions)
               accountSessions.add(new AccountSessionMetrics(session.sessionCreationTime, session.sessionLastResumedTime));

            return accountSessions;
         }
         else
            return Collections.emptyList();
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final RequestSessionResult processSession(final ClientIO clientIO)
   {
      return handleProcessSession(clientIO);
   }


   final void clearAccountSessions(final FeedbactoryUserAccount account)
   {
      handleClearAccountSessions(account);
   }


   final void clearAccountSessions(final FeedbactoryUserAccount account, final EntityID exceptedSessionID)
   {
      handleClearAccountSessions(account, exceptedSessionID);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public void saveCheckpoint(final Path checkpointPath) throws IOException
   {
      handleSaveCheckpoint(checkpointPath);
   }


   final public void restoreFromCheckpoint(final Path checkpointPath) throws IOException
   {
      handleRestoreFromCheckpoint(checkpointPath);
   }


   final public boolean isHousekeepingStarted()
   {
      return (housekeepingTask.isStarted());
   }


   final public void startHousekeeping()
   {
      housekeepingTask.start();
   }


   final public void shutdownHousekeeping() throws InterruptedException
   {
      housekeepingTask.shutdown();
   }


   final SessionManagerMetrics getMetrics()
   {
      return handleGetMetrics();
   }


   final List<AccountSessionMetrics> getUserAccountSessionMetrics(final FeedbactoryUserAccount account)
   {
      return handleGetAccountSessionMetrics(account);
   }
}