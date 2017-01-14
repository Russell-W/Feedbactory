/* Memos:
 * - This UI for reporting an error is a fallback for when the regular UI either has not yet already initialised or has failed in some way.
 *
 * - The modus operandi for this class is to only use core UI components, to reduce the possibility of failure if for example parts of the Feedbactory
 *   UI (even the constants, some of which rely on Nimbus having been first installed) have not been properly initialised.
 */

package com.feedbactory.client.ui;


import com.feedbactory.client.core.ExceptionReportAction;
import com.feedbactory.client.core.ExceptionReportContext;
import com.feedbactory.client.core.ExceptionReportContextType;
import com.feedbactory.client.core.ExceptionReportMailer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.LayoutStyle;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;


final class FailsafeErrorReportSubmissionDialog extends AbstractErrorReportSubmissionPanel
{
   // Saving a preference value for this is probably a little overkill.
   static private ExceptionReportAction savedExceptionReportAction = ExceptionReportAction.SendCompleteExceptionReport;

   final private JFrame delegateFrame = new JFrame();

   final private JLabel[] errorPromptLabels;

   final private JCheckBox sendErrorReportCheckBox = new JCheckBox();
   final private JCheckBox sendBrowserLocationsCheckBox;

   final private JButton okButton = new JButton();


   FailsafeErrorReportSubmissionDialog(final UIManager uiManager, final ExceptionReportMailer exceptionReportMailer, final ExceptionReportContext exceptionReportContext)
   {
      super(uiManager, exceptionReportMailer, exceptionReportContext);

      final String[] dialogMessage = getDialogMessage();
      errorPromptLabels = new JLabel[dialogMessage.length];

      if ((exceptionReportContext.exceptionContextType == ExceptionReportContextType.InitialisationException) ||
          (exceptionReportContext.exceptionContextType == ExceptionReportContextType.NoCompatibleBrowserException))
         sendBrowserLocationsCheckBox = null;
      else
         sendBrowserLocationsCheckBox = new JCheckBox();

      initialise(dialogMessage);
   }


   private void initialise(final String[] messages)
   {
      initialisePromptLabels(messages);
      initialiseCheckBoxes();
      initialiseOKButton();
      initialiseDelegateFrame();

      initialiseSavedState();
   }


   private void initialisePromptLabels(final String[] messages)
   {
      for (int labelIndex = 0; labelIndex < errorPromptLabels.length; labelIndex ++)
      {
         errorPromptLabels[labelIndex] = new JLabel();
         errorPromptLabels[labelIndex].setText(messages[labelIndex]);
      }
   }


   private void initialiseCheckBoxes()
   {
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

         sendBrowserLocationsCheckBox.setText("Include current browser locations.");
         sendBrowserLocationsCheckBox.setFocusable(false);
      }
   }


   private void initialiseOKButton()
   {
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


   private void initialiseDelegateFrame()
   {
      delegateFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      delegateFrame.setResizable(false);
      delegateFrame.setTitle(getDialogTitle());

      if ((exceptionReportContext.exceptionContextType == ExceptionReportContextType.InitialisationException) ||
          (exceptionReportContext.exceptionContextType == ExceptionReportContextType.NoCompatibleBrowserException))
      {
         delegateFrame.addWindowListener(new WindowAdapter()
         {
            @Override
            final public void windowClosing(final WindowEvent windowEvent)
            {
               uiManager.shutdownRequestedByApplication();
            }
         });
      }

      initialiseDelegateFrameLayout();
   }


   private void initialiseDelegateFrameLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(delegateFrame.getContentPane());
      delegateFrame.getContentPane().setLayout(panelLayout);

      final GroupLayout.ParallelGroup horizontalGroup = panelLayout.createParallelGroup();
      final GroupLayout.SequentialGroup verticalGroup = panelLayout.createSequentialGroup();

      horizontalGroup.addComponent(errorPromptLabels[0]);
      verticalGroup.addContainerGap(10, Integer.MAX_VALUE)
         .addComponent(errorPromptLabels[0]);

      for (int labelIndex = 1; labelIndex < errorPromptLabels.length; labelIndex ++)
      {
         horizontalGroup.addComponent(errorPromptLabels[labelIndex]);
         verticalGroup.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED);
         verticalGroup.addComponent(errorPromptLabels[labelIndex]);
      }

      horizontalGroup.addComponent(sendErrorReportCheckBox);
      verticalGroup.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED);
      verticalGroup.addComponent(sendErrorReportCheckBox);

      if (sendBrowserLocationsCheckBox != null)
      {
         horizontalGroup.addComponent(sendBrowserLocationsCheckBox);
         verticalGroup.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED);
         verticalGroup.addComponent(sendBrowserLocationsCheckBox);
      }

      verticalGroup.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 15, GroupLayout.PREFERRED_SIZE)
         .addComponent(okButton)
         .addContainerGap(10, Integer.MAX_VALUE);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
          .addContainerGap(10, Integer.MAX_VALUE)
          .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
             .addGroup(horizontalGroup)
             .addComponent(okButton)
          )
          .addContainerGap(10, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(verticalGroup);
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
      delegateFrame.dispose();

      savedExceptionReportAction = getErrorReportAction();
      if ((savedExceptionReportAction == ExceptionReportAction.SendCompleteExceptionReport) || (savedExceptionReportAction == ExceptionReportAction.SendBrowserCensoredExceptionReport))
      {
         final JFrame busyAlertFrame = createBusyAlert();
         showMessageDialog(busyAlertFrame);

         final ExecutorService executorService = Executors.newSingleThreadExecutor();

         executorService.execute(new Runnable()
         {
            @Override
            final public void run()
            {
               sendErrorReport(savedExceptionReportAction, busyAlertFrame);
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


   private JFrame createBusyAlert()
   {
      final JFrame frame = new JFrame(UIConstants.ApplicationTitle);
      frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      frame.setResizable(false);

      final JLabel label = new JLabel("Sending error report, please wait...");

      final JProgressBar progressBar = new JProgressBar();
      progressBar.setIndeterminate(true);

      final GroupLayout panelLayout = new GroupLayout(frame.getContentPane());
      frame.getContentPane().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(10, Integer.MAX_VALUE)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(label)
            .addComponent(progressBar, GroupLayout.PREFERRED_SIZE, 130, GroupLayout.PREFERRED_SIZE)
         )
         .addContainerGap(10, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(10, Integer.MAX_VALUE)
         .addComponent(label)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(progressBar)
         .addContainerGap(10, Integer.MAX_VALUE)
      );

      return frame;
   }


   private void showMessageDialog(final JFrame frame)
   {
      frame.pack();
      frame.setLocationRelativeTo(delegateFrame);
      frame.setVisible(true);
   }


   private void sendErrorReport(final ExceptionReportAction exceptionReportAction, final JFrame busyDialog)
   {
      try
      {
         exceptionReportMailer.sendErrorReport(exceptionReportAction, exceptionReportContext);

         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               showThankYouDialog();
            }
         });
      }
      catch (final Exception anyException)
      {
         // The exception may be related to the original exception that led to this point, so don't bother with any further processing aside from logging.
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Exception report mailer error.", anyException);

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
               busyDialog.dispose();
            }
         });
      }
   }


   private void showThankYouDialog()
   {
      final JFrame thankYouDialog = createThankYouDialog();
      showMessageDialog(thankYouDialog);
   }


   private JFrame createThankYouDialog()
   {
      final JFrame frame = new JFrame(UIConstants.ApplicationTitle);
      frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      frame.setResizable(false);

      if ((exceptionReportContext.exceptionContextType == ExceptionReportContextType.InitialisationException) ||
          (exceptionReportContext.exceptionContextType == ExceptionReportContextType.NoCompatibleBrowserException))
      {
         frame.addWindowListener(new WindowAdapter()
         {
            @Override
            final public void windowClosed(final WindowEvent windowEvent)
            {
               uiManager.shutdownRequestedByApplication();
            }
         });
      }

      final JLabel labelOne = new JLabel("An error report has been sent to Feedbactory support.");
      final JLabel labelTwo = new JLabel("Thank you!");
      final JButton button = new JButton("OK");

      button.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            frame.dispose();
         }
      });

      final GroupLayout panelLayout = new GroupLayout(frame.getContentPane());
      frame.getContentPane().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(10, Integer.MAX_VALUE)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(labelOne)
            .addComponent(labelTwo)
            .addComponent(button)
         )
         .addContainerGap(10, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(10, Integer.MAX_VALUE)
         .addComponent(labelOne)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(labelTwo)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 15, GroupLayout.PREFERRED_SIZE)
         .addComponent(button)
         .addContainerGap(10, Integer.MAX_VALUE)
      );

      return frame;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final JFrame getDelegate()
   {
      return delegateFrame;
   }
}