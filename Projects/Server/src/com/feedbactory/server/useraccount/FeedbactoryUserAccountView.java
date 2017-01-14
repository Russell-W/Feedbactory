
package com.feedbactory.server.useraccount;


import com.feedbactory.server.core.TimestampedMessage;
import com.feedbactory.server.useraccount.FeedbactoryUserAccount.ActivationState;
import com.feedbactory.shared.useraccount.Gender;
import java.net.InetAddress;


final public class FeedbactoryUserAccountView
{
   final public int userAccountID;

   final public String email;
   final public String pendingEmail;
   final public String emailConfirmationCode;
   final public long emailConfirmationCodeLastUpdatedTime;

   final public String passwordResetCode;
   final public long passwordResetCodeLastUpdatedTime;

   final public Gender gender;
   final public long dateOfBirth;
   final public boolean sendEmailAlerts;

   final public TimestampedMessage message;

   final public InetAddress lastAuthenticatedIPAddress;

   final public ActivationState activationState;

   final public long creationTime;


   FeedbactoryUserAccountView(final int userAccountID,
                              final String email, final String pendingEmail, final String emailConfirmationCode, final long emailConfirmationCodeLastUpdatedTime,
                              final String passwordResetCode, final long passwordResetCodeLastUpdatedTime,
                              final Gender gender, final long dateOfBirth, final boolean sendEmailAlerts,
                              final TimestampedMessage message,
                              final InetAddress lastAuthenticatedIPAddress,
                              final ActivationState activationState,
                              final long creationTime)
   {
      this.userAccountID = userAccountID;

      this.email = email;
      this.pendingEmail = pendingEmail;
      this.emailConfirmationCode = emailConfirmationCode;
      this.emailConfirmationCodeLastUpdatedTime = emailConfirmationCodeLastUpdatedTime;

      this.passwordResetCode = passwordResetCode;
      this.passwordResetCodeLastUpdatedTime = passwordResetCodeLastUpdatedTime;

      this.gender = gender;
      this.dateOfBirth = dateOfBirth;
      this.sendEmailAlerts = sendEmailAlerts;

      this.message = message;

      this.lastAuthenticatedIPAddress = lastAuthenticatedIPAddress;

      this.activationState = activationState;

      this.creationTime = creationTime;
   }
}