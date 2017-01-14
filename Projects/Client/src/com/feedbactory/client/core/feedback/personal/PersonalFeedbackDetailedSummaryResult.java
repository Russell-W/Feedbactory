
package com.feedbactory.client.core.feedback.personal;


import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackDetailedSummary;
import com.feedbactory.shared.network.BasicOperationStatus;


public class PersonalFeedbackDetailedSummaryResult
{
   final public NetworkRequestStatus requestStatus;
   final public BasicOperationStatus operationStatus;
   final public PersonalFeedbackDetailedSummary detailedSummary;


   PersonalFeedbackDetailedSummaryResult(final NetworkRequestStatus requestStatus)
   {
      this.requestStatus = requestStatus;
      operationStatus = null;
      detailedSummary = null;
   }


   PersonalFeedbackDetailedSummaryResult(final NetworkRequestStatus requestStatus, final BasicOperationStatus operationStatus)
   {
      this.requestStatus = requestStatus;
      this.operationStatus = operationStatus;
      detailedSummary = null;
   }


   PersonalFeedbackDetailedSummaryResult(final NetworkRequestStatus requestStatus, final BasicOperationStatus operationStatus,
                                         final PersonalFeedbackDetailedSummary detailedSummary)
   {
      this.requestStatus = requestStatus;
      this.operationStatus = operationStatus;
      this.detailedSummary = detailedSummary;
   }
}