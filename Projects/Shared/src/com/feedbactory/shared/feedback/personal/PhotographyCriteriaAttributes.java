
package com.feedbactory.shared.feedback.personal;


enum PhotographyCriteriaAttributes implements PersonalFeedbackCriteriaAttributes<PhotographyCriteria>
{
   instance;


   @Override
   final public PersonalFeedbackCriteriaType getCriteriaType()
   {
      return PersonalFeedbackCriteriaType.Photography;
   }


   @Override
   final public Class<PhotographyCriteria> getCriteriaClass()
   {
      return PhotographyCriteria.class;
   }


   @Override
   final public PhotographyCriteria[] getCriteriaSet()
   {
      return PhotographyCriteria.values();
   }


   @Override
   final public PhotographyCriteria getCriteriaFromValue(final byte value)
   {
      return PhotographyCriteria.fromValue(value);
   }


   @Override
   final public PersonalFeedbackSubmissionScaleProfile getSubmissionScaleProfile()
   {
      return ExcellentToTerribleFeedbackSubmissionScale.instance;
   }
}