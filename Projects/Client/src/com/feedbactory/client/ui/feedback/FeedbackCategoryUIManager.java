
package com.feedbactory.client.ui.feedback;


import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.feedback.BrowsedPageResult;
import com.feedbactory.client.ui.browser.feedback.FeedbackWebsiteURL;
import com.feedbactory.shared.feedback.FeedbackCategory;
import java.util.List;


public interface FeedbackCategoryUIManager
{
   public FeedbackCategory getFeedbackCategory();

   public List<FeedbackWebsiteURL> getWebsiteURLs();

   // Non-Swing EDT.
   public BrowsedPageResult reportBrowsedPage(final BrowserService browserService);

   public FeedbackPanelUIComponent activateFeedbackPanelComponent(final FeedbackPanel feedbackPanel);
   public void deactivateFeedbackPanelComponent(final FeedbackPanelUIComponent feedbackPanelUIComponent);

   public List<FeedbackMenuItem> getFeedbackMenuItems();
   public void invokeMenuItem(final FeedbackMenuItem menuItem);

   public FeedbackCategoryDataFormatter getFeedbackDataFormatter();

   public void shutdown();
}