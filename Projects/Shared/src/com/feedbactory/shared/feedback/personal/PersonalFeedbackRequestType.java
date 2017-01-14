
package com.feedbactory.shared.feedback.personal;


public enum PersonalFeedbackRequestType
{
   GetPersonDetailedFeedbackSummary((byte) 0),
   GetNewFeedbackItemsSample((byte) 1),
   GetHotFeedbackItemsSample((byte) 2);

   final public byte value;


   private PersonalFeedbackRequestType(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public PersonalFeedbackRequestType fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return GetPersonDetailedFeedbackSummary;
         case 1:
            return GetNewFeedbackItemsSample;
         case 2:
            return GetHotFeedbackItemsSample;

         /* No exception thrown here - let the caller decide how to react to a null value, eg. throwing a security exception at
          * encountering an unknown request type on the server.
          */
         default:
            return null;
      }
   }
}