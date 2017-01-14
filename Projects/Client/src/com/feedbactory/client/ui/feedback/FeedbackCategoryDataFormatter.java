
package com.feedbactory.client.ui.feedback;


import com.feedbactory.shared.feedback.FeedbackResultSummary;


public interface FeedbackCategoryDataFormatter
{
   public String getFeedbackSummaryLabel();
   public String getFeedbackSummaryString(final FeedbackResultSummary feedbackSummary);
   public String getFeedbackSubmissionSummaryString(final Object feedbackSubmissionSummary);

   public byte getSortableFeedbackSummary(final FeedbackResultSummary feedbackSummary);
   public byte getSortableSubmissionSummary(final Object feedbackSubmissionSummary);
}