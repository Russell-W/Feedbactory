
package com.feedbactory.client.ui.feedback;


import com.feedbactory.shared.feedback.FeedbackCategory;


final public class FeedbackMenuItem
{
   final public FeedbackCategory feedbackCategory;
   final public String menuItemLabel;
   final public String menuItemIdentifier;


   public FeedbackMenuItem(final FeedbackCategory feedbackCategory, final String menuItemLabel, final String menuItemIdentifier)
   {
      this.feedbackCategory = feedbackCategory;
      this.menuItemLabel = menuItemLabel;
      this.menuItemIdentifier = menuItemIdentifier;
   }
}