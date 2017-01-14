
package com.feedbactory.client.ui.useraccount;


import com.feedbactory.client.core.useraccount.AccountUtilities;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.*;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionSelection;
import java.awt.event.*;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


final public class AccountSignInPanel
{
   final private AccountUIManager parentPanel;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private RoundedPanel signInPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private SelfLabelledTextField emailTextField = new SelfLabelledTextField();
   final private SelfLabelledPasswordField passwordTextField = new SelfLabelledPasswordField();
   final private HyperlinkLabel forgotPasswordLink = new HyperlinkLabel();
   final private JCheckBox keepMeSignedInCheckBox = new JCheckBox();

   final private JButton signInButton = new JButton();
   final private JButton cancelButton = new JButton();

   final private JLabel signUpPromptLabel = new JLabel();
   final private JButton signUpButton = new JButton();

   final private JLabel activateAccountPromptLabel = new JLabel();
   final private HyperlinkLabel activateAccountLink = new HyperlinkLabel();


   public AccountSignInPanel(final AccountUIManager parentPanel, final String emailAddress)
   {
      this.parentPanel = parentPanel;

      initialise(emailAddress);
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Sign In");
      builder.setRadialGradientPaint(UIConstants.LighterPanelGradient);
      return new NimbusBorderPanel(builder);
   }


   private void initialise(final String emailAddress)
   {
      initialiseSignInPanel(emailAddress);
      initialiseSignUpPrompt();
      initialiseActivateAccountPrompt();
      initialiseDelegatePanel();
   }


   private void initialiseSignInPanel(final String emailAddress)
   {
      signInPanel.setBackground(UIConstants.ContentPanelColour);

      initialiseSignInFields(emailAddress);
      initialiseSignInPanelLayout();
   }


   private void initialiseSignInFields(final String emailAddress)
   {
      emailTextField.setFont(UIConstants.RegularFont);
      emailTextField.setLabel("Email");
      emailTextField.setText(emailAddress);

      passwordTextField.setFont(UIConstants.RegularFont);
      passwordTextField.setLabel("Password");

      keepMeSignedInCheckBox.setFont(UIConstants.RegularFont);
      keepMeSignedInCheckBox.setText("Keep me signed in");
      
      final DocumentListener documentListener = new DocumentListener()
      {
         @Override
         final public void insertUpdate(final DocumentEvent documentEvent)
         {
            handleInputComponentUpdated();
         }


         @Override
         final public void removeUpdate(final DocumentEvent documentEvent)
         {
            handleInputComponentUpdated();
         }


         @Override
         final public void changedUpdate(final DocumentEvent documentEvent)
         {
            handleInputComponentUpdated();
         }
      };

      final ActionListener signInActionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleSignInActionPerformed();
         }
      };

      emailTextField.getDocument().addDocumentListener(documentListener);
      emailTextField.addActionListener(signInActionListener);

      passwordTextField.getDocument().addDocumentListener(documentListener);
      passwordTextField.addActionListener(signInActionListener);

      signInButton.setFont(UIConstants.RegularFont);
      signInButton.setText("Sign In");
      signInButton.setEnabled(false);
      signInButton.addActionListener(signInActionListener);

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

      forgotPasswordLink.setFont(UIConstants.SmallFont);
      forgotPasswordLink.setHyperlink("Forgot Password", null);
      forgotPasswordLink.addMouseListener(new MouseAdapter()
      {
         @Override
         final public void mouseClicked(final MouseEvent mouseEvent)
         {
            handleForgotPasswordButtonActionPerformed();
         }
      });
   }


   private void initialiseSignInPanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(signInPanel);
      signInPanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(emailTextField, GroupLayout.PREFERRED_SIZE, 195, GroupLayout.PREFERRED_SIZE)
            .addGroup(panelLayout.createSequentialGroup()
               .addComponent(passwordTextField, GroupLayout.PREFERRED_SIZE, AccountUIConstants.PasswordFieldWidth, GroupLayout.PREFERRED_SIZE)
               .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
               .addComponent(forgotPasswordLink, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            )
            .addComponent(keepMeSignedInCheckBox)
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(emailTextField)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(passwordTextField)
            .addComponent(forgotPasswordLink)
         )
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(keepMeSignedInCheckBox)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );
   }


   private void initialiseSignUpPrompt()
   {
      signUpPromptLabel.setFont(UIConstants.RegularFont);
      signUpPromptLabel.setText("Don't have an account?");

      signUpButton.setFont(UIConstants.RegularFont);
      signUpButton.setText("Sign up - it's easy and free!");

      signUpButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleSignUpButtonActionPerformed();
         }
      });
   }


   private void initialiseActivateAccountPrompt()
   {
      activateAccountPromptLabel.setFont(UIConstants.RegularFont);
      activateAccountPromptLabel.setText("Have an account activation code?");

      activateAccountLink.setFont(UIConstants.SmallFont);
      activateAccountLink.setHyperlink("Activate Account", null);

      activateAccountLink.addMouseListener(new MouseAdapter()
      {
         @Override
         final public void mouseClicked(final MouseEvent mouseEvent)
         {
            handleActivateAccountButtonActionPerformed();
         }
      });
   }


   private void initialiseDelegatePanel()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getContentPane());
      delegatePanel.getContentPane().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(signInPanel)
            .addGroup(panelLayout.createSequentialGroup()
               .addGap(UIConstants.MediumContainerGapSize)
               .addGroup(panelLayout.createParallelGroup()
                  .addGroup(panelLayout.createSequentialGroup()
                     .addComponent(signInButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
                     .addPreferredGap(ComponentPlacement.UNRELATED)
                     .addComponent(cancelButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
                  )
                  .addComponent(signUpPromptLabel)
                  .addComponent(signUpButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
                  .addComponent(activateAccountPromptLabel)
                  .addComponent(activateAccountLink, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
               )
               .addGap(UIConstants.MediumContainerGapSize)
            )
         )
         .addContainerGap()
      );

      // Note the gaps doubled up at the base to equal the size of the doubled-up horizontal container gaps.
      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(signInPanel)
         .addPreferredGap(ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(signInButton)
            .addComponent(cancelButton)
         )
         .addGap(UIConstants.LargeUnrelatedComponentGapSize)
         .addComponent(signUpPromptLabel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(signUpButton)
         .addGap(UIConstants.LargeUnrelatedComponentGapSize)
         .addComponent(activateAccountPromptLabel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(activateAccountLink)
         .addContainerGap(UIConstants.MediumContainerGapSize * 2, Integer.MAX_VALUE)
      );
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleInputComponentUpdated()
   {
      signInButton.setEnabled(areRequiredFieldsFilledIn());
   }


   private boolean areRequiredFieldsFilledIn()
   {
      return (! emailTextField.getText().trim().isEmpty()) &&
             (passwordTextField.getDocument().getLength() > 0);
   }


   private void handleSignInActionPerformed()
   {
      if (! areRequiredFieldsFilledIn())
         return;

      final String emailAddress = emailTextField.getText().trim();

      final char[] password = passwordTextField.getPassword();
      blankPasswordTextField();

      if (! AccountUtilities.isValidEmail(emailAddress))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Error, new String[] {"Invalid email address."}, MessageDialog.PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK, true);
      }
      else if (! AccountUtilities.isValidPassword(password))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Error, new String[] {"Invalid password."}, MessageDialog.PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK, true);
      }
      else
         parentPanel.signIn(emailAddress, AccountUtilities.generatePasswordHash(emailAddress, password), keepMeSignedInCheckBox.isSelected());

      Arrays.fill(password, '\0');
   }


   private void handleCancelButtonActionPerformed()
   {
      blankPasswordTextField();

      parentPanel.dismissActiveAccountComponent();
   }


   private void handleForgotPasswordButtonActionPerformed()
   {
      blankPasswordTextField();

      final String emailAddress = emailTextField.getText().trim();
      parentPanel.showSendPasswordResetEmailPanel(AccountUtilities.isValidEmail(emailAddress) ? emailAddress : null);
   }


   private void handleSignUpButtonActionPerformed()
   {
      blankPasswordTextField();

      parentPanel.showSignUpPanel();
   }


   private void handleActivateAccountButtonActionPerformed()
   {
      blankPasswordTextField();

      parentPanel.showAccountActivationPanel(null);
   }


   private void blankPasswordTextField()
   {
      passwordTextField.setText(null);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}