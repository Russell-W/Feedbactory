
package com.feedbactory.shared.feedback.personal;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public enum ExcellentToTerribleFeedbackSubmissionScale implements PersonalFeedbackSubmissionScaleProfile
{
   instance;

   static final public PersonalFeedbackSubmissionScaleKeyValue Excellent = new PersonalFeedbackSubmissionScaleKeyValue((byte) 100, "Excellent");
   static final public PersonalFeedbackSubmissionScaleKeyValue Good = new PersonalFeedbackSubmissionScaleKeyValue((byte) 75, "Good");
   static final public PersonalFeedbackSubmissionScaleKeyValue Fair = new PersonalFeedbackSubmissionScaleKeyValue((byte) 50, "Fair");
   static final public PersonalFeedbackSubmissionScaleKeyValue Poor = new PersonalFeedbackSubmissionScaleKeyValue((byte) 25, "Poor");
   static final public PersonalFeedbackSubmissionScaleKeyValue Terrible = new PersonalFeedbackSubmissionScaleKeyValue((byte) 0, "Terrible");

   static final private List<PersonalFeedbackSubmissionScaleKeyValue> scaleKeyValues;

   static
   {
      final List<PersonalFeedbackSubmissionScaleKeyValue> scaleKeyValuesBuilder = new ArrayList<PersonalFeedbackSubmissionScaleKeyValue>(6);
      scaleKeyValuesBuilder.add(Excellent);
      scaleKeyValuesBuilder.add(Good);
      scaleKeyValuesBuilder.add(Fair);
      scaleKeyValuesBuilder.add(Poor);
      scaleKeyValuesBuilder.add(Terrible);
      scaleKeyValuesBuilder.add(PersonalFeedbackSubmissionScaleKeyValue.NoRating);

      scaleKeyValues = Collections.unmodifiableList(scaleKeyValuesBuilder);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public String getDisplayName()
   {
      return "Excellent to Terrible";
   }


   @Override
   final public List<PersonalFeedbackSubmissionScaleKeyValue> getKeyValues()
   {
      return scaleKeyValues;
   }


   @Override
   final public PersonalFeedbackSubmissionScaleKeyValue fromValue(final byte value)
   {
      switch (value)
      {
         case 100:
            return Excellent;
         case 75:
            return Good;
         case 50:
            return Fair;
         case 25:
            return Poor;
         case 0:
            return Terrible;
         case PersonalFeedbackSubmission.NoRatingValue:
            return PersonalFeedbackSubmissionScaleKeyValue.NoRating;

         /* No exception thrown here - let the caller decide how to react to a null value, whether it be throwing a security exception (eg. reading
          * a record on the server), or throwing an illegal argument exception on the client.
          */
         default:
            return null;
      }
   }
}