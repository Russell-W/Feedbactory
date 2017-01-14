
package com.feedbactory.shared.feedback.personal;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


final public class PersonalFeedbackCriteriaDistribution
{
   static final public byte SuppressedLowAverageRating = -1;
   static final public byte MinimumVisibleAverageCriteriaRating = 40;

   static final public PersonalFeedbackCriteriaDistribution NoCriteriaRatings = new PersonalFeedbackCriteriaDistribution();

   final public int numberOfRatings;
   final public byte averageFeedbackRating;
   final public Map<PersonalFeedbackSubmissionScaleKeyValue, Byte> feedbackDistributionPercentages;


   private PersonalFeedbackCriteriaDistribution()
   {
      this.numberOfRatings = 0;
      this.averageFeedbackRating = 0;
      feedbackDistributionPercentages = Collections.emptyMap();
   }


   public PersonalFeedbackCriteriaDistribution(final int numberOfRatings, final byte averageFeedbackRating)
   {
      this(numberOfRatings, averageFeedbackRating, Collections.<PersonalFeedbackSubmissionScaleKeyValue, Byte>emptyMap());
   }


   public PersonalFeedbackCriteriaDistribution(final int numberOfRatings, final byte averageFeedbackRating, final Map<PersonalFeedbackSubmissionScaleKeyValue, Byte> feedbackDistributionPercentages)
   {
      validate(numberOfRatings, averageFeedbackRating, feedbackDistributionPercentages);

      this.numberOfRatings = numberOfRatings;
      this.averageFeedbackRating = averageFeedbackRating;

      if (feedbackDistributionPercentages != Collections.<PersonalFeedbackSubmissionScaleKeyValue, Byte>emptyMap())
         this.feedbackDistributionPercentages = Collections.unmodifiableMap(new HashMap<PersonalFeedbackSubmissionScaleKeyValue, Byte>(feedbackDistributionPercentages));
      else
         this.feedbackDistributionPercentages = Collections.emptyMap();
   }


   private void validateNumberOfRatings(final int numberOfRatings)
   {
      if (numberOfRatings < 0)
         throw new IllegalArgumentException("Invalid number of ratings: " + numberOfRatings);
   }


   private void validate(final int numberOfRatings, final byte averageFeedbackRating, final Map<PersonalFeedbackSubmissionScaleKeyValue, Byte> feedbackDistributionPercentages)
   {
      validateNumberOfRatings(numberOfRatings);

      if (((averageFeedbackRating < 0) || (averageFeedbackRating > 100)) && (averageFeedbackRating != SuppressedLowAverageRating))
         throw new IllegalArgumentException("Invalid criteria average feedback rating: " + averageFeedbackRating);

      validateFeedbackDistribution(feedbackDistributionPercentages);
   }


   private void validateFeedbackDistribution(Map<PersonalFeedbackSubmissionScaleKeyValue, Byte> feedbackDistributionPercentages)
   {
      // Will also throw a NPE if the value is null.
      for (final Byte feedbackDistributionPercentage : feedbackDistributionPercentages.values())
      {
         if ((feedbackDistributionPercentage.byteValue() < 0) || (feedbackDistributionPercentage.byteValue() > 100))
            throw new IllegalArgumentException("Invalid feedback submission scale distribution percentage: " + feedbackDistributionPercentage.byteValue());
      }
   }
}