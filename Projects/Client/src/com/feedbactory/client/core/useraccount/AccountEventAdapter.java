
package com.feedbactory.client.core.useraccount;


abstract public class AccountEventAdapter implements AccountEventListener
{
   @Override
   public void signedInToUserAccount(final FeedbactoryUserAccount userAccount)
   {
   }


   @Override
   public void userAccountDetailsUpdated(final FeedbactoryUserAccount userAccount)
   {
   }


   @Override
   public void signedOutOfUserAccount(final FeedbactoryUserAccount userAccount)
   {
   }
}