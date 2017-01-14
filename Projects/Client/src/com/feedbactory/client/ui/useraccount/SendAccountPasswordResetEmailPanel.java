
package com.feedbactory.client.ui.useraccount;


import com.feedbactory.client.core.useraccount.AccountUtilities;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.HyperlinkLabel;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.MessageDialog.MessageType;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionConfiguration;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionSelection;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.client.ui.component.SelfLabelledTextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


final class SendAccountPasswordResetEmailPanel
{
   final private AccountUIManager parentPanel;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private RoundedPanel contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private JLabel passwordResetLabelOne = new JLabel();
   final private JLabel passwordResetLabelTwo = new JLabel();
   final private JLabel passwordResetLabelThree = new JLabel();
   final private SelfLabelledTextField emailTextField = new SelfLabelledTextField();
   final private JButton sendPasswordResetCodeButton = new JButton();
   final private JButton cancelButton = new JButton();

   final private JLabel resetPasswordPromptLabel = new JLabel();
   final private HyperlinkLabel resetPasswordLink = new HyperlinkLabel();


   SendAccountPasswordResetEmailPanel(final AccountUIManager parentPanel, final String email)
   {
      this.parentPanel = parentPanel;

      initialise(email);
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Send Password Reset Code");
      builder.setRadialGradientPaint(UIConstants.LighterPanelGradient);
      return new NimbusBorderPanel(builder);
   }


   private void initialise(final String email)
   {
      initialiseContentPanel(email);

      initialiseResetPasswordPrompt();

      initialiseDelegatePanel();
   }


   private void initialiseContentPanel(final String email)
   {
      contentPanel.setBackground(UIConstants.ContentPanelColour);

      initialiseSendPasswordResetCodeControls(email);

      initialiseContentPanelLayout();
   }


   private void initialiseSendPasswordResetCodeControls(final String emailAddress)
   {
      final ActionListener resetAccountPasswordActionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleSendPasswordResetCodeButtonActionPerformed();
         }
      };

      passwordResetLabelOne.setFont(UIConstants.RegularFont);
      passwordResetLabelOne.setText("To reset your account password, please");

      passwordResetLabelTwo.setFont(UIConstants.RegularFont);
      passwordResetLabelTwo.setText("enter your email address and a password");

      passwordResetLabelThree.setFont(UIConstants.RegularFont);
      passwordResetLabelThree.setText("reset code will be sent to you:");

      emailTextField.setFont(UIConstants.RegularFont);
      emailTextField.setLabel("Email");
      emailTextField.addActionListener(resetAccountPasswordActionListener);
      emailTextField.getDocument().addDocumentListener(new DocumentListener()
      {
         @Override
         final public void insertUpdate(final DocumentEvent documentEvent)
         {
            handleEmailAddressTextFieldUpdated();
         }


         @Override
         final public void removeUpdate(final DocumentEvent documentEvent)
         {
            handleEmailAddressTextFieldUpdated();
         }


         @Override
         final public void changedUpdate(final DocumentEvent documentEvent)
         {
            handleEmailAddressTextFieldUpdated();
         }
      });

      sendPasswordResetCodeButton.setFont(UIConstants.RegularFont);
      sendPasswordResetCodeButton.setText("Send Code");

      if (emailAddress != null)
         emailTextField.setText(emailAddress);
      else
         sendPasswordResetCodeButton.setEnabled(false);

      cancelButton.setFont(UIConstants.RegularFont);
      cancelButton.setText("Cancel");

      sendPasswordResetCodeButton.addActionListener(resetAccountPasswordActionListener);

      cancelButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleCancelButtonActionPerformed();
         }
      });
   }


   private void initialiseResetPasswordPrompt()
   {
      resetPasswordPromptLabel.setFont(UIConstants.RegularFont);
      resetPasswordPromptLabel.setText("Already have a password reset code?");

      resetPasswordLink.setFont(UIConstants.SmallFont);
      resetPasswordLink.setHyperlink("Reset Password", null);

      resetPasswordLink.addMouseListener(new MouseAdapter()
      {
         @Override
         final public void mouseClicked(final MouseEvent mouseEvent)
         {
            handleResetPasswordButtonActionPerformed();
         }
      });
   }


   private void initialiseContentPanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(contentPanel);
      contentPanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(passwordResetLabelOne)
            .addComponent(passwordResetLabelTwo)
            .addComponent(passwordResetLabelThree)
            .addComponent(emailTextField, GroupLayout.PREFERRED_SIZE, 225, GroupLayout.PREFERRED_SIZE)
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(passwordResetLabelOne)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(passwordResetLabelTwo)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(passwordResetLabelThree)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(emailTextField)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );
   }


   private void initialiseDelegatePanel()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getContentPane());
      delegatePanel.getContentPane().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(contentPanel)
            .addGroup(panelLayout.createSequentialGroup()
               .addGap(UIConstants.MediumContainerGapSize)
               .addGroup(panelLayout.createParallelGroup()
                  .addGroup(panelLayout.createSequentialGroup()
                     .addComponent(sendPasswordResetCodeButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
                     .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                     .addComponent(cancelButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
                  )
                  .addComponent(resetPasswordPromptLabel)
                  .addComponent(resetPasswordLink, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
               )
               .addGap(UIConstants.MediumContainerGapSize)
            )
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(contentPanel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(sendPasswordResetCodeButton)
            .addComponent(cancelButton)
         )
         .addGap(UIConstants.LargeUnrelatedComponentGapSize)
         .addComponent(resetPasswordPromptLabel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(resetPasswordLink)
         .addContainerGap(UIConstants.MediumContainerGapSize * 2, Integer.MAX_VALUE)
      );
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleEmailAddressTextFieldUpdated()
   {
      sendPasswordResetCodeButton.setEnabled(areRequiredFieldsFilledIn());
   }


   private boolean areRequiredFieldsFilledIn()
   {
      return (! emailTextField.getText().trim().isEmpty());
   }


   private void handleSendPasswordResetCodeButtonActionPerformed()
   {
      if (! areRequiredFieldsFilledIn())
         return;

      final String emailAddress = emailTextField.getText().trim();

      if (AccountUtilities.isValidEmail(emailAddress))
         parentPanel.sendPasswordResetEmail(emailAddress);
      else
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, new String[] {"Invalid email address."}, PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK, true);
      }
   }


   private void handleCancelButtonActionPerformed()
   {
      parentPanel.dismissActiveAccountComponent();
   }


   private void handleResetPasswordButtonActionPerformed()
   {
      parentPanel.showResetAccountPasswordPanel(null);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}