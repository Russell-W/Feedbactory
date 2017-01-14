
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.client.ui.UIConstants;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingConstants;


final class PersonalFeedbackDetailedSubmissionNotificationPanel
{
   final private PersonalFeedbackBasicSubmissionPanel parentPanel;

   final private JPanel delegatePanel = new JPanel(null);

   final private JLabel ratedByMeLabel = new JLabel();
   final private JLabel ratingResultLabel = new JLabel();

   final private JButton viewFeedbackButton = new JButton();
   final private JButton removeFeedbackButton = new JButton();


   PersonalFeedbackDetailedSubmissionNotificationPanel(final PersonalFeedbackBasicSubmissionPanel parentPanel)
   {
      this.parentPanel = parentPanel;

      initialiseDelegatePanel();
   }


   private void initialiseDelegatePanel()
   {
      initialisePanelContents();

      initialiseDelegatePanelLayout();

      delegatePanel.setOpaque(false);
   }


   private void initialisePanelContents()
   {
      ratedByMeLabel.setFont(UIConstants.RegularFont);
      ratedByMeLabel.setText("Rated by me (0-10):");

      ratingResultLabel.setFont(UIConstants.LargeBoldFont);
      ratingResultLabel.setHorizontalAlignment(SwingConstants.CENTER);

      viewFeedbackButton.setFont(UIConstants.RegularFont);
      viewFeedbackButton.setText("View Feedback");

      viewFeedbackButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleViewFeedbackButtonActionPerformed();
         }
      });

      removeFeedbackButton.setFont(UIConstants.RegularFont);
      removeFeedbackButton.setText("Remove Feedback");

      removeFeedbackButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleRemoveFeedbackButtonActionPerformed();
         }
      });
   }


   private void initialiseDelegatePanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel);
      delegatePanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addGroup(panelLayout.createSequentialGroup()
               .addComponent(ratedByMeLabel)
               .addPreferredGap(ComponentPlacement.RELATED)
               .addComponent(ratingResultLabel, GroupLayout.PREFERRED_SIZE, 35, GroupLayout.PREFERRED_SIZE)
            )
            .addGroup(panelLayout.createSequentialGroup()
               .addComponent(viewFeedbackButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addPreferredGap(ComponentPlacement.UNRELATED)
               .addComponent(removeFeedbackButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
            )
         )
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(ratedByMeLabel)
            .addComponent(ratingResultLabel)
         )
         .addGap(20)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(viewFeedbackButton)
            .addComponent(removeFeedbackButton)
         )
      );
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleViewFeedbackButtonActionPerformed()
   {
      parentPanel.viewDetailedFeedbackSubmission();
   }


   private void handleRemoveFeedbackButtonActionPerformed()
   {
      parentPanel.promptForFeedbackSubmissionRemoval();
   }


   private void handleSetOverallFeedbackRating(final byte overallFeedbackRating)
   {
      ratingResultLabel.setText(Float.toString(((float) overallFeedbackRating) / 10));
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel;
   }


   final void setOverallFeedbackRating(final byte overallFeedbackRating)
   {
      handleSetOverallFeedbackRating(overallFeedbackRating);
   }
}