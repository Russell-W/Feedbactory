/* Memos:
 * - It's more of a change password action than a reset, but I think that the terminology is more in line with what users are used to; less chance of being
 *   confused with a password change while signed in.
 */

package com.feedbactory.client.ui.useraccount;


import com.feedbactory.client.core.useraccount.AccountUtilities;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.MessageDialog.MessageType;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionConfiguration;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionSelection;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.client.ui.component.SelfLabelledPasswordField;
import com.feedbactory.client.ui.component.SelfLabelledTextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


final class ResetAccountPasswordPanel
{
   final private AccountUIManager parentPanel;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private RoundedPanel contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private JLabel resetPasswordCodePromptLabelOne = new JLabel();
   final private JLabel resetPasswordCodePromptLabelTwo = new JLabel();

   final private SelfLabelledTextField emailTextField = new SelfLabelledTextField();
   final private SelfLabelledTextField resetPasswordCodeTextField = new SelfLabelledTextField();
   final private SelfLabelledPasswordField newPasswordTextField = new SelfLabelledPasswordField();
   final private SelfLabelledPasswordField newPasswordConfirmationTextField = new SelfLabelledPasswordField();

   final private JButton resetPasswordButton = new JButton();
   final private JButton cancelButton = new JButton();


   ResetAccountPasswordPanel(final AccountUIManager parentPanel, final String email)
   {
      this.parentPanel = parentPanel;

      initialise(email);
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Reset Password");
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

      initialiseChangePasswordControls(email);
      initialiseContentPanelLayout();
   }


   private void initialiseChangePasswordControls(final String email)
   {
      final DocumentListener resetPasswordDocumentListener = new DocumentListener()
      {
         @Override
         final public void insertUpdate(final DocumentEvent documentEvent)
         {
            handleChangePasswordTextFieldUpdated();
         }


         @Override
         final public void removeUpdate(final DocumentEvent documentEvent)
         {
            handleChangePasswordTextFieldUpdated();
         }


         @Override
         final public void changedUpdate(final DocumentEvent documentEvent)
         {
            handleChangePasswordTextFieldUpdated();
         }
      };

      final ActionListener resetAccountPasswordActionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleChangePasswordActionPerformed();
         }
      };

      resetPasswordCodePromptLabelOne.setFont(UIConstants.RegularFont);
      resetPasswordCodePromptLabelOne.setText("To reset your password, please paste in");
      resetPasswordCodePromptLabelTwo.setFont(UIConstants.RegularFont);
      resetPasswordCodePromptLabelTwo.setText("the reset code that was emailed to you:");

      emailTextField.setFont(UIConstants.RegularFont);
      emailTextField.setLabel("Email");

      if (email == null)
         emailTextField.getDocument().addDocumentListener(resetPasswordDocumentListener);
      else
      {
         emailTextField.setText(email);
         emailTextField.setEditable(false);
         emailTextField.setFocusable(false);
      }

      emailTextField.addActionListener(resetAccountPasswordActionListener);

      resetPasswordCodeTextField.setFont(UIConstants.RegularFont);
      resetPasswordCodeTextField.setLabel("Password reset code");
      resetPasswordCodeTextField.getDocument().addDocumentListener(resetPasswordDocumentListener);
      resetPasswordCodeTextField.addActionListener(resetAccountPasswordActionListener);

      newPasswordTextField.setFont(UIConstants.RegularFont);
      newPasswordTextField.setLabel("New password");
      newPasswordTextField.getDocument().addDocumentListener(resetPasswordDocumentListener);
      newPasswordTextField.addActionListener(resetAccountPasswordActionListener);

      newPasswordConfirmationTextField.setFont(UIConstants.RegularFont);
      newPasswordConfirmationTextField.setLabel("Confirm new password");
      newPasswordConfirmationTextField.getDocument().addDocumentListener(resetPasswordDocumentListener);
      newPasswordConfirmationTextField.addActionListener(resetAccountPasswordActionListener);

      resetPasswordButton.setFont(UIConstants.RegularFont);
      resetPasswordButton.setText("Change Password");
      resetPasswordButton.setEnabled(false);
      resetPasswordButton.addActionListener(resetAccountPasswordActionListener);

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
            .addComponent(resetPasswordCodePromptLabelOne)
            .addComponent(resetPasswordCodePromptLabelTwo)
            .addComponent(emailTextField, GroupLayout.PREFERRED_SIZE, 220, GroupLayout.PREFERRED_SIZE)
            .addComponent(resetPasswordCodeTextField, GroupLayout.PREFERRED_SIZE, 220, GroupLayout.PREFERRED_SIZE)
            .addComponent(newPasswordTextField, GroupLayout.PREFERRED_SIZE, 220, GroupLayout.PREFERRED_SIZE)
            .addComponent(newPasswordConfirmationTextField, GroupLayout.PREFERRED_SIZE, 220, GroupLayout.PREFERRED_SIZE)
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(resetPasswordCodePromptLabelOne)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(resetPasswordCodePromptLabelTwo)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(emailTextField)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(resetPasswordCodeTextField)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(newPasswordTextField)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(newPasswordConfirmationTextField)
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
               .addComponent(resetPasswordButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
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
            .addComponent(resetPasswordButton)
            .addComponent(cancelButton)
         )
         .addContainerGap()
      );
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleChangePasswordTextFieldUpdated()
   {
      resetPasswordButton.setEnabled(areRequiredFieldsFilledIn());
   }


   private boolean areRequiredFieldsFilledIn()
   {
      return (! emailTextField.getText().trim().isEmpty()) &&
             (! resetPasswordCodeTextField.getText().trim().isEmpty()) &&
             (newPasswordTextField.getDocument().getLength() > 0) &&
             (newPasswordConfirmationTextField.getDocument().getLength() > 0);
   }


   private void handleChangePasswordActionPerformed()
   {
      if (! areRequiredFieldsFilledIn())
         return;

      final String enteredEmailAddress = emailTextField.getText().trim();
      final String resetPasswordCode = resetPasswordCodeTextField.getText().trim();
      final char[] password = newPasswordTextField.getPassword();
      final char[] passwordConfirmation = newPasswordConfirmationTextField.getPassword();
      handleBlankPasswordTextFields();

      if (! AccountUtilities.isValidEmail(enteredEmailAddress))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, new String[] {"Invalid email address."}, PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK, true);
      }
      else if (! AccountUtilities.isValidPasswordResetCode(resetPasswordCode))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, new String[] {"Invalid password reset code."}, MessageDialog.PresetOptionConfiguration.OK);
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
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, new String[] {"New password confirmation does not match."}, PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK, true);
      }
      else
         parentPanel.resetPassword(enteredEmailAddress, resetPasswordCode, AccountUtilities.generatePasswordHash(enteredEmailAddress, password));

      Arrays.fill(password, '\0');
      Arrays.fill(passwordConfirmation, '\0');
   }


   private void handleCancelButtonActionPerformed()
   {
      handleBlankPasswordTextFields();
      parentPanel.dismissActiveAccountComponent();
   }


   private void handleBlankPasswordTextFields()
   {
      newPasswordTextField.setText(null);
      newPasswordConfirmationTextField.setText(null);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}