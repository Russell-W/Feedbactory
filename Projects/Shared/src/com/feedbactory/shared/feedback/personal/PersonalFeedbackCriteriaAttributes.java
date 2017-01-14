
package com.feedbactory.shared.feedback.personal;


public interface PersonalFeedbackCriteriaAttributes<E extends Enum<E> & PersonalFeedbackCriteria>
{
   public PersonalFeedbackCriteriaType getCriteriaType();
   public Class<E> getCriteriaClass();
   public E[] getCriteriaSet();
   public E getCriteriaFromValue(final byte value);
   public PersonalFeedbackSubmissionScaleProfile getSubmissionScaleProfile();
}