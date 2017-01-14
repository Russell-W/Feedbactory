
package com.feedbactory.shared.network;


public enum SessionInitiationType
{
   SignUp((byte) 0),
   ActivateAccount((byte) 1),
   EmailSignIn((byte) 2),
   ResetPassword((byte) 3);

   final public byte value;


   private SessionInitiationType(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   static public SessionInitiationType fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return SignUp;
         case 1:
            return ActivateAccount;
         case 2:
            return EmailSignIn;
         case 3:
            return ResetPassword;

         default:
            return null;
      }
   }
}