
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


final class ChangeAccountPasswordPanel
{
   final private AccountDetailsPanel parentPanel;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private RoundedPanel contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private SelfLabelledPasswordField passwordTextField = new SelfLabelledPasswordField();
   final private SelfLabelledPasswordField newPasswordTextField = new SelfLabelledPasswordField();
   final private SelfLabelledPasswordField newPasswordConfirmationTextField = new SelfLabelledPasswordField();

   final private JButton applyUpdatePasswordButton = new JButton();
   final private JButton cancelButton = new JButton();


   ChangeAccountPasswordPanel(final AccountDetailsPanel parentPanel)
   {
      this.parentPanel = parentPanel;

      initialise();
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Change Password");
      builder.setRadialGradientPaint(UIConstants.LighterPanelGradient);
      return new NimbusBorderPanel(builder);
   }


   private void initialise()
   {
      initialiseContentPanel();
      initialiseDelegatePanel();
   }


   private void initialiseContentPanel()
   {
      contentPanel.setBackground(UIConstants.ContentPanelColour);
      initialiseNewPasswordControls();
      initialiseContentPanelLayout();
   }


   private void initialiseNewPasswordControls()
   {
      final DocumentListener documentListener = new DocumentListener()
      {
         @Override
         final public void insertUpdate(final DocumentEvent documentEvent)
         {
            handleInputFieldUpdated();
         }


         @Override
         final public void removeUpdate(final DocumentEvent documentEvent)
         {
            handleInputFieldUpdated();
         }


         @Override
         final public void changedUpdate(final DocumentEvent documentEvent)
         {
            handleInputFieldUpdated();
         }
      };

      final ActionListener updateAccountPasswordActionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleApplyUpdatePasswordButtonActionPerformed();
         }
      };

      passwordTextField.setFont(UIConstants.RegularFont);
      passwordTextField.setLabel("Current password");
      passwordTextField.getDocument().addDocumentListener(documentListener);
      passwordTextField.addActionListener(updateAccountPasswordActionListener);

      newPasswordTextField.setFont(UIConstants.RegularFont);
      newPasswordTextField.setLabel("New password (minimum length 8)");
      newPasswordTextField.getDocument().addDocumentListener(documentListener);
      newPasswordTextField.addActionListener(updateAccountPasswordActionListener);

      newPasswordConfirmationTextField.setFont(UIConstants.RegularFont);
      newPasswordConfirmationTextField.setLabel("Confirm new password");
      newPasswordConfirmationTextField.getDocument().addDocumentListener(documentListener);
      newPasswordConfirmationTextField.addActionListener(updateAccountPasswordActionListener);

      applyUpdatePasswordButton.setFont(UIConstants.RegularFont);
      applyUpdatePasswordButton.setText("Apply");
      applyUpdatePasswordButton.setEnabled(false);
      applyUpdatePasswordButton.addActionListener(updateAccountPasswordActionListener);

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
            .addComponent(passwordTextField, GroupLayout.PREFERRED_SIZE, 200, GroupLayout.PREFERRED_SIZE)
            .addComponent(newPasswordTextField, GroupLayout.PREFERRED_SIZE, 200, GroupLayout.PREFERRED_SIZE)
            .addComponent(newPasswordConfirmationTextField, GroupLayout.PREFERRED_SIZE, 200, GroupLayout.PREFERRED_SIZE)
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(passwordTextField)
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
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(contentPanel)
            .addGroup(panelLayout.createSequentialGroup()
               .addComponent(applyUpdatePasswordButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
               .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
               .addComponent(cancelButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
            )
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(contentPanel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(applyUpdatePasswordButton)
            .addComponent(cancelButton)
         )
         .addContainerGap()
      );
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleInputFieldUpdated()
   {
      applyUpdatePasswordButton.setEnabled(areRequiredFieldsFilledIn());
   }


   private boolean areRequiredFieldsFilledIn()
   {
      return (passwordTextField.getDocument().getLength() > 0) &&
             (newPasswordTextField.getDocument().getLength() > 0) &&
             (newPasswordConfirmationTextField.getDocument().getLength() > 0);
   }


   private void handleApplyUpdatePasswordButtonActionPerformed()
   {
      if (! areRequiredFieldsFilledIn())
         return;

      final char[] password = passwordTextField.getPassword();
      final char[] newPassword = newPasswordTextField.getPassword();
      final char[] newPasswordConfirmation = newPasswordConfirmationTextField.getPassword();

      blankPasswordTextFields();

      if (! AccountUtilities.isValidPassword(password))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, new String[] {"Invalid current password."}, PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK);
      }
      else if (! AccountUtilities.isValidPassword(newPassword))
      {
         final String[] message = new String[] {"Your password must be a minimum of 8 characters long", "and contain at least two letters and two non-letters."};
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, message, PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK);
      }
      else if (! Arrays.equals(newPassword, newPasswordConfirmation))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, new String[] {"New password confirmation does not match."}, PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK);
      }
      else if (Arrays.equals(password, newPassword))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, new String[] {"New password cannot be identical to the old."}, PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK);
      }
      else
      {
         parentPanel.dismissActiveAccountSubcomponent();
         parentPanel.updateAccountPassword(password, newPassword);
      }

      Arrays.fill(password, '\0');
      Arrays.fill(newPassword, '\0');
      Arrays.fill(newPasswordConfirmation, '\0');
   }


   private void blankPasswordTextFields()
   {
      passwordTextField.setText(null);
      newPasswordTextField.setText(null);
      newPasswordConfirmationTextField.setText(null);
   }


   private void handleCancelButtonActionPerformed()
   {
      blankPasswordTextFields();
      parentPanel.dismissActiveAccountSubcomponent();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}