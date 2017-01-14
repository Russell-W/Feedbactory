
package com.feedbactory.client.core.feedback.personal;


import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;
import com.feedbactory.shared.network.BasicOperationStatus;


public class PersonalFeedbackBasicSummaryResult
{
   final public NetworkRequestStatus requestStatus;
   final public BasicOperationStatus operationStatus;
   final public PersonalFeedbackBasicSummary basicSummary;


   PersonalFeedbackBasicSummaryResult(final NetworkRequestStatus requestStatus)
   {
      this.requestStatus = requestStatus;
      operationStatus = null;
      basicSummary = null;
   }


   PersonalFeedbackBasicSummaryResult(final NetworkRequestStatus requestStatus, final BasicOperationStatus operationStatus)
   {
      this.requestStatus = requestStatus;
      this.operationStatus = operationStatus;
      basicSummary = null;
   }


   PersonalFeedbackBasicSummaryResult(final NetworkRequestStatus requestStatus, final BasicOperationStatus operationStatus, final PersonalFeedbackBasicSummary basicSummary)
   {
      this.requestStatus = requestStatus;
      this.operationStatus = operationStatus;
      this.basicSummary = basicSummary;
   }
}