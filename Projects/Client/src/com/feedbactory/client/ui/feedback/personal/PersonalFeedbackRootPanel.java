
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.client.core.feedback.personal.ManagedPersonalFeedbackPerson;
import com.feedbactory.client.core.network.DataAvailabilityStatus;
import com.feedbactory.client.core.useraccount.FeedbactoryUserAccount;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.component.BouncingTextLabel;
import com.feedbactory.client.ui.component.ImageLoader.ImageLoadRequester;
import com.feedbactory.client.ui.component.HyperlinkLabel;
import com.feedbactory.client.ui.component.LockableComponent;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.SmileyProgressBar;
import com.feedbactory.client.ui.feedback.FeedbackMenuItem;
import com.feedbactory.client.ui.feedback.FeedbackPanel;
import com.feedbactory.client.ui.feedback.FeedbackPanelUIComponent;
import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.feedback.FeedbackItem;
import com.feedbactory.shared.feedback.personal.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.LayoutStyle.ComponentPlacement;


final class PersonalFeedbackRootPanel implements FeedbackPanelUIComponent, ImageLoadRequester
{
   static final private int MaximumProfileThumbnailDimensions = 100;
   static final private int MaximumProfilePhotoLoadAttempts = 3;
   static final private long ProfilePhotoLoadRetryDelayMilliseconds = 1000L;

   final private PersonalFeedbackUIManager uiManager;

   final private FeedbackPanel feedbackPanel;

   final private JPanel delegatePanel = new ShadedPanel();

   final private JButton profilePhotoButton = new JButton();
   private int profilePhotoLoadAttempts;
   private Future<?> profilePhotoLoadRetryTask;

   final private HyperlinkLabel moreCriteriaTypeFeedbackLabel = new HyperlinkLabel();

   final private JLabel averageRatingLabel = new JLabel();
   final private BouncingTextLabel averageRatingResultLabel = new BouncingTextLabel();
   final private BouncingTextLabel numberOfRatingsLabel = new BouncingTextLabel();
   final private BouncingTextLabel lowRatingResultLabel = new BouncingTextLabel();

   final private LockableComponent childFeedbackPanelContainer = new LockableComponent(new JPanel(null));
   final private PersonalFeedbackBasicSubmissionPanel basicPanel = new PersonalFeedbackBasicSubmissionPanel(this);
   final private PersonalFeedbackDetailedPanel detailedPanel = new PersonalFeedbackDetailedPanel(this);
   private JComponent activeFeedbackPanel;
   private JComponent activeLockedFeedbackComponent;

   final private JButton moreOrLessButton = new JButton();

   // A local copy of a person node from the PersonalFeedbackManager; locking should not be applied to this object, which must only be accessed on the Swing EDT.
   private ManagedPersonalFeedbackPerson activePerson;

   private FeedbactoryUserAccount signedInUserAccount;


   PersonalFeedbackRootPanel(final PersonalFeedbackUIManager uiManager, final FeedbackPanel feedbackPanel)
   {
      this.uiManager = uiManager;
      this.feedbackPanel = feedbackPanel;

      initialise();
   }
   
   
   private void initialise()
   {
      initialiseAverageRatingSection();

      initialiseChildFeedbackPanelContainer();

      initialiseDelegatePanel();
   }


   private void initialiseAverageRatingSection()
   {
      profilePhotoButton.setFocusable(false);
      profilePhotoButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleProfilePhotoButtonClicked();
         }
      });

      moreCriteriaTypeFeedbackLabel.setFont(UIConstants.SmallFont);
      moreCriteriaTypeFeedbackLabel.addMouseListener(new MouseAdapter()
      {
         @Override
         final public void mouseClicked(final MouseEvent mouseEvent)
         {
            handleMoreCriteriaTypeFeedbackLabelMouseClicked();
         }
      });

      averageRatingLabel.setFont(UIConstants.RegularFont);

      averageRatingResultLabel.setFont(UIConstants.LargeBoldFont);
      numberOfRatingsLabel.setFont(UIConstants.RegularFont);

      lowRatingResultLabel.setFont(UIConstants.RegularBoldFont);
   }


   private void initialiseChildFeedbackPanelContainer()
   {
      childFeedbackPanelContainer.getRootComponent().setOpaque(false);

      initialisePanelFooter();
      initialiseUnlockedChildFeedbackPanelContainer();
      initialiseLockedChildFeedbackPanelContainer();
      initialiseChildFeedbackPanelContainerLockListener();
   }


   private void initialisePanelFooter()
   {
      moreOrLessButton.setFont(UIConstants.RegularFont);
      moreOrLessButton.setText("More >>");

      moreOrLessButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleMoreOrLessButtonClicked();
         }
      });
   }


   private void initialiseUnlockedChildFeedbackPanelContainer()
   {
      childFeedbackPanelContainer.getUnlockedComponent().setOpaque(false);

      final GroupLayout panelLayout = new GroupLayout(childFeedbackPanelContainer.getUnlockedComponent());
      childFeedbackPanelContainer.getUnlockedComponent().setLayout(panelLayout);

      /* The reason for the peculiar placement of edge spacing around the darker coloured content panels (ie. some here, some in the nested panels) is to ensure that the
       * blur effect will not be 'squared off' at the extremities. So unfortunately we need to introduce gaps at the very edges of each darker panel. We happen to know that
       * our nested panels provide their own edge gaps, so for those we don't add to them here.
       */
      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(basicPanel.getDelegatePanel())
            .addComponent(moreOrLessButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(basicPanel.getDelegatePanel())
         .addPreferredGap(ComponentPlacement.RELATED)
         .addComponent(moreOrLessButton)
      );

      activeFeedbackPanel = basicPanel.getDelegatePanel();
   }


   private void initialiseLockedChildFeedbackPanelContainer()
   {
      activeLockedFeedbackComponent = new JPanel(null);

      final GroupLayout panelLayout = new GroupLayout(childFeedbackPanelContainer.getLockedComponent());
      childFeedbackPanelContainer.getLockedComponent().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addGap(0, 0, Integer.MAX_VALUE)
         .addComponent(activeLockedFeedbackComponent, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(0, 0, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addGap(0, 0, Integer.MAX_VALUE)
         .addComponent(activeLockedFeedbackComponent, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(0, 0, Integer.MAX_VALUE)
      );
   }


   private void initialiseChildFeedbackPanelContainerLockListener()
   {
      childFeedbackPanelContainer.addLockableComponentListener(new LockableComponent.LockableComponentListener()
      {
         @Override
         final public void componentLockedStateChanged(final LockableComponent component)
         {
            handleChildFeedbackPanelContainerLockStateChanged();
         }
      });
   }


   private void initialiseDelegatePanel()
   {
      delegatePanel.setBackground(UIConstants.LighterPanelColour);

      final GroupLayout panelLayout = new GroupLayout(delegatePanel);
      delegatePanel.setLayout(panelLayout);

      /* Be careful that the horizontal space taken up here doesn't creep beyond the space provided by the window. If it does, the window
       * shadow and top-right control buttons will be the first to go..
       */
      panelLayout.setHorizontalGroup(panelLayout.createParallelGroup()
         .addGroup(panelLayout.createSequentialGroup()
            .addGap(15)
            .addComponent(profilePhotoButton, GroupLayout.PREFERRED_SIZE, 110, GroupLayout.PREFERRED_SIZE)
            .addGap(10)
            .addGroup(panelLayout.createParallelGroup()
               .addGroup(panelLayout.createSequentialGroup()
                  .addComponent(averageRatingLabel)
                  .addPreferredGap(ComponentPlacement.UNRELATED)
                  .addGroup(panelLayout.createParallelGroup()
                     .addComponent(averageRatingResultLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                     .addComponent(numberOfRatingsLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                     .addComponent(lowRatingResultLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                  )
               )
               .addComponent(moreCriteriaTypeFeedbackLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            )
            .addGap(GroupLayout.PREFERRED_SIZE, 15, Integer.MAX_VALUE)
         )
         .addComponent(childFeedbackPanelContainer.getRootComponent())
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(profilePhotoButton, GroupLayout.PREFERRED_SIZE, 110, GroupLayout.PREFERRED_SIZE)
            .addGroup(panelLayout.createSequentialGroup()
               .addGap(42)
               .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
                  .addComponent(averageRatingLabel)
                  .addGroup(panelLayout.createParallelGroup()
                     .addComponent(averageRatingResultLabel)
                     .addComponent(lowRatingResultLabel)
                  )
               )
            )
            .addGroup(panelLayout.createSequentialGroup()
               // Added separately to the above group, as this allows slightly closer placement to the rating label.
               .addGap(65)
               .addComponent(numberOfRatingsLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            )
            .addGroup(panelLayout.createSequentialGroup()
               .addGap(92)
               .addComponent(moreCriteriaTypeFeedbackLabel)
            )
         )
         .addPreferredGap(ComponentPlacement.RELATED)
         .addComponent(childFeedbackPanelContainer.getRootComponent())
         .addContainerGap()
      );
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class ShadedPanel extends JPanel
   {
      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         final Rectangle2D gradientPaintExtent = new Rectangle2D.Float(-0.25f * getWidth(), 0f, 1.5f * getWidth(), childFeedbackPanelContainer.getRootComponent().getY());
         final Color[] colours = new Color[] {UIUtilities.shiftColourBrightness(getBackground(), 0.07f), getBackground()};
         final float[] colourRanges = new float[] {0.4f, 1f};
         final RadialGradientPaint radialGradientPaint = new RadialGradientPaint(gradientPaintExtent, colourRanges, colours, MultipleGradientPaint.CycleMethod.NO_CYCLE);

         final Graphics2D graphics2D = (Graphics2D) graphics;
         graphics2D.setPaint(radialGradientPaint);
         graphics2D.fillRect(0, 0, getWidth(), getHeight());
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private void handleProfilePhotoButtonClicked()
   {
      uiManager.showItemInBrowser(activePerson.getPersonProfile());
   }


   private void handleMoreCriteriaTypeFeedbackLabelMouseClicked()
   {
      final FeedbackMenuItem targetMenuItem = new FeedbackMenuItem(FeedbackCategory.Personal, null, Byte.toString(activePerson.getPersonProfile().person.getCriteriaType().value));
      uiManager.invokeMenuItem(targetMenuItem);
   }


   private void handleMoreOrLessButtonClicked()
   {
      if (isShowingBasicFeedbackPanel())
      {
         // Sync the overall feedback rating that the user may have entered on the detailed panel.
         if (signedInUserAccount != null)
            detailedPanel.setBasicFeedbackRating(basicPanel.getBasicFeedbackRating());

         switchToDetailedPanel();
      }
      else
      {
         // Sync the overall feedback rating that the user may have entered on the basic panel.
         if (signedInUserAccount != null)
            basicPanel.setBasicFeedbackRating(detailedPanel.getBasicFeedbackRating());

         switchToBasicPanel();
      }
   }


   private void handleChildFeedbackPanelContainerLockStateChanged()
   {
      if (! childFeedbackPanelContainer.isLocked())
         feedbackPanel.requestRepack();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void showLockedFeedbackComponent(final JComponent lockedComponentToActivate)
   {
      setActiveLockedComponent(lockedComponentToActivate);
      childFeedbackPanelContainer.setLocked(true);
   }


   private void setActiveLockedComponent(final JComponent lockedComponentToActivate)
   {
      final GroupLayout lockedPanelLayout = (GroupLayout) childFeedbackPanelContainer.getLockedComponent().getLayout();
      lockedPanelLayout.replace(activeLockedFeedbackComponent, lockedComponentToActivate);

      activeLockedFeedbackComponent = lockedComponentToActivate;
   }


   private void hideLockedFeedbackPanel()
   {
      childFeedbackPanelContainer.setLocked(false);
      setActiveLockedComponent(new JPanel(null));
   }


   private SmileyProgressBar getProgressBar()
   {
      final SmileyProgressBar progressBar = new SmileyProgressBar();

      progressBar.setBackground(UIConstants.ProgressBarShadingColour);
      progressBar.setPreferredSize(new Dimension(UIConstants.ProgressBarWidth, UIConstants.ProgressBarHeight));

      progressBar.setIndeterminate(true);

      return progressBar;
   }


   private MessageDialog getFeedbackRetrievalErrorDialog()
   {
      final JButton retryButton = new JButton();
      retryButton.setFont(UIConstants.RegularFont);
      retryButton.setText("Retry");
      retryButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            refreshItem();
         }
      });

      final String[] message = new String[] {"An error occurred while trying to load the feedback."};
      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Error, message);
      builder.setInputComponents(new JComponent[] {retryButton});

      return new MessageDialog(builder);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private boolean isShowingBasicFeedbackPanel()
   {
      return (activeFeedbackPanel == basicPanel.getDelegatePanel());
   }


   private boolean isShowingDetailedFeedbackPanel()
   {
      return (activeFeedbackPanel == detailedPanel.getDelegate());
   }


   private void switchToBasicPanel()
   {
      /* I don't necessarily want to sync the overall feedback rating on the detailed panel with the basic panel,
       * since this action may be triggered programmatically rather than the user wanting to switch views
       * (which is when I do want to sync the feedback between the two panels). At this point the user may not
       * have updated any feedback controls at all and the form may have just loaded existing feedback submission data;
       * in this case it would be a mistake to overwrite that with whatever overall feedback is selected on the other form.
       */
      if (! isShowingBasicFeedbackPanel())
      {
         switchToPanel(basicPanel.getDelegatePanel());
         moreOrLessButton.setText("More   >>");
      }
   }


   private void switchToDetailedPanel()
   {
      // See note above, regarding syncing basic & detailed feedback.
      if (! isShowingDetailedFeedbackPanel())
      {
         switchToPanel(detailedPanel.getDelegate());
         moreOrLessButton.setText("<<   Less");
      }

      checkRetrieveDetailedFeedbackSummary();
   }


   private void switchToPanel(final JComponent panelToActivate)
   {
      final GroupLayout delegatePanelLayout = (GroupLayout) childFeedbackPanelContainer.getUnlockedComponent().getLayout();
      delegatePanelLayout.replace(activeFeedbackPanel, panelToActivate);
      activeFeedbackPanel = panelToActivate;

      requestRepack();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private boolean isRetrievingBasicFeedbackSummary()
   {
      return (activePerson.getBasicFeedbackSummaryAvailabilityStatus() == DataAvailabilityStatus.Retrieving);
   }


   private void retrieveBasicFeedbackSummary()
   {
      uiManager.requestGetPersonBasicFeedbackSummary(activePerson.getPersonProfile().person);
   }


   private boolean isErrorRetrievingBasicFeedbackSummary()
   {
      return (activePerson.getBasicFeedbackSummaryAvailabilityStatus() == DataAvailabilityStatus.Failed);
   }


   private boolean isRetrievingDetailedFeedbackSummary()
   {
      return (activePerson.getDetailedFeedbackSummaryAvailabilityStatus() == DataAvailabilityStatus.Retrieving);
   }


   private void retrieveDetailedFeedbackSummary()
   {
      uiManager.requestGetPersonDetailedFeedbackSummary(activePerson.getPersonProfile().person);
   }


   private boolean isRetrievingFeedbackSubmission()
   {
      return (activePerson.getFeedbackSubmissionAvailabilityStatus() == DataAvailabilityStatus.Retrieving);
   }


   private void retrieveFeedbackSubmission()
   {
      uiManager.requestGetPersonFeedbackSubmission(activePerson.getPersonProfile().person);
   }


   private void passiveRetrieveAllFeedbackData()
   {
      checkRetrieveBasicFeedbackSummary();
      checkRetrieveDetailedFeedbackSummary();
      checkRetrieveFeedbackSubmission();
   }


   private void forcefulRetrieveAllFeedbackData()
   {
      /* This method will be invoked in response to the UI or user trying to force the refresh or retry load of feedback, whether already successful or failed.
       * In the latter case a request to retrieve the feedback submission from the user may need to be placed. Of course if the feedback submission has been
       * successfully retrieved, it should not be tampered with since the user may be midway through editing it.
       * Note the difference between this and passiveRetrieveAllFeedbackDataIfNeeded(), which will only load the feedback data if it hasn't been cached or
       * if the cached copy indicates that there was a load failure.
       */
      retrieveBasicFeedbackSummary();

      if (isShowingDetailedFeedbackPanel())
         retrieveDetailedFeedbackSummary();

      checkRetrieveFeedbackSubmission();
   }


   private void checkRetrieveBasicFeedbackSummary()
   {
      if ((activePerson.getBasicFeedbackSummaryAvailabilityStatus() == DataAvailabilityStatus.NotAvailable) ||
          (activePerson.getBasicFeedbackSummaryAvailabilityStatus() == DataAvailabilityStatus.Failed))
         retrieveBasicFeedbackSummary();
   }


   private void checkRetrieveDetailedFeedbackSummary()
   {
      if (isShowingDetailedFeedbackPanel() && detailedPanel.needsToLoadDetailedFeedbackSummary())
         retrieveDetailedFeedbackSummary();
   }


   private void checkRetrieveFeedbackSubmission()
   {
      if ((signedInUserAccount != null) &&
          ((activePerson.getFeedbackSubmissionAvailabilityStatus() == DataAvailabilityStatus.NotAvailable) ||
           (activePerson.getFeedbackSubmissionAvailabilityStatus() == DataAvailabilityStatus.Failed)))
         retrieveFeedbackSubmission();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handlePanelUpdateForChangedPerson()
   {
      updateUserProfileDisplay();
      handlePanelUpdateForChangedBasicFeedbackSummary(true, true);
   }


   private void handlePanelUpdateForChangedBasicFeedbackSummary(final boolean feedbackSummaryUpdated, final boolean numberOfRatingsUpdated)
   {
      updateBasicFeedbackSummary(feedbackSummaryUpdated, numberOfRatingsUpdated);
      updateForChangedDataAvailabilityStatus();
   }


   private void updateUserProfileDisplay()
   {
      profilePhotoButton.setIcon(null);

      profilePhotoLoadAttempts = 0;
      finishProfilePhotoLoadRetryTask();
      loadProfilePhotoImage();

      final String criteriaTypeName = activePerson.getPersonProfile().person.getCriteriaType().displayName.toLowerCase();
      moreCriteriaTypeFeedbackLabel.setHyperlink("More " + criteriaTypeName + " feedback", null);
   }


   private void finishProfilePhotoLoadRetryTask()
   {
      if (profilePhotoLoadRetryTask != null)
      {
         profilePhotoLoadRetryTask.cancel(false);
         profilePhotoLoadRetryTask = null;
      }
   }


   private void loadProfilePhotoImage()
   {
      final String resolvedPhotoURL = activePerson.getPersonProfile().getThumbnailImageURL();

      if (resolvedPhotoURL != null)
      {
         /* The ImageLoader will merge duplicate requests for URLs into the one network retrieval task. Furthermore, it will merge duplicate callbacks (per URL request).
          * This means that there is no worry about a backlog of requests and callbacks incoming if for example the user re-selects the same item in quick
          * succession before its profile photo has loaded. This also takes care of the same problem with the automatic retry when a profile photo load fails.
          */
         final BufferedImage profilePhoto = feedbackPanel.getImageLoader().loadImage(resolvedPhotoURL, MaximumProfileThumbnailDimensions, MaximumProfileThumbnailDimensions, this);
         if (profilePhoto != null)
            processProfilePhotoImage(profilePhoto);
      }
   }


   private void processProfilePhotoImage(final BufferedImage image)
   {
      final BufferedImage thumbnailScaledImage = UIUtilities.getSquareCroppedScaledImage(image, MaximumProfileThumbnailDimensions, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

      final Graphics graphics = thumbnailScaledImage.createGraphics();
      graphics.setColor(Color.lightGray);
      graphics.drawRect(0, 0, thumbnailScaledImage.getWidth() - 1, thumbnailScaledImage.getHeight() - 1);
      graphics.dispose();

      profilePhotoButton.setIcon(new ImageIcon(thumbnailScaledImage));
   }


   @Override
   final public void reportImageLoaded(final String imageURL, final BufferedImage image, final Throwable exception)
   {
      // Is the loaded image for the profile still active?
      if (activePerson.getPersonProfile().getThumbnailImageURL().equals(imageURL))
      {
         if (image != null)
            processProfilePhotoImage(image);
         else
         {
            /* Profile photo loads will occasionally fail for slower websites, leaving the profile icon blank.
             * The user could kick the UI into trying to reload the photo by opening and flicking through the dropdown item list,
             * but it's much nicer to offer an automatic reload.
             */
            profilePhotoLoadAttempts ++;
            if (profilePhotoLoadAttempts < MaximumProfilePhotoLoadAttempts)
               retryLoadProfilePhotoImage();
         }
      }
   }


   private void retryLoadProfilePhotoImage()
   {
      final Runnable swingRunnable = new Runnable()
      {
         @Override
         final public void run()
         {
            /* During the delay period the user may have switched to a different profile, in which case this request should not go ahead.
             * Another unlikely possibility is that during the same time, the user may have switched to and back to the original profile.
             * In either case this task will probably have been cancelled before executing via the call to finishProfilePhotoLoadRetryTask
             * in updateUserProfileDisplay(). There is still a chance that it will have been submitted to the Swing event queue however,
             * so perform a quick check here to ensure that the process is still valid for the active profile.
             */
            if (profilePhotoLoadRetryTask != null)
            {
               loadProfilePhotoImage();
               profilePhotoLoadRetryTask = null;
            }
         }
      };

      final Runnable executorRunnable = new Runnable()
      {
         @Override
         final public void run()
         {
            SwingUtilities.invokeLater(swingRunnable);
         }
      };

      final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
      profilePhotoLoadRetryTask = executor.schedule(executorRunnable, ProfilePhotoLoadRetryDelayMilliseconds, TimeUnit.MILLISECONDS);
      executor.shutdown();
   }


   private void updateBasicFeedbackSummary(final boolean feedbackSummaryUpdated, final boolean numberOfRatingsUpdated)
   {
      if (activePerson.getBasicFeedbackSummaryAvailabilityStatus() == DataAvailabilityStatus.Available)
      {
         if (activePerson.getBasicFeedbackSummary().numberOfRatings > 0)
         {
            averageRatingLabel.setText(uiManager.getFeedbackDataFormatter().getFeedbackSummaryLabel());

            if (feedbackSummaryUpdated)
            {
               final NumberFormat numberFormat = NumberFormat.getNumberInstance();
               numberFormat.setMinimumFractionDigits(1);

               if (activePerson.getBasicFeedbackSummary().averageRating != PersonalFeedbackBasicSummary.SuppressedLowAverageRating)
               {
                  lowRatingResultLabel.setVisible(false);

                  averageRatingResultLabel.showAnimatedText(numberFormat.format(activePerson.getBasicFeedbackSummary().averageRating / 10f));
                  averageRatingResultLabel.setVisible(true);
               }
               else
               {
                  averageRatingResultLabel.setVisible(false);

                  lowRatingResultLabel.showAnimatedText("Less than " + numberFormat.format(PersonalFeedbackBasicSummary.MinimumVisibleAverageRating / 10f));
                  lowRatingResultLabel.setVisible(true);
               }
            }

            if (numberOfRatingsUpdated)
            {
               final StringBuilder numberOfRatingsStringBuilder = new StringBuilder();
               numberOfRatingsStringBuilder.append(NumberFormat.getIntegerInstance().format(activePerson.getBasicFeedbackSummary().numberOfRatings));
               numberOfRatingsStringBuilder.append(" rating");
               if (activePerson.getBasicFeedbackSummary().numberOfRatings > 1)
                  numberOfRatingsStringBuilder.append('s');

               numberOfRatingsLabel.showAnimatedText(numberOfRatingsStringBuilder.toString());
            }

            numberOfRatingsLabel.setVisible(true);
         }
         else
         {
            averageRatingLabel.setText("Not yet rated");

            averageRatingResultLabel.setVisible(false);
            numberOfRatingsLabel.setVisible(false);
            lowRatingResultLabel.setVisible(false);
         }
      }
      else
      {
         /* Leave this blank display as is, without the "Not yet rated" wording. This section of the UI will be shown when the data is retrieving,
          * and it should be in a neutral state until the data is available.
          */
         averageRatingLabel.setText("");

         averageRatingResultLabel.setVisible(false);
         numberOfRatingsLabel.setVisible(false);
         lowRatingResultLabel.setVisible(false);
      }
   }


   private void updateForChangedDataAvailabilityStatus()
   {
      /* How the display of the various data availability statuses works:
       *
       * - This is the topmost personal feedback display panel, snapped in directly below the master feedback panel which selects which items to display.
       *
       * - All data availability updates between the personal feedback UI manager and the child personal feedback components pass through here.
       *   Most requests to fetch data also originate from this panel, except for some circumstances where a nested panel will know that it's the right
       *   time to place a request to fetch particular data.
       *
       * - Different requests and resulting statuses will result in different updates on this panel and nested panels. For example there are retrieval
       *   statuses which should indicate a progress bar, and failed statuses which should result in an error display.
       *
       * - Submitting feedback results in the entire feedback pad UI becoming locked with a top-level progress bar.
       *
       * - A little less restrictive of the UI are retrieval statuses for existing feedback, basic feedback summaries, and detailed feedback summaries.
       *   For these a progress bar is shown across the childFeedbackPanelContainer. This hides most of the personal feedback UI however it allows the
       *   user to see the parent feedback UI (including the currently displayed item name). The photo is also visible. Although visible, the basic
       *   feedback summary section will be showing a default display of "blank" data during loading.
       *   The rationale for not using a progress bar for every individual nested panel is that it looks very strange when multiple requests are
       *   active at once, as they often are when for example a user is signed in and they have first selected a detailed person profile to load.
       *
       * - Less restrictive still in terms of UI locking is the display of error statuses. These are displayed locally to each nested panel, so
       *   that the user may still freely switch around between items and panels, but they are given a notice if/when data retrieval fails for
       *   individual items. However the exception is if there is an error retrieving the basic feedback summary - the rest of the personal feedback
       *   UI will then be unavailable.
       */

      if (isRetrievingBasicFeedbackSummary() || isRetrievingFeedbackSubmission() || isRetrievingDetailedFeedbackSummary())
         showLockedFeedbackComponent(getProgressBar());
      else if (isErrorRetrievingBasicFeedbackSummary())
      {
         /* The More/Less button is not available when the retrieval of the basic feedback summary has failed. The window might be stuck in the
          * large detailed feedback state, and the user would have no means to change this other than switch to the user account or help panels.
          * Unfortunately due to the way that the deferred resizing of the pad happens - only once the pad has become unlocked - and the fact that
          * the pad is locked at this point, there is no clean way to force the pad into the smaller 'basic feedback' state. A call to
          * switchToBasicPanel() won't do the job since the repack() won't actually happen until after the feedback pad has moved out of its
          * locked state, by which time the showLockedFeedbackComponent() call below will have occurred (locking the personal feedback sub panel,
          * not the top level pad panel). When showLockedFeedbackComponent() is called it will cause the panel to be locked while displaying the
          * blurred unlocked image; the key point is that in this state, the preferred size becomes the size of that blurred image. So even if a
          * repack of the pad is called, the size of the personal feedback sub panel will still be fixed at whatever size it was at the time that
          * it was locked. In summary, it means that there's no straightforward way to programmatically switch to the smaller basic feedback view
          * when there is a network error. Also note that placing the More/Less button outside of the locked childFeedbackPanelContainer wouldn't
          * work since when locked (as mentioned above) the child container panel will not resize.
          *
          * It's not so bad though since I'm reluctant to force the resize anyway - it's a bit jarring to the user. Also not forgetting that it's
          * handling the UI in an exceptional, temporary state ie. while the server is unavailable. It should be a rare occurrence.
          */
         showLockedFeedbackComponent(getFeedbackRetrievalErrorDialog().getDelegate());
      }
      else
         hideLockedFeedbackPanel();
   }


   private void updateChildPanelControlsForActiveUserEntry()
   {
      basicPanel.showPersonProfile(activePerson.getPersonProfile(),
                               activePerson.getFeedbackSubmissionAvailabilityStatus(),
                               activePerson.getFeedbackSubmission());

      detailedPanel.showPersonProfileFeedback(activePerson.getPersonProfile(),
                                  activePerson.getDetailedFeedbackSummaryAvailabilityStatus(),
                                  activePerson.getDetailedFeedbackSummary(),
                                  activePerson.getFeedbackSubmissionAvailabilityStatus(),
                                  activePerson.getFeedbackSubmission());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handlePersonProfileDetailsUpdated(final PersonalFeedbackPersonProfile updatedPersonProfile)
   {
      if (updatedPersonProfile.person.equals(activePerson.getPersonProfile().person))
      {
         activePerson.setPersonProfile(updatedPersonProfile);

         updateUserProfileDisplay();

         basicPanel.profileDetailsUpdated(updatedPersonProfile);
         detailedPanel.profileDetailsUpdated(updatedPersonProfile);
      }
   }


   private void handleBasicFeedbackSummaryAvailabilityStatusUpdated(final PersonalFeedbackPerson person, final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus)
   {
      if (person.equals(activePerson.getPersonProfile().person))
      {
         activePerson.setBasicFeedbackSummaryAvailabilityStatus(basicFeedbackSummaryAvailabilityStatus);
         updateForChangedDataAvailabilityStatus();
      }
   }


   private void handleBasicFeedbackSummaryUpdated(final PersonalFeedbackPerson person, final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus,
                                                  final PersonalFeedbackBasicSummary basicFeedbackSummary)
   {
      if (person.equals(activePerson.getPersonProfile().person))
      {
         // Unlikely but possible that the feedback summary remains unchanged when the number of ratings is updated.
         final boolean feedbackSummaryUpdated = (! activePerson.getBasicFeedbackSummary().equals(basicFeedbackSummary));
         final boolean numberOfRatingsUpdated = (activePerson.getBasicFeedbackSummary().numberOfRatings != basicFeedbackSummary.numberOfRatings);

         activePerson.setBasicFeedbackSummary(basicFeedbackSummary);
         activePerson.setBasicFeedbackSummaryAvailabilityStatus(basicFeedbackSummaryAvailabilityStatus);

         handlePanelUpdateForChangedBasicFeedbackSummary(feedbackSummaryUpdated, numberOfRatingsUpdated);
      }
   }


   private void handleDetailedFeedbackSummaryAvailabilityStatusUpdated(final PersonalFeedbackPerson person,
                                                                       final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus)
   {
      if (person.equals(activePerson.getPersonProfile().person))
      {
         activePerson.setDetailedFeedbackSummaryAvailabilityStatus(detailedFeedbackSummaryAvailabilityStatus);
         updateForChangedDataAvailabilityStatus();
      }
   }


   private void handleDetailedFeedbackSummaryUpdated(final PersonalFeedbackPerson person, final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus,
                                                     final PersonalFeedbackDetailedSummary detailedFeedbackSummary)
   {
      if (person.equals(activePerson.getPersonProfile().person))
      {
         activePerson.setDetailedFeedbackSummary(detailedFeedbackSummary);
         activePerson.setDetailedFeedbackSummaryAvailabilityStatus(detailedFeedbackSummaryAvailabilityStatus);

         updateForChangedDataAvailabilityStatus();

         detailedPanel.detailedFeedbackSummaryUpdated(detailedFeedbackSummaryAvailabilityStatus, detailedFeedbackSummary);
      }
   }


   private void handleFeedbackSubmissionAvailabilityStatusUpdated(final PersonalFeedbackPerson person, final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus)
   {
      if (person.equals(activePerson.getPersonProfile().person))
      {
         activePerson.setFeedbackSubmissionAvailabilityStatus(feedbackSubmissionAvailabilityStatus);
         updateForChangedDataAvailabilityStatus();
      }
   }


   private void handleFeedbackSubmissionUpdated(final PersonalFeedbackPerson person, final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus,
                                                final PersonalFeedbackSubmission feedbackSubmission)
   {
      if (person.equals(activePerson.getPersonProfile().person))
      {
         activePerson.setFeedbackSubmission(feedbackSubmission);
         activePerson.setFeedbackSubmissionAvailabilityStatus(feedbackSubmissionAvailabilityStatus);

         updateForChangedDataAvailabilityStatus();

         basicPanel.feedbackFromUserUpdated(feedbackSubmissionAvailabilityStatus, feedbackSubmission);
         detailedPanel.feedbackFromUserUpdated(feedbackSubmissionAvailabilityStatus, feedbackSubmission);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleShowPerson(final PersonalFeedbackPerson person)
   {
      /* Fetch a local copy of the feedback manager's node containing the person's details.
       * Since it's initialised on the Swing thread, all data access must be via Swing.
       * Also of course since it's a copy of the ManagedPersonalFeedbackPerson, locking on it
       * has no effect on the manager operations.
       */
      final ManagedPersonalFeedbackPerson managedPersonCopy = uiManager.getManagedPersonCopy(person);

      this.activePerson = managedPersonCopy;

      handlePanelUpdateForChangedPerson();

      updateChildPanelControlsForActiveUserEntry();

      passiveRetrieveAllFeedbackData();
   }


   private boolean handleHasFeedbackSubmissionBeenUpdated()
   {
      if (signedInUserAccount != null)
      {
         if (isShowingBasicFeedbackPanel() && (! activePerson.getFeedbackSubmission().hasAtLeastOneCriteriaRating()))
            return basicPanel.hasFeedbackBeenUpdated();
         else
            return detailedPanel.hasFeedbackBeenUpdated();
      }
      else
         return false;
   }


   private void handleSignedInToUserAccount(final FeedbactoryUserAccount signedInUserAccount)
   {
      this.signedInUserAccount = signedInUserAccount;

      basicPanel.reportFeedbactoryUserAccountStatus(true);
      detailedPanel.reportFeedbactoryUserAccountStatus(true);

      /* Since the account sign in signal may have been received before this panel has been snapped in to a parent,
       * ie. before showItem() has been called, there must be a null check of the active person profile.
       */
      if (activePerson != null)
         uiManager.requestGetPersonFeedbackSubmission(activePerson.getPersonProfile().person);
   }


   private void handleUserAccountDetailsUpdated(final FeedbactoryUserAccount updatedUserAccount)
   {
      this.signedInUserAccount = updatedUserAccount;
   }


   private void handleSignedOutOfUserAccount()
   {
      this.signedInUserAccount = null;

      basicPanel.reportFeedbactoryUserAccountStatus(false);
      detailedPanel.reportFeedbactoryUserAccountStatus(false);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final boolean isShowingItem(final PersonalFeedbackPerson item)
   {
      return (activePerson != null) && (activePerson.getPersonProfile().person.equals(item));
   }


   final void personProfileDetailsUpdated(final PersonalFeedbackPersonProfile updatedPersonProfile)
   {
      handlePersonProfileDetailsUpdated(updatedPersonProfile);
   }


   final void basicFeedbackSummaryAvailabilityStatusUpdated(final PersonalFeedbackPerson person, final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus)
   {
      handleBasicFeedbackSummaryAvailabilityStatusUpdated(person, basicFeedbackSummaryAvailabilityStatus);
   }


   final void basicFeedbackSummaryUpdated(final PersonalFeedbackPerson person, final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus,
                                          final PersonalFeedbackBasicSummary basicFeedbackSummary)
   {
      handleBasicFeedbackSummaryUpdated(person, basicFeedbackSummaryAvailabilityStatus, basicFeedbackSummary);
   }


   final void detailedFeedbackSummaryAvailabilityStatusUpdated(final PersonalFeedbackPerson person, final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus)
   {
      handleDetailedFeedbackSummaryAvailabilityStatusUpdated(person, detailedFeedbackSummaryAvailabilityStatus);
   }


   final void detailedFeedbackSummaryUpdated(final PersonalFeedbackPerson person, final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus,
                                             final PersonalFeedbackDetailedSummary detailedFeedbackSummary)
   {
      handleDetailedFeedbackSummaryUpdated(person, detailedFeedbackSummaryAvailabilityStatus, detailedFeedbackSummary);
   }


   final void feedbackSubmissionAvailabilityStatusUpdated(final PersonalFeedbackPerson person, final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus)
   {
      handleFeedbackSubmissionAvailabilityStatusUpdated(person, feedbackSubmissionAvailabilityStatus);
   }


   final void feedbackSubmissionUpdated(final PersonalFeedbackPerson person, final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus,
                                        final PersonalFeedbackSubmission personalFeedbackSubmission)
   {
      handleFeedbackSubmissionUpdated(person, feedbackSubmissionAvailabilityStatus, personalFeedbackSubmission);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final void showMessageDialog(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection defaultAction, final boolean actionOnDialogHidden)
   {
      feedbackPanel.showMessageDialog(messageDialog, defaultAction, actionOnDialogHidden);
   }


   final void showAccountPanel()
   {
      feedbackPanel.showAccountPanel();
   }


   final void requestFetchFeedbackSubmission()
   {
      retrieveFeedbackSubmission();
   }


   final void requestFetchDetailedFeedbackSummary()
   {
      retrieveDetailedFeedbackSummary();
   }


   final void submitUserFeedback(final PersonalFeedbackSubmission personalFeedbackSubmission)
   {
      uiManager.requestAddPersonFeedbackSubmission(activePerson.getPersonProfile().person, personalFeedbackSubmission);
   }


   final void removeFeedbackSubmission()
   {
      uiManager.requestRemovePersonFeedbackSubmission(activePerson.getPersonProfile().person);
   }


   final void showDetailedFeedbackSubmission()
   {
      detailedPanel.showDetailedFeedbackSubmissionPanel();
      switchToDetailedPanel();
   }


   final void requestRepack()
   {
      feedbackPanel.requestRepack();
   }


   final void dispose()
   {
      finishProfilePhotoLoadRetryTask();

      averageRatingResultLabel.dispose();
      lowRatingResultLabel.dispose();
      numberOfRatingsLabel.dispose();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public FeedbackCategory getFeedbackCategory()
   {
      return FeedbackCategory.Personal;
   }


   @Override
   final public JComponent getDelegate()
   {
      return delegatePanel;
   }


   @Override
   final public void showItem(final FeedbackItem itemID)
   {
      handleShowPerson((PersonalFeedbackPerson) itemID);
   }


   @Override
   final public void refreshItem()
   {
      forcefulRetrieveAllFeedbackData();
   }


   @Override
   final public boolean hasItemFeedbackSubmissionBeenUpdated()
   {
      return handleHasFeedbackSubmissionBeenUpdated();
   }


   @Override
   final public void signedInToUserAccount(final FeedbactoryUserAccount userAccount)
   {
      handleSignedInToUserAccount(userAccount);
   }


   @Override
   final public void userAccountDetailsUpdated(final FeedbactoryUserAccount userAccount)
   {
      handleUserAccountDetailsUpdated(userAccount);
   }


   @Override
   final public void signedOutOfUserAccount(final FeedbactoryUserAccount userAccount)
   {
      handleSignedOutOfUserAccount();
   }
}