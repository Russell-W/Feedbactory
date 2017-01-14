

package com.feedbactory.shared.feedback;


public enum FeedbackCategory
{
   Personal((short) 0);


   final public short value;


   FeedbackCategory(final short value)
   {
      this.value = value;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public FeedbackCategory fromValue(final short value)
   {
      switch (value)
      {
         case 0:
            return Personal;

         default:
            return null;
      }
   }
}