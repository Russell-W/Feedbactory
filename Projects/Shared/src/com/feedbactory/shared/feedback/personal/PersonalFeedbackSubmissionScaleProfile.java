
package com.feedbactory.shared.feedback.personal;


import java.util.List;


public interface PersonalFeedbackSubmissionScaleProfile
{
   public String getDisplayName();
   // Return a list of the scale key values in the order of their declaration.
   public List<PersonalFeedbackSubmissionScaleKeyValue> getKeyValues();
   public PersonalFeedbackSubmissionScaleKeyValue fromValue(final byte value);
}