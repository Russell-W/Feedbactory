
package com.feedbactory.client.core.feedback;


import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.feedback.FeedbackItem;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import com.feedbactory.shared.feedback.FeedbackResultSummary;


public abstract class FeedbackCategoryHandler
{
   abstract public FeedbackCategory getCategory();
   abstract public FeedbackNetworkGateway getNetworkGateway();
   abstract public FeedbackItemProfile getFeedbackItemProfile(final FeedbackItem feedbackItem);
   abstract public FeedbackResultSummary getFeedbackResultSummary(final FeedbackItem feedbackItem);
   abstract public FeedbackItemProfile updateItemFeedbackSummary(final FeedbackItemProfile itemProfile, final FeedbackResultSummary feedbackResultSummary);
}