
package com.feedbactory.shared.feedback.personal;


import java.util.Collections;
import java.util.List;


public enum NoCriteriaFeedbackSubmissionScale implements PersonalFeedbackSubmissionScaleProfile
{
   instance;


   @Override
   final public String getDisplayName()
   {
      return "No Criteria";
   }


   @Override
   final public List<PersonalFeedbackSubmissionScaleKeyValue> getKeyValues()
   {
      return Collections.emptyList();
   }


   @Override
   final public PersonalFeedbackSubmissionScaleKeyValue fromValue(final byte value)
   {
      return null;
   }
}