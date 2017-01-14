
package com.feedbactory.client.ui.useraccount;


import com.feedbactory.client.core.useraccount.AccountUtilities;
import com.feedbactory.client.core.useraccount.FeedbactoryUserAccount;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.MessageDialog.MessageType;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionSelection;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.LayoutStyle;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingConstants;


final class AccountDetailsPanel
{
   final private AccountUIManager parent;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private RoundedPanel contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   private JComponent activeEmailConfirmationPrompt;

   final private JLabel emailLabel = new JLabel();
   final private JLabel emailValueLabel = new JLabel();
   final private JButton changeEmailButton = new JButton();

   final private JLabel passwordLabel = new JLabel();
   final private JLabel passwordValueLabel = new JLabel();
   final private JButton changePasswordButton = new JButton();

   final private JLabel genderLabel = new JLabel();
   final private JLabel genderValueLabel = new JLabel();

   final private JLabel dateOfBirthLabel = new JLabel();
   final private JLabel dateOfBirthValueLabel = new JLabel();

   final private JLabel sendEmailAlertsLabel = new JLabel();
   final private JLabel sendEmailAlertsValueLabel = new JLabel();
   final private JButton changeSendEmailAlertsButton = new JButton();

   final private JButton signOutButton = new JButton();
   final private JButton okButton = new JButton();

   private FeedbactoryUserAccount activeUserAccount;


   AccountDetailsPanel(final AccountUIManager parentPanel)
   {
      this.parent = parentPanel;

      initialise();
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Feedbactory Account");
      builder.setRadialGradientPaint(UIConstants.LighterPanelGradient);
      return new NimbusBorderPanel(builder);
   }


   private void initialise()
   {
      initialiseContentPanel();

      initialiseSignOutButton();

      initialiseOKButton();

      initialiseDelegatePanel();
   }


   private void initialiseContentPanel()
   {
      activeEmailConfirmationPrompt = createBlankEmailConfirmationPrompt();

      contentPanel.setBackground(UIConstants.ContentPanelColour);

      initialiseAccountDetailsComponents();
      initialiseContentPanelLayout();
   }


   private void initialiseAccountDetailsComponents()
   {
      emailLabel.setFont(UIConstants.RegularFont);
      emailLabel.setText("Email:");
      emailValueLabel.setFont(UIConstants.RegularFont);
      changeEmailButton.setFont(UIConstants.RegularFont);
      changeEmailButton.setText("Change");
      changeEmailButton.setFocusable(false);

      passwordLabel.setFont(UIConstants.RegularFont);
      passwordLabel.setText("Password:");
      passwordValueLabel.setFont(UIConstants.RegularFont);
      passwordValueLabel.setText("************");
      changePasswordButton.setFont(UIConstants.RegularFont);
      changePasswordButton.setText("Change");
      changePasswordButton.setFocusable(false);

      genderLabel.setFont(UIConstants.RegularFont);
      genderLabel.setText("Gender:");
      genderValueLabel.setFont(UIConstants.RegularFont);

      dateOfBirthLabel.setFont(UIConstants.RegularFont);
      dateOfBirthLabel.setText("Date of birth:");
      dateOfBirthValueLabel.setFont(UIConstants.RegularFont);

      sendEmailAlertsLabel.setFont(UIConstants.RegularFont);
      sendEmailAlertsLabel.setText("Email alerts:");
      sendEmailAlertsValueLabel.setFont(UIConstants.RegularFont);
      changeSendEmailAlertsButton.setFont(UIConstants.RegularFont);
      changeSendEmailAlertsButton.setText("Change");
      changeSendEmailAlertsButton.setFocusable(false);

      changeEmailButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleNewEmailButtonActionPerformed();
         }
      });

      changePasswordButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleChangePasswordButtonActionPerformed();
         }
      });

      changeSendEmailAlertsButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleChangeSendEmailAlertsButtonActionPerformed();
         }
      });
   }


   private void initialiseSignOutButton()
   {
      signOutButton.setFont(UIConstants.RegularFont);
      signOutButton.setText("Sign Out");
      signOutButton.setFocusable(false);

      signOutButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleSignOutButtonActionPerformed();
         }
      });
   }


   private void initialiseOKButton()
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


   private void initialiseContentPanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(contentPanel);
      contentPanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(activeEmailConfirmationPrompt)
            .addGroup(panelLayout.createSequentialGroup()
               .addGroup(panelLayout.createParallelGroup()
                  .addComponent(emailLabel)
                  .addComponent(passwordLabel)
                  .addComponent(genderLabel)
                  .addComponent(dateOfBirthLabel)
                  .addComponent(sendEmailAlertsLabel)
               )
               .addPreferredGap(ComponentPlacement.RELATED)
               .addGroup(panelLayout.createParallelGroup()
                  .addComponent(emailValueLabel, GroupLayout.PREFERRED_SIZE, 180, GroupLayout.PREFERRED_SIZE)
                  .addComponent(passwordValueLabel)
                  .addComponent(genderValueLabel)
                  .addComponent(dateOfBirthValueLabel)
                  .addComponent(sendEmailAlertsValueLabel)
               )
               .addPreferredGap(ComponentPlacement.RELATED)
               .addGroup(panelLayout.createParallelGroup()
                  .addComponent(changeEmailButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
                  .addComponent(changePasswordButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
                  .addComponent(changeSendEmailAlertsButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
                  .addComponent(signOutButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               )
            )
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      // Smaller gap at the top here, due to the existing extra gap for the (usually invisible) activeEmailConfirmationPrompt.
      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(activeEmailConfirmationPrompt)
         .addPreferredGap(ComponentPlacement.RELATED)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(emailLabel)
            .addComponent(emailValueLabel)
            .addComponent(changeEmailButton)
         )
         .addPreferredGap(ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(passwordLabel)
            .addComponent(passwordValueLabel)
            .addComponent(changePasswordButton)
         )
         .addPreferredGap(ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(genderLabel)
            .addComponent(genderValueLabel)
         )
         .addPreferredGap(ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(dateOfBirthLabel)
            .addComponent(dateOfBirthValueLabel)
         )
         .addPreferredGap(ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(sendEmailAlertsLabel)
            .addComponent(sendEmailAlertsValueLabel)
            .addComponent(changeSendEmailAlertsButton)
         )
         .addPreferredGap(ComponentPlacement.UNRELATED)
         .addComponent(signOutButton)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.linkSize(SwingConstants.VERTICAL, changeEmailButton, genderLabel, dateOfBirthLabel);
   }


   private void initialiseDelegatePanel()
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


   /****************************************************************************
    * 
    ***************************************************************************/


   private JComponent createBlankEmailConfirmationPrompt()
   {
      final JPanel panel = new JPanel(null);
      panel.setBackground(UIConstants.ContentPanelColour);
      return panel;
   }


   private void setActiveEmailNotification(final JComponent componentToActivate)
   {
      final GroupLayout panelLayout = (GroupLayout) contentPanel.getLayout();
      panelLayout.replace(activeEmailConfirmationPrompt, componentToActivate);

      activeEmailConfirmationPrompt = componentToActivate;
   }


   private void handleNewEmailButtonActionPerformed()
   {
      final String emailToDisplay = (activeUserAccount.pendingEmailAddress != null) ? activeUserAccount.pendingEmailAddress : activeUserAccount.emailAddress;
      parent.showFormSubcomponent(new ChangeAccountEmailPanel(this, emailToDisplay).getDelegate(), true);
   }


   private void handleChangePasswordButtonActionPerformed()
   {
      parent.showFormSubcomponent(new ChangeAccountPasswordPanel(this).getDelegate(), true);
   }


   private void handleChangeSendEmailAlertsButtonActionPerformed()
   {
      parent.showFormSubcomponent(new ChangeSendEmailAlertsPanel(this, activeUserAccount.sendEmailAlerts).getDelegate(), true);
   }


   private void handleSignOutButtonActionPerformed()
   {
      parent.signOut();
   }


   private void handleOKButtonActionPerformed()
   {
      parent.dismissActiveAccountComponent();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleUserAccountDetailsUpdated(final FeedbactoryUserAccount userAccount)
   {
      activeUserAccount = userAccount;

      if (userAccount != null)
      {
         if (userAccount.pendingEmailAddress == null)
         {
            setActiveEmailNotification(createBlankEmailConfirmationPrompt());
            emailValueLabel.setText(userAccount.emailAddress);
         }
         else
         {
            final String[] confirmationPrompt = new String[] {"Before you can sign in using your updated",
                                                              "email address you need to confirm it by",
                                                              "entering the code that was sent to you."};
            final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Warning, confirmationPrompt, new String[] {"Enter Confirmation Code"});

            final MessageDialog dialog = new MessageDialog(builder);
            dialog.addActionListener(new MessageDialog.ActionListener()
            {
               @Override
               final public void actionPerformed(final MessageDialog messageDialog, final PresetOptionSelection optionSelection, final int optionSelectionIndex)
               {
                  /* Don't auto-dismiss the confirm new email panel when the Feedbactory pad is hidden, since the users will need to minimise it while digging through their
                   * emails to find the email confirmation code.
                   */
                  parent.showFormSubcomponent(new ConfirmNewAccountEmailPanel(AccountDetailsPanel.this).getDelegate(), false);
               }
            });

            setActiveEmailNotification(dialog.getDelegate());

            emailValueLabel.setText(userAccount.pendingEmailAddress);
         }

         genderValueLabel.setText(userAccount.gender.toString());
         dateOfBirthValueLabel.setText(SimpleDateFormat.getDateInstance(DateFormat.LONG).format(userAccount.dateOfBirth));
         sendEmailAlertsValueLabel.setText(userAccount.sendEmailAlerts ? "Send" : "Do not send");
      }
      else
      {
         setActiveEmailNotification(createBlankEmailConfirmationPrompt());

         emailValueLabel.setText("");
         genderValueLabel.setText("");
         dateOfBirthValueLabel.setText("");
      }
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }


   final void userAccountDetailsUpdated(final FeedbactoryUserAccount userAccount)
   {
      handleUserAccountDetailsUpdated(userAccount);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void showMessageDialog(final MessageDialog messageDialog, final PresetOptionSelection defaultAction)
   {
      parent.showMessageDialog(messageDialog, defaultAction, true);
   }


   final void updateAccountEmail(final String newEmail)
   {
      parent.updateEmail(newEmail);
   }


   final void resendNewEmailConfirmationCode()
   {
      parent.resendChangeOfEmailConfirmationCode();
   }


   final void confirmNewEmail(final char[] password, final String newEmailConfirmationCode)
   {
      parent.confirmChangeOfEmail(newEmailConfirmationCode,
                                  AccountUtilities.generatePasswordHash(activeUserAccount.emailAddress, password),
                                  AccountUtilities.generatePasswordHash(activeUserAccount.pendingEmailAddress, password));
   }


   final void updateAccountPassword(final char[] password, final char[] newPassword)
   {
      parent.updatePasswordHash(AccountUtilities.generatePasswordHash(activeUserAccount.emailAddress, password),
                                AccountUtilities.generatePasswordHash(activeUserAccount.emailAddress, newPassword));
   }


   final void updateSendEmailAlerts(final boolean sendEmailAlerts)
   {
      parent.updateSendEmailAlerts(sendEmailAlerts);
   }


   final void dismissActiveAccountSubcomponent()
   {
      parent.dismissActiveAccountSubcomponent();
   }
}