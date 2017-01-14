
package com.feedbactory.client.ui.feedback;


import com.feedbactory.client.core.useraccount.FeedbactoryUserAccount;
import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.feedback.FeedbackItem;
import javax.swing.JComponent;


public interface FeedbackPanelUIComponent
{
   public FeedbackCategory getFeedbackCategory();

   public JComponent getDelegate();

   public void signedInToUserAccount(final FeedbactoryUserAccount userAccount);
   public void userAccountDetailsUpdated(final FeedbactoryUserAccount userAccount);
   public void signedOutOfUserAccount(final FeedbactoryUserAccount userAccount);

   public void showItem(final FeedbackItem itemID);
   public void refreshItem();
   public boolean hasItemFeedbackSubmissionBeenUpdated();
}