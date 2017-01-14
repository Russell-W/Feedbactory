
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.client.core.network.DataAvailabilityStatus;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.component.*;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionSelection;
import com.feedbactory.client.ui.pad.PadResources;
import com.feedbactory.client.ui.feedback.FeedbackPanel;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmission;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import java.awt.FlowLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JToggleButton.ToggleButtonModel;
import javax.swing.LayoutStyle.ComponentPlacement;


final class PersonalFeedbackBasicSubmissionPanel
{
   final private PersonalFeedbackRootPanel rootPanel;

   final private JComponent delegatePanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private LockableComponent innerPanel = new LockableComponent(new JPanel(null));
   private JComponent activeLockedComponent;

   final private JPanel basicFeedbackPanel = new JPanel(null);
   final private PersonalFeedbackDetailedSubmissionNotificationPanel detailedSubmissionNotificationPanel = new PersonalFeedbackDetailedSubmissionNotificationPanel(this);

   final private JLabel ratePromptLabel = new JLabel();

   final private ButtonGroup ratingsButtonGroup = new ButtonGroup();
   final private JRadioButton[] ratingButtons = new JRadioButton[11];

   final private JButton submitFeedbackButton = new JButton();
   final private JButton removeFeedbackButton = new JButton();

   private boolean isUserSignedIntoFeedbactoryAccount;

   private PersonalFeedbackPersonProfile activePersonProfile;

   private DataAvailabilityStatus feedbackSubmissionAvailabilityStatus = DataAvailabilityStatus.NotAvailable;
   private PersonalFeedbackSubmission feedbackSubmission = PersonalFeedbackSubmission.EmptyPersonalFeedbackSubmission;


   PersonalFeedbackBasicSubmissionPanel(final PersonalFeedbackRootPanel rootPanel)
   {
      this.rootPanel = rootPanel;

      initialise();
   }


   private void initialise()
   {
      initialiseInnerPanel();

      initialiseDelegatePanel();
   }


   private void initialiseInnerPanel()
   {
      initialiseBasicFeedbackPanel();
      initialiseSignedInFeedbackSubmissionSection();

      initialiseLockedUserFeedbackSection();

      innerPanel.getRootComponent().setOpaque(false);
   }


   private void initialiseBasicFeedbackPanel()
   {
      basicFeedbackPanel.setOpaque(false);

      ratePromptLabel.setFont(UIConstants.RegularFont);
      ratePromptLabel.setText("Rate:");

      initialiseRadioButtons();

      initialiseFeedbackControlButtons();

      final GroupLayout panelLayout = new GroupLayout(basicFeedbackPanel);
      basicFeedbackPanel.setLayout(panelLayout);

      final SequentialGroup horizontalRatingGroup = panelLayout.createSequentialGroup();
      final ParallelGroup verticalRatingGroup = panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER);

      horizontalRatingGroup.addComponent(ratePromptLabel)
         .addGap(10);

      verticalRatingGroup.addComponent(ratePromptLabel);

      for (int ratingButtonIndex = 0; ratingButtonIndex < 10; ratingButtonIndex ++)
      {
         horizontalRatingGroup.addComponent(ratingButtons[ratingButtonIndex]);
         horizontalRatingGroup.addPreferredGap(ComponentPlacement.RELATED);

         verticalRatingGroup.addComponent(ratingButtons[ratingButtonIndex]);
      }

      // Adding a component gap when there's no following component can mess up container gaps.
      horizontalRatingGroup.addComponent(ratingButtons[10]);
      verticalRatingGroup.addComponent(ratingButtons[10]);

      panelLayout.setHorizontalGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
         .addGroup(horizontalRatingGroup)
         .addGroup(panelLayout.createSequentialGroup()
            .addComponent(submitFeedbackButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
            .addPreferredGap(ComponentPlacement.UNRELATED)
            .addComponent(removeFeedbackButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
         )
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addGroup(verticalRatingGroup)
         .addGap(20)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(submitFeedbackButton)
            .addComponent(removeFeedbackButton)
         )
      );
   }


   private void initialiseRadioButtons()
   {
      final ActionListener actionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleFeedbackRadioButtonClicked();
         }
      };

      for (int ratingButtonIndex = 0; ratingButtonIndex <= 10; ratingButtonIndex ++)
      {
         ratingButtons[ratingButtonIndex] = new JRadioButton();

         ratingButtons[ratingButtonIndex].setModel(new BasicFeedbackRadioButtonModel((byte) (ratingButtonIndex * 10)));

         ratingButtons[ratingButtonIndex].setText(Integer.toString(ratingButtonIndex));
         ratingButtons[ratingButtonIndex].setFont(UIConstants.RegularFont);
         ratingButtons[ratingButtonIndex].setHorizontalTextPosition(SwingConstants.CENTER);
         ratingButtons[ratingButtonIndex].setVerticalTextPosition(SwingConstants.BOTTOM);

         ratingButtons[ratingButtonIndex].addActionListener(actionListener);

         ratingsButtonGroup.add(ratingButtons[ratingButtonIndex]);
      }
   }


   private void initialiseFeedbackControlButtons()
   {
      submitFeedbackButton.setFont(UIConstants.RegularFont);
      submitFeedbackButton.setText("Submit Feedback");

      submitFeedbackButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleSubmitButtonClicked();
         }
      });

      removeFeedbackButton.setFont(UIConstants.RegularFont);
      removeFeedbackButton.setText("Remove Feedback");

      removeFeedbackButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleRemoveFeedbackButtonClicked();
         }
      });
   }


   private void initialiseSignedInFeedbackSubmissionSection()
   {
      innerPanel.getUnlockedComponent().setOpaque(false);

      detailedSubmissionNotificationPanel.getDelegate().setVisible(false);

      final GroupLayout panelLayout = new GroupLayout(innerPanel.getUnlockedComponent());
      innerPanel.getUnlockedComponent().setLayout(panelLayout);

      // Ensure that the panel won't be resized
      panelLayout.setHonorsVisibility(false);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(basicFeedbackPanel)
            .addComponent(detailedSubmissionNotificationPanel.getDelegate())
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.TRAILING)
            .addComponent(basicFeedbackPanel)
            .addComponent(detailedSubmissionNotificationPanel.getDelegate())
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );
   }


   private void initialiseLockedUserFeedbackSection()
   {
      final GroupLayout panelLayout = new GroupLayout(innerPanel.getLockedComponent());
      innerPanel.getLockedComponent().setLayout(panelLayout);

      activeLockedComponent = new JPanel(null);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addGap(0, 0, Integer.MAX_VALUE)
         .addComponent(activeLockedComponent, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(0, 0, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addGap(0, 0, Integer.MAX_VALUE)
         .addComponent(activeLockedComponent, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(0, 0, Integer.MAX_VALUE)
      );
   }


   private void initialiseDelegatePanel()
   {
      delegatePanel.setBackground(UIConstants.ContentPanelColour);
      delegatePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
      delegatePanel.add(innerPanel.getRootComponent());
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class BasicFeedbackRadioButtonModel extends ToggleButtonModel
   {
      final private byte buttonSelectionValue;


      private BasicFeedbackRadioButtonModel(final byte buttonSelectionValue)
      {
         this.buttonSelectionValue = buttonSelectionValue;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private MessageDialog createSignInPromptDialog()
   {
      final JButton signInButton = new JButton("Sign in to submit feedback");
      signInButton.setFont(UIConstants.RegularFont);
      signInButton.setIconTextGap(8);

      try
      {
         final BufferedImage buttonImage = ImageIO.read(FeedbackPanel.class.getResourceAsStream(PadResources.AccountButtonImagePath));
         signInButton.setIcon(new ImageIcon(UIUtilities.getScaledImage(buttonImage, UIConstants.PreferredIconWidth, UIConstants.PreferredIconHeight,
                                                                       RenderingHints.VALUE_INTERPOLATION_BICUBIC)));
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }

      signInButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            rootPanel.showAccountPanel();
         }
      });

      final MessageDialog.Builder builder = new MessageDialog.Builder();
      builder.setInputComponents(new JComponent[] {signInButton});

      return new MessageDialog(builder);
   }


   private MessageDialog createFeedbackRetrievalErrorDialog()
   {
      final JButton retryButton = new JButton();
      retryButton.setFont(UIConstants.RegularFont);
      retryButton.setText("Retry");
      retryButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            requestFetchFeedback();
         }
      });

      final String[] message = new String[] {"An error occurred while trying to load the feedback."};
      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Error, message);
      builder.setInputComponents(new JComponent[] {retryButton});

      return new MessageDialog(builder);
   }


   private MessageDialog createOverallFeedbackRatingRequiredDialog()
   {
      final String[] message = new String[] {"Please select an overall feedback rating."};
      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Error, message, MessageDialog.PresetOptionConfiguration.OK);
      return new MessageDialog(builder);
   }


   private MessageDialog createFeedbackSubmissionConfirmationDialog(final String displayName)
   {
      final String truncatedDisplayName = UIUtilities.getEllipsisTruncatedString(displayName, 60);
      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Question, new String[] {"Change your feedback for", truncatedDisplayName + '?'}, MessageDialog.PresetOptionConfiguration.OKCancel);

      final MessageDialog dialog = new MessageDialog(builder);

      dialog.addActionListener(new MessageDialog.ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            if (optionSelection == PresetOptionSelection.OK)
               submitFeedback();
         }
      });

      return dialog;
   }


   private MessageDialog createFeedbackRemovalConfirmationDialog(final String displayName)
   {
      final String truncatedDisplayName = UIUtilities.getEllipsisTruncatedString(displayName, 60);
      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Question, new String[] {"Remove your feedback for", truncatedDisplayName + '?'}, MessageDialog.PresetOptionConfiguration.OKCancel);

      final MessageDialog dialog = new MessageDialog(builder);

      dialog.addActionListener(new MessageDialog.ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            if (optionSelection == PresetOptionSelection.OK)
               removeFeedback();
         }
      });

      return dialog;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleFeedbackRadioButtonClicked()
   {
      refreshSubmitButton();
   }


   private void handleSubmitButtonClicked()
   {
      if (getBasicFeedbackRating() == PersonalFeedbackSubmission.NoRatingValue)
         rootPanel.showMessageDialog(createOverallFeedbackRatingRequiredDialog(), PresetOptionSelection.OK, true);
      else if (hasExistingFeedbackSubmission())
         rootPanel.showMessageDialog(createFeedbackSubmissionConfirmationDialog(activePersonProfile.getFullName()), PresetOptionSelection.OK, true);
      else
         submitFeedback();
   }


   private void handleRemoveFeedbackButtonClicked()
   {
      promptForFeedbackSubmissionRemoval();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void showLockedComponent(final JComponent lockedComponentToActivate)
   {
      setActiveLockedComponent(lockedComponentToActivate);
      innerPanel.setLocked(true);
   }


   private void setActiveLockedComponent(final JComponent lockedComponentToActivate)
   {
      final GroupLayout lockedPanelLayout = (GroupLayout) innerPanel.getLockedComponent().getLayout();
      lockedPanelLayout.replace(activeLockedComponent, lockedComponentToActivate);

      activeLockedComponent = lockedComponentToActivate;
   }


   private void hideLockedPanel()
   {
      innerPanel.setLocked(false);
      setActiveLockedComponent(new JPanel(null));
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private boolean isErrorRetrievingFeedbackSubmission()
   {
      return (feedbackSubmissionAvailabilityStatus == DataAvailabilityStatus.Failed);
   }


   private boolean hasExistingFeedbackSubmission()
   {
      return ((feedbackSubmissionAvailabilityStatus == DataAvailabilityStatus.Available) &&
              (feedbackSubmission.overallFeedbackRating != PersonalFeedbackSubmission.NoRatingValue));
   }


   private boolean hasDetailedExistingFeedbackSubmission()
   {
      return ((feedbackSubmissionAvailabilityStatus == DataAvailabilityStatus.Available) &&
               feedbackSubmission.hasAtLeastOneCriteriaRating());

   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void updateControlValuesForLoadedSubmission()
   {
      if (hasExistingFeedbackSubmission())
      {
         if (feedbackSubmission.isOverallRatingCalculatedFromCriteriaFeedback)
            resetBasicFeedbackRating(PersonalFeedbackSubmission.NoRatingValue);
         else
            resetBasicFeedbackRating(feedbackSubmission.overallFeedbackRating);

         submitFeedbackButton.setEnabled(false);
         removeFeedbackButton.setEnabled(true);
      }
      else
      {
         resetBasicFeedbackRating(PersonalFeedbackSubmission.NoRatingValue);

         submitFeedbackButton.setEnabled(true);
         removeFeedbackButton.setEnabled(false);
      }
   }



   private void updateBasicFeedbackPanelVisibilityForLoadedSubmission()
   {
      if (hasDetailedExistingFeedbackSubmission())
      {
         detailedSubmissionNotificationPanel.setOverallFeedbackRating(feedbackSubmission.overallFeedbackRating);
         setBasicFeedbackSubmissionPanelAvailable(false);
      }
      else
         setBasicFeedbackSubmissionPanelAvailable(true);
   }


   private void setBasicFeedbackSubmissionPanelAvailable(final boolean isAvailable)
   {
      basicFeedbackPanel.setVisible(isAvailable);
      detailedSubmissionNotificationPanel.getDelegate().setVisible(! isAvailable);
   }


   private void resetBasicFeedbackRating(final byte basicFeedbackRating)
   {
      if (basicFeedbackRating != PersonalFeedbackSubmission.NoRatingValue)
         ratingButtons[basicFeedbackRating / 10].setSelected(true);
      else
         ratingsButtonGroup.clearSelection();
   }


   private void refreshSubmitButton()
   {
      submitFeedbackButton.setEnabled((! hasExistingFeedbackSubmission()) || hasFeedbackBeenUpdated());
   }


   private void updateLockedPanel()
   {
      if (isErrorRetrievingFeedbackSubmission())
      {
         /* The basic panel happens to be slightly too small for the error retrieval dialog, and when displayed the window will creep
          * a little into its shadow region. I considered making this panel slightly larger (eg. more spacing under the Submit button)
          * but that would make the vertical spacing between button and container a little inconsistent with the rest of the panels.
          * Not to mention it seems wasteful to bodge in extra space just for a very rare use case. If I really want I can do away
          * with displaying the retrieval error dialog here and instead rely on the root panel's error dialog (which has more space
          * to work with) to display the error notice.. but that would also mean locking all of the personal feedback UI
          * even if some portions of it have successfully loaded feedback.
          */
         showLockedComponent(createFeedbackRetrievalErrorDialog().getDelegate());
      }
      else if (! isUserSignedIntoFeedbactoryAccount)
         showLockedComponent(createSignInPromptDialog().getDelegate());
      else
         hideLockedPanel();
   }


   private void submitFeedback()
   {
      rootPanel.submitUserFeedback(new PersonalFeedbackSubmission(getBasicFeedbackRating()));
   }


   private void removeFeedback()
   {
      rootPanel.removeFeedbackSubmission();
   }


   private void requestFetchFeedback()
   {
      rootPanel.requestFetchFeedbackSubmission();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private byte handleGetBasicFeedbackRating()
   {
      final BasicFeedbackRadioButtonModel selectedButtonModel = (BasicFeedbackRadioButtonModel) ratingsButtonGroup.getSelection();

      if (selectedButtonModel != null)
         return selectedButtonModel.buttonSelectionValue;
      else
         return PersonalFeedbackSubmission.NoRatingValue;
   }


   private void handleSetBasicFeedbackRating(final byte basicFeedbackRating)
   {
      resetBasicFeedbackRating(basicFeedbackRating);

      refreshSubmitButton();
   }


   private void handlePanelUpdateForBasicFeedbackFromUser()
   {
      updateControlValuesForLoadedSubmission();

      updateBasicFeedbackPanelVisibilityForLoadedSubmission();

      updateLockedPanel();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleFeedbactoryUserAccountStatusUpdated(final boolean isUserSignedIntoFeedbactoryAccount)
   {
      this.isUserSignedIntoFeedbactoryAccount = isUserSignedIntoFeedbactoryAccount;

      feedbackSubmission = PersonalFeedbackSubmission.EmptyPersonalFeedbackSubmission;
      feedbackSubmissionAvailabilityStatus = DataAvailabilityStatus.NotAvailable;

      handlePanelUpdateForBasicFeedbackFromUser();
   }


   private boolean handleHasFeedbackBeenUpdated()
   {
      if (! isUserSignedIntoFeedbactoryAccount)
         return false;
      else if (hasExistingFeedbackSubmission())
         return (! ratingButtons[feedbackSubmission.overallFeedbackRating / 10].isSelected());
      else
         return (ratingsButtonGroup.getSelection() != null);
   }


   private void handlePromptForFeedbackSubmissionRemoval()
   {
      rootPanel.showMessageDialog(createFeedbackRemovalConfirmationDialog(activePersonProfile.getFullName()), PresetOptionSelection.Cancel, true);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final JComponent getDelegatePanel()
   {
      return delegatePanel;
   }


   final void reportFeedbactoryUserAccountStatus(final boolean isUserSignedIntoFeedbactoryAccount)
   {
      handleFeedbactoryUserAccountStatusUpdated(isUserSignedIntoFeedbactoryAccount);
   }


   final void showPersonProfile(final PersonalFeedbackPersonProfile personProfile,
                            final DataAvailabilityStatus existingFeedbackSubmissionAvailabilityStatus,
                            final PersonalFeedbackSubmission existingFeedbackSubmission)
   {
      this.activePersonProfile = personProfile;

      feedbackFromUserUpdated(existingFeedbackSubmissionAvailabilityStatus, existingFeedbackSubmission);
   }


   final void profileDetailsUpdated(final PersonalFeedbackPersonProfile updatedPersonProfile)
   {
      this.activePersonProfile = updatedPersonProfile;
   }


   final void feedbackFromUserUpdated(final DataAvailabilityStatus existingFeedbackSubmissionAvailabilityStatus, final PersonalFeedbackSubmission existingFeedbackSubmission)
   {
      this.feedbackSubmissionAvailabilityStatus = existingFeedbackSubmissionAvailabilityStatus;
      this.feedbackSubmission = existingFeedbackSubmission;

      handlePanelUpdateForBasicFeedbackFromUser();
   }


   final boolean hasFeedbackBeenUpdated()
   {
      return handleHasFeedbackBeenUpdated();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final byte getBasicFeedbackRating()
   {
      return handleGetBasicFeedbackRating();
   }


   final void setBasicFeedbackRating(final byte basicFeedbackRating)
   {
      handleSetBasicFeedbackRating(basicFeedbackRating);
   }


   final void viewDetailedFeedbackSubmission()
   {
      rootPanel.showDetailedFeedbackSubmission();
   }


   final void promptForFeedbackSubmissionRemoval()
   {
      handlePromptForFeedbackSubmissionRemoval();
   }
}