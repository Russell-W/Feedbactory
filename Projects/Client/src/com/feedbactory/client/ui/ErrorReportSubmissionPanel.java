
package com.feedbactory.client.ui;


import com.feedbactory.client.core.ExceptionReportAction;
import com.feedbactory.client.core.ExceptionReportContext;
import com.feedbactory.client.core.ExceptionReportContextType;
import com.feedbactory.client.core.ExceptionReportMailer;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.MessageDialog.MessageType;
import com.feedbactory.client.ui.component.MessageDialogDisplayManager;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.client.ui.component.SmileyProgressBar;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.LayoutStyle;
import javax.swing.SwingUtilities;


final class ErrorReportSubmissionPanel extends AbstractErrorReportSubmissionPanel
{
   // Saving a preference value for this is probably a little overkill.
   static private ExceptionReportAction savedExceptionReportAction = ExceptionReportAction.SendCompleteExceptionReport;

   final private NimbusBorderPanel delegatePanel;

   final private RoundedPanel contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private JLabel iconLabel = new JLabel();
   final private JLabel[] errorPromptLabels;

   final private JCheckBox sendErrorReportCheckBox = new JCheckBox();
   final private JCheckBox sendBrowserLocationsCheckBox;

   final private JButton okButton = new JButton();


   ErrorReportSubmissionPanel(final UIManager uiManager, final ExceptionReportMailer exceptionReportMailer, final ExceptionReportContext exceptionReportContext)
   {
      super(uiManager, exceptionReportMailer, exceptionReportContext);

      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder(getDialogTitle());
      builder.setRadialGradientPaint(UIConstants.LighterPanelGradient);

      delegatePanel = new NimbusBorderPanel(builder);

      final String[] dialogMessage = getDialogMessage();
      errorPromptLabels = new JLabel[dialogMessage.length];

      if ((exceptionReportContext.exceptionContextType == ExceptionReportContextType.InitialisationException) ||
          (exceptionReportContext.exceptionContextType == ExceptionReportContextType.NoCompatibleBrowserException))
         sendBrowserLocationsCheckBox = null;
      else
         sendBrowserLocationsCheckBox = new JCheckBox();

      initialise(dialogMessage);
   }


   private void initialise(final String[] dialogMessage)
   {
      initialiseContentPanel(dialogMessage);
      initialiseDelegatePanel();

      initialiseSavedState();
   }


   private void initialiseContentPanel(final String[] dialogMessage)
   {
      initialisePromptLabel(dialogMessage);
      initialiseCheckboxes();
      initialiseContentPanelLayout();
   }


   private void initialisePromptLabel(final String[] dialogMessage)
   {
      iconLabel.setFont(UIConstants.RegularFont);
      iconLabel.setIcon(UIConstants.ErrorIconMedium);

      JLabel label;

      for (int labelIndex = 0; labelIndex < errorPromptLabels.length; labelIndex ++)
      {
         label = new JLabel();
         errorPromptLabels[labelIndex] = label;
         label.setFont(UIConstants.RegularFont);
         label.setText(dialogMessage[labelIndex]);
      }
   }


   private void initialiseCheckboxes()
   {
      sendErrorReportCheckBox.setFont(UIConstants.RegularFont);
      sendErrorReportCheckBox.setText("Send an error report.");
      sendErrorReportCheckBox.setFocusable(false);

      if (sendBrowserLocationsCheckBox != null)
      {
         sendErrorReportCheckBox.addItemListener(new ItemListener()
         {
            @Override
            final public void itemStateChanged(final ItemEvent itemEvent)
            {
               handleSendReportCheckBoxUpdated();
            }
         });

         sendBrowserLocationsCheckBox.setFont(UIConstants.RegularFont);
         sendBrowserLocationsCheckBox.setText("Include current browser locations.");
         sendBrowserLocationsCheckBox.setFocusable(false);
      }
   }


   private void initialiseContentPanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(contentPanel);
      contentPanel.setLayout(panelLayout);

      final GroupLayout.ParallelGroup horizontalGroup = panelLayout.createParallelGroup();
      final GroupLayout.SequentialGroup verticalGroup = panelLayout.createSequentialGroup();

      // Can assume that the message argument is at least one line long.
      horizontalGroup.addComponent(errorPromptLabels[0]);
      verticalGroup.addContainerGap(10, Integer.MAX_VALUE)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.TRAILING)
            .addComponent(iconLabel)
            .addComponent(errorPromptLabels[0])
         );

      for (int labelIndex = 1; labelIndex < errorPromptLabels.length; labelIndex ++)
      {
         horizontalGroup.addComponent(errorPromptLabels[labelIndex]);
         verticalGroup.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED);
         verticalGroup.addComponent(errorPromptLabels[labelIndex]);
      }

      horizontalGroup.addComponent(sendErrorReportCheckBox);
      verticalGroup.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(sendErrorReportCheckBox);

      if (sendBrowserLocationsCheckBox != null)
      {
         horizontalGroup.addComponent(sendBrowserLocationsCheckBox);
         verticalGroup.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED);
         verticalGroup.addComponent(sendBrowserLocationsCheckBox);
      }

      verticalGroup.addContainerGap(10, Integer.MAX_VALUE);

      panelLayout.setHorizontalGroup(panelLayout.createParallelGroup()
        .addGroup(panelLayout.createSequentialGroup()
          .addContainerGap(10, Integer.MAX_VALUE)
          .addComponent(iconLabel)
          .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
          .addGroup(horizontalGroup)
          .addContainerGap(10, Integer.MAX_VALUE)
        )
      );

      panelLayout.setVerticalGroup(verticalGroup);
   }


   private void initialiseDelegatePanel()
   {
      initialiseDelegatePanelControls();
      initialiseDelegatePanelLayout();
   }


   private void initialiseDelegatePanelControls()
   {
      okButton.setFont(UIConstants.RegularFont);
      okButton.setText("OK");

      okButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleOKButtonActionPerformed();
         }
      });
   }


   private void initialiseDelegatePanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getContentPane());
      delegatePanel.getContentPane().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(contentPanel)
            .addComponent(okButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(contentPanel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(okButton)
         .addContainerGap()
      );
   }


   private void initialiseSavedState()
   {
      if ((savedExceptionReportAction == ExceptionReportAction.SendCompleteExceptionReport) ||
          (savedExceptionReportAction == ExceptionReportAction.SendBrowserCensoredExceptionReport))
         sendErrorReportCheckBox.setSelected(true);

      if ((sendBrowserLocationsCheckBox != null) && (savedExceptionReportAction == ExceptionReportAction.SendCompleteExceptionReport))
         sendBrowserLocationsCheckBox.setSelected(true);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSendReportCheckBoxUpdated()
   {
      sendBrowserLocationsCheckBox.setEnabled(sendErrorReportCheckBox.isSelected());
   }


   private void handleOKButtonActionPerformed()
   {
      uiManager.dismissLockingComponent(delegatePanel.getDelegate());

      savedExceptionReportAction = getErrorReportAction();
      if ((savedExceptionReportAction == ExceptionReportAction.SendCompleteExceptionReport) || (savedExceptionReportAction == ExceptionReportAction.SendBrowserCensoredExceptionReport))
      {
         /* The busy notification here can't use a JOptionPane via the UIManager's failsafe show message mechanism, because a JOptionPane dialog blocks
          * the Swing thread. So, a MessageDialog must be used on the regular UI, which at this point (despite the original error occurring) I can be fairly
          * confident is still available since the parent delegatePanel was able to be shown without throwing any errors; if an error had occurred at that point,
          * the UI manager would have used the FailsafeErrorReportSubmissionDialog as a fallback.
          */
         final MessageDialog busyDialog = createBusyDialog();

         /* Priority of ApplicationHighPriorityDialog ensures that the busy dialog will be displayed above even an existing busy progress bar
          * if the original error occurred while the UI was stuck in that state.
          */
         uiManager.showMessageDialog(busyDialog, MessageDialogDisplayManager.ComponentDisplayPriority.ApplicationHighPriorityDialog, null, false);

         final ExecutorService executorService = Executors.newSingleThreadExecutor();

         executorService.execute(new Runnable()
         {
            @Override
            final public void run()
            {
               sendErrorReport(savedExceptionReportAction, busyDialog);
            }
         });

         executorService.shutdown();
      }
      else if ((savedExceptionReportAction == ExceptionReportAction.DoNotSendExceptionReport) &&
               ((exceptionReportContext.exceptionContextType == ExceptionReportContextType.InitialisationException) ||
                (exceptionReportContext.exceptionContextType == ExceptionReportContextType.NoCompatibleBrowserException)))
         uiManager.shutdownRequestedByApplication();
   }


   private ExceptionReportAction getErrorReportAction()
   {
      if (sendErrorReportCheckBox.isSelected())
      {
         if ((sendBrowserLocationsCheckBox == null) || sendBrowserLocationsCheckBox.isSelected())
            return ExceptionReportAction.SendCompleteExceptionReport;
         else
            return ExceptionReportAction.SendBrowserCensoredExceptionReport;
      }
      else
         return ExceptionReportAction.DoNotSendExceptionReport;
   }


   private MessageDialog createBusyDialog()
   {
      final SmileyProgressBar progressBar = new SmileyProgressBar();
      progressBar.setBackground(UIConstants.ProgressBarShadingColour);
      final Dimension progressBarDimension = new Dimension(UIConstants.ProgressBarWidth, UIConstants.ProgressBarHeight);
      progressBar.setPreferredSize(progressBarDimension);
      progressBar.setIndeterminate(true);

      final MessageDialog.Builder busyAlertBuilder = new MessageDialog.Builder(MessageType.Plain, new String[] {"Sending error report, please wait..."});
      busyAlertBuilder.setInputComponents(new JComponent[] {progressBar});

      return new MessageDialog(busyAlertBuilder);
   }


   private void sendErrorReport(final ExceptionReportAction exceptionReportAction, final MessageDialog busyDialog)
   {
      try
      {
         exceptionReportMailer.sendErrorReport(exceptionReportAction, exceptionReportContext);

         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               showThankYouMessageDialog();
            }
         });

      }
      catch (final Exception anyException)
      {
         // The exception may be related to the original exception that led to this point, so don't bother with any further processing aside from logging.
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Send error report task failed", anyException);

         if ((exceptionReportContext.exceptionContextType == ExceptionReportContextType.InitialisationException) ||
             (exceptionReportContext.exceptionContextType == ExceptionReportContextType.NoCompatibleBrowserException))
            uiManager.shutdownRequestedByApplication();
      }
      finally
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               uiManager.dismissLockingComponent(busyDialog.getDelegate());
            }
         });
      }
   }


   private void showThankYouMessageDialog()
   {
      final String[] message = {"An error report been sent", "to Feedbactory support.", "Thank you!"};
      final MessageDialog.Builder thankYouDialogBuilder = new MessageDialog.Builder(MessageType.Information, message, MessageDialog.PresetOptionConfiguration.OK);
      final MessageDialog thankYouDialog = new MessageDialog(thankYouDialogBuilder);

      if ((exceptionReportContext.exceptionContextType == ExceptionReportContextType.InitialisationException) ||
          (exceptionReportContext.exceptionContextType == ExceptionReportContextType.NoCompatibleBrowserException))
      {
         thankYouDialog.addActionListener(new MessageDialog.ActionListener()
         {
            @Override
            final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
            {
               uiManager.shutdownRequestedByApplication();
            }
         });
      }

      uiManager.showMessageDialog(thankYouDialog, MessageDialogDisplayManager.ComponentDisplayPriority.ApplicationHighPriorityDialog, MessageDialog.PresetOptionSelection.OK, false);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}