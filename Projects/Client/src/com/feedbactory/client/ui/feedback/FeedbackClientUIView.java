
package com.feedbactory.client.ui.feedback;


public interface FeedbackClientUIView
{
   public void addAlertListener(final FeedbackUIEventListener feedbackAlertListener);
   public void removeAlertListener(final FeedbackUIEventListener feedbackAlertListener);
}