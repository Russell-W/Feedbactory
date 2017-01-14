
package com.feedbactory.shared.feedback;


public enum FeedbackSubmissionStatus
{
   OK((byte) 0),
   FailedWebsiteNotEnabled((byte) 1),
   FailedTooManySubmissions((byte) 2);


   private FeedbackSubmissionStatus(final byte value)
   {
      this.value = value;
   }


   final public byte value;


   /****************************************************************************
    *
    ***************************************************************************/


   static public FeedbackSubmissionStatus fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return OK;
         case 1:
            return FailedWebsiteNotEnabled;
         case 2:
            return FailedTooManySubmissions;

         default:
            return null;
      }
   }
}