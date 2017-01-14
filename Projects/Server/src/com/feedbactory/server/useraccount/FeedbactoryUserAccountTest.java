
package com.feedbactory.server.useraccount;


import com.feedbactory.server.FeedbactoryServer;
import com.feedbactory.server.network.component.EntityID;
import com.feedbactory.shared.useraccount.FeedbactoryUserAccountConstants;
import com.feedbactory.shared.useraccount.Gender;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


final public class FeedbactoryUserAccountTest
{
   static final private Path TestUserAccountEmailsFilename = Paths.get("Test", "AccountEmails.txt");


   /****************************************************************************
    *
    ***************************************************************************/


   static private void handleCreateTestUserAccounts(final UserAccountManager userAccountManager) throws IOException
   {
      // Check this to be certain that things such as the account mailer won't be triggered when creating the accounts.
      if (FeedbactoryServer.getExecutionProfile() == FeedbactoryServer.ExecutionProfile.Production)
         throw new IllegalStateException("Test cannot be performed when Feedbactory server is using the production profile.");

      System.out.println("Reading test account emails...");
      final List<String> testAccountEmails = getTestAccountEmails();
      System.out.println("Finished reading " + testAccountEmails.size() + " test account emails.");

      System.out.println("Adding test accounts...");

      final InetAddress inetAddress = InetAddress.getLoopbackAddress();
      byte[] passwordHash;
      AddNewUserAccountResult addNewUserAccountResult;

      for (final String testAccountEmail : testAccountEmails)
      {
         addNewUserAccountResult = userAccountManager.addNewAccount(testAccountEmail, getRandomGender(), getRandomDateOfBirth(), true, InetAddress.getLoopbackAddress(), false);
         if (addNewUserAccountResult.status == AddNewUserAccountStatus.Success)
         {
            synchronized (addNewUserAccountResult.account)
            {
               passwordHash = new EntityID(FeedbactoryUserAccountConstants.PasswordHashLengthBytes).asByteArray();
               userAccountManager.activateAccount(testAccountEmail, addNewUserAccountResult.account.getEmailConfirmationCode(), passwordHash, inetAddress);
            }
         }
      }

      System.out.println("Finished adding test accounts.");
   }


   static private List<String> getTestAccountEmails() throws IOException
   {
      final List<String> testAccountEmails = new ArrayList<>(20001);

      try
      (
         final BufferedReader reader = Files.newBufferedReader(TestUserAccountEmailsFilename, StandardCharsets.UTF_8);
      )
      {
         String nextLine;

         while ((nextLine = reader.readLine()) != null)
            testAccountEmails.add(nextLine);
      }

      return testAccountEmails;
   }


   static private Gender getRandomGender()
   {
      return (ThreadLocalRandom.current().nextBoolean() ? Gender.Male : Gender.Female);
   }


   static private long getRandomDateOfBirth()
   {
      final int startYear = 1900;
      final int endYear = 2001;

      final Calendar calendar = Calendar.getInstance();
      calendar.setLenient(false);
      calendar.set(Calendar.YEAR, ThreadLocalRandom.current().nextInt(startYear, endYear));
      calendar.set(Calendar.DAY_OF_YEAR, 1);
      calendar.add(Calendar.DAY_OF_YEAR, ThreadLocalRandom.current().nextInt(365));

      return calendar.getTimeInMillis();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public void createTestUserAccounts(final UserAccountManager userAccountManager) throws IOException
   {
      handleCreateTestUserAccounts(userAccountManager);
   }
}