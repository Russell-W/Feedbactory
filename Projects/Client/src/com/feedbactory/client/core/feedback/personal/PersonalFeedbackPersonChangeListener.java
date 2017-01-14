
package com.feedbactory.client.core.feedback.personal;


import com.feedbactory.client.core.network.DataAvailabilityStatus;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackDetailedSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmission;


public interface PersonalFeedbackPersonChangeListener
{
   public void personProfileDetailsChanged(final PersonalFeedbackPersonProfile personProfile);

   public void basicFeedbackSummaryAvailabilityStatusChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus);
   public void basicFeedbackSummaryChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus,
                                           final PersonalFeedbackBasicSummary basicFeedbackSummary);

   public void detailedFeedbackSummaryAvailabilityStatusChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus);
   public void detailedFeedbackSummaryChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus,
                                              final PersonalFeedbackDetailedSummary detailedFeedbackSummary);

   public void feedbackSubmissionAvailabilityStatusChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus);
   public void feedbackSubmissionChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus,
                                         final PersonalFeedbackSubmission personalFeedbackSubmission);
}