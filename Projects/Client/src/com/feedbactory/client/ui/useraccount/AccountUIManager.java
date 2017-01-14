/* Memos:
 * - This class is managing the behaviour for when its components and subcomponents can be automatically dismissed, eg. via pressing Escape or closing the Feedbactory pad.
 *   This is because there is some non-trivial logic for when components should be dismissed. When the account details panel is displayed on its own then it's fine to be able to
 *   automatically close it, but when the confirm new email sub-panel is being displayed over the top of the account details panel, neither panel should then be automatically
 *   dismissed - it would be annoying for the user to have them closed when they switch between Feedbactory and their email window to retrieve the confirmation code.
 *   So all requests funnelled through this UI manager class to display account components will be forced to display those components as persistent panels, and when a signal is
 *   received from the parent UI (the FeedbactoryPad) that it might be time to dismiss some panels, this manager class manually handles the operation. This is in contrast to
 *   having the FeedbactoryPad's MessageDialogDisplayManager automatically handle closing any component that is tagged as temporary.
 */

package com.feedbactory.client.ui.useraccount;


import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.client.core.network.ProcessedRequestBasicResponse;
import com.feedbactory.client.core.useraccount.AccountEventAdapter;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.client.core.useraccount.AuthenticationRequestResponse;
import com.feedbactory.client.core.useraccount.FeedbactoryUserAccount;
import com.feedbactory.client.ui.UIManager;
import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.BrowserUIManagerService;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.MessageDialog.MessageType;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionConfiguration;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionSelection;
import com.feedbactory.client.ui.pad.FeedbactoryPadUIView;
import com.feedbactory.shared.network.AuthenticationStatus;
import com.feedbactory.shared.network.BasicOperationStatus;
import com.feedbactory.shared.useraccount.Gender;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;


final public class AccountUIManager
{
   static final private String PersistentSessionEmailPreferencesKey = "PersistentSessionEmail";

   final private AccountSessionManager userAccountManager;

   final private UIManager uiManager;

   final private FeedbactoryPadUIView feedbactoryPad;

   private JComponent activeAccountComponent;
   private AccountDetailsPanel activeAccountDetailsPanel;
   private boolean isComponentTemporary = true;
   private JComponent activeSubcomponent;
   private boolean isSubcomponentTemporary = true;

   /* A variable used for the convenience of the user between sign-ins, so that they don't have to re-enter their email.
    * Not used operationally while a user is signed in.
    */
   private String mostRecentEmail;

   final private Set<AccountUIStatusListener> userAccountUIStatusListeners = new CopyOnWriteArraySet<AccountUIStatusListener>();

   private FeedbactoryUserAccount signedInUserAccount;


   public AccountUIManager(final AccountSessionManager userAccountManager, final UIManager uiManager, final FeedbactoryPadUIView feedbactoryPad)
   {
      this.userAccountManager = userAccountManager;
      this.uiManager = uiManager;
      this.feedbactoryPad = feedbactoryPad;

      initialise();
   }


   private void initialise()
   {
      restorePersistentSessionEmail();

      initialiseUserAccountListener();
   }


   private void initialiseUserAccountListener()
   {
      userAccountManager.addUserAccountEventUIListener(new AccountEventAdapter()
      {
         @Override
         final public void signedInToUserAccount(final FeedbactoryUserAccount userAccount)
         {
            handleSignedInToUserAccount(userAccount);
         }


         @Override
         final public void userAccountDetailsUpdated(final FeedbactoryUserAccount userAccount)
         {
            handleUserAccountDetailsUpdated(userAccount);
         }


         @Override
         public void signedOutOfUserAccount(FeedbactoryUserAccount userAccount)
         {
            handleSignedOutOfUserAccount();
         }
      });
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSignedInToUserAccount(final FeedbactoryUserAccount userAccount)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            signedInUserAccount = userAccount;
            mostRecentEmail = userAccount.emailAddress;

            dismissActiveComponents();
         }
      });
   }


   private void handleUserAccountDetailsUpdated(final FeedbactoryUserAccount userAccount)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            signedInUserAccount = userAccount;
            mostRecentEmail = userAccount.emailAddress;

            // This should always be true due to the workings of the account UI, but just in case...
            if (activeAccountDetailsPanel != null)
            {
               activeAccountDetailsPanel.userAccountDetailsUpdated(userAccount);

               /* Some of the operations that this class handles may sometimes result in the account details panel growing or shrinking in size, eg.
                * when the user changes their email, producing an email confirmation dialog within the account details panel. This will alter the size
                * of the panel, requiring an immediate repack.
                * I could place the hint to repack the Feedbactory pad throughout the various handlers (for updating account details) below but placing it here
                * instead seems just as fine since this method will be called any time that the account details are updated. The only downside is that sometimes
                * it will be over zealous in calling the repack hint when the panel remains the same size, but there's also no harm done.
                */
               feedbactoryPad.requestRepack();
            }
         }
      });
   }


   private void handleSignedOutOfUserAccount()
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            signedInUserAccount = null;
            dismissActiveComponents();
         }
      });
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void savePersistentSessionEmail()
   {
      if (mostRecentEmail != null)
      {
         final Preferences feedbactorySettings = Preferences.userNodeForPackage(getClass());
         feedbactorySettings.put(PersistentSessionEmailPreferencesKey, mostRecentEmail);
      }
   }


   private void restorePersistentSessionEmail()
   {
      final Preferences feedbactorySettings = Preferences.userNodeForPackage(getClass());
      mostRecentEmail = feedbactorySettings.get(PersistentSessionEmailPreferencesKey, null);
   }


   private void clearPersistentSessionEmail()
   {
      final Preferences feedbactorySettings = Preferences.userNodeForPackage(getClass());
      feedbactorySettings.remove(PersistentSessionEmailPreferencesKey);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void notifyAccountUIStatusUpdated(final AccountUIStatus uiStatus, final boolean isSignInActivity)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            notifyAccountUIStatusUpdatedEDT(uiStatus, isSignInActivity);
         }
      });
   }


   private void notifyAccountUIStatusUpdatedEDT(final AccountUIStatus uiStatus, final boolean isSignInActivity)
   {
      for (final AccountUIStatusListener listener : userAccountUIStatusListeners)
         listener.feedbactoryUserAccountUIStatusChanged(uiStatus, isSignInActivity);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void showAccountComponent(final JComponent componentToActivate, final boolean isTemporary)
   {
      // Show the new component before dismissing the existing one, to avoid the pad from snapping between locked and unlocked states.
      final JComponent showingComponent = activeAccountComponent;

      /* Regardless of whether a component is temporary or not, this UI manager is overriding the behaviour for when its components and subcomponents
       * can be dismissed. So, always pass false to the parent for the temporary flag so that the component will be persistent, but take note of the
       * component's preference so that it can be managed correctly.
       */
      feedbactoryPad.showFormComponent(componentToActivate, false);
      activeAccountComponent = componentToActivate;
      isComponentTemporary = isTemporary;
      attachActiveComponentCancelKeyBinding();

      if (showingComponent != null)
      {
         feedbactoryPad.dismissLockingComponent(showingComponent);
         activeAccountDetailsPanel = null;
      }
   }


   private void attachActiveComponentCancelKeyBinding()
   {
      final InputMap inputMap = activeAccountComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      final ActionMap actionMap = activeAccountComponent.getActionMap();

      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelComponent");

      actionMap.put("cancelComponent", new AbstractAction()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            dismissActiveAccountComponent();
         }
      });
   }


   private void showAccountDetailsPanel()
   {
      final AccountDetailsPanel newAccountDetailsPanel = new AccountDetailsPanel(this);
      newAccountDetailsPanel.userAccountDetailsUpdated(signedInUserAccount);
      showAccountComponent(newAccountDetailsPanel.getDelegate(), true);
      activeAccountDetailsPanel = newAccountDetailsPanel;
   }


   private void showSignInPanel()
   {
      showAccountComponent(new AccountSignInPanel(this, mostRecentEmail).getDelegate(), true);
   }


   private void dismissActiveComponents()
   {
      if (activeAccountComponent != null)
      {
         if (activeSubcomponent != null)
            dismissActiveSubcomponent();

         feedbactoryPad.dismissLockingComponent(activeAccountComponent);

         activeAccountComponent = null;
         activeAccountDetailsPanel = null;
         isComponentTemporary = true;
      }
   }


   private void showAccountSubcomponent(final JComponent component, final boolean temporaryComponent)
   {
      /* As above, regardless of whether a component is temporary or not, this UI manager is overriding the behaviour for when its components and subcomponents
       * can be dismissed. So, always pass false to the parent for the temporary flag so that the component will be persistent, but take note of the
       * component's preference so that it can be managed correctly.
       */
      feedbactoryPad.showFormSubcomponent(component, false);
      activeSubcomponent = component;
      attachActiveSubComponentCancelKeyBinding();
      isSubcomponentTemporary = temporaryComponent;
   }


   private void attachActiveSubComponentCancelKeyBinding()
   {
      final InputMap inputMap = activeSubcomponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      final ActionMap actionMap = activeSubcomponent.getActionMap();

      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelSubcomponent");

      actionMap.put("cancelSubcomponent", new AbstractAction()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            dismissActiveSubcomponent();
         }
      });
   }


   private void dismissActiveSubcomponent()
   {
      feedbactoryPad.dismissLockingComponent(activeSubcomponent);
      activeSubcomponent = null;
      isSubcomponentTemporary = true;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleShowRootComponent()
   {
      if (activeAccountComponent == null)
      {
         if (signedInUserAccount == null)
            showSignInPanel();
         else
            showAccountDetailsPanel();
      }
   }


   private void handleRequestDismissActiveComponents()
   {
      if (isComponentTemporary && isSubcomponentTemporary)
         dismissActiveComponents();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void showTimedInformationDialog(final String[] message)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            showTimedInformationDialogEDT(MessageType.Information, message, 1000L);
         }
      });
   }


   private void showTimedInformationDialogEDT(final MessageType messageType, final String[] message, final long durationMilliseconds)
   {
      final int okButtonIndex = PresetOptionConfiguration.OK.toButtonIndex(PresetOptionSelection.OK);
      final MessageDialog.Builder messageDialogBuilder = new MessageDialog.Builder(messageType, message, PresetOptionConfiguration.OK);
      showTimedMessageDialog(messageDialogBuilder, true, okButtonIndex, durationMilliseconds);
   }


   private void showAcknowledgeMessageDialog(final MessageType messageType, final String[] message)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            showAcknowledgeMessageDialogEDT(messageType, message);
         }
      });
   }


   private void showAcknowledgeMessageDialogEDT(final MessageType messageType, final String[] message)
   {
      final MessageDialog.Builder messageDialogBuilder = new MessageDialog.Builder(messageType, message, PresetOptionConfiguration.OK);
      showMessageDialog(new MessageDialog(messageDialogBuilder), PresetOptionSelection.OK, false);
   }


   private void showFailedAuthenticationTooManyAttemptsMessageDialog()
   {
      final String[] message = new String[] {"Too many authentication attempts.", "Please try again later."};
      showAcknowledgeMessageDialog(MessageType.Error, message);
   }


   private void processDefaultFailedBasicRequestResponse(final ProcessedRequestBasicResponse basicRequestResponse)
   {
      if ((basicRequestResponse.requestStatus == NetworkRequestStatus.OK) && (basicRequestResponse.data == BasicOperationStatus.Failed))
         showGeneralFailedRequestMessageDialog();
   }


   private void showGeneralFailedRequestMessageDialog()
   {
      final String[] message = new String[] {"An error occurred while processing the request."};
      showAcknowledgeMessageDialog(MessageType.Error, message);
   }


   private void handleOpenURL(final String url)
   {
      final BrowserUIManagerService browserManagerService = uiManager.getBrowserManagerService();
      final BrowserService newBrowserService = browserManagerService.newBrowserService();
      newBrowserService.openURL(url);
      browserManagerService.setActiveBrowserService(newBrowserService);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSignUp(final String email, final Gender gender, final long dateOfBirth, final boolean sendEmailAlerts)
   {
      notifyAccountUIStatusUpdatedEDT(AccountUIStatus.Busy, false);

      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final Runnable task = new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               processSignUpResult(email, sendEmailAlerts, userAccountManager.signUp(email, gender, dateOfBirth, sendEmailAlerts));
            }
            finally
            {
               notifyAccountUIStatusUpdated(AccountUIStatus.Idle, false);
            }
         }
      };

      executor.execute(task);
      executor.shutdown();
   }


   private void processSignUpResult(final String email, final boolean sendEmailAlerts, final AuthenticationRequestResponse signUpResponse)
   {
      if (signUpResponse.networkStatus == NetworkRequestStatus.OK)
      {
         // The authentication status for sign ups is hardwired to SuccessAccountNotActivated.
         if (signUpResponse.authenticationResult == AuthenticationStatus.SuccessAccountNotActivated)
            processSignUpSuccessful(email);
         else if (signUpResponse.authenticationResult == AuthenticationStatus.FailedCapacityReached)
            processSignUpCapacityReached(sendEmailAlerts);
         else
            throw new AssertionError("Unhandled account sign up authentication result: " + signUpResponse.authenticationResult);
      }
   }


   private void processSignUpSuccessful(final String email)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            final String[] message = new String[] {"A Feedbactory account activation code has been",
                                                   "emailed to you.",
                                                   "You will need to paste or enter this code into the",
                                                   "next page to complete your account registration."};

            showAcknowledgeMessageDialogEDT(MessageType.Information, message);

            showAccountActivationPanel(email);
         }
      });
   }


   private void processSignUpCapacityReached(final boolean sendEmailAlerts)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            final ArrayList<String> message = new ArrayList<String>();
            message.add("Sorry, no more Feedbactory sign ups are available at this time.");
            message.add("");
            message.add("This is a temporary limitation put in place during this early");
            message.add("release period to ensure that the platform is refined and ready");
            message.add("for a wider release in the very near future.");
            message.add("");

            if (sendEmailAlerts)
            {
               message.add("Stay tuned - you will be notified soon via your nominated email");
               message.add("when rating submissions have been made more widely available.");
            }
            else
               message.add("Stay tuned - check back with us soon for more announcements.");

            showAcknowledgeMessageDialogEDT(MessageType.Information, message.toArray(new String[message.size()]));
         }
      });
   }


   private void handleResendActivationCode(final String email)
   {
      notifyAccountUIStatusUpdatedEDT(AccountUIStatus.Busy, false);

      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final Runnable task = new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               processResendActivationCodeStatus(userAccountManager.resendActivationCode(email));
            }
            finally
            {
               notifyAccountUIStatusUpdated(AccountUIStatus.Idle, false);
            }
         }
      };

      executor.execute(task);
      executor.shutdown();
   }


   private void processResendActivationCodeStatus(final ProcessedRequestBasicResponse resendActivationCodeResponse)
   {
      if ((resendActivationCodeResponse.requestStatus == NetworkRequestStatus.OK) && (resendActivationCodeResponse.data == BasicOperationStatus.OK))
         showTimedInformationDialog(new String[] {"Email sent."});
      else
         processDefaultFailedBasicRequestResponse(resendActivationCodeResponse);
   }


   private void handleActivateAccount(final String email, final String activationCode, final byte[] initialPasswordHash)
   {
      notifyAccountUIStatusUpdatedEDT(AccountUIStatus.Busy, true);

      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final Runnable task = new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               processActivationAccountResult(userAccountManager.activateAccount(email, activationCode, initialPasswordHash));
            }
            finally
            {
               notifyAccountUIStatusUpdated(AccountUIStatus.Idle, true);
            }
         }
      };

      executor.execute(task);
      executor.shutdown();
   }


   private void processActivationAccountResult(final AuthenticationRequestResponse accountActivationResponse)
   {
      if (accountActivationResponse.networkStatus == NetworkRequestStatus.OK)
      {
         if (accountActivationResponse.authenticationResult == AuthenticationStatus.Success)
         {
            final String[] message = {"Your Feedbactory account has been activated.",
                                      "You are signed in and can begin submitting",
                                      "ratings for browsed items."};
            showAcknowledgeMessageDialog(MessageType.Information, message);
         }
         else if (accountActivationResponse.authenticationResult == AuthenticationStatus.FailedAuthentication)
            showAcknowledgeMessageDialog(MessageType.Error, new String[] {"Incorrect email or Feedbactory account activation code."});
         else if (accountActivationResponse.authenticationResult == AuthenticationStatus.FailedTooManyAttempts)
            showFailedAuthenticationTooManyAttemptsMessageDialog();
         else
            throw new AssertionError("Unhandled account activation authentication result: " + accountActivationResponse.authenticationResult);
      }
   }


   private void handleSendPasswordResetEmail(final String email)
   {
      notifyAccountUIStatusUpdatedEDT(AccountUIStatus.Busy, false);

      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final Runnable task = new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               handleSendPasswordResetEmailStatus(email, userAccountManager.sendResetPasswordEmail(email));
            }
            finally
            {
               notifyAccountUIStatusUpdated(AccountUIStatus.Idle, false);
            }
         }
      };

      executor.execute(task);
      executor.shutdown();
   }


   private void handleSendPasswordResetEmailStatus(final String email, final ProcessedRequestBasicResponse sendPasswordResetEmailResponse)
   {
      if ((sendPasswordResetEmailResponse.requestStatus == NetworkRequestStatus.OK) && (sendPasswordResetEmailResponse.data == BasicOperationStatus.OK))
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               showTimedInformationDialogEDT(MessageType.Information, new String[] {"Email sent."}, 1000L);

               showResetAccountPasswordPanel(email);
            }
         });
      }
      else
         processDefaultFailedBasicRequestResponse(sendPasswordResetEmailResponse);
   }


   private void handleResetPassword(final String email, final String passwordResetCode, final byte[] newPasswordHash)
   {
      notifyAccountUIStatusUpdatedEDT(AccountUIStatus.Busy, true);

      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final Runnable task = new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               processResetPasswordResult(userAccountManager.resetPassword(email, passwordResetCode, newPasswordHash));
            }
            finally
            {
               notifyAccountUIStatusUpdated(AccountUIStatus.Idle, true);
            }
         }
      };

      executor.execute(task);
      executor.shutdown();
   }


   private void processResetPasswordResult(final AuthenticationRequestResponse resetPasswordResponse)
   {
      if (resetPasswordResponse.networkStatus == NetworkRequestStatus.OK)
      {
         if (resetPasswordResponse.authenticationResult == AuthenticationStatus.Success)
         {
            // Unlike other sign ins, maybe it's best to leave the user at their account form to emphasise that resetting their password has signed them in.
            showTimedInformationDialog(new String[] {"Password changed."});
         }
         else if (resetPasswordResponse.authenticationResult == AuthenticationStatus.FailedAuthentication)
            showAcknowledgeMessageDialog(MessageType.Error, new String[] {"Incorrect email or password reset code."});
         else if (resetPasswordResponse.authenticationResult == AuthenticationStatus.FailedTooManyAttempts)
            showFailedAuthenticationTooManyAttemptsMessageDialog();
         else
            throw new AssertionError("Unhandled account password reset authentication result: " + resetPasswordResponse.authenticationResult);
      }
   }


   private void handleSignIn(final String email, final byte[] passwordHash, final boolean persistSession)
   {
      notifyAccountUIStatusUpdatedEDT(AccountUIStatus.Busy, true);

      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final Runnable task = new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               processSignInResult(userAccountManager.signIn(email, passwordHash, persistSession));
            }
            finally
            {
               notifyAccountUIStatusUpdated(AccountUIStatus.Idle, true);
            }
         }
      };

      executor.execute(task);
      executor.shutdown();
   }


   private void processSignInResult(final AuthenticationRequestResponse signInResponse)
   {
      if (signInResponse.networkStatus == NetworkRequestStatus.OK)
      {
         if (signInResponse.authenticationResult == AuthenticationStatus.Success)
         {
            SwingUtilities.invokeLater(new Runnable()
            {
               @Override
               final public void run()
               {
                  showTimedInformationDialogEDT(MessageType.Information, new String[] {"Signed in."}, 1000L);
               }
            });
         }
         else if (signInResponse.authenticationResult == AuthenticationStatus.FailedAuthentication)
            showAcknowledgeMessageDialog(MessageType.Error, new String[] {"Incorrect email or password."});
         else if (signInResponse.authenticationResult == AuthenticationStatus.FailedTooManyAttempts)
            showFailedAuthenticationTooManyAttemptsMessageDialog();
         else
            throw new AssertionError("Unhandled account sign in authentication result: " + signInResponse.authenticationResult);
      }
   }


   private void handleSignInToPersistentSession()
   {
      notifyAccountUIStatusUpdatedEDT(AccountUIStatus.Busy, true);

      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final Runnable task = new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               processSignInToPersistentSessionResult(userAccountManager.signInToPersistentSession());
            }
            finally
            {
               notifyAccountUIStatusUpdated(AccountUIStatus.Idle, true);
            }
         }
      };

      executor.execute(task);
      executor.shutdown();
   }


   private void processSignInToPersistentSessionResult(final ProcessedRequestBasicResponse persistentSessionSignInResponse)
   {
      if ((persistentSessionSignInResponse.requestStatus == NetworkRequestStatus.OK) && (persistentSessionSignInResponse.data == BasicOperationStatus.OK))
         showTimedInformationDialog(new String[] {"Signed in."});

      // Else do nothing; the session has expired or is otherwise invalid and the session manager will quietly drop it.
   }


   private void handleUpdateEmail(final String newEmail)
   {
      notifyAccountUIStatusUpdatedEDT(AccountUIStatus.Busy, false);

      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final Runnable task = new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               processDefaultFailedBasicRequestResponse(userAccountManager.updateEmail(newEmail));
            }
            finally
            {
               notifyAccountUIStatusUpdated(AccountUIStatus.Idle, false);
            }
         }
      };

      executor.execute(task);
      executor.shutdown();
   }


   private void handleUpdatePasswordHash(final byte[] existingPasswordHash, final byte[] newPasswordHash)
   {
      notifyAccountUIStatusUpdatedEDT(AccountUIStatus.Busy, false);

      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final Runnable task = new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               processUpdatedPasswordHashResult(userAccountManager.updatePasswordHash(existingPasswordHash, newPasswordHash));
            }
            finally
            {
               notifyAccountUIStatusUpdated(AccountUIStatus.Idle, false);
            }
         }
      };

      executor.execute(task);
      executor.shutdown();
   }


   private void processUpdatedPasswordHashResult(final AuthenticationRequestResponse updatePasswordHashResponse)
   {
      if (updatePasswordHashResponse.networkStatus == NetworkRequestStatus.OK)
      {
         if (updatePasswordHashResponse.authenticationResult == AuthenticationStatus.Success)
            showTimedInformationDialog(new String[] {"Password changed."});
         else
            showAcknowledgeMessageDialog(MessageType.Error, new String[] {"Incorrect password."});
      }
   }


   private void handleResendChangeOfEmailConfirmationCode()
   {
      notifyAccountUIStatusUpdatedEDT(AccountUIStatus.Busy, false);

      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final Runnable task = new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               processResendChangeOfEmailConfirmationCodeResult(userAccountManager.resendChangeOfEmailConfirmationCode());
            }
            finally
            {
               notifyAccountUIStatusUpdated(AccountUIStatus.Idle, false);
            }
         }
      };

      executor.execute(task);
      executor.shutdown();
   }


   private void processResendChangeOfEmailConfirmationCodeResult(final ProcessedRequestBasicResponse resendConfirmationCodeResponse)
   {
      if ((resendConfirmationCodeResponse.requestStatus == NetworkRequestStatus.OK) && (resendConfirmationCodeResponse.data == BasicOperationStatus.OK))
         showAcknowledgeMessageDialog(MessageType.Information, new String[] {"A change of email confirmation code has been sent to you."});
      else
         processDefaultFailedBasicRequestResponse(resendConfirmationCodeResponse);
   }


   private void handleConfirmChangeOfEmail(final String changeOfEmailConfirmationCode, final byte[] passwordHash, final byte[] newEmailPasswordHash)
   {
      notifyAccountUIStatusUpdatedEDT(AccountUIStatus.Busy, false);

      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final Runnable task = new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               processConfirmChangeOfEmailResult(userAccountManager.confirmChangeOfEmail(changeOfEmailConfirmationCode, passwordHash, newEmailPasswordHash));
            }
            finally
            {
               notifyAccountUIStatusUpdated(AccountUIStatus.Idle, false);
            }
         }
      };

      executor.execute(task);
      executor.shutdown();
   }


   private void processConfirmChangeOfEmailResult(final AuthenticationRequestResponse confirmChangeOfEmailResponse)
   {
      if (confirmChangeOfEmailResponse.networkStatus == NetworkRequestStatus.OK)
      {
         if (confirmChangeOfEmailResponse.authenticationResult == AuthenticationStatus.Success)
            showTimedInformationDialog(new String[] {"Email updated."});
         else
            showAcknowledgeMessageDialog(MessageType.Error, new String[] {"Incorrect password or email confirmation code."});
      }
   }


   private void handleUpdateSendEmailAlerts(final boolean sendEmailAlerts)
   {
      notifyAccountUIStatusUpdatedEDT(AccountUIStatus.Busy, false);

      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final Runnable task = new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               handleUpdateSendEmailAlertsStatus(userAccountManager.updateSendEmailAlerts(sendEmailAlerts));
            }
            finally
            {
               notifyAccountUIStatusUpdated(AccountUIStatus.Idle, false);
            }
         }
      };

      executor.execute(task);
      executor.shutdown();
   }


   private void handleUpdateSendEmailAlertsStatus(final ProcessedRequestBasicResponse sendPasswordResetEmailResponse)
   {
      if ((sendPasswordResetEmailResponse.requestStatus != NetworkRequestStatus.OK) || (sendPasswordResetEmailResponse.data == BasicOperationStatus.OK))
         processDefaultFailedBasicRequestResponse(sendPasswordResetEmailResponse);
   }


   private void handleSignOut()
   {
      notifyAccountUIStatusUpdatedEDT(AccountUIStatus.Busy, true);

      final ExecutorService executor = Executors.newSingleThreadExecutor();

      final Runnable task = new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               processSignOutResult(userAccountManager.signOut());
            }
            finally
            {
               notifyAccountUIStatusUpdated(AccountUIStatus.Idle, true);
            }
         }
      };

      executor.execute(task);
      executor.shutdown();
   }


   private void processSignOutResult(final ProcessedRequestBasicResponse signOutResponse)
   {
      if ((signOutResponse.requestStatus == NetworkRequestStatus.OK) && (signOutResponse.data == BasicOperationStatus.OK))
         showTimedInformationDialog(new String[] {"Signed out."});
      else
         processDefaultFailedBasicRequestResponse(signOutResponse);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleShutdown()
   {
      /* If the user has a persistent session at the time of shutdown, it may have expired by the time they next start up the app.
       * Restoring their session isn't possible but at least pre-filling in their email ready for signing in is a nice starting point.
       */
      if (userAccountManager.hasPersistentSession())
         savePersistentSessionEmail();
      else
         clearPersistentSessionEmail();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final void showSignUpPanel()
   {
      showAccountComponent(new AccountSignUpPanel(this).getDelegate(), false);
   }


   final void showAccountActivationPanel(final String emailAddress)
   {
      showAccountComponent(new AccountActivationPanel(this, emailAddress).getDelegate(), false);
   }


   final void showSendPasswordResetEmailPanel(final String emailAddress)
   {
      showAccountComponent(new SendAccountPasswordResetEmailPanel(this, emailAddress).getDelegate(), true);
   }


   final void showResetAccountPasswordPanel(final String emailAddress)
   {
      showAccountComponent(new ResetAccountPasswordPanel(this, emailAddress).getDelegate(), false);
   }


   final void showMessageDialog(final MessageDialog messageDialog, final PresetOptionSelection defaultAction, final boolean temporaryComponent)
   {
      feedbactoryPad.showMessageDialog(messageDialog, defaultAction, temporaryComponent);
   }


   final void showTimedMessageDialog(final MessageDialog.Builder messageDialogBuilder, final boolean actionOnPadHidden, final int defaultActionIndex,
                                     final long displayDurationMilliseconds)
   {
      feedbactoryPad.showTimedMessageDialog(messageDialogBuilder, actionOnPadHidden, defaultActionIndex, displayDurationMilliseconds);
   }


   final void showFormSubcomponent(final JComponent component, final boolean temporaryComponent)
   {
      showAccountSubcomponent(component, temporaryComponent);
   }


   final void dismissActiveAccountSubcomponent()
   {
      dismissActiveSubcomponent();
   }


   final void dismissActiveAccountComponent()
   {
      dismissActiveComponents();
   }


   final void openURL(final String url)
   {
      handleOpenURL(url);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void signUp(final String email, final Gender gender, final long dateOfBirth, final boolean sendEmailAlerts)
   {
      handleSignUp(email, gender, dateOfBirth, sendEmailAlerts);
   }


   final void resendActivationCode(final String email)
   {
      handleResendActivationCode(email);
   }


   final void activateAccount(final String email, final String activationCode, final byte[] initialPasswordHash)
   {
      handleActivateAccount(email, activationCode, initialPasswordHash);
   }


   final void sendPasswordResetEmail(final String email)
   {
      handleSendPasswordResetEmail(email);
   }


   final void resetPassword(final String email, final String passwordResetCode, final byte[] newPasswordHash)
   {
      handleResetPassword(email, passwordResetCode, newPasswordHash);
   }


   final void signIn(final String email, final byte[] passwordHash, final boolean persistSession)
   {
      handleSignIn(email, passwordHash, persistSession);
   }


   final void updateEmail(final String newEmail)
   {
      handleUpdateEmail(newEmail);
   }


   final void updatePasswordHash(final byte[] existingPasswordHash, final byte[] newPasswordHash)
   {
      handleUpdatePasswordHash(existingPasswordHash, newPasswordHash);
   }


   final void resendChangeOfEmailConfirmationCode()
   {
      handleResendChangeOfEmailConfirmationCode();
   }


   final void confirmChangeOfEmail(final String changeOfEmailConfirmationCode, final byte[] passwordHash, final byte[] newEmailPasswordHash)
   {
      handleConfirmChangeOfEmail(changeOfEmailConfirmationCode, passwordHash, newEmailPasswordHash);
   }


   final void updateSendEmailAlerts(final boolean sendEmailAlerts)
   {
      handleUpdateSendEmailAlerts(sendEmailAlerts);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void addUserAccountActivityListener(final AccountUIStatusListener activityListener)
   {
      userAccountUIStatusListeners.add(activityListener);
   }


   final public void removeUserAccountActivityListener(final AccountUIStatusListener activityListener)
   {
      userAccountUIStatusListeners.remove(activityListener);
   }


   final public void showRootAccountComponent()
   {
      handleShowRootComponent();
   }


   final public void requestDismissActiveComponents()
   {
      handleRequestDismissActiveComponents();
   }


   final public void signInToPersistentSession()
   {
      handleSignInToPersistentSession();
   }


   final public void signOut()
   {
      handleSignOut();
   }


   final public void shutdown()
   {
      handleShutdown();
   }
}