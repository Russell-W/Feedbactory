
package com.feedbactory.server.feedback;


import com.feedbactory.shared.feedback.FeedbackCategory;


public abstract class FeedbackCategoryHandler
{
   abstract public FeedbackCategory getCategory();
   abstract public FeedbackCategoryManager getCategoryManager();
   abstract public FeedbackCategoryNetworkGateway getCategoryNetworkGateway();

   abstract public String processConsoleCommand(final String[] command);
}