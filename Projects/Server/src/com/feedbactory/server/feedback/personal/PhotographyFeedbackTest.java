/* Memos:
 * - From the console, try 'account test' to load the 20000 accounts, followed by 'feedback 0 test multiple 10000 500',
 *   which will generate up to 500 ratings spread across 10000 photographs.
 *
 * - Useful command line switches for controlling & gauging the performance of String.intern():
 *   -XX:StringTableSize=n (should be a large prime)
 *   -XX:+PrintStringTableStatistics
 *
 * - The jmap utility will also display a summary of the number of interned strings: 'jmap -heap <java process ID>'.
 *
 * - This class will provide a gross overestimation of string intern usage per feedback count due to the fact that it is generating randomised strings for photo tags.
 *   A more genuine test might be to pull words from a dictionary based on their frequency in real world use.
 */

package com.feedbactory.server.feedback.personal;


import com.feedbactory.server.FeedbactoryServer;
import com.feedbactory.server.feedback.FeedbackManager;
import com.feedbactory.server.feedback.ItemProfileFeedbackSubmission;
import com.feedbactory.server.test.TestUtilities;
import com.feedbactory.server.useraccount.FeedbactoryUserAccount;
import com.feedbactory.server.useraccount.FeedbactoryUserAccountView;
import com.feedbactory.server.useraccount.UserAccountManager;
import com.feedbactory.shared.feedback.personal.ExcellentToTerribleFeedbackSubmissionScale;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteria;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmission;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleKeyValue;
import com.feedbactory.shared.feedback.personal.PhotographyCriteria;
import com.feedbactory.shared.feedback.personal.service.FiveHundredPX;
import com.feedbactory.shared.feedback.personal.service.SmugMug;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;


final public class PhotographyFeedbackTest
{
   private PhotographyFeedbackTest()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private InetAddress createRandomIPAddress()
   {
      try
      {
         final byte[] ip4Address = new byte[4];
         ThreadLocalRandom.current().nextBytes(ip4Address);
         return InetAddress.getByAddress(ip4Address);
      }
      catch (final UnknownHostException unknownHostException)
      {
         throw new RuntimeException(unknownHostException);
      }
   }


   static private PersonalFeedbackPerson createRandomPhotograph()
   {
      final String photographID = TestUtilities.createRandomAlphanumericString(10);
      return new PersonalFeedbackPerson(FiveHundredPX.instance, photographID, PersonalFeedbackCriteriaType.Photography);
   }


   static private PersonalFeedbackPersonProfile createRandomPhotographProfile()
   {
      final String userID = TestUtilities.createRandomAlphanumericString(8, 20);
      final String photographTitle = TestUtilities.createRandomAlphanumericString(10, 50);
      final String photographerName = TestUtilities.createRandomAlphanumericString(10, 30);
      final String photographName = photographTitle + '\0' + photographerName;
      final String thumbnailURL = TestUtilities.createRandomAlphanumericString(40);

      return new PersonalFeedbackPersonProfile(createRandomPhotograph(), userID, photographName, thumbnailURL, null, createRandomPhotographKeywords());
   }


   static private Set<String> createRandomPhotographKeywords()
   {
      final int numberOfKeywords = ThreadLocalRandom.current().nextInt(PersonalFeedbackConstants.MaximumPersonProfileTags + 1);

      if (numberOfKeywords == 0)
         return Collections.emptySet();

      final Set<String> keywords = new HashSet<>(numberOfKeywords);
      String keyword;
      for (int keywordNumber = 0; keywordNumber < numberOfKeywords; keywordNumber ++)
      {
         keyword = TestUtilities.createRandomAlphanumericString(PersonalFeedbackConstants.MinimumPersonProfileTagLength, PersonalFeedbackConstants.MaximumPersonProfileTagLength).toLowerCase();
         keywords.add(keyword.intern());
      }

      return keywords;
   }


   static private byte createRandomFeedbackAnchorValue()
   {
      return (byte) ThreadLocalRandom.current().nextInt(101);
   }


   static private byte generateRandomDeviationFromAnchorValue(final byte anchorValue, final byte increment)
   {
      byte result;
      int selectorValue;

      start:
      for (;;)
      {
         result = anchorValue;

         for (;;)
         {
            selectorValue = ThreadLocalRandom.current().nextInt(200);

            if (selectorValue >= 100)
               break;
            else if (selectorValue >= 50)
            {
               result += increment;
               if (result > 100)
                  continue start;
            }
            else
            {
               result -= increment;
               if (result < 0)
                  continue start;
            }
         }

         return (byte) result;
      }
   }


   static private Map<PhotographyCriteria, PersonalFeedbackSubmissionScaleKeyValue> createRandomPhotographyCriteriaFeedbackAnchor(final byte criteriaFeedbackAnchor)
   {
      final Map<PhotographyCriteria, PersonalFeedbackSubmissionScaleKeyValue> criteriaFeedback = new EnumMap<>(PhotographyCriteria.class);
      PersonalFeedbackSubmissionScaleKeyValue submissionScaleValue;

      for (final PhotographyCriteria photographyCriteria : PhotographyCriteria.values())
      {
         submissionScaleValue = createRandomSubmissionScaleValue(criteriaFeedbackAnchor);
         criteriaFeedback.put(photographyCriteria, submissionScaleValue);
      }

      return criteriaFeedback;
   }


   static private PersonalFeedbackSubmissionScaleKeyValue createRandomSubmissionScaleValue(final byte criteriaFeedbackAnchor)
   {
      final int randomSubmissionScaleValue = generateRandomDeviationFromAnchorValue(criteriaFeedbackAnchor, (byte) 25);

      int floorSubmissionValue = (randomSubmissionScaleValue / 25) * 25;
      int floorSubmissionRemainder = randomSubmissionScaleValue - floorSubmissionValue;
      int ceilingSubmissionValue = floorSubmissionValue + 25;
      int ceilingSubmissionValueRemainder = ceilingSubmissionValue - randomSubmissionScaleValue;

      if (floorSubmissionRemainder <= ceilingSubmissionValueRemainder)
         return ExcellentToTerribleFeedbackSubmissionScale.instance.fromValue((byte) floorSubmissionValue);
      else
         return ExcellentToTerribleFeedbackSubmissionScale.instance.fromValue((byte) ceilingSubmissionValue);
   }


   static private Map<PhotographyCriteria, Integer> createPhotographyCriteriaProbabilityMap()
   {
      final Map<PhotographyCriteria, Integer> feedbackProbabilityMap = new EnumMap<>(PhotographyCriteria.class);

      for (final PhotographyCriteria photographyCriteria : PhotographyCriteria.values())
         feedbackProbabilityMap.put(photographyCriteria, ThreadLocalRandom.current().nextInt(10));

      return feedbackProbabilityMap;
   }


   static private PersonalFeedbackSubmission createRandomPhotographyFeedbackSubmission(final byte anchorRating, final Map<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> anchorCriteriaRatings,
                                                                                       final Map<PhotographyCriteria, Integer> criteriaFeedbackProbabilityMap)
   {
      /* The anchorRating and anchorCriteriaRatings are necessarily separated rather than thrown together into one PersonalFeedbackSubmission object,
       * since that class will throw exceptions for some combinations of the two, eg. if both are supplied and the overall rating is not evenly divisible by 10.
       * For testing it's important to have the flexibility to provide any anchor rating, eg. one that is not calculated but has been provided by an existing
       * overall feedback rating for an item - see processCreateSpreadFeedbackFromSourceItems().
       */
      if (ThreadLocalRandom.current().nextInt(5) == 0)
         return new PersonalFeedbackSubmission(createRandomFeedbackSummary(anchorRating));
      else if (ThreadLocalRandom.current().nextBoolean())
         return new PersonalFeedbackSubmission(createRandomFeedbackSummary(anchorRating),
                                               createRandomPhotographyCriteriaFeedback(anchorCriteriaRatings, criteriaFeedbackProbabilityMap));
      else
      {
         final PersonalFeedbackSubmission submission = new PersonalFeedbackSubmission(createRandomPhotographyCriteriaFeedback(anchorCriteriaRatings, criteriaFeedbackProbabilityMap));

         /* This case is attempting to generate a feedback submission where the isOverallRatingCalculatedFromCriteriaFeedback flag is set, ie. feedback criteria
          * without any explicit overall feedback rating supplied.
          * If no criteria feedback was generated however (due to the workings of the probability map), the generated submission cannot be used as the object
          * will have an overall rating of PersonalFeedbackSubmission.NoRatingValue.
          * In this case use the feedback submission constructor from the previous case as a fallback.
          */
         if (submission.hasAtLeastOneCriteriaRating())
            return submission;
         else
            return new PersonalFeedbackSubmission(createRandomFeedbackSummary(anchorRating),
                                                  createRandomPhotographyCriteriaFeedback(anchorCriteriaRatings, criteriaFeedbackProbabilityMap));
      }
   }


   static private byte createRandomFeedbackSummary(final byte anchorValue)
   {
      final int feedbackSummary = generateRandomDeviationFromAnchorValue(anchorValue, (byte) 10);

      int floorSubmissionValue = (feedbackSummary / 10) * 10;
      int floorSubmissionRemainder = feedbackSummary - floorSubmissionValue;
      int ceilingSubmissionValue = floorSubmissionValue + 10;
      int ceilingSubmissionValueRemainder = ceilingSubmissionValue - feedbackSummary;

      return (floorSubmissionRemainder <= ceilingSubmissionValueRemainder) ? (byte) floorSubmissionValue : (byte) ceilingSubmissionValue;
   }


   static private EnumMap<PhotographyCriteria, PersonalFeedbackSubmissionScaleKeyValue> createRandomPhotographyCriteriaFeedback(final Map<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> anchorValues,
                                                                                                                                final Map<PhotographyCriteria, Integer> criteriaFeedbackProbabilityMap)
   {
      final EnumMap<PhotographyCriteria, PersonalFeedbackSubmissionScaleKeyValue> criteriaFeedback = new EnumMap<>(PhotographyCriteria.class);

      for (final Entry<PhotographyCriteria, Integer> criteriaProbabilityEntry : criteriaFeedbackProbabilityMap.entrySet())
      {
         // The use of <= ensures that the criteria feedback has at least a 10% chance of being generated when the probability map contains 0 for that criteria.
         if (ThreadLocalRandom.current().nextInt(10) <= criteriaProbabilityEntry.getValue())
            criteriaFeedback.put(criteriaProbabilityEntry.getKey(), createRandomSubmissionScaleValue(anchorValues.get(criteriaProbabilityEntry.getKey()).value));
      }

      // The returned map may be empty;
      return criteriaFeedback;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private String handleProcessConsoleCommand(final FeedbackManager feedbackManager, final PersonalFeedbackManager personalFeedbackManager,
                                                     final UserAccountManager userAccountManager, final String[] command)
   {
      if ((command.length == 2) && command[0].equals("one"))
         return processCreateFeedbackForOnePhotograph(personalFeedbackManager, userAccountManager, Integer.parseInt(command[1]));
      else if ((command.length == 3) && command[0].equals("multiple"))
         return processCreateSpreadFeedbackForMultiplePhotographs(personalFeedbackManager, userAccountManager, Integer.parseInt(command[1]), Integer.parseInt(command[2]));
      else if ((command.length == 2) && command[0].equals("existing"))
         return processCreateSpreadFeedbackFromSourceItems(feedbackManager, personalFeedbackManager, userAccountManager, Integer.parseInt(command[1]));
      else if ((command.length == 1) && command[0].equals("fragment"))
         return processCreateFragmentedPhotographProfiles(personalFeedbackManager, userAccountManager);
      else
         return "Invalid command switch.";
   }


   static private String processCreateFeedbackForOnePhotograph(final PersonalFeedbackManager personalFeedbackManager, final UserAccountManager userAccountManager,
                                                               final int numberOfRatings)
   {
      // Check this to be certain that things such as the account mailer won't be triggered when creating the accounts.
      if (FeedbactoryServer.getExecutionProfile() == FeedbactoryServer.ExecutionProfile.Production)
         throw new IllegalStateException("Test cannot be performed when Feedbactory server is using the production profile.");

      final UserAccountManager.UserAccountManagerMetrics accountManagerMetrics = userAccountManager.getAccountManagerMetrics();
      final PersonalFeedbackPersonProfile photographProfile = createRandomPhotographProfile();
      FeedbactoryUserAccount ratingUserAccount;
      final byte anchorRating = createRandomFeedbackAnchorValue();
      final Map<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> anchorCriteriaRatings = createRandomPhotographyCriteriaFeedbackAnchor(anchorRating);
      final Map<PhotographyCriteria, Integer> criteriaProbabilityMap = createPhotographyCriteriaProbabilityMap();
      PersonalFeedbackSubmission feedbackSubmission;

      for (int ratingNumber = 0; ratingNumber < numberOfRatings; ratingNumber ++)
      {
         ratingUserAccount = userAccountManager.getAccountByID(ThreadLocalRandom.current().nextInt(accountManagerMetrics.activatedAccounts));
         feedbackSubmission = createRandomPhotographyFeedbackSubmission(anchorRating, anchorCriteriaRatings, criteriaProbabilityMap);
         personalFeedbackManager.addPersonalFeedbackSubmission(ratingUserAccount, photographProfile, feedbackSubmission);
      }

      final Formatter formatter = new Formatter();
      formatter.format("Photography feedback generated for %s photo ID: %s", photographProfile.person.getWebsite().getName(), photographProfile.person.getItemID());
      return formatter.toString();
   }


   static private String processCreateSpreadFeedbackForMultiplePhotographs(final PersonalFeedbackManager personalFeedbackManager, final UserAccountManager userAccountManager,
                                                                           final int numberOfPhotographs, final int maximumNumberOfRatingsPerPhotograph)
   {
      // Check this to be certain that things such as the account mailer won't be triggered when creating the accounts.
      if (FeedbactoryServer.getExecutionProfile() == FeedbactoryServer.ExecutionProfile.Production)
         throw new IllegalStateException("Test cannot be performed when Feedbactory server is using the production profile.");

      final UserAccountManager.UserAccountManagerMetrics accountManagerMetrics = userAccountManager.getAccountManagerMetrics();
      int totalNumberOfRatings = 0;
      PersonalFeedbackPersonProfile photographProfile;
      FeedbactoryUserAccount ratingUserAccount;
      int numberOfPhotographRatings;
      byte anchorRating;
      Map<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> anchorCriteriaRatings;
      Map<PhotographyCriteria, Integer> criteriaProbabilityMap;
      PersonalFeedbackSubmission feedbackSubmission;

      for (int photographNumber = 0; photographNumber < numberOfPhotographs; photographNumber ++)
      {
         photographProfile = createRandomPhotographProfile();

         numberOfPhotographRatings = ThreadLocalRandom.current().nextInt(1, maximumNumberOfRatingsPerPhotograph + 1);

         anchorRating = createRandomFeedbackAnchorValue();
         anchorCriteriaRatings = createRandomPhotographyCriteriaFeedbackAnchor(anchorRating);
         criteriaProbabilityMap = createPhotographyCriteriaProbabilityMap();

         for (int ratingNumber = 0; ratingNumber < numberOfPhotographRatings; ratingNumber ++)
         {
            ratingUserAccount = userAccountManager.getAccountByID(ThreadLocalRandom.current().nextInt(accountManagerMetrics.activatedAccounts));
            feedbackSubmission = createRandomPhotographyFeedbackSubmission(anchorRating, anchorCriteriaRatings, criteriaProbabilityMap);
            personalFeedbackManager.addPersonalFeedbackSubmission(ratingUserAccount, photographProfile, feedbackSubmission);
         }

         totalNumberOfRatings += numberOfPhotographRatings;
      }

      final Formatter formatter = new Formatter();
      formatter.format("%d new ratings added, spread across %d new photographs.", totalNumberOfRatings, numberOfPhotographs);
      return formatter.toString();
   }


   static private String processCreateSpreadFeedbackFromSourceItems(final FeedbackManager feedbackManager, final PersonalFeedbackManager personalFeedbackManager,
                                                                    final UserAccountManager userAccountManager, final int maximumNumberOfRatingsPerPhotograph)
   {
      // Check this to be certain that things such as the account mailer won't be triggered when creating the accounts.
      if (FeedbactoryServer.getExecutionProfile() == FeedbactoryServer.ExecutionProfile.Production)
         throw new IllegalStateException("Test cannot be performed when Feedbactory server is using the production profile.");

      final FeedbactoryUserAccount sourceAccount = userAccountManager.getAccountByID(0);
      final FeedbactoryUserAccountView sourceAccountView = userAccountManager.getAccountView(sourceAccount);
      final List<PersonProfileFeedbackSubmission> sourceFeedback;

      /* feedbackManager.getAllUserFeedbackSubmissions provides a read-only list view of the account feedback, which must be copied while the
       * user account lock is being held.
       */
      synchronized (sourceAccount)
      {
         final List<ItemProfileFeedbackSubmission> accountSubmissions = feedbackManager.getAllUserFeedbackSubmissions(sourceAccountView);
         sourceFeedback = new ArrayList<>(accountSubmissions.size());
         for (final ItemProfileFeedbackSubmission sourceItemProfileSubmission : accountSubmissions)
            sourceFeedback.add((PersonProfileFeedbackSubmission) sourceItemProfileSubmission);
      }

      final UserAccountManager.UserAccountManagerMetrics accountManagerMetrics = userAccountManager.getAccountManagerMetrics();
      FeedbactoryUserAccount ratingUserAccount;
      int numberOfPhotographRatings;
      Map<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> anchorCriteriaRatings;
      Map<PhotographyCriteria, Integer> criteriaProbabilityMap;
      PersonalFeedbackSubmission feedbackSubmission;

      int totalNumberOfRatings = 0;

      for (final PersonProfileFeedbackSubmission sourceItemProfileFeedbackSubmission : sourceFeedback)
      {
         numberOfPhotographRatings = ThreadLocalRandom.current().nextInt(1, maximumNumberOfRatingsPerPhotograph + 1);
         anchorCriteriaRatings = createRandomPhotographyCriteriaFeedbackAnchor(sourceItemProfileFeedbackSubmission.feedbackSubmission.overallFeedbackRating);
         criteriaProbabilityMap = createPhotographyCriteriaProbabilityMap();

         for (int ratingNumber = 0; ratingNumber < numberOfPhotographRatings; ratingNumber ++)
         {
            // Ensure that the root account already containing the source feedback (0) is not selected.
            ratingUserAccount = userAccountManager.getAccountByID(ThreadLocalRandom.current().nextInt(accountManagerMetrics.activatedAccounts - 1) + 1);
            feedbackSubmission = createRandomPhotographyFeedbackSubmission(sourceItemProfileFeedbackSubmission.feedbackSubmission.overallFeedbackRating, anchorCriteriaRatings, criteriaProbabilityMap);
            personalFeedbackManager.addPersonalFeedbackSubmission(ratingUserAccount, sourceItemProfileFeedbackSubmission.personProfile, feedbackSubmission);
         }

         totalNumberOfRatings += numberOfPhotographRatings;
      }

      final Formatter formatter = new Formatter();
      formatter.format("%d new ratings added, spread across %d existing photographs.", totalNumberOfRatings, sourceFeedback.size());

      return formatter.toString();
   }


   static private String processCreateFragmentedPhotographProfiles(final PersonalFeedbackManager personalFeedbackManager, final UserAccountManager userAccountManager)
   {
      // Check this to be certain that things such as the account mailer won't be triggered when creating the accounts.
      if (FeedbactoryServer.getExecutionProfile() == FeedbactoryServer.ExecutionProfile.Production)
         throw new IllegalStateException("Test cannot be performed when Feedbactory server is using the production profile.");

      /* This is the base code for a bunch of variations, testing the behaviour of the parent feedback manager's (FeedbackManager) housekeeping
       * task which tries to identify and propagate dominant item profiles (mergeFragmentedItemProfiles), and also clean the item profile cache.
       * The test accounts must first be loaded.
       *
       * To effectively run variations of this test to check the behaviours of the housekeeping task, place outputs and delays as required in
       * the parent feedback manager. To simulate events such as a feedback submission being removed midway through the mergeFragmentedItemProfiles
       * process, add a long delay temporarily in the housekeeping task and a temporary console test task here which can remove an account's feedback.
       */
      final PersonalFeedbackPerson photographID = new PersonalFeedbackPerson(SmugMug.instance, "DummyID", PersonalFeedbackCriteriaType.Photography);

      /* Another test to try is to create multiple identical dominant profile objects, to make sure that the dominant profile is still picked up based on
       * the values, and also that it replaces the existing profiles that are value-equals but not reference equals.
       * Need to temporarily disable the parent feedback manager's item profile cache to properly test this.
       */
      final PersonalFeedbackPersonProfile dominantProfile = new PersonalFeedbackPersonProfile(photographID, "userID", "Dominant photo profile", "test\0smugmug\0url", null);

      final PersonalFeedbackPersonProfile fragmentedProfile = new PersonalFeedbackPersonProfile(photographID, "userID", "Fragmented photo profile", "test\0smugmug\0url", null);
      final PersonalFeedbackSubmission dummySubmission = new PersonalFeedbackSubmission(createRandomFeedbackAnchorValue());

      FeedbactoryUserAccount account;

      for (int accountNumber = 0; accountNumber < 10; accountNumber ++)
      {
         account = userAccountManager.getAccountByID(accountNumber);

         personalFeedbackManager.addPersonalFeedbackSubmission(account, fragmentedProfile, dummySubmission);
      }

      // Place a delay here to ensure a split in times between the different sets of feedback.
      try
      {
         Thread.sleep(2000);
      }
      catch (final InterruptedException interruptedException)
      {
      }

      for (int accountNumber = 10; accountNumber < 20; accountNumber ++)
      {
         account = userAccountManager.getAccountByID(accountNumber);
         synchronized (account)
         {
            /* Simulate different IP addresses to ensure that a dominant item profile can be identified.
             * Need to make this method public to allow this.
             * Comment it out to simulate the feedback submissions originating from the same IP address, to ensure that
             * a dominant item profile won't be picked up!
             */
            //account.setLastAuthenticatedIPAddress(createRandomIPAddress());
         }

         personalFeedbackManager.addPersonalFeedbackSubmission(account, dominantProfile, dummySubmission);
      }

      return "Done";
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static String processConsoleCommand(final FeedbackManager feedbackManager, final PersonalFeedbackManager personalFeedbackManager, final UserAccountManager userAccountManager, final String[] command)
   {
      return handleProcessConsoleCommand(feedbackManager, personalFeedbackManager, userAccountManager, command);
   }
}