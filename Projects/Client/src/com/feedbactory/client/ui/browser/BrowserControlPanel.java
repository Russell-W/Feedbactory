/* Memos:
 * - Regarding the browser engine manager's active browser service, and the active URL:
 * 
 * From the Swing side of things, the active browser service (current tab page) and URL may change at any time and be out of date. There's not much that we can do, since
 * we can't exactly lock down the browser while Swing wishes to fire off its events. If we put a lock in place from this class that still wouldn't prevent a situation 
 * where the browser has changed tabs and the SWT events fire while our Swing handlers are midway through with the lock held. The locking doesn't achieve anything in that
 * instance, except for (potentially) an unnecessary delay once the SWT event handler reaches this class and hits the lock - the browser tab will already have been changed
 * though, so the Swing events will be operating on the previously visible tab.
 * With that in mind, our best bet is to just enforce a policy within the Swing handlers that even if our reference to the active browser service is out of date, it should at
 * least be consistent in referencing the same object within the same call. Hence the use of the local variable copies within the handlers.
 *
 * - The handleHistorySearch method will not correctly handle the first keystroke of 'replaced' text strings in the URL combo box, ie. when the user types over an existing
 *   selection. This is due to replacement events being fired as two consecutive events (removeUpdate, insertUpdate) without an intervening keystroke, and the
 *   isEditingURLFilter flag is reset after the first event. The outcome isn't too bad though, more of a delay in filtering the items once the user types
 *   the 2nd character.
 *
 * - Regarding the custom handling of the URL entry combo box, for the purpose of filtering over the session history:
 *
 * Previously I was using the regular ActionListener on the combo box to fire off events, however this proved to be very messy for reasons outlined below.
 * I'm retaining these comments as a reference for why the previous method of handling events was a poor one.
 * The newer implementation manually handles both the key stroke actions on the combo box (consuming the events to prevent the regular handling),
 * as well as 'firing' off the list item URL selection event via the GraftableComboBox's ComboBoxIndexSelectionListener interface rather than using ActionListener.
 *
 * *** Outdated comments regarding previous implementation, kept for reference: ***
 *
 * - Regarding the prevention of multiple spurious ActionEvents being fired from the URL combo box, both when items are selected (or Enter is pressed) as normal, or
 *      the focus is lost:
 *
 * Swing's combo box is overly zealous in firing off ActionEvents. We're using a modded combo box with a history list and contents changing every keystroke which doesn't
 *    help matters, but it turns out that even a vanilla Swing combo box will fire off action events even under some peculiar circumstances, eg. an editable combo
 *    losing focus after the value has changed will immediately fire off two action events. We not only have to contend with the unwanted action events due to the
 *    focus lost events, but also when:
 *
 *    - The user cancels their URL edit and we wish to reinstate the original URL.
 *    - A page load has completed and we need to update the displayed URL and also the starting history list, to be aligned with the new base URL.
 *    - The user changes tabs and we have to do the same as above.
 *    - The user is arrowing through the URL dropdown, without even hitting Enter or clicking an item.
 *    - (Technically triggered by another lost focus) The combo box loses focus while the app is shutting down.
 *
 * My previous approach was to set a couple of flags, basically indicating to our action event method when to process the event and when to ignore it. This proved to be
 *    fairly problematic and unreliable however, since it's difficult to know how many redundant action events will be fired off under any given circumstance. So I've
 *    gone with a very different approach and used a timer flag, lastActionEventTime, to control the flow of incoming action events. Essentially the flag will only allow
 *    one action event every time window, equal to RepeatedActionEventsIgnoreWindowMilliseconds. As bodgy as it sounds it seems to work really well for all cases,
 *    at least when the time window is set to 100ms on my machine. And although the method is crude, the code involved is clean and simple - wherever the lastActionEventTime
 *    flag is reset, we know that we are suppressing unwanted action events which are otherwise sure to follow.
 *
 * *** End outdated comments. ***
 */


package com.feedbactory.client.ui.browser;


import com.feedbactory.client.core.useraccount.AccountEventAdapter;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.client.core.useraccount.FeedbactoryUserAccount;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.browser.event.BrowserLoadProgressEvent;
import com.feedbactory.client.ui.browser.event.BrowserLoadProgressListener;
import com.feedbactory.client.ui.browser.event.BrowserLocationEvent;
import com.feedbactory.client.ui.browser.event.BrowserLocationListener;
import com.feedbactory.client.ui.browser.event.BrowserTabEvent;
import com.feedbactory.client.ui.browser.event.BrowserTabListener;
import com.feedbactory.client.ui.browser.feedback.FeedbackWebsiteURL;
import com.feedbactory.client.ui.component.ShadedButton;
import com.feedbactory.client.ui.component.graftable.GraftableBouncingTextLabel;
import com.feedbactory.client.ui.component.graftable.GraftableComboBox;
import com.feedbactory.client.ui.component.graftable.GraftableComponentPeer;
import com.feedbactory.client.ui.component.graftable.GraftableComponentSwingFramework;
import com.feedbactory.client.ui.component.graftable.GraftableLabel;
import com.feedbactory.client.ui.component.graftable.GraftablePanel;
import com.feedbactory.client.ui.component.graftable.GraftableSeparator;
import com.feedbactory.client.ui.component.graftable.GraftableShadedButton;
import com.feedbactory.client.ui.component.graftable.GraftableSmileyProgressBar;
import com.feedbactory.client.ui.feedback.FeedbackCategoryUIManager;
import com.feedbactory.client.ui.feedback.FeedbackCategoryUIRegistry;
import com.feedbactory.client.ui.feedback.FeedbackUIEventListener;
import com.feedbactory.client.ui.feedback.FeedbackUIManager;
import com.feedbactory.client.ui.pad.FeedbactoryPadUIView;
import com.feedbactory.client.ui.useraccount.AccountUIManager;
import com.feedbactory.client.ui.useraccount.AccountUIStatus;
import com.feedbactory.client.ui.useraccount.AccountUIStatusListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.LayoutStyle;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.eclipse.swt.SWT;


final class BrowserControlPanel
{
   static final private int ControlPanelVerticalGapSize = 9;
   static final int ControlPanelHeight = BrowserResources.BrowserButtonIconSize.height + (2 * ShadedButton.VerticalImageSpacing) + (2 * ControlPanelVerticalGapSize);

   static final private int ButtonGroupIndentSize = 10;
   static final private int RelatedComponentGapSize = 3;
   static final private int UnrelatedComponentGapSize = 20;

   static final private int URLEntryComboBoxHeight = 27;

   /* The position of the URL combo box needs to be available to the main browser window, since the pop-up peer that it owns must appear in line with it.
    * The position is:
    *
    * Horizontal: The button group indent size, plus the width of four buttons (including the left & right image margins for each), plus the three small gaps between
    *    the buttons, plus one larger gap on the left of the URL combo box, plus the combo box's popup menu offset from the base combo box (1 pixel!).
    *
    * Vertical: The vertical midpoint of the control panel minus half the height of the control panel (to get the URL combo box upper draw position), plus of course the height
    *    of the combo box. Depending on how the graftable group is laid out (see below), if a rigid vertical gap was used for the combo box then this could also be used
    *    here as the y offset for the pop-up.
    */
   static final Point URLEntryComboBoxPopupOffset = new Point(ButtonGroupIndentSize + (4 * (BrowserResources.BrowserButtonIconSize.width + (2 * ShadedButton.HorizontalSpacing))) +
                                                             (3 * RelatedComponentGapSize) + UnrelatedComponentGapSize + 1,
                                                             ((ControlPanelHeight - URLEntryComboBoxHeight) / 2) + URLEntryComboBoxHeight);

   final private FeedbactoryBrowserWindow browserWindow;

   final private BrowserUIManagerService browserManagerService;

   final private FeedbactoryPadUIView feedbactoryPad;

   final private GraftablePanel delegatePanel = new GraftablePanel();

   final private GraftableShadedButton backButton = new GraftableShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private GraftableShadedButton forwardButton = new GraftableShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private GraftableShadedButton homeButton = new GraftableShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private GraftableShadedButton stopAndRefreshButton = new GraftableShadedButton(UIConstants.TitleBarButtonCornerRadius);

   final private GraftableShadedButton feedbackButton = new GraftableShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private GraftableSeparator leftFeedbackSeparator = new GraftableSeparator();
   final private GraftableBouncingTextLabel feedbackItemLabel = new GraftableBouncingTextLabel();
   final private GraftableLabel feedbackItemRatingLabel = new GraftableLabel();
   final private GraftableBouncingTextLabel feedbackItemRatingResultLabel = new GraftableBouncingTextLabel();
   final private GraftableBouncingTextLabel feedbackItemNumberOfRatingsLabel = new GraftableBouncingTextLabel();
   final private GraftableSeparator rightFeedbackSeparator = new GraftableSeparator();

   final private GraftableShadedButton accountButton = new GraftableShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private GraftableSmileyProgressBar accountBusyProgressBar = new GraftableSmileyProgressBar();
   final private GraftableShadedButton settingsButton = new GraftableShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private GraftableShadedButton helpButton = new GraftableShadedButton(UIConstants.TitleBarButtonCornerRadius);

   private BufferedImage stopButtonImage;
   private BufferedImage refreshButtonImage;

   private boolean isStopButtonVisible;

   final private BrowserEventsHandler browserEventsHandler = new BrowserEventsHandler();

   final private BrowserHistory browserHistory = new BrowserHistory();
   final private BrowserHistoryComboBoxModel urlEntryComboBoxModel = new BrowserHistoryComboBoxModel(browserHistory);
   final private GraftableComboBox urlEntryComboBox;

   private boolean isEditingURLFilter;


   BrowserControlPanel(final AccountSessionManager userAccountManager,
                       final FeedbactoryBrowserWindow browserWindow, final BrowserUIManagerService browserManagerService,
                       final GraftableComponentSwingFramework graftableComponentSwingFramework,
                       final GraftableComponentPeer controlPanelPeer, final GraftableComponentPeer urlEntryComboBoxPopupPeer,
                       final AccountUIManager userAccountUIManager, final FeedbactoryPadUIView feedbactoryPad)
   {
      this.browserWindow = browserWindow;
      this.browserManagerService = browserManagerService;
      this.feedbactoryPad = feedbactoryPad;

      urlEntryComboBox = new GraftableComboBox(graftableComponentSwingFramework, urlEntryComboBoxModel);

      initialise(userAccountManager, graftableComponentSwingFramework, controlPanelPeer, urlEntryComboBoxPopupPeer, userAccountUIManager);
   }


   private void initialise(final AccountSessionManager userAccountManager,
                           final GraftableComponentSwingFramework graftableComponentSwingFramework,
                           final GraftableComponentPeer controlPanelPeer, final GraftableComponentPeer urlEntryComboBoxPopupPeer,
                           final AccountUIManager userAccountUIManager)
   {
      attachComponentsToPeer(controlPanelPeer, urlEntryComboBoxPopupPeer);

      initialiseControlButtons();

      initialiseURLEntryField();

      initialiseFeedbackItemDisplay();

      initialiseControlPanelLayout();

      graftableComponentSwingFramework.addTopLevelSwingComponent(delegatePanel);

      initialiseBrowserListener();

      initialiseFeedbactoryPadSwitchKeyListener();

      initialiseFeedbactoryUserAccountListeners(userAccountManager, userAccountUIManager);
   }


   private void attachComponentsToPeer(final GraftableComponentPeer controlPanelPeer, final GraftableComponentPeer urlEntryComboBoxPopupPeer)
   {
      backButton.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(backButton);

      forwardButton.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(forwardButton);

      stopAndRefreshButton.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(stopAndRefreshButton);

      homeButton.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(homeButton);

      urlEntryComboBox.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(urlEntryComboBox);

      leftFeedbackSeparator.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(leftFeedbackSeparator);

      feedbackButton.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(feedbackButton);

      feedbackItemLabel.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(feedbackItemLabel);

      feedbackItemRatingLabel.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(feedbackItemRatingLabel);

      feedbackItemRatingResultLabel.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(feedbackItemRatingResultLabel);

      feedbackItemNumberOfRatingsLabel.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(feedbackItemNumberOfRatingsLabel);

      rightFeedbackSeparator.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(rightFeedbackSeparator);

      accountButton.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(accountButton);

      settingsButton.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(settingsButton);

      helpButton.attachToPeer(delegatePanel);
      delegatePanel.attachComponent(helpButton);

      delegatePanel.attachToPeer(controlPanelPeer);
      controlPanelPeer.attachComponent(delegatePanel);

      urlEntryComboBox.getPopupMenu().attachToPeer(urlEntryComboBoxPopupPeer);
      urlEntryComboBoxPopupPeer.attachComponent(urlEntryComboBox.getPopupMenu());
   }


   private void initialiseControlButtons()
   {
      initialiseControlButtonImages();

      backButton.setEnabled(false);
      forwardButton.setEnabled(false);

      backButton.setToolTipText("Back");
      forwardButton.setToolTipText("Forward");
      stopAndRefreshButton.setToolTipText("Refresh");
      homeButton.setToolTipText("Home");

      feedbackButton.setFont(UIConstants.SmallFont);
      feedbackButton.setText("Feedback");
      feedbackButton.setToolTipText("View and submit anonymous feedback.");

      accountButton.setFont(UIConstants.SmallFont);
      accountButton.setText("Not Signed In");
      accountButton.setToolTipText("Manage your Feedbactory account.");

      accountBusyProgressBar.setBackground(UIConstants.ProgressBarShadingColour);

      settingsButton.setFont(UIConstants.SmallFont);
      settingsButton.setText("Settings");
      settingsButton.setToolTipText("Change Feedbactory settings.");

      helpButton.setFont(UIConstants.SmallFont);
      helpButton.setText("Help");
      helpButton.setToolTipText("Help");

      backButton.addActionListener(new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleBackButtonActionPerformed();
         }
      });

      forwardButton.addActionListener(new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleForwardButtonActionPerformed();
         }
      });

      homeButton.addActionListener(new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            showURL(FeedbactoryBrowserWindow.FeedbactoryWelcomePage);
         }
      });

      stopAndRefreshButton.addActionListener(new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleStopAndRefreshButtonActionPerformed();
         }
      });

      feedbackButton.addActionListener(new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleFeedbackButtonActionPerformed();
         }
      });

      accountButton.addActionListener(new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleFeedbactoryUserAccountButtonActionPerformed();
         }
      });

      settingsButton.addActionListener(new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleSettingsButtonActionPerformed();
         }
      });

      helpButton.addActionListener(new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleHelpButtonActionPerformed();
         }
      });
   }


   private void initialiseControlButtonImages()
   {
      try
      {
         stopButtonImage = initialiseScaledControlButtonImage(BrowserResources.StopButtonImagePath);
         refreshButtonImage = initialiseScaledControlButtonImage(BrowserResources.RefreshButtonImagePath);

         initialiseControlImageButton(backButton, initialiseScaledControlButtonImage(BrowserResources.BackButtonImagePath));
         initialiseControlImageButton(forwardButton, initialiseScaledControlButtonImage(BrowserResources.ForwardButtonImagePath));
         initialiseControlImageButton(stopAndRefreshButton, refreshButtonImage);
         initialiseControlImageButton(homeButton, initialiseScaledControlButtonImage(BrowserResources.HomeButtonImagePath));
         initialiseControlImageButton(feedbackButton, initialiseScaledControlButtonImage(UIConstants.ApplicationIconSmallPath, 19, 19));
         initialiseControlImageButton(accountButton, initialiseScaledControlButtonImage(BrowserResources.AccountButtonImagePath));
         initialiseControlImageButton(settingsButton, initialiseScaledControlButtonImage(BrowserResources.SettingsButtonImagePath));
         initialiseControlImageButton(helpButton, initialiseScaledControlButtonImage(BrowserResources.HelpButtonImagePath));
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }
   }


   private void initialiseControlImageButton(final GraftableShadedButton button, final BufferedImage image)
   {
      button.setImage(image);
      button.setMouseOverRadialGradientPaint(UIConstants.ControlPanelButtonMouseOverGradient);
      button.setPressedRadialGradientPaint(UIConstants.ControlPanelButtonPressedGradient);
      button.setAlertRadialGradientPaint(UIConstants.ControlPanelButtonAlertGradient);
   }


   private BufferedImage initialiseScaledControlButtonImage(final String imageSource) throws IOException
   {
      return initialiseScaledControlButtonImage(imageSource, BrowserResources.BrowserButtonIconSize.width, BrowserResources.BrowserButtonIconSize.height);
   }


   private BufferedImage initialiseScaledControlButtonImage(final String imageSource, final int iconWidth, final int iconHeight) throws IOException
   {
      final BufferedImage buttonImage = ImageIO.read(BrowserControlPanel.class.getResourceAsStream(imageSource));
      return UIUtilities.getScaledImage(buttonImage, iconWidth, iconHeight, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
   }


   private void initialiseURLEntryField()
   {
      urlEntryComboBox.setEditable(true);
      urlEntryComboBox.setRenderer(new URLEntryListCellRenderer());
      urlEntryComboBox.setMaximumRowCount(10);
      urlEntryComboBox.setFont(UIConstants.MediumFont);

      final JTextComponent urlEntryFieldEditorComponent = ((JTextComponent) urlEntryComboBox.getEditor().getEditorComponent());

      urlEntryFieldEditorComponent.addKeyListener(new KeyAdapter()
      {
         @Override
         final public void keyPressed(final KeyEvent keyEvent)
         {
            handleURLEntryComboBoxKeyPressed(keyEvent);
         }
      });

      urlEntryFieldEditorComponent.getDocument().addDocumentListener(new DocumentListener()
      {
         @Override
         final public void insertUpdate(final DocumentEvent documentEvent)
         {
            handleHistorySearch(true);
         }


         @Override
         final public void removeUpdate(final DocumentEvent documentEvent)
         {
            handleHistorySearch(false);
         }


         @Override
         final public void changedUpdate(final DocumentEvent documentEvent)
         {
            // NOP
         }
      });

      urlEntryComboBox.addIndexSelectionListener(new GraftableComboBox.ComboBoxIndexSelectionListener()
      {
         @Override
         final public void indexSelected(final GraftableComboBox comboBox, final int selectedIndex)
         {
            processURLEntryActionPerformed();
         }
      });

      urlEntryFieldEditorComponent.addFocusListener(new FocusAdapter()
      {
         @Override
         final public void focusGained(final FocusEvent focusEvent)
         {
            handleURLEntryFocusGained(urlEntryFieldEditorComponent);
         }


         @Override
         final public void focusLost(final FocusEvent focusEvent)
         {
            handleURLEntryFocusLost(urlEntryFieldEditorComponent);
         }
      });
   }


   private void initialiseFeedbackItemDisplay()
   {
      leftFeedbackSeparator.setOrientation(SwingConstants.VERTICAL);

      feedbackItemLabel.setFont(UIConstants.SmallFont);
      feedbackItemRatingLabel.setFont(UIConstants.SmallFont);
      feedbackItemRatingResultLabel.setFont(UIConstants.RegularBoldFont);
      feedbackItemNumberOfRatingsLabel.setFont(UIConstants.SmallFont);

      rightFeedbackSeparator.setOrientation(SwingConstants.VERTICAL);
   }


   private void initialiseControlPanelLayout()
   {
      delegatePanel.setDefaultFocusComponent(urlEntryComboBox);

      // Enforce a fixed display width for the account button, as its preferred width will change depending on the sign in status.
      final int accountButtonPreferredWidth = accountButton.getDelegateComponent().getPreferredSize().width;
      final int accountBusyProgressBarHorizontalOffset = (2 * ShadedButton.HorizontalSpacing) + BrowserResources.BrowserButtonIconSize.width;
      final int accountBusyProgressBarMaximumWidth = accountButton.getDelegateComponent().getPreferredSize().width - accountBusyProgressBarHorizontalOffset;

      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getDelegateComponent());
      delegatePanel.setLayout(panelLayout);

      // Allowing the url entry a flexible size will produce odd resizing effects when the feedback item labels are updated.
      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addGap(ButtonGroupIndentSize)
         .addComponent(backButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(RelatedComponentGapSize)
         .addComponent(forwardButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(RelatedComponentGapSize)
         .addComponent(stopAndRefreshButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(RelatedComponentGapSize)
         .addComponent(homeButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(UnrelatedComponentGapSize)
         .addComponent(urlEntryComboBox.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, 300, GroupLayout.PREFERRED_SIZE)
         .addGap(UnrelatedComponentGapSize)
         .addComponent(feedbackButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(UnrelatedComponentGapSize)
         .addComponent(leftFeedbackSeparator.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(10)
         .addComponent(feedbackItemLabel.getDelegateComponent(), 10, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(feedbackItemRatingLabel.getDelegateComponent(), 70, 70, GroupLayout.PREFERRED_SIZE)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(feedbackItemRatingResultLabel.getDelegateComponent(), 25, 25, GroupLayout.PREFERRED_SIZE)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(feedbackItemNumberOfRatingsLabel.getDelegateComponent(), 80, 80, GroupLayout.PREFERRED_SIZE)
         .addGap(10)
         .addComponent(rightFeedbackSeparator.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(UnrelatedComponentGapSize)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(accountButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, accountButtonPreferredWidth, GroupLayout.PREFERRED_SIZE)
            .addGroup(panelLayout.createSequentialGroup()
               .addGap(accountBusyProgressBarHorizontalOffset)
               .addComponent(accountBusyProgressBar.getDelegateComponent(), 0, 0, accountBusyProgressBarMaximumWidth)
            )
         )
         .addGap(RelatedComponentGapSize)
         .addComponent(settingsButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(RelatedComponentGapSize)
         .addComponent(helpButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(ButtonGroupIndentSize)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addGap(ControlPanelVerticalGapSize)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(backButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(forwardButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(stopAndRefreshButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(homeButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(urlEntryComboBox.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, URLEntryComboBoxHeight, GroupLayout.PREFERRED_SIZE)
            .addComponent(feedbackButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(leftFeedbackSeparator.getDelegateComponent())
            .addGroup(panelLayout.createSequentialGroup()
               .addGap(2) // Minor correction needed to align the baselines of these controls with the slightly larger font of feedbackItemRatingResultLabel.
               .addGroup(panelLayout.createParallelGroup()
                  .addComponent(feedbackItemLabel.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, 17, GroupLayout.PREFERRED_SIZE)
                  .addComponent(feedbackItemRatingLabel.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, 17, GroupLayout.PREFERRED_SIZE)
                  .addComponent(feedbackItemNumberOfRatingsLabel.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, 17, GroupLayout.PREFERRED_SIZE)
               )
            )
            .addComponent(feedbackItemRatingResultLabel.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, 17, GroupLayout.PREFERRED_SIZE)
            .addComponent(rightFeedbackSeparator.getDelegateComponent())
            .addComponent(accountButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(accountBusyProgressBar.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, 27, GroupLayout.PREFERRED_SIZE)
            .addComponent(settingsButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(helpButton.getDelegateComponent(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         )
         .addGap(ControlPanelVerticalGapSize)
      );
   }


   private void initialiseBrowserListener()
   {
      browserManagerService.addBrowserTabEventListener(browserEventsHandler);
   }


   private void initialiseFeedbactoryPadSwitchKeyListener()
   {
      /* This is the opposing trigger for the feature that temporarily hides the Feedbactory pad using the ALT key.
       * There is also (unfortunately) a key handler required (see below) for each browser page, since the SWT browser window may have the focus rather
       * than the control panel.
       */
      final InputMap inputMap = delegatePanel.getDelegateComponent().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      final ActionMap actionMap = delegatePanel.getDelegateComponent().getActionMap();

      // Unlike the key released Swing event, the key press event for modifiers such as alt does include the modifier key mask.
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ALT, KeyEvent.ALT_DOWN_MASK), "togglePadVisible");

      actionMap.put("togglePadVisible", new AbstractAction()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleHidePadHotkeyPressed();
         }
      });
   }


   private void initialiseFeedbactoryUserAccountListeners(final AccountSessionManager userAccountManager, final AccountUIManager userAccountUIManager)
   {
      userAccountManager.addUserAccountEventUIListener(new AccountEventAdapter()
      {
         @Override
         final public void signedInToUserAccount(final FeedbactoryUserAccount userAccount)
         {
            handleUserAccountSignedInStatusChanged(true);
         }


         @Override
         final public void signedOutOfUserAccount(final FeedbactoryUserAccount userAccount)
         {
            handleUserAccountSignedInStatusChanged(false);
         }
      });

      userAccountUIManager.addUserAccountActivityListener(new AccountUIStatusListener()
      {
         @Override
         final public void feedbactoryUserAccountUIStatusChanged(final AccountUIStatus uiStatus, final boolean isSignInActivity)
         {
            handleFeedbactoryUserAccountUIStatusChanged(uiStatus, isSignInActivity);
         }
      });
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handlePostInitialise(final FeedbackUIManager feedbackUI)
   {
      initialiseBrowserHistoryWithAvailableWebsites(feedbackUI.getFeedbackCategoryUIRegistry());

      initialiseFeedbackAlertListener(feedbackUI);
   }


   private void initialiseBrowserHistoryWithAvailableWebsites(final FeedbackCategoryUIRegistry feedbackCategoryUIRegistry)
   {
      for (final FeedbackCategoryUIManager serviceHandler : feedbackCategoryUIRegistry.getRegisteredUIHandlers())
      {
         final List<FeedbackWebsiteURL> categoryWebsiteURLs = serviceHandler.getWebsiteURLs();

         for (final FeedbackWebsiteURL websiteURL : categoryWebsiteURLs)
            browserHistory.addToHistory(websiteURL.serviceURL, websiteURL.serviceName);
      }

      urlEntryComboBoxModel.refreshFromBrowserHistory();
   }


   private void initialiseFeedbackAlertListener(final FeedbackUIManager feedbackUI)
   {
      feedbackUI.addFeedbackAlertListener(new FeedbackUIEventListener()
      {
         @Override
         final public void activeFeedbackItemUpdated(final String itemDisplayName)
         {
            handleActiveFeedbackItemUpdated(itemDisplayName);
         }


         @Override
         final public void activeFeedbackSummaryUpdated(final String feedbackSummaryLabel, final String feedbackSummary)
         {
            handleActiveFeedbackSummaryUpdated(feedbackSummaryLabel, feedbackSummary);
         }


         @Override
         final public void activeFeedbackNumberOfRatingsUpdated(final String numberOfRatings)
         {
            handleActiveFeedbackNumberOfRatingsUpdated(numberOfRatings);
         }


         @Override
         final public void alertActive()
         {
            feedbackButton.showButtonAlert();
         }


         @Override
         final public void alertCancelled()
         {
            feedbackButton.cancelButtonAlert();
         }
      });
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final private class URLEntryListCellRenderer extends JTextPane implements ListCellRenderer
   {
      static final private Color UrlTextColour = new Color(30, 144, 255);

      private int index;
      private boolean isSelected;

      final private MutableAttributeSet urlAttributes = new SimpleAttributeSet();


      private URLEntryListCellRenderer()
      {
         initialise();
      }


      private void initialise()
      {
         setFont(UIConstants.MediumFont);

         setBorder(BorderFactory.createEmptyBorder(4, 5, 4, 2));

         // HACK: Nimbus workaround for issue where setBackground(Color) is ignored (known bug for JTextPane), need this for selected rows to be painted.
         // The alternative is to mess with the Nimbus UIDefaults.
         setBackground(UIConstants.ClearColour);

         StyleConstants.setForeground(urlAttributes, UrlTextColour);
      }


      @Override
      final public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus)
      {
         this.index = index;
         this.isSelected = isSelected;

         final BrowserHistoryItem browserHistoryItem = (BrowserHistoryItem) value;

         final StringBuilder builder = new StringBuilder();
         builder.append(browserHistoryItem.getPageTitle());
         builder.append('\n');
         builder.append(browserHistoryItem.getPageURL());

         final String componentText = builder.toString();

         setText(componentText);

         final StyledDocument document = getStyledDocument();
         document.setCharacterAttributes(browserHistoryItem.getPageTitle().length() + 1, browserHistoryItem.getPageURL().length(), urlAttributes, false);

         /* Previously I was highlighting (bold and underlined) the combo box matching text within the cell,
          * by looping through the cell text searching for matches and updating the styled document's character attributes accordingly.
          * However this feature has the drawback that it will often result in wrapped text within the cell due to the larger bold font size,
          * which in turn results in the cell growing vertically when selected, the result of which is some visual messiness with the graftable
          * combo box list which assumes uniform cell heights.
          *
          * I don't think the highlighting feature adds much, or at least I wouldn't anticipate users regularly searching for keywords through
          * their previous URLs from that session(period), nor being bothered by the lack of highlighting. If I wanted I could retain the
          * underlining without the bold (which looks fine), but I think I'd rather do away with the extra text processing.
          */
         return this;
      }


      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         // No need to paint the background for non-selected rows - the white background of the list behind does the job.
         // See above for note regarding background colour bug with JTextPane in Nimbus, necessitating a manual paint.
         if (isSelected)
         {
            graphics.setColor(UIConstants.ListCellSelectionHighlightColour);
            graphics.fillRect(0, 0, getWidth(), getHeight());
         }
         else if ((index % 2) == 0)
         {
            graphics.setColor(UIConstants.ListCellStripeColour);
            graphics.fillRect(0, 0, getWidth(), getHeight());
         }

         super.paintComponent(graphics);

         // Don't paint the border for the root combo box display.
         if (index != -1)
         {
            graphics.setColor(UIConstants.ListCellBorderColour);
            graphics.drawLine(0, getHeight() - 1, getWidth() - 1, getHeight() - 1);
         }
      }


      // Methods overriden to prevent needless calls to RepaintManager - we're doing our own painting.
      @Override
      final public void repaint()
      {
      }


      @Override
      final public void repaint(final long milliseconds)
      {
      }


      @Override
      final public void repaint(final int x, final int y, final int width, final int height)
      {
      }


      @Override
      final public void repaint(final Rectangle repaintRectangle)
      {
      }


      @Override
      final public void repaint(final long milliseconds, final int x, final int y, final int width, final int height)
      {
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class BrowserEventsHandler implements BrowserLocationListener, BrowserLoadProgressListener, BrowserTabListener, org.eclipse.swt.events.KeyListener
   {
      final private Set<String> historyExcludedURLs = new HashSet<String>();
      final private Set<String> historyExcludedTitles = new HashSet<String>();   // HACK: Ugh... No 404 codes, so it comes to this...
      {
         historyExcludedURLs.add(BrowserEngineManager.BlankPageUrl);
         historyExcludedTitles.add("Internet Explorer cannot display the webpage");
      };

      volatile private BrowserService activeBrowserService;
      volatile private String activeBrowserServiceURL = "";


      /****************************************************************************
       *
       ***************************************************************************/


      private void handleBrowserPageLoadProgressChanged(final BrowserService browserService)
      {
         if (browserService == activeBrowserService)
         {
            SwingUtilities.invokeLater(new Runnable()
            {
               @Override
               final public void run()
               {
                  setStopButtonVisible(true);
               }
            });
         }
      }


      private void handleBrowserPageLoadCompleted(final BrowserService browserService)
      {
         updateBrowserHistory(browserService);

         if (browserService == activeBrowserService)
            refreshActiveBrowserControls(false);
      }


      private void updateBrowserHistory(final BrowserService browserService)
      {
         // Browser URL cannot be null, title may be..?
         final String browserURL = browserService.getURL();
         final String browserTabTitle = (browserService.getTitle() != null) ? browserService.getTitle() : "";

         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               if ((! historyExcludedURLs.contains(browserURL)) && (! historyExcludedTitles.contains(browserTabTitle)))
                  browserHistory.addToHistory(browserURL, browserTabTitle);
            }
         });
      }


      private void refreshActiveBrowserControls(final boolean isTabChange)
      {
         /* The local values stored here may be slightly out of date by the time that the invokeLater() has executed, but it's not such a big deal because
          * the up-to-date events should be fired almost immediately afterwards. Doing things this way prevents the need to hook -back- into the
          * SWT thread from the Swing thread on the isBackEnabled() and isForwardEnabled() calls.
          *
          * Updating the display URL to the browser's pending URL is purely a cosmetic thing, allowing the browser to show the loading URL
          * (if there is one) when it first opens rather than a blank until the page has completely loaded.
          */
         if (! activeBrowserService.getURL().equals(BrowserEngineManager.BlankPageUrl))
            activeBrowserServiceURL = activeBrowserService.getURL();
         else
            activeBrowserServiceURL = activeBrowserService.getPendingURL();

         final boolean isBackEnabled = activeBrowserService.isBackEnabled();
         final boolean isForwardEnabled = activeBrowserService.isForwardEnabled();
         final boolean isLoadingPage = activeBrowserService.isLoadingPage();
         final String urlStringToDisplay = activeBrowserServiceURL;

         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               backButton.setEnabled(isBackEnabled);
               forwardButton.setEnabled(isForwardEnabled);

               setStopButtonVisible(isLoadingPage);

               updateURLEntryComboBox(urlStringToDisplay, isTabChange);
            }
         });
      }


      private void updateURLEntryComboBox(final String urlStringToDisplay, final boolean isTabChange)
      {
         /* Refresh the url combo box if the user switches tabs, or if there is a page load but not if the user happens to be editing the
          * URL text (isEditingURLFilter will be true during this time).
          */
         if (isTabChange || (! isEditingURLFilter))
         {
            if (isTabChange)
            {
               // Close the url combo popup when switching tabs.
               urlEntryComboBox.setPopupVisible(false);
            }

            setURLEntryComboBoxSelectedItem(urlStringToDisplay);
         }
      }


      private void handleActiveBrowserTabChanged(final BrowserTabEvent browserTabEvent)
      {
         activeBrowserService = browserTabEvent.browserService;

         refreshActiveBrowserControls(true);

         if (activeBrowserService.getPendingURL().equals(BrowserEngineManager.BlankPageUrl))
            browserWindow.requestURLEntryFocus();
         else
            activeBrowserService.requestFocus();
      }


      private void handleKeyAction(final org.eclipse.swt.events.KeyEvent keyEvent)
      {
         // It's not so good having to use a key listener on each browser instance, just to provide this one handy feature.. It looks super dodgy.
         if (keyEvent.keyCode == SWT.ALT)
         {
            SwingUtilities.invokeLater(new Runnable()
            {
               @Override
               final public void run()
               {
                  handleHidePadHotkeyPressed();
               }
            });
         }
      }


      /****************************************************************************
       *
       ***************************************************************************/


      @Override
      final public boolean browserPageLocationChanging(final BrowserLocationEvent pageLocationChangingEvent)
      {
         /* As mentioned in the BrowserEngine class, this event is not a reliable indicator of whether or not a web page
          * is loading at the top page level, therefore it isn't used here.
          */
         return true;
      }


      @Override
      final public void browserPageLocationChanged(final BrowserLocationEvent pageChangedEvent)
      {
         // This event is fired when the new URL is known and maybe an initial page title, so update the UI to reflect the state.
         if (pageChangedEvent.isTopFrame)
            handleBrowserPageLoadCompleted(pageChangedEvent.browserService);
      }


      @Override
      final public void browserPageLoadProgressChanged(final BrowserLoadProgressEvent pageProgressChangedEvent)
      {
         handleBrowserPageLoadProgressChanged(pageProgressChangedEvent.browserService);
      }


      @Override
      final public void browserPageLoadCompleted(final BrowserLoadProgressEvent pageProgressChangedEvent)
      {
         /* This event should be fired last after a full page load has completed, hence any final change to the page's
          * title should be picked up here. If it has changed, it will be updated in the history list.
          */
         handleBrowserPageLoadCompleted(pageProgressChangedEvent.browserService);
      }


      @Override
      final public void newBrowserTabOpened(final BrowserTabEvent browserTabEvent)
      {
         browserTabEvent.browserService.addLocationListener(this);
         browserTabEvent.browserService.addLoadProgressListener(this);
         browserTabEvent.browserService.addKeyListener(this);
      }


      @Override
      final public void activeBrowserTabChanged(final BrowserTabEvent browserTabEvent)
      {
         handleActiveBrowserTabChanged(browserTabEvent);
      }


      @Override
      final public void browserTabClosed(final BrowserTabEvent browserTabEvent)
      {
         browserTabEvent.browserService.removeLocationListener(this);
         browserTabEvent.browserService.removeLoadProgressListener(this);
         browserTabEvent.browserService.removeKeyListener(this);
      }


      @Override
      final public void keyPressed(final org.eclipse.swt.events.KeyEvent keyEvent)
      {
         handleKeyAction(keyEvent);
      }


      @Override
      final public void keyReleased(final org.eclipse.swt.events.KeyEvent keyEvent)
      {
      }
   }


   /****************************************************************************
    *
    *
    * 
    ***************************************************************************/


   private void setURLEntryComboBoxSelectedItem(final String selectedItem)
   {
      if ((selectedItem == null) || selectedItem.trim().equals("") || selectedItem.equals(BrowserEngineManager.BlankPageUrl))
         urlEntryComboBoxModel.setSelectedItem("");
      else
         urlEntryComboBoxModel.setSelectedItem(selectedItem);

      /* This is more of a personal touch than strictly necessary. If not for this call, the combo box's text field retains its
       * 'scrolled to the right' position each time that the control is updated with a new value from the combo box, which can be
       * a bit annoying. Unfortunately setScrollOffset() doesn't appear to work here.
       */
      ((JTextComponent) urlEntryComboBox.getEditor().getEditorComponent()).setCaretPosition(0);
   }


   private void handleBackButtonActionPerformed()
   {
      final BrowserService browserService = browserEventsHandler.activeBrowserService;

      if (browserService != null)
      {
         browserService.back();
         forwardButton.setEnabled(true);
      }
   }


   private void handleForwardButtonActionPerformed()
   {
      final BrowserService browserService = browserEventsHandler.activeBrowserService;

      if (browserService != null)
      {
         browserService.forward();
         backButton.setEnabled(true);
      }
   }


   private void handleStopAndRefreshButtonActionPerformed()
   {
      if (isStopButtonVisible)
         handleStopButtonActionPerformed();
      else
         handleRefreshButtonActionPerformed();
   }


   private void handleStopButtonActionPerformed()
   {
      final BrowserService browserService = browserEventsHandler.activeBrowserService;

      if (browserService != null)
      {
         browserService.stop();

         /* HACK: Detecting the correct conditions for the pages being completely loaded is very fiddly for each browser let alone catering for all,
          * so our implementation plays it fairly conservative and as a consequence is often left believing that a page is still loading, displaying the stop
          * button all the while. So, as a bodgy failsafe we revert the stop button to the refresh button, at least of course until the next page load event is
          * received.
          */
         setStopButtonVisible(false);
      }
   }


   private void handleRefreshButtonActionPerformed()
   {
      final BrowserService browserService = browserEventsHandler.activeBrowserService;

      if (browserService != null)
         browserService.refresh();
   }


   private void setStopButtonVisible(final boolean isStopButtonVisible)
   {
      if (this.isStopButtonVisible == isStopButtonVisible)
         return;

      if (isStopButtonVisible)
      {
         stopAndRefreshButton.setImage(stopButtonImage);
         stopAndRefreshButton.setToolTipText("Stop");
      }
      else
      {
         stopAndRefreshButton.setImage(refreshButtonImage);
         stopAndRefreshButton.setToolTipText("Refresh");
      }

      stopAndRefreshButton.repaint();

      this.isStopButtonVisible = isStopButtonVisible;
   }


   private void handleFeedbackButtonActionPerformed()
   {
      // Hide the feedback pad (toggle visibility) if the feedback panel is already visible.
      // Previously I was checking the ActionEvent's alert flag, but I don't know whether this is really needed now.
      if (feedbactoryPad.isVisible())
         feedbactoryPad.setVisible(false);
      else
         feedbactoryPad.setVisible(true);
   }


   private void handleFeedbactoryUserAccountButtonActionPerformed()
   {
      feedbactoryPad.showAccountPanel();
      feedbactoryPad.setVisible(true);
   }


   private void handleSettingsButtonActionPerformed()
   {
      feedbactoryPad.showSettingsPanel();
      feedbactoryPad.setVisible(true);
   }


   private void handleHelpButtonActionPerformed()
   {
      feedbactoryPad.showHelpPanel();
      feedbactoryPad.setVisible(true);
   }


   private void handleURLEntryComboBoxKeyPressed(final KeyEvent keyEvent)
   {
      isEditingURLFilter = false;

      final int keyCode = keyEvent.getKeyCode();

      if (keyCode == KeyEvent.VK_UP)
         processURLEntryUpKeyPressed(keyEvent);
      else if (keyCode == KeyEvent.VK_DOWN)
         processURLEntryDownKeyPressed(keyEvent);
      else if (keyCode == KeyEvent.VK_PAGE_UP)
         processURLEntryPageUpKeyPressed(keyEvent);
      else if (keyCode == KeyEvent.VK_PAGE_DOWN)
         processURLEntryPageDownKeyPressed(keyEvent);
      else if (keyCode == KeyEvent.VK_ENTER)
         processURLEntryEnterKeyPressed(keyEvent);
      else if (keyCode == KeyEvent.VK_ESCAPE)
         processURLEntryEscapeKeyPressed(keyEvent);
      else // User is probably editing text.
         isEditingURLFilter = true;
   }


   private void processURLEntryUpKeyPressed(final KeyEvent keyEvent)
   {
      if (urlEntryComboBox.isPopupVisible())
      {
         final JList popUpList = ((ComboPopup) urlEntryComboBox.getPopupMenu().getDelegateComponent()).getList();
         if (popUpList.getSelectedIndex() > 0)
         {
            popUpList.setSelectedIndex(popUpList.getSelectedIndex() - 1);
            urlEntryComboBox.getEditor().setItem(popUpList.getSelectedValue());
         }
      }
      else
         urlEntryComboBox.setPopupVisible(true);

      keyEvent.consume();
   }


   private void processURLEntryDownKeyPressed(final KeyEvent keyEvent)
   {
      if (urlEntryComboBox.isPopupVisible())
      {
         final JList popUpList = ((ComboPopup) urlEntryComboBox.getPopupMenu().getDelegateComponent()).getList();
         if (popUpList.getSelectedIndex() < (popUpList.getModel().getSize() - 1))
         {
            popUpList.setSelectedIndex(popUpList.getSelectedIndex() + 1);
            urlEntryComboBox.getEditor().setItem(popUpList.getSelectedValue());
         }
      }
      else
         urlEntryComboBox.setPopupVisible(true);

      keyEvent.consume();
   }


   private void processURLEntryPageUpKeyPressed(final KeyEvent keyEvent)
   {
      if (urlEntryComboBox.isPopupVisible())
      {
         final JList popUpList = ((ComboPopup) urlEntryComboBox.getPopupMenu().getDelegateComponent()).getList();
         if (popUpList.getSelectedIndex() > 0)
         {
            int newListIndex = popUpList.getSelectedIndex() - (urlEntryComboBox.getMaximumRowCount() - 1);
            if (newListIndex < 0)
               newListIndex = 0;

            popUpList.setSelectedIndex(newListIndex);
            urlEntryComboBox.getEditor().setItem(popUpList.getSelectedValue());
         }
      }
      else
         urlEntryComboBox.setPopupVisible(true);

      keyEvent.consume();
   }


   private void processURLEntryPageDownKeyPressed(final KeyEvent keyEvent)
   {
      if (urlEntryComboBox.isPopupVisible())
      {
         final JList popUpList = ((ComboPopup) urlEntryComboBox.getPopupMenu().getDelegateComponent()).getList();
         final int maximumListIndex = (popUpList.getModel().getSize() - 1);

         if (popUpList.getSelectedIndex() < maximumListIndex)
         {
            int newListIndex = popUpList.getSelectedIndex() + (urlEntryComboBox.getMaximumRowCount() - 1);
            if (newListIndex > maximumListIndex)
               newListIndex = maximumListIndex;

            popUpList.setSelectedIndex(newListIndex);
            urlEntryComboBox.getEditor().setItem(popUpList.getSelectedValue());
         }
      }
      else
         urlEntryComboBox.setPopupVisible(true);

      keyEvent.consume();
   }


   private void processURLEntryEnterKeyPressed(final KeyEvent keyEvent)
   {
      processURLEntryActionPerformed();
      keyEvent.consume();
   }


   private void processURLEntryActionPerformed()
   {
      String urlEntered = urlEntryComboBox.getEditor().getItem().toString().trim();

      if (urlEntered.isEmpty())
         urlEntered = BrowserEngineManager.BlankPageUrl;

      showURL(urlEntered);
   }


   private void handleURLEntryFocusGained(final JTextComponent urlEntryFieldEditorComponent)
   {
      // Select from the end of the text field to the start, so that long URLs are displayed from their beginning.
      urlEntryFieldEditorComponent.setCaretPosition(urlEntryFieldEditorComponent.getText().length());
      urlEntryFieldEditorComponent.moveCaretPosition(0);
   }


   private void handleURLEntryFocusLost(final JTextComponent urlEntryFieldEditorComponent)
   {
      /* Reset the editing flag to false as soon as the focus is lost, to prevent the possibility of firing further handleHistorySearch calls
       * if/when the combo box text is changed by means other than the user, ie. page loads. If the isEditingURLFilter is still true (eg. from
       * the user last pressing an editing key or even a home/end arrow key) this will ultimately lead to the setPopupVisible(true)
       * being called.
       */
      isEditingURLFilter = false;

      // Clear the filter that may be applied to the model.
      urlEntryComboBoxModel.refreshFromBrowserHistory();

      // Reset the combo box text editor to the non-scrolled position.
      urlEntryFieldEditorComponent.setCaretPosition(0);
   }


   private void processURLEntryEscapeKeyPressed(final KeyEvent keyEvent)
   {
      // The user has cancelled the URL edit, so revert it to the browser's active URL.
      String activeBrowserServiceURL = browserEventsHandler.activeBrowserServiceURL;
      if (activeBrowserServiceURL.equals(BrowserEngineManager.BlankPageUrl))
         activeBrowserServiceURL = "";

      urlEntryComboBox.getEditor().setItem(activeBrowserServiceURL);

      if (urlEntryComboBox.isPopupVisible())
         urlEntryComboBox.setPopupVisible(false);

      keyEvent.consume();
   }


   private void handleHistorySearch(final boolean isNarrowingSearch)
   {
      if (isEditingURLFilter)
      {
         isEditingURLFilter = false;

         urlEntryComboBox.setTextEntryMode(true);
         final JTextComponent textComponent = (JTextComponent) urlEntryComboBox.getEditor().getEditorComponent();
         final boolean haveContentsChanged = urlEntryComboBoxModel.applyFilter(textComponent.getText().trim(), isNarrowingSearch);
         urlEntryComboBox.setTextEntryMode(false);

         if (haveContentsChanged)
         {
            // Set the graftable Swing portion of the combo box to invisible, to allow it to resize when it next becomes visible.
            // Meanwhile the SWT peer remains visible, so that it's not so jarring for the user.
            urlEntryComboBox.setPopupVisible(false, false);
         }

         urlEntryComboBox.setPopupVisible(true);
      }
   }


   private void showURL(final String url)
   {
      final BrowserService browserService = browserEventsHandler.activeBrowserService;

      if (browserService != null)
      {
         browserService.openURL(url);
         backButton.setEnabled(true);
         forwardButton.setEnabled(false);

         browserService.requestFocus();
      }
   }


   private void handleHidePadHotkeyPressed()
   {
      feedbactoryPad.setVisible(! feedbactoryPad.isVisible());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleUserAccountSignedInStatusChanged(final boolean isSignedIn)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            accountButton.setText(isSignedIn ? "Signed In" : "Not Signed In");
            accountButton.repaint();
         }
      });
   }


   private void handleFeedbactoryUserAccountUIStatusChanged(final AccountUIStatus uiStatus, final boolean isSignInActivity)
   {
      final boolean isUserAccountActivityActive = (uiStatus == AccountUIStatus.Busy);
      final boolean controlPanelAlertActive = accountBusyProgressBar.isIndeterminate();

      if ((! isSignInActivity) || (isUserAccountActivityActive == controlPanelAlertActive))
         return;

      if (isUserAccountActivityActive)
      {
         accountButton.setEnabled(false);

         accountBusyProgressBar.attachToPeer(delegatePanel);
         delegatePanel.attachComponent(accountBusyProgressBar);

         accountBusyProgressBar.setIndeterminate(true);
      }
      else
      {
         accountBusyProgressBar.setIndeterminate(false);

         delegatePanel.detachComponent(accountBusyProgressBar);
         accountBusyProgressBar.detachFromPeer();

         accountButton.setEnabled(true);
         accountButton.repaint();
      }
   }


   private void handleActiveFeedbackItemUpdated(final String itemDisplayName)
   {
      feedbackItemLabel.showAnimatedText(itemDisplayName);
   }


   private void handleActiveFeedbackSummaryUpdated(final String feedbackSummaryLabel, final String feedbackSummary)
   {
      feedbackItemRatingLabel.setText(feedbackSummaryLabel);
      feedbackItemRatingResultLabel.showAnimatedText(feedbackSummary);
   }


   private void handleActiveFeedbackNumberOfRatingsUpdated(final String numberOfRatings)
   {
      feedbackItemNumberOfRatingsLabel.showAnimatedText(numberOfRatings);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handlePreDispose(final GraftableComponentPeer controlPanelPeer, final GraftableComponentPeer urlEntryComboBoxPopupPeer)
   {
      /* Previously it had been possible for residual SWT browser events to fire and trigger update UI events (eg. refreshActiveBrowserControls()) as the app was
       * shutting down. A call such as applyFilter() within refreshActiveBrowserControls() would result in the graftable popup box UI being updated after
       * it had been detached from its SWT peer... NullPointerException.
       *
       * But now with all of the browser events being disconnected during the first SWT phase of the shutdown (see notes in FeedbactoryBrowserWindow),
       * and with all of the flow of SWT events to Swing peers being disconnected here (also during the first SWT phase of the shutdown), I don't
       * think it's possible for residual browser events to cause events on a disposed UI; either the listeners have already been disconnected, in which case
       * a method such as refreshActiveBrowserControls() will never be called even once the web page manages to finish loading, or the sequence of events submitted
       * to both SWT and Swing will all play out before the synchronous shutdown phases have reached the point of disposing objects, as described in
       * FeedbactoryBrowserWindow. The logic can be tricky to follow, eg. would it matter if a Swing call such as urlEntryComboBox.setPopupVisible(false) pushed its
       * SWT visibility request asynchronously instead of synchronously? But, carefully going through the thread logic in step with the synchronous shutdown steps should
       * prove that the 2nd phase shutdown methods will always run after all of the other events have been submitted and completed.
       *
       * When analysing the races between the shutdown and potentially problematic competing tasks, you need to ask: What is the last point in time that this task
       * could possibly be run? Could it execute after SWT disposal?
       */
      delegatePanel.preDispose();

      detachComponentsFromControlPanel();

      // Detach top-level control panel and url entry pop-up Swing components from SWT peers.
      controlPanelPeer.detachComponent(delegatePanel);
      urlEntryComboBoxPopupPeer.detachComponent(urlEntryComboBox.getPopupMenu());
   }


   private void detachComponentsFromControlPanel()
   {
      delegatePanel.detachComponent(backButton);
      delegatePanel.detachComponent(forwardButton);
      delegatePanel.detachComponent(stopAndRefreshButton);
      delegatePanel.detachComponent(homeButton);

      delegatePanel.detachComponent(urlEntryComboBox);

      delegatePanel.detachComponent(feedbackButton);
      delegatePanel.detachComponent(leftFeedbackSeparator);
      delegatePanel.detachComponent(feedbackItemLabel);
      delegatePanel.detachComponent(feedbackItemRatingLabel);
      delegatePanel.detachComponent(feedbackItemRatingResultLabel);
      delegatePanel.detachComponent(feedbackItemNumberOfRatingsLabel);

      delegatePanel.detachComponent(accountButton);

      if (accountBusyProgressBar.isIndeterminate())
      {
         accountBusyProgressBar.setIndeterminate(false);
         delegatePanel.detachComponent(accountBusyProgressBar);
      }

      delegatePanel.detachComponent(settingsButton);

      delegatePanel.detachComponent(helpButton);
   }


   private void handleDispose()
   {
      delegatePanel.detachFromPeer();

      backButton.detachFromPeer();
      forwardButton.detachFromPeer();
      stopAndRefreshButton.detachFromPeer();
      homeButton.detachFromPeer();

      urlEntryComboBox.detachFromPeer();
      urlEntryComboBox.getPopupMenu().detachFromPeer();

      feedbackButton.detachFromPeer();
      leftFeedbackSeparator.detachFromPeer();
      feedbackItemLabel.detachFromPeer();
      feedbackItemRatingLabel.detachFromPeer();
      feedbackItemRatingResultLabel.detachFromPeer();
      feedbackItemNumberOfRatingsLabel.detachFromPeer();

      accountButton.detachFromPeer();
      accountBusyProgressBar.detachFromPeer();

      settingsButton.detachFromPeer();

      helpButton.detachFromPeer();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void postInitialise(final FeedbackUIManager feedbackUI)
   {
      handlePostInitialise(feedbackUI);
   }


   final void preDispose(final GraftableComponentPeer controlPanelPeer, final GraftableComponentPeer urlEntryComboBoxPopupPeer)
   {
      handlePreDispose(controlPanelPeer, urlEntryComboBoxPopupPeer);
   }


   final void dispose()
   {
      handleDispose();
   }
}