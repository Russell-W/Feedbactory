
package com.feedbactory.client.core.feedback.personal;


import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.shared.feedback.FeedbackSubmissionStatus;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;


final public class PersonalFeedbackAddSubmissionResult
{
   final public NetworkRequestStatus requestStatus;
   final public FeedbackSubmissionStatus feedbackSubmissionStatus;
   final public PersonalFeedbackBasicSummary basicSummary;


   PersonalFeedbackAddSubmissionResult(final NetworkRequestStatus requestStatus)
   {
      this.requestStatus = requestStatus;
      feedbackSubmissionStatus = null;
      basicSummary = null;
   }


   PersonalFeedbackAddSubmissionResult(final NetworkRequestStatus requestStatus, final FeedbackSubmissionStatus feedbackSubmissionStatus)
   {
      this.requestStatus = requestStatus;
      this.feedbackSubmissionStatus = feedbackSubmissionStatus;
      basicSummary = null;
   }


   PersonalFeedbackAddSubmissionResult(final NetworkRequestStatus requestStatus, final FeedbackSubmissionStatus feedbackSubmissionStatus, final PersonalFeedbackBasicSummary feedbackBasicSummary)
   {
      this.requestStatus = requestStatus;
      this.feedbackSubmissionStatus = feedbackSubmissionStatus;
      this.basicSummary = feedbackBasicSummary;
   }
}