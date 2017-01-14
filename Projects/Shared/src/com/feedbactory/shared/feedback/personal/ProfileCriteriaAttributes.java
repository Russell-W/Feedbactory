
package com.feedbactory.shared.feedback.personal;


enum ProfileCriteriaAttributes implements PersonalFeedbackCriteriaAttributes<ProfileCriteria>
{
   instance;


   @Override
   final public PersonalFeedbackCriteriaType getCriteriaType()
   {
      return PersonalFeedbackCriteriaType.Profile;
   }


   @Override
   final public Class<ProfileCriteria> getCriteriaClass()
   {
      return ProfileCriteria.class;
   }


   @Override
   final public ProfileCriteria[] getCriteriaSet()
   {
      return ProfileCriteria.values();
   }


   @Override
   final public ProfileCriteria getCriteriaFromValue(final byte value)
   {
      return ProfileCriteria.fromValue(value);
   }


   @Override
   final public PersonalFeedbackSubmissionScaleProfile getSubmissionScaleProfile()
   {
      return VeryToNotAtAllFeedbackSubmissionScale.instance;
   }
}