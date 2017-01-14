
package com.feedbactory.server.useraccount;


import com.feedbactory.shared.useraccount.FeedbactoryUserAccountConstants;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;


abstract class UserAccountUtilities
{
   private UserAccountUtilities()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private void handleValidateEmail(final String email)
   {
      if ((email.length() < FeedbactoryUserAccountConstants.MinimumEmailAddressLength) || (email.length() > FeedbactoryUserAccountConstants.MaximumEmailAddressLength))
         throw new IllegalArgumentException("Invalid email address length: " + email.length());
      else if (Character.isWhitespace(email.charAt(0)) || Character.isWhitespace(email.charAt(email.length() - 1)))
         throw new IllegalArgumentException("Invalid email address: " + email);

      try
      {
         new InternetAddress(email).validate();
      }
      catch (final AddressException addressException)
      {
         throw new IllegalArgumentException("Invalid email address: " + email);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static void validateEmail(final String email)
   {
      handleValidateEmail(email);
   }


   static String getNormalisedEmail(final String email)
   {
      // Server Locale is English.
      return (email != null) ? email.toLowerCase() : null;
   }
}