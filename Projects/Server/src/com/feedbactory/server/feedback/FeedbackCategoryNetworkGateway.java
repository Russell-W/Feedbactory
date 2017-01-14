
package com.feedbactory.server.feedback;


import com.feedbactory.server.network.application.ProcessedOperationStatus;
import com.feedbactory.server.network.application.RequestUserSession;
import com.feedbactory.server.network.component.buffer.WritableByteBuffer;
import com.feedbactory.shared.feedback.FeedbackSubmission;
import com.feedbactory.shared.feedback.FeedbackItem;
import com.feedbactory.shared.feedback.FeedbackItemProfile;


public interface FeedbackCategoryNetworkGateway
{
   public void writeHandshakeResponse(final WritableByteBuffer responseBuffer);

   public ProcessedOperationStatus processGetItemFeedbackSummaryRequest(final RequestUserSession userSession);

   public ProcessedOperationStatus processGetItemFeedbackSubmissionRequest(final RequestUserSession userSession);
   public ProcessedOperationStatus processAddItemFeedbackSubmissionRequest(final RequestUserSession userSession);
   public ProcessedOperationStatus processRemoveItemFeedbackSubmissionRequest(final RequestUserSession userSession);

   public ProcessedOperationStatus processFeedbackCategoryRequest(final RequestUserSession userSession);

   public void writeItem(final FeedbackItem item, final WritableByteBuffer responseBuffer);
   public void writeItemProfile(final FeedbackItemProfile itemProfile, final WritableByteBuffer responseBuffer);
   public void writeItemFeedbackSubmission(final FeedbackSubmission feedbackSubmission, final WritableByteBuffer responseBuffer);
   public void writeItemFeedbackSubmissionSummary(final FeedbackSubmission feedbackSubmission, final WritableByteBuffer responseBuffer);
   public void writeItemFeedbackSummary(final FeedbackItem item, final WritableByteBuffer responseBuffer);
}