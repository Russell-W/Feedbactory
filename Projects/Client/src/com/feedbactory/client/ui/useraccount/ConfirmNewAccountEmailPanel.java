
package com.feedbactory.client.ui.useraccount;


import com.feedbactory.client.core.useraccount.AccountUtilities;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.MessageDialog;
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


final class ConfirmNewAccountEmailPanel
{
   final private AccountDetailsPanel parentPanel;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private RoundedPanel contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private SelfLabelledTextField newEmailConfirmationCodeTextField = new SelfLabelledTextField();
   final private JButton resendCodeButton = new JButton();
   final private SelfLabelledPasswordField passwordTextField = new SelfLabelledPasswordField();
   final private JButton confirmNewEmailButton = new JButton();
   final private JButton cancelButton = new JButton();


   ConfirmNewAccountEmailPanel(final AccountDetailsPanel parentPanel)
   {
      this.parentPanel = parentPanel;

      initialise();
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Confirm New Email Address");
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

      initialiseEmailConfirmationControls();
      initialiseContentPanelLayout();
   }


   private void initialiseEmailConfirmationControls()
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

      final ActionListener confirmNewEmailActionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleConfirmNewEmailButtonActionPerformed();
         }
      };

      newEmailConfirmationCodeTextField.setFont(UIConstants.RegularFont);
      newEmailConfirmationCodeTextField.setLabel("Email confirmation code");
      newEmailConfirmationCodeTextField.getDocument().addDocumentListener(documentListener);
      newEmailConfirmationCodeTextField.addActionListener(confirmNewEmailActionListener);

      resendCodeButton.setFont(UIConstants.RegularFont);
      resendCodeButton.setText("Resend Code");
      resendCodeButton.setFocusable(false);
      resendCodeButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleResendCodeButtonActionPerformed();
         }
      });

      passwordTextField.setFont(UIConstants.RegularFont);
      passwordTextField.setLabel("Password");
      passwordTextField.getDocument().addDocumentListener(documentListener);
      passwordTextField.addActionListener(confirmNewEmailActionListener);

      confirmNewEmailButton.setText("Confirm Email");
      confirmNewEmailButton.setFont(UIConstants.RegularFont);
      confirmNewEmailButton.setEnabled(false);

      cancelButton.setFont(UIConstants.RegularFont);
      cancelButton.setText("Cancel");

      confirmNewEmailButton.addActionListener(confirmNewEmailActionListener);

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
            .addGroup(panelLayout.createSequentialGroup()
               .addGroup(panelLayout.createParallelGroup()
                  .addComponent(newEmailConfirmationCodeTextField, GroupLayout.PREFERRED_SIZE, 150, GroupLayout.PREFERRED_SIZE)
                  .addComponent(passwordTextField, GroupLayout.PREFERRED_SIZE, 150, GroupLayout.PREFERRED_SIZE)
               )
               .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
               .addComponent(resendCodeButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
            )
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(newEmailConfirmationCodeTextField)
            .addComponent(resendCodeButton)
         )
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(passwordTextField)
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
               .addComponent(confirmNewEmailButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
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
            .addComponent(confirmNewEmailButton)
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
      confirmNewEmailButton.setEnabled(areRequiredFieldsFilledIn());
   }


   private boolean areRequiredFieldsFilledIn()
   {
      return (passwordTextField.getDocument().getLength() > 0) &&
             (! newEmailConfirmationCodeTextField.getText().trim().isEmpty());
   }


   private void handleConfirmNewEmailButtonActionPerformed()
   {
      if (! areRequiredFieldsFilledIn())
         return;

      final char[] password = passwordTextField.getPassword();
      passwordTextField.setText(null);

      final String emailConfirmationCode = newEmailConfirmationCodeTextField.getText().trim();

      if (! AccountUtilities.isValidEmailConfirmationCode(emailConfirmationCode))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Error, new String[] {"Invalid email confirmation code."}, MessageDialog.PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), MessageDialog.PresetOptionSelection.OK);
      }
      else if (! AccountUtilities.isValidPassword(password))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Error, new String[] {"Invalid password."}, MessageDialog.PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), MessageDialog.PresetOptionSelection.OK);
      }
      else
      {
         parentPanel.dismissActiveAccountSubcomponent();
         parentPanel.confirmNewEmail(password, emailConfirmationCode);
      }

      Arrays.fill(password, '\0');
   }


   private void handleCancelButtonActionPerformed()
   {
      parentPanel.dismissActiveAccountSubcomponent();
   }


   private void handleResendCodeButtonActionPerformed()
   {
      parentPanel.resendNewEmailConfirmationCode();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}