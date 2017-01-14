
package com.feedbactory.shared.feedback;


import java.util.List;


abstract public class FeedbackItemProfile
{
   final public FeedbackCategory getFeedbackCategory()
   {
      return getItem().getFeedbackCategory();
   }


   abstract public FeedbackItem getItem();
   abstract public String getShortName();
   abstract public String getFullName();
   abstract public String getThumbnailImageURL();
   abstract public String getLargeImageURL();
   abstract public String getURL();
   abstract public boolean hasTag(final String tag);
   abstract public List<String> getTags();


   @Override
   public String toString()
   {
      // Ensures a user friendly display when FeedbackItemProfile objects are displayed in an editable combo box.
      return getFullName();
   }
}