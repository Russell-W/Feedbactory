
package com.feedbactory.server.network.application;


import com.feedbactory.server.useraccount.FeedbactoryUserAccount;
import com.feedbactory.shared.network.AuthenticationStatus;


final public class SessionAuthentication
{
   static final public SessionAuthentication SuccessAccountNotActivated = new SessionAuthentication(AuthenticationStatus.SuccessAccountNotActivated);
   static final public SessionAuthentication Failed = new SessionAuthentication(AuthenticationStatus.FailedAuthentication);
   static final public SessionAuthentication FailedTooManyAttempts = new SessionAuthentication(AuthenticationStatus.FailedTooManyAttempts);
   static final public SessionAuthentication FailedCapacityReached = new SessionAuthentication(AuthenticationStatus.FailedCapacityReached);

   final public AuthenticationStatus result;
   final public FeedbactoryUserAccount account;


   private SessionAuthentication(final AuthenticationStatus result)
   {
      this(result, null);
   }


   public SessionAuthentication(final AuthenticationStatus result, final FeedbactoryUserAccount account)
   {
      validate(result);

      this.result = result;
      this.account = account;
   }


   private void validate(final AuthenticationStatus result)
   {
      if (result == null)
         throw new IllegalArgumentException("Authentication status cannot be null.");
   }
}