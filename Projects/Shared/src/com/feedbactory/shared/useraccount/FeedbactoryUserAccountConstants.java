
package com.feedbactory.shared.useraccount;


abstract public class FeedbactoryUserAccountConstants
{
   static final public int PasswordHashLengthBytes = 32;

   static final public int MinimumEmailAddressLength = 4;
   static final public int MaximumEmailAddressLength = 100;

   static final public int EmailConfirmationCodeLength = 12;

   static final public int PasswordResetCodeLength = 12;


   /****************************************************************************
    *
    ***************************************************************************/


   private FeedbactoryUserAccountConstants()
   {
   }
}