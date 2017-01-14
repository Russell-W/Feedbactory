
package com.feedbactory.client.ui.useraccount;


import com.feedbactory.client.core.useraccount.AccountUtilities;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.client.ui.component.SelfLabelledTextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


final class ChangeAccountEmailPanel
{
   final private AccountDetailsPanel parentPanel;
   final private String existingEmail;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private RoundedPanel contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private SelfLabelledTextField newEmailTextField = new SelfLabelledTextField();

   final private JButton applyUpdateEmailButton = new JButton();
   final private JButton cancelButton = new JButton();


   ChangeAccountEmailPanel(final AccountDetailsPanel parentPanel, final String existingEmail)
   {
      this.parentPanel = parentPanel;
      this.existingEmail = existingEmail;

      initialise();
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Change Email Address");
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
      final ActionListener updateAccountEmailActionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleApplyUpdateEmailButtonActionPerformed();
         }
      };

      newEmailTextField.setFont(UIConstants.RegularFont);
      newEmailTextField.setLabel("New email");
      newEmailTextField.setText(existingEmail);
      newEmailTextField.selectAll();
      newEmailTextField.addActionListener(updateAccountEmailActionListener);
      newEmailTextField.getDocument().addDocumentListener(new DocumentListener()
      {
         @Override
         final public void insertUpdate(final DocumentEvent documentEvent)
         {
            handleNewEmailTextFieldUpdated();
         }


         @Override
         final public void removeUpdate(final DocumentEvent documentEvent)
         {
            handleNewEmailTextFieldUpdated();
         }


         @Override
         final public void changedUpdate(final DocumentEvent documentEvent)
         {
            handleNewEmailTextFieldUpdated();
         }
      });

      applyUpdateEmailButton.setFont(UIConstants.RegularFont);
      applyUpdateEmailButton.setText("Apply");
      applyUpdateEmailButton.setEnabled(false);
      applyUpdateEmailButton.addActionListener(updateAccountEmailActionListener);

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
         .addComponent(newEmailTextField, GroupLayout.PREFERRED_SIZE, AccountUIConstants.EmailAddressFieldWidth, GroupLayout.PREFERRED_SIZE)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(newEmailTextField)
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
               .addComponent(applyUpdateEmailButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
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
            .addComponent(applyUpdateEmailButton)
            .addComponent(cancelButton)
         )
         .addContainerGap()
      );
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleNewEmailTextFieldUpdated()
   {
      applyUpdateEmailButton.setEnabled(areRequiredFieldsFilledIn() && hasEmailChanged());
   }


   private boolean areRequiredFieldsFilledIn()
   {
      return (! newEmailTextField.getText().trim().isEmpty());
   }


   private boolean hasEmailChanged()
   {
      // Still allow the user to change the case of their email.
      return (! newEmailTextField.getText().trim().equals(existingEmail));
   }


   private void handleApplyUpdateEmailButtonActionPerformed()
   {
      if (! areRequiredFieldsFilledIn())
         return;

      // Preserve the case of the email.
      final String newEmailEntered = newEmailTextField.getText().trim();

      if (! AccountUtilities.isValidEmail(newEmailEntered))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Error, new String[] {"Invalid email address."}, MessageDialog.PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), MessageDialog.PresetOptionSelection.OK);
      }
      else
      {
         parentPanel.dismissActiveAccountSubcomponent();
         parentPanel.updateAccountEmail(newEmailEntered);
      }
   }


   private void handleCancelButtonActionPerformed()
   {
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