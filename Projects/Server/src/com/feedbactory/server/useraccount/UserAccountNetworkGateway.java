/* Memos:
 * - A collection of failed sign in attempts by emails is maintained, even if no accounts exist for those emails. This ensures that the server isn't leaking email
 *   registration information if someone tries to determine whether or not a specific email is registered by hammering the sign in system with unsuccessful sign ins
 *   until they receive a FailedTooManyAttempts response. Too many authentication attempts for any email, regardless of whether or not it's attached to an existing account,
 *   will result in a FailedTooManyAttempts response.
 * 
 * - At the moment I'm not tracking the number of unsuccessful attempts for a user to update their account details (incorrect existing password) or to
 *   confirm their new email via an email confirmation code. Both of these operations operate within encrypted streams, where the user has already been validated
 *   for that session. A malicious user would have to do a brute force attack on validation but only after they have already been validated the first time, so for
 *   now I see little point in defending against this type of attack.
 *
 * - The tracking of unsuccessful sign ins works differently on a per email and per IP address basis. For unsuccessful email sign ins (from an IP address),
 *   a long-ish period of time is monitored. Each unsuccessful sign in accrues a strike against the IP address for that email address, with the time of the
 *   last unsuccessful sign in being recorded. If a threshold is passed, the IP is barred from further sign ins for that email for a minimum amount of time. The
 *   accumulation method is fairly crude - there's no historical digest of sign in attempts and times, only the most recent one. Given this, it's entirely possible
 *   for an IP to be barred from signing into an email if it was to spread out its attempts over a long period of time, say, each attempt slightly less than
 *   EmailLockoutPeriodMilliseconds after the last. It may seem a little harsh, but it saves from having to store EVERY sign in attempt as a separate node, and it
 *   does achieve the purpose: an IP will only be barred from signing into an email if it has made a number of unsuccessful attempts within a relatively short time
 *   frame. And, after the EmailLockoutPeriodMilliseconds has passed, its standing is completely reinstated.
 *
 * - Lockouts per IP address (for ANY unsuccessful email sign in) operate differently, and understanding the lockout process for these is easiest if you consider the
 *   attempts-per-IP and attempts-per-IP-per-email as being unrelated. More of the attempts-per-IP are allowed before imposing a temporary ban, however the monitor
 *   period is much shorter to detect the more likely form of attack: automated (ie. rapid-fire) requests using the same password for different emails, originating
 *   from the one IP address. As with the monitoring per email, once the threshold has passed, the IP is barred for a long-ish period yet completely reinstated
 *   afterwards. If the IP's unsuccessful sign ins don't push it across the lockout threshold within a monitor period, the counts are reset. Again the intended target for
 *   this type of attack is an automated one. If instead accumulated counts of unsuccessful sign ins for each IP -across- monitor periods were kept (ie. tracking last
 *   unsuccessful sign in time as with the per-email), I would likely run into trouble with larger networks with many PCs sitting behind NAT and sharing the same IP.
 * 
 * - I may wish to consider the merits of blacklisting repeat offenders via the IPAddressRequestMonitor.
 *
 * - It'd be good to know what the reasonable upper limit should be for user account sessions per IP, ie. NAT. But I'm not so keen on adding in a separate collection
 *   (and associated overhead) just to track this metric. I think it should be OK to enforce a moderate number of failed authentications per IP, and if this is causing
 *   problems for certain NAT setups they will quickly become known (and I can investigate their scale from there).
 *
 * - It may also be worthwhile considering an auxilliary anti-spam system for any requests which result in sending emails.
 *
 * - The public checkpointing and housekeeping management methods aren't threadsafe, the caller must carefully coordinate calls to them. For example it's
 *   unsafe to overlap calls to startHousekeeping() and shutdownHousekeeping(), or startHousekeeping() and restoreFromCheckpoint().
 *   It's OK though for a checkpoint to be saved (NOT restored), either periodically or manually, while a housekeeping run is active.
 */

package com.feedbactory.server.useraccount;


import com.feedbactory.server.core.FeedbactoryServerConstants;
import com.feedbactory.server.core.TimeCache;
import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.log.SecurityLogLevel;
import com.feedbactory.server.core.log.SystemLogLevel;
import com.feedbactory.server.network.application.NetworkToApplicationGateway.SessionManagerInterface;
import com.feedbactory.server.network.application.ProcessedOperationStatus;
import com.feedbactory.server.network.application.RequestUserSession;
import com.feedbactory.server.network.application.SessionAuthentication;
import com.feedbactory.server.network.component.ClientIO;
import com.feedbactory.server.network.component.buffer.ReadableByteBuffer;
import com.feedbactory.server.network.component.buffer.WritableByteBuffer;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.Message;
import com.feedbactory.shared.MessageType;
import com.feedbactory.shared.network.AuthenticationStatus;
import com.feedbactory.shared.network.BasicOperationStatus;
import com.feedbactory.shared.useraccount.AccountOperationType;
import com.feedbactory.shared.useraccount.FeedbactoryUserAccountConstants;
import com.feedbactory.shared.useraccount.Gender;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;


final public class UserAccountNetworkGateway
{
   static final private String UnsuccessfulSignInAttemptsFilename = "IPAddressFailedAuthentications" + FeedbactoryServerConstants.DataFileExtension;

   static final private int FailedAuthenticationMapInitialCapacity = 5000;

   static final private long EmailLockoutPeriodMilliseconds = TimeUnit.MINUTES.toMillis(62);
   static final private long IPAddressLockoutPeriodMilliseconds = TimeUnit.MINUTES.toMillis(62);

   // Number of failed email authenticarions from an IP address permitted per EmailLockoutPeriodMilliseconds.
   static final private int EmailLockoutThreshold = 8;

   /* Number of IP address failed authentications (for any email) permitted per HousekeepingTaskFrequencyMinutes (note: NOT per IPAddressLockoutPeriodMilliseconds).
    * If I was to impose a figure per IPAddressLockoutPeriodMilliseconds, it would have to be much higher, allowing many more sign in attempts before triggering a ban.
    * By monitoring a smaller time frame I can more effectively target the type of attack most likely to target many user accounts, ie. spammy, eg. trying the same password
    * for different emails in automated requests.
    */
   static final private int IPAddressLockoutThreshold = 34;

   static final private int HousekeepingTaskFrequencyMinutes = 5;

   final private UserAccountManager accountManager;

   final private SessionManagerInterface sessionManagerInterface;

   final private ConcurrentHashMap<InetAddress, IPAddressAuthentication> failedAuthenticationByIPAddress = new ConcurrentHashMap<>(FailedAuthenticationMapInitialCapacity, 0.75f, FeedbactoryServerConstants.ServerConcurrency);

   final private HousekeepingTask housekeepingTask = new HousekeepingTask();


   public UserAccountNetworkGateway(final UserAccountManager accountManager, final SessionManagerInterface sessionManagerInterface)
   {
      this.accountManager = accountManager;
      this.sessionManagerInterface = sessionManagerInterface;
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final public class IPAuthenticationMetrics
   {
      final public boolean isHousekeepingEnabled;
      final public int housekeepingFrequencyMinutes;
      final public long housekeepingLastRunStartTime;

      final public int numberOfIPAddressesTracking;

      final public int emailLockoutThreshold;
      final public int emailLockoutPeriodMinutes;
      final public int numberOfEmailLockouts;

      final public int ipAddressLockoutThreshold;
      final public int ipAddressLockoutPeriodMinutes;
      final public int numberOfIPAddressLockouts;


      private IPAuthenticationMetrics(final boolean isHousekeepingEnabled, final long housekeepingLastRunStartTime,
                                      final int numberOfIPAddressesTracking, final int numberOfEmailLockouts, final int numberOfIPAddressLockouts)
      {
         this.isHousekeepingEnabled = isHousekeepingEnabled;
         this.housekeepingFrequencyMinutes = HousekeepingTaskFrequencyMinutes;
         this.housekeepingLastRunStartTime = housekeepingLastRunStartTime;

         this.numberOfIPAddressesTracking = numberOfIPAddressesTracking;

         this.emailLockoutThreshold = EmailLockoutThreshold;
         this.emailLockoutPeriodMinutes = (int) TimeUnit.MILLISECONDS.toMinutes(EmailLockoutPeriodMilliseconds);
         this.numberOfEmailLockouts = numberOfEmailLockouts;

         this.ipAddressLockoutThreshold = IPAddressLockoutThreshold;
         this.ipAddressLockoutPeriodMinutes = (int) TimeUnit.MILLISECONDS.toMinutes(IPAddressLockoutPeriodMilliseconds);
         this.numberOfIPAddressLockouts = numberOfIPAddressLockouts;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class EmailBlockedIPAddressMetrics
   {
      final public InetAddress ipAddress;
      final public int spreadOfEmails;
      final public long lastLockoutTime;


      private EmailBlockedIPAddressMetrics(final InetAddress ipAddress, final int spreadOfEmails, final long lastLockoutTime)
      {
         this.ipAddress = ipAddress;
         this.lastLockoutTime = lastLockoutTime;
         this.spreadOfEmails = spreadOfEmails;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class IPAddressFailedAuthenticationMetrics
   {
      static final private IPAddressFailedAuthenticationMetrics NoFailedAuthentications = new IPAddressFailedAuthenticationMetrics(false, FeedbactoryConstants.NoTime,
                                                                                                                                   Collections.<EmailFailedAuthenticationMetrics>emptyList());

      final public boolean isIPAddressLockedOut;
      final public long lastLockoutTime;
      final public Collection<EmailFailedAuthenticationMetrics> emailFailedAuthentications;


      private IPAddressFailedAuthenticationMetrics(final boolean isIPAddressLockedOut, final long lastLockoutTime,
                                                   final Collection<EmailFailedAuthenticationMetrics> emailFailedAuthentications)
      {
         this.isIPAddressLockedOut = isIPAddressLockedOut;
         this.lastLockoutTime = lastLockoutTime;
         this.emailFailedAuthentications = emailFailedAuthentications;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class EmailFailedAuthenticationMetrics
   {
      final public String email;
      final public int failedAuthentications;
      final public long lastFailedAuthenticationTime;


      private EmailFailedAuthenticationMetrics(final String email, final int failedAuthentications, final long lastFailedAuthenticationTime)
      {
         this.email = email;
         this.failedAuthentications = failedAuthentications;
         this.lastFailedAuthenticationTime = lastFailedAuthenticationTime;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class IPAddressAuthentication
   {
      // "In addition, the visible values for any other object or array referenced by those final fields will be at least as up-to-date as the final fields."
      final private Map<String, FailedEmailAuthentication> attemptsByEmail;

      private int failedAuthentications;

      /* Operationally using the default value of 0L to indicate FeedbactoryConstants.NoTime, to save from having to execute an extra
       * synchronization block upon initialisation.
       */
      private long lastLockoutTime;
      private boolean isDeleted;


      // Operational constructor.
      private IPAddressAuthentication()
      {
         attemptsByEmail = new HashMap<>(1);
      }


      // Restoration constructor.
      private IPAddressAuthentication(final Map<String, FailedEmailAuthentication> attemptsByEmail)
      {
         this.attemptsByEmail = attemptsByEmail;
      }


      private void incrementFailedAuthentications()
      {
         failedAuthentications ++;
      }


      private void setFailedAuthentications(final int failedAuthentications)
      {
         this.failedAuthentications = failedAuthentications;
      }


      private void resetFailedAuthentications()
      {
         failedAuthentications = 0;
      }


      private void markLockoutTime()
      {
         lastLockoutTime = TimeCache.getCurrentTimeMilliseconds();
      }


      private void setLastLockoutTime(final long lastLockoutTime)
      {
         this.lastLockoutTime = lastLockoutTime;
      }


      private FailedEmailAuthentication putIfAbsentEmailFailedAuthentication(final String normalisedEmail)
      {
         FailedEmailAuthentication emailSignInAttempt = attemptsByEmail.get(normalisedEmail);

         if (emailSignInAttempt == null)
         {
            emailSignInAttempt = new FailedEmailAuthentication();
            attemptsByEmail.put(normalisedEmail, emailSignInAttempt);
         }

         return emailSignInAttempt;
      }


      private void clearEmailFailedAuthentications(final String normalisedEmail)
      {
         attemptsByEmail.remove(normalisedEmail);
      }


      private void markAsDeleted()
      {
         isDeleted = true;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final private class FailedEmailAuthentication
   {
      private int failedAuthentications;
      private long lastEventTime;


      // Restored data constructor.
      private FailedEmailAuthentication(final int failedAuthentications, final long lastMarkedEventTime)
      {
         this.failedAuthentications = failedAuthentications;
         this.lastEventTime = lastMarkedEventTime;
      }


      // Operational constructor.
      private FailedEmailAuthentication()
      {
         markEventTime();
      }


      private void incrementFailedAuthentications()
      {
         failedAuthentications ++;
      }


      private void markEventTime()
      {
         lastEventTime = TimeCache.getCurrentTimeMilliseconds();
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

      /* This variable is written by the housekeeping thread, and read by the owner thread of this class instance.
       * At the moment there are no other metrics variables with which the housekeeping start time needs to be written atomically,
       * so marking it as volatile rather than using locking is fine.
       */
      volatile private long lastRunStartTime = FeedbactoryConstants.NoTime;


      @Override
      final public Thread newThread(final Runnable runnable)
      {
         final Thread thread = new Thread(runnable, "User account network gateway housekeeping task");
         thread.setDaemon(true);
         return thread;
      }


      @Override
      final public void run()
      {
         try
         {
            updateIPAddressAuthenticationStatuses();
         }
         catch (final Exception anyException)
         {
            /* Exception handling provided for -any- exception, since any exceptions will otherwise be captured
             * by the enclosing FutureTask that is generated when this Runnable is submitted to ScheduledExecutorService.scheduleAtFixedRate().
             * Unhandled exceptions would also prevent further scheduleAtFixedRate() invocations from running.
             */
            final String message = "User account network gateway housekeeping task failed";
            FeedbactoryLogger.logSystemEvent(SystemLogLevel.ApplicationError, getClass(), message, anyException);
         }
      }


      final public void updateIPAddressAuthenticationStatuses()
      {
         lastRunStartTime = TimeCache.getCurrentTimeMilliseconds();

         final Iterator<Entry<InetAddress, IPAddressAuthentication>> entries = failedAuthenticationByIPAddress.entrySet().iterator();

         IPAddressAuthentication ipAddressAuthentications;
         Iterator<Entry<String, FailedEmailAuthentication>> emailFailedAuthenticationsIterator;
         FailedEmailAuthentication emailFailedAuthentications;

         while (entries.hasNext())
         {
            ipAddressAuthentications = entries.next().getValue();

            synchronized (ipAddressAuthentications)
            {
               emailFailedAuthenticationsIterator = ipAddressAuthentications.attemptsByEmail.entrySet().iterator();

               while (emailFailedAuthenticationsIterator.hasNext())
               {
                  emailFailedAuthentications = emailFailedAuthenticationsIterator.next().getValue();

                  // See memos in the class header for more details.
                  if ((TimeCache.getCurrentTimeMilliseconds() - emailFailedAuthentications.lastEventTime) >= EmailLockoutPeriodMilliseconds)
                     emailFailedAuthenticationsIterator.remove();
               }

               /* See memos in the class header for more details of the lockout logic.
                * Note that lastLockoutTime of 0L is used to represent FeedbactoryConstants.NoTime here, for the convenience and efficiency of not having to
                * synchronize and explicitly initialise it to a default value of FeedbactoryConstants.NoTime within the IPAddressAuthentication constructor.
                */
               if ((ipAddressAuthentications.lastLockoutTime == 0L) ||
                   ((TimeCache.getCurrentTimeMilliseconds() - ipAddressAuthentications.lastLockoutTime) >= IPAddressLockoutPeriodMilliseconds))
               {
                  if ((ipAddressAuthentications.failedAuthentications == 0) && ipAddressAuthentications.attemptsByEmail.isEmpty())
                  {
                     ipAddressAuthentications.markAsDeleted();
                     entries.remove();
                  }
                  else
                     ipAddressAuthentications.resetFailedAuthentications();
               }
            }
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

         /* Kicking off the task immediately ensures that any items persisted between runs of the server can be immediately cleaned up
          * if enough time has passed.
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
    * The following section of methods are for authenticating a new session.
    * 
    ***************************************************************************/


   private IPAddressAuthentication putIfAbsentIPAddressAuthentication(final InetAddress ipAddress)
   {
      IPAddressAuthentication ipAddressAuthentication = failedAuthenticationByIPAddress.get(ipAddress);
      if (ipAddressAuthentication == null)
      {
         ipAddressAuthentication = new IPAddressAuthentication();
         final IPAddressAuthentication existingIPAddressFailedAuthentications = failedAuthenticationByIPAddress.putIfAbsent(ipAddress, ipAddressAuthentication);
         if (existingIPAddressFailedAuthentications != null)
            ipAddressAuthentication = existingIPAddressFailedAuthentications;
      }

      return ipAddressAuthentication;
   }


   private boolean hasHadTooManyFailedAuthentications(final IPAddressAuthentication ipAddressAuthentications, final String normalisedEmail)
   {
      if (ipAddressAuthentications.failedAuthentications >= IPAddressLockoutThreshold)
         return true;
      else
      {
         final FailedEmailAuthentication emailFailedAuthentications = ipAddressAuthentications.attemptsByEmail.get(normalisedEmail);

         if (emailFailedAuthentications != null)
            return (emailFailedAuthentications.failedAuthentications >= EmailLockoutThreshold);
      }

      return false;
   }


   private boolean incrementFailedAuthentications(final IPAddressAuthentication ipAddressFailedAuthentications, final String normalisedEmail)
   {
      boolean triggerLockout = false;

      ipAddressFailedAuthentications.incrementFailedAuthentications();

      if (ipAddressFailedAuthentications.failedAuthentications >= IPAddressLockoutThreshold)
      {
         ipAddressFailedAuthentications.markLockoutTime();
         triggerLockout = true;
      }

      final FailedEmailAuthentication emailFailedAuthentications = ipAddressFailedAuthentications.putIfAbsentEmailFailedAuthentication(normalisedEmail);

      emailFailedAuthentications.incrementFailedAuthentications();
      emailFailedAuthentications.markEventTime();

      if (emailFailedAuthentications.failedAuthentications >= EmailLockoutThreshold)
         triggerLockout = true;

      return triggerLockout;
   }


   private void clearEmailFailedAuthentications(final IPAddressAuthentication ipAddressFailedAuthentications, final String normalisedEmail)
   {
      ipAddressFailedAuthentications.clearEmailFailedAuthentications(normalisedEmail);
   }


   private SessionAuthentication processFailedAuthentication(final ClientIO clientIO, final IPAddressAuthentication ipAddressFailedAuthentications, final String normalisedEmail)
   {
      if (incrementFailedAuthentications(ipAddressFailedAuthentications, normalisedEmail))
      {
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Low, getClass(), "Too many authentication attempts by the IP address for the email: " + normalisedEmail, clientIO);

         return SessionAuthentication.FailedTooManyAttempts;
      }
      else
         return SessionAuthentication.Failed;
   }


   private SessionAuthentication handleProcessSignUpRequest(final ClientIO clientIO, final ReadableByteBuffer buffer)
   {
      final String email = buffer.getUTF8EncodedString();

      final byte genderValue = buffer.get();
      final Gender gender = Gender.fromValue(genderValue);
      if (gender == null)
         throw new IllegalArgumentException("Invalid gender value: " + genderValue);

      final long dateOfBirth = buffer.getLong();
      final boolean sendEmailAlerts = buffer.getBoolean();

      final AddNewUserAccountResult addAcountResult = accountManager.addNewAccount(email, gender, dateOfBirth, sendEmailAlerts, clientIO.networkID.inetSocketAddress.getAddress());
      if (addAcountResult.status == AddNewUserAccountStatus.FailedCapacityReached)
         return SessionAuthentication.FailedCapacityReached;
      else
         return SessionAuthentication.SuccessAccountNotActivated;
   }


   private SessionAuthentication handleProcessAccountActivationRequest(final ClientIO clientIO, final ReadableByteBuffer buffer)
   {
      final String email = buffer.getUTF8EncodedString();
      final String normalisedEmail = UserAccountUtilities.getNormalisedEmail(email);
      final String activationCode = buffer.getUTF8EncodedString();
      final byte[] passwordHash = new byte[FeedbactoryUserAccountConstants.PasswordHashLengthBytes];
      buffer.get(passwordHash);

      /* Loop to handle the very unlikely but possible case where the housekeeping task has marked the IPAddressAuthentication object as deleted
       * between this method retrieving (or creating) the object and grabbing the lock on it.
       */
      for (;;)
      {
         IPAddressAuthentication ipAddressAuthentication = putIfAbsentIPAddressAuthentication(clientIO.networkID.inetSocketAddress.getAddress());

         synchronized (ipAddressAuthentication)
         {
            if (ipAddressAuthentication.isDeleted)
               continue;

            if (hasHadTooManyFailedAuthentications(ipAddressAuthentication, normalisedEmail))
               return SessionAuthentication.FailedTooManyAttempts;

            /* In keeping with our policy of not inadvertently revealing email registration information to prying parties, repeated activation attempts upon accounts which are
             * already activated will behave as if there is no such email (rather than eg. returning an AccountAlreadyActivated).
             */
            final FeedbactoryUserAccount account = accountManager.activateAccount(email, activationCode, passwordHash, clientIO.networkID.inetSocketAddress.getAddress());

            if (account != null)
            {
               // On successful authentication, we should reset the count of unsuccessful authentications for the IP address.
               clearEmailFailedAuthentications(ipAddressAuthentication, normalisedEmail);

               return new SessionAuthentication(AuthenticationStatus.Success, account);
            }
            else
               return processFailedAuthentication(clientIO, ipAddressAuthentication, normalisedEmail);
         }
      }
   }


   private SessionAuthentication handleProcessSignInRequest(final ClientIO clientIO, final ReadableByteBuffer buffer)
   {
      final String email = buffer.getUTF8EncodedString();
      final String normalisedEmail = UserAccountUtilities.getNormalisedEmail(email);
      final byte[] passwordHash = new byte[FeedbactoryUserAccountConstants.PasswordHashLengthBytes];
      buffer.get(passwordHash);

      /* Loop to handle the very unlikely but possible case where the housekeeping task has marked the IPAddressAuthentication object as deleted
       * between this method retrieving (or creating) the object and grabbing the lock on it.
       */
      for (;;)
      {
         IPAddressAuthentication ipAddressAuthentication = putIfAbsentIPAddressAuthentication(clientIO.networkID.inetSocketAddress.getAddress());

         synchronized (ipAddressAuthentication)
         {
            if (ipAddressAuthentication.isDeleted)
               continue;

            if (hasHadTooManyFailedAuthentications(ipAddressAuthentication, normalisedEmail))
               return SessionAuthentication.FailedTooManyAttempts;

            final FeedbactoryUserAccount account = accountManager.authenticateAccountSignIn(email, passwordHash, clientIO.networkID.inetSocketAddress.getAddress());

            /* Since non-activated accounts do not yet have a password set for them, it's not possible for a user to successfully authenticate for
             * a non-activated account, hence our returned result can never be SuccessAccountNotActivated.
             */
            if (account != null)
            {
               // On successful authentication, we should reset the count of unsuccessful authentications for the IP address.
               clearEmailFailedAuthentications(ipAddressAuthentication, normalisedEmail);

               return new SessionAuthentication(AuthenticationStatus.Success, account);
            }
            else
               return processFailedAuthentication(clientIO, ipAddressAuthentication, normalisedEmail);
         }
      }
   }


   private SessionAuthentication handleProcessPasswordResetRequest(final ClientIO clientIO, final ReadableByteBuffer buffer)
   {
      final String email = buffer.getUTF8EncodedString();
      final String normalisedEmail = UserAccountUtilities.getNormalisedEmail(email);
      final String passwordResetCode = buffer.getUTF8EncodedString();
      final byte[] newPasswordHash = new byte[FeedbactoryUserAccountConstants.PasswordHashLengthBytes];
      buffer.get(newPasswordHash);

      /* Loop to handle the very unlikely but possible case where the housekeeping task has marked the IPAddressAuthentication object as deleted
       * between this method retrieving (or creating) the object and grabbing the lock on it.
       */
      for (;;)
      {
         IPAddressAuthentication ipAddressAuthentication = putIfAbsentIPAddressAuthentication(clientIO.networkID.inetSocketAddress.getAddress());

         synchronized (ipAddressAuthentication)
         {
            if (ipAddressAuthentication.isDeleted)
               continue;

            if (hasHadTooManyFailedAuthentications(ipAddressAuthentication, normalisedEmail))
               return SessionAuthentication.FailedTooManyAttempts;

            /* In keeping with our policy of not inadvertently revealing email registration information to prying parties, failed password reset attempts against any email
             * string, whether the account exists or not or does or doesn't have a password reset code, will count as a strike towards an IP address being temp banned or
             * blacklisted.
             */
            final FeedbactoryUserAccount account = accountManager.resetPassword(email, passwordResetCode, newPasswordHash, clientIO.networkID.inetSocketAddress.getAddress());

            if (account != null)
            {
               // On successful authentication, we should reset the count of unsuccessful authentications for the IP address.
               clearEmailFailedAuthentications(ipAddressAuthentication, normalisedEmail);

               /* The user's password has been updated. As a security measure it's a good idea to disable all of their
                * persistent sessions (eg. auto sign ins from different machines). The session that will be created as a result
                * of returning from this method (processPasswordResetRequest) will be unaffected.
                */
               sessionManagerInterface.clearAccountSessions(account);

               return new SessionAuthentication(AuthenticationStatus.Success, account);
            }
            else
               return processFailedAuthentication(clientIO, ipAddressAuthentication, normalisedEmail);
         }
      }
   }


   /****************************************************************************
    * The following section of methods are for processing a specified user account
    * related function within either a non-session request or an encrypted
    * session request.
    ***************************************************************************/


   private ProcessedOperationStatus handleProcessAccountRequest(final RequestUserSession userSession)
   {
      switch (userSession.sessionRequestType)
      {
         case None:
            return handleProcessNoSessionAccountRequest(userSession);

         case RegularSessionRequest:
            final String message = "RegularSessionRequest is an invalid session request type for any user account operation";
            FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

            return ProcessedOperationStatus.ErroneousRequest;

         case EncryptedSessionRequest:
            return handleProcessEncryptedAccountRequest(userSession);

         default:
            throw new AssertionError("Unhandled session request type for user account request: " + userSession.sessionRequestType);
      }
   }


   private ProcessedOperationStatus handleProcessNoSessionAccountRequest(final RequestUserSession userSession)
   {
      final byte accountOperationTypeValue = userSession.requestBuffer.get();
      final AccountOperationType accountOperationType = AccountOperationType.fromValue(accountOperationTypeValue);

      if (accountOperationType == null)
      {
         final String message = "Invalid session-less user account operation type value: " + accountOperationTypeValue;
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

         return ProcessedOperationStatus.ErroneousRequest;
      }

      switch (accountOperationType)
      {
         case ResendActivationCode:
            return handleResendAccountActivationCode(userSession);

         case SendPasswordResetCode:
            return handleSendPasswordResetCode(userSession);

         default:
            final String message = "Invalid session-less user account operation: " + accountOperationType.toString();
            FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

            return ProcessedOperationStatus.ErroneousRequest;
      }
   }


   private ProcessedOperationStatus handleResendAccountActivationCode(final RequestUserSession userSession)
   {
      accountManager.resendActivationCode(userSession.requestBuffer.getUTF8EncodedString());

      userSession.responseBuffer.put(BasicOperationStatus.OK.value);

      return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus handleSendPasswordResetCode(final RequestUserSession userSession)
   {
      accountManager.sendPasswordResetCode(userSession.requestBuffer.getUTF8EncodedString());

      userSession.responseBuffer.put(BasicOperationStatus.OK.value);

      return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus handleProcessEncryptedAccountRequest(final RequestUserSession userSession)
   {
      final byte accountOperationTypeValue = userSession.requestBuffer.get();
      final AccountOperationType accountOperationType = AccountOperationType.fromValue(accountOperationTypeValue);

      if (accountOperationType == null)
      {
         final String message = "Invalid encrypted user account operation type value: " + accountOperationTypeValue;
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

         return ProcessedOperationStatus.ErroneousRequest;
      }

      switch (accountOperationType)
      {
         case UpdateEmail:
            return handleUpdateEmail(userSession);

         case ResendNewEmailConfirmationCode:
            return handleResendNewEmailConfirmationCode(userSession);

         case ConfirmNewEmail:
            return handleConfirmUpdatedEmail(userSession);

         case UpdatePasswordHash:
            return handleUpdatePasswordHash(userSession);

         case UpdateSendEmailAlerts:
            return handleUpdateSendEmailAlerts(userSession);

         default:
            final String message = "Invalid encrypted user account operation: " + accountOperationType.toString();
            FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

            return ProcessedOperationStatus.ErroneousRequest;
      }
   }


   private ProcessedOperationStatus handleUpdateEmail(final RequestUserSession userSession)
   {
      final String newEmail = userSession.requestBuffer.getUTF8EncodedString();

      final FeedbactoryUserAccount account = userSession.account;

      synchronized (account)
      {
         accountManager.updateAccountEmail(account, newEmail);

         userSession.responseBuffer.put(BasicOperationStatus.OK.value);

         /* The user may be reclaiming a previous email; the client can't know the new values of email & pending email for sure,
          * so we need to provide them in the response.
          */
         populateBufferWithEmail(account, userSession.responseBuffer);
      }

      return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus handleResendNewEmailConfirmationCode(final RequestUserSession userSession)
   {
      if (accountManager.resendConfirmNewEmailCode(userSession.account))
         userSession.responseBuffer.put(BasicOperationStatus.OK.value);
      else
         userSession.responseBuffer.put(BasicOperationStatus.Failed.value);

      return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus handleConfirmUpdatedEmail(final RequestUserSession userSession)
   {
      final String emailConfirmationCode = userSession.requestBuffer.getUTF8EncodedString();

      final byte[] existingPasswordHash = new byte[FeedbactoryUserAccountConstants.PasswordHashLengthBytes];
      userSession.requestBuffer.get(existingPasswordHash);

      final byte[] newEmailPasswordHash = new byte[FeedbactoryUserAccountConstants.PasswordHashLengthBytes];
      userSession.requestBuffer.get(newEmailPasswordHash);

      final FeedbactoryUserAccount account = userSession.account;

      synchronized (account)
      {
         if (accountManager.confirmNewEmail(account, emailConfirmationCode, existingPasswordHash, newEmailPasswordHash))
         {
            userSession.responseBuffer.put(AuthenticationStatus.Success.value);
            populateBufferWithEmail(account, userSession.responseBuffer);
         }
         else
            userSession.responseBuffer.put(AuthenticationStatus.FailedAuthentication.value);
      }

      return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus handleUpdatePasswordHash(final RequestUserSession userSession)
   {
      final byte[] existingPasswordHash = new byte[FeedbactoryUserAccountConstants.PasswordHashLengthBytes];
      userSession.requestBuffer.get(existingPasswordHash);

      final byte[] newPasswordHash = new byte[FeedbactoryUserAccountConstants.PasswordHashLengthBytes];
      userSession.requestBuffer.get(newPasswordHash);

      final FeedbactoryUserAccount account = userSession.account;

      synchronized (account)
      {
         if (accountManager.updateAccountPasswordHash(account, existingPasswordHash, newPasswordHash))
         {
            /* The user's password has been updated. As a safety precaution it's a good idea to disable all of their
             * persistent sessions (eg. auto sign ins from different machines), except for the session that is attached to the
             * current request..
             */
            sessionManagerInterface.clearAccountSessions(account, userSession.sessionID);

            userSession.responseBuffer.put(AuthenticationStatus.Success.value);
         }
         else
            userSession.responseBuffer.put(AuthenticationStatus.FailedAuthentication.value);
      }

      return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus handleUpdateSendEmailAlerts(final RequestUserSession userSession)
   {
      final boolean sendEmailAlerts = userSession.requestBuffer.getBoolean();
      final FeedbactoryUserAccount account = userSession.account;

      synchronized (account)
      {
         accountManager.updateSendEmailAlerts(account, sendEmailAlerts);
         userSession.responseBuffer.put(BasicOperationStatus.OK.value);
      }

      return ProcessedOperationStatus.OK;
   }


   private void handleFlushAccountMessagesToBuffer(final FeedbactoryUserAccount account, final WritableByteBuffer buffer)
   {
      synchronized (account)
      {
         final Message message = account.getMessage().message;

         buffer.put(message.messageType.value);

         if (message.messageType != MessageType.NoMessage)
         {
            buffer.putUTF8EncodedString(message.message);
            account.setMessage(Message.NoMessage);
         }
      }
   }


   private void handleWriteNoAccountMessagesToBuffer(final WritableByteBuffer buffer)
   {
      buffer.put(MessageType.NoMessage.value);
   }


   private void handlePopulateBufferWithAccountDetails(final FeedbactoryUserAccount account, final WritableByteBuffer buffer)
   {
      synchronized (account)
      {
         populateBufferWithEmail(account, buffer);

         buffer.put(account.getGender().value);
         buffer.putLong(account.getDateOfBirth());
         buffer.putBoolean(account.getSendEmailAlerts());
      }
   }


   private void populateBufferWithEmail(final FeedbactoryUserAccount account, final WritableByteBuffer buffer)
   {
      synchronized (account)
      {
         buffer.putUTF8EncodedString(account.getEmail());
         buffer.putUTF8EncodedString(account.getPendingEmail());
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSaveCheckpoint(final Path checkpointPath) throws IOException
   {
      final File file = checkpointPath.resolve(UnsuccessfulSignInAttemptsFilename).toFile();

      try
      (
         // We could also make use of Files.newOutputStream().. but do we really need thread-safe streams?
         final DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
      )
      {
         IPAddressAuthentication ipAddressAuthentication;
         byte[] inetAddressBytes;

         for (final Entry<InetAddress, IPAddressAuthentication> signInAttempts : failedAuthenticationByIPAddress.entrySet())
         {
            ipAddressAuthentication = signInAttempts.getValue();

            synchronized (signInAttempts.getValue())
            {
               if (ipAddressAuthentication.isDeleted)
                  continue;

               inetAddressBytes = signInAttempts.getKey().getAddress();

               dataOutputStream.writeByte(inetAddressBytes.length);
               dataOutputStream.write(inetAddressBytes);
               dataOutputStream.writeInt(ipAddressAuthentication.failedAuthentications);
               dataOutputStream.writeLong(ipAddressAuthentication.lastLockoutTime);

               dataOutputStream.writeInt(ipAddressAuthentication.attemptsByEmail.size());

               for (final Entry<String, FailedEmailAuthentication> signInAttemptsByEmail : ipAddressAuthentication.attemptsByEmail.entrySet())
               {
                  dataOutputStream.writeUTF(signInAttemptsByEmail.getKey());
                  dataOutputStream.writeInt(signInAttemptsByEmail.getValue().failedAuthentications);
                  dataOutputStream.writeLong(signInAttemptsByEmail.getValue().lastEventTime);
               }
            }
         }

         // Finalise the output with a -1 for the EOF.
         dataOutputStream.writeByte(-1);
      }
   }


   private void handleRestoreFromCheckpoint(final Path checkpointPath) throws IOException
   {
      if (isHousekeepingStarted())
         throw new IllegalStateException("Cannot restore from checkpoint while housekeeping task is active.");

      failedAuthenticationByIPAddress.clear();

      final File file = checkpointPath.resolve(UnsuccessfulSignInAttemptsFilename).toFile();

      try
      (
         // We could also make use of Files.newOutputStream().. but do we really need thread-safe streams?
         final DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
      )
      {
         byte inetAddressSize;
         byte[] inetAddressBytes;
         InetAddress inetAddress;

         int totalFailedAuthentications;
         long lastLockoutTime;

         int numberOfEmailsFlaggedForThisInetAddress;
         Map<String, FailedEmailAuthentication> emailFailedAuthentications;

         String email;
         int emailTotalUnsuccessfulSignInAttempts;
         long emailLastEventTime;

         IPAddressAuthentication ipAddressFailedAuthentication;

         while ((inetAddressSize = dataInputStream.readByte()) != -1)
         {
            inetAddressBytes = new byte[inetAddressSize];
            dataInputStream.readFully(inetAddressBytes);
            inetAddress = InetAddress.getByAddress(inetAddressBytes);

            totalFailedAuthentications = dataInputStream.readInt();
            lastLockoutTime = dataInputStream.readLong();

            numberOfEmailsFlaggedForThisInetAddress = dataInputStream.readInt();
            emailFailedAuthentications = new HashMap<>(numberOfEmailsFlaggedForThisInetAddress);

            for (int emailNumber = 0; emailNumber < numberOfEmailsFlaggedForThisInetAddress; emailNumber ++)
            {
               email = dataInputStream.readUTF();
               emailTotalUnsuccessfulSignInAttempts = dataInputStream.readInt();
               emailLastEventTime = dataInputStream.readLong();

               emailFailedAuthentications.put(email, new FailedEmailAuthentication(emailTotalUnsuccessfulSignInAttempts, emailLastEventTime));
            }

            ipAddressFailedAuthentication = new IPAddressAuthentication(emailFailedAuthentications);

            /* Ensure that the non-final IP authentication fields are visible to subsequent threads.
             * The final field HashMap and its sub-fields/objects are guaranteed to have been completely initialised and visible.
             */
            synchronized (ipAddressFailedAuthentication)
            {
               ipAddressFailedAuthentication.setFailedAuthentications(totalFailedAuthentications);
               ipAddressFailedAuthentication.setLastLockoutTime(lastLockoutTime);
            }

            failedAuthenticationByIPAddress.put(inetAddress, ipAddressFailedAuthentication);
         }
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private IPAuthenticationMetrics handleGetNetworkFailedAuthenticationMetrics()
   {
      int numberOfIPAddressesTracking = 0;
      int numberOfAccountLockouts = 0;
      int numberOfIPAddressLockouts = 0;

      for (final IPAddressAuthentication ipAddressFailedAuthentications : failedAuthenticationByIPAddress.values())
      {
         numberOfIPAddressesTracking ++;

         synchronized (ipAddressFailedAuthentications)
         {
            if (ipAddressFailedAuthentications.failedAuthentications >= IPAddressLockoutThreshold)
               numberOfIPAddressLockouts ++;

            for (final FailedEmailAuthentication accountFailedAuthentications : ipAddressFailedAuthentications.attemptsByEmail.values())
            {
               if (accountFailedAuthentications.failedAuthentications >= EmailLockoutThreshold)
                  numberOfAccountLockouts ++;
            }
         }
      }

      return new IPAuthenticationMetrics(isHousekeepingStarted(), housekeepingTask.lastRunStartTime,
                                         numberOfIPAddressesTracking, numberOfAccountLockouts, numberOfIPAddressLockouts);
   }


   private List<EmailBlockedIPAddressMetrics> handleGetLockedOutIPAddressMetrics()
   {
      final List<EmailBlockedIPAddressMetrics> lockedOutIPAddresses = new LinkedList<>();

      IPAddressAuthentication ipAddressFailedAuthentications;

      for (final Entry<InetAddress, IPAddressAuthentication> ipAddressFailedAuthenticationsEntry : failedAuthenticationByIPAddress.entrySet())
      {
         ipAddressFailedAuthentications = ipAddressFailedAuthenticationsEntry.getValue();

         synchronized (ipAddressFailedAuthentications)
         {
            if (ipAddressFailedAuthentications.failedAuthentications >= IPAddressLockoutThreshold)
            {
               // lastLockoutTime doesn't need to be converted to FeedbactoryConstants.NoTime here if it's 0L, since it won't be under this condition.
               lockedOutIPAddresses.add(new EmailBlockedIPAddressMetrics(ipAddressFailedAuthenticationsEntry.getKey(), ipAddressFailedAuthentications.attemptsByEmail.size(),
                                                                         ipAddressFailedAuthentications.lastLockoutTime));
            }
         }
      }

      return lockedOutIPAddresses;
   }


   private IPAddressFailedAuthenticationMetrics handleGetIPAddressAuthenticationMetrics(final InetAddress inetAddress)
   {
      final IPAddressAuthentication failedAuthentications = failedAuthenticationByIPAddress.get(inetAddress);
      FailedEmailAuthentication emailFailedAuthentications;

      if (failedAuthentications != null)
      {
         synchronized (failedAuthentications)
         {
            final Collection<EmailFailedAuthenticationMetrics> emails = new ArrayList<>(failedAuthentications.attemptsByEmail.size());

            for (final Entry<String, FailedEmailAuthentication> emailFailedAuthenticationsEntry : failedAuthentications.attemptsByEmail.entrySet())
            {
               emailFailedAuthentications = emailFailedAuthenticationsEntry.getValue();

               emails.add(new EmailFailedAuthenticationMetrics(emailFailedAuthenticationsEntry.getKey(), emailFailedAuthentications.failedAuthentications,
                                                               emailFailedAuthentications.lastEventTime));
            }

            // Convert the default value of lastLockoutTime (0L) to FeedbactoryConstants.NoTime so that it can be displayed correctly by the metrics handler.
            final long lastLockoutTime = (failedAuthentications.lastLockoutTime == 0L) ? FeedbactoryConstants.NoTime : failedAuthentications.lastLockoutTime;
            return new IPAddressFailedAuthenticationMetrics(failedAuthentications.failedAuthentications >= IPAddressLockoutThreshold, lastLockoutTime, emails);
         }
      }
      else
         return IPAddressFailedAuthenticationMetrics.NoFailedAuthentications;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public UserAccountManager getAccountManager()
   {
      return accountManager;
   }


   final public SessionAuthentication processSignUpRequest(final ClientIO clientIO, final ReadableByteBuffer buffer)
   {
      return handleProcessSignUpRequest(clientIO, buffer);
   }


   final public SessionAuthentication processAccountActivationRequest(final ClientIO clientIO, final ReadableByteBuffer buffer)
   {
      return handleProcessAccountActivationRequest(clientIO, buffer);
   }


   final public SessionAuthentication processSignInRequest(final ClientIO clientIO, final ReadableByteBuffer buffer)
   {
      return handleProcessSignInRequest(clientIO, buffer);
   }


   final public SessionAuthentication processResetPasswordRequest(final ClientIO clientIO, final ReadableByteBuffer buffer)
   {
      return handleProcessPasswordResetRequest(clientIO, buffer);
   }


   final public void flushAccountMessagesToBuffer(final FeedbactoryUserAccount account, final WritableByteBuffer buffer)
   {
      handleFlushAccountMessagesToBuffer(account, buffer);
   }


   final public void writeNoAccountMessagesToBuffer(final WritableByteBuffer buffer)
   {
      handleWriteNoAccountMessagesToBuffer(buffer);
   }


   final public void populateBufferWithAccountDetails(final FeedbactoryUserAccount account, final WritableByteBuffer buffer)
   {
      handlePopulateBufferWithAccountDetails(account, buffer);
   }


   final public ProcessedOperationStatus processAccountRequest(final RequestUserSession userSession)
   {
      return handleProcessAccountRequest(userSession);
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
      return housekeepingTask.isStarted();
   }


   final public void startHousekeeping()
   {
      housekeepingTask.start();
   }


   final public void shutdownHousekeeping() throws InterruptedException
   {
      housekeepingTask.shutdown();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public IPAuthenticationMetrics getNetworkFailedAuthenticationMetrics()
   {
      return handleGetNetworkFailedAuthenticationMetrics();
   }


   final public List<EmailBlockedIPAddressMetrics> getLockedOutIPAddressMetrics()
   {
      return handleGetLockedOutIPAddressMetrics();
   }


   final public IPAddressFailedAuthenticationMetrics getIPAddressFailedAuthenticationMetrics(final InetAddress inetAddress)
   {
      return handleGetIPAddressAuthenticationMetrics(inetAddress);
   }
}