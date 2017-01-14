/* Memos:
 * - A single replaceable critical alert dialog is used to report various server or network states. The dialog is replaceable to prevent multiple dialogs
 *   from stacking up in the event that the user makes continual requests which result in the state being repeatedly reported.
 *
 * - In the event that users do send further requests to the server with the dialog in place, the system should be robust enough to handle the outcome.
 *   For example if the UI receives a signal that the client version is out of date and displays a message to that effect, it should be no problem if
 *   the user somehow manages to cause the app to send further requests - these should result in the same message being displayed, or possibly a 'server unavailable'
 *   or 'server busy' message if that happens. In the latter case it might then be possible for the user to clear the dialog but the 'outdated client'
 *   notice should appear at the next request. No harm done.
 */

package com.feedbactory.client.ui;


import com.feedbactory.client.core.ConfigurationManager;
import com.feedbactory.client.core.ExceptionReportContext;
import com.feedbactory.client.core.ExceptionReportContextType;
import com.feedbactory.client.core.ExceptionReportMailer;
import com.feedbactory.client.core.OperationsManager;
import com.feedbactory.client.core.feedback.FeedbackManager;
import com.feedbactory.client.core.network.ClientNetworkConstants;
import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.client.core.network.ServerAvailability;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.client.core.useraccount.FeedbactoryUserAccount;
import com.feedbactory.client.ui.browser.BrowserServicesDebugInformation;
import com.feedbactory.client.ui.browser.BrowserUIManagerService;
import com.feedbactory.client.ui.browser.FeedbactoryBrowserWindow;
import com.feedbactory.client.ui.component.ImageLoader;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.MessageDialog.ActionListener;
import com.feedbactory.client.ui.component.MessageDialog.MessageType;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionSelection;
import com.feedbactory.client.ui.component.MessageDialogDisplayManager.ComponentDisplayPriority;
import com.feedbactory.client.ui.feedback.FeedbackUIManager;
import com.feedbactory.client.ui.pad.FeedbactoryPad;
import com.feedbactory.client.ui.pad.FeedbactoryPadUIView;
import com.feedbactory.client.ui.settings.SettingsUIManager;
import com.feedbactory.client.ui.useraccount.AccountUIManager;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.network.ClientCompatibilityStatus;
import com.feedbactory.shared.network.FeedbactoryApplicationServerStatus;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.text.DefaultEditorKit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;


final public class UIManager
{
   static final private String FirstTimeLaunchPreferencesKey = "FirstTimeLaunch";

   static final private String FeedbactoryDownloadsPage = ClientNetworkConstants.FeedbactoryWebServer + "/index.html?download";

   final private OperationsManager operationsManager;

   final private ExceptionReportMailer exceptionReportMailer;

   final private AccountSessionManager userAccountManager;

   final private SWTDisplayManager swtDisplayManager = new SWTDisplayManager(this);

   private SettingsUIManager settingsUIManager;

   private FeedbactoryBrowserWindow browserWindow;

   private AccountUIManager userAccountUIManager;

   private FeedbackUIManager feedbackUIManager;

   private FeedbactoryPad feedbactoryPad;

   private WindowStateManager windowStateManager;

   private ImageLoader imageLoader;

   private ServerAvailability serverAvailability;
   private ClientCompatibilityStatus clientCompatibilityStatus;
   private MessageDialog highPriorityMessageDialog;

   private ShutdownState shutdownState = ShutdownState.None;


   public UIManager(final OperationsManager operationsManager, final ExceptionReportMailer exceptionReportMailer, final AccountSessionManager userAccountManager)
   {
      this.operationsManager = operationsManager;
      this.exceptionReportMailer = exceptionReportMailer;
      this.userAccountManager = userAccountManager;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class FeedbactoryPadClientUIViewDelegate implements FeedbactoryPadUIView
   {
      @Override
      final public ImageLoader getImageLoader()
      {
         return imageLoader;
      }


      @Override
      final public void requestRepack()
      {
         feedbactoryPad.requestRepack();
      }


      @Override
      final public boolean isVisible()
      {
         return feedbactoryPad.isVisible();
      }


      @Override
      final public void setVisible(final boolean isVisible)
      {
         feedbactoryPad.setVisible(isVisible);
      }


      @Override
      final public void setOpacity(final byte opacity)
      {
         feedbactoryPad.setOpacity(opacity);
      }


      @Override
      final public boolean isFeedbackPanelShowing()
      {
         return feedbactoryPad.isFeedbackPanelShowing();
      }


      @Override
      final public void showAccountPanel()
      {
         feedbactoryPad.showAccountPanel();
      }


      @Override
      final public void showSettingsPanel()
      {
         feedbactoryPad.showSettingsPanel();
      }


      @Override
      final public void showHelpPanel()
      {
         feedbactoryPad.showHelpPanel();
      }


      @Override
      final public void showFormComponent(final JComponent component, final boolean isTemporary)
      {
         feedbactoryPad.showLockingComponent(component, ComponentDisplayPriority.FormComponent, isTemporary);
      }


      @Override
      final public void showFormSubcomponent(final JComponent component, final boolean isTemporary)
      {
         feedbactoryPad.showLockingComponent(component, ComponentDisplayPriority.FormSubcomponent, isTemporary);
      }


      @Override
      final public void dismissLockingComponent(final JComponent component)
      {
         feedbactoryPad.dismissLockingComponent(component);
      }


      @Override
      final public void showMessageDialog(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection defaultAction, final boolean actionOnDialogHidden)
      {
         feedbactoryPad.showMessageDialog(messageDialog, ComponentDisplayPriority.FormRegularDialog, defaultAction, actionOnDialogHidden);
      }


      @Override
      final public void showHighPriorityMessageDialog(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection defaultAction, final boolean actionOnDialogHidden)
      {
         feedbactoryPad.showMessageDialog(messageDialog, ComponentDisplayPriority.FormHighPriorityDialog, defaultAction, actionOnDialogHidden);
      }


      @Override
      final public void showTimedMessageDialog(final MessageDialog.Builder messageDialogBuilder, final boolean actionOnPadHidden, final int defaultActionIndex,
                                               final long displayDurationMilliseconds)
      {
         feedbactoryPad.showTimedMessageDialog(messageDialogBuilder, ComponentDisplayPriority.FormRegularDialog, actionOnPadHidden, defaultActionIndex, displayDurationMilliseconds);
      }


      @Override
      final public boolean isFeedbackPadBusy()
      {
         return feedbactoryPad.isBusy();
      }


      @Override
      final public void setBusy(final boolean isSubmissionActive)
      {
         feedbactoryPad.setBusy(isSubmissionActive);
      }


      @Override
      final public void cancelDiscardUnsavedFeedback()
      {
         feedbactoryPad.cancelDiscardUnsavedFeedback();
      }


      @Override
      final public void confirmDiscardUnsavedFeedback()
      {
         feedbactoryPad.confirmDiscardUnsavedFeedback();
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private Thread handleGetSwingThread()
   {
      final AtomicReference<Thread> swingThreadReference = new AtomicReference<Thread>();

      try
      {
         SwingUtilities.invokeAndWait(new Runnable()
         {
            @Override
            final public void run()
            {
               swingThreadReference.set(Thread.currentThread());
            }
         });
      }
      catch (final Exception anyException)
      {
         throw new RuntimeException(anyException);
      }

      return swingThreadReference.get();
   }


   private void handleInitialise(final Display swtDisplay, final FeedbackManager feedbackManager)
   {
      try
      {
         SwingUtilities.invokeAndWait(new Runnable()
         {
            @Override
            final public void run()
            {
               serverAvailability = ServerAvailability.NotAvailable;

               setNimbusLookAndFeel();

               checkInstallLocale();

               /* Initialising the image loader safely outside of the Swing thread (eg. as a final class variable) causes a problem on Mac OS X,
                * namely that the iconified main window will no longer respond to clicks to be deiconified. A warning message is also
                * dumped to stdout indicating that something deeper down has gone awry. It seems to be related to accessing (and probably initialising)
                * any AWT related classes even if they can otherwise be used outside of the Swing thread, eg. Dimension and ImageIO.
                * I think when a program using SWT is run on Mac OS X and the -XstartOnFirstThread JVM thread is used, OS X will crap itself if
                * any Swing is accessed/initialised before SWT, eg. if Nimbus is installed before the SWT Display is created.
                */
               initialiseImageLoader();

               final FeedbactoryPadUIView feedbactoryPadUIView = new FeedbactoryPadClientUIViewDelegate();

               initialiseSettingsUIManager(feedbactoryPadUIView);

               initialiseUserAccountUIManager(feedbactoryPadUIView);

               initialiseBrowserWindow(swtDisplay, feedbactoryPadUIView);

               initialiseFeedbackUIManager(feedbackManager, feedbactoryPadUIView);

               initialiseFeedbactoryPad(feedbactoryPadUIView);

               initialiseWindowStateManager();

               postInitialiseBrowserWindow();
            }
         });
      }
      catch (final Exception anyException)
      {
         throw new RuntimeException(anyException);
      }
   }


   private void setNimbusLookAndFeel()
   {
      try
      {
         for (final LookAndFeelInfo lookAndFeelInfo : javax.swing.UIManager.getInstalledLookAndFeels())
         {
            if (lookAndFeelInfo.getName().equals("Nimbus"))
            {
               // Workaround for Nimbus bug that vanishes small scrollbar thumbs: https://bugs.openjdk.java.net/browse/JDK-8134828
               javax.swing.UIManager.put("ScrollBar.minimumThumbSize", new Dimension(32, 32));

               javax.swing.UIManager.setLookAndFeel(lookAndFeelInfo.getClassName());
               initialiseCrossPlatformKeyboardShortcuts();
               return;
            }
         }

         throw new IllegalStateException("Nimbus look and feel not found.");
      }
      catch (final Exception anyException)
      {
         throw new RuntimeException(anyException);
      }
   }


   private void initialiseCrossPlatformKeyboardShortcuts()
   {
      // Nimbus overrides the Mac OS X Command key for copy and paste etc with Ctrl, so restore this here.
      final int shortcutKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
      final InputMap inputMap = (InputMap) javax.swing.UIManager.get("TextField.focusInputMap");
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcutKeyMask), DefaultEditorKit.cutAction);
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcutKeyMask), DefaultEditorKit.copyAction);
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcutKeyMask), DefaultEditorKit.pasteAction);
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, shortcutKeyMask), DefaultEditorKit.selectAllAction);
   }


   private void checkInstallLocale()
   {
      /* For now I'm reluctantly forcing the US locale (always present in every JRE) for any Locale where the Roboto font can't display numbers
       * when using Formatter.format or NumberFormat.format.
       * An example is Hindi, there are probably other cases as well...
       * Note that this step must be performed after installing Nimbus, to ensure that the icons in the UIConstants
       * class are correctly initialised.
       */
      final NumberFormat numberFormat = NumberFormat.getNumberInstance();
      if (UIConstants.RegularFont.canDisplayUpTo(numberFormat.format(123456789.01)) != -1)
         Locale.setDefault(Locale.US);
   }


   private void initialiseImageLoader()
   {
      imageLoader = new ImageLoader();
   }


   private void initialiseSettingsUIManager(final FeedbactoryPadUIView feedbactoryPadUIView)
   {
      settingsUIManager = new SettingsUIManager(feedbactoryPadUIView);
   }


   private void initialiseUserAccountUIManager(final FeedbactoryPadUIView feedbactoryPadUIView)
   {
      userAccountUIManager = new AccountUIManager(userAccountManager, this, feedbactoryPadUIView);
   }


   private void initialiseBrowserWindow(final Display swtDisplay, final FeedbactoryPadUIView feedbactoryPadUIView)
   {
      browserWindow = new FeedbactoryBrowserWindow(userAccountManager, this, swtDisplay, userAccountUIManager, feedbactoryPadUIView);
   }


   private void initialiseFeedbackUIManager(final FeedbackManager feedbackManager, final FeedbactoryPadUIView feedbactoryPadUIView)
   {
      feedbackUIManager = new FeedbackUIManager(feedbackManager, userAccountManager, this, settingsUIManager, feedbactoryPadUIView);
   }


   private void initialiseFeedbactoryPad(final FeedbactoryPadUIView feedbactoryPadUIView)
   {
      feedbactoryPad = new FeedbactoryPad(userAccountManager, this, feedbactoryPadUIView, settingsUIManager, userAccountUIManager, feedbackUIManager);
   }


   private void initialiseWindowStateManager()
   {
      windowStateManager = new WindowStateManager(this, browserWindow.getDelegate());
      windowStateManager.manageWindow(feedbactoryPad.getDelegate());
   }


   private void postInitialiseBrowserWindow()
   {
      browserWindow.postInitialise(feedbackUIManager);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void failsafeShowHighPriorityAlert(final String title, final MessageType messageType, final String[] message, final String[] options, final int defaultActionIndex,
                                              final ActionListener actionListener)
   {
      if (SwingUtilities.isEventDispatchThread())
         failsafeShowHighPriorityAlertEDT(title, messageType, message, options, defaultActionIndex, actionListener);
      else
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               failsafeShowHighPriorityAlertEDT(title, messageType, message, options, defaultActionIndex, actionListener);
            }
         });
      }
   }


   private void failsafeShowHighPriorityAlertEDT(final String title, final MessageType messageType, final String[] message, final String[] options, final int defaultActionIndex,
                                                 final ActionListener actionListener)
   {
      if (feedbactoryPad != null)
      {
         try
         {
            final MessageDialog.Builder messageDialogBuilder = new MessageDialog.Builder(messageType, message, options);
            messageDialogBuilder.setBorderTitle(title);
            final MessageDialog messageDialog = new MessageDialog(messageDialogBuilder);

            if (actionListener != null)
               messageDialog.addActionListener(actionListener);

            showHighPriorityMessageDialog(messageDialog, defaultActionIndex);
         }
         catch (final Exception anyException)
         {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Exception while initialising high priority alert dialog.", anyException);

            // If the dialog display on the pad fails for any reason, show a JOptionPane message dialog against a hidden frame.
            showHighPriorityJOptionPaneMessage(title, messageType, message, options, defaultActionIndex, actionListener);
         }
      }
      else // Or if the Feedbactory pad has not even made it to initialisation, again show a JOptionPane message dialog against a hidden frame.
         showHighPriorityJOptionPaneMessage(title, messageType, message, options, defaultActionIndex, actionListener);
   }


   private void showHighPriorityMessageDialog(final MessageDialog messageDialog, final int defaultActionIndex)
   {
      // Dismiss any existing high priority message so that they don't pile up.
      dismissHighPriorityMessageDialog();

      highPriorityMessageDialog = messageDialog;

      feedbactoryPad.showMessageDialog(messageDialog, ComponentDisplayPriority.ApplicationHighPriorityDialog, defaultActionIndex, false);
      feedbactoryPad.setVisible(true);
   }


   private void dismissHighPriorityMessageDialog()
   {
      if (highPriorityMessageDialog != null)
      {
         feedbactoryPad.dismissLockingComponent(highPriorityMessageDialog.getDelegate());
         highPriorityMessageDialog = null;
      }
   }


   private void showHighPriorityJOptionPaneMessage(final String title, final MessageType messageType, final String[] message, final String[] options,
                                                   final int defaultActionIndex, final ActionListener actionListener)
   {
      final int jOptionPaneMessageType = UIUtilities.dialogMessageTypeToJOptionPaneMessageType(messageType);

      final int selectionMade = JOptionPane.showOptionDialog(null, message, title, JOptionPane.DEFAULT_OPTION, jOptionPaneMessageType, null, options, options[defaultActionIndex]);

      if (actionListener != null)
         actionListener.actionPerformed(null, null, selectionMade);
   }


   private ActionListener createPersistentSessionSignInRetryActionListener()
   {
      /* If the user has a restored session which has not yet been attached/confirmed with a user account via a request to the server,
       * we must force the client to resend the 'resume session' request rather than let the user continue to use the app when their session is in a
       * sort of limbo state.
       */
      return new ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            userAccountUIManager.signInToPersistentSession();
         }
      };
   }


   private void openExternalBrowserURL(final String url)
   {
      if (Desktop.isDesktopSupported())
      {
         try
         {
            final Desktop desktop = Desktop.getDesktop();
            desktop.browse(new URI(url));
         }
         catch (final Exception anyException)
         {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "External browse URL exception.", anyException);
         }
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleLaunch()
   {
      try
      {
         SwingUtilities.invokeAndWait(new Runnable()
         {
            @Override
            final public void run()
            {
               // Browser window reference was initialised on the Swing EDT and likewise must be accessed on that thread.
               launchBrowser();
               launchFeedbactoryPad();
            }
         });
      }
      catch (final Exception anyException)
      {
         throw new RuntimeException(anyException);
      }
   }


   private void launchBrowser()
   {
      browserWindow.setVisible(true);
      browserWindow.showWelcomePage();
   }


   private void launchFeedbactoryPad()
   {
      final Preferences preferences = Preferences.userNodeForPackage(getClass());

      if (preferences.getBoolean(FirstTimeLaunchPreferencesKey, true))
      {
         preferences.putBoolean(FirstTimeLaunchPreferencesKey, false);

         feedbactoryPad.showHelpPanel();
         feedbactoryPad.setVisible(true);
      }
   }


   private void handleSetUIBusy(final boolean isUIBusy)
   {
      if (SwingUtilities.isEventDispatchThread())
      {
         /* It's rare that the pad would be null when this method is invoked, but one possibility is if the UI initialisation fails
          * and the user elects to submit an error report.
          */
         if (feedbactoryPad != null)
            feedbactoryPad.setBusy(isUIBusy);
      }
      else
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               if (feedbactoryPad != null)
                  feedbactoryPad.setBusy(isUIBusy);
            }
         });
      }
   }


   private void handleSignInToPersistentSession()
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            userAccountUIManager.signInToPersistentSession();
         }
      });
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processReportException(final ExceptionReportContext exceptionReportContext)
   {
      if (SwingUtilities.isEventDispatchThread())
         processReportExceptionEDT(exceptionReportContext);
      else
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               processReportExceptionEDT(exceptionReportContext);
            }
         });
      }
   }


   private void processReportExceptionEDT(final ExceptionReportContext exceptionReportContext)
   {
      // Follow similar logic to the failsafe dialog message display here, testing whether the UI has been successfully initialised.
      if (feedbactoryPad != null)
      {
         try
         {
            final ErrorReportSubmissionPanel errorReportSubmissionPanel = new ErrorReportSubmissionPanel(this, exceptionReportMailer, exceptionReportContext);

            /* If an error occurs during initialisation, the browser window won't yet be shown and if the Feedbactory pad loses
             * focus it will disappear, leaving the user with no means to access the crashed app. So as a safeguard, ensure
             * that the main browser window is displayed along with the pad.
             */
            browserWindow.setVisible(true);

            feedbactoryPad.showLockingComponent(errorReportSubmissionPanel.getDelegate(), ComponentDisplayPriority.ApplicationHighPriorityDialog, false);
            feedbactoryPad.setVisible(true);
         }
         catch (final Exception anyException)
         {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Exception while initialising error report dialog.", anyException);

            // If the dialog display on the pad fails for any reason, show a JOptionPane message dialog against a hidden frame.
            showFailsafeErrorReportSubmissionDialog(exceptionReportContext);
         }
      }
      else // Or if the Feedbactory pad has not even made it to initialisation, again show a JOptionPane message dialog against a hidden frame.
         showFailsafeErrorReportSubmissionDialog(exceptionReportContext);
   }


   private void showFailsafeErrorReportSubmissionDialog(final ExceptionReportContext exceptionReportContext)
   {
      final FailsafeErrorReportSubmissionDialog errorReportSubmissionDialog = new FailsafeErrorReportSubmissionDialog(this, exceptionReportMailer, exceptionReportContext);

      final JFrame errorReportSubmissionFrame = errorReportSubmissionDialog.getDelegate();
      errorReportSubmissionFrame.pack();
      final Point frameCentrePoint = UIUtilities.getFrameCentredPosition(errorReportSubmissionFrame);
      errorReportSubmissionFrame.setLocation(frameCentrePoint);
      errorReportSubmissionFrame.setVisible(true);
   }


   private void handleReportUncaughtException(final Class<?> contextClass, final String contextMessage, final Thread thread, final Throwable throwable)
   {
      // Push immediately onto the Swing thread to ensure the thread safety of the browserWindow variable.
      if (SwingUtilities.isEventDispatchThread())
         handleReportUncaughtExceptionEDT(contextClass, contextMessage, thread, throwable);
      else
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               handleReportUncaughtExceptionEDT(contextClass, contextMessage, thread, throwable);
            }
         });
      }
   }


   private void handleReportUncaughtExceptionEDT(final Class<?> contextClass, final String contextMessage, final Thread thread, final Throwable throwable)
   {
      /* Grab the context information immediately, as it may change by the time the user agrees to send the error report,
       * ie. time elapsed as well as browser tabs opened or closed.
       */
      final long approximateServerErrorTime = (operationsManager.hasSentHandshakeRequest() ? operationsManager.getApproximateServerTime() : FeedbactoryConstants.NoTime);
      final FeedbactoryUserAccount signedInUserAccount = userAccountManager.getSignedInUserAccount();
      final BrowserServicesDebugInformation browserDebugInformation;
      if (browserWindow != null)
         browserDebugInformation = getBrowserManagerService().getBrowserServicesDebugInformation();
      else
         browserDebugInformation = null;

      final ExceptionReportContext exceptionReportContext = new ExceptionReportContext(ExceptionReportContextType.UncaughtException, contextClass, contextMessage, thread, throwable,
                                                                                       approximateServerErrorTime, signedInUserAccount, browserDebugInformation);
      processReportExceptionEDT(exceptionReportContext);
   }


   private void handleReportConfigurationError()
   {
      final ActionListener actionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            processConfigurationErrorActionPerformed(optionSelectionIndex);
         }
      };

      final String feedbactoryFolderMessageLine;

      if (ConfigurationManager.isRunningWindows)
         feedbactoryFolderMessageLine = "(eg. C:\\Users\\YourName\\.feedbactory).";
      else if (ConfigurationManager.isRunningMacOSX)
         feedbactoryFolderMessageLine = "(eg. /Users/YourName/.feedbactory).";
      else
         feedbactoryFolderMessageLine = "(eg. /home/YourName/.feedbactory).";

      final String[] errorMessage = {"A problem has been detected with this installation of Feedbactory.",
                                     "Please try closing the program and deleting the Feedbactory",
                                     "application folder, located under your home folder",
                                     feedbactoryFolderMessageLine,
                                     "",
                                     "If the error persists, please try reinstalling Feedbactory."};

      failsafeShowHighPriorityAlert("Configuration Error", MessageType.Error, errorMessage, new String[] {"Open Download Page", "Close Feedbactory"}, 0, actionListener);
   }


   private void processConfigurationErrorActionPerformed(final int optionSelectionIndex)
   {
      if (optionSelectionIndex == 0)
         openExternalBrowserURL(FeedbactoryDownloadsPage);

      operationsManager.requestShutdown();
   }


   private void handleReportApplicationInstanceActive()
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            processApplicationInstanceActive();
         }
      });
   }


   private void processApplicationInstanceActive()
   {
      final String[] instanceActiveMessage = {"An instance of Feedbactory is already running.",
                                              "",
                                              "If this message persists after closing Feedbactory,",
                                              "please try restarting your device."};

      final MessageDialog.ActionListener actionListener = new MessageDialog.ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            operationsManager.requestShutdown();
         }
      };

      failsafeShowHighPriorityAlert("Feedbactory Instance Active", MessageType.Warning, instanceActiveMessage, new String[] {"OK"}, 0, actionListener);
   }


   private void handleReportLaunchError(final Throwable throwable)
   {
      final long approximateServerErrorTime = (operationsManager.hasSentHandshakeRequest() ? operationsManager.getApproximateServerTime() : FeedbactoryConstants.NoTime);

      final ExceptionReportContext exceptionReportContext = new ExceptionReportContext(ExceptionReportContextType.InitialisationException, UIManager.class, "Launch error",
                                                                                       Thread.currentThread(), throwable, approximateServerErrorTime);

      processReportException(exceptionReportContext);
   }


   private void handleReportNoCompatibleBrowser(final Throwable throwable)
   {
      final long approximateServerErrorTime = (operationsManager.hasSentHandshakeRequest() ? operationsManager.getApproximateServerTime() : FeedbactoryConstants.NoTime);

      final ExceptionReportContext exceptionReportContext = new ExceptionReportContext(ExceptionReportContextType.NoCompatibleBrowserException, FeedbactoryBrowserWindow.class,
                                                                                       "No compatible browser", Thread.currentThread(), throwable, approximateServerErrorTime);

      processReportException(exceptionReportContext);
   }


   private void handleReportClientCompatibilityUpdated(final ClientCompatibilityStatus clientCompatibilityStatus)
   {
      // Placing this task on the Swing thread purely for the thread safety of the clientCompatibilityStatus flag.
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            processClientCompatibilityUpdated(clientCompatibilityStatus);
         }
      });
   }


   private void processClientCompatibilityUpdated(final ClientCompatibilityStatus clientCompatibilityStatus)
   {
      this.clientCompatibilityStatus = clientCompatibilityStatus;

      if (clientCompatibilityStatus == ClientCompatibilityStatus.UpdateAvailable)
         processClientUpdateAvailable();
      else if (clientCompatibilityStatus == ClientCompatibilityStatus.UpdateRequired)
         processClientUpdateRequired();
   }


   private void processClientUpdateAvailable()
   {
      final String[] updatedVersionAvailableMessage = {"An updated version of Feedbactory is available.",
                                                       "Restarting it now will automatically download and",
                                                       "update to the most recent version."};

      final ActionListener actionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            if (optionSelectionIndex == 0)
               operationsManager.requestUpdateAndRestart();
         }
      };

      failsafeShowHighPriorityAlert("Update Available", MessageType.Information, updatedVersionAvailableMessage, new String[] {"Restart and Update", "Update Later"}, 0, actionListener);
   }


   private void processClientUpdateRequired()
   {
      final String[] versionIncompatibleMessage = {"An updated version of Feedbactory is available.",
                                                   "Please restart Feedbactory to automatically download",
                                                   "and update to the most recent version."};

      final ActionListener actionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            if (optionSelectionIndex == 0)
               operationsManager.requestUpdateAndRestart();
            else
               operationsManager.requestShutdown();
         }
      };

      failsafeShowHighPriorityAlert("Update Required", MessageType.Information, versionIncompatibleMessage, new String[] {"Restart and Update", "Close Feedbactory"}, 0, actionListener);
   }


   private void handleReportApplicationUpdateError()
   {
      final ActionListener actionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            processApplicationUpdateErrorActionPerformed(optionSelectionIndex);
         }
      };

      // Slightly different wording to that in the launcher - an update error occurring here is not caused by a dead internet connection.
      final String[] errorMessage = {"An error occurred during the automatic update process.",
                                     "",
                                     "If the problem persists, please try reinstalling Feedbactory."};

      // The UI may not be initialised if the client needs to update SWT, hence the use of the failsafe message display.
      failsafeShowHighPriorityAlert("Feedbactory Update Error", MessageType.Error, errorMessage, new String[] {"Open Download Page", "Close Feedbactory"}, 0, actionListener);
   }


   private void processApplicationUpdateErrorActionPerformed(final int optionSelectionIndex)
   {
      if (optionSelectionIndex == 0)
         openExternalBrowserURL(FeedbactoryDownloadsPage);

      operationsManager.requestShutdown();
   }


   private void handleReportNetworkFailure()
   {
      final String[] message = {"Unable to connect to Feedbactory.",
                                "Please check your internet",
                                "connection and try again."};

      final boolean hasUnresolvedPersistentSession = userAccountManager.hasPersistentSession() && (! userAccountManager.hasResolvedSession());
      final ActionListener actionListener = hasUnresolvedPersistentSession ? createPersistentSessionSignInRetryActionListener() : null;

      failsafeShowHighPriorityAlert("Connection Failed", MessageType.Error, message, new String[] {"OK"}, 0, actionListener);
   }


   private void handleReportRequestError(final boolean networkError)
   {
      final String[] message = {(networkError ? "A network" : "An") + " error occurred while processing the request.",
                                "Please try again at a later time."};

      final boolean hasUnresolvedPersistentSession = userAccountManager.hasPersistentSession() && (! userAccountManager.hasResolvedSession());
      final ActionListener actionListener = hasUnresolvedPersistentSession ? createPersistentSessionSignInRetryActionListener() : null;

      failsafeShowHighPriorityAlert(networkError ? "Network Error" : "Request Error", MessageType.Error, message, new String[] {"OK"}, 0, actionListener);
   }


   private void handleReportIPAddressTemporarilyBlocked()
   {
      /* Your network has sent too many requests this hour, and has been temporarily blocked.
       * Please try again later.
       */
      final String[] temporarilyBlockedMessage = {"Your network has sent too many requests",
                                                  "this hour and has been temporarily blocked.",
                                                  "Please try again at a later time."};

      final boolean hasUnresolvedPersistentSession = userAccountManager.hasPersistentSession() && (! userAccountManager.hasResolvedSession());
      final ActionListener actionListener = hasUnresolvedPersistentSession ? createPersistentSessionSignInRetryActionListener() : null;

      failsafeShowHighPriorityAlert("Service Blocked", MessageType.Warning, temporarilyBlockedMessage, new String[] {"OK"}, 0, actionListener);
   }


   private void handleReportServerStatusUpdated(final ServerAvailability serverAvailability)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            processServerStatusUpdated(serverAvailability);
         }
      });
   }


   private void processServerStatusUpdated(final ServerAvailability newServerAvailability)
   {
      serverAvailability = newServerAvailability;

      switch (serverAvailability.serverStatus)
      {
         case Available:
            dismissHighPriorityMessageDialog();
            break;

         case Busy:
            processServerBusyStatus();
            break;

         case NotAvailable:
            processServerUnavailableStatus(serverAvailability.unavailableMessage);
            break;

         default:
            throw new AssertionError("Unhandled server status in UI manager: " + serverAvailability.serverStatus);
      }
   }


   private void processServerBusyStatus()
   {
      final String[] busyMessage = {"Feedbactory is experiencing heavy traffic at the moment.",
                                    "Please try again at a later time."};

      final boolean hasUnresolvedPersistentSession = userAccountManager.hasPersistentSession() && (! userAccountManager.hasResolvedSession());
      final ActionListener actionListener = hasUnresolvedPersistentSession ? createPersistentSessionSignInRetryActionListener() : null;

      failsafeShowHighPriorityAlert("Service Busy", MessageType.Warning, busyMessage, new String[] {"OK"}, 0, actionListener);
   }


   private void processServerUnavailableStatus(final com.feedbactory.shared.Message unavailableMessage)
   {
      final MessageType messageType;
      final String[] message;

      if (unavailableMessage.messageType != com.feedbactory.shared.MessageType.NoMessage)
      {
         messageType = UIUtilities.networkMessageTypeToDialogMessageType(unavailableMessage.messageType);
         message = unavailableMessage.message.split("\n");
      }
      else
      {
         messageType = MessageType.Warning;
         message = new String[] {"Feedbactory is temporarily unavailable.",
                                 "Please try again at a later time."};
      }

      final ActionListener actionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            if (optionSelectionIndex == 0)
            {
               if (userAccountManager.hasPersistentSession() && (! userAccountManager.hasResolvedSession()))
                  userAccountUIManager.signInToPersistentSession();
            }
            else if (optionSelectionIndex == 1)
               operationsManager.requestShutdown();
         }
      };

      failsafeShowHighPriorityAlert("Service Unavailable", messageType, message, new String[] {"OK", "Close Feedbactory"}, 0, actionListener);
   }


   private void handleReportGeneralBroadcastMessage(final com.feedbactory.shared.Message generalBroadcastMessage)
   {
      final MessageType messageType = UIUtilities.networkMessageTypeToDialogMessageType(generalBroadcastMessage.messageType);
      final String[] message = generalBroadcastMessage.message.split("\n");

      failsafeShowHighPriorityAlert("Message To All Users", messageType, message, new String[] {"OK"}, 0, null);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private Point handleGetBrowserLocation()
   {
      if (browserWindow != null)
      {
         final org.eclipse.swt.graphics.Point browserLocationSWTPoint = browserWindow.getLocation();
         return new Point(browserLocationSWTPoint.x, browserLocationSWTPoint.y);
      }
      else
         return null;
   }


   private void handleHideApplicationChildWindows()
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            feedbactoryPad.setVisible(false);
         }
      });
   }


   private void handleShutdownRequestedByUser()
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            handleShutdownRequestedByUserEDT();
         }
      });
   }


   private void handleShutdownRequestedByUserEDT()
   {
      // Ignore if a shutdown request is already pending or being processed.
      if (shutdownState != ShutdownState.None)
         return;

      /* Don't allow any "discard changed feedback" confirmation prompts to the user if the server is busy or unavailable,
       * or if the client version has become superseded (which may in theory occur after the client has already had previous interaction
       * with the server).
       * Another reason to not prompt for "discard changed feedback" if the server is busy or unavailable is that those dialogs
       * are displayed as high priority alerts, which would hide any lower priority prompt to discard changed feedback...
       */
      if ((serverAvailability.serverStatus == FeedbactoryApplicationServerStatus.Available) && (clientCompatibilityStatus != ClientCompatibilityStatus.UpdateRequired))
      {
         shutdownState = feedbactoryPad.requestShutdown();

         if (shutdownState == ShutdownState.OK)
            operationsManager.requestShutdown();
      }
      else
      {
         /* The above regular case will gracefully handle a shutdown request if the pad is busy, by temporarily ignoring the shutdown request.
          * So there's no fear of the UI being disposed of while the pad is busy, and then later (for eg.) a failed network connection alert
          * trying to pop up. Setting a Swing Window to visible after it's been disposed will revive it, and in the case of the Feedbactory pad
          * can prevent the app from exiting since closing the pad again will only hide it, not dispose of it.
          * So for this type of unconditional shutdown the pad needs to be informed that it should not re-open. An example case of where this
          * might happen is during start-up when the serverAvailability is NotAvailable while the handshake is being performed, meanwhile
          * the user tries to close the window, and sometime later the attempted handshake times out and under usual circumstances the pad
          * would be set visible with a "connection failed" alert.
          */
         feedbactoryPad.markForShutdown();
         operationsManager.requestShutdown();
      }
   }


   private void handleShutdownCancelled()
   {
      shutdownState = ShutdownState.None;
   }


   private void handleShutdownConfirmed()
   {
      if (shutdownState == ShutdownState.Pending)
      {
         shutdownState = ShutdownState.OK;
         operationsManager.requestShutdown();
      }
      else
         throw new IllegalStateException("Confirm shutdown on idle shutdown state.");
   }


   private void handlePreShutdown()
   {
      /* To guarantee thread safety of the browserWindow, windowStateManager, and feedbackUIManager variables, the initialisation of the variables must 'happen-before' the
       * invocation of this method by a new shutdown thread run by an ExecutorService.
       */
      if (browserWindow != null)
      {
         browserWindow.preDispose();

         if (windowStateManager != null)
            windowStateManager.shutdown();

         if (feedbackUIManager != null)
            feedbackUIManager.preShutdown();
      }
   }


   private void handleShutdown()
   {
      /* Prior to the two-phase shutdown approach, window activation & deactivation at shutdown time would cause a bit of havoc (window hangs, orphaned pad window)
       * when events were fired to the UI manager. I needed to be very careful about how the shutdown was processed.
       *
       * The orphaned pad window was caused by the main browser window activation event being fired, which in a previous implementation ultimately set the pad window to
       * visible (the goal was to remember when the pad had been maximised and synchronize the visibility of the pad and main window, but this turned out to be more
       * annoying for the user than convenient). In any case during shutdown if the main window activation event was fired (often the case), the pad would then be set to
       * visible and 'resurrected' even though it'd been disposed. Just something to watch out for, if I decide to reinstate this feature. With the newer method of
       * disconnecting the windowStateManager task before disposing of the windows, however, I'm not sure that this would be a problem any longer.
       *
       * The window hang issue was caused by the main browser window activation event on the SWTNimbusFrame, which is interestingly called further down the chain (ie. in the
       * same call) after the call to the shell's dispose method has been called. The window activation event defined locally to the SWTNimbusFrame exists to update the frame's
       * control buttons and border appearance, however it was during this update process that the call stumbled into an SWT widget disposed exception. Again, with the two
       * phase shutdown this no longer presents a problem as long as the frame disconnects the shell active listener before the call to the shell's dispose method has been
       * made.
       */

      shutdownSwingUI();

      shutdownBrowserWindow();

      shutdownSWTDisplay();
   }


   private void shutdownSwingUI()
   {
      try
      {
         SwingUtilities.invokeAndWait(new Runnable()
         {
            @Override
            final public void run()
            {
               if (settingsUIManager != null)
               {
                  settingsUIManager.shutdown();
                  settingsUIManager = null;
               }

               if (feedbackUIManager != null)
               {
                  feedbackUIManager.shutdown();
                  feedbackUIManager = null;
               }

               if (userAccountUIManager != null)
               {
                  userAccountUIManager.shutdown();
                  userAccountUIManager = null;
               }

               if (feedbactoryPad != null)
               {
                  feedbactoryPad.shutdown();
                  feedbactoryPad = null;
               }

               if (imageLoader != null)
               {
                  imageLoader.shutdown();
                  imageLoader = null;
               }
            }
         });
      }
      catch (final Exception anyException)
      {
         throw new RuntimeException(anyException);
      }
   }


   private void shutdownBrowserWindow()
   {
      /* To guarantee thread safety of the browserWindow variable, the initialisation of the variable must 'happen-before' the
       * invocation of this method, eg. by a new shutdown thread run by an ExecutorService.
       */
      if (browserWindow != null)
      {
         browserWindow.dispose();
         browserWindow = null;
      }
   }


   private void shutdownSWTDisplay()
   {
      swtDisplayManager.dispose();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final void showMessageDialog(final MessageDialog messageDialog, final ComponentDisplayPriority displayPriority, final PresetOptionSelection defaultAction,
                                final boolean actionOnDialogHidden)
   {
      feedbactoryPad.showMessageDialog(messageDialog, displayPriority, defaultAction, actionOnDialogHidden);
   }


   final void dismissLockingComponent(final JComponent component)
   {
      feedbactoryPad.dismissLockingComponent(component);
   }


   final void hideApplicationChildWindows()
   {
      handleHideApplicationChildWindows();
   }


   final void shutdownRequestedByUser()
   {
      handleShutdownRequestedByUser();
   }


   final void shutdownRequestedByApplication()
   {
      operationsManager.requestShutdown();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public Thread getSwingThread()
   {
      return handleGetSwingThread();
   }


   final public boolean isCompatibleSWTVersion()
   {
      /* SWT.isLoadable() will return false if the OS and/or architecture combination is mismatched with the current JVM,
       * including attempting to load 32-bit libraries on a 64-bit JVM or vise versa.
       */
      return (SWT.getVersion() >= 4527) && SWT.isLoadable();
   }


   final public Display createSWTDisplay()
   {
      return swtDisplayManager.createDisplay();
   }


   final public void executeSWTReadAndDispatch()
   {
      swtDisplayManager.executeReadAndDispatch();
   }


   final public void initialise(final Display swtDisplay, final FeedbackManager feedbackManager)
   {
      handleInitialise(swtDisplay, feedbackManager);
   }


   final public void reportUncaughtException(final Class<?> contextClass, final String contextMessage, final Thread thread, final Throwable throwable)
   {
      handleReportUncaughtException(contextClass, contextMessage, thread, throwable);
   }


   final public void reportConfigurationError()
   {
      handleReportConfigurationError();
   }


   final public void reportApplicationInstanceActive()
   {
      handleReportApplicationInstanceActive();
   }


   final public void reportLaunchError(final Throwable throwable)
   {
      handleReportLaunchError(throwable);
   }


   final public void reportNoCompatibleBrowser(final Throwable throwable)
   {
      handleReportNoCompatibleBrowser(throwable);
   }


   final public void reportClientCompatibilityUpdated(final ClientCompatibilityStatus clientCompatibilityStatus)
   {
      handleReportClientCompatibilityUpdated(clientCompatibilityStatus);
   }


   final public void reportApplicationUpdateError()
   {
      handleReportApplicationUpdateError();
   }


   final public void reportNetworkFailure(final NetworkRequestStatus networkStatus)
   {
      // Disregarding the exact type of error (timeout or other) for now.
      handleReportNetworkFailure();
   }


   final public void reportNetworkSecurityCondition()
   {
      handleReportRequestError(true);
   }


   final public void reportRequestError()
   {
      handleReportRequestError(false);
   }


   final public void reportIPAddressTemporarilyBlocked()
   {
      handleReportIPAddressTemporarilyBlocked();
   }


   final public void reportServerStatusUpdated(final ServerAvailability serverAvailability)
   {
      handleReportServerStatusUpdated(serverAvailability);
   }


   final public void reportGeneralBroadcastMessage(final com.feedbactory.shared.Message generalBroadcastMessage)
   {
      handleReportGeneralBroadcastMessage(generalBroadcastMessage);
   }


   final public BrowserUIManagerService getBrowserManagerService()
   {
      return browserWindow.getBrowserUIManagerService();
   }


   final public Point getBrowserLocation()
   {
      return handleGetBrowserLocation();
   }


   final public ImageLoader getImageLoader()
   {
      return imageLoader;
   }


   final public void launch()
   {
      handleLaunch();
   }


   final public void setUIBusy(final boolean isUIBusy)
   {
      handleSetUIBusy(isUIBusy);
   }


   final public void signInToPersistentSession()
   {
      handleSignInToPersistentSession();
   }


   final public void cancelShutdown()
   {
      handleShutdownCancelled();
   }


   final public void confirmShutdown()
   {
      handleShutdownConfirmed();
   }


   /* THREADING: Synchronous Swing and/or SWT calls ahead. To prevent deadlock, this method must not be invoked from either the Swing or SWT EDT threads, or any
    * other threads which have tied them up.
    */
   final public void preShutdown()
   {
      handlePreShutdown();
   }


   /* THREADING: Synchronous Swing and/or SWT calls ahead. To prevent deadlock, this method must not be invoked from either the Swing or SWT EDT threads, or any
    * other threads which have tied them up.
    */
   final public void shutdown()
   {
      handleShutdown();
   }
}