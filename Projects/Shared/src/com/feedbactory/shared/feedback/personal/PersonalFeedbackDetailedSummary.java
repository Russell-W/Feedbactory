/* Memos:
 * - Note the importance of having the criteriaFeedback constructor parameter be declared as EnumMap rather than just Map: this way the locally created EnumMap
 *   copy can use the EnumMap constructor that doesn't rely on there being at least one mapping present from which to infer the enum key type. See the EnumMap
 *   constructors for more details.
 */

package com.feedbactory.shared.feedback.personal;


import com.feedbactory.shared.feedback.FeedbackResult;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;


final public class PersonalFeedbackDetailedSummary implements FeedbackResult
{
   static final public byte SuppressedLowAveragePercentage = -1;
   static final private byte[] SuppressedLowAveragePercentages = new byte[11];

   static
   {
      Arrays.fill(SuppressedLowAveragePercentages, SuppressedLowAveragePercentage);
   }

   static final public PersonalFeedbackDetailedSummary EmptyFeedbackSummary = new PersonalFeedbackDetailedSummary(new byte[11], true);
   static final public PersonalFeedbackDetailedSummary SuppressedLowAveragePercentagesSummary = new PersonalFeedbackDetailedSummary(SuppressedLowAveragePercentages, true);

   final private byte[] ratingDistributionPercentages;
   final public Map<? extends PersonalFeedbackCriteria, PersonalFeedbackCriteriaDistribution> criteriaFeedback;


   private PersonalFeedbackDetailedSummary(final byte[] ratingDistributionPercentages, final boolean trustedRatingDistributionPercentages)
   {
      if (trustedRatingDistributionPercentages)
      {
         validateRatingDistributionPercentages(ratingDistributionPercentages);

         this.ratingDistributionPercentages = ratingDistributionPercentages.clone();
         criteriaFeedback = Collections.emptyMap();
      }
      else
      {
         this.ratingDistributionPercentages = ratingDistributionPercentages;
         this.criteriaFeedback = Collections.emptyMap();
      }
   }


   private <E extends Enum<E> & PersonalFeedbackCriteria> PersonalFeedbackDetailedSummary(final byte[] ratingDistributionPercentages,
                                                                                          final EnumMap<E, PersonalFeedbackCriteriaDistribution> criteriaFeedback,
                                                                                          final boolean trustedRatingDistributionPercentages)
   {
      if (trustedRatingDistributionPercentages)
      {
         validateRatingDistributionPercentages(ratingDistributionPercentages);
         validateCriteriaFeedback(criteriaFeedback);

         this.ratingDistributionPercentages = ratingDistributionPercentages.clone();
      }
      else
      {
         validateCriteriaFeedback(criteriaFeedback);

         this.ratingDistributionPercentages = ratingDistributionPercentages;
      }

      this.criteriaFeedback = Collections.unmodifiableMap(new EnumMap<E, PersonalFeedbackCriteriaDistribution>(criteriaFeedback));
   }


   public PersonalFeedbackDetailedSummary(final byte[] ratingDistributionPercentages)
   {
      this(ratingDistributionPercentages, false);
   }


   public <E extends Enum<E> & PersonalFeedbackCriteria> PersonalFeedbackDetailedSummary(final byte[] ratingDistributionPercentages,
                                                                                         final EnumMap<E, PersonalFeedbackCriteriaDistribution> criteriaFeedback)
   {
      this(ratingDistributionPercentages, criteriaFeedback, false);
   }


   private void validateRatingDistributionPercentages(final byte[] ratingDistributionPercentages)
   {
      if (ratingDistributionPercentages == null)
         throw new IllegalArgumentException("Rating distribution percentages cannot be null.");
      else if (ratingDistributionPercentages.length != 11)
         throw new IllegalArgumentException("Invalid number of elements in rating distribution percentages array: " + ratingDistributionPercentages.length);
      else
      {
         for (final byte percentage : ratingDistributionPercentages)
         {
            if (((percentage < 0) || (percentage > 100)) && (percentage != SuppressedLowAveragePercentage))
               throw new IllegalArgumentException("Invalid rating distribution percentage: " + percentage);
         }
      }
   }


   private <E extends Enum<E> & PersonalFeedbackCriteria> void validateCriteriaFeedback(final EnumMap<E, PersonalFeedbackCriteriaDistribution> criteriaFeedback)
   {
      if (criteriaFeedback == null)
         throw new IllegalArgumentException("Criteria feedback collections cannot be null.");
      else
      {
         for (final PersonalFeedbackCriteriaDistribution criteriaDistribution : criteriaFeedback.values())
         {
            if (criteriaDistribution == null)
               throw new IllegalArgumentException("Feedback criteria distribution cannot be null.");
         }
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public byte[] getRatingDistributionPercentages()
   {
      return ratingDistributionPercentages.clone();
   }



   /****************************************************************************
    *
    ***************************************************************************/


   static public <E extends Enum<E> & PersonalFeedbackCriteria> PersonalFeedbackDetailedSummary createSuppressedLowAverageFeedbackSummary(final EnumMap<E, PersonalFeedbackCriteriaDistribution> criteriaFeedback)
   {
      return new PersonalFeedbackDetailedSummary(SuppressedLowAveragePercentages, criteriaFeedback, true);
   }
}