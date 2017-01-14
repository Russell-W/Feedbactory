
package com.feedbactory.client.core.feedback.personal;


import com.feedbactory.client.core.network.DataAvailabilityStatus;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;


public interface CriteriaFeedbackBasicSummaryListener
{
   public void basicFeedbackSummaryUpdated(final PersonalFeedbackPerson item, final DataAvailabilityStatus basicSummaryAvailabilityStatus,
                                           final PersonalFeedbackBasicSummary basicSummary);
}