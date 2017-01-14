
package com.feedbactory.client.ui.settings;


public enum FlashingFeedbackAlertOption
{
   Never((byte) 0, "Never"),
   ShowForItemsHavingFeedback((byte) 1, "For items having feedback"),
   ShowForAnyRecognisedItems((byte) 2, "For any recognised items");

   final byte value;
   public final String displayLabel;


   private FlashingFeedbackAlertOption(final byte value,
                                       final String displayLabel)
   {
      this.value = value;
      this.displayLabel = displayLabel;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public String toString()
   {
      return displayLabel;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static FlashingFeedbackAlertOption fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return Never;
         case 1:
            return ShowForItemsHavingFeedback;
         case 2:
            return ShowForAnyRecognisedItems;

         default:
            throw new AssertionError("Unhandled flashing feedback alert option value: " + value);
      }
   }
}