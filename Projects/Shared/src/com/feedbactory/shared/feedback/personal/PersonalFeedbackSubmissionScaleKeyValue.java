/* Memos:
 * - For efficiency, the equals method forgoes the check of the display name; PersonalFeedbackSubmissionScaleKeyValue's of different feedback scales
 *   (eg. Excellent, Good, Fair, versus Strongly Agree, Agree, etc) should never be compared, if they are it's because of an error at a higher level.
 *   Even with the display name check it would be possible for example for two PersonalFeedbackSubmissionScaleKeyValue from different scales to be (incorrectly) equal
 *   in the case that both the display names and the values are also identical.
 */

package com.feedbactory.shared.feedback.personal;


final public class PersonalFeedbackSubmissionScaleKeyValue
{
   static final public PersonalFeedbackSubmissionScaleKeyValue NoRating = new PersonalFeedbackSubmissionScaleKeyValue(PersonalFeedbackSubmission.NoRatingValue, "No Rating");

   final public byte value;
   final public String displayName;


   PersonalFeedbackSubmissionScaleKeyValue(final byte value, final String displayName)
   {
      this.value = value;
      this.displayName = displayName;
   }


   @Override
   final public boolean equals(final Object otherObject)
   {
      if (otherObject instanceof PersonalFeedbackSubmissionScaleKeyValue)
         return (((PersonalFeedbackSubmissionScaleKeyValue) otherObject).value == value);
      else
         return false;
   }


   @Override
   final public int hashCode()
   {
      return value;
   }
}