/* Memos:
 *
 * - This class utilises a MessageDialogDisplayManager to display a combination of regular panels as well as 'locking' panels/components, which are displayed on top of regular
 *   panels, effectively locking them out of input much like modal dialogs until the top level component has been dismissed. Multiple locking components can be stacked and
 *   MessageDialogDisplayManager defines a set of component priorities that can be used to determine the display order. When there are no 'locking' components being shown,
 *   the MessageDialogDisplayManager is simply unlocked and whatever regular panel is attached to the frame's content area will be displayed.
 *
 * - The priorities are intended to be used carefully by the callers, components such as message dialogs or nested subcomponents being assigned as minimal priority as
 *   possible. Only the UIManager (parent) should be placing 'Applicationxxxx' priority components. Other callers should be using the 'Formxxx' priorities. This ensures
 *   that critical alerts etc will be pushed to the top of the display stack. Note that the FeedbactoryPadClientUIView interface helps funnel display priorities to
 *   the 'Formxxx' set of priorities; callers having only a reference to that interface cannot provide arbitrary display priorities.
 *
 * - To a great extent this class manually manages the resizing of the frame to accommodate its preferred size as components are added to and dismissed from the
 *   MessageDialogDisplayManager. Mostly this is straightforward, but there are some conditions to keep in mind:
 *
 *   - The pad will update its size to a new preferred size when a new locking component is added only if there is not already enough space to display the locking component.
 *     The reasoning is that locking components are displayed as centered sub-components on a blurred background, and it's fine to display for example a very small message
 *     dialog against a large blurred background.
 *
 *   - The same rule applies when a locking component is removed, only to reveal another (lower priority) locking component beneath.
 *
 *   - When the final locking component is removed - unlocking the MessageDialogDisplayManager's regular base component - the pad is always reset to its preferred size,
 *     whether that be smaller or larger than the current size.
 *
 *   - With the above rules in mind, there are good and bad ways for callers to manage the display of their subforms. For instance, the user account manager UI displays
 *     a few different forms of the same priority at different times, replacing one form for another. Unless it wants this pad to force a resize every time, it must
 *     avoid the MessageDialogDisplayManager jumping between unlocked and locked states for every panel swap. So, the order of calls becomes important and it places a new
 *     locking subcomponent first before dismissing the old locking one, which ensures that the MessageDialogDisplayManager never transitions to the 'unlocked' state
 *     in between.
 *
 *   - The only component size that matters to the MessageDialogDisplayManager is that of the actively showing component, whether that be the regular base-level component
 *     or a locking component. So if the preferred size of the regular base-level component changes while the display is in the 'locked' state, no resize will be
 *     performed until that component is once again showing. For one thing it would be jarring to the user to have the panel suddenly change in size for
 *     apparently no reason while the panel is still locked and the visible top level component hasn't changed size. This rule comes into play when the user for example
 *     clicks the toolbar icon to switch from the Feedback panel to the Help panel, while a message dialog or user account prompt is being displayed.
 *
 *   - This deferred resizing strategy has implications if a caller requests that the pad be resized to a new preferred size; it may not immediately happen.
 *
 *   - The resize is also deferred if the pad is not actually visible.
 *
 *   - Callers (client panels) should rarely need to manually request a resize. It is appropriate however when only the caller/client can reasonably know that a panel
 *     needs to be resized, eg. the More/Less button in personal feedback.
 *
 * - See the handling of the More/Less button on changed feedback in PersonalFeedbackRootPanel for more comments on how correctly handling the deferred resizing can
 *   be tricky.
 *
 * - The locking components on the MessageDialogDisplayManager can be either temporary or persistent. If they're temporary, they will be dismissed when the pad is hidden,
 *   otherwise they'll stay put - a good idea for important message dialogs such as error notifications etc, but also for things such as prompts for email confirmation
 *   codes; it would be annoying for the user to switch programs to find their email confirmation code, and then switch back to Feedbactory only to find that the code prompt
 *   has disappeared. There's a method in this class (dismissLockingComponents()), triggered when the pad is hidden, that pushes requests to dismiss temporary UI
 *   components. The requests are made to the UI sub-managers (eg. account manager, settings, help) as well as directly to the pad's MessageDialogDisplayManager.
 *   Originally this was taken care of automatically by the MessageDialogDisplayManager using a HierarchyListener and the HierarchyEvent.SHOWING_CHANGED flag, however
 *   as noted in the memo for that class, it would be erroneously triggered when the pad performs an animated resize.
 *
 * - Also related to dismissing the temporary components: some of the logic for deciding whether to hide or persist a component may be non-trivial, hence the
 *   delegations to the UI sub-managers to decide which components to dismiss or keep. The account UI manager and its account details panel is a good example; normally
 *   that panel can be automatically dismissed when the pad is hidden, however not when one of its sub-panels is also open, eg. change email address.
 *
 * - In JRE7, the focus is a little uncooperative in jumping from the browser window to the pad control when the pad first becomes visible, eg. when the user presses the
 *   feedback, help, or account toolbar buttons. Actually this only appears to happen (no focused control on the pad) when the focus is trying to shift from the
 *   browser's URL entry field to the pad. Usually if the focus is on the browser window it will be within the rendering area as the user scrolls with the mouse,
 *   so the focus misfire in JRE7 is not such a problem.
 */

package com.feedbactory.client.ui.pad;


import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.component.TranslucencyUtilities;
import com.feedbactory.client.ui.component.ImageLoader;
import com.feedbactory.client.ui.help.HelpUIManager;
import com.feedbactory.client.core.FeedbactoryClientConstants;
import com.feedbactory.client.ui.useraccount.AccountUIStatusListener;
import com.feedbactory.client.core.useraccount.FeedbactoryUserAccount;
import com.feedbactory.client.core.useraccount.AccountEventAdapter;
import com.feedbactory.client.core.useraccount.AccountMessageListener;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.client.ui.*;
import com.feedbactory.client.ui.UIManager;
import com.feedbactory.client.ui.component.*;
import com.feedbactory.client.ui.component.MessageDialog.MessageType;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionConfiguration;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionSelection;
import com.feedbactory.client.ui.component.MessageDialogDisplayManager.ComponentDisplayPriority;
import com.feedbactory.client.ui.console.BrowserConsoleUIManager;
import com.feedbactory.client.ui.feedback.FeedbackMenuItem;
import com.feedbactory.client.ui.useraccount.AccountUIStatus;
import com.feedbactory.client.ui.useraccount.AccountUIManager;
import com.feedbactory.client.ui.feedback.FeedbackUIManager;
import com.feedbactory.client.ui.settings.SettingsUIManager;
import com.feedbactory.shared.Message;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.*;


final public class FeedbactoryPad
{
   static final public String WindowLocationPreferencesKey = "FeedbactoryPadBrowserOffset";
   static final public Point DefaultBrowserOffset = new Point(350, 200);

   final private UIManager uiManager;

   final private SwingNimbusFrame delegateFrame = new SwingNimbusFrame();
   final private FrameResizeAnimator frameResizeAnimator = new FrameResizeAnimator(delegateFrame);
   final private Dimension frameDecorationSize;
   private boolean isFrameOpaque = true;
   private boolean isRepackPending = true;

   final private MessageDialogDisplayManager delegatePanel;

   final private BrowserConsoleUIManager browserConsoleUIManager;

   final private SettingsUIManager settingsUIManager;

   final private FeedbackUIManager feedbackUIManager;

   final private AccountUIManager userAccountUIManager;

   final private HelpUIManager helpUIManager;

   final private SlidingMenuBar feedbackMenu = new SlidingMenuBar(UIConstants.LighterPanelGradient);
   final private ShadedButton feedbackButton = new ShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private ShadedButton feedbackFromMeButton = new ShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private ShadedButton browserConsoleButton = new ShadedButton(UIConstants.TitleBarButtonCornerRadius);

   final private SlidingMenuBar optionsMenu = new SlidingMenuBar(UIConstants.LighterPanelGradient);
   final private ShadedButton optionsButton = new ShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private ShadedButton settingsButton = new ShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private ShadedButton accountButton = new ShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private ShadedButton signOutButton = new ShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private ShadedButton helpButton = new ShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private ShadedButton aboutButton = new ShadedButton(UIConstants.TitleBarButtonCornerRadius);

   private JComponent activeProgressBar;

   private ShutdownState shutdownState = ShutdownState.None;


   public FeedbactoryPad(final AccountSessionManager feedbactoryUserAccountManager,
                         final UIManager feedbactoryUIManager, final FeedbactoryPadUIView feedbactoryPadUIView,
                         final SettingsUIManager settingsUIManager, final AccountUIManager userAccountUIManager, final FeedbackUIManager feedbackUIManager)
   {
      this.uiManager = feedbactoryUIManager;
      this.browserConsoleUIManager = (FeedbactoryClientConstants.IsDevelopmentProfile ? new BrowserConsoleUIManager(feedbactoryPadUIView, uiManager.getBrowserManagerService()) : null);
      this.settingsUIManager = settingsUIManager;
      this.feedbackUIManager = feedbackUIManager;
      this.userAccountUIManager = userAccountUIManager;
      this.helpUIManager = new HelpUIManager(feedbactoryPadUIView, uiManager.getBrowserManagerService());

      delegatePanel = new MessageDialogDisplayManager(feedbackUIManager.getFeedbackPanel().getDelegate(), UIConstants.MediumContainerGapSize);

      initialise(feedbactoryUserAccountManager);

      // Initialise the frame decoration size after the frame has been initialised and packed to its preferred size.
      frameDecorationSize = calculateFrameDecorationSize();
   }


   private void initialise(final AccountSessionManager feedbactoryUserAccountManager)
   {
      initialiseFeedbactoryUserAccountListeners(feedbactoryUserAccountManager);

      initialiseDelegatePanel();

      initialiseDelegateFrame();
   }


   private void initialiseFeedbactoryUserAccountListeners(final AccountSessionManager feedbactoryUserAccountManager)
   {
      feedbactoryUserAccountManager.addUserAccountEventUIListener(new AccountEventAdapter()
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

      feedbactoryUserAccountManager.addUserAccountMessageListener(new AccountMessageListener()
      {
         @Override
         final public void userAccountMessageReceived(final Message message)
         {
            handleUserAccountMessageReceived(message);
         }
      });

      userAccountUIManager.addUserAccountActivityListener(new AccountUIStatusListener()
      {
         @Override
         final public void feedbactoryUserAccountUIStatusChanged(final AccountUIStatus uiStatus, final boolean isSignInActivity)
         {
            handleUserAccountUIStatusChanged(uiStatus);
         }
      });
   }


   private void initialiseDelegatePanel()
   {
      initialiseDelegatePanelDisplayListener();

      initialiseDelegatePanelHidePadHotkeyListener();
   }


   private void initialiseDelegatePanelDisplayListener()
   {
      delegatePanel.addDisplayListener(new MessageDialogDisplayManager.DisplayListener()
      {
         @Override
         final public void displayLocked()
         {
            handlePanelLocked();
         }


         @Override
         final public void displayUnlocked()
         {
            handlePanelUnlocked();
         }


         @Override
         final public void newLockingComponentDisplayed(final JComponent component, final ComponentDisplayPriority displayPriority)
         {
            handlePanelNewLockingComponentDisplayed(displayPriority);
         }


         @Override
         final public void lockingComponentDismissed(final JComponent component, final ComponentDisplayPriority displayPriority, final boolean wasBeingDisplayed)
         {
            handlePanelLockingComponentDismissed(wasBeingDisplayed);
         }
      });
   }


   private void initialiseDelegatePanelHidePadHotkeyListener()
   {
      final InputMap inputMap = delegatePanel.getRootComponent().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      final ActionMap actionMap = delegatePanel.getRootComponent().getActionMap();

      // Unlike the key released Swing event, the key press event for modifiers such as alt does include the modifier key mask.
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ALT, KeyEvent.ALT_DOWN_MASK), "hidePad");

      actionMap.put("hidePad", new AbstractAction()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleHidePadHotkeyPressed();
         }
      });
   }


   private void initialiseDelegateFrame()
   {
      delegateFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      delegateFrame.setWindowDecorationStyle(JRootPane.PLAIN_DIALOG);
      delegateFrame.setResizable(false);
      delegateFrame.setFrameTitle(UIConstants.ApplicationTitle, false);

      // Save some grief during debugging by allowing the pad window to be pushed to the background.
      if (! FeedbactoryClientConstants.IsDebugMode)
         delegateFrame.setAlwaysOnTop(true);

      try
      {
         final BufferedImage frameIconImage = ImageIO.read(getClass().getResourceAsStream(UIConstants.ApplicationIconLargePath));
         delegateFrame.setFrameIcon(frameIconImage, false);
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }

      initialiseFrameTitleBar();

      delegateFrame.setMenuComponents(new JComponent[] {feedbackButton}, new JComponent[] {feedbackMenu.getDelegate()},
                                      new JComponent[] {optionsButton}, new JComponent[] {optionsMenu.getDelegate()});

      delegateFrame.setContent(delegatePanel.getRootComponent());

      /* An unconditional pack() after initialisation ensures that even if the pad is in its locked state when first set visible
       * it will be at a displayable size. This situation is possible if for example there is a failure during initialisation and
       * an error message is displayed on the pad, placing it immediately in the locked state.
       *
       * The pack() also ensures that the calculation of the frame's border decoration size (required for the animation of the frame resize)
       * is performed after the frame has been initialised to its correct size.
       */
      delegateFrame.pack();

      restorePadLocationOffsetFromBrowserFrame();

      delegateFrame.addWindowListener(new WindowAdapter()
      {
         @Override
         final public void windowClosing(final WindowEvent windowEvent)
         {
            handlePadCloseButtonActionPerformed();
         }
      });

      if (TranslucencyUtilities.isWindowOpacityControlSupported())
      {
         delegateFrame.addWindowFocusListener(new WindowFocusListener()
         {
            @Override
            final public void windowGainedFocus(final WindowEvent windowEvent)
            {
               handlePadActivated();
            }


            @Override
            final public void windowLostFocus(final WindowEvent windowEvent)
            {
               handlePadDeactivated();
            }
         });

         delegateFrame.addMouseListener(new MouseAdapter()
         {
            @Override
            final public void mouseEntered(final MouseEvent mouseEvent)
            {
               handleMouseEnteredPad();
            }


            @Override
            final public void mouseExited(final MouseEvent mouseEvent)
            {
               handleMouseExitedPad(mouseEvent);
            }
         });
      }
   }


   private void initialiseFrameTitleBar()
   {
      initialiseFeedbackMenu();

      initialiseOptionsMenu();
   }


   private void initialiseFeedbackMenu()
   {
      initialiseFrameTitleBarButton(feedbackButton, "Feedback", UIConstants.ApplicationIconSmallPath, 19, 19);

      final ShadedButton.ActionListener feedbackFromMeActionListener = new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleFeedbackFromMeButtonActionPerformed();
         }
      };

      initialiseNestedMenuItemButton(feedbackFromMeButton, "Feedback From Me", false, feedbackFromMeActionListener);

      final List<FeedbackMenuItem> feedbackMenuItems = feedbackUIManager.getFeedbackMenuItems();
      final ShadedButton[] menuItems = new ShadedButton[feedbackMenuItems.size() + (FeedbactoryClientConstants.IsDevelopmentProfile ? 2 : 1)];
      menuItems[0] = feedbackFromMeButton;
      ShadedButton.ActionListener menuItemActionListener;
      int feedbackMenuItemIndex = 1;

      for (final FeedbackMenuItem menuItem : feedbackMenuItems)
      {
         menuItems[feedbackMenuItemIndex] = new ShadedButton(UIConstants.TitleBarButtonCornerRadius);

         menuItemActionListener = new ShadedButton.ActionListener()
         {
            @Override
            final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
            {
               handleFeedbackMenuItemActionPerformed(menuItem);
            }
         };

         initialiseNestedMenuItemButton(menuItems[feedbackMenuItemIndex], menuItem.menuItemLabel, true, menuItemActionListener);

         feedbackMenuItemIndex ++;
      }

      if (FeedbactoryClientConstants.IsDevelopmentProfile)
      {
         initialiseBrowserConsoleButton();
         menuItems[feedbackMenuItemIndex] = browserConsoleButton;
      }

      feedbackMenu.setMenuComponents(feedbackButton, menuItems);
   }


   private void initialiseFrameTitleBarButton(final ShadedButton button, final String label, final String imageIconPath)
   {
      initialiseFrameTitleBarButton(button, label, imageIconPath, UIConstants.PreferredIconWidth, UIConstants.PreferredIconHeight);
   }


   private void initialiseFrameTitleBarButton(final ShadedButton button, final String label, final String imageIconPath, final int imageIconWidth, final int imageIconHeight)
   {
      button.setFont(UIConstants.SmallFont);
      button.setText(label);
      button.setFocusable(false);

      button.setMouseOverRadialGradientPaint(UIConstants.FrameTitleBarButtonMouseOverGradient);
      button.setPressedRadialGradientPaint(UIConstants.FrameTitleBarButtonPressedGradient);
      button.setAlertRadialGradientPaint(UIConstants.FrameTitleBarButtonAlertGradient);

      try
      {
         final BufferedImage buttonImage = ImageIO.read(getClass().getResourceAsStream(imageIconPath));
         button.setImage(UIUtilities.getScaledImage(buttonImage, imageIconWidth,
                                                    imageIconHeight,
                                                    RenderingHints.VALUE_INTERPOLATION_BICUBIC));
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }
   }


   private void initialiseNestedMenuItemButton(final ShadedButton button, final String label, final boolean isEnabled, final ShadedButton.ActionListener actionListener)
   {
      button.setFont(UIConstants.SmallFont);
      button.setText(label);
      button.setFocusable(false);
      button.setEnabled(isEnabled);
      button.setMouseOverRadialGradientPaint(UIConstants.MenuButtonMouseOverGradient);
      button.setPressedRadialGradientPaint(UIConstants.MenuButtonPressedGradient);

      button.addActionListener(actionListener);
   }


   private void initialiseBrowserConsoleButton()
   {
      final ShadedButton.ActionListener browserConsoleActionListener = new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleBrowserConsoleButtonActionPerformed();
         }
      };

      initialiseNestedMenuItemButton(browserConsoleButton, "Console", true, browserConsoleActionListener);

      // Add an empty icon image to the button to ensure correct horizontal alignment with the other buttons.
      browserConsoleButton.setImage(UIUtilities.createCompatibleImage(UIConstants.PreferredIconWidth, UIConstants.PreferredIconHeight, Transparency.TRANSLUCENT));
   }


   private void initialiseOptionsMenu()
   {
      initialiseFrameTitleBarButton(optionsButton, "Options", PadResources.OptionsButtonImagePath);

      initialiseAccountButtons();

      initialiseSettingsButton();

      initialiseHelpButton();

      initialiseAboutButton();

      optionsMenu.setMenuComponents(optionsButton, new JComponent[] {accountButton, signOutButton, settingsButton, helpButton, aboutButton});
   }


   private void initialiseAccountButtons()
   {
      initialiseMenuInvocationButton(accountButton, "Account", PadResources.AccountButtonImagePath);

      accountButton.addActionListener(new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleAccountButtonActionPerformed();
         }
      });

      final ShadedButton.ActionListener signOutActionListener = new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleSignOutButtonActionPerformed();
         }
      };

      initialiseNestedMenuItemButton(signOutButton, "Sign Out", false, signOutActionListener);

      // Add an empty icon image to the sign out button to ensure correct horizontal alignment with the other buttons.
      signOutButton.setImage(UIUtilities.createCompatibleImage(UIConstants.PreferredIconWidth, UIConstants.PreferredIconHeight, Transparency.TRANSLUCENT));
   }


   private void initialiseSettingsButton()
   {
      initialiseMenuInvocationButton(settingsButton, "Settings", PadResources.SettingsButtonImagePath);

      settingsButton.addActionListener(new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleSettingsButtonActionPerformed();
         }
      });
   }


   private void initialiseHelpButton()
   {
      initialiseMenuInvocationButton(helpButton, "Help", PadResources.HelpButtonImagePath);

      helpButton.addActionListener(new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleHelpButtonActionPerformed();
         }
      });
   }


   private void initialiseAboutButton()
   {
      final ShadedButton.ActionListener aboutButtonActionListener = new ShadedButton.ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            handleAboutButtonActionPerformed();
         }
      };

      initialiseNestedMenuItemButton(aboutButton, "About", true, aboutButtonActionListener);

      // Add an empty icon image to the button to ensure correct horizontal alignment with the other buttons.
      aboutButton.setImage(UIUtilities.createCompatibleImage(UIConstants.PreferredIconWidth, UIConstants.PreferredIconHeight, Transparency.TRANSLUCENT));
   }


   private void initialiseMenuInvocationButton(final ShadedButton button, final String label, final String imageIconPath)
   {
      button.setFont(UIConstants.SmallFont);
      button.setText(label);
      button.setFocusable(false);

      button.setMouseOverRadialGradientPaint(UIConstants.MenuButtonMouseOverGradient);
      button.setPressedRadialGradientPaint(UIConstants.MenuButtonPressedGradient);

      try
      {
         final BufferedImage buttonImage = ImageIO.read(getClass().getResourceAsStream(imageIconPath));
         button.setImage(UIUtilities.getScaledImage(buttonImage, UIConstants.PreferredIconWidth,
                                                    UIConstants.PreferredIconHeight,
                                                    RenderingHints.VALUE_INTERPOLATION_BICUBIC));
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }
   }


   private Dimension calculateFrameDecorationSize()
   {
      /* Calculation of the frame decoration size is required to later calculate arguments to pass to the frame resize animator,
       * since those arguments are in terms of the outer frame size rather than the inner content size.
       */
      final Dimension frameSize = delegateFrame.getSize();
      final Dimension contentSize = delegateFrame.getContent().getSize();

      return new Dimension(frameSize.width - contentSize.width, frameSize.height - contentSize.height);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void savePadLocationRelativeToBrowserFrame()
   {
      final Preferences feedbactoryBrowserPreferences = Preferences.userNodeForPackage(getClass());

      final Point browserContentLocation = uiManager.getBrowserLocation();
      final Point windowLocation = delegateFrame.getLocation();
      final Point windowLocationRelativeToActiveBrowser = new Point(windowLocation.x - browserContentLocation.x, windowLocation.y - browserContentLocation.y);

      feedbactoryBrowserPreferences.put(WindowLocationPreferencesKey, UIUtilities.getPreferencesLocationString(windowLocationRelativeToActiveBrowser));
   }


   private void restorePadLocationOffsetFromBrowserFrame()
   {
      final Point browserContentLocation = uiManager.getBrowserLocation();
      final Point restoredPadLocation = getSavedPadOffsetFromBrowserContent();

      restoredPadLocation.translate(browserContentLocation.x, browserContentLocation.y);

      correctPadLocationForScreen(restoredPadLocation);

      delegateFrame.setRestoredLocation(restoredPadLocation.x, restoredPadLocation.y);
   }


   private Point getSavedPadOffsetFromBrowserContent()
   {
      final Preferences preferences = Preferences.userNodeForPackage(getClass());
      final String savedWindowBrowserOffset = preferences.get(WindowLocationPreferencesKey, null);

      if (savedWindowBrowserOffset != null)
      {
         try
         {
            return UIUtilities.parsePreferencesLocationString(savedWindowBrowserOffset);
         }
         catch (final Exception anyException)
         {
            // Don't allow the constant to be mutated.
            return new Point(DefaultBrowserOffset.x, DefaultBrowserOffset.y);
         }
      }
      else
      {
         // Don't allow the constant to be mutated.
         return new Point(DefaultBrowserOffset.x, DefaultBrowserOffset.y);
      }
   }


   private void correctPadLocationForScreen(final Point location)
   {
      final Rectangle screenBounds = delegateFrame.getGraphicsConfiguration().getBounds();
      final Rectangle windowBounds = delegateFrame.getBounds();

      if (location.x < screenBounds.x)
         location.x = screenBounds.x;
      else if ((location.x + windowBounds.width) > (screenBounds.x + screenBounds.width))
         location.x = (screenBounds.x + screenBounds.width - windowBounds.width);

      if (location.y < screenBounds.y)
         location.y = screenBounds.y;
      else if ((location.y + windowBounds.height) > (screenBounds.y + screenBounds.height))
         location.y = (screenBounds.y + screenBounds.height - windowBounds.height);
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
            feedbackFromMeButton.setEnabled(isSignedIn);
            signOutButton.setEnabled(isSignedIn);
         }
      });
   }


   private void handleUserAccountMessageReceived(final Message message)
   {
      final MessageType dialogMessageType = UIUtilities.networkMessageTypeToDialogMessageType(message.messageType);

      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            final MessageDialog.Builder messageDialogBuilder = new MessageDialog.Builder(dialogMessageType, message.message.split("\\n"), PresetOptionConfiguration.OK);
            messageDialogBuilder.setBorderTitle("Message For You");
            showMessageDialog(new MessageDialog(messageDialogBuilder), ComponentDisplayPriority.ApplicationRegularDialog, PresetOptionSelection.OK, false);

            setVisible(true);
         }
      });
   }


   private void handleUserAccountUIStatusChanged(final AccountUIStatus status)
   {
      setBusy(status == AccountUIStatus.Busy);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleHidePadHotkeyPressed()
   {
      setVisible(false);
   }


   private void handlePadActivated()
   {
      setPadOpaque(true);

      initialiseFocusInPad();
   }


   private void handlePadDeactivated()
   {
      setPadTranslucent();
   }


   private void handleMouseEnteredPad()
   {
      setPadOpaque(true);
   }


   private void handleMouseExitedPad(final MouseEvent mouseEvent)
   {
      if (settingsUIManager.isIdlePadTranslucencyEnabled() &&
          ((mouseEvent.getX() < 0) || (mouseEvent.getX() >= delegateFrame.getWidth()) ||
          (mouseEvent.getY() < 0) || (mouseEvent.getY() >= delegateFrame.getHeight())))
      {
         final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();

         if (keyboardFocusManager.getFocusedWindow() != delegateFrame.getDelegate())
            setPadTranslucent();
      }
   }


   private void handlePanelLocked()
   {
      setPadMenuButtonsEnabled(false);
   }


   private void handlePanelUnlocked()
   {
      setPadMenuButtonsEnabled(true);

      requestRepack();

      initialiseFocusInPad();
   }


   private void handlePanelNewLockingComponentDisplayed(final ComponentDisplayPriority displayPriority)
   {
      checkSizeForNewLockingComponent();

      if (displayPriority.isPriorityOrHigher(ComponentDisplayPriority.FormRegularDialog))
         setPadOpaque(true);

      initialiseFocusInPad();
   }


   private void handlePanelLockingComponentDismissed(final boolean wasBeingDisplayed)
   {
      /* The pad unlocked event has its own trigger to resize the pad, and its conditions for resizing are more rigid - it will always be resized if the
       * pad size is larger than the preferred size of the unlocked component (this would look strange, unlike locked components being placed in the centre
       * of a larger than required blurred field) - so defer to the unlock event trigger for any resizing that may be required.
       */
      if (wasBeingDisplayed && delegatePanel.isLocked())
         checkSizeForNewLockingComponent();
   }


   private void handleFeedbackFromMeButtonActionPerformed()
   {
      feedbackUIManager.showFeedbackFromUserPanel();
   }


   private void handleFeedbackMenuItemActionPerformed(final FeedbackMenuItem menuItem)
   {
      feedbackUIManager.invokeFeedbackMenuItem(menuItem);
   }


   private void handleBrowserConsoleButtonActionPerformed()
   {
      browserConsoleUIManager.showBrowserConsole();
   }


   private void handleAccountButtonActionPerformed()
   {
      showAccountPanel();
   }


   private void handleSignOutButtonActionPerformed()
   {
      if (feedbackUIManager.getFeedbackPanel().promptIfUnsavedFeedback())
         setVisible(true);
      else
         userAccountUIManager.signOut();
   }


   private void handleSettingsButtonActionPerformed()
   {
      showSettingsPanel();
   }


   private void handleHelpButtonActionPerformed()
   {
      showHelpPanel();
   }


   private void handleAboutButtonActionPerformed()
   {
      showLockingComponent(new AboutDialog(this).getDelegate(), ComponentDisplayPriority.FormComponent, true);
   }


   private void handlePadCloseButtonActionPerformed()
   {
      setVisible(false);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void setPadMenuButtonsEnabled(final boolean isEnabled)
   {
      feedbackMenu.setEnabled(isEnabled);
      optionsMenu.setEnabled(isEnabled);
   }


   private void dismissLockingComponents()
   {
      userAccountUIManager.requestDismissActiveComponents();
      settingsUIManager.dismissSettingsPanel();
      helpUIManager.dismissHelpPanel();

      // See the memo in the message display manager regarding why this is manually handled here rather than automated using a HierarchyListener.
      delegatePanel.dismissTemporaryComponents();
   }


   private void checkSizeForNewLockingComponent()
   {
      final Dimension currentSize = delegatePanel.getRootComponent().getSize();
      final Dimension postAddPreferredSize = delegatePanel.getRootComponent().getPreferredSize();

      /* For updated locked components being displayed, try to avoid excessive repacking/resizing of the pad,
       * so only resize if the pad's current dimensions are too small to display the new locked component.
       * A locked component being displayed centred against a larger than necessary blurred background looks OK.
       */
      if ((postAddPreferredSize.height > currentSize.height) || (postAddPreferredSize.width > currentSize.width))
         repackPadNow(postAddPreferredSize);
   }


   private void repackPadIfNeeded()
   {
      final Dimension currentContentSize = delegatePanel.getRootComponent().getSize();
      final Dimension preferredContentSize = delegatePanel.getRootComponent().getPreferredSize();

      if (! preferredContentSize.equals(currentContentSize))
         repackPadNow(preferredContentSize);
   }


   private void repackPadNow(final Dimension preferredSize)
   {
      // Convert the preferred content size to a preferred frame size.
      preferredSize.width += frameDecorationSize.width;
      preferredSize.height += frameDecorationSize.height;

      if (settingsUIManager.isAnimatedPadResizeEnabled())
         frameResizeAnimator.animateToSize(preferredSize.width, preferredSize.height);
      else
         delegateFrame.setSize(preferredSize.width, preferredSize.height);

      isRepackPending = false;
   }


   private void setPadTranslucent()
   {
      /* An annoying flicker effect can be avoided by not setting the pad to opaque immediately as it reappears.
       * The trick is to instead pre-emptively set it to opaque as soon as it's hidden so that no extra work needs to be done
       * when the pad reappears. One pitfall is that the pad window is deactivated immediately after being hidden, potentially
       * undoing that trick, so a check is performed here to ensure that the deactivated pad is set to translucent only when
       * it's actually visible.
       */
      if (isVisible())
         setPadOpaque(false);
   }


   private void setPadOpaque(final boolean isFrameOpaque)
   {
      // If the TranslucencyUtilities.isWindowOpacityControlSupported() is false, settingsUIManager.isIdlePadTranslucencyEnabled() will also have been initialised to false.
      if (settingsUIManager.isIdlePadTranslucencyEnabled() && (this.isFrameOpaque != isFrameOpaque))
      {
         setPadOpacity(isFrameOpaque ? SettingsUIManager.MaximumIdlePadOpacity : settingsUIManager.getIdlePadOpacity());
         this.isFrameOpaque = isFrameOpaque;
      }
   }


   private void setPadOpacity(final byte opacity)
   {
      try
      {
         // The caller must check whether window opacity is supported.
         delegateFrame.setWindowOpacity(opacity / 100f);
      }
      catch (final Exception anyException)
      {
         throw new RuntimeException(anyException);
      }
   }


   private void initialiseFocusInPad()
   {
      // Avoid wrenching the focus away from another window if the pad is not actually the focus owner.
      if (delegateFrame.getContent().requestFocusInWindow())
         KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(delegatePanel.getRootComponent());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleShowAccountPanel()
   {
      if (! delegatePanel.isLocked())
         userAccountUIManager.showRootAccountComponent();
   }


   private void handleShowSettingsPanel()
   {
      if (! delegatePanel.isLocked())
         settingsUIManager.showSettingsPanel();
   }


   private void handleShowHelpPanel()
   {
      if (! delegatePanel.isLocked())
         helpUIManager.showHelpPanel();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleSetVisible(final boolean isVisible)
   {
      if ((isVisible == delegateFrame.isVisible()) ||
          (shutdownState == ShutdownState.OK))
         return;

      if (isVisible)
      {
         if (isRepackPending && (! delegatePanel.isLocked()))
            repackPadIfNeeded();

         /* Under at least one circumstance on Windows, when the pad is shown and the user selects Windows' show desktop button, the pad is forcibly iconified.
          * The next time that the pad is set to visible we also need to force it to its non-iconified state.
          */
         if ((delegateFrame.getExtendedState() & Frame.ICONIFIED) != 0)
            delegateFrame.setExtendedState(Frame.NORMAL);

         delegateFrame.setVisible(true);

          /* Although the pad window is flagged as always being on top, it seems to require this kick to ensure that it takes the focus when it becomes visible,
           * at least on Windows. If it doesn't take the focus, a problem crops up whereby the feedback pad is left orphaned and on top even after the rest of the app has
           * lost the focus.
           * To see this rare case in action, comment out the line below, load the app and immediately click to activate a non-Feedbactory window before the main window
           * appears. Then switch to the Feedbactory frame, open the pad, and again switch to a non-Feedbactory window - the pad window will be left orphaned.
           */
          delegateFrame.toFront();
      }
      else
      {
         delegateFrame.setVisible(false);

         dismissLockingComponents();

         // Avoid the flicker effect by setting the pad to opaque once it's hidden, so that the opacity won't then have to be set when it reappears.
         setPadOpaque(true);
      }
   }


   // As mentioned in the top comments, the resizing is deferred when the pad is in the locked state or is not visible.
   private void handleRequestRepack()
   {
      if ((! delegateFrame.isVisible()) || delegatePanel.isLocked())
         isRepackPending = true;
      else
         repackPadIfNeeded();
   }


   private void handleSetBusy(final boolean isBusy)
   {
      if (isBusy != isBusy())
      {
         if (isBusy)
         {
            activeProgressBar = createProgressBar();
            showLockingComponent(activeProgressBar, ComponentDisplayPriority.ApplicationBusyComponent, false);
         }
         else
         {
            dismissLockingComponent(activeProgressBar);
            activeProgressBar = null;
         }
      }
   }


   private JComponent createProgressBar()
   {
      final SmileyProgressBar progressBar = new SmileyProgressBar();

      progressBar.setBackground(UIConstants.ProgressBarShadingColour);
      final Dimension progressBarDimension = new Dimension(UIConstants.ProgressBarWidth, UIConstants.ProgressBarHeight);
      progressBar.setPreferredSize(progressBarDimension);

      progressBar.setIndeterminate(true);

      return progressBar;
   }


   private ShutdownState handleRequestShutdown()
   {
      if (isBusy())
      {
         shutdownState = ShutdownState.None;

         setVisible(true);
      }
      else if (feedbackUIManager.getFeedbackPanel().promptIfUnsavedFeedback())
      {
         shutdownState = ShutdownState.Pending;

         setVisible(true);
      }
      else
         shutdownState = ShutdownState.OK;

      return shutdownState;
   }


   private void handleCancelDiscardUnsavedFeedback()
   {
      if (shutdownState == ShutdownState.Pending)
      {
         shutdownState = ShutdownState.None;
         uiManager.cancelShutdown();
      }
   }


   private void handleConfirmDiscardUnsavedFeedback()
   {
      if (shutdownState == ShutdownState.Pending)
      {
         shutdownState = ShutdownState.OK;
         uiManager.confirmShutdown();
      }
      else
         userAccountUIManager.signOut();
   }


   private void handleShutdown()
   {
      savePadLocationRelativeToBrowserFrame();

      delegatePanel.shutdown();
      feedbackMenu.dispose();
      optionsMenu.dispose();
      frameResizeAnimator.shutdown();
      delegateFrame.dispose();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public SwingNimbusFrame getDelegate()
   {
      return delegateFrame;
   }


   final public ImageLoader getImageLoader()
   {
      return uiManager.getImageLoader();
   }


   final public boolean isVisible()
   {
      return delegateFrame.isVisible();
   }


   final public void setVisible(final boolean isVisible)
   {
      handleSetVisible(isVisible);
   }


   final public void requestRepack()
   {
      handleRequestRepack();
   }


   final public void setOpacity(final byte opacity)
   {
      setPadOpacity(opacity);
   }


   final public boolean isFeedbackPanelShowing()
   {
      return (! delegatePanel.isLocked());
   }


   final public void showAccountPanel()
   {
      handleShowAccountPanel();
   }


   final public void showSettingsPanel()
   {
      handleShowSettingsPanel();
   }


   final public void showHelpPanel()
   {
      handleShowHelpPanel();
   }


   final public void showMessageDialog(final MessageDialog messageDialog, final ComponentDisplayPriority displayPriority, final PresetOptionSelection defaultAction,
                                       final boolean actionOnDialogHidden)
   {
      delegatePanel.showMessageDialog(messageDialog, displayPriority, actionOnDialogHidden, defaultAction);
   }


   final public void showMessageDialog(final MessageDialog messageDialog, final ComponentDisplayPriority displayPriority, final int defaultActionIndex,
                                       final boolean actionOnDialogHidden)
   {
      delegatePanel.showMessageDialog(messageDialog, displayPriority, actionOnDialogHidden, defaultActionIndex);
   }


   final public void showTimedMessageDialog(final MessageDialog.Builder messageDialogBuilder, final ComponentDisplayPriority displayPriority, final boolean isTemporary,
                                            final int defaultFocusIndex,  final long displayDurationMilliseconds)
   {
      delegatePanel.showTimedMessageDialog(messageDialogBuilder, displayPriority, isTemporary, defaultFocusIndex, displayDurationMilliseconds);
   }


   final public void showLockingComponent(final JComponent component, final ComponentDisplayPriority displayPriority, final boolean dismissOnDisplayHidden)
   {
      delegatePanel.showLockingComponent(component, displayPriority, dismissOnDisplayHidden);
   }


   final public void dismissLockingComponent(final JComponent component)
   {
      delegatePanel.dismissLockingComponent(component);
   }


   final public boolean isBusy()
   {
      return (activeProgressBar != null);
   }


   final public void setBusy(final boolean isPadBusy)
   {
      handleSetBusy(isPadBusy);
   }


   final public ShutdownState requestShutdown()
   {
      return handleRequestShutdown();
   }


   final public void markForShutdown()
   {
      shutdownState = ShutdownState.OK;
   }


   final public void cancelDiscardUnsavedFeedback()
   {
      handleCancelDiscardUnsavedFeedback();
   }


   final public void confirmDiscardUnsavedFeedback()
   {
      handleConfirmDiscardUnsavedFeedback();
   }


   final public void shutdown()
   {
      handleShutdown();
   }
}