
package com.feedbactory.client.ui.browser.feedback;


import com.feedbactory.shared.feedback.FeedbackItemProfile;


public interface BrowsedPageResult
{
   public boolean isBrowserReadyStateComplete();
   public FeedbackItemProfile getFeedbackItemProfile();
}