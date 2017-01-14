/* Memos:
 * - Both add and remove feedback submission ultimately need to return to the client the current feedback summary. To help with this is it worth promoting
 *   PersonalFeedbackNode to a package level class, and returning it from these methods, to prevent an extra lookup? We can then add another getItemFeedbackSummary()
 *   method, which takes that object as a parameter. The caller may lock on the user account to ensure that the feedback node can't possibly have been removed.
 *
 * - I'm a little wary of the method that generates the detailed feedback summary. The calculations are straightforward but involve one floating point op per feedback
 *   submission scale (eg. Moderately) per criteria. If server profiling reveals a lot of CPU expense here, it may be worth palming the calculations off to the client.
 *   However note that unfortunately I can't delegate all of the calculations to the client, since we need to withhold data from the client if the average rating for
 *   any criteria is less than the visibility threshold, so there's no getting out of calculating that much at least. The good news is that if the average criteria
 *   rating is below the visibility threshold, neither server nor client need calculate anything further.
 *
 * - As with the user account manager, validation on feedback ops are performed by the public accessor methods, however none are performed during the restoration process.
 *   This is a tricky decision to make since depending on how you look at things pulling data from disk could be put in the same boat as an untrusted caller. But I think
 *   that the resulting validation would be quite unwiedly and unnecessary; if the persisted data on the server is compromised and changed, validation of individual
 *   feedback profiles and submissions is the least of the concerns.
 */

package com.feedbactory.server.feedback.personal;


import com.feedbactory.server.core.FeedbactoryServerConstants;
import com.feedbactory.server.core.MutableInteger;
import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.log.SystemLogLevel;
import com.feedbactory.server.feedback.FeedbackCategoryManager;
import com.feedbactory.server.feedback.FeedbackManager;
import com.feedbactory.server.feedback.ItemProfileFeedbackSubmission;
import com.feedbactory.server.useraccount.FeedbactoryUserAccount;
import com.feedbactory.server.useraccount.UserAccountManager;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.feedback.FeedbackItem;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import com.feedbactory.shared.feedback.FeedbackSubmission;
import com.feedbactory.shared.feedback.personal.CriteriaFeedbackFeaturedItemsFilter;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteria;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaAttributes;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaDistribution;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackDetailedSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackFeaturedPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmission;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleKeyValue;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


final class PersonalFeedbackManager implements FeedbackCategoryManager
{
   static final private int PersonalFeedbackPersonMapInitialCapacity = 10000;

   static final private int FeaturedFeedbackMaximumSampleSize = 50;
   static final private PersonalFeedbackFeaturedPerson FeaturedItemsEndOfDataMarker = new PersonalFeedbackFeaturedPerson(FeedbactoryConstants.EndOfDataLong);

   static final private long HousekeepingFeaturedItemsTaskFrequencyMinutes = 5;

   final private FeedbackManager feedbackManager;
   final private UserAccountManager userAccountManager;

   final private ConcurrentHashMap<PersonalFeedbackPerson, PersonalFeedbackNode> personalFeedback = new ConcurrentHashMap<>(PersonalFeedbackPersonMapInitialCapacity, 0.75f, FeedbactoryServerConstants.ServerConcurrency);

   final private Map<PersonalFeedbackCriteriaType, CriteriaTypeFeaturedPeopleNode> criteriaTypeNewFeedback = new EnumMap<>(PersonalFeedbackCriteriaType.class);
   final private Map<PersonalFeedbackCriteriaType, CriteriaTypeFeaturedPeopleNode> criteriaTypeHotFeedback = new EnumMap<>(PersonalFeedbackCriteriaType.class);
   final private FeaturedPersonComparator featuredPersonComparator = new FeaturedPersonComparator();
   final private FeaturedItemsSearchListComparator featuredItemsSearchListComparator = new FeaturedItemsSearchListComparator();

   final private HousekeepingTask housekeepingTask = new HousekeepingTask();


   PersonalFeedbackManager(final FeedbackManager feedbackManager, final UserAccountManager userAccountManager)
   {
      this.feedbackManager = feedbackManager;
      this.userAccountManager = userAccountManager;

      initialise();
   }


   private void initialise()
   {
      initialiseCriteriaTypeFeaturedFeedback();
   }


   private void initialiseCriteriaTypeFeaturedFeedback()
   {
      for (final PersonalFeedbackCriteriaType criteriaType : PersonalFeedbackCriteriaType.values())
      {
         criteriaTypeNewFeedback.put(criteriaType, new CriteriaTypeFeaturedPeopleNode(PersonalFeedbackWebsite.getWebsites(criteriaType)));
         criteriaTypeHotFeedback.put(criteriaType, new CriteriaTypeFeaturedPeopleNode(PersonalFeedbackWebsite.getWebsites(criteriaType)));
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final class PersonalFeedbackNode
   {
      final private Map<FeedbactoryUserAccount, PersonProfileFeedbackSubmission> submissions;

      final private Map<? extends PersonalFeedbackCriteria, PersonalFeedbackCriteriaCumulativeDistribution> criteriaFeedback;

      private long cumulativeOverallRating;

      final private int[] ratingDistribution = new int[11];

      private long creationTime;

      private boolean isDeleted;


      private PersonalFeedbackNode(final PersonalFeedbackCriteriaAttributes<?> criteriaAttributes, final long creationTime)
      {
         submissions = new HashMap<>(1);
         criteriaFeedback = initialiseCriteriaFeedback(criteriaAttributes);
         this.creationTime = creationTime;
      }


      private <E extends Enum<E> & PersonalFeedbackCriteria> Map<? extends PersonalFeedbackCriteria, PersonalFeedbackCriteriaCumulativeDistribution> initialiseCriteriaFeedback(final PersonalFeedbackCriteriaAttributes<E> criteriaAttributes)
      {
         return new EnumMap<>(criteriaAttributes.getCriteriaClass());
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private void handleAddFeedbackSubmission(final FeedbactoryUserAccount userAccount, final PersonProfileFeedbackSubmission newSubmission)
      {
         final PersonProfileFeedbackSubmission previousSubmission = submissions.put(userAccount, newSubmission);

         PersonalFeedbackSubmission feedbackSubmission;

         if (previousSubmission != null)
         {
            feedbackSubmission = previousSubmission.feedbackSubmission;
            if (! feedbackSubmission.criteriaSubmissions.isEmpty())
               removeCriteriaFeedback(feedbackSubmission);

            cumulativeOverallRating -= feedbackSubmission.overallFeedbackRating;
            ratingDistribution[Math.round(feedbackSubmission.overallFeedbackRating / 10f)] --;
         }

         feedbackSubmission = newSubmission.feedbackSubmission;
         if (! feedbackSubmission.criteriaSubmissions.isEmpty())
            addCriteriaFeedback(feedbackSubmission);

         cumulativeOverallRating += feedbackSubmission.overallFeedbackRating;
         ratingDistribution[Math.round(feedbackSubmission.overallFeedbackRating / 10f)] ++;
      }


      @SuppressWarnings("unchecked")
      private <E extends Enum<E> & PersonalFeedbackCriteria> void addCriteriaFeedback(final PersonalFeedbackSubmission newSubmission)
      {
         final Map<E, PersonalFeedbackSubmissionScaleKeyValue> newSubmissionCriteriaFeedback = (Map<E, PersonalFeedbackSubmissionScaleKeyValue>) newSubmission.criteriaSubmissions;
         final Map<E, PersonalFeedbackCriteriaCumulativeDistribution> typesafeCriteriaFeedback = (Map<E, PersonalFeedbackCriteriaCumulativeDistribution>) this.criteriaFeedback;
         PersonalFeedbackCriteriaCumulativeDistribution criteriaRatingDistributions;

         for (final Entry<E, PersonalFeedbackSubmissionScaleKeyValue> entry : newSubmissionCriteriaFeedback.entrySet())
         {
            criteriaRatingDistributions = criteriaFeedback.get(entry.getKey());

            if (criteriaRatingDistributions == null)
            {
               criteriaRatingDistributions = new PersonalFeedbackCriteriaCumulativeDistribution();
               typesafeCriteriaFeedback.put(entry.getKey(), criteriaRatingDistributions);
            }

            criteriaRatingDistributions.addCriteriaFeedback(entry.getValue());
         }
      }


      private void handleRemoveFeedbackSubmission(final FeedbactoryUserAccount userAccount)
      {
         // Can assume from this trusted code path that the result will not be null.
         final PersonalFeedbackSubmission previousSubmission = submissions.remove(userAccount).feedbackSubmission;

         if (! previousSubmission.criteriaSubmissions.isEmpty())
            removeCriteriaFeedback(previousSubmission);

         cumulativeOverallRating -= previousSubmission.overallFeedbackRating;
         ratingDistribution[Math.round(previousSubmission.overallFeedbackRating / 10f)] --;
      }


      private void removeCriteriaFeedback(final PersonalFeedbackSubmission existingSubmission)
      {
         PersonalFeedbackCriteriaCumulativeDistribution criteriaRatingDistributions;

         for (final Entry<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> entry : existingSubmission.criteriaSubmissions.entrySet())
         {
            criteriaRatingDistributions = criteriaFeedback.get(entry.getKey());

            if (criteriaRatingDistributions.getNumberOfRatings() > 1)
               criteriaRatingDistributions.removeCriteriaFeedback(entry.getValue());
            else
               criteriaFeedback.remove(entry.getKey());
         }
      }


      private PersonalFeedbackBasicSummary handleGetFeedbackSummary(final boolean showFeedbackLessThanMinimumThreshold)
      {
         if (! isEmpty())
            return new PersonalFeedbackBasicSummary(submissions.size(), calculateAverageRating(showFeedbackLessThanMinimumThreshold));
         else
            return PersonalFeedbackBasicSummary.EmptyFeedbackBasicSummary;
      }


      private byte calculateAverageRating()
      {
         return (byte) Math.round(((float) cumulativeOverallRating) / submissions.size());
      }


      private byte calculateAverageRating(final boolean allowFeedbackLessThanMinimumThreshold)
      {
         byte averageOverallRating = calculateAverageRating();

         if (allowFeedbackLessThanMinimumThreshold || (averageOverallRating >= PersonalFeedbackBasicSummary.MinimumVisibleAverageRating))
            return averageOverallRating;
         else
            return PersonalFeedbackBasicSummary.SuppressedLowAverageRating;
      }


      @SuppressWarnings("unchecked")
      private <E extends Enum<E> & PersonalFeedbackCriteria> PersonalFeedbackDetailedSummary handleGetDetailedFeedbackSummary(final PersonalFeedbackCriteriaAttributes<E> criteriaTypeAttributes,
                                                                                                                              final boolean allowFeedbackLessThanMinimumThreshold)
      {
         if (isEmpty())
            return PersonalFeedbackDetailedSummary.EmptyFeedbackSummary;
         else if (criteriaFeedback.isEmpty())
         {
            if (allowFeedbackLessThanMinimumThreshold || (calculateAverageRating() >= PersonalFeedbackBasicSummary.MinimumVisibleAverageRating))
               return new PersonalFeedbackDetailedSummary(calculateRatingDistributionPercentages());
            else
               return PersonalFeedbackDetailedSummary.SuppressedLowAveragePercentagesSummary;
         }

         final EnumMap<E, PersonalFeedbackCriteriaDistribution> feedbackCriteriaDistribution = new EnumMap<>(criteriaTypeAttributes.getCriteriaClass());

         PersonalFeedbackCriteriaDistribution criteriaFeedbackPercentageDistribution;

         PersonalFeedbackCriteriaCumulativeDistribution criteriaFeedbackDistribution;
         byte averageRatingForCriteria;
         Map<PersonalFeedbackSubmissionScaleKeyValue, Byte> feedbackDistributionPercentages;

         final Map<E, PersonalFeedbackCriteriaCumulativeDistribution> typesafeCriteriaFeedback = (Map<E, PersonalFeedbackCriteriaCumulativeDistribution>) this.criteriaFeedback;
         for (final Entry<E, PersonalFeedbackCriteriaCumulativeDistribution> criteriaFeedbackNodeEntry : typesafeCriteriaFeedback.entrySet())
         {
            criteriaFeedbackDistribution = criteriaFeedbackNodeEntry.getValue();
            averageRatingForCriteria = criteriaFeedbackDistribution.calculateAverageRating();

            if (allowFeedbackLessThanMinimumThreshold || (averageRatingForCriteria >= PersonalFeedbackCriteriaDistribution.MinimumVisibleAverageCriteriaRating))
            {
               feedbackDistributionPercentages = criteriaFeedbackDistribution.getRatingPercentageDistribution();
               criteriaFeedbackPercentageDistribution = new PersonalFeedbackCriteriaDistribution(criteriaFeedbackDistribution.numberOfRatings, averageRatingForCriteria, feedbackDistributionPercentages);
            }
            else
               criteriaFeedbackPercentageDistribution = new PersonalFeedbackCriteriaDistribution(criteriaFeedbackDistribution.numberOfRatings,
                                                                                                 PersonalFeedbackCriteriaDistribution.SuppressedLowAverageRating);

            feedbackCriteriaDistribution.put(criteriaFeedbackNodeEntry.getKey(), criteriaFeedbackPercentageDistribution);
         }

         if (allowFeedbackLessThanMinimumThreshold || (calculateAverageRating() >= PersonalFeedbackBasicSummary.MinimumVisibleAverageRating))
            return new PersonalFeedbackDetailedSummary(calculateRatingDistributionPercentages(), feedbackCriteriaDistribution);
         else
            return PersonalFeedbackDetailedSummary.createSuppressedLowAverageFeedbackSummary(feedbackCriteriaDistribution);
      }


      private byte[] calculateRatingDistributionPercentages()
      {
         assert (! isEmpty());

         /* Ensure that the percentages add up to 100.
          * This algorithm adds up the raw integral proportions, which may add up to less than 100,
          * then progressively bumps up the individual proportions if needed to make up the shortfall.
          * The integralRemainders array contains the fractional portion of the percentage; the largest fractional values are
          * chosen for the progressive incrementing.
          */
         final byte[] ratingDistributionPercentages = new byte[11];
         final float[] integralRemainders = new float[11];

         byte integralPercentage;
         int cumulativePercentage = 0;

         for (int ratingIndex = 0; ratingIndex < 11; ratingIndex ++)
         {
            integralPercentage = (byte) (100 * ratingDistribution[ratingIndex] / submissions.size());
            ratingDistributionPercentages[ratingIndex] = integralPercentage;
            integralRemainders[ratingIndex] = (100f * ratingDistribution[ratingIndex] / submissions.size()) - integralPercentage;
            cumulativePercentage += integralPercentage;
         }

         float largestRemainder;
         int largestRemainderIndex;

         for (int cumulativeShortfall = (100 - cumulativePercentage); cumulativeShortfall > 0; cumulativeShortfall --)
         {
            largestRemainder = -1f;
            largestRemainderIndex = -1;

            for (int ratingIndex = 0; ratingIndex < 11; ratingIndex ++)
            {
               if (integralRemainders[ratingIndex] > largestRemainder)
               {
                  largestRemainder = integralRemainders[ratingIndex];
                  largestRemainderIndex = ratingIndex;
               }
            }

            // Increment the integral percentage.
            ratingDistributionPercentages[largestRemainderIndex] ++;

            // Take the fractional portion out of contention for further rounds of increments.
            integralRemainders[largestRemainderIndex] = -1f;
         }

         return ratingDistributionPercentages;
      }


      private PersonProfileFeedbackSubmission handleReplacePersonProfile(final FeedbactoryUserAccount userAccount, final PersonalFeedbackPersonProfile personProfile)
      {
         final PersonProfileFeedbackSubmission existingFeedbackSubmission = submissions.get(userAccount);

         /* Checking for reference inequality; due to caching by the parent feedback manager, item profile items will generally always be reference equal
          * if they are also value equal. If they are, simply return the existing object, otherwise replace the profile within the existing submission and
          * return the new object.
          */
         if ((existingFeedbackSubmission != null) && (existingFeedbackSubmission.personProfile != personProfile))
         {
            final PersonProfileFeedbackSubmission updatedFeedbackSubmission = new PersonProfileFeedbackSubmission(personProfile,
                                                                                                                  existingFeedbackSubmission.feedbackSubmission,
                                                                                                                  existingFeedbackSubmission.submissionTime);
            submissions.put(userAccount, updatedFeedbackSubmission);
            return updatedFeedbackSubmission;
         }
         else
            return existingFeedbackSubmission;
      }


      /****************************************************************************
       *
       ***************************************************************************/


      // Indicates whether the feedback node has effectively been deleted, ie. all feedback submissions removed.
      private boolean isEmpty()
      {
         return submissions.isEmpty();
      }


      private void addFeedbackSubmission(final FeedbactoryUserAccount userAccount, final PersonProfileFeedbackSubmission newSubmission)
      {
         handleAddFeedbackSubmission(userAccount, newSubmission);
      }


      private void removeFeedbackSubmission(final FeedbactoryUserAccount userAccount)
      {
         handleRemoveFeedbackSubmission(userAccount);
      }


      private PersonalFeedbackBasicSummary getFeedbackSummary(final boolean showFeedbackLessThanMinimumThreshold)
      {
         return handleGetFeedbackSummary(showFeedbackLessThanMinimumThreshold);
      }


      private PersonalFeedbackDetailedSummary getDetailedFeedbackSummary(final PersonalFeedbackPerson person)
      {
         return handleGetDetailedFeedbackSummary(person.getCriteriaType().attributes, person.getWebsite().showFeedbackLessThanMinimumThreshold());
      }


      private PersonProfileFeedbackSubmission replacePersonProfile(final FeedbactoryUserAccount userAccount, final PersonalFeedbackPersonProfile personProfile)
      {
         return handleReplacePersonProfile(userAccount, personProfile);
      }


      private void setCreationTime(final long creationTime)
      {
         this.creationTime = creationTime;
      }


      private void markAsDeleted()
      {
         isDeleted = true;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class PersonalFeedbackCriteriaCumulativeDistribution
   {
      private int numberOfRatings;
      private long cumulativeRating;
      final private Map<PersonalFeedbackSubmissionScaleKeyValue, MutableInteger> feedbackDistribution;


      private PersonalFeedbackCriteriaCumulativeDistribution()
      {
         feedbackDistribution = new HashMap<>(1);
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private void handleAddCriteriaFeedback(final PersonalFeedbackSubmissionScaleKeyValue criteriaFeedback)
      {
         final MutableInteger existingCount = feedbackDistribution.get(criteriaFeedback);

         if (existingCount == null)
            feedbackDistribution.put(criteriaFeedback, new MutableInteger(1));
         else
            existingCount.increment();

         cumulativeRating += criteriaFeedback.value;

         numberOfRatings ++;
      }


      private void handleRemoveCriteriaFeedback(final PersonalFeedbackSubmissionScaleKeyValue criteriaFeedback)
      {
         final MutableInteger existingCount = feedbackDistribution.get(criteriaFeedback);

         if (existingCount.get() > 1)
            existingCount.decrement();
         else
            feedbackDistribution.remove(criteriaFeedback);

         cumulativeRating -= criteriaFeedback.value;

         numberOfRatings --;
      }


      private Map<PersonalFeedbackSubmissionScaleKeyValue, Byte> handleGetRatingPercentageDistribution()
      {
         assert (! feedbackDistribution.isEmpty());

         final PersonalFeedbackSubmissionScaleKeyValue[] submissionScaleKeys = new PersonalFeedbackSubmissionScaleKeyValue[feedbackDistribution.size()];
         final byte[] ratingDistributionPercentages = new byte[feedbackDistribution.size()];
         final float[] integralRemainders = new float[feedbackDistribution.size()];

         int ratingIndex = 0;
         byte integralPercentage;
         int cumulativePercentage = 0;

         for (final Entry<PersonalFeedbackSubmissionScaleKeyValue, MutableInteger> submissionScaleEntry : feedbackDistribution.entrySet())
         {
            submissionScaleKeys[ratingIndex] = submissionScaleEntry.getKey();
            integralPercentage = (byte) (100 * submissionScaleEntry.getValue().get() / numberOfRatings);
            ratingDistributionPercentages[ratingIndex] = integralPercentage;
            integralRemainders[ratingIndex] = (100f * submissionScaleEntry.getValue().get() / numberOfRatings) - integralPercentage;
            cumulativePercentage += integralPercentage;

            ratingIndex ++;
         }

         float largestRemainder;
         int largestRemainderIndex;

         for (int cumulativeShortfall = (100 - cumulativePercentage); cumulativeShortfall > 0; cumulativeShortfall --)
         {
            largestRemainder = -1f;
            largestRemainderIndex = -1;

            for (ratingIndex = 0; ratingIndex < submissionScaleKeys.length; ratingIndex ++)
            {
               if (integralRemainders[ratingIndex] > largestRemainder)
               {
                  largestRemainder = integralRemainders[ratingIndex];
                  largestRemainderIndex = ratingIndex;
               }
            }

            ratingDistributionPercentages[largestRemainderIndex] ++;
            integralRemainders[largestRemainderIndex] = -1f;
         }

         final Map<PersonalFeedbackSubmissionScaleKeyValue, Byte> ratingPercentageDistribution = new HashMap<>(feedbackDistribution.size());
         for (ratingIndex = 0; ratingIndex < submissionScaleKeys.length; ratingIndex ++)
            ratingPercentageDistribution.put(submissionScaleKeys[ratingIndex], ratingDistributionPercentages[ratingIndex]);

         return ratingPercentageDistribution;
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private int getNumberOfRatings()
      {
         return numberOfRatings;
      }


      private byte calculateAverageRating()
      {
         return (byte) Math.round(((float) cumulativeRating) / numberOfRatings);
      }


      private void addCriteriaFeedback(final PersonalFeedbackSubmissionScaleKeyValue criteriaFeedback)
      {
         handleAddCriteriaFeedback(criteriaFeedback);
      }


      private void removeCriteriaFeedback(final PersonalFeedbackSubmissionScaleKeyValue criteriaFeedback)
      {
         handleRemoveCriteriaFeedback(criteriaFeedback);
      }


      private Map<PersonalFeedbackSubmissionScaleKeyValue, Byte> getRatingPercentageDistribution()
      {
         return handleGetRatingPercentageDistribution();
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class CriteriaTypeFeaturedPeopleNode
   {
      final private ReadWriteLock feedbackLock = new ReentrantReadWriteLock();

      /* Index the sorted featured feedback by website, and then by tag within each website.
       * An unindexed collection for queries specifying no website filter is not necessary due to the
       * fairly efficient method that the retrieval algorithm uses.
       */
      final private Map<PersonalFeedbackWebsite, FeaturedItemsFeedbackNode> feedbackByWebsite;


      private CriteriaTypeFeaturedPeopleNode(final Set<PersonalFeedbackWebsite> criteriaTypeWebsites)
      {
         feedbackByWebsite = initialiseFeedbackByWebsite(criteriaTypeWebsites);
      }


      private Map<PersonalFeedbackWebsite, FeaturedItemsFeedbackNode> initialiseFeedbackByWebsite(final Set<PersonalFeedbackWebsite> criteriaTypeWebsites)
      {
         final Map<PersonalFeedbackWebsite, FeaturedItemsFeedbackNode> feedbackBuilder = new HashMap<>(criteriaTypeWebsites.size());

         for (final PersonalFeedbackWebsite website : criteriaTypeWebsites)
            feedbackBuilder.put(website, new FeaturedItemsFeedbackNode());

         return feedbackBuilder;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class FeaturedItemsFeedbackNode
   {
      // The lists should be randomly accessible, as binary search will be used on them.
      final private List<PersonalFeedbackFeaturedPerson> feedback = new ArrayList<>();
      final private Map<String, List<PersonalFeedbackFeaturedPerson>> feedbackByTag = new HashMap<>();
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class FeaturedPersonComparator implements Comparator<PersonalFeedbackFeaturedPerson>
   {
      @Override
      final public int compare(final PersonalFeedbackFeaturedPerson personOne, final PersonalFeedbackFeaturedPerson personTwo)
      {
         // There's no need to compare criteria type, as the featured items are already grouped by that.
         if (personOne.sortValue < personTwo.sortValue)
            return -1;
         else if (personOne.sortValue > personTwo.sortValue)
            return 1;
         else if (personOne.personProfile.getWebsite().getID() < personTwo.personProfile.getWebsite().getID())
            return -1;
         else if (personOne.personProfile.getWebsite().getID() > personTwo.personProfile.getWebsite().getID())
            return 1;
         else
            return personOne.personProfile.person.getItemID().compareTo(personTwo.personProfile.person.getItemID());
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private class FeaturedItemsSearchList
   {
      final private List<PersonalFeedbackFeaturedPerson> searchList;
      final private String sourceTag;
      private int activeSearchIndex;


      private FeaturedItemsSearchList(final List<PersonalFeedbackFeaturedPerson> searchList, final String sourceTag, final int activeSearchIndex)
      {
         this.searchList = searchList;
         this.sourceTag = sourceTag;
         this.activeSearchIndex = activeSearchIndex;
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private void decrementFeedbackSearchIndex()
      {
         this.activeSearchIndex --;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class FeaturedItemsSearchListComparator implements Comparator<FeaturedItemsSearchList>
   {
      @Override
      final public int compare(final FeaturedItemsSearchList searchListOne, final FeaturedItemsSearchList searchListTwo)
      {
         return featuredPersonComparator.compare(searchListOne.searchList.get(searchListOne.activeSearchIndex), searchListTwo.searchList.get(searchListTwo.activeSearchIndex));
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class HousekeepingTask implements Runnable, ThreadFactory
   {
      private ScheduledThreadPoolExecutor executor;


      @Override
      final public Thread newThread(final Runnable runnable)
      {
         final Thread thread = new Thread(runnable, "Personal feedback housekeeping task");
         thread.setDaemon(true);
         return thread;
      }


      @Override
      final public void run()
      {
         try
         {
            for (final Entry<PersonalFeedbackCriteriaType, CriteriaTypeFeaturedPeopleNode> criteriaTypeFeaturedFeedbackNodeEntry : criteriaTypeNewFeedback.entrySet())
               updateCriteriaTypeNewFeedback(criteriaTypeFeaturedFeedbackNodeEntry.getKey(), criteriaTypeFeaturedFeedbackNodeEntry.getValue());

            for (final Entry<PersonalFeedbackCriteriaType, CriteriaTypeFeaturedPeopleNode> criteriaTypeFeaturedFeedbackNodeEntry : criteriaTypeHotFeedback.entrySet())
               updateCriteriaTypeHotFeedback(criteriaTypeFeaturedFeedbackNodeEntry.getKey(), criteriaTypeFeaturedFeedbackNodeEntry.getValue());
         }
         catch (final Exception anyException)
         {
            /* Exception handling provided for -any- exception, since any exceptions will otherwise be captured
             * by the enclosing FutureTask that is generated when this Runnable is submitted to ScheduledExecutorService.scheduleAtFixedRate().
             * Unhandled exceptions would also prevent further scheduleAtFixedRate() invocations from running.
             */
            FeedbactoryLogger.logSystemEvent(SystemLogLevel.ApplicationError, getClass(), "Personal feedback manager housekeeping task failed", anyException);
         }
      }


      private void updateCriteriaTypeNewFeedback(final PersonalFeedbackCriteriaType criteriaType, final CriteriaTypeFeaturedPeopleNode newFeedbackNode)
      {
         try
         {
            newFeedbackNode.feedbackLock.writeLock().lock();

            hollowOutCriteriaTypeFeaturedItemsNode(newFeedbackNode);
            generateNewFeedbackList(criteriaType, newFeedbackNode);
            removeEmptyFeedbackByTagMapEntries(newFeedbackNode);
         }
         finally
         {
            newFeedbackNode.feedbackLock.writeLock().unlock();
         }
      }


      private void hollowOutCriteriaTypeFeaturedItemsNode(final CriteriaTypeFeaturedPeopleNode feedbackNode)
      {
         for (final FeaturedItemsFeedbackNode websiteNode : feedbackNode.feedbackByWebsite.values())
            hollowOutFeaturedItemsFeedbackNode(websiteNode);
      }


      private void hollowOutFeaturedItemsFeedbackNode(final FeaturedItemsFeedbackNode feedbackNode)
      {
         feedbackNode.feedback.clear();

         // Clear the featured items by tag lists before the rebuilding; don't remove these lists just yet as most will probably need to be recreated immediately.
         for (final List<PersonalFeedbackFeaturedPerson> feedbackByTagList : feedbackNode.feedbackByTag.values())
            feedbackByTagList.clear();
      }


      private void generateNewFeedbackList(final PersonalFeedbackCriteriaType criteriaType, final CriteriaTypeFeaturedPeopleNode newFeedbackNode)
      {
         PersonalFeedbackNode personalFeedbackNode;
         PersonalFeedbackPersonProfile canonicalPersonProfile;
         PersonalFeedbackBasicSummary feedbackSummary;
         PersonalFeedbackFeaturedPerson featuredItem;
         FeaturedItemsFeedbackNode websiteNode;

         for (final Entry<PersonalFeedbackPerson, PersonalFeedbackNode> personalFeedbackEntry : personalFeedback.entrySet())
         {
            if (personalFeedbackEntry.getKey().getCriteriaType() != criteriaType)
               continue;

            personalFeedbackNode = personalFeedbackEntry.getValue();

            synchronized (personalFeedbackNode)
            {
               if (personalFeedbackNode.isDeleted)
                  continue;

               // Cannot be null, since there must be at least one entry if the node is not marked as deleted.
               canonicalPersonProfile = getCanonicalPersonProfile(personalFeedbackNode);
               feedbackSummary = personalFeedbackNode.getFeedbackSummary(canonicalPersonProfile.getWebsite().showFeedbackLessThanMinimumThreshold());
               featuredItem = new PersonalFeedbackFeaturedPerson(canonicalPersonProfile, feedbackSummary, personalFeedbackNode.creationTime, personalFeedbackNode.creationTime);
               websiteNode = newFeedbackNode.feedbackByWebsite.get(canonicalPersonProfile.getWebsite());

               addFeaturedPersonToList(websiteNode.feedback, featuredItem);
               addFeaturedPersonTags(websiteNode.feedbackByTag, featuredItem);
            }
         }
      }


      private PersonalFeedbackPersonProfile getCanonicalPersonProfile(final PersonalFeedbackNode feedbackNode)
      {
         /* Find and return the most popular item profile, based on frequency.
          * When submitting feedback it's possible for users to send different variations of an item profile for the same feedback item ID, depending on what
          * data the browser has ripped. When the user sees the summary of their own feedback, they only see whichever item profile they originally sent,
          * barring housekeeping by the parent feedback manager which can 'defragment' different item profiles - see the FeedbackManager's housekeeping task.
          * So it's expected that on average each item has only one item profile, but it's not strictly enforced.
          * When a user wishes to see feedback submitting by others, eg. hot or new feedback for a particular category, which item profile should be
          * sent? This is where this method comes in handy, to provide a best guess as to the correct item profile based on its popularity. This will hopefully
          * avoid (or reduce) the likelihood of a malicious user providing offensive profile (or photo) URLs, item names, etc.
          */

         // It's reasonable to expect each item to have only one known profile version on average.
         final Map<PersonalFeedbackPersonProfile, MutableInteger> itemProfileCounts = new HashMap<>(1);
         MutableInteger itemProfileCount;

         for (final PersonProfileFeedbackSubmission feedbackSubmissionProfile : feedbackNode.submissions.values())
         {
            // Relies on the PersonalFeedbackPersonProfile equals method.
            itemProfileCount = itemProfileCounts.get(feedbackSubmissionProfile.personProfile);
            if (itemProfileCount == null)
               itemProfileCounts.put(feedbackSubmissionProfile.personProfile, new MutableInteger(1));
            else
               itemProfileCount.increment();
         }

         PersonalFeedbackPersonProfile mostCommonItemProfile = null;
         int mostCommonItemProfileCount = 0;

         for (final Entry<PersonalFeedbackPersonProfile, MutableInteger> personProfileCountEntry : itemProfileCounts.entrySet())
         {
            if (personProfileCountEntry.getValue().get() > mostCommonItemProfileCount)
            {
               mostCommonItemProfile = personProfileCountEntry.getKey();
               mostCommonItemProfileCount = personProfileCountEntry.getValue().get();
            }
         }

         return mostCommonItemProfile;
      }


      private void addFeaturedPersonToList(final List<PersonalFeedbackFeaturedPerson> keywordFeedbackPeople, final PersonalFeedbackFeaturedPerson newFeaturedFeedbackPerson)
      {
         int insertionPoint = Collections.binarySearch(keywordFeedbackPeople, newFeaturedFeedbackPerson, featuredPersonComparator);

         // The item to be inserted will never already be present when this method is called, so 'invert' the insertion point index to obtain the correct position.
         insertionPoint = -(insertionPoint + 1);
         keywordFeedbackPeople.add(insertionPoint, newFeaturedFeedbackPerson);
      }


      private void addFeaturedPersonTags(final Map<String, List<PersonalFeedbackFeaturedPerson>> feedbackByTag, final PersonalFeedbackFeaturedPerson newFeaturedFeedbackPerson)
      {
         // First gather the implicit keywords from the item profile's display name (lowercased).
         final Set<String> allKeywords = getItemDisplayNameTags(newFeaturedFeedbackPerson.personProfile.getFullName());

         // Add to that set the explicit item profile keywords.
         allKeywords.addAll(newFeaturedFeedbackPerson.personProfile.getTags());

         List<PersonalFeedbackFeaturedPerson> keywordFeedbackList;

         for (final String personKeyword : allKeywords)
         {
            keywordFeedbackList = feedbackByTag.get(personKeyword);
            if (keywordFeedbackList == null)
            {
               // The list should be randomly accessible, as binary search will be used on it.
               keywordFeedbackList = new ArrayList<>(1);
               keywordFeedbackList.add(newFeaturedFeedbackPerson);
               feedbackByTag.put(personKeyword, keywordFeedbackList);
            }
            else
               addFeaturedPersonToList(keywordFeedbackList, newFeaturedFeedbackPerson);
         }
      }


      private void removeEmptyFeedbackByTagMapEntries(final CriteriaTypeFeaturedPeopleNode feedbackNode)
      {
         for (final FeaturedItemsFeedbackNode websiteNode : feedbackNode.feedbackByWebsite.values())
            removeEmptyFeedbackByTagMapEntries(websiteNode);
      }


      private void removeEmptyFeedbackByTagMapEntries(final FeaturedItemsFeedbackNode feedbackNode)
      {
         // After the featured items lists have been rebuilt, remove the tag entries that no longer have featured items attached.
         Iterator<List<PersonalFeedbackFeaturedPerson>> feedbackByKeywordIterator = feedbackNode.feedbackByTag.values().iterator();
         while (feedbackByKeywordIterator.hasNext())
         {
            if (feedbackByKeywordIterator.next().isEmpty())
               feedbackByKeywordIterator.remove();
         }
      }


      private void updateCriteriaTypeHotFeedback(final PersonalFeedbackCriteriaType criteriaType, final CriteriaTypeFeaturedPeopleNode hotFeedbackNode)
      {
         try
         {
            hotFeedbackNode.feedbackLock.writeLock().lock();

            hollowOutCriteriaTypeFeaturedItemsNode(hotFeedbackNode);
            generateHotFeedbackList(criteriaType, hotFeedbackNode);
            removeEmptyFeedbackByTagMapEntries(hotFeedbackNode);
         }
         finally
         {
            hotFeedbackNode.feedbackLock.writeLock().unlock();
         }
      }


      private void generateHotFeedbackList(final PersonalFeedbackCriteriaType criteriaType, final CriteriaTypeFeaturedPeopleNode hotFeedbackNode)
      {
         PersonalFeedbackNode personalFeedbackNode;
         long hotRating;
         PersonalFeedbackPersonProfile canonicalPersonProfile;
         PersonalFeedbackBasicSummary feedbackSummary;
         PersonalFeedbackFeaturedPerson featuredItem;
         FeaturedItemsFeedbackNode websiteNode;

         for (final Entry<PersonalFeedbackPerson, PersonalFeedbackNode> personalFeedbackEntry : personalFeedback.entrySet())
         {
            if (personalFeedbackEntry.getKey().getCriteriaType() != criteriaType)
               continue;

            personalFeedbackNode = personalFeedbackEntry.getValue();

            synchronized (personalFeedbackNode)
            {
               /* The node is effectively deleted if the last feedback submission for it has just been removed.
                * Note that this check ensures that the calculation performed in getHotRating() won't produce a division by zero exception.
                */
               if (personalFeedbackNode.isDeleted)
                  continue;

               hotRating = getHotRating(personalFeedbackNode);
               if (hotRating > 0)
               {
                  // Cannot be null, since there must be at least one entry in the node is not marked as deleted.
                  canonicalPersonProfile = getCanonicalPersonProfile(personalFeedbackNode);
                  feedbackSummary = personalFeedbackNode.getFeedbackSummary(canonicalPersonProfile.getWebsite().showFeedbackLessThanMinimumThreshold());
                  featuredItem = new PersonalFeedbackFeaturedPerson(canonicalPersonProfile, feedbackSummary, personalFeedbackNode.creationTime, hotRating);
                  websiteNode = hotFeedbackNode.feedbackByWebsite.get(canonicalPersonProfile.getWebsite());

                  addFeaturedPersonToList(websiteNode.feedback, featuredItem);
                  addFeaturedPersonTags(websiteNode.feedbackByTag, featuredItem);
               }
            }
         }
      }


      private long getHotRating(final PersonalFeedbackNode feedbackNode)
      {
         /* Submissions cannot be empty, otherwise there'll be a division by zero.
          * Also note that the average rating calculated here is even more approximate (not using floating point) than that
          * calculated via the PersonalFeedbackNode's calculateAverageRating() method. This is fine, it only needs to
          * provide a rough estimate for the sake of ranking each item in the hotlist.
          * The promotionPotential * averageRatingScaling term is later divided by 100, in effect providing
          * (averageRatingScaling / 100) * promotionPotential. The later division of the larger term also saves from using
          * floating point here.
          *
          * TODO: I should also consider returning a -1L if enough time has passed and the item has not become 'hot'; this may not even
          * have to calculate the average rating, maybe only the number of ratings matters..? The reason for doing this is to declutter the
          * hot history of items that may have initially gotten some attention but never really became popular. Without such decluttering,
          * a user attempting to navigate back to a popular item from 3 days ago would first have to wade through a couple of days' worth of
          * items that may have had some positive feedback (enough to have a positive hot rating) but were not genuinely popular.
          * How many ratings are needed for an item to be considered popular? Maybe I also need to maintain a count of the average number of
          * ratings per item, and use this. Eg. After 24 hours an item with 50% more than the average number of ratings is considered popular, otherwise drop it.
          */
         final byte averageRatingScaling = (byte) (feedbackNode.cumulativeOverallRating / feedbackNode.submissions.size());
         if (averageRatingScaling < 50)
            return -1L;

         final int promotionPotential = getHotRatingPromotionPotential(feedbackNode.submissions.size());

         // Advance by 100 units per 12 hours.
         final long naturalTimeRating = (feedbackNode.creationTime / 432000L);

         return naturalTimeRating + ((promotionPotential * averageRatingScaling) / 100);
      };


      private int getHotRatingPromotionPotential(final int numberOfRatings)
      {
         if (numberOfRatings <= 10)
            return (numberOfRatings * 10);
         else if (numberOfRatings <= 100)
         {
            /* Approximately distribute the values from 11 to 100 throughout the range of 100 to 200.
             * The formula is the same as (((numberOfRatings - 10) / 90) * 100) + 100, except that that version would prematurely round down/truncate the
             * ((numberOfRatings - 10) / 90) result to zero due to the integer math being used.
             */
            return (((numberOfRatings - 10) / 9) * 10) + 100;
         }
         else if (numberOfRatings <= 1000)
         {
            // Approximately distribute the values from 101 to 1000 through the range of 200 to 300.
            return ((numberOfRatings - 100) / 9) + 200;
         }
         else
            return 300;
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private boolean isHousekeepingStarted()
      {
         return (executor != null);
      }


      private void start()
      {
         if (isHousekeepingStarted())
            throw new IllegalStateException("Housekeeping task has already been started.");

         executor = new ScheduledThreadPoolExecutor(1, this);
         executor.setKeepAliveTime(10, TimeUnit.SECONDS);
         executor.allowCoreThreadTimeOut(true);

         executor.scheduleAtFixedRate(this, 0, HousekeepingFeaturedItemsTaskFrequencyMinutes, TimeUnit.MINUTES);
      }


      private void shutdown() throws InterruptedException
      {
         if (isHousekeepingStarted())
         {
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            executor = null;
         }
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private Set<String> getItemDisplayNameTags(final String rawTagsString)
   {
      final Set<String> tags = new HashSet<>(PersonalFeedbackConstants.MaximumPersonProfileTags);
      processRawTag(tags, rawTagsString);
      return tags;
   }


   static private void processRawTag(final Set<String> tags, final String rawTagString)
   {
      int characterIndex = 0;

      while ((characterIndex = seekTagStart(rawTagString, characterIndex)) < rawTagString.length())
      {
         characterIndex = processTag(rawTagString, characterIndex, tags);

         // Bail out if the maximum legal number of tags has been reached, or if the end of the input string has been reached.
         if ((tags.size() == PersonalFeedbackConstants.MaximumPersonProfileTags) || (characterIndex >= rawTagString.length()))
            break;
      }
   }


   static private int seekTagStart(final String rawTagString, final int characterIndex)
   {
      int tagStartSearchIndex = characterIndex;
      char currentCharacter;

      while (tagStartSearchIndex < rawTagString.length())
      {
         currentCharacter = rawTagString.charAt(tagStartSearchIndex);
         if (Character.isLetterOrDigit(currentCharacter))
            break;
         else if (PersonalFeedbackConstants.PermittedTagDelimiterCharacters.indexOf(currentCharacter) == -1)
            tagStartSearchIndex = seekWhitespace(rawTagString, tagStartSearchIndex + 1);

         tagStartSearchIndex ++;
      }

      return tagStartSearchIndex;
   }


   static private int seekWhitespace(final String rawTagString, final int characterIndex)
   {
      int whitespaceSearchIndex = characterIndex;

      while ((whitespaceSearchIndex < rawTagString.length()) && (! Character.isWhitespace(rawTagString.charAt(whitespaceSearchIndex))))
         whitespaceSearchIndex ++;

      return whitespaceSearchIndex;
   }


   static private int processTag(final String rawTagString, final int tagStartIndex, final Set<String> tags)
   {
      int tagEndSearchIndex = tagStartIndex + 1;
      char tagCharacter;
      boolean wasPreviousALetterOrDigit = true;

      while (tagEndSearchIndex < rawTagString.length())
      {
         tagCharacter = rawTagString.charAt(tagEndSearchIndex);
         if (Character.isLetterOrDigit(tagCharacter))
            wasPreviousALetterOrDigit = true;
         else if (PersonalFeedbackConstants.PermittedTagNonAlphaNumericCharacters.indexOf(tagCharacter) != -1)
         {
            if (wasPreviousALetterOrDigit)
               wasPreviousALetterOrDigit = false;
            else
            {
               // More than one successive non-alphanumeric character - bail out of processing the remainder of the token.
               return seekWhitespace(rawTagString, tagEndSearchIndex + 1);
            }
         }
         else if (PersonalFeedbackConstants.PermittedTagDelimiterCharacters.indexOf(tagCharacter) != -1)
            break;
         else
         {
            // Invalid tag characters - bail out of processing the remainder of the token.
            return seekWhitespace(rawTagString, tagEndSearchIndex + 1);
         }

         tagEndSearchIndex ++;
      }

      addProcessedTag(rawTagString, tagStartIndex, tagEndSearchIndex, tags);

      return tagEndSearchIndex;
   }


   static private void addProcessedTag(final String rawTagString, final int startIndex, final int endIndex, final Set<String> tags)
   {
      int adjustedEndIndex = endIndex;
      if (PersonalFeedbackConstants.PermittedTagNonAlphaNumericCharacters.indexOf(rawTagString.charAt(endIndex - 1)) != -1)
         adjustedEndIndex --;

      final int tagLength = (adjustedEndIndex - startIndex);

      if ((tagLength >= PersonalFeedbackConstants.MinimumPersonProfileTagLength) &&
          (tagLength <= PersonalFeedbackConstants.MaximumPersonProfileTagLength))
      {
         /* As with the lowercase processing of the explicit item tags (see PersonalFeedbackNetworkGateway),
          * there is the assumption that the locale has been set elsewhere in the server, to ensure consistent results.
          *
          * Also as with the explicit item tags, the tags here should be intern()'d for the same reasons (saving memory,
          * with the side benefit that string comparisons against the index will be faster).
          */
         final String tagWord = rawTagString.substring(startIndex, adjustedEndIndex).toLowerCase().intern();

         if (! PersonalFeedbackServerConstants.FeaturedItemsExcludedTags.contains(tagWord))
            tags.add(tagWord);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private PersonalFeedbackBasicSummary handleGetPersonalFeedbackBasicSummary(final PersonalFeedbackNode feedbackNode, final boolean showFeedbackLessThanMinimumThreshold)
   {
      synchronized (feedbackNode)
      {
         return feedbackNode.getFeedbackSummary(showFeedbackLessThanMinimumThreshold);
      }
   }


   private PersonalFeedbackBasicSummary handleGetPersonalFeedbackBasicSummary(final PersonalFeedbackPerson person)
   {
      final PersonalFeedbackNode allFeedbackForPerson = personalFeedback.get(person);

      if (allFeedbackForPerson != null)
         return getPersonalFeedbackBasicSummary(allFeedbackForPerson, person.getWebsite().showFeedbackLessThanMinimumThreshold());

      return PersonalFeedbackBasicSummary.EmptyFeedbackBasicSummary;
   }


   private PersonalFeedbackDetailedSummary handleGetPersonalFeedbackDetailedSummary(final PersonalFeedbackPerson person)
   {
      final PersonalFeedbackNode allFeedbackForPerson = personalFeedback.get(person);

      if (allFeedbackForPerson != null)
      {
         synchronized (allFeedbackForPerson)
         {
            return allFeedbackForPerson.getDetailedFeedbackSummary(person);
         }
      }

      return PersonalFeedbackDetailedSummary.EmptyFeedbackSummary;
   }


   private PersonalFeedbackNode handleAddItemFeedbackSubmission(final FeedbactoryUserAccount userAccount, final PersonalFeedbackPersonProfile personalFeedbackProfile,
                                                                final PersonalFeedbackSubmission feedbackSubmission)
   {
      /* There's no validation performed here for the profile or submission, since this is a trusted method call; any validation must have already been performed
       * by the caller. This saves unnecessarily doubling up on the same validation.
       * If I later provide public utility methods to add/remove feedback, eg. from the administrator, these will require validation.
       */

      // Locking on the user account, and then the feedback node.
      synchronized (userAccount)
      {
         // The parent feedback manager will return null if the submission is rejected.
         final PersonProfileFeedbackSubmission submissionResult = (PersonProfileFeedbackSubmission) feedbackManager.addFeedbackSubmission(userAccount, personalFeedbackProfile, feedbackSubmission);
         if (submissionResult != null)
         {
            final PersonalFeedbackPerson person = submissionResult.personProfile.person;

            /* This loop & retry approach looks a bit dodgy but it follows the same pattern used in the Java Concurrency in Practice book
             * (ref: section 5.19, cache memoization).
             * The reason that the loop is needed is to correctly handle the case where the feedback submissions collection for a person have been removed by another
             * thread which has just removed the last submission for the person. In practice it would be incredibly rare for the concurrent situation to happen even
             * once, meaning that the loop will likewise rarely if ever come into play. In essence it's the same pattern as AtomicInteger's getAndIncrement(), which will
             * loop until the increment is equal to an expected value.
             * If I later relax the requirement for empty feedback nodes to be removed, I can ditch the loop and of course the isDelected check.
             *
             * Alternative implementations:
             *
             * - Maintain a separate collection of 'canonical' person ID's, similar to String's intern(). This method (and others) could then safely lock on the canonical
             *   object. The problem is that in a way it would just be shifting the issue to other code since there would still be the accumulation of unused ID's
             *   (although there'd be no need to maintain them between restarts). This approach is still worth keeping in mind though because it has the potential
             *   to solve other related problems where I might need to lock on a definitive object for a particular ID. The FeedbackManager actually generates something
             *   similar to canonical feedback item IDs - a cache of IDs, to help prevent memory bloat - but they aren't safe to be used for locking in their current form.
             *
             * - Use a background task and a ReentrantReadWriteLock to periodically clean up the empty feedback nodes. The big drawback is that this would periodically
             *   impose a complete lockdown on new submissions, albeit for an instant.
             *
             * - Just allow the empty feedback nodes to accumulate, but don't persist them. They won't be present on restart.
             */
            for (;;)
            {
               PersonalFeedbackNode feedbackForPerson = personalFeedback.get(person);

               if (feedbackForPerson == null)
               {
                  feedbackForPerson = new PersonalFeedbackNode(personalFeedbackProfile.person.getCriteriaType().attributes, submissionResult.submissionTime);

                  // No need to place a synchronized block here to initialise the new object's non-final fields; their default values are as they need to be.

                  final PersonalFeedbackNode existingEntry = personalFeedback.putIfAbsent(person, feedbackForPerson);

                  if (existingEntry != null)
                     feedbackForPerson = existingEntry;
               }

               synchronized (feedbackForPerson)
               {
                  /* If between the time of checking the collection and grabbing the lock the feedback node has been marked and ditched,
                   * the procedure needs to bail out and try again. Note that as unlikely or even impossible as the case may be within the
                   * constraints of the application, this also covers the case where the node that has just been added is immediately removed by another thread.
                   */
                  if (! feedbackForPerson.isDeleted)
                  {
                     feedbackForPerson.addFeedbackSubmission(userAccount, submissionResult);
                     return feedbackForPerson;
                  }
               }
            }
         }
         else
            return null;
      }
   }


   private PersonalFeedbackSubmission handleGetPersonalFeedbackSubmission(final FeedbactoryUserAccount userAccount, final PersonalFeedbackPerson person)
   {
      // Fetch the submission from the parent feedback manager, and avoid grabbing an extra lock.
      final FeedbackSubmission submission = feedbackManager.getFeedbackSubmission(userAccount, person);

      return (submission != null) ? ((PersonalFeedbackSubmission) submission) : PersonalFeedbackSubmission.EmptyPersonalFeedbackSubmission;
   }


   private PersonalFeedbackNode handleRemovePersonalFeedbackSubmission(final FeedbactoryUserAccount userAccount, final PersonalFeedbackPerson person)
   {
      // Locking on the user account, and then the feedback node.
      synchronized (userAccount)
      {
         /* Refer to addItemFeedbackSubmission() comments.
          *
          * We don't need the loop and isDeleted check, since a) we have a lock on the user account while making the call to
          * the parent feedback manager to remove the user's feedback for the item, and b) if that call is true, and assuming the data isn't corrupted, we
          * know that the feedback node for the item here must not yet be empty/deleted since at the very least it must contain the feedback submission that we have
          * just removed from the parent feedback manager (and are about to remove here).
          *
          * Regardless of the outcome of a), we will be returning the feedback node containing all of its current submissions to the caller. If there is no feedback
          * submission to remove here, the node may be null.
          */
         final PersonalFeedbackNode feedbackForPerson = personalFeedback.get(person);

         // The parent feedback manager will return true if there was an existing submission by the Feedbactory user account for the browsed user.
         if (feedbackManager.removeFeedbackSubmission(userAccount, person))
         {
            synchronized (feedbackForPerson)
            {
               feedbackForPerson.removeFeedbackSubmission(userAccount);

               if (feedbackForPerson.isEmpty())
               {
                  feedbackForPerson.markAsDeleted();
                  personalFeedback.remove(person);
               }
            }
         }

         return feedbackForPerson;
      }
   }


   private List<PersonalFeedbackFeaturedPerson> handleGetNextNewItemsSample(final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      final CriteriaTypeFeaturedPeopleNode newFeedbackNode = criteriaTypeNewFeedback.get(featuredItemsFilter.criteriaType);
      return getNextFeaturedItemsSample(newFeedbackNode, featuredItemsFilter);
   }


   private List<PersonalFeedbackFeaturedPerson> handleGetNextHotItemsSample(final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      final CriteriaTypeFeaturedPeopleNode hotFeedbackNode = criteriaTypeHotFeedback.get(featuredItemsFilter.criteriaType);
      return getNextFeaturedItemsSample(hotFeedbackNode, featuredItemsFilter);
   }


   private List<PersonalFeedbackFeaturedPerson> getNextFeaturedItemsSample(final CriteriaTypeFeaturedPeopleNode featuredFeedbackNode,
                                                                           final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      try
      {
         featuredFeedbackNode.feedbackLock.readLock().lock();

         /* This algorithm is broken into two main steps:
          * 1. Examine the featured items request filter and retrieve a group of all sorted lists that may be needed to satisfy the request.
          * 2. Process the group of sorted lists, retrieving items until either the standard request amount quota has been reached, or there are no more items.
          *
          * Narrowing down the 'group of sorted lists' assembled in the first step is mostly the key to ensuring an efficient process. Each search list
          * in the group is already sorted, and is either associated with feedback for a website as a whole (when no tag filter is supplied by the request) or else
          * associated with a tag used within a website. A search list can be thought of as a view into one single featured items list, including a tag context (if any)
          * and a current item pointer. As mentioned in the housekeeping methods, the lists are sorted so that the top-ranked items start at the end of each list.
          * Once the pointer for a search list reaches the zero index, that list has been exhausted and can be removed from contention when attempting to
          * retrieve further featured items.
          */
         final List<FeaturedItemsSearchList> featuredItemsSearchListGroup = getFeaturedItemsSearchListGroup(featuredFeedbackNode, featuredItemsFilter);
         return getFeaturedItemsSample(featuredItemsSearchListGroup, featuredItemsFilter.filterTags);
      }
      finally
      {
         featuredFeedbackNode.feedbackLock.readLock().unlock();
      }
   }


   private List<FeaturedItemsSearchList> getFeaturedItemsSearchListGroup(final CriteriaTypeFeaturedPeopleNode featuredFeedbackNode,
                                                                         final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      final List<FeaturedItemsSearchList> featuredItemsSearchListGroup = new ArrayList<>(featuredItemsFilter.filterWebsites.size());
      FeaturedItemsSearchList featuredItemsSearchList;

      for (final PersonalFeedbackWebsite website : featuredItemsFilter.filterWebsites)
      {
         featuredItemsSearchList = getFeaturedItemsSearchListGroup(featuredFeedbackNode.feedbackByWebsite.get(website), featuredItemsFilter);
         if (featuredItemsSearchList != null)
            featuredItemsSearchListGroup.add(featuredItemsSearchList);
      }

      return featuredItemsSearchListGroup;
   }


   private FeaturedItemsSearchList getFeaturedItemsSearchListGroup(final FeaturedItemsFeedbackNode featuredFeedbackNode,
                                                                   final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      if (featuredItemsFilter.filterTags.isEmpty())
         return getInitialisedSearchFeaturedItemsList(featuredFeedbackNode.feedback, null, featuredItemsFilter);
      else
         return getTagFilteredFeaturedItemsSearchListGroup(featuredFeedbackNode.feedbackByTag, featuredItemsFilter);
   }


   private FeaturedItemsSearchList getInitialisedSearchFeaturedItemsList(final List<PersonalFeedbackFeaturedPerson> feedback, final String feedbackSourceTag,
                                                                         final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      if (feedback.isEmpty())
         return null;
      else if (featuredItemsFilter.lastRetrievedSortValue == FeedbactoryConstants.NoTime)
         return new FeaturedItemsSearchList(feedback, feedbackSourceTag, feedback.size() - 1);
      else
      {
         /* Initialise the search start index of the user's next batch of featured items to retrieve, assuming progressively descending movement through the list,
          * based on the user's last retrieved sort value and last retrieved item ID. The combination of the two allows for an efficient binary search within the sorted
          * featured item list, effectively providing a quick starting point.
          *
          * Erroneous lastRetrievedSortValue and/or lastRetrievedItem supplied by rogue clients can't really have an adverse affect; if the rogue item isn't found,
          * the search starting point will at worst be adjusted back to the beginning of the list, otherwise the algorithm just proceeds from wherever the non-existent
          * item would be present.
          */
         final PersonalFeedbackPersonProfile personProfile = new PersonalFeedbackPersonProfile(featuredItemsFilter.lastRetrievedItem);
         final PersonalFeedbackFeaturedPerson searchPerson = new PersonalFeedbackFeaturedPerson(personProfile, featuredItemsFilter.lastRetrievedSortValue);

         int searchStartIndex = Collections.binarySearch(feedback, searchPerson, featuredPersonComparator);

         if ((searchStartIndex == -1) || (searchStartIndex == 0))
         {
            // This feedback list has no more items to offer after the last retrieved item.
            return null;
         }

         if (searchStartIndex > 0)
         {
            // The last retrieved item was found, so bump the pointer to the next ranked item index.
            searchStartIndex --;
         }
         else
         {
            /* If the binary search result is negative, no element having the lastRetrievedSortValue was found and the method will return:
             * -(the index of where the lastRetrievedSortValue should be inserted into the sorted list + 1), so that a return result of 0 is not ambiguous.
             * If the housekeeping run executes between successive retrievals by a client, the lastRetrievedSortValue element may have since been removed, or
             * (in the case of the hot rating) simply changed as a result of more feedback.
             * Either way, there is little point in trying to go searching for the lastRetrievedItem since it could be either no longer present, or way up or
             * down from the last known position. Even if found, the new starting point for retrieving list results may be completely wrong for the user,
             * leading to retrieving items that they had already seen or skipping way ahead of items that they hadn't yet seen. So, the safest approach is to use
             * the binary search result as the starting point. Any entries already received by the client during previous requests can be quietly hidden from the
             * client end; the perks of not working with stateless requests.
             */
            searchStartIndex = -(searchStartIndex + 1);
            if (searchStartIndex >= feedback.size())
            {
               /* If the supplied lastRetrievedSortValue is higher than any existing value, which is highly unlikely though possible due to feedback changes as outlined
                * in the previous comment, simply nudge the starting position to the topmost ranked item in the list.
                */
               searchStartIndex = (feedback.size() - 1);
            }
         }

         return new FeaturedItemsSearchList(feedback, feedbackSourceTag, searchStartIndex);
      }
   }


   private FeaturedItemsSearchList getTagFilteredFeaturedItemsSearchListGroup(final Map<String, List<PersonalFeedbackFeaturedPerson>> feedbackByTag,
                                                                              final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      /* If there are one or more filter tags, all of them must exist against an item profile to constitute a successful match.
       * Try to reduce the upcoming workload by selecting the shortest of the candidate tag lists to examine.
       */
      List<PersonalFeedbackFeaturedPerson> shortestFeaturedItemsList = null;
      String shortestFeaturedItemsListTag = null;
      List<PersonalFeedbackFeaturedPerson> tagFeaturedItemsList;

      for (final String tag : featuredItemsFilter.filterTags)
      {
         tagFeaturedItemsList = feedbackByTag.get(tag);

         if (tagFeaturedItemsList == null)
         {
            // An easy case to handle - one of the requested tags appears nowhere in any featured item, therefore there are no matching items.
            return null;
         }
         else if ((shortestFeaturedItemsList == null) || (tagFeaturedItemsList.size() < shortestFeaturedItemsList.size()))
         {
            shortestFeaturedItemsList = tagFeaturedItemsList;
            shortestFeaturedItemsListTag = tag;
         }
      }

      return getInitialisedSearchFeaturedItemsList(shortestFeaturedItemsList, shortestFeaturedItemsListTag, featuredItemsFilter);
   }


   private List<PersonalFeedbackFeaturedPerson> getFeaturedItemsSample(final List<FeaturedItemsSearchList> featuredItemsSearchListGroup, final Set<String> filterTags)
   {
      final List<PersonalFeedbackFeaturedPerson> featuredFeedbackSample = new ArrayList<>(FeaturedFeedbackMaximumSampleSize);

      if (! filterTags.isEmpty())
         initialiseTagFilteredSearchListGroup(featuredItemsSearchListGroup, filterTags);

      /* All sorted search lists within the group have now been initialised so that they are pointing to the next featured item that matches all filter criteria.
       * From here it's a matter of retrieving the top-ranked FeaturedFeedbackMaximumSampleSize items from across the lists.
       * To make this process as efficient as possible, the list group itself is sorted so that the top-ranked featured item will be accessible from the
       * list at the final index position. After that initialisation, the algorithm becomes very simple: grab that top-ranked item and nudge the pointer for
       * that list to its next filter-matching featured item. Next, remove and immediately re-add that list in its new sorted position within the group using
       * a binary search. Repeat until either FeaturedFeedbackMaximumSampleSize items have been retrieved, or all lists within the group have become exhausted of items.
       * To progressively reduce the processing load, individual exhausted lists are removed from contention on the fly.
       */
      Collections.sort(featuredItemsSearchListGroup, featuredItemsSearchListComparator);

      FeaturedItemsSearchList nextFeaturedItemList;
      int sortedIndex;

      while (featuredFeedbackSample.size() < FeaturedFeedbackMaximumSampleSize)
      {
         if (! featuredItemsSearchListGroup.isEmpty())
         {
            nextFeaturedItemList = featuredItemsSearchListGroup.get(featuredItemsSearchListGroup.size() - 1);
            featuredFeedbackSample.add(nextFeaturedItemList.searchList.get(nextFeaturedItemList.activeSearchIndex));
            nextFeaturedItemList.decrementFeedbackSearchIndex();

            featuredItemsSearchListGroup.remove(featuredItemsSearchListGroup.size() - 1);

            if (getNextFeaturedItem(nextFeaturedItemList, filterTags) != null)
            {
               // The list still has items to offer, so reinsert it into the group at its new sorted position.
               sortedIndex = Collections.binarySearch(featuredItemsSearchListGroup, nextFeaturedItemList, featuredItemsSearchListComparator);
               sortedIndex = -(sortedIndex + 1);
               featuredItemsSearchListGroup.add(sortedIndex, nextFeaturedItemList);
            }
         }
         else
         {
            featuredFeedbackSample.add(FeaturedItemsEndOfDataMarker);
            break;
         }
      }

      return featuredFeedbackSample;
   }


   private void initialiseTagFilteredSearchListGroup(final List<FeaturedItemsSearchList> featuredItemsSearchListGroup, final Set<String> filterTags)
   {
      /* When a tag filter has been specified, ensure that all of the search lists are initialised such that their data index points to the first
       * featured item that matches the tag filter.
       */
      final Iterator<FeaturedItemsSearchList> searchListIterator = featuredItemsSearchListGroup.iterator();
      FeaturedItemsSearchList searchList;

      while (searchListIterator.hasNext())
      {
         searchList = searchListIterator.next();
         if (getNextFeaturedItem(searchList, filterTags) == null)
            searchListIterator.remove();
      }
   }


   private PersonalFeedbackFeaturedPerson getNextFeaturedItem(final FeaturedItemsSearchList featuredItemsSearchList, final Set<String> filterTags)
   {
      PersonalFeedbackFeaturedPerson nextFeaturedPerson;

      while (featuredItemsSearchList.activeSearchIndex >= 0)
      {
         nextFeaturedPerson = featuredItemsSearchList.searchList.get(featuredItemsSearchList.activeSearchIndex);

         if (filterTags.isEmpty() || matchesTagFilter(nextFeaturedPerson, filterTags, featuredItemsSearchList.sourceTag))
            return nextFeaturedPerson;

         featuredItemsSearchList.decrementFeedbackSearchIndex();
      }

      return null;
   }


   private boolean matchesTagFilter(final PersonalFeedbackFeaturedPerson featuredItem, final Set<String> filterTags, final String excludeTag)
   {
      final Set<String> displayNameTags = getItemDisplayNameTags(featuredItem.personProfile.getFullName());

      /* Attempt to find every tag in each candidate feedback item; bail out of processing the item if
       * a tag isn't found in either the item's explicit tags or those implied by the item's display name.
       */
      for (final String tag : filterTags)
      {
         /* Don't recheck for the presence of the tag that was used to obtain the feedback list,
          * since it's already known to be a match.
          * Equals() will always check for reference equality first, so this is not needlessly expensive.
          */
         if (tag.equals(excludeTag))
            continue;

         if ((! featuredItem.personProfile.hasTag(tag)) && (! displayNameTags.contains(tag)))
            return false;
      }

      return true;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private Map<FeedbactoryUserAccount, ItemProfileFeedbackSubmission> handleGetItemFeedbackSubmissions(final PersonalFeedbackPerson person)
   {
      final PersonalFeedbackNode feedbackForPerson = personalFeedback.get(person);
      if (feedbackForPerson != null)
      {
         synchronized (feedbackForPerson)
         {
            /* There's little point in checking the deleted status, since the data is being provided to an external caller; non-deleted nodes
             * may become deleted an instant after this map has been generated.
             */
            return new HashMap<FeedbactoryUserAccount, ItemProfileFeedbackSubmission>(feedbackForPerson.submissions);
         }
      }

      return Collections.emptyMap();
   }


   private ItemProfileFeedbackSubmission handleReplaceItemProfile(final FeedbactoryUserAccount account, final PersonalFeedbackPersonProfile personProfile)
   {
      final PersonalFeedbackNode feedbackForPerson = personalFeedback.get(personProfile.person);
      if (feedbackForPerson != null)
      {
         synchronized (feedbackForPerson)
         {
            return feedbackForPerson.replacePersonProfile(account, personProfile);
         }
      }

      return null;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void validateFieldLength(final String fieldName, final String stringField, final int maximumAllowableLength)
   {
      if ((stringField != null) && (stringField.length() > maximumAllowableLength))
      {
         final String exceptionMessage = "Personal feedback profile length exceeded for field: " + fieldName + ", length was: " + stringField.length();
         throw new IllegalArgumentException(exceptionMessage);
      }
   }


   private String readNullableString(final DataInputStream dataInputStream) throws IOException
   {
      return dataInputStream.readBoolean() ? dataInputStream.readUTF() : null;
   }


   private void writeNullableString(final String string, final DataOutputStream dataOutputStream) throws IOException
   {
      if (string != null)
      {
         dataOutputStream.writeBoolean(true);
         dataOutputStream.writeUTF(string);
      }
      else
         dataOutputStream.writeBoolean(false);
   }


   private PersonalFeedbackPerson handleReadPersonalFeedbackPerson(final DataInputStream dataInputStream) throws IOException
   {
      final short websiteValue = dataInputStream.readShort();
      final PersonalFeedbackWebsite website = PersonalFeedbackWebsite.fromValue(websiteValue);
      if (website == null)
         throw new IllegalArgumentException("Invalid personal feedback website value: " + websiteValue);

      final String personID = dataInputStream.readUTF();
      final byte criteriaTypeValue = dataInputStream.readByte();
      final PersonalFeedbackCriteriaType criteriaType = PersonalFeedbackCriteriaType.fromValue(criteriaTypeValue);

      if (criteriaType == null)
         throw new IllegalArgumentException("Invalid personal feedback criteria type value: " + criteriaTypeValue);
      else if (! website.getCriteriaTypes().contains(criteriaType))
         throw new IllegalArgumentException("Website " + website.getName() + " does not support criteria feedback type: " + criteriaType);

      validateFieldLength("Person ID", personID, PersonalFeedbackConstants.MaximumPersonIDLength);

      return new PersonalFeedbackPerson(website, personID, criteriaType);
   }


   private void handleWritePersonalFeedbackPerson(final PersonalFeedbackPerson person, final DataOutputStream dataOutputStream) throws IOException
   {
      dataOutputStream.writeShort(person.getWebsite().getID());
      dataOutputStream.writeUTF(person.getItemID());
      dataOutputStream.writeByte(person.getCriteriaType().value);
   }


   private PersonalFeedbackPersonProfile handleReadPersonalFeedbackPersonProfile(final DataInputStream dataInputStream) throws IOException
   {
      final PersonalFeedbackPerson person = readFeedbackItem(dataInputStream);

      final String username = readNullableString(dataInputStream);
      final String displayName = readNullableString(dataInputStream);
      final String photoURL = readNullableString(dataInputStream);
      final String url = readNullableString(dataInputStream);
      final Set<String> keywords = readPersonProfileKeywords(dataInputStream);

      validateFieldLength("Username", username, PersonalFeedbackConstants.MaximumPersonProfileUserIDLength);
      validateFieldLength("Display Name", displayName, PersonalFeedbackConstants.MaximumPersonProfileDisplayNameLength);
      validateFieldLength("Photo URL", photoURL, PersonalFeedbackConstants.MaximumPersonProfilePhotoURLLength);
      validateFieldLength("URL", url, PersonalFeedbackConstants.MaximumPersonProfileURLLength);

      return new PersonalFeedbackPersonProfile(person, username, displayName, photoURL, url, keywords);
   }


   private Set<String> readPersonProfileKeywords(final DataInputStream dataInputStream) throws IOException
   {
      final byte numberOfKeywords = dataInputStream.readByte();
      if (numberOfKeywords == 0)
         return Collections.emptySet();
      else if ((numberOfKeywords < 0) || (numberOfKeywords > PersonalFeedbackConstants.MaximumPersonProfileTags))
         throw new IllegalArgumentException("Invalid number of item profile keywords: " + numberOfKeywords);

      final Set<String> keywords = new HashSet<>(numberOfKeywords);
      String keyword;

      for (byte keywordIndex = 0; keywordIndex < numberOfKeywords; keywordIndex ++)
      {
         keyword = dataInputStream.readUTF();

         if ((keyword.length() < PersonalFeedbackConstants.MinimumPersonProfileTagLength) ||
             (keyword.length() > PersonalFeedbackConstants.MaximumPersonProfileTagLength))
            throw new IllegalArgumentException("Invalid item profile keyword length: " + keyword + ", length " + keyword.length());
         else if (keywords.contains(keyword))
            throw new IllegalArgumentException("Duplicate item profile keyword: " + keyword);

         // Many keywords will be heavily duplicated across item profiles and other requests, so intern() them here to save memory.
         keywords.add(keyword.toLowerCase().intern());
      }

      return keywords;
   }


   private void handleWritePersonalFeedbackPersonProfile(final PersonalFeedbackPersonProfile personProfile, final DataOutputStream dataOutputStream) throws IOException
   {
      writeFeedbackItem(personProfile.person, dataOutputStream);

      writeNullableString(personProfile.userID, dataOutputStream);
      writeNullableString(personProfile.nameElements, dataOutputStream);
      writeNullableString(personProfile.imageURLElements, dataOutputStream);
      writeNullableString(personProfile.urlElements, dataOutputStream);
      writePersonProfileKeywords(personProfile, dataOutputStream);
   }


   private void writePersonProfileKeywords(final PersonalFeedbackPersonProfile personProfile, final DataOutputStream dataOutputStream) throws IOException
   {
      final List<String> keywords = personProfile.getTags();

      dataOutputStream.writeByte((byte) keywords.size());
      for (final String keyword : keywords)
         dataOutputStream.writeUTF(keyword);
   }


   private PersonalFeedbackSubmission handleReadFeedbackSubmission(final PersonalFeedbackPerson item, final DataInputStream dataInputStream) throws IOException
   {
      final byte overallFeedbackRating;
      final byte numberOfFeedbackCriteria = dataInputStream.readByte();

      if (numberOfFeedbackCriteria > 0)
      {
         if (item.getCriteriaType() == PersonalFeedbackCriteriaType.None)
            throw new IllegalArgumentException("NoCriteria type applies for item but personal feedback criteria count is non-zero.");

         return readProfileCriteriaFeedbackSubmission(numberOfFeedbackCriteria, item.getCriteriaType().attributes, dataInputStream);
      }
      else
      {
         overallFeedbackRating = dataInputStream.readByte();

         if (overallFeedbackRating == PersonalFeedbackSubmission.NoRatingValue)
            throw new IllegalArgumentException("No overall rating provided for personal feedback submission.");

         return new PersonalFeedbackSubmission(overallFeedbackRating);
      }
   }


   private <E extends Enum<E> & PersonalFeedbackCriteria> PersonalFeedbackSubmission readProfileCriteriaFeedbackSubmission(final int numberOfFeedbackCriteria,
                                                                                                                           final PersonalFeedbackCriteriaAttributes<E> criteriaAttributes,
                                                                                                                           final DataInputStream dataInputStream) throws IOException
   {
      final PersonalFeedbackSubmissionScaleProfile submissionScaleProfile = criteriaAttributes.getSubmissionScaleProfile();
      final EnumMap<E, PersonalFeedbackSubmissionScaleKeyValue> criteriaFeedback = new EnumMap<>(criteriaAttributes.getCriteriaClass());
      byte feedbackCriteriaValue;
      E feedbackCriteria;
      byte submissionScaleValue;
      PersonalFeedbackSubmissionScaleKeyValue submissionScale;

      for (byte feedbackCriteriaIndex = 0; feedbackCriteriaIndex < numberOfFeedbackCriteria; feedbackCriteriaIndex ++)
      {
         feedbackCriteriaValue = dataInputStream.readByte();
         feedbackCriteria = criteriaAttributes.getCriteriaFromValue(feedbackCriteriaValue);
         if (feedbackCriteria == null)
            throw new IllegalArgumentException("Invalid value for " + criteriaAttributes.getCriteriaType().displayName + " feedback criteria: " + feedbackCriteriaValue);

         submissionScaleValue = dataInputStream.readByte();
         submissionScale = submissionScaleProfile.fromValue(submissionScaleValue);

         if (submissionScale == null)
            throw new IllegalArgumentException("Invalid value for " + submissionScaleProfile.getDisplayName() + " submission scale: " + submissionScaleValue);
         if (submissionScale.value == PersonalFeedbackSubmission.NoRatingValue)
            throw new IllegalArgumentException("Personal feedback submission must not contain 'no rating' feedback for criteria.");

         criteriaFeedback.put(feedbackCriteria, submissionScale);
      }

      final boolean isOverallRatingCalculatedFromCriteriaFeedback = dataInputStream.readBoolean();

      if (isOverallRatingCalculatedFromCriteriaFeedback)
         return new PersonalFeedbackSubmission(criteriaFeedback);
      else
      {
         final byte overallFeedbackRating = dataInputStream.readByte();
         return new PersonalFeedbackSubmission(overallFeedbackRating, criteriaFeedback);
      }
   }


   private void handleWriteFeedbackSubmission(final PersonalFeedbackSubmission feedbackSubmission, final DataOutputStream dataOutputStream) throws IOException
   {
      dataOutputStream.writeByte(feedbackSubmission.criteriaSubmissions.size());

      if (feedbackSubmission.criteriaSubmissions.size() > 0)
      {
         for (final Entry<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> criteriaFeedbackEntry : feedbackSubmission.criteriaSubmissions.entrySet())
         {
            dataOutputStream.writeByte(criteriaFeedbackEntry.getKey().getValue());
            dataOutputStream.writeByte(criteriaFeedbackEntry.getValue().value);
         }

         dataOutputStream.writeBoolean(feedbackSubmission.isOverallRatingCalculatedFromCriteriaFeedback);
         if (! feedbackSubmission.isOverallRatingCalculatedFromCriteriaFeedback)
            dataOutputStream.writeByte(feedbackSubmission.overallFeedbackRating);
      }
      else
         dataOutputStream.writeByte(feedbackSubmission.overallFeedbackRating);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleClearItemFeedbackSubmissions()
   {
      personalFeedback.clear();

      for (final CriteriaTypeFeaturedPeopleNode featuredPeopleNode : criteriaTypeNewFeedback.values())
         clearFeaturedItemFeedback(featuredPeopleNode);

      for (final CriteriaTypeFeaturedPeopleNode featuredPeopleNode : criteriaTypeHotFeedback.values())
         clearFeaturedItemFeedback(featuredPeopleNode);
   }


   private void clearFeaturedItemFeedback(final CriteriaTypeFeaturedPeopleNode featuredPeopleNode)
   {
      try
      {
         featuredPeopleNode.feedbackLock.writeLock().lock();

         for (final FeaturedItemsFeedbackNode websiteNode : featuredPeopleNode.feedbackByWebsite.values())
         {
            websiteNode.feedback.clear();
            // TODO: Trim to size?
            websiteNode.feedbackByTag.clear();
         }
      }
      finally
      {
         featuredPeopleNode.feedbackLock.writeLock().unlock();
      }
   }


   /* This method is performing the same work as addPersonalFeedbackSubmission() except that there's no add to the parent feedback manager (this is to be taken care of
    * by the caller), and this method can also assume that there's no thread contention for the person feedback collection and that the necessary locking on the user account is
    * already taken care of by the caller.
    *
    * Also note that the creation time is progressively reset to the earliest submission time. It's unfortunate that this field cannot be made final and
    * initialised in a more efficient way, but there are a few issues that make this a tricky (and/or expensive) goal:
    * 
    * - This method must assume that calls are made to it from the parent feedback manager in no particular order, and furthermore it wouldn't make sense for the parent to
    *   change this behaviour, eg. ordered by feedback item and by created time.
    * - Even if the creation time was saved & later restored, and reconciled to each PersonalFeedbackNode where possible, placing a new put() call to the
    *   personalFeedback collection must be done with care otherwise the key - a PersonalFeedbackPerson object - will no longer be a shared cached object; this will be
    *   the case if for example the PersonalFeedbackPerson is reconstituted from a data file rather than received from the parent as it is here.
    * - Re: the previous point, it would probably be unreasonable to expect the parent feedback manager to also maintain and provide cached copies of the
    *   FeedbackItem's, since it has no use for that collection itself. It would also make no sense for this class to maintain such a collection, since it would only be
    *   used once for the restoration operation.
    * - Likewise providing an intern() method to cache the object on a hidden static map stored within the FeedbackItem itself isn't such a good option,
    *   since it would have to be a concurrent collection and this would be totally wasted on the client, which also uses the FeedbackItem object.
    * - The creation time could be restored via a post-checkpoint-restore method that reads this information from a file, but even aside from the above caching issue to
    *   contend with, there is the problem of dealing with the concurrency: feedback items may be added or removed between the time that the parent feedback manager
    *   persists its data, and then invokes the post-checkpoint-save methods on this class. This is actually not so difficult to deal with but again some extra work:
    *   if a creation time is stored for a feedback item that no longer exists according to the parent feedback manager, the creation time is simply discarded;
    *   in the reverse case, there will be no creation time persisted by this class but any of the item's feedback submission times may be used as a fallback since
    *   it must have been created in response to a feedback submission received at the very moment between the parent persisting its data, and the post-checkpoint-save
    *   being called on this class.
    */
   private void handleRestoreItemFeedbackSubmission(final FeedbactoryUserAccount userAccount, final PersonProfileFeedbackSubmission feedbackSubmission)
   {
      final PersonalFeedbackPerson person = feedbackSubmission.personProfile.person;
      final long submissionTime = feedbackSubmission.submissionTime;

      boolean setCreationTime = false;
      PersonalFeedbackNode feedbackForPerson = personalFeedback.get(person);

      if (feedbackForPerson == null)
      {
         feedbackForPerson = new PersonalFeedbackNode(person.getCriteriaType().attributes, submissionTime);
         personalFeedback.put(person, feedbackForPerson);
      }
      else if (submissionTime < feedbackForPerson.creationTime)
         setCreationTime = true;

      // Ensure the visibility of the updated non-final feedback fields to subsequent threads.
      synchronized (feedbackForPerson)
      {
         feedbackForPerson.addFeedbackSubmission(userAccount, feedbackSubmission);

         if (setCreationTime)
            feedbackForPerson.setCreationTime(submissionTime);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final PersonalFeedbackBasicSummary getPersonalFeedbackBasicSummary(final PersonalFeedbackNode allFeedbackForPerson, final boolean showFeedbackLessThanMinimumThreshold)
   {
      return handleGetPersonalFeedbackBasicSummary(allFeedbackForPerson, showFeedbackLessThanMinimumThreshold);
   }


   final PersonalFeedbackBasicSummary getPersonalFeedbackBasicSummary(final PersonalFeedbackPerson person)
   {
      return handleGetPersonalFeedbackBasicSummary(person);
   }


   final PersonalFeedbackDetailedSummary getPersonalFeedbackDetailedSummary(final PersonalFeedbackPerson person)
   {
      return handleGetPersonalFeedbackDetailedSummary(person);
   }


   final PersonalFeedbackNode addPersonalFeedbackSubmission(final FeedbactoryUserAccount userAccount, final PersonalFeedbackPersonProfile personProfile, final PersonalFeedbackSubmission feedbackSubmission)
   {
      return handleAddItemFeedbackSubmission(userAccount, personProfile, feedbackSubmission);
   }


   final PersonalFeedbackSubmission getPersonalFeedbackSubmission(final FeedbactoryUserAccount userAccount, final PersonalFeedbackPerson person)
   {
      return handleGetPersonalFeedbackSubmission(userAccount, person);
   }


   final PersonalFeedbackNode removePersonalFeedbackSubmission(final FeedbactoryUserAccount userAccount, final PersonalFeedbackPerson person)
   {
      return handleRemovePersonalFeedbackSubmission(userAccount, person);
   }


   final List<PersonalFeedbackFeaturedPerson> getNextNewItemsSample(final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      return handleGetNextNewItemsSample(featuredItemsFilter);
   }


   final List<PersonalFeedbackFeaturedPerson> getNextHotItemsSample(final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      return handleGetNextHotItemsSample(featuredItemsFilter);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public ItemProfileFeedbackSubmission createItemProfileFeedbackSubmission(final FeedbackItemProfile itemProfile, final FeedbackSubmission feedbackSubmission,
                                                                                  final long submissionTime)
   {
      return new PersonProfileFeedbackSubmission((PersonalFeedbackPersonProfile) itemProfile, (PersonalFeedbackSubmission) feedbackSubmission, submissionTime);
   }


   @Override
   final public Set<FeedbackItem> getFeedbackItems()
   {
      return new HashSet<FeedbackItem>(personalFeedback.keySet());
   }


   @Override
   final public Map<FeedbactoryUserAccount, ItemProfileFeedbackSubmission> getItemFeedbackSubmissions(final FeedbackItem item)
   {
      return handleGetItemFeedbackSubmissions((PersonalFeedbackPerson) item);
   }


   @Override
   final public ItemProfileFeedbackSubmission replaceItemProfile(final FeedbactoryUserAccount account, final FeedbackItemProfile itemProfile)
   {
      return handleReplaceItemProfile(account, (PersonalFeedbackPersonProfile) itemProfile);
   }


   @Override
   final public PersonalFeedbackPerson readFeedbackItem(final DataInputStream dataInputStream) throws IOException
   {
      return handleReadPersonalFeedbackPerson(dataInputStream);
   }


   @Override
   final public void writeFeedbackItem(final FeedbackItem item, final DataOutputStream dataOutputStream) throws IOException
   {
      handleWritePersonalFeedbackPerson((PersonalFeedbackPerson) item, dataOutputStream);
   }


   @Override
   final public PersonalFeedbackPersonProfile readFeedbackItemProfile(final DataInputStream dataInputStream) throws IOException
   {
      return handleReadPersonalFeedbackPersonProfile(dataInputStream);
   }


   @Override
   final public void writeFeedbackItemProfile(final FeedbackItemProfile itemProfile, final DataOutputStream dataOutputStream) throws IOException
   {
      handleWritePersonalFeedbackPersonProfile((PersonalFeedbackPersonProfile) itemProfile, dataOutputStream);
   }


   @Override
   final public PersonalFeedbackSubmission readFeedbackSubmission(final FeedbackItem item, final DataInputStream dataInputStream) throws IOException
   {
      return handleReadFeedbackSubmission((PersonalFeedbackPerson) item, dataInputStream);
   }


   @Override
   final public void writeFeedbackSubmission(final FeedbackSubmission feedbackSubmission, final DataOutputStream dataOutputStream) throws IOException
   {
      handleWriteFeedbackSubmission((PersonalFeedbackSubmission) feedbackSubmission, dataOutputStream);
   }


   @Override
   final public void preCheckpointSave(final Path checkpointPath) throws IOException
   {
      // NOP for this category manager.
   }


   @Override
   final public void postCheckpointSave(final Path checkpointPath) throws IOException
   {
      // NOP for this category manager.
   }


   @Override
   final public void clearItemFeedbackSubmissions()
   {
      handleClearItemFeedbackSubmissions();
   }


   @Override
   final public void preCheckpointRestore(final Path checkpointPath) throws IOException
   {
      // NOP for this category manager.
   }


   @Override
   final public void restoreItemFeedbackSubmission(final FeedbactoryUserAccount userAccount, final ItemProfileFeedbackSubmission restoredItemProfileFeedbackSubmission)
   {
      handleRestoreItemFeedbackSubmission(userAccount, (PersonProfileFeedbackSubmission) restoredItemProfileFeedbackSubmission);
   }


   @Override
   final public void postCheckpointRestore(final Path checkpointPath) throws IOException
   {
      // NOP for this category manager.
   }


   @Override
   final public void startHousekeeping()
   {
      housekeepingTask.start();
   }


   @Override
   final public void shutdownHousekeeping() throws InterruptedException
   {
      housekeepingTask.shutdown();
   }
}