/* Memos:
 * - I tried using the same PersonalFeedbackSubmissionTableCellDelegate object for both the table cell criteria renderer and editor.
 *   It almost works but understandably there are some visual glitches as the user moves between editing and rendering: sometimes a radio button on
 *   a previously active row appears 'half-selected', and sometimes when switching to editing the whole cell goes blank. All in all it's safer to
 *   just keep the renderer and editor separate.
 */

package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.client.core.network.DataAvailabilityStatus;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.component.LockableComponent;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.ShadowedToolTipButton;
import com.feedbactory.client.ui.feedback.FeedbackPanel;
import com.feedbactory.client.ui.pad.PadResources;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteria;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaAttributes;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmission;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleKeyValue;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton.ToggleButtonModel;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;


final class PersonalFeedbackFromUserPanel
{
   static final private int FeedbackTableWidth = 590;

   final private PersonalFeedbackDetailedPanel parentPanel;

   final private LockableComponent delegatePanel = new LockableComponent(new ShadedPanel());
   private JComponent activeLockedComponent;

   final private JPanel basicFeedbackPanel = new JPanel(null);
   final private JLabel basicRatePromptLabel = new JLabel();
   final private ButtonGroup basicFeedbackButtonGroup = new ButtonGroup();
   final private JRadioButton[] basicFeedbackButtons = new JRadioButton[11];

   final private JPanel detailedFeedbackResultPanel = new JPanel(null);
   final private JLabel detailedFeedbackRatingLabel = new JLabel();
   final private JLabel detailedFeedbackResultLabel = new JLabel();

   final private ShadowedToolTipButton detailedFeedbackHelpButton = new ShadowedToolTipButton();

   final private JCheckBox calculateRatingFromCriteriaFeedbackCheckBox = new JCheckBox();

   final private JTable feedbackTable = new JTable();
   final private JScrollPane feedbackTableScrollPane = new JScrollPane(feedbackTable);
   final private PersonalFeedbackSubmissionTableCellEditor feedbackTableCellEditor = new PersonalFeedbackSubmissionTableCellEditor();
   final private PersonalFeedbackSubmissionTableCellRenderer feedbackTableCellRenderer = new PersonalFeedbackSubmissionTableCellRenderer();

   private EnumMap<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> criteriaFeedback;

   final private JButton submitFeedbackButton = new JButton();
   final private JButton resetButton = new JButton();
   final private JButton removeFeedbackButton = new JButton();

   private boolean isUserSignedIntoFeedbactoryAccount;

   private PersonalFeedbackPersonProfile activePersonProfile;
   private DataAvailabilityStatus feedbackSubmissionAvailabilityStatus;
   private PersonalFeedbackSubmission feedbackSubmission = PersonalFeedbackSubmission.EmptyPersonalFeedbackSubmission;


   public PersonalFeedbackFromUserPanel(final PersonalFeedbackDetailedPanel parentPanel)
   {
      this.parentPanel = parentPanel;

      initialise();

      //resetFormForBlankFeedback();
   }


   private void initialise()
   {
      initialiseDelegatePanel();
   }


   private void initialiseDelegatePanel()
   {
      initialiseEnabledUserFeedbackSection();

      initialiseLockedUserFeedbackSection();

      delegatePanel.getRootComponent().setOpaque(false);
   }


   private void initialiseEnabledUserFeedbackSection()
   {
      delegatePanel.getUnlockedComponent().setOpaque(false);

      calculateRatingFromCriteriaFeedbackCheckBox.setText("Calculate rating from criteria feedback:");
      calculateRatingFromCriteriaFeedbackCheckBox.setFont(UIConstants.RegularFont);
      calculateRatingFromCriteriaFeedbackCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
      calculateRatingFromCriteriaFeedbackCheckBox.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleCalculateRatingFromCriteriaFeedbackChanged();
         }
      });

      initialiseRadioButtons();

      initialiseBasicFeedbackPanel();

      initialiseDetailedFeedbackSection();

      initialiseFeedbackControlButtons();

      initialiseEnabledUserFeedbackLayout();
   }


   private void initialiseRadioButtons()
   {
      final ActionListener actionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleBasicFeedbackRadioButtonClicked();
         }
      };

      for (int ratingButtonIndex = 0; ratingButtonIndex <= 10; ratingButtonIndex ++)
      {
         basicFeedbackButtons[ratingButtonIndex] = new JRadioButton();

         basicFeedbackButtons[ratingButtonIndex].setModel(new BasicFeedbackRadioButtonModel((byte) (ratingButtonIndex * 10)));

         basicFeedbackButtons[ratingButtonIndex].setFont(UIConstants.RegularFont);
         basicFeedbackButtons[ratingButtonIndex].setText(Integer.toString(ratingButtonIndex));
         basicFeedbackButtons[ratingButtonIndex].setHorizontalTextPosition(SwingConstants.CENTER);
         basicFeedbackButtons[ratingButtonIndex].setVerticalTextPosition(SwingConstants.BOTTOM);

         basicFeedbackButtons[ratingButtonIndex].addActionListener(actionListener);

         basicFeedbackButtonGroup.add(basicFeedbackButtons[ratingButtonIndex]);
      }
   }


   private void initialiseBasicFeedbackPanel()
   {
      basicRatePromptLabel.setFont(UIConstants.RegularFont);
      basicRatePromptLabel.setText("Rate:");

      basicFeedbackPanel.setOpaque(false);

      final GroupLayout panelLayout = new GroupLayout(basicFeedbackPanel);
      basicFeedbackPanel.setLayout(panelLayout);

      final SequentialGroup horizontalRatingButtonGroup = panelLayout.createSequentialGroup();
      final ParallelGroup verticalRatingButtonGroup = panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER);

      horizontalRatingButtonGroup.addComponent(basicRatePromptLabel);
      horizontalRatingButtonGroup.addGap(15);

      verticalRatingButtonGroup.addComponent(basicRatePromptLabel);

      for (int ratingButtonIndex = 0; ratingButtonIndex < 10; ratingButtonIndex ++)
      {
         horizontalRatingButtonGroup.addComponent(basicFeedbackButtons[ratingButtonIndex]);
         horizontalRatingButtonGroup.addPreferredGap(ComponentPlacement.UNRELATED);

         verticalRatingButtonGroup.addComponent(basicFeedbackButtons[ratingButtonIndex]);
      }

      // Adding a component gap when there's no following component can mess up container gaps.
      horizontalRatingButtonGroup.addComponent(basicFeedbackButtons[10]);
      verticalRatingButtonGroup.addComponent(basicFeedbackButtons[10]);

      panelLayout.setHorizontalGroup(horizontalRatingButtonGroup);
      panelLayout.setVerticalGroup(verticalRatingButtonGroup);
   }


   private void initialiseDetailedFeedbackSection()
   {
      initialiseDetailedFeedbackResultPanel();
      initialiseDetailedFeedbackHelpButton();
      initialiseFeedbackCriteriaTable();
   }


   private void initialiseDetailedFeedbackResultPanel()
   {
      detailedFeedbackRatingLabel.setFont(UIConstants.RegularFont);
      detailedFeedbackRatingLabel.setText("Rated by me (0-10):");

      detailedFeedbackResultLabel.setHorizontalAlignment(SwingConstants.CENTER);

      detailedFeedbackResultPanel.setOpaque(false);

      final GroupLayout panelLayout = new GroupLayout(detailedFeedbackResultPanel);
      detailedFeedbackResultPanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addComponent(detailedFeedbackRatingLabel)
         .addGap(10)
         .addComponent(detailedFeedbackResultLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
      );

      panelLayout.setVerticalGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
         .addComponent(detailedFeedbackRatingLabel)
         .addComponent(detailedFeedbackResultLabel)
      );
   }


   private void initialiseDetailedFeedbackHelpButton()
   {
      detailedFeedbackHelpButton.setFocusable(false);

      try
      {
         final BufferedImage buttonImage = ImageIO.read(FeedbackPanel.class.getResourceAsStream(PadResources.HelpButtonImagePath));
         detailedFeedbackHelpButton.setIcon(new ImageIcon(UIUtilities.getScaledImage(buttonImage, UIConstants.PreferredIconWidth, UIConstants.PreferredIconHeight,
                                                                                     RenderingHints.VALUE_INTERPOLATION_BICUBIC)));
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }

      final StringBuilder tipTextStringBuilder = new StringBuilder();
      tipTextStringBuilder.append("Fill in as many of the criteria below as you like,\n");
      tipTextStringBuilder.append("skipping any that you're unsure of.\n");
      tipTextStringBuilder.append("It's better to pass than to hazard a guess.\n\n");
      tipTextStringBuilder.append("If you later change your mind you can update\n");
      tipTextStringBuilder.append("your previous feedback or remove it entirely.");

      detailedFeedbackHelpButton.setToolTipText(tipTextStringBuilder.toString());
      detailedFeedbackHelpButton.setToolTipOffset(new Point(-260, 30));
      detailedFeedbackHelpButton.setToolTipDismissDelayMilliseconds(20000);

      detailedFeedbackHelpButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            synthesiseShowToolTipEvent();
         }
      });
   }


   private void synthesiseShowToolTipEvent()
   {
      final int originalToolTipDelay = ToolTipManager.sharedInstance().getInitialDelay();
      ToolTipManager.sharedInstance().setInitialDelay(0);

      ToolTipManager.sharedInstance().mouseEntered(new MouseEvent(detailedFeedbackHelpButton, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 0, 0, 0, false));
      ToolTipManager.sharedInstance().mouseMoved(new MouseEvent(detailedFeedbackHelpButton, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 0, 0, 0, false));

      ToolTipManager.sharedInstance().setInitialDelay(originalToolTipDelay);
   }


   private void initialiseFeedbackCriteriaTable()
   {
      feedbackTable.setModel(new PersonalFeedbackSubmissionTableModel());

      feedbackTable.setGridColor(UIConstants.ListCellBorderColour);
      feedbackTable.setShowGrid(true);

      // See the layout note below for the rationale behind overriding the table's default scrollable viewport size.
      feedbackTable.setPreferredScrollableViewportSize(new Dimension(0, 0));
      feedbackTable.setFillsViewportHeight(true);
      feedbackTable.setRowHeight(30);

      feedbackTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      feedbackTable.setDefaultRenderer(PersonalFeedbackCriteria.class, new PersonalFeedbackGeneralCellRenderer<PersonalFeedbackCriteria>());
      feedbackTable.setDefaultRenderer(PersonalFeedbackSubmissionScaleKeyValue.class, feedbackTableCellRenderer);

      feedbackTable.setDefaultEditor(PersonalFeedbackSubmissionScaleKeyValue.class, feedbackTableCellEditor);
      feedbackTableCellEditor.addSelectionChangeListener(new PersonalFeedbackSubmissionSelectionChangeListener()
      {
         @Override
         final public void selectionChanged(final PersonalFeedbackSubmissionScaleKeyValue newSelection)
         {
            handleFormFeedbackUpdated();
         }
      });

      final JTableHeader tableHeader = feedbackTable.getTableHeader();
      tableHeader.setFont(UIConstants.RegularFont);
      tableHeader.setReorderingAllowed(false);
      tableHeader.setResizingAllowed(false);

      /* Refer to JTable.doLayout() for a detailed description of JTable's column resizing policies.
       * Here I'm applying percentage widths to each column, using the overall width of the table.
       */
      final TableColumn criteriaColumn = feedbackTable.getColumnModel().getColumn(PersonalFeedbackSubmissionTableModel.CriteriaColumnIndex);
      criteriaColumn.setPreferredWidth((int) (0.25f * FeedbackTableWidth));

      final TableColumn criteriaFeedbackColumn = feedbackTable.getColumnModel().getColumn(PersonalFeedbackSubmissionTableModel.CriteriaFeedbackColumnIndex);
      criteriaFeedbackColumn.setPreferredWidth((int) (0.75f * FeedbackTableWidth));

      feedbackTableScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
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

      resetButton.setFont(UIConstants.RegularFont);
      resetButton.setText("Reset");

      resetButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleResetButtonClicked();
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


   private void initialiseEnabledUserFeedbackLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getUnlockedComponent());
      delegatePanel.getUnlockedComponent().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createParallelGroup()
         .addGroup(panelLayout.createSequentialGroup()
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
            .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
               .addComponent(basicFeedbackPanel)
               .addComponent(detailedFeedbackResultPanel)
            )
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         )
         .addGroup(panelLayout.createSequentialGroup()
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
            .addGroup(panelLayout.createParallelGroup()
               .addGroup(panelLayout.createSequentialGroup()
                  .addGap(10)
                  .addComponent(calculateRatingFromCriteriaFeedbackCheckBox)
                  .addPreferredGap(ComponentPlacement.UNRELATED, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
                  .addComponent(detailedFeedbackHelpButton, GroupLayout.PREFERRED_SIZE, UIConstants.PreferredIconButtonWidth, GroupLayout.PREFERRED_SIZE)
                  .addGap(10)
               )
               .addComponent(feedbackTableScrollPane, GroupLayout.PREFERRED_SIZE, FeedbackTableWidth, GroupLayout.PREFERRED_SIZE)
            )
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         )
         .addGroup(panelLayout.createSequentialGroup()
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
            .addComponent(submitFeedbackButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
            .addPreferredGap(ComponentPlacement.UNRELATED)
            .addComponent(resetButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
            .addPreferredGap(ComponentPlacement.UNRELATED)
            .addComponent(removeFeedbackButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         )
      );

      /* The default preferred scrollable size for JTables is arbitrarily set to 450, 400.
       * Normally it would be possible to override this with sensible preferred values based on the column widths and the cell row height, but the overall height
       * of this panel is dictated by the parent panel in PersonalFeedbackDetailedPanel, for the sake of having the height of this panel and the
       * PersonalFeedbackFromEveryonePanel be exactly the same. At least the width of the table can be controlled by the horizontal group above, where
       * it's set to an exact amount - that part is simple.
       *
       * But there's a potential problem. If the table's default preferred height of 400 is greater than the room allowed it by the parent panel (and it is), then
       * the table will try to vertically squeeze out other components wherever it can, eg. the container gaps. By the same token it's no good to set a rigid smaller
       * preferred height on the table. The goal is to allow the table to fill out as much as it can vertically, while not eating into the preferred sizes of
       * any other components or gaps. The trick to it is to set rigid vertical container gaps on the GroupLayout, while allowing complete flexibility for the
       * table to grow within the available space - give it constraints of minimum 0, preferred 0, maximum Integer.MAX_VALUE.
       */
      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(20, 20)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(basicFeedbackPanel)
            .addComponent(detailedFeedbackResultPanel)
         )
         .addGap(15)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.TRAILING)
            .addComponent(calculateRatingFromCriteriaFeedbackCheckBox)
            .addComponent(detailedFeedbackHelpButton, GroupLayout.PREFERRED_SIZE, UIConstants.PreferredIconButtonHeight, GroupLayout.PREFERRED_SIZE)
         )
         .addPreferredGap(ComponentPlacement.RELATED)
         .addComponent(feedbackTableScrollPane, 0, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
         .addGap(20)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(submitFeedbackButton)
            .addComponent(resetButton)
            .addComponent(removeFeedbackButton)
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, UIConstants.MediumContainerGapSize)
      );

      /* The display will switch between showing either basic feedback controls (radio buttons with labels) and a label showing the overall feedback result as
       * calculated from the feedback criteria, depending on the state of the onlineProfileFeedbackOnlyCheckBox.
       * Since the vertical height of the basic feedback controls and the detailed feedback result label may be different, I need to reserve enough space for
       * both so that switching between the two doesn't result in the panel being either squished up or (if all gaps are rigid) the outer frame itself resized larger.
       * So, the setHonorsVisibility() method is used to reserve enough space for both panels even when they're invisible.
       * To see the effect without the reserved space, comment out the setHonorsVisibility() calls and toggle the onlineProfileFeedbackOnlyCheckBox.
       */
      panelLayout.setHonorsVisibility(basicFeedbackPanel, false);
      panelLayout.setHonorsVisibility(detailedFeedbackResultPanel, false);
   }


   private void initialiseLockedUserFeedbackSection()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getLockedComponent());
      delegatePanel.getLockedComponent().setLayout(panelLayout);

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


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final private class ShadedPanel extends JPanel
   {
      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         final float thirdHeight = getHeight() / 3f;
         final Rectangle2D gradientPaintExtent = new Rectangle2D.Float(0f, 0f, getWidth(), thirdHeight);
         final Color[] colours = new Color[] {UIUtilities.shiftColourBrightness(UIConstants.ContentPanelColour, 0.1f), UIConstants.ContentPanelColour};
         final float[] colourRanges = new float[] {0.2f, 1f};
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
            parentPanel.showAccountPanel();
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


   private MessageDialog createCriteriaFeedbackRequiredDialog()
   {
      final String[] message = new String[] {"Please select feedback for one or more criteria."};
      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Error, message, MessageDialog.PresetOptionConfiguration.OK);
      return new MessageDialog(builder);
   }


   private MessageDialog createFeedbackSubmissionConfirmationDialog(final String displayName)
   {
      final String truncatedDisplayName = UIUtilities.getEllipsisTruncatedString(displayName, 60);
      final String[] message = new String[] {"Change your feedback for", truncatedDisplayName + '?'};
      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Question, message, MessageDialog.PresetOptionConfiguration.OKCancel);

      final MessageDialog dialog = new MessageDialog(builder);

      dialog.addActionListener(new MessageDialog.ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            if (optionSelection == MessageDialog.PresetOptionSelection.OK)
               submitFeedback();
         }
      });

      return dialog;
   }


   private MessageDialog createFeedbackRemovalConfirmationDialog(final String displayName)
   {
      final String truncatedDisplayName = UIUtilities.getEllipsisTruncatedString(displayName, 60);
      final String[] message = new String[] {"Remove your feedback for", truncatedDisplayName + '?'};
      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Question, message, MessageDialog.PresetOptionConfiguration.OKCancel);

      final MessageDialog dialog = new MessageDialog(builder);

      dialog.addActionListener(new MessageDialog.ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            if (optionSelection == MessageDialog.PresetOptionSelection.OK)
               removeFeedback();
         }
      });

      return dialog;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleBasicFeedbackRadioButtonClicked()
   {
      refreshControlButtons();
   }


   private void handleCalculateRatingFromCriteriaFeedbackChanged()
   {
      refreshOverallFeedbackResultControls();
      refreshControlButtons();
   }


   private void handleFormFeedbackUpdated()
   {
      /* When switching between the radio buttons within the same table row, changes are not automatically committed back to the table model.
       * We can save ourselves some grief by taking care of that here, so that the feedback model is always up to date. The alternative is
       * to not auto-commit here but ensure that the commit is performed whenever we need to fetch the model data.
       */
      commitCriteriaFeedbackTableUpdates();

      final Map<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> updatedFormData = getCriteriaFeedbackTableData();

      if (calculateRatingFromCriteriaFeedbackCheckBox.isSelected())
         displayDetailedOverallFeedbackResult(PersonalFeedbackSubmission.calculateDetailedFeedbackRating(updatedFormData));

      refreshControlButtons();
   }


   private void handleSubmitButtonClicked()
   {
      final PersonalFeedbackSubmission submission = getFeedbackFormSubmission();
      if (submission.overallFeedbackRating == PersonalFeedbackSubmission.NoRatingValue)
      {
         if (calculateRatingFromCriteriaFeedbackCheckBox.isSelected())
            parentPanel.showMessageDialog(createCriteriaFeedbackRequiredDialog(), MessageDialog.PresetOptionSelection.OK, true);
         else
            parentPanel.showMessageDialog(createOverallFeedbackRatingRequiredDialog(), MessageDialog.PresetOptionSelection.OK, true);
      }
      else if (hasExistingFeedbackSubmission())
         parentPanel.showMessageDialog(createFeedbackSubmissionConfirmationDialog(activePersonProfile.getFullName()), MessageDialog.PresetOptionSelection.OK, true);
      else
         submitFeedback();
   }


   private void handleResetButtonClicked()
   {
      resetFormForActiveFeedback();
   }


   private void handleRemoveFeedbackButtonClicked()
   {
      parentPanel.showMessageDialog(createFeedbackRemovalConfirmationDialog(activePersonProfile.getFullName()), MessageDialog.PresetOptionSelection.Cancel, true);
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


   private void requestFetchFeedback()
   {
      parentPanel.requestFetchFeedbackSubmission();
   }


   private void submitFeedback()
   {
      parentPanel.submitUserFeedback(getFeedbackFormSubmission());
   }


   private void removeFeedback()
   {
      parentPanel.removeFeedback();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void updateLockedPanel()
   {
      if (isErrorRetrievingFeedbackSubmission())
         showLockedComponent(createFeedbackRetrievalErrorDialog().getDelegate());
      else if (! isUserSignedIntoFeedbactoryAccount)
         showLockedComponent(createSignInPromptDialog().getDelegate());
      else
         hideLockedPanel();
   }


   private void showLockedComponent(final JComponent lockedComponentToActivate)
   {
      setActiveLockedComponent(lockedComponentToActivate);
      delegatePanel.setLocked(true);
   }


   private void setActiveLockedComponent(final JComponent lockedComponentToActivate)
   {
      final GroupLayout lockedPanelLayout = (GroupLayout) delegatePanel.getLockedComponent().getLayout();
      lockedPanelLayout.replace(activeLockedComponent, lockedComponentToActivate);

      activeLockedComponent = lockedComponentToActivate;
   }


   private void hideLockedPanel()
   {
      delegatePanel.setLocked(false);
      setActiveLockedComponent(new JPanel(null));
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private byte handleGetBasicFeedbackRating()
   {
      final BasicFeedbackRadioButtonModel selectedButtonModel = (BasicFeedbackRadioButtonModel) basicFeedbackButtonGroup.getSelection();

      if (selectedButtonModel != null)
         return selectedButtonModel.buttonSelectionValue;
      else
         return PersonalFeedbackSubmission.NoRatingValue;
   }


   private void resetBasicFeedbackRating(final byte basicFeedbackRating)
   {
      if (basicFeedbackRating != PersonalFeedbackSubmission.NoRatingValue)
         basicFeedbackButtons[basicFeedbackRating / 10].setSelected(true);
      else
         basicFeedbackButtonGroup.clearSelection();
   }


   private void commitCriteriaFeedbackTableUpdates()
   {
      if (feedbackTable.isEditing())
      {
         final PersonalFeedbackSubmissionTableCellEditor editor = (PersonalFeedbackSubmissionTableCellEditor) feedbackTable.getDefaultEditor(PersonalFeedbackSubmissionScaleKeyValue.class);
         editor.fireEditingStopped();
      }
   }


   private Map<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> getCriteriaFeedbackTableData()
   {
      return ((PersonalFeedbackSubmissionTableModel) feedbackTable.getModel()).getActiveFeedback();
   }


   private void resetCriteriaFeedbackTableData()
   {
      final PersonalFeedbackCriteriaType criteriaType;
      if (activePersonProfile != null)
         criteriaType = activePersonProfile.person.getCriteriaType();
      else
         criteriaType = PersonalFeedbackCriteriaType.None;

      feedbackTableCellRenderer.switchToSubmissionScaleProfile(criteriaType.attributes.getSubmissionScaleProfile());
      feedbackTableCellEditor.switchToSubmissionScaleProfile(criteriaType.attributes.getSubmissionScaleProfile());
      final TableColumn criteriaFeedbackColumn = feedbackTable.getColumnModel().getColumn(PersonalFeedbackSubmissionTableModel.CriteriaFeedbackColumnIndex);
      criteriaFeedbackColumn.setHeaderValue(PersonalFeedbackSubmissionScaleRenderer.getRendererFor(criteriaType.attributes.getSubmissionScaleProfile()).getSubmissionColumnHeader());
      ((PersonalFeedbackSubmissionTableModel) feedbackTable.getModel()).setActiveFeedback(criteriaType, criteriaFeedback);
   }


   private PersonalFeedbackSubmission getFeedbackFormSubmission()
   {
      if (activePersonProfile == null)
         return PersonalFeedbackSubmission.EmptyPersonalFeedbackSubmission;

      if (calculateRatingFromCriteriaFeedbackCheckBox.isSelected())
         return new PersonalFeedbackSubmission(criteriaFeedback);
      else
         return new PersonalFeedbackSubmission(getBasicFeedbackRating(), criteriaFeedback);
   }


   private boolean hasFeedbackFormBeenUpdated(final PersonalFeedbackSubmission feedbackFormSubmission)
   {
      /* Some care is needed here not to be overly sensitive to changed feedback, so as not to prompt the user to 'discard unsaved feedback' when they haven't
       * updated anything of note.
       * If the user has existing feedback including criteria ratings the checks should be more strict in detecting changes to the form, ie. if they've switched
       * the calculation for the overall feedback rating. The user would expect a prompt to discard unsaved changes in that case. Note that
       * the existing feedback case is not completely straightforward, since there is a distinction between existing basic feedback - only an overall rating
       * provided, no criteria - and detailed feedback, where at least one criteria has been rated. If the user has existing basic feedback, they would not expect
       * that toggling the calculation for the overall feedback rating control would affect whether or not the feedback is considered to have changed. In this instance,
       * feedbackEquals() should not be used since that method checks the isOverallRatingCalculatedFromCriteriaFeedback flag.
       *
       * If the user has no existing feedback, the checking required is essentially the same as that with existing basic feedback: the form has been updated once
       * the overall feedback changes (from no selection at all, to any selection) or at least one criteria feedback is updated to a non-pass value.
       */
      if (hasExistingFeedbackSubmission())
      {
         if (feedbackSubmission.hasAtLeastOneCriteriaRating())
            return (! feedbackFormSubmission.feedbackEquals(feedbackSubmission));
         else
            return (feedbackFormSubmission.overallFeedbackRating != feedbackSubmission.overallFeedbackRating) || feedbackFormSubmission.hasAtLeastOneCriteriaRating();
      }
      else
         return ((feedbackFormSubmission.overallFeedbackRating != PersonalFeedbackSubmission.NoRatingValue) || feedbackFormSubmission.hasAtLeastOneCriteriaRating());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void resetFormForActiveFeedback()
   {
      // Reset the table to view the top row.
      feedbackTableScrollPane.getViewport().setViewPosition(new Point(0, 0));

      populateEmptyCriteriaFeedback(activePersonProfile.person.getCriteriaType().attributes);

      if (hasExistingFeedbackSubmission())
      {
         if (feedbackSubmission.hasAtLeastOneCriteriaRating())
            resetFormForExistingDetailedFeedback();
         else  
            resetFormForExistingBasicFeedback();
      }
      else
         resetFormForBlankFeedback();
   }


   @SuppressWarnings("unchecked")
   private <E extends Enum<E> & PersonalFeedbackCriteria> void populateEmptyCriteriaFeedback(final PersonalFeedbackCriteriaAttributes<E> criteriaTypeAttributes)
   {
      final EnumMap<E, PersonalFeedbackSubmissionScaleKeyValue> targetCollection = new EnumMap<E, PersonalFeedbackSubmissionScaleKeyValue>(criteriaTypeAttributes.getCriteriaClass());

      for (final E criteria : criteriaTypeAttributes.getCriteriaSet())
         targetCollection.put(criteria, PersonalFeedbackSubmissionScaleKeyValue.NoRating);

      criteriaFeedback = targetCollection;
   }


   private void resetFormForExistingDetailedFeedback()
   {
      if (feedbackSubmission.isOverallRatingCalculatedFromCriteriaFeedback)
      {
         resetBasicFeedbackRating(PersonalFeedbackSubmission.NoRatingValue);
         calculateRatingFromCriteriaFeedbackCheckBox.setSelected(true);
      }
      else
      {
         resetBasicFeedbackRating(feedbackSubmission.overallFeedbackRating);
         calculateRatingFromCriteriaFeedbackCheckBox.setSelected(false);
      }

      populateExistingCriteriaFeedback(activePersonProfile.person.getCriteriaType().attributes);

      resetCriteriaFeedbackTableData();

      refreshOverallFeedbackResultControls();

      submitFeedbackButton.setEnabled(false);
      resetButton.setEnabled(false);
      removeFeedbackButton.setEnabled(true);
   }


   @SuppressWarnings("unchecked")
   private <E extends Enum<E> & PersonalFeedbackCriteria> void populateExistingCriteriaFeedback(final PersonalFeedbackCriteriaAttributes<E> criteriaTypeAttributes)
   {
      /* Without an explicit type parameter, the type for this method call cannot be known by the compiler. Javac 6 complains, Netbeans doesn't.
       * By providing the criteriaResolver (which has a type derived from using a supplied PersonalFeedbackCriteriaType), the compiler can
       * successfully resolve the type and compile this method even though the criteriaResolver is not explicitly used within it.
       */
      final Map<E, PersonalFeedbackSubmissionScaleKeyValue> targetCollection = (Map<E, PersonalFeedbackSubmissionScaleKeyValue>) criteriaFeedback;
      targetCollection.putAll((Map<E, PersonalFeedbackSubmissionScaleKeyValue>) feedbackSubmission.criteriaSubmissions);
   }


   private void resetFormForExistingBasicFeedback()
   {
      resetBasicFeedbackRating(feedbackSubmission.overallFeedbackRating);

      calculateRatingFromCriteriaFeedbackCheckBox.setSelected(false);

      resetCriteriaFeedbackTableData();

      refreshOverallFeedbackResultControls();

      submitFeedbackButton.setEnabled(false);
      resetButton.setEnabled(false);
      removeFeedbackButton.setEnabled(true);
   }


   private void resetFormForBlankFeedback()
   {
      resetBasicFeedbackRating(PersonalFeedbackSubmission.NoRatingValue);

      resetCriteriaFeedbackTableData();

      refreshOverallFeedbackResultControls();

      submitFeedbackButton.setEnabled(true);
      resetButton.setEnabled(false);
      removeFeedbackButton.setEnabled(false);
   }


   private void refreshOverallFeedbackResultControls()
   {
      if (calculateRatingFromCriteriaFeedbackCheckBox.isSelected())
      {
         displayManualOverallRatingControls(false);
         displayDetailedOverallFeedbackResult(PersonalFeedbackSubmission.calculateDetailedFeedbackRating(getCriteriaFeedbackTableData()));
      }
      else
         displayManualOverallRatingControls(true);
   }


   private void displayManualOverallRatingControls(final boolean areManualOverallRatingControlsEnabled)
   {
      basicFeedbackPanel.setVisible(areManualOverallRatingControlsEnabled);
      detailedFeedbackResultPanel.setVisible(! areManualOverallRatingControlsEnabled);
   }


   private void displayDetailedOverallFeedbackResult(final byte overallFeedbackGrade)
   {
      if (overallFeedbackGrade == PersonalFeedbackSubmission.NoRatingValue)
      {
         detailedFeedbackResultLabel.setFont(UIConstants.MediumBoldFont);
         detailedFeedbackResultLabel.setText("Unrated");
      }
      else
      {
         detailedFeedbackResultLabel.setFont(UIConstants.LargeBoldFont);
         detailedFeedbackResultLabel.setText(Float.toString(((float) overallFeedbackGrade) / 10));
      }
   }


   private void refreshControlButtons()
   {
      final boolean hasFeedbackBeenUpdated = hasFeedbackFormBeenUpdated(getFeedbackFormSubmission());

      if (hasExistingFeedbackSubmission())
         submitFeedbackButton.setEnabled(hasFeedbackBeenUpdated);
      else
         submitFeedbackButton.setEnabled(true);

      resetButton.setEnabled(hasFeedbackBeenUpdated);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleFeedbactoryUserAccountStatusUpdated(final boolean isUserSignedIntoFeedbactoryAccount)
   {
      this.isUserSignedIntoFeedbactoryAccount = isUserSignedIntoFeedbactoryAccount;

      feedbackSubmissionAvailabilityStatus = DataAvailabilityStatus.NotAvailable;
      feedbackSubmission = PersonalFeedbackSubmission.EmptyPersonalFeedbackSubmission;

      if (! isUserSignedIntoFeedbactoryAccount)
         resetFormForActiveFeedback();

      updateLockedPanel();
   }


   private void handleReportFeedbackFromUser()
   {
      resetFormForActiveFeedback();

      updateLockedPanel();
   }


   private boolean handleHasFeedbackBeenUpdated()
   {
      if (! isUserSignedIntoFeedbactoryAccount)
         return false;
      else
         return hasFeedbackFormBeenUpdated(getFeedbackFormSubmission());
   }


   private void handleSetBasicFeedbackRating(final byte basicFeedbackRating)
   {
      resetBasicFeedbackRating(basicFeedbackRating);

      refreshControlButtons();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final JComponent getDelegatePanel()
   {
      return delegatePanel.getRootComponent();
   }


   final void feedbactoryUserAccountStatusUpdated(final boolean isUserSignedIntoFeedbactoryAccount)
   {
      handleFeedbactoryUserAccountStatusUpdated(isUserSignedIntoFeedbactoryAccount);
   }


   final void showPersonProfileFeedback(final PersonalFeedbackPersonProfile userProfile,
                                        final DataAvailabilityStatus detailedFeedbackFromUserAvailabilityStatus,
                                        final PersonalFeedbackSubmission detailedFeedbackFromUser)
   {
      this.activePersonProfile = userProfile;

      reportFeedbackFromUser(detailedFeedbackFromUserAvailabilityStatus, detailedFeedbackFromUser);
   }


   final void profileDetailsUpdated(final PersonalFeedbackPersonProfile updatedPersonProfile)
   {
      this.activePersonProfile = updatedPersonProfile;
   }


   final void reportFeedbackFromUser(final DataAvailabilityStatus feedbackFromUserAvailabilityStatus, final PersonalFeedbackSubmission feedbackFromUser)
   {
      this.feedbackSubmissionAvailabilityStatus = feedbackFromUserAvailabilityStatus;
      this.feedbackSubmission = feedbackFromUser;

      handleReportFeedbackFromUser();
   }


   final boolean hasFeedbackBeenUpdated()
   {
      return handleHasFeedbackBeenUpdated();
   }


   final byte getBasicFeedbackRating()
   {
      return handleGetBasicFeedbackRating();
   }


   final void setBasicFeedbackRating(final byte basicFeedbackRating)
   {
      handleSetBasicFeedbackRating(basicFeedbackRating);
   }
}