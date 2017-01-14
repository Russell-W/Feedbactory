
package com.feedbactory.client.core.useraccount;


import com.feedbactory.shared.useraccount.Gender;


public class FeedbactoryUserAccount
{
   final public String emailAddress;
   final public String pendingEmailAddress;
   final public Gender gender;
   final public long dateOfBirth;
   final public boolean sendEmailAlerts;


   public FeedbactoryUserAccount(final String emailAddress, final String pendingEmailAddress, final Gender gender, final long dateOfBirth, final boolean sendEmailAlerts)
   {
      this.emailAddress = emailAddress;
      this.pendingEmailAddress = pendingEmailAddress;
      this.gender = gender;
      this.dateOfBirth = dateOfBirth;
      this.sendEmailAlerts = sendEmailAlerts;
   }


   public FeedbactoryUserAccount(final FeedbactoryUserAccount userAccount, final String emailAddress, final String pendingEmailAddress)
   {
      this(emailAddress, pendingEmailAddress, userAccount.gender, userAccount.dateOfBirth, userAccount.sendEmailAlerts);
   }


   public FeedbactoryUserAccount(final FeedbactoryUserAccount userAccount, final String publicContactEmailAddress)
   {
      this(userAccount.emailAddress, userAccount.pendingEmailAddress, userAccount.gender, userAccount.dateOfBirth, userAccount.sendEmailAlerts);
   }


   public FeedbactoryUserAccount(final FeedbactoryUserAccount userAccount, final boolean sendEmailAlerts)
   {
      this(userAccount.emailAddress, userAccount.pendingEmailAddress, userAccount.gender, userAccount.dateOfBirth, sendEmailAlerts);
   }
}