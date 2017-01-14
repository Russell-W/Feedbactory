/* Memos:
 * - hashCode() has been implemented for correctness, but this class is never intended to be used as a key in any hashing collection.
 * - The equals() is useful in the client's FeedbackManager, when updating a (possibly identical) FeedbackResultSummary.
 */

package com.feedbactory.shared.feedback.personal;


import com.feedbactory.shared.feedback.FeedbackResultSummary;


final public class PersonalFeedbackBasicSummary implements FeedbackResultSummary
{
   /* Any average personal rating below this threshold will not be visible to users, or even sent as-is from the server.
    * Hopefully this will help discourage the practice of feedback trolling.
    */
   static final public byte MinimumVisibleAverageRating = 40;

   // Server transmitted value to indicate that the average rating for an item is below the threshold.
   static final public byte SuppressedLowAverageRating = -1;

   static final public PersonalFeedbackBasicSummary EmptyFeedbackBasicSummary = new PersonalFeedbackBasicSummary(0, (byte) 0);

   final public int numberOfRatings;
   final public byte averageRating;


   public PersonalFeedbackBasicSummary(final int numberOfRatings, final byte averageRating)
   {
      this.numberOfRatings = numberOfRatings;
      this.averageRating = averageRating;
   }


   @Override
   final public int getNumberOfRatings()
   {
      return numberOfRatings;
   }


   @Override
   final public Byte getFeedbackResultSummary()
   {
      return averageRating;
   }


   @Override
   final public boolean equals(final Object otherObject)
   {
      if (! (otherObject instanceof PersonalFeedbackBasicSummary))
         return false;

      final PersonalFeedbackBasicSummary otherFeedbackBasicSummary = (PersonalFeedbackBasicSummary) otherObject;
      return (otherFeedbackBasicSummary.numberOfRatings == numberOfRatings) && (otherFeedbackBasicSummary.averageRating == averageRating);
   }


   @Override
   final public int hashCode()
   {
      int result = 38;
      result = (31 * result) + numberOfRatings;
      result = (31 * result) + averageRating;

      return result;
   }
}