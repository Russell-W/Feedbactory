/* Memos:
 * - I'm not sure that the equals() & hashCode() methods are even needed.
 * - For the criteria submissions, PersonalFeedbackSubmissionScaleKeyValue is used rather than a Byte object since in practice the PersonalFeedbackSubmissionScaleKeyValue
 *   will be one of a small number of discrete objects, ie. Excellent, Good, Fair, etc. The server can exploit this to save memory rather than allocate many
 *   instances of a Byte object.
 * - Note the importance of having the criteriaSubmissions constructor parameter be declared as EnumMap rather than just Map: this way the locally created EnumMap
 *   copy can use the EnumMap constructor that doesn't rely on there being at least one mapping present from which to infer the enum key type. See the EnumMap
 *   constructors for more details.
 */

package com.feedbactory.shared.feedback.personal;


import com.feedbactory.shared.feedback.FeedbackSubmission;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;


final public class PersonalFeedbackSubmission implements FeedbackSubmission
{
   static final public byte NoRatingValue = -1;

   static final public PersonalFeedbackSubmission EmptyPersonalFeedbackSubmission = new PersonalFeedbackSubmission(NoRatingValue);

   final public byte overallFeedbackRating;
   final public boolean isOverallRatingCalculatedFromCriteriaFeedback;
   final public Map<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> criteriaSubmissions;


   public PersonalFeedbackSubmission(final byte overallFeedbackRating)
   {
      validateNonCriteriaCalculatedOverallFeedbackRating(overallFeedbackRating);

      this.overallFeedbackRating = overallFeedbackRating;

      isOverallRatingCalculatedFromCriteriaFeedback = false;
      criteriaSubmissions = Collections.emptyMap();
   }


   public <E extends Enum<E> & PersonalFeedbackCriteria> PersonalFeedbackSubmission(final EnumMap<E, PersonalFeedbackSubmissionScaleKeyValue> criteriaSubmissions)
   {
      overallFeedbackRating = calculateDetailedFeedbackRating(criteriaSubmissions);
      isOverallRatingCalculatedFromCriteriaFeedback = true;
      this.criteriaSubmissions = Collections.unmodifiableMap(new EnumMap<E, PersonalFeedbackSubmissionScaleKeyValue>(criteriaSubmissions));
   }


   public <E extends Enum<E> & PersonalFeedbackCriteria> PersonalFeedbackSubmission(final byte overallFeedbackRating, final EnumMap<E, PersonalFeedbackSubmissionScaleKeyValue> criteriaSubmissions)
   {
      validateNonCriteriaCalculatedOverallFeedbackRating(overallFeedbackRating);

      this.overallFeedbackRating = overallFeedbackRating;
      this.isOverallRatingCalculatedFromCriteriaFeedback = false;
      this.criteriaSubmissions = Collections.unmodifiableMap(new EnumMap<E, PersonalFeedbackSubmissionScaleKeyValue>(criteriaSubmissions));
   }


   private void validateNonCriteriaCalculatedOverallFeedbackRating(final byte overallFeedbackRating)
   {
      if ((overallFeedbackRating != NoRatingValue) &&
          ((overallFeedbackRating < 0) || (overallFeedbackRating > 100) || ((overallFeedbackRating % 10) != 0)))
         throw new IllegalArgumentException("Invalid overall feedback rating for non criteria calculated rating: " + overallFeedbackRating);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private boolean criteriaFeedbackEquals(final Map<?, PersonalFeedbackSubmissionScaleKeyValue> otherCriteriaSubmissions)
   {
      return criteriaFeedbackEquals(criteriaSubmissions, otherCriteriaSubmissions) &&
             criteriaFeedbackEquals(otherCriteriaSubmissions, criteriaSubmissions);
   }


   private boolean criteriaFeedbackEquals(final Map<?, PersonalFeedbackSubmissionScaleKeyValue> criteriaSubmissionsOne,
                                          final Map<?, PersonalFeedbackSubmissionScaleKeyValue> criteriaSubmissionsTwo)
   {
      for (final Entry<?, PersonalFeedbackSubmissionScaleKeyValue> criteriaSubmissionsOneEntry : criteriaSubmissionsOne.entrySet())
      {
         if (criteriaSubmissionsOneEntry.getValue().value != NoRatingValue)
         {
            if (! criteriaSubmissionsOneEntry.getValue().equals(criteriaSubmissionsTwo.get(criteriaSubmissionsOneEntry.getKey())))
               return false;
         }
      }

      return true;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public boolean equals(final Object otherObject)
   {
      if (! (otherObject instanceof PersonalFeedbackSubmission))
         return false;

      final PersonalFeedbackSubmission otherFeedbackSubmission = (PersonalFeedbackSubmission) otherObject;

      return (overallFeedbackRating == otherFeedbackSubmission.overallFeedbackRating) &&
             (isOverallRatingCalculatedFromCriteriaFeedback == otherFeedbackSubmission.isOverallRatingCalculatedFromCriteriaFeedback) &&
             (criteriaSubmissions.equals(otherFeedbackSubmission.criteriaSubmissions));
   }


   @Override
   final public int hashCode()
   {
      int result = 35;
      result = (31 * result) + overallFeedbackRating;
      result = (31 * result) + (isOverallRatingCalculatedFromCriteriaFeedback ? 1 : 0);
      result = (31 * result) + criteriaSubmissions.hashCode();

      return result;
   }


   @Override
   final public Byte getSummary()
   {
      return overallFeedbackRating;
   }


   final public boolean hasAtLeastOneCriteriaRating()
   {
      for (final PersonalFeedbackSubmissionScaleKeyValue submissionRating : criteriaSubmissions.values())
      {
         if (submissionRating.value != NoRatingValue)
            return true;
      }

      return false;
   }


   @SuppressWarnings("unchecked")
   final public boolean feedbackEquals(final PersonalFeedbackSubmission otherFeedbackSubmission)
   {
      if (otherFeedbackSubmission == null)
         return false;

      if ((isOverallRatingCalculatedFromCriteriaFeedback == otherFeedbackSubmission.isOverallRatingCalculatedFromCriteriaFeedback) &&
          (overallFeedbackRating == otherFeedbackSubmission.overallFeedbackRating))
         return criteriaFeedbackEquals(otherFeedbackSubmission.criteriaSubmissions);
      else
         return false;
   }


   static public byte calculateDetailedFeedbackRating(final Map<?, PersonalFeedbackSubmissionScaleKeyValue> updatedFormFeedback)
   {
      int cumulativeRatingScore = 0;
      int criteriaItemsFilledOut = 0;

      for (final PersonalFeedbackSubmissionScaleKeyValue feedbackItem : updatedFormFeedback.values())
      {
         if (feedbackItem.value != NoRatingValue)
         {
            cumulativeRatingScore += feedbackItem.value;
            criteriaItemsFilledOut ++;
         }
      }

      if (criteriaItemsFilledOut > 0)
         return (byte) Math.round(((float) cumulativeRatingScore) / criteriaItemsFilledOut);
      else
         return NoRatingValue;
   }
}