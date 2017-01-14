
package com.feedbactory.shared.feedback.personal;


public enum ProfileCriteria implements PersonalFeedbackCriteria
{
   Attractive((byte) 0, "Attractive"),
   ClearPhotos((byte) 1, "Clear photo(s)"),
   Fun((byte) 2, "Fun"),
   GoodSenseOfHumour((byte) 3, "Good sense of humour"),
   Informative((byte) 4, "Informative"),
   Intelligent((byte) 5, "Intelligent"),
   Interesting((byte) 6, "Interesting"),
   Modest((byte) 7, "Modest"),
   NonSleazy((byte) 8, "Non-sleazy"),
   Unique((byte) 9, "Unique");


   static
   {
      assert checkForDuplicateValues();
      assert checkForMismatchedValues();
   }


   final private byte value;
   final private String displayName;


   private ProfileCriteria(final byte value, final String displayName)
   {
      this.value = value;
      this.displayName = displayName;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private boolean checkForDuplicateValues()
   {
      final boolean[] duplicateValueCheck = new boolean[Byte.MAX_VALUE];

      for (final ProfileCriteria criteria : values())
      {
         if (duplicateValueCheck[criteria.value])
            return false;

         duplicateValueCheck[criteria.value] = true;
      }

      return true;
   }


   static private boolean checkForMismatchedValues()
   {
      for (final ProfileCriteria criteria : values())
      {
         if (fromValue(criteria.value) != criteria)
            return false;
      }

      return true;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public byte getValue()
   {
      return value;
   }


   @Override
   final public String getDisplayName()
   {
      return displayName;
   }


   @Override
   final public String toString()
   {
      return displayName;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public ProfileCriteria fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return Attractive;
         case 1:
            return ClearPhotos;
         case 2:
            return Fun;
         case 3:
            return GoodSenseOfHumour;
         case 4:
            return Informative;
         case 5:
            return Intelligent;
         case 6:
            return Interesting;
         case 7:
            return Modest;
         case 8:
            return NonSleazy;
         case 9:
            return Unique;

         /* No exception thrown here - let the caller decide how to react to a null value, whether it be throwing a security exception (eg. reading
          * a record on the server), or throwing an illegal state exception or similar on the client.
          */
         default:
            return null;
      }
   }
}