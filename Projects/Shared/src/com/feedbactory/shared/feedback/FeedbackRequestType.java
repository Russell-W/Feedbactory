
package com.feedbactory.shared.feedback;


public enum FeedbackRequestType
{
   GetItemFeedbackSummary((byte) 0),
   GetItemFeedbackSubmission((byte) 1),
   AddItemFeedbackSubmission((byte) 2),
   RemoveItemFeedbackSubmission((byte) 3),
   GetUserFeedbackSubmissions((byte) 4),
   FeedbackCategoryRequest(Byte.MAX_VALUE);

   final public byte value;


   private FeedbackRequestType(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public FeedbackRequestType fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return GetItemFeedbackSummary;
         case 1:
            return GetItemFeedbackSubmission;
         case 2:
            return AddItemFeedbackSubmission;
         case 3:
            return RemoveItemFeedbackSubmission;
         case 4:
            return GetUserFeedbackSubmissions;

         case Byte.MAX_VALUE:
            return FeedbackCategoryRequest;

         default:
            return null;
      }
   }
}