
package com.feedbactory.server.useraccount;


final public class AddNewUserAccountResult
{
   static final AddNewUserAccountResult CapacityReachedResult = new AddNewUserAccountResult(AddNewUserAccountStatus.FailedCapacityReached);

   final public AddNewUserAccountStatus status;
   final public FeedbactoryUserAccount account;


   AddNewUserAccountResult(final AddNewUserAccountStatus status)
   {
      this(status, null);
   }


   AddNewUserAccountResult(final AddNewUserAccountStatus status, final FeedbactoryUserAccount account)
   {
      this.status = status;
      this.account = account;
   }
}