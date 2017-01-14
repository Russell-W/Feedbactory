

package com.feedbactory.client.core.useraccount;


public interface AccountEventListener
{
   public void signedInToUserAccount(final FeedbactoryUserAccount userAccount);
   public void userAccountDetailsUpdated(final FeedbactoryUserAccount userAccount);
   public void signedOutOfUserAccount(final FeedbactoryUserAccount userAccount);
}