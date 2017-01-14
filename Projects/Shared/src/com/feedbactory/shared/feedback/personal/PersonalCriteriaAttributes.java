
package com.feedbactory.shared.feedback.personal;


enum PersonalCriteriaAttributes implements PersonalFeedbackCriteriaAttributes<PersonalCriteria>
{
   instance;


   @Override
   final public PersonalFeedbackCriteriaType getCriteriaType()
   {
      return PersonalFeedbackCriteriaType.Personal;
   }


   @Override
   final public Class<PersonalCriteria> getCriteriaClass()
   {
      return PersonalCriteria.class;
   }


   @Override
   final public PersonalCriteria[] getCriteriaSet()
   {
      return PersonalCriteria.values();
   }


   @Override
   final public PersonalCriteria getCriteriaFromValue(final byte value)
   {
      return PersonalCriteria.fromValue(value);
   }


   @Override
   final public PersonalFeedbackSubmissionScaleProfile getSubmissionScaleProfile()
   {
      return VeryToNotAtAllFeedbackSubmissionScale.instance;
   }
}