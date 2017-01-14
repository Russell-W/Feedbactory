
package com.feedbactory.client.ui.feedback;


public interface FeedbackUIEventListener
{
   public void activeFeedbackItemUpdated(final String itemDisplayName);
   public void activeFeedbackSummaryUpdated(final String feedbackSummaryLabel, final String feedbackSummary);
   public void activeFeedbackNumberOfRatingsUpdated(final String numberOfRatings);

   public void alertActive();
   public void alertCancelled();
}