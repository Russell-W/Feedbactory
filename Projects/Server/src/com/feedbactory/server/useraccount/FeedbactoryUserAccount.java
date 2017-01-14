/* Memos:
 * - The field values are not validated or checked for consistency against each other within the context of the application; this class is for the most part a
 *   'dumb' container for items which are owned and managed by the classes within the package. Likewise there are no checks for consistency between the data
 *   fields when they're updated - trust is placed in the package-private callers to do the right thing. Also the caller should be in a position to update
 *   multiple fields atomically, where either all fields are updated, or none (if there's a validation error).
 * 
 * - In all but declaration this class is a friend of all user account related classes within the package. The temptation is there to double handle the
 *   validation when updating the fields here, and provide safe cloning of arrays on update, etc, but the classes within this same package are considered
 *   trusted clients and effectively the owners/managers of instances of this class.
 * 
 * - I originally separated some of the container items in this class into another class, ManagedUserAccount, which was specific to the server.useraccount
 *   package. The original reasoning being that although the rest of the application probably required access to at the very least a user account stub or even ID
 *   so that it could link operations to an account and maybe even have some access to basic user information, the actual management data & operations
 *   for things such as the activation state, pending email, pending messages, etc, were more tied to the management classes in the server.useraccount
 *   package and had little relevance outside of that. However there are a few counter-arguments against that approach, and for conglomerating all of these data
 *   items into the one class as it is now:
 * 
 *   1) Why disregard the capability and usefulness of hiding public access to package-private operations?
 * 
 *   2) From a user session standpoint, since all established sessions have an attached user account (see UserAccountSession), it is very convenient to
 *      have the -managed- user account data available after the 'session header' portion of a user request has been initialised. If I only provided
 *      (for eg.) a user account ID or a stub as part of UserAccountSession, any operation after the session header that requires access to the
 *      managed data would have to provide the ID or stub to the account manager, which would have to again look up the account. Instead, I can simply
 *      pass the complete user account back to the account manager - no lookup required on its part.
 * 
 *   3) The user account is a sensible object to lock on across different packages whenever safe access to is members is required. Unfortunately with a
 *      separate managed user account (which would have the user account stub as a child reference), it's all too easy to accidentally lock on the
 *      outer managed user account object rather than, eg. managedUserAccount.accountStub.
 */

package com.feedbactory.server.useraccount;


import com.feedbactory.server.core.TimeCache;
import com.feedbactory.server.core.TimestampedMessage;
import com.feedbactory.shared.Message;
import com.feedbactory.shared.MessageType;
import com.feedbactory.shared.useraccount.Gender;
import java.net.InetAddress;
import java.util.Arrays;


final public class FeedbactoryUserAccount
{
   /* There will be less object allocations due to autoboxing (via calls to getUserAccountID()) if I provide an Integer object up-front.
    * The user account ID as an Integer reference type may frequently be used as a PK in collections, so this may be worthwhile.
    */
   final private Integer userAccountID;

   private String email;
   private String pendingEmail;
   private String emailConfirmationCode;
   private long emailConfirmationCodeLastUpdatedTime;

   private byte[] passwordHash;
   private String passwordResetCode;
   private long passwordResetCodeLastUpdatedTime;

   private Gender gender;
   private long dateOfBirth;
   private boolean sendEmailAlerts;

   private TimestampedMessage message;

   private InetAddress lastAuthenticatedIPAddress;

   private ActivationState activationState;

   final private long creationTime;


   // As with the setter methods, the package-private caller is trusted to provide a valid account ID for the constructor.
   FeedbactoryUserAccount(final Integer userAccountID, final long creationTime)
   {
      this.userAccountID = userAccountID;
      this.creationTime = creationTime;
   }


   /****************************************************************************
    *
    * 
    * 
    ***************************************************************************/


   static public enum ActivationState
   {
      NotActivated((byte) 0),
      Activated((byte) 1),
      Expired((byte) 2);

      final byte value;


      private ActivationState(final byte value)
      {
         this.value = value;
      }


      static ActivationState toActivationState(final byte value)
      {
         switch (value)
         {
            case 0:
               return NotActivated;
            case 1:
               return Activated;
            case 2:
               return Expired;
            default:
               throw new IllegalArgumentException("Invalid managed user account activation state value: " + value);
         }
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private void handleSetEmailConfirmationCode(final String emailConfirmationCode, final long emailConfirmationCodeLastUpdatedTime)
   {
      this.emailConfirmationCode = emailConfirmationCode;
      this.emailConfirmationCodeLastUpdatedTime = emailConfirmationCodeLastUpdatedTime;
   }


   private void handleSetPasswordResetCode(final String passwordResetCode, final long passwordResetCodeLastUpdatedTime)
   {
      this.passwordResetCode = passwordResetCode;
      this.passwordResetCodeLastUpdatedTime = passwordResetCodeLastUpdatedTime;
   }


   private boolean handleEquals(final Object otherObject)
   {
      if (! (otherObject instanceof FeedbactoryUserAccount))
         return false;

      return (((FeedbactoryUserAccount) otherObject).userAccountID.intValue() == userAccountID.intValue());
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final void setEmail(final String email)
   {
      this.email = email;
   }


   final void setPendingEmail(final String pendingEmail)
   {
      this.pendingEmail = pendingEmail;
   }


   final boolean hasEmailConfirmationCode()
   {
      return (emailConfirmationCode != null);
   }


   final String getEmailConfirmationCode()
   {
      return emailConfirmationCode;
   }


   final void setEmailConfirmationCode(final String emailConfirmationCode)
   {
      setEmailConfirmationCode(emailConfirmationCode, TimeCache.getCurrentTimeMilliseconds());
   }


   final void setEmailConfirmationCode(final String emailConfirmationCode, final long emailConfirmationCodeLastUpdatedTime)
   {
      handleSetEmailConfirmationCode(emailConfirmationCode, emailConfirmationCodeLastUpdatedTime);
   }


   final boolean authenticateEmailConfirmationCode(final String emailConfirmationCode)
   {
      return hasEmailConfirmationCode() && this.emailConfirmationCode.equals(emailConfirmationCode);
   }


   final long getEmailConfirmationCodeLastUpdatedTime()
   {
      return emailConfirmationCodeLastUpdatedTime;
   }


   final boolean hasPasswordHash()
   {
      return (passwordHash != null);
   }


   final byte[] getPasswordHash()
   {
      return passwordHash;
   }


   final void setPasswordHash(final byte[] passwordHash)
   {
      this.passwordHash = passwordHash;
   }


   final boolean authenticatePasswordHash(final byte[] passwordHash)
   {
      // Arrays.equals considers null arrays to be equal.
      return hasPasswordHash() && Arrays.equals(this.passwordHash, passwordHash);
   }


   final boolean hasPasswordResetCode()
   {
      return (passwordResetCode != null);
   }


   final String getPasswordResetCode()
   {
      return passwordResetCode;
   }


   final void setPasswordResetCode(final String passwordResetCode)
   {
      handleSetPasswordResetCode(passwordResetCode, TimeCache.getCurrentTimeMilliseconds());
   }


   final void setPasswordResetCode(final String passwordResetCode, final long passwordResetCodeLastUpdatedTime)
   {
      handleSetPasswordResetCode(passwordResetCode, passwordResetCodeLastUpdatedTime);
   }


   final boolean authenticatePasswordResetCode(final String passwordResetCode)
   {
      return hasPasswordResetCode() && this.passwordResetCode.equals(passwordResetCode);
   }


   final long getPasswordResetCodeLastUpdatedTime()
   {
      return passwordResetCodeLastUpdatedTime;
   }


   final void setGender(final Gender gender)
   {
      this.gender = gender;
   }


   final void setDateOfBirth(final long dateOfBirth)
   {
      this.dateOfBirth = dateOfBirth;
   }


   final void setSendEmailAlerts(final boolean sendEmailAlerts)
   {
      this.sendEmailAlerts = sendEmailAlerts;
   }


   final void setMessage(final Message message)
   {
      setMessage((message.messageType == MessageType.NoMessage) ? TimestampedMessage.NoMessage : new TimestampedMessage(message, TimeCache.getCurrentTimeMilliseconds()));
   }


   final void setMessage(final TimestampedMessage message)
   {
      this.message = message;
   }


   final void setLastAuthenticatedIPAddress(final InetAddress lastAuthenticatedIPAddress)
   {
      this.lastAuthenticatedIPAddress = lastAuthenticatedIPAddress;
   }


   final ActivationState getActivationState()
   {
      return activationState;
   }


   final void setActivationState(final ActivationState activationState)
   {
      this.activationState = activationState;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public boolean equals(final Object otherObject)
   {
      return handleEquals(otherObject);
   }


   @Override
   final public int hashCode()
   {
      return userAccountID.intValue();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public Integer getID()
   {
      return userAccountID;
   }


   final public String getEmail()
   {
      return email;
   }


   final public String getPendingEmail()
   {
      return pendingEmail;
   }


   final public Gender getGender()
   {
      return gender;
   }


   final public long getDateOfBirth()
   {
      return dateOfBirth;
   }


   final public boolean getSendEmailAlerts()
   {
      return sendEmailAlerts;
   }


   final public TimestampedMessage getMessage()
   {
      return message;
   }


   final public InetAddress getLastAuthenticatedIPAddress()
   {
      return lastAuthenticatedIPAddress;
   }


   final public long getCreationTime()
   {
      return creationTime;
   }
}