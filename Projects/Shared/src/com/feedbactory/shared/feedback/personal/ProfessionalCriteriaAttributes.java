
package com.feedbactory.shared.feedback.personal;


enum ProfessionalCriteriaAttributes implements PersonalFeedbackCriteriaAttributes<ProfessionalCriteria>
{
   instance;


   @Override
   final public PersonalFeedbackCriteriaType getCriteriaType()
   {
      return PersonalFeedbackCriteriaType.Professional;
   }


   @Override
   final public Class<ProfessionalCriteria> getCriteriaClass()
   {
      return ProfessionalCriteria.class;
   }


   @Override
   final public ProfessionalCriteria[] getCriteriaSet()
   {
      return ProfessionalCriteria.values();
   }


   @Override
   final public ProfessionalCriteria getCriteriaFromValue(final byte value)
   {
      return ProfessionalCriteria.fromValue(value);
   }


   @Override
   final public PersonalFeedbackSubmissionScaleProfile getSubmissionScaleProfile()
   {
      return VeryToNotAtAllFeedbackSubmissionScale.instance;
   }
}