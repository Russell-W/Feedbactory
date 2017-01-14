
package com.feedbactory.shared.feedback.personal;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public enum VeryToNotAtAllFeedbackSubmissionScale implements PersonalFeedbackSubmissionScaleProfile
{
   instance;

   static final public PersonalFeedbackSubmissionScaleKeyValue Very = new PersonalFeedbackSubmissionScaleKeyValue((byte) 100, "Very");
   static final public PersonalFeedbackSubmissionScaleKeyValue Considerably = new PersonalFeedbackSubmissionScaleKeyValue((byte) 75, "Considerably");
   static final public PersonalFeedbackSubmissionScaleKeyValue Moderately = new PersonalFeedbackSubmissionScaleKeyValue((byte) 50, "Moderately");
   static final public PersonalFeedbackSubmissionScaleKeyValue NotVery = new PersonalFeedbackSubmissionScaleKeyValue((byte) 25, "Not very");
   static final public PersonalFeedbackSubmissionScaleKeyValue NotAtAll = new PersonalFeedbackSubmissionScaleKeyValue((byte) 0, "Not at all");

   static final private List<PersonalFeedbackSubmissionScaleKeyValue> scaleKeyValues;

   static
   {
      final List<PersonalFeedbackSubmissionScaleKeyValue> scaleKeyValuesBuilder = new ArrayList<PersonalFeedbackSubmissionScaleKeyValue>(6);
      scaleKeyValuesBuilder.add(Very);
      scaleKeyValuesBuilder.add(Considerably);
      scaleKeyValuesBuilder.add(Moderately);
      scaleKeyValuesBuilder.add(NotVery);
      scaleKeyValuesBuilder.add(NotAtAll);
      scaleKeyValuesBuilder.add(PersonalFeedbackSubmissionScaleKeyValue.NoRating);

      scaleKeyValues = Collections.unmodifiableList(scaleKeyValuesBuilder);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public String getDisplayName()
   {
      return "Very to Not At All";
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
            return Very;
         case 75:
            return Considerably;
         case 50:
            return Moderately;
         case 25:
            return NotVery;
         case 0:
            return NotAtAll;
         case PersonalFeedbackSubmission.NoRatingValue:
            return PersonalFeedbackSubmissionScaleKeyValue.NoRating;

         /* No exception thrown here - let the caller decide how to react to a null value, whether it be throwing a security exception (eg. reading
          * a record on the server), or throwing an illegal state exception or similar on the client.
          */
         default:
            return null;
      }
   }
}