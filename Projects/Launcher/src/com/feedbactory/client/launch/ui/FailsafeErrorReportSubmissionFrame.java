/* Memos:
 * - This UI for reporting an error is a fallback for when the regular UI either has not yet already initialised or has failed in some way.
 *
 * - The modus operandi for this class is to only use core UI components, to reduce the possibility of failure if for example parts of the Feedbactory
 *   UI (even the constants, some of which rely on Nimbus having been first installed) have not been properly initialised.
 */

package com.feedbactory.client.launch.ui;


import com.feedbactory.client.launch.core.ConfigurationManager;
import com.feedbactory.client.launch.core.ExceptionReportContext;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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


final class FailsafeErrorReportSubmissionFrame
{
   final private UIManager uiManager;
   final private ExceptionReportContext exceptionReportContext;

   final private JFrame delegateFrame = new JFrame();

   final private JLabel[] errorPromptLabels;

   final private JCheckBox sendErrorReportCheckBox = new JCheckBox();

   final private JButton okButton = new JButton();


   FailsafeErrorReportSubmissionFrame(final UIManager uiManager, final ExceptionReportContext exceptionReportContext)
   {
      this.uiManager = uiManager;
      this.exceptionReportContext = exceptionReportContext;

      final String[] dialogMessage = getDialogMessage();
      errorPromptLabels = new JLabel[dialogMessage.length];

      initialise(dialogMessage);
   }


   private String[] getDialogMessage()
   {
      final String feedbactoryFolderMessageLine;

      if (ConfigurationManager.isRunningWindows)
         feedbactoryFolderMessageLine = "your home folder (eg. C:\\Users\\YourName\\.feedbactory).";
      else if (ConfigurationManager.isRunningMacOSX)
         feedbactoryFolderMessageLine = "your home folder (eg. /Users/YourName/.feedbactory).";
      else
         feedbactoryFolderMessageLine = "your home folder (eg. /home/YourName/.feedbactory).";

      return new String[] {"An error occurred during the initialisation of Feedbactory.",
                           "",
                           "If the error persists please try closing the program and",
                           "deleting the Feedbactory application folder, located under",
                           feedbactoryFolderMessageLine};
   }


   private void initialise(final String[] dialogMessage)
   {
      initialisePromptLabels(dialogMessage);
      initialiseCheckBoxes();
      initialiseOKButton();
      initialiseDelegateFrame();
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
      sendErrorReportCheckBox.setSelected(true);
      sendErrorReportCheckBox.setFocusable(false);
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
      delegateFrame.setTitle("Feedbactory Error");

      initialiseDelegateFrameLayout();
   }


   private void initialiseDelegateFrameLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(delegateFrame.getContentPane());
      delegateFrame.getContentPane().setLayout(panelLayout);

      final GroupLayout.ParallelGroup horizontalGroup = panelLayout.createParallelGroup();
      final GroupLayout.SequentialGroup verticalGroup = panelLayout.createSequentialGroup();

      // Can assume that the message argument is at least one line long.
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
      verticalGroup.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(sendErrorReportCheckBox)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 15, GroupLayout.PREFERRED_SIZE)
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


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleOKButtonActionPerformed()
   {
      delegateFrame.dispose();

      if (sendErrorReportCheckBox.isSelected())
      {
         final JFrame busyAlertFrame = createBusyAlert();
         showMessageDialog(busyAlertFrame);

         final ExecutorService executorService = Executors.newSingleThreadExecutor();

         executorService.execute(new Runnable()
         {
            @Override
            final public void run()
            {
               sendErrorReport(busyAlertFrame);
            }
         });

         executorService.shutdown();
      }
      else
         uiManager.exceptionReportSubmissionDialogProcessed();
   }


   private JFrame createBusyAlert()
   {
      final JFrame frame = new JFrame("Feedbactory");
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


   private void sendErrorReport(final JFrame busyAlertFrame)
   {
      try
      {
         uiManager.getExceptionReportMailer().sendErrorReport(exceptionReportContext);

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
         // The new exception may be related to the original exception that led to this point, so don't bother with any further processing aside from logging.
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Failsafe send error report exception", anyException);
         uiManager.exceptionReportSubmissionDialogProcessed();
      }
      finally
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               busyAlertFrame.dispose();
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
      final JFrame frame = new JFrame("Feedbactory");
      frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      frame.setResizable(false);

      final JLabel labelOne = new JLabel("An error report has been sent to Feedbactory support.");
      final JLabel labelTwo = new JLabel("Thank you!");
      final JButton button = new JButton("OK");

      button.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            frame.dispose();
            uiManager.exceptionReportSubmissionDialogProcessed();
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