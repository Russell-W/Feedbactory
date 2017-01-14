
package com.feedbactory.shared.useraccount;


public enum AccountOperationType
{
   ResendActivationCode((byte) 0),
   SendPasswordResetCode((byte) 1),
   UpdateEmail((byte) 2),
   ResendNewEmailConfirmationCode((byte) 3),
   ConfirmNewEmail((byte) 4),
   UpdatePasswordHash((byte) 5),
   UpdateSendEmailAlerts((byte) 6);

   final public byte value;


   private AccountOperationType(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   static public AccountOperationType fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return ResendActivationCode;
         case 1:
            return SendPasswordResetCode;
         case 2:
            return UpdateEmail;
         case 3:
            return ResendNewEmailConfirmationCode;
         case 4:
            return ConfirmNewEmail;
         case 5:
            return UpdatePasswordHash;
         case 6:
            return UpdateSendEmailAlerts;

         default:
            return null;
      }
   }
}