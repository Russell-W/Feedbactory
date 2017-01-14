
package com.feedbactory.client.ui.useraccount;


import com.feedbactory.client.core.useraccount.AccountUtilities;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.*;
import com.feedbactory.client.ui.component.MessageDialog.MessageType;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionConfiguration;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


final class AccountActivationPanel
{
   final private AccountUIManager parentPanel;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private RoundedPanel contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private JLabel activationCodePromptLabelOne = new JLabel();
   final private JLabel activationCodePromptLabelTwo = new JLabel();
   final private SelfLabelledTextField emailTextField = new SelfLabelledTextField();
   final private JButton resendActivationCodeButton = new JButton();
   final private SelfLabelledTextField activationCodeTextField = new SelfLabelledTextField();
   final private SelfLabelledPasswordField passwordTextField = new SelfLabelledPasswordField();
   final private SelfLabelledPasswordField passwordConfirmationTextField = new SelfLabelledPasswordField();

   final private JButton activateAccountButton = new JButton();
   final private JButton cancelButton = new JButton();


   AccountActivationPanel(final AccountUIManager parentPanel, final String email)
   {
      this.parentPanel = parentPanel;

      initialise(email);
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Activate Account");
      builder.setRadialGradientPaint(UIConstants.LighterPanelGradient);
      return new NimbusBorderPanel(builder);
   }


   private void initialise(final String email)
   {
      initialiseContentPanel(email);
      initialiseDelegatePanel();
   }


   private void initialiseContentPanel(final String email)
   {
      contentPanel.setBackground(UIConstants.ContentPanelColour);

      initialiseActivationControls(email);
      initialiseContentPanelLayout();
   }


   private void initialiseActivationControls(final String email)
   {
      activationCodePromptLabelOne.setFont(UIConstants.RegularFont);
      activationCodePromptLabelOne.setText("To complete your Feedbactory account registration, please");

      activationCodePromptLabelTwo.setFont(UIConstants.RegularFont);
      activationCodePromptLabelTwo.setText("paste in the activation code that was emailed to you:");

      emailTextField.setFont(UIConstants.RegularFont);
      emailTextField.setLabel("Email");

      final DocumentListener documentListener = new DocumentListener()
      {
         @Override
         final public void insertUpdate(final DocumentEvent documentEvent)
         {
            handleTextFieldUpdated();
         }


         @Override
         final public void removeUpdate(final DocumentEvent documentEvent)
         {
            handleTextFieldUpdated();
         }


         @Override
         final public void changedUpdate(final DocumentEvent documentEvent)
         {
            handleTextFieldUpdated();
         }
      };

      final ActionListener activateAccountActionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleActivateAccountActionPerformed();
         }
      };

      if (email == null)
      {
         emailTextField.getDocument().addDocumentListener(documentListener);

         resendActivationCodeButton.setEnabled(false);
      }
      else
      {
         emailTextField.setText(email);
         emailTextField.setEditable(false);
         emailTextField.setFocusable(false);

         resendActivationCodeButton.setEnabled(true);
      }

      emailTextField.addActionListener(activateAccountActionListener);

      resendActivationCodeButton.setFont(UIConstants.RegularFont);
      resendActivationCodeButton.setText("Resend Code");
      resendActivationCodeButton.setFocusable(false);

      resendActivationCodeButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleResendActivationCodeButtonActionPerformed();
         }
      });

      activationCodeTextField.setFont(UIConstants.RegularFont);
      activationCodeTextField.setLabel("Activation code");
      activationCodeTextField.getDocument().addDocumentListener(documentListener);
      activationCodeTextField.addActionListener(activateAccountActionListener);

      passwordTextField.setFont(UIConstants.RegularFont);
      passwordTextField.setLabel("Initial password (minimum length 8)");
      passwordTextField.getDocument().addDocumentListener(documentListener);
      passwordTextField.addActionListener(activateAccountActionListener);

      passwordConfirmationTextField.setFont(UIConstants.RegularFont);
      passwordConfirmationTextField.setLabel("Confirm password");
      passwordConfirmationTextField.getDocument().addDocumentListener(documentListener);
      passwordConfirmationTextField.addActionListener(activateAccountActionListener);

      activateAccountButton.setFont(UIConstants.RegularFont);
      activateAccountButton.setText("Activate Account");
      activateAccountButton.setEnabled(false);

      activateAccountButton.addActionListener(activateAccountActionListener);

      cancelButton.setFont(UIConstants.RegularFont);
      cancelButton.setText("Cancel");

      cancelButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleCancelButtonActionPerformed();
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
            .addComponent(activationCodePromptLabelOne)
            .addComponent(activationCodePromptLabelTwo)
            .addGroup(panelLayout.createSequentialGroup()
               .addGroup(panelLayout.createParallelGroup()
                  .addComponent(emailTextField, GroupLayout.PREFERRED_SIZE, AccountUIConstants.EmailAddressFieldWidth, GroupLayout.PREFERRED_SIZE)
                  .addComponent(activationCodeTextField, GroupLayout.PREFERRED_SIZE, AccountUIConstants.EmailAddressFieldWidth, GroupLayout.PREFERRED_SIZE)
                  .addComponent(passwordTextField, GroupLayout.PREFERRED_SIZE, AccountUIConstants.EmailAddressFieldWidth, GroupLayout.PREFERRED_SIZE)
                  .addComponent(passwordConfirmationTextField, GroupLayout.PREFERRED_SIZE, AccountUIConstants.EmailAddressFieldWidth, GroupLayout.PREFERRED_SIZE)
               )
               .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
               .addComponent(resendActivationCodeButton)
            )
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(activationCodePromptLabelOne)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(activationCodePromptLabelTwo)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(emailTextField)
            .addComponent(resendActivationCodeButton)
         )
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(activationCodeTextField)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(passwordTextField)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(passwordConfirmationTextField)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );
   }


   private void initialiseDelegatePanel()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getContentPane());
      delegatePanel.getContentPane().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(contentPanel)
            .addGroup(panelLayout.createSequentialGroup()
               .addComponent(activateAccountButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
               .addComponent(cancelButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
            )
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(contentPanel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(activateAccountButton)
            .addComponent(cancelButton)
         )
         .addContainerGap()
      );
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleTextFieldUpdated()
   {
      resendActivationCodeButton.setEnabled(! emailTextField.getText().trim().isEmpty());
      activateAccountButton.setEnabled(areRequiredFieldsFilledIn());
   }


   private boolean areRequiredFieldsFilledIn()
   {
      return (! emailTextField.getText().trim().isEmpty()) &&
             (! activationCodeTextField.getText().trim().isEmpty()) &&
             (passwordTextField.getDocument().getLength() > 0) &&
             (passwordConfirmationTextField.getDocument().getLength() > 0);
   }


   private void handleActivateAccountActionPerformed()
   {
      if (! areRequiredFieldsFilledIn())
         return;

      final String email = emailTextField.getText().trim();

      final String activationCode = activationCodeTextField.getText().trim();

      final char[] password = passwordTextField.getPassword();
      final char[] passwordConfirmation = passwordConfirmationTextField.getPassword();

      handleBlankPasswordTextFields();

      if (! AccountUtilities.isValidEmail(email))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, new String[] {"Invalid email address."}, PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK, true);
      }
      else if (! AccountUtilities.isValidEmailConfirmationCode(activationCode))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, new String[] {"Invalid activation code."}, MessageDialog.PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK, true);
      }
      else if (! AccountUtilities.isValidPassword(password))
      {
         final String[] message = new String[] {"Your password must be a minimum of 8 characters long", "and contain at least two letters and two non-letters."};
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, message, MessageDialog.PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK, true);
      }
      else if (! Arrays.equals(password, passwordConfirmation))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, new String[] {"Password confirmation does not match."}, MessageDialog.PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK, true);
      }
      else
         parentPanel.activateAccount(email, activationCode, AccountUtilities.generatePasswordHash(email, password));

      Arrays.fill(password, '\0');
      Arrays.fill(passwordConfirmation, '\0');
   }


   private void handleCancelButtonActionPerformed()
   {
      handleBlankPasswordTextFields();

      parentPanel.dismissActiveAccountComponent();
   }


   private void handleResendActivationCodeButtonActionPerformed()
   {
      handleBlankPasswordTextFields();

      final String enteredEmailAddress = emailTextField.getText().trim();

      if (AccountUtilities.isValidEmail(enteredEmailAddress))
         parentPanel.resendActivationCode(enteredEmailAddress);
      else
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, new String[] {"Invalid email address."}, PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK, true);
      }
   }


   private void handleBlankPasswordTextFields()
   {
      passwordTextField.setText(null);
      passwordConfirmationTextField.setText(null);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}