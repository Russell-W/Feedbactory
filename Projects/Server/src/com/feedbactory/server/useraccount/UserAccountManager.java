/* Memos:
 * - Case is preserved for requested account email addresses, however Feedbactory will only allow one account per case-insensitive match of email address.
 *   The implication is simply that the user accounts collection accountsByEmail is only ever keyed by a 'normalised' version of an account email address,
 *   whether active, pending, or superseded.
 *
 * - Since the password hash is derived from the active account email, and performed on the client end, it is assumed that the client also does this
 *   using a normalised version of the email, to prevent producing different hashes depending on the email case that the user enters.
 *
 * - The housekeeping process may run at the same time as the data persistence task, since both lock on the individual user accounts before doing their thing and neither
 *   place the data into an inconsistent state. If the data persistence task saves a pending email for a given user account immediately before the housekeeping task
 *   is due to expire and remove it, it's no problem to save the data in that state because the next iteration of the housekeeper will produce the expiry.
 *   The same applies to the unactivated account expiry. So, the houskeeping task does not need to be shut down before the final run of the persistence task.
 *
 * - Likewise the data persistence task may safely run at any time, although note that the persistence task iterates over userAccountsByID, which during the account
 *   creation operation may momentarily have multiple account ID's added for the same email - see the add new account method for further comments.
 *   Under these circumstances the orphaned accounts (having an entry in the accountsByID collection but not in accountsByEmail) will be saved and later restored,
 *   but they will also eventually be cleaned up by the housekeeping operation which also cleans up the expired accountsByID objects during its normal operation.
 *   Since there is no way for the orphaned ID objects to become activated (via normal Feedbactory operation), they will eventually expire.
 *
 * - What happens to new accounts that are created midway through the persistence operation? Eg. an account NOT created in time to be written out to userAccountsByID,
 *   but present by the time the persistence task iterates through accountsByEmail? The accountsByEmail data is saved but always must be checked during restoration to
 *   ensure that there is a link to an existing accountsByID entry. Which leads to the next point regarding persistence in general:
 * 
 * - The nature of a persistence operation during active connections is that it is out of date as soon as it's finished running. For a completely consistent snapshot
 *   of the data at shutdown time, the persistence operation must be run a final time -after- all active connections have finished.
 * 
 * - Not providing a password at account sign up protects against leaking email registration information. If I allowed an initial password on sign up, a user could
 *   immediately try to sign in using their provided password to determine whether or not the sign in worked. If they received a SuccessAccountNotActivated, the account
 *   was not already registered. If however they received a Failed response, it did. So, only allow users to provide an initial password at the point of them
 *   confirming their email address.
 * 
 * - Related to the above point, users are allowed to (temporarily) update their pending email to one that is already registered for someone else. There's no other option
 *   without revealing to the user that the email is already taken. Of course there's no way provided for the user to confirm that email address for their account, so it
 *   will eventually expire and revert to their most recent confirmed email. Meanwhile I need to keep in mind that a user account can have a pending email which
 *   does not belong to them, which has a different user account in the userAccountsByEmail collection, and for which there is no corresponding email confirmation code.
 *   This has implications when managing the userAccountsByEmail collection.
 *
 * - For the user passwords a client-side hashing scheme is employed which:
 *
 *   a) Allows peace of mind for the users that their raw passwords are never transmitted (encrypted or otherwise) to the server.
 *
 *   b) Saves the server from performing its own computationally expensive hash operations. Rehashing on the server is still required for the stored passwords to protect
 *      against an attack where a party gains read-only access to the server - they could effectively use these client-generated hashes as the raw password, unless
 *      they are rehashed server-side. However to guard against this attack vector a less expensive hash algorithm may be used on the server.
 *
 *   Key to the hashing scheme is that the client must be able to generate the same hash every time. But, each hash salt must be different for each user both within our
 *   database and for those same user emails on different app databases, to ensure that the hashes are not susceptible to a rainbow table attack. So our scheme is to generate
 *   a deterministic salt using a combination of a fixed Feedbactory salt prefix, and the user's email address. This does have the drawback that the user's hash must be
 *   updated every time that they change their email address, but it simply requires a prompting for their existing password at the time that they do this,
 *   which is good practice in any case.
 *
 *   Refer to http://security.stackexchange.com/questions/23006/client-side-password-hashing
 *
 * - When I move the data to fixed storage, ie. database, I will then have to start re-hashing the password hashes (which have already been hashed client-side).
 *
 * - Given the need to update the user's password hash every time their email is updated, there are a couple of choices as to how to go about it:
 *
 *   1) Capture the new password hash at the time that the user nominates a new email address, but before they confirm it via the email confirmation code, or
 *   2) Capture the new password hash but only once the user has confirmed their updated email.
 *
 *   It turns out that option 1) won't work, when I consider the case where a user has a pending email address & password hash and then forgets their password and
 *   attempts to reset it. For a password reset on the client side, the user can be prompted for their email, the password reset code, and generate the reset password hash,
 *   however meanwhile they have a pending email which is attached to a pending password hash which was generated using their -old- password. Since the hashing is done
 *   client-side using the plain-text password (which never makes it to the server), the server has no way of generating the equivalent hash of the reset password for
 *   the pending email. Unless I applied an immediate follow-up prompt to the user, but there's no guarantee that they will go through with that, and it's an awful
 *   mess in terms of user friendliness in any case. So, option 2) is the way to go. This has the slight downside of needing to prompt the user for their password
 *   when they confirm their email, but it's not too jarring.
 *
 * - I was considering providing some more detailed return data for many of these user account methods, for when they might be called outside of a network context,
 *   perhaps by a utility on the server. For example the resendActivationCode(email) method might return an enum result describing the four possible email actions that it
 *   has taken: AccountNotActivated, AccountAlreadyActivated, SupercededAccountEmail, or AccountNotFound/AccountExpired. The network caller doesn't care about the result
 *   of that method, only that the request has been processed and an email of some sort has been sent. Providing detailed results seems a bit of a waste however until
 *   a genuine server-side use case (ie. outside of the network context) presents itself.
 *
 * - Validation on user account ops are performed by the public accessor methods, however none are performed during the restoration process. This is a tricky decision to
 *   make since depending on how you look at things pulling data from disk could be put in the same boat as an untrusted caller. But I think that the resulting validation
 *   would be quite unwieldy and unnecessary; if the persisted data is compromised and changed, validation of individual accounts is the least of the concerns.
 *
 * - The public checkpointing and housekeeping management methods aren't threadsafe, the caller must carefully coordinate calls to them. For example it's
 *   unsafe to overlap calls to startHousekeeping() and shutdownHousekeeping(), or startHousekeeping() and restoreFromCheckpoint().
 *   It's OK though for a checkpoint to be saved (NOT restored), either periodically or manually, while a housekeeping run is active.
 */


package com.feedbactory.server.useraccount;


import com.feedbactory.server.core.FeedbactoryServerConstants;
import com.feedbactory.server.core.TimeCache;
import com.feedbactory.server.core.TimestampedMessage;
import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.log.SystemLogLevel;
import com.feedbactory.server.useraccount.FeedbactoryUserAccount.ActivationState;
import com.feedbactory.server.useraccount.UserAccountMailer.UserAccountMailerMetrics;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.Message;
import com.feedbactory.shared.MessageType;
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
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


final public class UserAccountManager
{
   static final private int AccountCapacity = 5000;

   static final public int AccountCollectionInitialCapacity = AccountCapacity;

   static final private String AccountDataFilename = "UserAccountData" + FeedbactoryServerConstants.DataFileExtension;
   static final private String AccountEmailsFilename = "UserAccountEmails" + FeedbactoryServerConstants.DataFileExtension;

   static final private long PendingAccountExpiryPeriodMilliseconds = TimeUnit.DAYS.toMillis(14);
   static final private long PendingEmailExpiryPeriodMilliseconds = TimeUnit.DAYS.toMillis(14);
   static final private long PasswordResetCodeExpiryPeriodMilliseconds = TimeUnit.DAYS.toMillis(1);

   static final private long HousekeepingTaskFrequencyMilliseconds = TimeUnit.DAYS.toMillis(1);

   static final private long EarliestAllowableDateOfBirth;
   static final private long LatestAllowableDateOfBirth;

   static
   {
      final Calendar calendar = Calendar.getInstance();

      final int currentYear = calendar.get(Calendar.YEAR);

      calendar.set(currentYear - 120, 0, 1, 0, 0, 0);
      EarliestAllowableDateOfBirth = calendar.getTimeInMillis();

      calendar.set(currentYear, 0, 1, 0, 0, 0);
      LatestAllowableDateOfBirth = calendar.getTimeInMillis();
   }

   static final private char[] AuthenticationCodeSourceTable = {'A', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'P', 'R', 'T', 'W', 'X', 'Y', 'Z',
                                                                'r', 'o', 'y', 's', 'u', 'j', 'k', 'e', 'd',
                                                                '2', '3', '4', '7'};

   final private AtomicInteger accountIDCounter = new AtomicInteger();

   final private ConcurrentHashMap<Integer, FeedbactoryUserAccount> accountsByID = new ConcurrentHashMap<>(AccountCollectionInitialCapacity, 0.75f, FeedbactoryServerConstants.ServerConcurrency);
   final private ConcurrentHashMap<String, FeedbactoryUserAccount> accountsByEmail = new ConcurrentHashMap<>(AccountCollectionInitialCapacity, 0.75f, FeedbactoryServerConstants.ServerConcurrency);

   final private HousekeepingTask housekeepingTask = new HousekeepingTask();

   final private UserAccountMailer mailer = new UserAccountMailer();

   final private SignUpInterestRegistry signUpInterestRegistry = new SignUpInterestRegistry();


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class UserAccountManagerMetrics
   {
      final public boolean isHousekeepingEnabled;
      final public int housekeepingFrequencyDays;
      final public long housekeepingLastRunStartTime;

      final public int pendingAccountExpiryPeriodDays;
      final public int pendingAccounts;
      final public int activatedAccounts;

      final public int pendingEmailExpiryPeriodDays;
      final public int pendingEmails;

      final public int passwordResetCodeExpiryPeriodDays;
      final public int activePasswordResetCodes;


      private UserAccountManagerMetrics(final boolean isHousekeepingEnabled, final long housekeepingLastRunStartTime,
                                        final int pendingAccounts, final int activatedAccounts,
                                        final int pendingEmails,
                                        final int activePasswordResetCodes)
      {
         this.isHousekeepingEnabled = isHousekeepingEnabled;
         housekeepingFrequencyDays = (int) TimeUnit.MILLISECONDS.toDays(HousekeepingTaskFrequencyMilliseconds);
         this.housekeepingLastRunStartTime = housekeepingLastRunStartTime;

         pendingAccountExpiryPeriodDays = (int) TimeUnit.MILLISECONDS.toDays(PendingAccountExpiryPeriodMilliseconds);
         this.pendingAccounts = pendingAccounts;
         this.activatedAccounts = activatedAccounts;

         pendingEmailExpiryPeriodDays = (int) TimeUnit.MILLISECONDS.toDays(PendingEmailExpiryPeriodMilliseconds);
         this.pendingEmails = pendingEmails;

         passwordResetCodeExpiryPeriodDays = (int) TimeUnit.MILLISECONDS.toDays(PasswordResetCodeExpiryPeriodMilliseconds);
         this.activePasswordResetCodes = activePasswordResetCodes;
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

      /* This variable is written by the housekeeping thread, and read by the owner thread of the account manager.
       * At the moment there are no other metrics variables with which the housekeeping start time needs to be written atomically,
       * so marking it as volatile rather than using locking is fine.
       */
      volatile private long lastRunStartTime = FeedbactoryConstants.NoTime;


      @Override
      final public Thread newThread(final Runnable runnable)
      {
         final Thread thread = new Thread(runnable, "User account manager housekeeping task");
         thread.setDaemon(true);
         return thread;
      }


      @Override
      final public void run()
      {
         try
         {
            lastRunStartTime = TimeCache.getCurrentTimeMilliseconds();

            handleExpiredPendingAccounts();
            handleExpiredPendingEmails();
            handleExpiredPasswordResetCodes();
         }
         catch (final Exception anyException)
         {
            /* Exception handling provided for -any- exception, since any exceptions will otherwise be captured
             * by the enclosing FutureTask that is generated when this Runnable is submitted to ScheduledExecutorService.scheduleAtFixedRate().
             * Unhandled exceptions would also prevent further scheduleAtFixedRate() invocations from running.
             */
            FeedbactoryLogger.logSystemEvent(SystemLogLevel.ApplicationError, getClass(), "User account manager housekeeping task failed", anyException);
         }
      }


      private void handleExpiredPendingAccounts()
      {
         /* Iterating over the userAccountsByID collection ensures no double handling (or worse) accounts which have had multiple
          * email addresses associated with them. There is however the very small possibility of still encountering the same email with two different
          * IDs, due to the non-atomic rollback performed by the handleAddNewAccount() method, ie. two accounts may be inserted into the userAccountsByID
          * collection, with one almost immediately removed if it turns out that the email has already been registered in the userAccountsByEmail collection.
          * 
          * What I don't want to do with these redundant accounts is unconditionally remove their associated email from the userAccountsByEmail collection,
          * because although the user account added to userAccountsByID is redundant, the entry in userAccountsByEmail for that email is not!
          * During uptime and active connections, I would almost surely be safe with an unconditional removal against userAccountsByEmail since I can safely
          * assume that these momentarily newly created redundant accounts - added and then removed by handleAddNewAccount - will not
          * be due to expire since they've only just been created an instant earlier. However what about a housekeeping run immediately after restoration,
          * perhaps after the expiry time has passed? For this reason I do need to place the conditional remove() call on userAccountsByEmail: only remove the
          * user account entry in that collection if the user account is NOT the redundant copy. Note that the redundant entry in userAccountsByID is still
          * removed, however.
          * 
          * So, as a side effect of purging expired accounts, it turns out that the housekeeping task is also handy for automatically removing those
          * redundant (orphaned) userAccountsByID entries which may have been momentarily present at the time of persistence. This saves from performing an
          * additional 'cleaning' operation at the time of restoring the saved user accounts from disk.
          */
         final Iterator<Entry<Integer, FeedbactoryUserAccount>> entries = accountsByID.entrySet().iterator();

         FeedbactoryUserAccount activationPendingAccount;

         while (entries.hasNext())
         {
            activationPendingAccount = entries.next().getValue();

            synchronized (activationPendingAccount)
            {
               /* If the activate user account method hits its sync block first, the user account will be flagged as Activated. If so,
                * the housekeeping task needs to abort the deregistration of that user account.
                */
               if ((activationPendingAccount.getActivationState() == ActivationState.NotActivated) &&
                   ((TimeCache.getCurrentTimeMilliseconds() - activationPendingAccount.getEmailConfirmationCodeLastUpdatedTime()) >= PendingAccountExpiryPeriodMilliseconds))
               {
                  entries.remove();

                  // Here's the conditional removal - very important. See the comments above.
                  accountsByEmail.remove(UserAccountUtilities.getNormalisedEmail(activationPendingAccount.getEmail()), activationPendingAccount);

                  activationPendingAccount.setActivationState(ActivationState.Expired);
               }
            }
         }
      }


      private void handleExpiredPendingEmails()
      {
         final Iterator<Entry<String, FeedbactoryUserAccount>> entries = accountsByEmail.entrySet().iterator();

         Entry<String, FeedbactoryUserAccount> accountEntry;
         FeedbactoryUserAccount account;

         while (entries.hasNext())
         {
            accountEntry = entries.next();
            account = accountEntry.getValue();

            synchronized (account)
            {
               /* The following logic is deceptively tricky due to the properties of the accountsByEmail collection:
                * - At a minimum, account objects in the accountsByEmail collection are keyed by the active email address.
                * - Account objects in accountsByEmail are also keyed by all previously confirmed email addresses, if any.
                * - Account objects in accountsByEmail are also temporarily keyed by a pending email address, if any.
                * - Only the keys for expiring pending emails should ever be removed; never the active email key nor keys for previous active emails.
                * - When the expiring pending email data on the account object is cleared, the pending email key must be removed at the same time to ensure that the
                *   object is in a consistent state. Otherwise the pending key would then effectively be the same as a previously confirmed email key.
                * - Where a user has attempted to take an email that is already attached to a different account, their account object will be tagged with a non-null
                *   pending email -however- their account will not be keyed via that pending email in the accountsByEmail collection.
                *   See the memo notes at the top of the class for more details, also refer to the comments in handleUpdateAccountEmail().
                *
                * The result of this is that care needs to be taken when expiring pending emails to ensure that account objects are not temporarily
                * put in an inconsistent state. Of particular note is the accountEntry.getKey().equals(account.getPendingEmail()) check, which ensures
                * that only the -pending- email key will be removed from accountsByEmail and furthermore this will be done at the same time as the object's pending
                * email data is cleared; it would not be good to do the latter step for example using the account object's active email key, leaving the
                * collection with a phantom pending email key.
                */
               if ((account.getPendingEmail() != null) &&
                  ((TimeCache.getCurrentTimeMilliseconds() - account.getEmailConfirmationCodeLastUpdatedTime()) >= PendingEmailExpiryPeriodMilliseconds))
               {
                  // Be careful not to remove a key entry for an active (non-pending) email.
                  if (accountEntry.getKey().equals(UserAccountUtilities.getNormalisedEmail(account.getPendingEmail())))
                  {
                     /* Remove key entry for a pending email which has not been confirmed before the expiry time.
                      * At the same time, clear the account object's pending email data.
                      */
                     account.setPendingEmail(null);
                     account.setEmailConfirmationCode(null);
                     entries.remove();
                  }
                  else if (! account.hasEmailConfirmationCode())
                  {
                     /* If the account has a 'faked' pending email, ie. a pending email which is already attached to a different account (which can't be revealed to the user),
                      * the account won't be attached to accountsByEmail via that pending email key. Also it won't even have an email confirmation code.
                      * So, the only work to do is to null the pending email.
                      */
                     account.setPendingEmail(null);
                  }
               }
            }
         }
      }


      private void handleExpiredPasswordResetCodes()
      {
         final Iterator<Entry<Integer, FeedbactoryUserAccount>> entries = accountsByID.entrySet().iterator();

         FeedbactoryUserAccount account;

         while (entries.hasNext())
         {
            account = entries.next().getValue();

            synchronized (account)
            {
               if (account.hasPasswordResetCode() &&
                  ((TimeCache.getCurrentTimeMilliseconds() - account.getPasswordResetCodeLastUpdatedTime()) >= PasswordResetCodeExpiryPeriodMilliseconds))
                  account.setPasswordResetCode(null);
            }
         }
      }


      /****************************************************************************
       * 
       ***************************************************************************/


      private boolean isHousekeepingStarted()
      {
         return (executor != null);
      }


      private void start()
      {
         if (isHousekeepingStarted())
            throw new IllegalStateException("Housekeeping task has already been started.");

         executor = new ScheduledThreadPoolExecutor(1, this);
         executor.setKeepAliveTime(10, TimeUnit.SECONDS);
         executor.allowCoreThreadTimeOut(true);

         executor.scheduleAtFixedRate(this, 0, HousekeepingTaskFrequencyMilliseconds, TimeUnit.MILLISECONDS);
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


   // Caller must be sync'd on the user account, and email parameter already normalised.
   private boolean isActiveEmailForActivatedAccount(final String normalisedEmail, final FeedbactoryUserAccount account)
   {
      return (account.getActivationState() == ActivationState.Activated) && UserAccountUtilities.getNormalisedEmail(account.getEmail()).equals(normalisedEmail);
   }


   private String generateAuthenticationCode(final int codeLength)
   {
      assert ((codeLength % 6) == 0) : "The authentication code length must be evenly divisible by 6";

      final int integersPerCode = (codeLength / 6);
      final int andMask = 0b11111;

      final SecureRandom secureRandom = new SecureRandom();
      final StringBuilder builder = new StringBuilder();

      int nextInt;
      int indexValue;

      for (int roundNumber = 0; roundNumber < integersPerCode; roundNumber ++)
      {
         nextInt = secureRandom.nextInt();

         for (int shiftNumber = 0; shiftNumber < 6; shiftNumber ++)
         {
            indexValue = nextInt & andMask;

            builder.append(AuthenticationCodeSourceTable[indexValue]);

            /* Don't preserve the high order bit, makes no operational difference since only the lower 30 bits are used, but it does make
             * it easier to view the output if I want to debug using Integer.toBinaryString(nextInteger)..
             */
            nextInt >>>= 5;
         }
      }

      return builder.toString();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   // May be used later.
   private void appendGetFeedbactoryEmailInstructions(final StringBuilder emailTextBuilder)
   {
      emailTextBuilder.append("If you haven't already installed Feedbactory, you can download the latest version at www.feedbactory.com/download\n\n");
   }


   private void appendActivationEmailInstructions(final StringBuilder emailTextBuilder)
   {
      emailTextBuilder.append("Activating your account is easy. Open the Feedbactory application, press the account button (\"Not Signed In\") ");
      emailTextBuilder.append("on the browser toolbar and then click the \"Activate Account\" link at the bottom of the sign in form. From there, ");
      emailTextBuilder.append("enter the activation code along with an initial password to complete the registration and automatically sign in to your new account.\n\n");
      emailTextBuilder.append("Please note that your Feedbactory account will automatically expire if it's not activated within approximately two weeks.\n\n");
   }


   private void appendResetPasswordEmailInstructions(final StringBuilder emailTextBuilder)
   {
      emailTextBuilder.append("If you've forgotten your password, you can reset it by following these steps:\n\n");
      emailTextBuilder.append("Open the Feedbactory application and press the account button (\"Not Signed In\") on the browser toolbar. ");
      emailTextBuilder.append("Then, click the \"Forgot Password\" link alongside the sign in prompt, enter your Feedbactory account email, and press the ");
      emailTextBuilder.append("\"Send Code\" button to have a password reset code emailed to you. Follow the emailed instructions to reset your password.\n\n");
   }


   private void appendExpiredNonActivatedAccountNotification(final StringBuilder emailTextBuilder)
   {
      emailTextBuilder.append("If the account had previously been registered but not activated using an activation code, the two week activation period ");
      emailTextBuilder.append("may have expired in which case the account has been automatically deleted and you will need to re-register.\n\n");
   }


   private void appendFeedbactoryEmailFooter(final StringBuilder emailTextBuilder)
   {
      emailTextBuilder.append("For more information, visit feedbactory.com\n");
   }


   // May be used later.
   private void sendNewEmailConfirmationReminderEmail(final FeedbactoryUserAccount emailPendingAccount)
   {
      final StringBuilder emailTextBuilder = new StringBuilder();
      emailTextBuilder.append("This is a friendly reminder to let you know that your updated email hasn't yet been confirmed for your Feedbactory account. ");
      emailTextBuilder.append("Your account will automatically revert to your previous email (");
      emailTextBuilder.append(emailPendingAccount.getEmail());
      emailTextBuilder.append(") in one week if you don't confirm this new email.\n\n");
      emailTextBuilder.append("Your email confirmation code is: ");
      emailTextBuilder.append(emailPendingAccount.getEmailConfirmationCode());
      emailTextBuilder.append(".\n\n");
      emailTextBuilder.append("To confirm your updated email, open the Feedbactory application and if you're not automatically signed in, click the ");
      emailTextBuilder.append("account (\"Not Signed In\") button on the browser toolbar and enter your email and password to sign in. You must use your previous email ");
      emailTextBuilder.append("to sign in until your updated email has been confirmed. Once you're signed in, select the Account option under the Options menu ");
      emailTextBuilder.append("to bring up the account management page, then click on the highlighted \"Enter Confirmation Code\" button. Enter the above code along ");
      emailTextBuilder.append("with your existing password to confirm your new email address.");

      mailer.sendEmail(emailPendingAccount.getPendingEmail(), "Confirm updated Feedbactory email reminder", emailTextBuilder.toString());
   }


   // May be used later.
   private void sendAccountActivationReminderEmail(final FeedbactoryUserAccount activationPendingAccount)
   {
      final StringBuilder emailTextBuilder = new StringBuilder(1000);
      emailTextBuilder.append("This is a friendly reminder to let you know that your new Feedbactory account hasn't yet been activated ");
      emailTextBuilder.append("and will automatically expire in one week if you don't confirm your email.\n\n");
      emailTextBuilder.append("Your account activation code is: ");
      emailTextBuilder.append(activationPendingAccount.getEmailConfirmationCode());
      emailTextBuilder.append(".\n\n");
      emailTextBuilder.append("Activating your account is easy. Open the Feedbactory application, press the ");
      emailTextBuilder.append("account (\"Not Signed In\") button on the browser toolbar and click the \"Activate Account\" link at the bottom of the sign in form. From there, ");
      emailTextBuilder.append("enter the activation code along with an initial password to complete the registration and automatically sign in to your new account.");

      mailer.sendEmail(activationPendingAccount.getEmail(), "Your non-activated Feedbactory account will soon expire", emailTextBuilder.toString());
   }


   private void sendNewAccountEmail(final String email, final String activationCode)
   {
      final StringBuilder emailTextBuilder = new StringBuilder(1000);
      emailTextBuilder.append("Thank you for signing up to Feedbactory.\n\n");
      emailTextBuilder.append("Your account activation code is: ");
      emailTextBuilder.append(activationCode);
      emailTextBuilder.append(".\n\n");
      emailTextBuilder.append("Please note that your Feedbactory account will automatically expire if it's not activated within approximately two weeks.\n\n");
      appendFeedbactoryEmailFooter(emailTextBuilder);

      mailer.sendEmail(email, "Please confirm your Feedbactory account email", emailTextBuilder.toString());
   }


   private void sendExistingNonActivatedAccountEmail(final String email, final String activationCode)
   {
      final StringBuilder emailTextBuilder = new StringBuilder(1000);
      emailTextBuilder.append("A new Feedbactory account was requested for this email, however an account has already been registered for it.\n\n");
      emailTextBuilder.append("The account must be activated before you can sign in and submit feedback.\n\n");
      emailTextBuilder.append("Your account activation code is: ");
      emailTextBuilder.append(activationCode);
      emailTextBuilder.append(".\n\n");
      appendActivationEmailInstructions(emailTextBuilder);
      appendFeedbactoryEmailFooter(emailTextBuilder);

      mailer.sendEmail(email, "Please confirm your Feedbactory account email", emailTextBuilder.toString());
   }


   private void sendExistingAccountEmail(final String email)
   {
      final StringBuilder emailTextBuilder = new StringBuilder(1000);
      emailTextBuilder.append("A new Feedbactory account was requested for this email, however an account has already been registered for it.\n\n");
      appendResetPasswordEmailInstructions(emailTextBuilder);
      appendFeedbactoryEmailFooter(emailTextBuilder);

      mailer.sendEmail(email, "Feedbactory account registration", emailTextBuilder.toString());
   }


   private void sendExistingAccountSupersededEmail(final String email)
   {
      /* Don't give away the new email. If a user's old email has been hacked and the original owner no longer has access to it,
       * they'd probably rather not have references to the new one appearing in messages sent to their old email address.
       */
      final StringBuilder emailTextBuilder = new StringBuilder(1000);
      emailTextBuilder.append("A new Feedbactory account was requested for this email, however an account has already been registered for it.\n\n");
      emailTextBuilder.append("Our records indicate that the account is now attached to a different email, which must be used to sign in to the account.\n\n");
      appendResetPasswordEmailInstructions(emailTextBuilder);
      appendFeedbactoryEmailFooter(emailTextBuilder);

      mailer.sendEmail(email, "Feedbactory account registration", emailTextBuilder.toString());
   }


   private void sendAlreadyActivatedAccountEmail(final String email)
   {
      final StringBuilder emailTextBuilder = new StringBuilder(1000);
      emailTextBuilder.append("A Feedbactory account activation code was requested for this email, however the account has already been activated.\n\n");
      appendResetPasswordEmailInstructions(emailTextBuilder);
      appendFeedbactoryEmailFooter(emailTextBuilder);

      mailer.sendEmail(email, "Feedbactory account activation", emailTextBuilder.toString());
   }


   private void sendAlreadyActivatedAccountSupersededEmail(final String email)
   {
      /* Don't give away the new email. If a user's old email has been hacked and the original owner no longer has access to it,
       * they'd probably rather not have references to the new one appearing in messages sent to their old email address.
       */
      final StringBuilder emailTextBuilder = new StringBuilder(1000);
      emailTextBuilder.append("A Feedbactory account activation code was requested for this email, however the account has already been activated.\n\n");
      emailTextBuilder.append("Our records indicate that the account is now attached to a different email, which must be used to sign in to the account.\n\n");
      appendResetPasswordEmailInstructions(emailTextBuilder);
      appendFeedbactoryEmailFooter(emailTextBuilder);

      mailer.sendEmail(email, "Feedbactory account activation", emailTextBuilder.toString());
   }


   private void sendNonExistentAccountActivationCodeEmail(final String email)
   {
      final StringBuilder emailTextBuilder = new StringBuilder(1000);
      emailTextBuilder.append("A Feedbactory account activation code was requested for this email, however our records indicate ");
      emailTextBuilder.append("that there is no account registered for it.\n\n");
      appendExpiredNonActivatedAccountNotification(emailTextBuilder);
      appendFeedbactoryEmailFooter(emailTextBuilder);

      mailer.sendEmail(email, "Feedbactory account activation", emailTextBuilder.toString());
   }


   private void sendAccountPasswordResetCodeEmail(final String email, final String passwordResetCode)
   {
      final StringBuilder emailTextBuilder = new StringBuilder(1000);
      emailTextBuilder.append("A Feedbactory account password reset code was requested for this email.\n\n");
      emailTextBuilder.append("The password reset code is: ");
      emailTextBuilder.append(passwordResetCode);
      emailTextBuilder.append(".\n\n");
      emailTextBuilder.append("For your security the password reset code will automatically expire after approximately 24 hours.\n\n");
      appendFeedbactoryEmailFooter(emailTextBuilder);

      mailer.sendEmail(email, "Feedbactory account password reset code", emailTextBuilder.toString());
   }


   private void sendAccountPasswordResetCodeNonCurrentEmail(final String email)
   {
      /* Don't give away the new email. If a user's old email has been hacked and the original owner no longer has access to it,
       * they'd probably rather not have references to the new one appearing in messages sent to their old email address.
       * Further to that logic, it goes without saying that the password reset code should definitely not be sent to the old email.
       *
       * Also note that this wording needs to cover both the cases where the email specified is a previous email as well as the case where the email specified
       * is the account's pending email.
       */
      final StringBuilder emailTextBuilder = new StringBuilder(1000);
      emailTextBuilder.append("A Feedbactory account password reset code was requested for this email, however our records indicate ");
      emailTextBuilder.append("that the account is attached to a different email.\n\n");
      emailTextBuilder.append("The account's current email must be used to sign in to the account or to request a password reset.\n\n");
      appendFeedbactoryEmailFooter(emailTextBuilder);

      mailer.sendEmail(email, "Reset Feedbactory account password", emailTextBuilder.toString());
   }


   private void sendNonActivatedAccountPasswordResetEmail(final String email, final String activationCode)
   {
      final StringBuilder emailTextBuilder = new StringBuilder(1000);
      emailTextBuilder.append("A Feedbactory account password reset code was requested for this email, however our records indicate ");
      emailTextBuilder.append("that the account has not yet been activated.\n\n");
      emailTextBuilder.append("Your account activation code is: ");
      emailTextBuilder.append(activationCode);
      emailTextBuilder.append(".\n\n");
      appendActivationEmailInstructions(emailTextBuilder);
      appendFeedbactoryEmailFooter(emailTextBuilder);

      mailer.sendEmail(email, "Please confirm your Feedbactory account email", emailTextBuilder.toString());
   }


   private void sendNonExistentAccountPasswordResetEmail(final String email)
   {
      final StringBuilder emailTextBuilder = new StringBuilder(1000);
      emailTextBuilder.append("A Feedbactory account password reset code was requested for this email, however our records indicate ");
      emailTextBuilder.append("that there is no account registered for this email.\n\n");
      appendExpiredNonActivatedAccountNotification(emailTextBuilder);
      appendFeedbactoryEmailFooter(emailTextBuilder);

      mailer.sendEmail(email, "Reset Feedbactory account password", emailTextBuilder.toString());
   }


   private void sendUpdatedAccountEmail(final String email, final String updatedEmailConfirmationCode)
   {
      final StringBuilder emailTextBuilder = new StringBuilder(1000);
      emailTextBuilder.append("Thank you for updating your Feedbactory account email.\n\n");
      emailTextBuilder.append("Before you can sign in using this email, you need to confirm it by entering a confirmation code into the Feedbactory account management page.\n\n");
      emailTextBuilder.append("Your email confirmation code is: ");
      emailTextBuilder.append(updatedEmailConfirmationCode);
      emailTextBuilder.append(".\n\n");
      emailTextBuilder.append("Your account will automatically revert to your previous email if you don't confirm your new email within approximately 2 weeks.\n\n");
      appendFeedbactoryEmailFooter(emailTextBuilder);

      mailer.sendEmail(email, "Please confirm your Feedbactory account email", emailTextBuilder.toString());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private AddNewUserAccountResult handleAddNewAccount(final String email, final Gender gender, final long dateOfBirth, final boolean sendEmailAlerts,
                                                       final InetAddress requestIPAddress, final boolean sendEmail)
   {
      UserAccountUtilities.validateEmail(email);
      final String normalisedEmail = UserAccountUtilities.getNormalisedEmail(email);

      if (gender == null)
         throw new IllegalArgumentException("User account gender cannot be null.");
      else if ((dateOfBirth < EarliestAllowableDateOfBirth) || (dateOfBirth > LatestAllowableDateOfBirth))
         throw new IllegalArgumentException("Invalid user account date of birth: " + dateOfBirth);
      else if (requestIPAddress == null)
         throw new IllegalArgumentException("Invalid request IP address.");
      else if (accountsByID.size() >= AccountCapacity)
      {
         if (sendEmailAlerts)
            signUpInterestRegistry.registerSignUpInterest(normalisedEmail);

         // To maintain account/email anonymity, this result must be returned even when the account already exists.
         return AddNewUserAccountResult.CapacityReachedResult;
      }

      // Retry loop to handle the case where the housekeeping task marks an existing account as expired in between the check and the lock.
      for (;;)
      {
         FeedbactoryUserAccount existingAccount = getAccountByEmail(normalisedEmail);

         if (existingAccount == null)
         {
            final String initialEmailConfirmationCode = generateAuthenticationCode(FeedbactoryUserAccountConstants.EmailConfirmationCodeLength);

            final FeedbactoryUserAccount newAccount = new FeedbactoryUserAccount(accountIDCounter.getAndIncrement(), TimeCache.getCurrentTimeMilliseconds());

            // Synchronize to ensure the visibility of the initialised non-final fields to subsequent threads.
            synchronized (newAccount)
            {
               newAccount.setEmail(email);
               newAccount.setEmailConfirmationCode(initialEmailConfirmationCode);
               newAccount.setPasswordResetCode(null, FeedbactoryConstants.NoTime);
               newAccount.setGender(gender);
               newAccount.setDateOfBirth(dateOfBirth);
               newAccount.setSendEmailAlerts(sendEmailAlerts);
               newAccount.setMessage(Message.NoMessage);
               newAccount.setLastAuthenticatedIPAddress(requestIPAddress);
               newAccount.setActivationState(ActivationState.NotActivated);
            }

            accountsByID.put(newAccount.getID(), newAccount);

            existingAccount = accountsByEmail.putIfAbsent(normalisedEmail, newAccount);

            /* There's an interesting concurrency issue here during population of the user account collections. Although each collection is individually safe for multi-thread
             * access, population of both of them during account creation is not an atomic process. If not for the fact that the add to the userAccountsByID collection is
             * performed before the userAccountsByEmail.putIfAbsent(), there would be a small chance that, for example, this thread adds to the userAccountsByEmail collection
             * just before another thread makes a call to resend the account registration information. At that point, no corresponding record for the email would exist in the
             * userAccountsByID collection. It would be fine in that instance but only because I had already attached the email confirmation code to the user account above.
             * Clearly this is living a little dangerously and forcing me to consider the concurrency implications of the other user account ops more than I should have to.
             * So I've gone with a safer approach of adding to the userAccountsByID collection first even though there is the very rare possibility that a rollback/delete
             * may have to be performed immediately after performing the userAccountsByEmail.putIfAbsent() (see below).
             *
             * Another issue is that the collections are out of sync as another thread persists them to disk. The persistence issue isn't that much of a problem when I
             * consider that regardless of how the user account collections are populated, an autosave operation during server uptime is 'out of date' anyway as soon as
             * new accounts are added. If the server shuts down naturally, a final save needs to be performed after all connections become idle, at which time the collections
             * are guaranteed to be in a consistent state. During uptime and active connections there are two possibilities for the account data to be saved in an inconsistent
             * state:
             * 1. The persist operation might save two different accounts with different account IDs for the same email address, due to the non-atomic rollback used in
             *    this method when there is already an email address. However the orphaned user account (that linked to no emails in userAccountsByEmail) will eventually
             *    be cleaned up by the housekeeper - refer to the comments against it.
             * 2. A new account is added midway through the persistence task, resulting in no entry for the account in the accountsByID file but an orphaned entry
             *    in the accountsByEmail file. In this case the restoration process must be careful to drop any orphaned accountsByEmail entries for which there are no
             *    corresponding accountsByID objects.
             *
             * What about races between this add new account operation and the housekeeper? Considering a race between an expired account in housekeeping and a proposed new
             * account of the same email, there are two possible outcomes: 1) this thread runs its existingAccount check first and finds one, then retries the entire
             * process as part of a loop when it grabs a lock on the existing object and sees that it has expired. Or 2) the housekeeping task removes the email from the
             * userAccountsByEmail collection before the putIfAbsent call here, in which case from this method's perspective it's as if the account never existed at all
             * and this method adds the new account to both accountsByID and accountsByEmail.
             */
            if (existingAccount == null)
            {
               // Expected case: new account, no previous references to the email address. Send the welcome email, then bail out.
               if (sendEmail)
                  sendNewAccountEmail(email, initialEmailConfirmationCode);

               return new AddNewUserAccountResult(AddNewUserAccountStatus.Success, newAccount);
            }
            else // Rollback - remove the account just added, an account having that email has just been created on a different thread.
               accountsByID.remove(newAccount.getID());
         }

         // If there is an existing account...
         synchronized (existingAccount)
         {
            if (existingAccount.getActivationState() == ActivationState.Expired)
            {
               /* Extremely unlikely but possible - the housekeeping thread has just expired the account and removed it from the collection.
                * The process of adding, checking, and locking must be repeated.
                */
               continue;
            }
            else if (existingAccount.getActivationState() == ActivationState.NotActivated)
            {
               /* It's possible that users will sign up for Feedbactory accounts using emails that they don't own. They can't activate the accounts
                * without access to the email, but they can set the sign up parameters of the user - gender, and date of birth. If the legit email owner
                * then tries to sign up, they should be able to override any existing values for a non-activated account.
                * I think that the email activation code (and time) should also be reset. Firstly because the legit email owner should have the full
                * time period to activate their account after they sign up before the account expires. And if I'm going to allow the time extension I think
                * the code must also be reset otherwise it allows attackers effectively unlimited time to work at the one code.
                * A drawback is that it opens up a loophole that would allow non-activated accounts to be sitting around forever so long as they are periodicially
                * re-registered without being activated. However is this really any different to allowing a non-activated account to expire and then immediately
                * re-registering it?
                * Another drawback is that it potentially allows attackers a small window of time to repeatedly reset the activation code of other people's
                * accounts, however the risk is small since the target account must be in a non-activated state for this to be a problem.
                */
               final String initialEmailConfirmationCode = generateAuthenticationCode(FeedbactoryUserAccountConstants.EmailConfirmationCodeLength);

               existingAccount.setEmailConfirmationCode(initialEmailConfirmationCode);
               existingAccount.setGender(gender);
               existingAccount.setDateOfBirth(dateOfBirth);
               existingAccount.setSendEmailAlerts(sendEmailAlerts);

               if (sendEmail)
                  sendExistingNonActivatedAccountEmail(email, existingAccount.getEmailConfirmationCode());
            }
            else if (sendEmail && (existingAccount.getActivationState() == ActivationState.Activated) && (existingAccount.getPendingEmail() == null))
            {
               /* Send notification emails only if the existing account holder has no pending email change, otherwise the email directed to their old email address
                * would probably be confusing; they will have already received an email prompting them to confirm their new email address.
                * If they missed the first email it's more likely that they'll sign in again and have it resent.
                */
               if (UserAccountUtilities.getNormalisedEmail(existingAccount.getEmail()).equals(normalisedEmail))
               {
                  // The most likely case of a new sign up using an already taken, activated, and current email.
                  sendExistingAccountEmail(email);
               }
               else
               {
                  /* A new sign up request where the requested email address has already been taken by an active account, however the email
                   * is no longer the current email for the existing account.
                   */
                  sendExistingAccountSupersededEmail(email);
               }
            }
         }

         return new AddNewUserAccountResult(AddNewUserAccountStatus.EmailAccountExists, existingAccount);
      }
   }


   private void handleResendAccountActivationCode(final String email)
   {
      final String normalisedEmail = UserAccountUtilities.getNormalisedEmail(email);
      final FeedbactoryUserAccount account = getAccountByEmail(normalisedEmail);

      if (account != null)
      {
         synchronized (account)
         {
            switch (account.getActivationState())
            {
               case NotActivated:
                  sendNewAccountEmail(email, account.getEmailConfirmationCode());
                  break;

               case Activated:

                  if (UserAccountUtilities.getNormalisedEmail(account.getEmail()).equals(normalisedEmail))
                     sendAlreadyActivatedAccountEmail(email);
                  else
                     sendAlreadyActivatedAccountSupersededEmail(email);

                  break;

               case Expired:
                  // Effectively the account does not exist - same as the non-existent account case handled below except that the email has already been validated.
                  sendNonExistentAccountActivationCodeEmail(email);
                  break;

               default:
                  throw new AssertionError("Unhandled user account activation state: " + account.getActivationState());
            }
         }
      }
      else
      {
         // Validate the unknown email before using it.
         UserAccountUtilities.validateEmail(email);
         sendNonExistentAccountActivationCodeEmail(email);
      }
   }


   private FeedbactoryUserAccount handleActivateAccount(final String email, final String emailConfirmationCode, final byte[] initialPasswordHash, final InetAddress requestIPAddress)
   {
      if (initialPasswordHash.length != FeedbactoryUserAccountConstants.PasswordHashLengthBytes)
         throw new IllegalArgumentException("Invalid password hash length.");
      else if (requestIPAddress == null)
         throw new IllegalArgumentException("Invalid request IP address.");

      final String normalisedEmail = UserAccountUtilities.getNormalisedEmail(email);
      final FeedbactoryUserAccount account = getAccountByEmail(normalisedEmail);

      if (account != null)
      {
         synchronized (account)
         {
            /* If our expired pending accounts housekeeping task hits its sync block first, the user account's activation status will be set to Expired,
             * and we must use this as an indicator that we have just missed the boat.
             *
             * Also, once activated we must ensure that the code cannot be used repeatedly to access the account, otherwise this would allow attackers
             * unlimited opportunity over time to attempt to effectively sign in to the account, or at least to determine that an account is attached to
             * a particular email (if we were for example to retain the activation code in a separate field after activation and respond to further
             * activation attempts with "your account is already activated" within the UI).
             *
             * Users who genuinely forget that they've already activated their account can always use the password reset feature.
             *
             * Also for now I'm not sending a confirmation email to the user after they've activated their account, I may decide to
             * change this later if there's any extra information that they should receive at that point...
             */
            if ((account.getActivationState() == ActivationState.NotActivated) && account.authenticateEmailConfirmationCode(emailConfirmationCode))
            {
               account.setEmailConfirmationCode(null);
               account.setPasswordHash(initialPasswordHash);

               account.setLastAuthenticatedIPAddress(requestIPAddress);

               account.setActivationState(ActivationState.Activated);

               return account;
            }
         }
      }

      return null;
   }


   private FeedbactoryUserAccount handleAuthenticateAccountSignIn(final String email, final byte[] passwordHash, final InetAddress requestIPAddress)
   {
      if (requestIPAddress == null)
         throw new IllegalArgumentException("Invalid request IP address.");

      final String normalisedEmail = UserAccountUtilities.getNormalisedEmail(email);
      final FeedbactoryUserAccount account = getAccountByEmail(normalisedEmail);

      if (account != null)
      {
         synchronized (account)
         {
            /* Since non-activated accounts do not yet have a password set for them, it's not possible for a user to successfully authenticate for
             * a non-activated account, hence the authentication outcome can never be SuccessAccountNotActivated.
             */
            if (isActiveEmailForActivatedAccount(normalisedEmail, account) && account.authenticatePasswordHash(passwordHash))
            {
               account.setLastAuthenticatedIPAddress(requestIPAddress);
               return account;
            }
         }
      }

      return null;
   }


   private void handleSendPasswordResetCode(final String email)
   {
      final String normalisedEmail = UserAccountUtilities.getNormalisedEmail(email);
      final FeedbactoryUserAccount account = getAccountByEmail(normalisedEmail);

      if (account != null)
      {
         synchronized (account)
         {
            if (account.getActivationState() == ActivationState.Activated)
            {
               if (UserAccountUtilities.getNormalisedEmail(account.getEmail()).equals(normalisedEmail))
               {
                  /* If the account already has a password reset code, don't update it since this would provide a means for malicious parties to
                   * continually change the password reset codes of other accounts.
                   * Also don't reset the timestamp on the code, since this would effectively provide an unlimited attack window for the
                   * one reset code.
                   */
                  if (! account.hasPasswordResetCode())
                  {
                     final String passwordResetCode = generateAuthenticationCode(FeedbactoryUserAccountConstants.PasswordResetCodeLength);
                     account.setPasswordResetCode(passwordResetCode);
                  }

                  sendAccountPasswordResetCodeEmail(email, account.getPasswordResetCode());
               }
               else
               {
                  /* Note that this covers both the cases where the email is a previous email AND the case where the email specified
                   * is the account's pending email.
                   */
                  sendAccountPasswordResetCodeNonCurrentEmail(email);
               }
            }
            else if (account.getActivationState() == ActivationState.NotActivated)
               sendNonActivatedAccountPasswordResetEmail(email, account.getEmailConfirmationCode());
            else if (account.getActivationState() == ActivationState.Expired)
            {
               // Effectively the account does not exist - same as the non-existent account case handled below except that the email has already been validated.
               sendNonExistentAccountPasswordResetEmail(email);
            }
         }
      }
      else
      {
         // Validate the unknown email before using it.
         UserAccountUtilities.validateEmail(email);
         sendNonExistentAccountPasswordResetEmail(email);
      }
   }


   private FeedbactoryUserAccount handleResetPassword(final String email, final String passwordResetCode, final byte[] newPasswordHash, final InetAddress requestIPAddress)
   {
      if (newPasswordHash.length != FeedbactoryUserAccountConstants.PasswordHashLengthBytes)
         throw new IllegalArgumentException("Invalid password hash length");
      else if (requestIPAddress == null)
         throw new IllegalArgumentException("Invalid request IP address.");

      final String normalisedEmail = UserAccountUtilities.getNormalisedEmail(email);
      final FeedbactoryUserAccount account = getAccountByEmail(normalisedEmail);

      if (account != null)
      {
         synchronized (account)
         {
            // At the moment, password reset codes cannot be requested for pending emails.
            if (isActiveEmailForActivatedAccount(normalisedEmail, account) && account.authenticatePasswordResetCode(passwordResetCode))
            {
               account.setPasswordResetCode(null);
               account.setPasswordHash(newPasswordHash);

               account.setLastAuthenticatedIPAddress(requestIPAddress);

               return account;
            }
         }
      }

      return null;
   }


   private void handleUpdateAccountEmail(final FeedbactoryUserAccount account, final String newEmail)
   {
      synchronized (account)
      {
         if (account.getActivationState() != ActivationState.Activated)
            throw new IllegalStateException("Cannot update email for non-activated account, ID " + account.getID());

         final String normalisedNewEmail = UserAccountUtilities.getNormalisedEmail(newEmail);

         if (account.getPendingEmail() != null)
         {
            final String normalisedPendingEmail = UserAccountUtilities.getNormalisedEmail(account.getPendingEmail());

            if (normalisedNewEmail.equals(normalisedPendingEmail))
            {
               /* If the nominated new email is case-insensitive equal to an existing pending email, there's not much work to do,
                * and none at all if the emails are identical.
                */
               if (! account.getPendingEmail().equals(newEmail))
               {
                  // Validate the case-different new email (just in case..?), and update the pending email on the account.
                  UserAccountUtilities.validateEmail(newEmail);
                  account.setPendingEmail(newEmail);
               }

               // Nothing more needs to be done in this instance.
               return;
            }
            else
            {
               /* If the account already has a pending email (which we know is more than case-different to the new one that they've nominated),
                * deregister it from the accountsByEmail collection but only if they actually do own it!
                * Due to the way that we are blinding users to which other email accounts are registered, it's possible
                * for a user account object's pending email to be set to one which is already attached to a different user account.
                */
               accountsByEmail.remove(normalisedPendingEmail, account);
               account.setPendingEmail(null);
               account.setEmailConfirmationCode(null);
            }
         }

         if (normalisedNewEmail.equals(UserAccountUtilities.getNormalisedEmail(account.getEmail())))
         {
            /* Similar to the case-insensitive equal pending email check - there is very little or possibly no work to do from here.
             * The account's password hash will be unchanged if the normalised emails are equal.
             */
            if (! account.getEmail().equals(newEmail))
            {
               UserAccountUtilities.validateEmail(newEmail);
               account.setEmail(newEmail);
            }
         }
         else
         {
            UserAccountUtilities.validateEmail(newEmail);

            final FeedbactoryUserAccount existingAccountForNewEmail = accountsByEmail.putIfAbsent(normalisedNewEmail, account);

            if ((existingAccountForNewEmail == null) || (existingAccountForNewEmail == account))
            {
               /* The regular case (existingAccountForNewEmail == null):
                * The new email address was available and has now been taken by the user (confirmation pending).
                *
                * The less regular case: (existingAccountForNewEmail == account):
                * The 'new' email nominated by the user happens to be one of their previous emails.
                * We know that the previous email must have been one that had been already confirmed by the user because cancelled pending emails are not
                * kept in the userAccountsByEmail collection. If the pending email had expired, the housekeeping task would have removed it. And if the user
                * had changed their mind about a pending email address, we would have removed it above in this method; both tasks operate with a lock on the user account.
                * It would be nice to simply switch back to the previous confirmed email with no further action on the user's part, however the user's password
                * hash also needs to be regenerated client side for the updated email. So unfortunately they will be prompted to reconfirm their old email
                * with both a code and existing password. This case of course should be very rare.
                */
               final String emailConfirmationCode = generateAuthenticationCode(FeedbactoryUserAccountConstants.EmailConfirmationCodeLength);

               account.setPendingEmail(newEmail);
               account.setEmailConfirmationCode(emailConfirmationCode);

               sendUpdatedAccountEmail(newEmail, emailConfirmationCode);
            }
            else
            {
               /* The irregular case - the new email nominated by the user is already attached to a different user's account. We don't want to reveal the
                * presence of someone else's registration, so we present a front to the user: we update the user's pending email without generating a new
                * confirmation code. The setEmailConfirmationCode(null) is however required to ensure that the timestamp is updated and that the
                * faked pending email will eventually expire as expected.
                */
               account.setPendingEmail(newEmail);
               account.setEmailConfirmationCode(null);
            }
         }
      }
   }


   private boolean handleResendConfirmNewEmailCode(final FeedbactoryUserAccount account)
   {
      synchronized (account)
      {
         /* Note that it's possible for a user to have a non-null pending email while having no email confirmation code,
          * in the case of a user attempting to update their email to that of an already registered email (belonging to a different account).
          * In this case, no email should be sent. However we don't want to tip off that user that the email is already attached to
          * an existing account. So we can return a result of success as long as the user has a pending email - even a dummy one, without
          * a possible confirmation code.
          */
         if (account.getActivationState() != ActivationState.Activated)
            throw new IllegalStateException("Cannot resend email confirmation code for non-activated account, ID " + account.getID());

         if (account.hasEmailConfirmationCode())
            sendUpdatedAccountEmail(account.getPendingEmail(), account.getEmailConfirmationCode());

         return (account.getPendingEmail() != null);
      }
   }


   /* When we update a user's email address, we also need to update their password hash, since the email is used as part of the salt on the
    * password generation hash. If we didn't update the password hash to match that generated by the user with their new email, the hash used
    * by the user to authenticate would no longer match the one that we have stored.
    * So, when a user updates their email address they need to supply their existing password, firstly to generate the new hash to use with the new email,
    * and also of course to generate the existing password hash so that we can be sure that, even though the user is already authenticated for the current
    * session, the new hash was definitely produced using their correct password.
    */
   private boolean handleConfirmNewEmail(final FeedbactoryUserAccount account, final String emailConfirmationCode, final byte[] passwordHash, final byte[] newEmailPasswordHash)
   {
      if (newEmailPasswordHash.length != FeedbactoryUserAccountConstants.PasswordHashLengthBytes)
         throw new IllegalArgumentException("Invalid password hash length");

      synchronized (account)
      {
         if (account.getActivationState() != ActivationState.Activated)
            throw new IllegalStateException("Cannot confirm change of email for non-activated account, ID " + account.getID());

         if (account.authenticatePasswordHash(passwordHash) && account.authenticateEmailConfirmationCode(emailConfirmationCode))
         {
            account.setEmail(account.getPendingEmail());
            account.setPasswordHash(newEmailPasswordHash);
            account.setPendingEmail(null);
            account.setEmailConfirmationCode(null);

            return true;
         }
         else
            return false;
      }
   }


   private boolean handleUpdateAccountPasswordHash(final FeedbactoryUserAccount account, final byte[] passwordHash, final byte[] newPasswordHash)
   {
      if (newPasswordHash.length != FeedbactoryUserAccountConstants.PasswordHashLengthBytes)
         throw new IllegalArgumentException("Invalid password hash length");

      synchronized (account)
      {
         if (account.getActivationState() != ActivationState.Activated)
            throw new IllegalStateException("Cannot update password hash for non-activated account, ID " + account.getID());

         if (account.authenticatePasswordHash(passwordHash))
         {
            account.setPasswordHash(newPasswordHash);
            account.setPasswordResetCode(null);

            return true;
         }
         else
            return false;
      }
   }


   private void handleUpdateSendEmailAlerts(final FeedbactoryUserAccount account, final boolean sendEmailAlerts)
   {
      synchronized (account)
      {
         if (account.getActivationState() != ActivationState.Activated)
            throw new IllegalStateException("Cannot update send email alerts for non-activated account, ID " + account.getID());

         account.setSendEmailAlerts(sendEmailAlerts);
      }
   }


   private TimestampedMessage handleGetAccountMessage(final int accountID)
   {
      final FeedbactoryUserAccount account = getAccountByID(accountID);

      if (account != null)
      {
         synchronized (account)
         {
            return account.getMessage();
         }
      }
      else
         return null;
   }


   private FeedbactoryUserAccount handleSetUserAccountMessage(final int accountID, final Message message)
   {
      if (message == null)
         throw new IllegalArgumentException("User account message cannot be null.");

      final FeedbactoryUserAccount account = getAccountByID(accountID);

      if (account != null)
      {
         synchronized (account)
         {
            account.setMessage(message);
         }
      }

      return account;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   /* Refer to the concurrency memos at the top of the class, within handleAddNewAccount, and within the housekeeper regarding the integrity of the data of both
    * the save and restore methods.
    */
   private void handleSaveCheckpoint(final Path checkpointPath) throws IOException
   {
      final File accountDataFile = checkpointPath.resolve(AccountDataFilename).toFile();
      final File accountEmailsFile = checkpointPath.resolve(AccountEmailsFilename).toFile();

      try
      (
         final DataOutputStream accountDataStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(accountDataFile)));
         final DataOutputStream accountEmailsStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(accountEmailsFile)));
      )
      {
         byte[] lastAuthenticatedIPAddress;

         for (final FeedbactoryUserAccount account : accountsByID.values())
         {
            synchronized (account)
            {
               if (account.getActivationState() == ActivationState.Expired)
                  continue;

               accountDataStream.writeInt(account.getID().intValue());

               accountDataStream.writeUTF(account.getEmail());

               if (account.getPendingEmail() != null)
               {
                  accountDataStream.writeBoolean(true);
                  accountDataStream.writeUTF(account.getPendingEmail());
               }
               else
                  accountDataStream.writeBoolean(false);

               if (account.hasEmailConfirmationCode())
               {
                  accountDataStream.writeBoolean(true);
                  accountDataStream.writeUTF(account.getEmailConfirmationCode());
                  accountDataStream.writeLong(account.getEmailConfirmationCodeLastUpdatedTime());
               }
               else
                  accountDataStream.writeBoolean(false);

               if (account.hasPasswordHash())
               {
                  accountDataStream.writeBoolean(true);
                  accountDataStream.write(account.getPasswordHash());
               }
               else
                  accountDataStream.writeBoolean(false);

               if (account.hasPasswordResetCode())
               {
                  accountDataStream.writeBoolean(true);
                  accountDataStream.writeUTF(account.getPasswordResetCode());
                  accountDataStream.writeLong(account.getPasswordResetCodeLastUpdatedTime());
               }
               else
                  accountDataStream.writeBoolean(false);

               accountDataStream.writeByte(account.getGender().value);
               accountDataStream.writeLong(account.getDateOfBirth());
               accountDataStream.writeBoolean(account.getSendEmailAlerts());

               accountDataStream.writeByte(account.getMessage().message.messageType.value);
               if (account.getMessage().message.messageType != MessageType.NoMessage)
               {
                  accountDataStream.writeUTF(account.getMessage().message.message);
                  accountDataStream.writeLong(account.getMessage().messageTime);
               }

               lastAuthenticatedIPAddress = account.getLastAuthenticatedIPAddress().getAddress();
               // Writing the InetAddress byte length as a single byte, think I can safely assume this (4 bytes for IPv4, 16 for IPv6).
               accountDataStream.writeByte(lastAuthenticatedIPAddress.length);
               accountDataStream.write(lastAuthenticatedIPAddress);

               accountDataStream.writeByte(account.getActivationState().value);

               accountDataStream.writeLong(account.getCreationTime());
            }
         }

         // Finalise the output with a 'null' user account ID.
         accountDataStream.writeInt(UserAccountConstants.NoAccountID);

         for (final Entry<String, FeedbactoryUserAccount> accountsEntry : accountsByEmail.entrySet())
         {
            synchronized (accountsEntry.getValue())
            {
               /* Since the user account emails are written after the attached user accounts, it's possible that:
                * 1. The housekeeping task may have expired an account between the user account being written and the attached email being written here.
                *    In this case, the user account data above will have been persisted but not the email (there will only be one for a non-activated account,
                *    and other account types cannot be expired). On restoration, a record will be created in userAccountsByID but with no corresponding
                *    record in userAccountsByEmail. This is not a problem since after restoration, the housekeeping will eventually expire the 'orphaned'
                *    user account as it would with any non-activated account. See the note above in handleExpiredPendingUserAccounts().
                *
                * 2. A new account is added midway through this save process, leading to the reverse of case 1: the account was not added in time
                *    to have its data saved in the above loop, but has been created by this point and its email is saved, pointing to a phantom
                *    account ID. For this reason, the restoration process needs to perform a check to ensure that each ID attached to an account email
                *    actually references an existing account object.
                */
               if (accountsEntry.getValue().getActivationState() == ActivationState.Expired)
                  continue;

               accountEmailsStream.writeInt(accountsEntry.getValue().getID().intValue());
               accountEmailsStream.writeUTF(accountsEntry.getKey());
            }
         }

         // Finalise the output with a 'null' user account ID.
         accountEmailsStream.writeInt(UserAccountConstants.NoAccountID);
      }
   }


   /* Refer to the concurrency memos at the top of the class, within handleAddNewAccount, and within the housekeeper regarding the integrity of the data of both
    * the save and restore methods.
    *
    * From a cold start of the server, this method should be run before both housekeeping and the client connections are active. Also it must be run before other
    * dependent data restoration services in other classes are run, ie. which link to user accounts.
    */
   private void handleRestoreFromCheckpoint(final Path checkpointPath) throws IOException
   {
      if (isHousekeepingStarted())
         throw new IllegalStateException("Cannot restore from checkpoint while housekeeping task is active.");

      accountIDCounter.set(0);
      accountsByID.clear();
      accountsByEmail.clear();

      final File accountDataFile = checkpointPath.resolve(AccountDataFilename).toFile();
      final File accountEmailsFile = checkpointPath.resolve(AccountEmailsFilename).toFile();

      try
      (
         final DataInputStream accountDataStream = new DataInputStream(new BufferedInputStream(new FileInputStream(accountDataFile)));
         final DataInputStream accountEmailsStream = new DataInputStream(new BufferedInputStream(new FileInputStream(accountEmailsFile)));
      )
      {
         int accountID;
         int largestAccountID = -1;
         String email;
         String pendingEmail;
         String emailConfirmationCode;
         long emailConfirmationCodeLastUpdatedTime;
         byte[] passwordHash;
         String passwordResetCode;
         long passwordResetCodeLastUpdatedTime;
         byte genderValue;
         Gender gender;
         long dateOfBirth;
         boolean sendAnnouncements;
         byte messageTypeValue;
         MessageType messageType;
         Message message;
         TimestampedMessage timestampedMessage;
         byte lastAuthenticatedIPAddressLength;
         byte[] lastAuthenticatedIPAddress;
         ActivationState activationState;
         long creationTime;
         FeedbactoryUserAccount account;

         while ((accountID = accountDataStream.readInt()) != UserAccountConstants.NoAccountID)
         {
            /* Track the largest user account ID, since the primary key counter (accountIDCounter) will have
             * to be reset to a point beyond it after the restoration so that new unique user account ID's can be generated.
             */
            if (accountID > largestAccountID)
               largestAccountID = accountID;

            email = accountDataStream.readUTF();

            pendingEmail = accountDataStream.readBoolean() ? accountDataStream.readUTF() : null;

            if (accountDataStream.readBoolean())
            {
               emailConfirmationCode = accountDataStream.readUTF();
               emailConfirmationCodeLastUpdatedTime = accountDataStream.readLong();
            }
            else
            {
               emailConfirmationCode = null;
               emailConfirmationCodeLastUpdatedTime = FeedbactoryConstants.NoTime;
            }

            if (accountDataStream.readBoolean())
            {
               passwordHash = new byte[FeedbactoryUserAccountConstants.PasswordHashLengthBytes];
               accountDataStream.readFully(passwordHash);
            }
            else
               passwordHash = null;

            if (accountDataStream.readBoolean())
            {
               passwordResetCode = accountDataStream.readUTF();
               passwordResetCodeLastUpdatedTime = accountDataStream.readLong();
            }
            else
            {
               passwordResetCode = null;
               passwordResetCodeLastUpdatedTime = FeedbactoryConstants.NoTime;
            }

            genderValue = accountDataStream.readByte();
            gender = Gender.fromValue(genderValue);
            if (gender == null)
               throw new IllegalArgumentException("Invalid gender value: " + genderValue);

            dateOfBirth = accountDataStream.readLong();

            sendAnnouncements = accountDataStream.readBoolean();

            messageTypeValue = accountDataStream.readByte();
            messageType = MessageType.fromValue(messageTypeValue);
            if (messageType == null)
               throw new IllegalArgumentException("Invalid message type value: " + messageTypeValue);
            else if (messageType != MessageType.NoMessage)
            {
               message = new Message(messageType, accountDataStream.readUTF());
               timestampedMessage = new TimestampedMessage(message, accountDataStream.readLong());
            }
            else
               timestampedMessage = TimestampedMessage.NoMessage;

            lastAuthenticatedIPAddressLength = accountDataStream.readByte();
            lastAuthenticatedIPAddress = new byte[lastAuthenticatedIPAddressLength];
            accountDataStream.readFully(lastAuthenticatedIPAddress);

            activationState = ActivationState.toActivationState(accountDataStream.readByte());

            creationTime = accountDataStream.readLong();

            account = new FeedbactoryUserAccount(accountID, creationTime);

            // Synchronize to ensure the visibility of the initialised non-final fields to subsequent threads.
            synchronized (account)
            {
               account.setEmail(email);
               account.setPendingEmail(pendingEmail);
               account.setEmailConfirmationCode(emailConfirmationCode, emailConfirmationCodeLastUpdatedTime);
               account.setPasswordHash(passwordHash);
               account.setPasswordResetCode(passwordResetCode, passwordResetCodeLastUpdatedTime);
               account.setGender(gender);
               account.setDateOfBirth(dateOfBirth);
               account.setSendEmailAlerts(sendAnnouncements);
               account.setMessage(timestampedMessage);
               account.setLastAuthenticatedIPAddress(InetAddress.getByAddress(lastAuthenticatedIPAddress));
               account.setActivationState(activationState);
            }

            accountsByID.put(accountID, account);
         }

         // Reset the account primary key counter, ready to grab the next user account ID.
         accountIDCounter.set(largestAccountID + 1);

         while ((accountID = accountEmailsStream.readInt()) != UserAccountConstants.NoAccountID)
         {
            email = accountEmailsStream.readUTF();
            account = accountsByID.get(accountID);

            /* Due to the concurrency at the time of persisting the data, the email may have been referencing a new account ID that
             * had 'missed the boat' during the earlier part of the save process. See the comments in the save method.
             * For this reason, the restoration process must check for emails referencing phantom account IDs.
             */
            if (account != null)
               accountsByEmail.put(email, account);
         }
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private FeedbactoryUserAccountView handleGetAccountView(final int accountID)
   {
      final FeedbactoryUserAccount feedbactoryUserAccount = accountsByID.get(accountID);
      return (feedbactoryUserAccount != null) ? getAccountView(feedbactoryUserAccount) : null;
   }


   private FeedbactoryUserAccountView handleGetAccountView(final String email)
   {
      final FeedbactoryUserAccount feedbactoryUserAccount = accountsByEmail.get(UserAccountUtilities.getNormalisedEmail(email));
      return (feedbactoryUserAccount != null) ? getAccountView(feedbactoryUserAccount) : null;
   }


   private FeedbactoryUserAccountView handleGetAccountView(final FeedbactoryUserAccount account)
   {
      synchronized (account)
      {
         return new FeedbactoryUserAccountView(account.getID(),
                                               account.getEmail(), account.getPendingEmail(), account.getEmailConfirmationCode(),
                                               account.getEmailConfirmationCodeLastUpdatedTime(),
                                               account.getPasswordResetCode(), account.getPasswordResetCodeLastUpdatedTime(),
                                               account.getGender(), account.getDateOfBirth(), account.getSendEmailAlerts(),
                                               account.getMessage(), account.getLastAuthenticatedIPAddress(),
                                               account.getActivationState(), account.getCreationTime());
      }
   }


   private UserAccountManagerMetrics handleGetAccountManagerMetrics()
   {
      int pendingAccounts = 0;
      int activatedAccounts = 0;
      int pendingEmails = 0;
      int activePasswordResetCodes = 0;

      for (final FeedbactoryUserAccount account : accountsByID.values())
      {
         synchronized (account)
         {
            if (account.getActivationState() == ActivationState.Activated)
            {
               activatedAccounts ++;

               if (account.hasEmailConfirmationCode())
                  pendingEmails ++;

               if (account.hasPasswordResetCode())
                  activePasswordResetCodes ++;
            }
            else if (account.getActivationState() == ActivationState.NotActivated)
               pendingAccounts ++;
         }
      }

      return new UserAccountManagerMetrics(isHousekeepingStarted(), housekeepingTask.lastRunStartTime, pendingAccounts, activatedAccounts, pendingEmails, activePasswordResetCodes);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public FeedbactoryUserAccount getAccountByID(final int accountID)
   {
      return accountsByID.get(accountID);
   }


   final public FeedbactoryUserAccount getAccountByEmail(final String normalisedEmail)
   {
      return accountsByEmail.get(normalisedEmail);
   }


   final public AddNewUserAccountResult addNewAccount(final String email, final Gender gender, final long dateOfBirth, final boolean sendEmailAlerts, final InetAddress requestIPAddress)
   {
      return addNewAccount(email, gender, dateOfBirth, sendEmailAlerts, requestIPAddress, true);
   }


   final public AddNewUserAccountResult addNewAccount(final String email, final Gender gender, final long dateOfBirth, final boolean sendEmailAlerts,
                                                      final InetAddress requestIPAddress, final boolean sendEmail)
   {
      return handleAddNewAccount(email, gender, dateOfBirth, sendEmailAlerts, requestIPAddress, sendEmail);
   }


   final public void resendActivationCode(final String email)
   {
      handleResendAccountActivationCode(email);
   }


   final public FeedbactoryUserAccount activateAccount(final String email, final String emailConfirmationCode, final byte[] initialPasswordHash, final InetAddress requestIPAddress)
   {
      return handleActivateAccount(email, emailConfirmationCode, initialPasswordHash, requestIPAddress);
   }


   final public FeedbactoryUserAccount authenticateAccountSignIn(final String email, final byte[] passwordHash, final InetAddress requestIPAddress)
   {
      return handleAuthenticateAccountSignIn(email, passwordHash, requestIPAddress);
   }


   final public void sendPasswordResetCode(final String email)
   {
      handleSendPasswordResetCode(email);
   }


   final public FeedbactoryUserAccount resetPassword(final String email, final String passwordResetCode, final byte[] newPasswordHash, final InetAddress requestIPAddress)
   {
      return handleResetPassword(email, passwordResetCode, newPasswordHash, requestIPAddress);
   }


   final public void updateAccountEmail(final FeedbactoryUserAccount account, final String newEmail)
   {
      handleUpdateAccountEmail(account, newEmail);
   }


   final public boolean resendConfirmNewEmailCode(final FeedbactoryUserAccount account)
   {
      return handleResendConfirmNewEmailCode(account);
   }


   final public boolean confirmNewEmail(final FeedbactoryUserAccount account, final String emailConfirmationCode, final byte[] passwordHash, final byte[] newEmailPasswordHash)
   {
      return handleConfirmNewEmail(account, emailConfirmationCode, passwordHash, newEmailPasswordHash);
   }


   final public boolean updateAccountPasswordHash(final FeedbactoryUserAccount account, final byte[] passwordHash, final byte[] newPasswordHash)
   {
      return handleUpdateAccountPasswordHash(account, passwordHash, newPasswordHash);
   }


   final public void updateSendEmailAlerts(final FeedbactoryUserAccount account, final boolean sendEmailAlerts)
   {
      handleUpdateSendEmailAlerts(account, sendEmailAlerts);
   }


   final public TimestampedMessage getAccountMessage(final int accountID)
   {
      return handleGetAccountMessage(accountID);
   }


   final public FeedbactoryUserAccount setAccountMessage(final int accountID, final Message message)
   {
      return handleSetUserAccountMessage(accountID, message);
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
      return housekeepingTask.isHousekeepingStarted();
   }


   final public void startHousekeeping()
   {
      housekeepingTask.start();
   }


   final public void shutdownHousekeeping() throws InterruptedException
   {
      housekeepingTask.shutdown();
   }


   final public void shutdown() throws InterruptedException
   {
      mailer.shutdown();
      signUpInterestRegistry.shutdown();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public FeedbactoryUserAccountView getAccountView(final int accountID)
   {
      return handleGetAccountView(accountID);
   }


   final public FeedbactoryUserAccountView getAccountView(final String email)
   {
      return handleGetAccountView(email);
   }


   final public FeedbactoryUserAccountView getAccountView(final FeedbactoryUserAccount account)
   {
      return handleGetAccountView(account);
   }


   final public UserAccountManagerMetrics getAccountManagerMetrics()
   {
      return handleGetAccountManagerMetrics();
   }


   final public UserAccountMailerMetrics getAccountMailerMetrics()
   {
      return mailer.getMetrics();
   }
}