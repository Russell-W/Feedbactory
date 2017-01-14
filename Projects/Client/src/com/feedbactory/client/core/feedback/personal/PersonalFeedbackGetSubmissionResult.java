
package com.feedbactory.client.core.feedback.personal;


import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmission;
import com.feedbactory.shared.network.BasicOperationStatus;


final public class PersonalFeedbackGetSubmissionResult
{
   final public NetworkRequestStatus requestStatus;
   final public BasicOperationStatus operationStatus;
   final public PersonalFeedbackSubmission submission;


   PersonalFeedbackGetSubmissionResult(final NetworkRequestStatus requestStatus)
   {
      this.requestStatus = requestStatus;
      operationStatus = null;
      submission = null;
   }


   PersonalFeedbackGetSubmissionResult(final NetworkRequestStatus requestStatus, final BasicOperationStatus operationStatus)
   {
      this.requestStatus = requestStatus;
      this.operationStatus = operationStatus;
      submission = null;
   }


   PersonalFeedbackGetSubmissionResult(final NetworkRequestStatus requestStatus, final BasicOperationStatus operationStatus, final PersonalFeedbackSubmission submission)
   {
      this.requestStatus = requestStatus;
      this.operationStatus = operationStatus;
      this.submission = submission;
   }
}