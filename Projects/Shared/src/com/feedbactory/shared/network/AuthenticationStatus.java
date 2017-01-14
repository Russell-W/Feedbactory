/* Memos:
 * - There is no FailedEmailAlreadyExists for sign ups - we don't want to leak information (at least within our app) regarding whether or not an email is already registered.
 *   Valid sign up applications will either result in a SuccessAccountNotActivated (and the user not being signed in until account activation) or FailedCapacityReached.
 *   Behind the scenes, we are free to send emails to the account holders, with different wording depending on the known status of the email.
 */

package com.feedbactory.shared.network;


public enum AuthenticationStatus
{
   Success((byte) 0),
   SuccessAccountNotActivated((byte) 1),
   FailedAuthentication((byte) 2),
   FailedTooManyAttempts((byte) 3),
   FailedCapacityReached((byte) 4);

   final public byte value;


   private AuthenticationStatus(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public AuthenticationStatus fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return Success;
         case 1:
            return SuccessAccountNotActivated;
         case 2:
            return FailedAuthentication;
         case 3:
            return FailedTooManyAttempts;
         case 4:
            return FailedCapacityReached;

         default:
            return null;
      }
   }
}