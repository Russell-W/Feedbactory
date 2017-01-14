
package com.feedbactory.server.feedback;


import com.feedbactory.shared.feedback.FeedbackSubmission;
import com.feedbactory.shared.feedback.FeedbackItemProfile;


public interface ItemProfileFeedbackSubmission
{
   public FeedbackItemProfile getItemProfile();
   public FeedbackSubmission getFeedbackSubmission();
   public long getSubmissionTime();
}