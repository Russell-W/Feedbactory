
package com.feedbactory.shared.feedback.personal;


// Deleted: Considerate (respectful covers it?), Active (motivated?), Tolerant (respectful?).
// Add: Healthy?
// Could have outwardly confident and inwardly confident?
public enum PersonalCriteria implements PersonalFeedbackCriteria
{
   AccuratePhotos((byte) 0, "Accurate photo(s)"),
   Affectionate((byte) 1, "Affectionate"),
   Appreciative((byte) 2, "Appreciative"),
   Attractive((byte) 3, "Attractive"),
   BaggageFree((byte) 4, "Baggage free"),
   CleanLiving((byte) 5, "Clean living"),
   Committed((byte) 6, "Committed"),
   Confident((byte) 7, "Confident"),
   Creative((byte) 8, "Creative"),
   EasyGoing((byte) 9, "Easy going"),
   Faithful((byte) 10, "Faithful"),
   FinanciallySecure((byte) 11, "Financially secure"),
   Forgiving((byte) 12, "Forgiving"),
   Fun((byte) 13, "Fun"),
   Generous((byte) 14, "Generous"),
   GoodCommunicator((byte) 15, "Good communicator"),
   GoodListener((byte) 16, "Good listener"),
   GoodLover((byte) 17, "Good lover"),
   GoodSenseOfHumour((byte) 18, "Good sense of humour"),
   Happy((byte) 19, "Happy"),
   Healthy((byte) 20, "Healthy"),
   HonestWithOthers((byte) 21, "Honest with others"),
   HonestWithThemselves((byte) 22, "Honest with themselves"),
   Hygienic((byte) 23, "Hygienic"),
   Independent((byte) 24, "Independent"),
   Intelligent((byte) 25, "Intelligent"),
   Interesting((byte) 26, "Interesting"),
   Kind((byte) 27, "Kind"),
   Loyal((byte) 28, "Loyal"),
   Mature((byte) 29, "Mature"),
   Modest((byte) 30, "Modest"),
   Motivated((byte) 31, "Motivated"),
   NonSleazy((byte) 32, "Non-sleazy"),
   NonViolent((byte) 33, "Non-violent"),
   Organised((byte) 34, "Organised"),
   Patient((byte) 35, "Patient"),
   Positive((byte) 36, "Positive"),
   Practical((byte) 37, "Practical"),
   Rational((byte) 38, "Rational"),
   Respectful((byte) 39, "Respectful"),
   SafeInCompany((byte) 40, "Safe in company"),
   Spiritual((byte) 41, "Spiritual"),
   Spontaneous((byte) 42, "Spontaneous"),
   Stylish((byte) 43, "Stylish"),
   Trusting((byte) 44, "Trusting"),
   Unique((byte) 45, "Unique");


   static
   {
      assert checkForDuplicateValues();
      assert checkForMismatchedValues();
   }


   final private byte value;
   final private String displayName;


   private PersonalCriteria(final byte value, final String displayName)
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

      for (final PersonalCriteria criteria : values())
      {
         if (duplicateValueCheck[criteria.value])
            return false;

         duplicateValueCheck[criteria.value] = true;
      }

      return true;
   }


   static private boolean checkForMismatchedValues()
   {
      for (final PersonalCriteria criteria : values())
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


   static public PersonalCriteria fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return AccuratePhotos;
         case 1:
            return Affectionate;
         case 2:
            return Appreciative;
         case 3:
            return Attractive;
         case 4:
            return BaggageFree;
         case 5:
            return CleanLiving;
         case 6:
            return Committed;
         case 7:
            return Confident;
         case 8:
            return Creative;
         case 9:
            return EasyGoing;
         case 10:
            return Faithful;
         case 11:
            return FinanciallySecure;
         case 12:
            return Forgiving;
         case 13:
            return Fun;
         case 14:
            return Generous;
         case 15:
            return GoodCommunicator;
         case 16:
            return GoodListener;
         case 17:
            return GoodLover;
         case 18:
            return GoodSenseOfHumour;
         case 19:
            return Happy;
         case 20:
            return Healthy;
         case 21:
            return HonestWithOthers;
         case 22:
            return HonestWithThemselves;
         case 23:
            return Hygienic;
         case 24:
            return Independent;
         case 25:
            return Intelligent;
         case 26:
            return Interesting;
         case 27:
            return Kind;
         case 28:
            return Loyal;
         case 29:
            return Mature;
         case 30:
            return Modest;
         case 31:
            return Motivated;
         case 32:
            return NonSleazy;
         case 33:
            return NonViolent;
         case 34:
            return Organised;
         case 35:
            return Patient;
         case 36:
            return Positive;
         case 37:
            return Practical;
         case 38:
            return Rational;
         case 39:
            return Respectful;
         case 40:
            return SafeInCompany;
         case 41:
            return Spiritual;
         case 42:
            return Spontaneous;
         case 43:
            return Stylish;
         case 44:
            return Trusting;
         case 45:
            return Unique;

         /* No exception thrown here - let the caller decide how to react to a null value, whether it be throwing a security exception (eg. reading
          * a record on the server), or throwing an illegal state exception or similar on the client.
          */
         default:
            return null;
      }
   }
}