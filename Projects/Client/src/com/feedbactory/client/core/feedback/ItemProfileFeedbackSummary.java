
package com.feedbactory.client.core.feedback;


import com.feedbactory.shared.feedback.FeedbackItemProfile;
import com.feedbactory.shared.feedback.FeedbackResultSummary;


final public class ItemProfileFeedbackSummary
{
   final public FeedbackItemProfile itemProfile;
   final public Object feedbackSubmissionSummary;
   final public long feedbackSubmissionTime;
   final public FeedbackResultSummary feedbackSummary;


   public ItemProfileFeedbackSummary(final FeedbackItemProfile itemProfile, final Object feedbackSubmissionSummary, final long feedbackSubmissionTime, final FeedbackResultSummary feedbackSummary)
   {
      validate(itemProfile, feedbackSubmissionSummary, feedbackSummary);

      this.itemProfile = itemProfile;
      this.feedbackSubmissionSummary = feedbackSubmissionSummary;
      this.feedbackSubmissionTime = feedbackSubmissionTime;
      this.feedbackSummary = feedbackSummary;
   }


   private void validate(final FeedbackItemProfile itemProfile, final Object feedbackSubmissionSummary, final FeedbackResultSummary feedbackSummary)
   {
      if (itemProfile == null)
         throw new IllegalArgumentException("Profile attached to item feedback summary cannot be null.");
      else if (feedbackSubmissionSummary == null)
         throw new IllegalArgumentException("Item feedback submission summary cannot be null.");
      else if (feedbackSummary == null)
         throw new IllegalArgumentException("Item feedback summary cannot be null.");

      // I'm reluctant to enforce any validation on the submission time, eg. if it's set to a slightly future time.
   }
}