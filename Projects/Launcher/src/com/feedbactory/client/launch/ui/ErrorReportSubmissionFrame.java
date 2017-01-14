
package com.feedbactory.client.launch.ui;


import com.feedbactory.client.launch.core.ConfigurationManager;
import com.feedbactory.client.launch.core.ExceptionReportContext;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.client.ui.component.SmileyProgressBar;
import com.feedbactory.client.ui.component.SwingNimbusFrame;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.LayoutStyle;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;


final class ErrorReportSubmissionFrame
{
   final private UIManager uiManager;
   final ExceptionReportContext exceptionReportContext;

   final private SwingNimbusFrame delegateFrame = new SwingNimbusFrame();

   final private JComponent borderPanel = new JPanel(null);
   final private RoundedPanel contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private JLabel iconLabel = new JLabel();
   final private JLabel[] errorPromptLabels;

   final private JCheckBox sendErrorReportCheckBox = new JCheckBox();

   final private JButton okButton = new JButton();


   ErrorReportSubmissionFrame(final UIManager uiManager, final ExceptionReportContext exceptionReportContext)
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
      initialiseContentPanel(dialogMessage);
      initialiseBorderPanel();
      initialiseDelegateFrame();
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
      sendErrorReportCheckBox.setSelected(true);
      sendErrorReportCheckBox.setFocusable(false);
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
         .addComponent(sendErrorReportCheckBox)
         .addContainerGap(10, Integer.MAX_VALUE);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
          .addContainerGap(10, Integer.MAX_VALUE)
          .addComponent(iconLabel)
          .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
          .addGroup(horizontalGroup)
          .addContainerGap(10, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(verticalGroup);
   }


   private void initialiseBorderPanel()
   {
      borderPanel.setBackground(UIConstants.LighterPanelColour);

      initialiseBorderPanelControls();
      initialiseBorderPanelLayout();
   }


   private void initialiseBorderPanelControls()
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


   private void initialiseBorderPanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(borderPanel);
      borderPanel.setLayout(panelLayout);

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


   private void initialiseDelegateFrame()
   {
      delegateFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      delegateFrame.setWindowDecorationStyle(JRootPane.PLAIN_DIALOG);
      delegateFrame.setResizable(false);
      delegateFrame.setFrameTitle("Feedbactory Error", true);

      try
      {
         final BufferedImage frameIconImage = ImageIO.read(getClass().getResourceAsStream(UIConstants.ApplicationIconLargePath));
         delegateFrame.setFrameIcon(frameIconImage, true);
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }

      delegateFrame.setContent(borderPanel);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleOKButtonActionPerformed()
   {
      delegateFrame.dispose();

      if (sendErrorReportCheckBox.isSelected())
      {
         final MessageDialog busyMessageDialog = createBusyMessageDialog();
         showMessageDialog(busyMessageDialog);

         final ExecutorService executorService = Executors.newSingleThreadExecutor();

         executorService.execute(new Runnable()
         {
            @Override
            final public void run()
            {
               sendErrorReport(busyMessageDialog);
            }
         });

         executorService.shutdown();
      }
      else
         uiManager.exceptionReportSubmissionDialogProcessed();
   }


   private MessageDialog createBusyMessageDialog()
   {
      final JComponent progressBar = createProgressBar();

      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Plain, new String[] {"Sending error report, please wait..."});
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


   private void showMessageDialog(final MessageDialog messageDialog)
   {
      final SwingNimbusFrame frame = messageDialog.getDelegate();
      frame.pack();
      frame.getDelegate().setLocationRelativeTo(delegateFrame.getDelegate());
      frame.setVisible(true);
   }


   private void sendErrorReport(final MessageDialog busyMessageDialog)
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
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Send error report exception", anyException);
         uiManager.exceptionReportSubmissionDialogProcessed();
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
   }


   private void showThankYouDialog()
   {
      final MessageDialog thankYouDialog = createThankYouDialog();
      showMessageDialog(thankYouDialog);
   }


   private MessageDialog createThankYouDialog()
   {
      final String[] message = {"An error report been sent", "to Feedbactory support.", "Thank you!"};
      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Information, message, MessageDialog.PresetOptionConfiguration.OK);
      builder.setBorderTitle(UIConstants.ApplicationTitle);

      final MessageDialog thankYouDialog = new MessageDialog(builder);

      thankYouDialog.addActionListener(new MessageDialog.ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            thankYouDialog.dispose();
            uiManager.exceptionReportSubmissionDialogProcessed();
         }
      });

      return thankYouDialog;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final SwingNimbusFrame getDelegate()
   {
      return delegateFrame;
   }
}