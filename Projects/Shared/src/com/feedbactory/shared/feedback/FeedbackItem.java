/* Memos:
 * - Adding the website info here was a difficult choice. It's conceivable that for some feedback categories, feedback for a single item might apply across multiple
 *   websites. Essentially the item/entity itself might span across several websites. But in that instance, the item ID would also possibly be invalid. An example might
 *   be a movie or a game that is listed on multiple different websites. I think that if an item can span several sites, it's probably best to represent that information
 *   externally to this class, maybe have a separate class that encapsulates the linked relationship between the same item on different websites.
 */

package com.feedbactory.shared.feedback;


abstract public class FeedbackItem
{
   @Override
   public boolean equals(final Object otherObject)
   {
      if (! (otherObject instanceof FeedbackItem))
         return false;

      final FeedbackItem otherFeedbackItem = (FeedbackItem) otherObject;

      // == is used for the FeedbackCategory enum.
      return (getFeedbackCategory() == otherFeedbackItem.getFeedbackCategory()) &&
              getWebsite().equals(otherFeedbackItem.getWebsite()) &&
              getItemID().equals(otherFeedbackItem.getItemID());
   }


   @Override
   public int hashCode()
   {
      int result = 35;
      result = (31 * result) + getFeedbackCategory().hashCode();
      result = (31 * result) + getWebsite().hashCode();
      result = (31 * result) + getItemID().hashCode();

      return result;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   abstract public FeedbackCategory getFeedbackCategory();
   abstract public FeedbackWebsite getWebsite();
   abstract public Object getItemID();
}