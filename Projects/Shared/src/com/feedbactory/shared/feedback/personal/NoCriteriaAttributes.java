
package com.feedbactory.shared.feedback.personal;


enum NoCriteriaAttributes implements PersonalFeedbackCriteriaAttributes<NoCriteria>
{
   instance;

   static final private NoCriteria[] Values = new NoCriteria[0];


   @Override
   final public PersonalFeedbackCriteriaType getCriteriaType()
   {
      return PersonalFeedbackCriteriaType.None;
   }


   @Override
   final public Class<NoCriteria> getCriteriaClass()
   {
      return NoCriteria.class;
   }


   @Override
   final public NoCriteria[] getCriteriaSet()
   {
      return Values;
   }


   @Override
   final public NoCriteria getCriteriaFromValue(final byte value)
   {
      throw new IllegalArgumentException("Invalid NoCriteria criteria value: " + value);
   }


   @Override
   final public PersonalFeedbackSubmissionScaleProfile getSubmissionScaleProfile()
   {
      return NoCriteriaFeedbackSubmissionScale.instance;
   }
}