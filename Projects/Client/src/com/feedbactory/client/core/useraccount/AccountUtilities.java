/* Memos:
 * - For the user passwords, I'm employing a client-side hashing scheme, which:
 *   a) Allows peace of mind for the users that their raw passwords are never transmitted (encrypted or otherwise) to the server.
 *   b) Saves the server from performing its own computationally expensive hash operations. Rehashing on the server is still required for the stored passwords to protect
 *         against an attack where a party gains read-only access to the server - they could effectively use these client-generated hashes as the raw password, unless
 *         I rehash them. However to guard against this attack vector a less expensive hash algorithm may be used on the server.
 *
 *   Key to the hashing scheme is that the client must be able to generate the same hash every time. But, each hash salt must be different for each user both within the
 *   Feedbactory database and for those same user emails on different app databases, to ensure that the hashes are not susceptible to a rainbow table attack. So the scheme
 *   is to generate a deterministic salt using a combination of a fixed Feedbactory salt prefix, and the user's email address. This does have the drawback that the user's
 *   hash must be updated every time that they change their email address, but it simply requires a prompting for their existing password at the time that they do this,
 *   which is good practice in any case.
 *
 *   Refer to http://security.stackexchange.com/questions/23006/client-side-password-hashing
 */

package com.feedbactory.client.core.useraccount;


import com.feedbactory.shared.useraccount.FeedbactoryUserAccountConstants;
import java.io.UnsupportedEncodingException;
import java.security.spec.KeySpec;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;


abstract public class AccountUtilities
{
   static final private byte[] FeedbactoryPasswordSaltPrefix = {'F', 'e', 'e', 'd', 'b', 'a', 'c', 't', 'o', 'r', 'y'};
   static final private String PasswordHashAlgorithm = "PBKDF2WithHmacSHA1";
   static final private int PasswordHashIterations = 10000;


   private AccountUtilities()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private boolean handleIsValidEmail(final String email)
   {
      if ((email.length() < FeedbactoryUserAccountConstants.MinimumEmailAddressLength) || (email.length() > FeedbactoryUserAccountConstants.MaximumEmailAddressLength))
         return false;

      try
      {
         new InternetAddress(email).validate();
         return true;
      }
      catch (final AddressException addressException)
      {
         return false;
      }
   }


   static private boolean handleIsValidPassword(final char[] password)
   {
      if ((password.length < 8) || Character.isWhitespace(password[0]) || Character.isWhitespace(password[password.length - 1]))
         return false;

      int letterCount = 0;
      int nonLetterCount = 0;

      for (int characterIndex = 0; characterIndex < password.length; characterIndex ++)
      {
         if (Character.isISOControl(password[characterIndex]))
            return false;
         else if (Character.isLetter(password[characterIndex]))
            letterCount ++;
         else if (! Character.isWhitespace(password[characterIndex]))
            nonLetterCount ++;
      }

      return (letterCount > 1) && (nonLetterCount > 1);
   }


   static private byte[] handleGeneratePasswordHash(final String email, final char[] passwordCharacters)
   {
      final byte[] salt = generatePasswordSaltForEmail(email);

      final KeySpec keySpec = new PBEKeySpec(passwordCharacters, salt, PasswordHashIterations, FeedbactoryUserAccountConstants.PasswordHashLengthBytes * 8);

      try
      {
         final SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(PasswordHashAlgorithm);
         return secretKeyFactory.generateSecret(keySpec).getEncoded();
      }
      catch (final Exception anyException)
      {
         throw new RuntimeException(anyException);
      }
   }


   static private byte[] generatePasswordSaltForEmail(final String email)
   {
      try
      {
         /* Generate a salt which is reproducible by the client (given their email) for future sign ins, yet which is also unique to the Feedbactory application and
          * therefore not susceptible to cross-database rainbow table attacks.
          * Refer to http://security.stackexchange.com/questions/23006/client-side-password-hashing .
          * 
          * A variable-length salt is used, which is a combination of the fixed-length Feedbactory app salt bytes (FeedbactoryPasswordSaltPrefix), and
          * the user's normalised email address encoded using UTF-8.
          */
         final byte[] emailBytes = getNormalisedEmail(email).getBytes("utf-8");
         final byte[] saltBytes = new byte[FeedbactoryPasswordSaltPrefix.length + emailBytes.length];
         System.arraycopy(FeedbactoryPasswordSaltPrefix, 0, saltBytes, 0, FeedbactoryPasswordSaltPrefix.length);
         System.arraycopy(emailBytes, 0, saltBytes, FeedbactoryPasswordSaltPrefix.length, emailBytes.length);

         return saltBytes;
      }
      catch (final UnsupportedEncodingException unsupportedEncodingException)
      {
         throw new RuntimeException(unsupportedEncodingException);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public boolean isValidEmail(final String email)
   {
      return handleIsValidEmail(email);
   }


   static public String getNormalisedEmail(final String email)
   {
      // The same Locale must always be used, otherwise the lowercase could produce different results (and therefore password hashes) for different Locales.
      return (email != null) ? email.toLowerCase(Locale.ENGLISH) : null;
   }


   static public boolean isValidPassword(final char[] password)
   {
      return handleIsValidPassword(password);
   }


   static public boolean isValidEmailConfirmationCode(final String emailConfirmationCode)
   {
      return (emailConfirmationCode != null) && (emailConfirmationCode.length() == FeedbactoryUserAccountConstants.EmailConfirmationCodeLength);
   }


   static public boolean isValidPasswordResetCode(final String passwordResetCode)
   {
      return (passwordResetCode != null) && (passwordResetCode.length() == FeedbactoryUserAccountConstants.PasswordResetCodeLength);
   }


   static public byte[] generatePasswordHash(final String email, final char[] passwordCharacters)
   {
      return handleGeneratePasswordHash(email, passwordCharacters);
   }
}