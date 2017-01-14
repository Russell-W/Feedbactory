
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.client.core.network.DataAvailabilityStatus;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.HorizontalSeparator;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackDetailedSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmission;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;


final class PersonalFeedbackDetailedPanel
{
   final private PersonalFeedbackRootPanel rootPanel;

   final private JPanel delegatePanel = new JPanel(null);

   final private JComponent innerPanel = new RoundedPanel();

   final private JToggleButton feedbackFromEveryoneButton = new JToggleButton();
   final private JToggleButton feedbackFromMeButton = new JToggleButton();
   final private ButtonGroup feedbackPanelSelectionButtonGroup = new ButtonGroup();

   final private PersonalFeedbackFromEveryonePanel detailedFeedbackSummaryPanel = new PersonalFeedbackFromEveryonePanel(this);
   final private PersonalFeedbackFromUserPanel detailedFeedbackSubmissionPanel = new PersonalFeedbackFromUserPanel(this);

   private DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus;


   PersonalFeedbackDetailedPanel(final PersonalFeedbackRootPanel rootPanel)
   {
      this.rootPanel = rootPanel;

      initialise();
   }


   private void initialise()
   {
      initialiseInnerPanel();
      initialisePanelSelectionButtons();
      initialiseDelegatePanel();
   }


   private void initialiseInnerPanel()
   {
      innerPanel.setBackground(UIConstants.ContentPanelColour);

      final GroupLayout panelLayout = new GroupLayout(innerPanel);
      innerPanel.setLayout(panelLayout);

      /* The innerPanel is a rounded panel containing both the detailed feedback submission and detailed feedback summary panels.
       * Why not instead have each of those subpanels use a lockable rounded panel and then add those panels directly to the outer panel?
       * Because the blur effect wouldn't appear correctly. When a blur effect is applied to a component, it will blur to very the edges
       * including those rounded corners, which means that everything looks boxy. So, use an intermediary containing panel with a rounded
       * border which provides its own natural vertical and horizontal gap at the edges and can therefore hide the squared off corners of any
       * blur effects that the child components use.
       */
      panelLayout.setHorizontalGroup(panelLayout.createParallelGroup()
         .addComponent(detailedFeedbackSummaryPanel.getDelegatePanel())
         .addComponent(detailedFeedbackSubmissionPanel.getDelegatePanel())
      );

      panelLayout.setVerticalGroup(panelLayout.createParallelGroup()
         .addComponent(detailedFeedbackSummaryPanel.getDelegatePanel(), GroupLayout.PREFERRED_SIZE, 406, GroupLayout.PREFERRED_SIZE)
         .addComponent(detailedFeedbackSubmissionPanel.getDelegatePanel(), GroupLayout.PREFERRED_SIZE, 406, GroupLayout.PREFERRED_SIZE)
      );

      detailedFeedbackSummaryPanel.getDelegatePanel().setVisible(false);
   }


   private void initialisePanelSelectionButtons()
   {
      feedbackFromEveryoneButton.setFont(UIConstants.RegularFont);
      feedbackFromEveryoneButton.setText("Feedback From Everyone");
      feedbackFromEveryoneButton.setFocusable(false);

      feedbackFromEveryoneButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleShowFeedbackFromEveryoneButtonActionPerformed();
         }
      });

      feedbackFromMeButton.setFont(UIConstants.RegularFont);
      feedbackFromMeButton.setText("Feedback From Me");
      feedbackFromMeButton.setFocusable(false);

      feedbackFromMeButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleShowFeedbackFromUserButtonActionPerformed();
         }
      });

      feedbackPanelSelectionButtonGroup.add(feedbackFromEveryoneButton);
      feedbackPanelSelectionButtonGroup.add(feedbackFromMeButton);
      feedbackPanelSelectionButtonGroup.setSelected(feedbackFromMeButton.getModel(), true);
   }


   private void initialiseDelegatePanel()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel);
      delegatePanel.setLayout(panelLayout);

      final HorizontalSeparator separator = new HorizontalSeparator();

      panelLayout.setHorizontalGroup(panelLayout.createParallelGroup()
         .addComponent(separator, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
         .addGroup(panelLayout.createSequentialGroup()
            .addComponent(feedbackFromEveryoneButton)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(feedbackFromMeButton)
         )
         .addComponent(innerPanel)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addComponent(separator, GroupLayout.PREFERRED_SIZE, 15, GroupLayout.PREFERRED_SIZE)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(feedbackFromEveryoneButton)
            .addComponent(feedbackFromMeButton)
         )
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(innerPanel)
      );
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleShowFeedbackFromUserButtonActionPerformed()
   {
      showDetailedFeedbackSubmissionPanel();
   }


   private void handleShowFeedbackFromEveryoneButtonActionPerformed()
   {
      showDetailedFeedbackSummaryPanel();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleShowUserEntry(final PersonalFeedbackPersonProfile userProfile,
                                    final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus,
                                    final PersonalFeedbackDetailedSummary detailedFeedbackSummary,
                                    final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus,
                                    final PersonalFeedbackSubmission feedbackSubmission)
   {
      this.detailedFeedbackSummaryAvailabilityStatus = detailedFeedbackSummaryAvailabilityStatus;

      detailedFeedbackSummaryPanel.showUserEntry(userProfile, detailedFeedbackSummaryAvailabilityStatus, detailedFeedbackSummary);

      detailedFeedbackSubmissionPanel.showPersonProfileFeedback(userProfile, feedbackSubmissionAvailabilityStatus, feedbackSubmission);
   }


   private void handleShowFeedbackFromUserPanel()
   {
      if (! isShowingDetailedFeedbackSubmissionPanel())
      {
         feedbackPanelSelectionButtonGroup.setSelected(feedbackFromMeButton.getModel(), true);

         detailedFeedbackSubmissionPanel.getDelegatePanel().setVisible(true);
         detailedFeedbackSummaryPanel.getDelegatePanel().setVisible(false);

         rootPanel.requestRepack();
      }
   }


   private void handleShowFeedbackFromEveryonePanel()
   {
      if (! isShowingDetailedFeedbackSummaryPanel())
      {
         feedbackPanelSelectionButtonGroup.setSelected(feedbackFromEveryoneButton.getModel(), true);

         detailedFeedbackSummaryPanel.getDelegatePanel().setVisible(true);
         detailedFeedbackSubmissionPanel.getDelegatePanel().setVisible(false);

         rootPanel.requestRepack();

         if (needsToLoadDetailedFeedbackSummary())
            requestFetchDetailedFeedbackSummary();
      }
   }


   private boolean handleNeedsToLoadDetailedFeedbackSummary()
   {
      return (feedbackFromEveryoneButton.isSelected() &&
             (detailedFeedbackSummaryAvailabilityStatus == DataAvailabilityStatus.NotAvailable) ||
             (detailedFeedbackSummaryAvailabilityStatus == DataAvailabilityStatus.Failed));
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel;
   }


   final void reportFeedbactoryUserAccountStatus(final boolean isUserSignedIntoFeedbactoryAccount)
   {
      detailedFeedbackSubmissionPanel.feedbactoryUserAccountStatusUpdated(isUserSignedIntoFeedbactoryAccount);
   }


   final void showPersonProfileFeedback(final PersonalFeedbackPersonProfile userProfile,
                                        final DataAvailabilityStatus detailedFeedbackFromEveryoneAvailabilityStatus,
                                        final PersonalFeedbackDetailedSummary detailedFeedbackFromEveryone,
                                        final DataAvailabilityStatus detailedFeedbackFromUserAvailabilityStatus,
                                        final PersonalFeedbackSubmission detailedFeedbackFromUser)
   {
      handleShowUserEntry(userProfile, detailedFeedbackFromEveryoneAvailabilityStatus, detailedFeedbackFromEveryone,
                          detailedFeedbackFromUserAvailabilityStatus, detailedFeedbackFromUser);
   }


   final void profileDetailsUpdated(final PersonalFeedbackPersonProfile updatedPersonProfile)
   {
      detailedFeedbackSummaryPanel.profileDetailsUpdated(updatedPersonProfile);
      detailedFeedbackSubmissionPanel.profileDetailsUpdated(updatedPersonProfile);
   }


   final void detailedFeedbackSummaryUpdated(final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus,
                                             final PersonalFeedbackDetailedSummary detailedFeedbackSummary)
   {
      this.detailedFeedbackSummaryAvailabilityStatus = detailedFeedbackSummaryAvailabilityStatus;

      detailedFeedbackSummaryPanel.detailedFeedbackSummaryUpdated(detailedFeedbackSummaryAvailabilityStatus, detailedFeedbackSummary);
   }


   final void feedbackFromUserUpdated(final DataAvailabilityStatus detailedFeedbackFromUserAvailabilityStatus, final PersonalFeedbackSubmission detailedFeedbackFromUser)
   {
      detailedFeedbackSubmissionPanel.reportFeedbackFromUser(detailedFeedbackFromUserAvailabilityStatus, detailedFeedbackFromUser);
   }


   final boolean hasFeedbackBeenUpdated()
   {
      return detailedFeedbackSubmissionPanel.hasFeedbackBeenUpdated();
   }


   final byte getBasicFeedbackRating()
   {
      return detailedFeedbackSubmissionPanel.getBasicFeedbackRating();
   }


   final void setBasicFeedbackRating(final byte basicFeedbackRating)
   {
      detailedFeedbackSubmissionPanel.setBasicFeedbackRating(basicFeedbackRating);
   }


   final boolean isShowingDetailedFeedbackSubmissionPanel()
   {
      return detailedFeedbackSubmissionPanel.getDelegatePanel().isVisible();
   }


   final void showDetailedFeedbackSubmissionPanel()
   {
      handleShowFeedbackFromUserPanel();
   }


   final boolean isShowingDetailedFeedbackSummaryPanel()
   {
      return detailedFeedbackSummaryPanel.getDelegatePanel().isVisible();
   }


   final void showDetailedFeedbackSummaryPanel()
   {
      handleShowFeedbackFromEveryonePanel();
   }


   final boolean needsToLoadDetailedFeedbackSummary()
   {
      return handleNeedsToLoadDetailedFeedbackSummary();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final void requestFetchFeedbackSubmission()
   {
      rootPanel.requestFetchFeedbackSubmission();
   }


   final void requestFetchDetailedFeedbackSummary()
   {
      rootPanel.requestFetchDetailedFeedbackSummary();
   }


   final void showAccountPanel()
   {
      rootPanel.showAccountPanel();
   }


   final void showMessageDialog(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection defaultAction, final boolean actionOnDialogHidden)
   {
      rootPanel.showMessageDialog(messageDialog, defaultAction, actionOnDialogHidden);
   }


   final void submitUserFeedback(final PersonalFeedbackSubmission personalFeedbackSubmission)
   {
      rootPanel.submitUserFeedback(personalFeedbackSubmission);
   }


   final void removeFeedback()
   {
      rootPanel.removeFeedbackSubmission();
   }
}