
package com.feedbactory.client.launch.ui;


import com.feedbactory.client.launch.core.ApplicationUpdateResult;
import com.feedbactory.client.launch.core.ConfigurationManager;
import com.feedbactory.client.launch.core.ExceptionReportContext;
import com.feedbactory.client.launch.core.ExceptionReportMailer;
import com.feedbactory.client.launch.core.OperationsManager;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.MessageDialog.MessageType;
import com.feedbactory.client.ui.component.SmileyProgressBar;
import java.awt.Desktop;
import java.awt.Dimension;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;


final public class UIManager
{
   // Keep the http prefix as it's vital for Desktop.browse() to know which external application to invoke.
   static final private String FeedbactoryDownloadsPage = "http://feedbactory.com/index.html?download";

   final private OperationsManager operationsManager;
   final private ConfigurationManager updateManager;


   public UIManager(final OperationsManager operationsManager, final ConfigurationManager updateManager)
   {
      this.operationsManager = operationsManager;
      this.updateManager = updateManager;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void checkInitialise()
   {
      // Lazily initialise the UI, which will typically not be required unless updating Feedbactory or if there's a problem.
      if (! isInitialised())
         initialise();
   }


   private boolean isInitialised()
   {
      return ((javax.swing.UIManager.getLookAndFeel() != null) && javax.swing.UIManager.getLookAndFeel().getID().equals("Nimbus"));
   }


   private void initialise()
   {
      setNimbusLookAndFeel();
   }


   private void setNimbusLookAndFeel()
   {
      try
      {
         for (final javax.swing.UIManager.LookAndFeelInfo lookAndFeelInfo : javax.swing.UIManager.getInstalledLookAndFeels())
         {
            if (lookAndFeelInfo.getName().equals("Nimbus"))
            {
               javax.swing.UIManager.setLookAndFeel(lookAndFeelInfo.getClassName());
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


   /****************************************************************************
    *
    ***************************************************************************/


   private void failsafeShowAlert(final MessageType messageType, final String[] message, final String[] options, final int defaultActionIndex,
                                  final MessageDialog.ActionListener actionListener)
   {
      if (SwingUtilities.isEventDispatchThread())
         failsafeShowAlertEDT(messageType, message, options, defaultActionIndex, actionListener);
      else
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               failsafeShowAlertEDT(messageType, message, options, defaultActionIndex, actionListener);
            }
         });
      }
   }


   private void failsafeShowAlertEDT(final MessageType messageType, final String[] message, final String[] options, final int defaultActionIndex,
                                     final MessageDialog.ActionListener actionListener)
   {
      try
      {
         checkInitialise();

         final MessageDialog.Builder messageDialogBuilder = new MessageDialog.Builder(messageType, message, options);
         messageDialogBuilder.setBorderTitle(UIConstants.ApplicationTitle);
         final MessageDialog messageDialog = new MessageDialog(messageDialogBuilder);

         if (actionListener != null)
            messageDialog.addActionListener(actionListener);

         showMessageDialogEDT(messageDialog);
      }
      catch (final Exception anyException)
      {
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Failsafe show alert exception", anyException);

         // If the regular message display fails for any reason, show a JOptionPane message dialog instead.
         showJOptionPaneAlertEDT(messageType, message, options, defaultActionIndex, actionListener);
      }
   }


   private void showMessageDialogEDT(final MessageDialog messageDialog)
   {
      messageDialog.addActionListener(new MessageDialog.ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            messageDialog.getDelegate().dispose();
         }
      });

      showCentredFrame(messageDialog.getDelegate().getDelegate());
   }


   private void showCentredFrame(final JFrame frame)
   {
      frame.pack();
      frame.setLocation(UIUtilities.getFrameCentredPosition(frame));
      frame.setVisible(true);
   }


   private void showJOptionPaneAlertEDT(final MessageType messageType, final String[] message, final String[] options, final int defaultActionIndex,
                                        final MessageDialog.ActionListener actionListener)
   {
      /* No attempt should be made to initialise the regular Feedbactory UI here, as this code path may be executing due to an error during that process.
       * The JOptionPane alert shown here doesn't rely on any Nimbus-based custom UI components.
       */
      final int jOptionPaneMessageType = UIUtilities.dialogMessageTypeToJOptionPaneMessageType(messageType);

      final int selectionMade = JOptionPane.showOptionDialog(null, message, "Feedbactory", JOptionPane.DEFAULT_OPTION, jOptionPaneMessageType, null, options,
                                                             options[defaultActionIndex]);

      if (actionListener != null)
         actionListener.actionPerformed(null, null, selectionMade);
   }


   private void showURLInBrowser(final String url)
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
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "External browser show URL exception", anyException);
         }
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleReportUncaughtException(final ExceptionReportContext exceptionReportContext)
   {
      if (SwingUtilities.isEventDispatchThread())
         handleReportUncaughtExceptionEDT(exceptionReportContext);
      else
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               handleReportUncaughtExceptionEDT(exceptionReportContext);
            }
         });
      }
   }


   private void handleReportUncaughtExceptionEDT(final ExceptionReportContext exceptionReportContext)
   {
      try
      {
         checkInitialise();

         final ErrorReportSubmissionFrame errorReportSubmissionFrame = new ErrorReportSubmissionFrame(this, exceptionReportContext);
         showCentredFrame(errorReportSubmissionFrame.getDelegate().getDelegate());
      }
      catch (final Exception anyException)
      {
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Show error report submission frame exception", anyException);

         // If the regular dialog display fails for any reason, show a JOptionPane message dialog instead.
         showFailsafeErrorReportSubmissionDialog(exceptionReportContext);
      }
   }


   private void showFailsafeErrorReportSubmissionDialog(final ExceptionReportContext exceptionReportContext)
   {
      final FailsafeErrorReportSubmissionFrame errorReportSubmissionFrame = new FailsafeErrorReportSubmissionFrame(this, exceptionReportContext);
      showCentredFrame(errorReportSubmissionFrame.getDelegate());
   }


   private void handleReportHeadlessEnvironmentUnsupported()
   {
      // No UI components will be available, fall back to console IO.
      System.out.println("Feedbactory is not supported on headless environments.");
      operationsManager.requestShutdown();
   }


   private void handleReportUnsupportedPlatform()
   {
      final MessageDialog.ActionListener actionListener = new MessageDialog.ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            operationsManager.requestShutdown();
         }
      };

      final String[] errorMessage = {"This operating system and Java platform combination",
                                     "is not currently supported by Feedbactory."};

      /* If an unsupported version of Java is running, the regular Feedbactory UI may fail since the Nimbus look & feel and translucency effects may be unavailable.
       * In that instance this alert will be shown using JOptionPane as a fallback.
       *
       * Another thing that may be unavailable is the Desktop (since Java 1.6), so unfortunately it can't be used to open the user's browser at the Java download page.
       */
      failsafeShowAlert(MessageType.Warning, errorMessage, new String[] {"Close Feedbactory"}, 0, actionListener);
   }


   private void handleReportSupersededJavaVersion()
   {
      final MessageDialog.ActionListener actionListener = new MessageDialog.ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            operationsManager.requestShutdown();
         }
      };

      final String[] errorMessage = {"This version of Java has been superseded",
                                     "and is not supported by Feedbactory.",
                                     "",
                                     "Please check the official Java website at",
                                     "java.com for an updated release."};

      // Again the regular Nimbus-based UI (and Desktop for opening a browser window) may be unavailable, requiring a fallback to JOptionPane message dialogs here.
      failsafeShowAlert(MessageType.Warning, errorMessage, new String[] {"Close Feedbactory"}, 0, actionListener);
   }


   private void handleReportConfigurationError()
   {
      final MessageDialog.ActionListener actionListener = new MessageDialog.ActionListener()
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

      // A configuration error will occur only if the JVM & OS compatibility checks have already passed, so using the Desktop API is permitted.
      failsafeShowAlert(MessageType.Error, errorMessage, new String[] {"Open Download Page", "Close Feedbactory"}, 0, actionListener);
   }


   private void processConfigurationErrorActionPerformed(final int optionSelectionIndex)
   {
      if (optionSelectionIndex == 0)
         showURLInBrowser(FeedbactoryDownloadsPage);

      operationsManager.requestShutdown();
   }


   private void handleInitiateApplicationUpdate()
   {
      // This may be called on the Swing thread in the case of a user retry after a failed update.
      if (SwingUtilities.isEventDispatchThread())
         handleInitiateApplicationUpdateEDT();
      else
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               handleInitiateApplicationUpdateEDT();
            }
         });
      }
   }


   private void handleInitiateApplicationUpdateEDT()
   {
      checkInitialise();

      final MessageDialog busyMessageDialog = createBusyMessageDialog(new String[] {"Updating packages, please wait..."});
      showMessageDialogEDT(busyMessageDialog);

      final ExecutorService executorService = Executors.newSingleThreadExecutor();

      executorService.execute(new Runnable()
      {
         @Override
         final public void run()
         {
            updateApplication(busyMessageDialog);
         }
      });

      executorService.shutdown();
   }


   private MessageDialog createBusyMessageDialog(final String[] message)
   {
      final JComponent progressBar = createProgressBar();

      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Plain, message);
      builder.setBorderTitle(UIConstants.ApplicationTitle);
      builder.setInputComponents(new JComponent[] {progressBar});

      return new MessageDialog(builder);
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


   private void updateApplication(final MessageDialog busyMessageDialog)
   {
      ApplicationUpdateResult updateResult;

      try
      {
         /* Allow non-IO exceptions to bubble up as uncaught exceptions, as they are out of the ordinary and should be treated as more serious errors, possibly bugs.
          * The uncaught exception handler will trigger a UI alert.
          */
         updateResult = updateManager.updateApplication();
      }
      catch (final IOException ioException)
      {
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Feedbactory update exception", ioException);
         updateResult = ApplicationUpdateResult.UpdateError;
      }
      finally
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               busyMessageDialog.dispose();
            }
         });
      }

      processApplicationUpdateResult(updateResult);
   }


   private void processApplicationUpdateResult(final ApplicationUpdateResult updateResult)
   {
      switch (updateResult)
      {
         case Success:
            operationsManager.reportApplicationUpdated();
            break;

         case InstanceActive:
            processApplicationInstanceActive();
            break;

         case ServerUnavailable:
            processApplicationServerUnavailable();
            break;

         case UpdateError:
            processApplicationUpdateError();
            break;

         default:
            throw new AssertionError("Unhandled application update result: " + updateResult);
      }
   }


   private void processApplicationInstanceActive()
   {
      final MessageDialog.ActionListener actionListener = new MessageDialog.ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            operationsManager.requestShutdown();
         }
      };

      final String[] errorMessage = {"An instance of Feedbactory is already running.",
                                     "",
                                     "If this message persists after closing Feedbactory,",
                                     "please try restarting your device."};

      failsafeShowAlert(MessageType.Warning, errorMessage, new String[] {"OK"}, 0, actionListener);
   }


   private void processApplicationServerUnavailable()
   {
      final MessageDialog.ActionListener actionListener = new MessageDialog.ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            operationsManager.requestShutdown();
         }
      };

      final String[] errorMessage = {"Feedbactory automatic updates are currently unavailable.",
                                     "Please try again at a later time."};

      failsafeShowAlert(MessageType.Warning, errorMessage, new String[] {"Close Feedbactory"}, 0, actionListener);
   }


   private void processApplicationUpdateError()
   {
      final MessageDialog.ActionListener actionListener = new MessageDialog.ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            processApplicationUpdateErrorActionPerformed(optionSelectionIndex);
         }
      };

      final String[] errorMessage = {"An error occurred during the automatic update process.",
                                     "Please check your internet connection and try again.",
                                     "",
                                     "If the problem persists, please try reinstalling Feedbactory."};

      failsafeShowAlert(MessageType.Warning, errorMessage, new String[] {"Retry", "Open Download Page", "Close Feedbactory"}, 0, actionListener);
   }


   private void processApplicationUpdateErrorActionPerformed(final int optionSelectionIndex)
   {
      if (optionSelectionIndex == 0)
      {
         /* Restart the launch process. For now it's fine on the Swing thread but should be shifted to a separate thread if
          * there is heavier processing to be done in the operations manager.
          */
         operationsManager.launch();
      }
      else if (optionSelectionIndex == 1)
      {
         showURLInBrowser(FeedbactoryDownloadsPage);
         operationsManager.requestShutdown();
      }
      else
         operationsManager.requestShutdown();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final ExceptionReportMailer getExceptionReportMailer()
   {
      return operationsManager.getExceptionReportMailer();
   }


   final void exceptionReportSubmissionDialogProcessed()
   {
      operationsManager.requestShutdown();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void reportUncaughtException(final ExceptionReportContext exceptionReportContext)
   {
      handleReportUncaughtException(exceptionReportContext);
   }


   final public void reportHeadlessEnvironmentUnsupported()
   {
      handleReportHeadlessEnvironmentUnsupported();
   }


   final public void reportUnsupportedPlatform()
   {
      handleReportUnsupportedPlatform();
   }


   final public void reportSupersededJavaVersion()
   {
      handleReportSupersededJavaVersion();
   }


   final public void reportConfigurationError()
   {
      handleReportConfigurationError();
   }


   final public void initiateApplicationUpdate()
   {
      handleInitiateApplicationUpdate();
   }
}