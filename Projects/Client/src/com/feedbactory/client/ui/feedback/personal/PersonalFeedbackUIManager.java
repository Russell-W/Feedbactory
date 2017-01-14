/* Memos:
 * - Photography feedback is hardwired in as a menu item for the purposes of demonstration.
 *   Later this should be amended to allow users to pin & unpin arbitrary menu items for different feedback types, essentially
 *   for different PersonalFeedbackCriteriaType's.
 *   This information should be saved to preferences, hence the scheme of using strings for the menu identifiers; there'd be no use in
 *   using ActionListeners or other runtime-specific objects.
 *
 */

package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.client.core.ClientUtilities;
import com.feedbactory.client.core.feedback.personal.ManagedPersonalFeedbackPerson;
import com.feedbactory.client.core.feedback.personal.PersonalFeedbackAddSubmissionResult;
import com.feedbactory.client.core.feedback.personal.PersonalFeedbackBasicSummaryResult;
import com.feedbactory.client.core.feedback.personal.PersonalFeedbackDetailedSummaryResult;
import com.feedbactory.client.core.feedback.personal.PersonalFeedbackFeaturedItemsSampleResult;
import com.feedbactory.client.core.feedback.personal.PersonalFeedbackGetSubmissionResult;
import com.feedbactory.client.core.feedback.personal.PersonalFeedbackManager;
import com.feedbactory.client.core.feedback.personal.PersonalFeedbackPersonChangeListener;
import com.feedbactory.client.core.network.DataAvailabilityStatus;
import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.client.core.useraccount.AccountEventAdapter;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.client.core.useraccount.FeedbactoryUserAccount;
import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.feedback.FeedbackWebsiteURL;
import com.feedbactory.client.ui.browser.feedback.personal.PersonalFeedbackBrowsedPageResult;
import com.feedbactory.client.ui.browser.feedback.personal.PersonalFeedbackBrowserEventManager;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.MessageDialog.MessageType;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionConfiguration;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionSelection;
import com.feedbactory.client.ui.feedback.FeedbackCategoryDataFormatter;
import com.feedbactory.client.ui.feedback.FeedbackCategoryUIManager;
import com.feedbactory.client.ui.feedback.FeedbackMenuItem;
import com.feedbactory.client.ui.feedback.FeedbackPanel;
import com.feedbactory.client.ui.feedback.FeedbackPanelUIComponent;
import com.feedbactory.client.ui.feedback.FeedbackUIManager;
import com.feedbactory.client.ui.feedback.FeedbackUIManager.BrowserEventManagerClientView;
import com.feedbactory.client.ui.pad.FeedbactoryPadUIView;
import com.feedbactory.client.ui.settings.FlashingFeedbackAlertOption;
import com.feedbactory.client.ui.settings.SettingsUIManager;
import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.feedback.FeedbackSubmissionStatus;
import com.feedbactory.shared.feedback.personal.CriteriaFeedbackFeaturedItemsFilter;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackDetailedSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmission;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.network.BasicOperationStatus;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;


public class PersonalFeedbackUIManager implements FeedbackCategoryUIManager
{
   final private PersonalFeedbackManager personalFeedbackManager;

   final private FeedbackUIManager feedbackUIManager;
   final private SettingsUIManager settingsUIManager;
   final private FeedbactoryPadUIView feedbactoryPad;

   final private FeedbackCategoryDataFormatter stringFormatter = new PersonalFeedbackFormatter();

   final private PersonalFeedbackBrowserEventManager browserEventManager;

   private PersonalFeedbackRootPanel activeRootPanel;
   final private Map<PersonalFeedbackCriteriaType, PersonalFeedbackFeaturedItemsPanel> activeFeaturedItemsPanels = new EnumMap<PersonalFeedbackCriteriaType, PersonalFeedbackFeaturedItemsPanel>(PersonalFeedbackCriteriaType.class);

   private FeedbactoryUserAccount signedInUserAccount;

   final private ExecutorService requestTaskExecutor = Executors.newSingleThreadExecutor();

   /* UI callers may fire off a rapid chain of requests to fetch feedback, eg. as the user flicks through the people in a combo box.
    * In those instances it's very likely that the user isn't yet interested in the feedback for the people that are flicked past,
    * only those who are displayed last when the UI is at a resting position.
    * These maps of active requests are used to cancel successive queued tasks of the same request type as described above, as well
    * as abort the submission of requests where a request with the same key request parameter is already underway, eg. repeatedly requesting the
    * basic feedback summary for a specific user. In the first instance, later requests will always supersede earlier requests,
    * and the final request received will always be carried out, whether or not earlier tasks were successfully cancelled. Only
    * queued (not yet active) tasks will be successfully cancelled. The single threaded executor service ensures that tasks will always be
    * queued if there are active requests of any type.
    * In the case of requests having identical key parameters, a prior request will not be cancelled and the follow-up request(s) will
    * simply bail out without submitting to the executor service.
    * In practice the UI callers may enforce their own restrictions of successive requests, but this is a solid mechanism to have
    * at the 'last post' before requests are shunted off on separate task threads.
    */
   final private Map<RequestType, TaskRequest<PersonalFeedbackPerson>> activePersonRequests = new EnumMap<RequestType, TaskRequest<PersonalFeedbackPerson>>(RequestType.class);
   final private Map<RequestType, TaskRequest<CriteriaFeedbackFeaturedItemsFilter>> activeFeaturedItemRequests = new EnumMap<RequestType, TaskRequest<CriteriaFeedbackFeaturedItemsFilter>>(RequestType.class);

   private MessageDialog activeWebsiteNotEnabledMessageDialog;


   public PersonalFeedbackUIManager(final PersonalFeedbackManager personalFeedbackManager,
                                    final AccountSessionManager userAccountManager,
                                    final FeedbackUIManager feedbackUIManager,
                                    final BrowserEventManagerClientView browserEventManagerClientView,
                                    final SettingsUIManager settingsUIManager,
                                    final FeedbactoryPadUIView feedbactoryPad)
   {
      this.personalFeedbackManager = personalFeedbackManager;
      this.feedbackUIManager = feedbackUIManager;
      this.settingsUIManager = settingsUIManager;
      this.feedbactoryPad = feedbactoryPad;

      browserEventManager = new PersonalFeedbackBrowserEventManager(this, browserEventManagerClientView);

      initialise(userAccountManager);
   }


   private void initialise(final AccountSessionManager userAccountManager)
   {
      initialiseActiveFeaturedItemsPanels();
      initialisePersonChangeListener();
      initialiseAccountEventListener(userAccountManager);
   }


   private void initialiseActiveFeaturedItemsPanels()
   {
      for (final PersonalFeedbackCriteriaType criteriaType : PersonalFeedbackCriteriaType.values())
         activeFeaturedItemsPanels.put(criteriaType, null);
   }


   private void initialisePersonChangeListener()
   {
      personalFeedbackManager.addPersonalFeedbackPersonChangeListener(new PersonalFeedbackPersonChangeListener()
      {
         @Override
         final public void personProfileDetailsChanged(final PersonalFeedbackPersonProfile personProfile)
         {
            handlePersonProfileChanged(personProfile);
         }


         @Override
         final public void basicFeedbackSummaryAvailabilityStatusChanged(final PersonalFeedbackPerson person,
                                                                         final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus)
         {
            handleBasicFeedbackSummaryAvailabilityStatusChanged(person, basicFeedbackSummaryAvailabilityStatus);
         }


         @Override
         final public void basicFeedbackSummaryChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus,
                                                       final PersonalFeedbackBasicSummary basicSummary)
         {
            handleBasicFeedbackSummaryChanged(person, basicFeedbackSummaryAvailabilityStatus, basicSummary);
         }


         @Override
         final public void detailedFeedbackSummaryAvailabilityStatusChanged(final PersonalFeedbackPerson person,
                                                                            final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus)
         {
            handleDetailedFeedbackSummaryAvailabilityStatusChanged(person, detailedFeedbackSummaryAvailabilityStatus);
         }


         @Override
         final public void detailedFeedbackSummaryChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus,
                                                          final PersonalFeedbackDetailedSummary detailedFeedbackSummary)
         {
            handleDetailedFeedbackSummaryChanged(person, detailedFeedbackSummaryAvailabilityStatus, detailedFeedbackSummary);
         }


         @Override
         final public void feedbackSubmissionAvailabilityStatusChanged(final PersonalFeedbackPerson person,
                                                                       final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus)
         {
            handleFeedbackSubmissionAvailabilityStatusChanged(person, feedbackSubmissionAvailabilityStatus);
         }


         @Override
         final public void feedbackSubmissionChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus,
                                                     final PersonalFeedbackSubmission personalFeedbackSubmission)
         {
            handleFeedbackSubmissionChanged(person, feedbackSubmissionAvailabilityStatus, personalFeedbackSubmission);
         }
      });
   }


   private void initialiseAccountEventListener(final AccountSessionManager userAccountManager)
   {
      userAccountManager.addUserAccountEventUIListener(new AccountEventAdapter()
      {
         @Override
         final public void signedInToUserAccount(final FeedbactoryUserAccount userAccount)
         {
            handleSignedInUserAccountUpdated(userAccount);
         }


         @Override
         final public void userAccountDetailsUpdated(final FeedbactoryUserAccount userAccount)
         {
            handleSignedInUserAccountUpdated(userAccount);
         }


         @Override
         final public void signedOutOfUserAccount(final FeedbactoryUserAccount userAccount)
         {
            handleSignedInUserAccountUpdated(null);
         }
      });
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static private enum RequestType
   {
      GetBasicFeedbackSummary,
      GetDetailedFeedbackSummary,
      AddFeedbackSubmission,
      GetFeedbackSubmission,
      RemoveFeedbackSubmission,
      GetHotFeedbackItemsSample,
      GetNewFeedbackItemsSample;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class TaskRequest<E> extends FutureTask<Void>
   {
      final private RequestType requestType;
      final private E taskArgument;


      private TaskRequest(final RequestType requestType, final E taskArgument, final Runnable taskRunnable)
      {
         super(taskRunnable, null);

         this.requestType = requestType;
         this.taskArgument = taskArgument;
      }


      @Override
      final public void run()
      {
         Thread.currentThread().setName("PersonalFeedbackUIManager." + requestType + "Task");

         /* Runnables wrapped in a Future submitted to an Executor will by default capture any exceptions for later processing if/when one of the
          * Future's get() methods is called. Since this class is not making use of get(), the exception occurrence must be intercepted when
          * FutureTask's setException() method is invoked by super.run() - see below.
          */
         super.run();
      }


      @Override
      final protected void setException(Throwable throwable)
      {
         super.setException(throwable);

         final String errorMessage = MessageFormat.format("Error while making personal feedback request {0} for item: {1}.", new Object[] {requestType, taskArgument.toString()});
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, errorMessage, throwable);

         feedbackUIManager.reportUncaughtException(getClass(), errorMessage, Thread.currentThread(), throwable);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void handlePersonProfileChanged(final PersonalFeedbackPersonProfile personProfile)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            handlePersonProfileChangedEDT(personProfile);
         }
      });
   }


   private void handlePersonProfileChangedEDT(final PersonalFeedbackPersonProfile personProfile)
   {
      if (activeRootPanel != null)
         activeRootPanel.personProfileDetailsUpdated(personProfile);

      final PersonalFeedbackFeaturedItemsPanel featuredItemsPanel = activeFeaturedItemsPanels.get(personProfile.person.getCriteriaType());
      if (featuredItemsPanel != null)
         featuredItemsPanel.reportItemProfileDetailsUpdated(personProfile);

      feedbackUIManager.reportItemProfileUpdated(personProfile);
   }


   private void handleBasicFeedbackSummaryAvailabilityStatusChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            handleBasicFeedbackSummaryAvailabilityStatusChangedEDT(person, basicFeedbackSummaryAvailabilityStatus);
         }
      });
   }


   private void handleBasicFeedbackSummaryAvailabilityStatusChangedEDT(final PersonalFeedbackPerson person,
                                                                       final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus)
   {
      if (activeRootPanel != null)
         activeRootPanel.basicFeedbackSummaryAvailabilityStatusUpdated(person, basicFeedbackSummaryAvailabilityStatus);
   }


   private void handleBasicFeedbackSummaryChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus,
                                                  final PersonalFeedbackBasicSummary basicSummary)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            handleBasicFeedbackSummaryChangedEDT(person, basicFeedbackSummaryAvailabilityStatus, basicSummary);
         }
      });
   }


   private void handleBasicFeedbackSummaryChangedEDT(final PersonalFeedbackPerson person, final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus,
                                                     final PersonalFeedbackBasicSummary basicSummary)
   {
      if (activeRootPanel != null)
         activeRootPanel.basicFeedbackSummaryUpdated(person, basicFeedbackSummaryAvailabilityStatus, basicSummary);

      if (basicFeedbackSummaryAvailabilityStatus == DataAvailabilityStatus.Available)
      {
         final PersonalFeedbackFeaturedItemsPanel featuredItemsPanel = activeFeaturedItemsPanels.get(person.getCriteriaType());
         if (featuredItemsPanel != null)
            featuredItemsPanel.reportItemBasicFeedbackSummaryUpdated(person, basicSummary);
      }

      feedbackUIManager.reportItemFeedbackSummaryUpdated(person, basicSummary);
   }


   private void handleDetailedFeedbackSummaryAvailabilityStatusChanged(final PersonalFeedbackPerson person,
                                                                       final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            handleDetailedFeedbackSummaryAvailabilityStatusChangedEDT(person, detailedFeedbackSummaryAvailabilityStatus);
         }
      });
   }


   private void handleDetailedFeedbackSummaryAvailabilityStatusChangedEDT(final PersonalFeedbackPerson person,
                                                                          final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus)
   {
      if (activeRootPanel != null)
         activeRootPanel.detailedFeedbackSummaryAvailabilityStatusUpdated(person, detailedFeedbackSummaryAvailabilityStatus);
   }


   private void handleDetailedFeedbackSummaryChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus,
                                                     final PersonalFeedbackDetailedSummary detailedSummary)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            handleDetailedFeedbackSummaryChangedEDT(person, detailedFeedbackSummaryAvailabilityStatus, detailedSummary);
         }
      });
   }


   private void handleDetailedFeedbackSummaryChangedEDT(final PersonalFeedbackPerson person, final DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus,
                                                        final PersonalFeedbackDetailedSummary detailedSummary)
   {
      if (activeRootPanel != null)
         activeRootPanel.detailedFeedbackSummaryUpdated(person, detailedFeedbackSummaryAvailabilityStatus, detailedSummary);
   }


   private void handleFeedbackSubmissionAvailabilityStatusChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            handleFeedbackSubmissionAvailabilityStatusChangedEDT(person, feedbackSubmissionAvailabilityStatus);
         }
      });
   }


   private void handleFeedbackSubmissionAvailabilityStatusChangedEDT(final PersonalFeedbackPerson person, final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus)
   {
      if (activeRootPanel != null)
         activeRootPanel.feedbackSubmissionAvailabilityStatusUpdated(person, feedbackSubmissionAvailabilityStatus);
   }


   private void handleFeedbackSubmissionChanged(final PersonalFeedbackPerson person, final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus,
                                                final PersonalFeedbackSubmission personalFeedbackSubmission)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            handleFeedbackSubmissionChangedEDT(person, feedbackSubmissionAvailabilityStatus, personalFeedbackSubmission);
         }
      });
   }


   private void handleFeedbackSubmissionChangedEDT(final PersonalFeedbackPerson person, final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus,
                                                   final PersonalFeedbackSubmission personalFeedbackSubmission)
   {
      if (activeRootPanel != null)
         activeRootPanel.feedbackSubmissionUpdated(person, feedbackSubmissionAvailabilityStatus, personalFeedbackSubmission);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSignedInUserAccountUpdated(final FeedbactoryUserAccount signedInUserAccount)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            PersonalFeedbackUIManager.this.signedInUserAccount = signedInUserAccount;
         }
      });
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private <E> boolean scheduleRequest(final Map<RequestType, TaskRequest<E>> activeRequestMap, final RequestType requestType, final E taskArgument,
                                       final Runnable taskRunnable)
   {
      final TaskRequest<E> activeRequest = activeRequestMap.get(requestType);
      if (activeRequest != null)
      {
         // Bail out if a request with the same argument is already in progress.
         if (activeRequest.taskArgument.equals(taskArgument))
            return false;
         else
         {
            // Try to prevent a backlog of quickfire requests by removing any queued tasks.
            activeRequest.cancel(false);
         }
      }

      final TaskRequest<E> newRequest = new TaskRequest<E>(requestType, taskArgument, taskRunnable);
      activeRequestMap.put(requestType, newRequest);

      requestTaskExecutor.execute(newRequest);

      return true;
   }


   private void clearRequest(final Map<RequestType, ? extends TaskRequest<?>> activeRequestMap, final RequestType requestType)
   {
      activeRequestMap.remove(requestType);
   }


   private void showWebsiteNotAvailableDialog(final PersonalFeedbackPerson person)
   {
      if (activeWebsiteNotEnabledMessageDialog == null)
      {
         final String[] message = new String[] {person.getWebsite().getName() + " feedback is",
                                                "temporarily unavailable.",
                                                "Please try again later."};

         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Warning, message, PresetOptionConfiguration.OK);
         builder.setBorderTitle("Service Unavailable");

         activeWebsiteNotEnabledMessageDialog = new MessageDialog(builder);
         activeWebsiteNotEnabledMessageDialog.addActionListener(new MessageDialog.ActionListener()
         {
            @Override
            final public void actionPerformed(final MessageDialog messageDialog, final PresetOptionSelection optionSelection, final int optionSelectionIndex)
            {
               handleActiveWebsiteNotEnabledMessageDialogActionPerformed();
            }
         });

         feedbactoryPad.showMessageDialog(activeWebsiteNotEnabledMessageDialog, PresetOptionSelection.OK, true);
      }
   }


   private void handleActiveWebsiteNotEnabledMessageDialogActionPerformed()
   {
      activeWebsiteNotEnabledMessageDialog = null;
   }


   private void handleRequestGetPersonBasicFeedbackSummary(final PersonalFeedbackPerson person)
   {
      assert SwingUtilities.isEventDispatchThread();

      final Runnable taskRunnable = new Runnable()
      {
         @Override
         final public void run()
         {
            final ManagedPersonalFeedbackPerson managedPerson = personalFeedbackManager.getManagedPersonCopy(person);
            final boolean hadNoFeedbackBeforeRequest = (managedPerson.getBasicFeedbackSummary().numberOfRatings == 0);

            PersonalFeedbackBasicSummaryResult requestResult = null;

            try
            {
               requestResult = personalFeedbackManager.getPersonBasicFeedbackSummary(person);
            }
            finally
            {
               postProcessGetPersonBasicFeedbackSummary(person, hadNoFeedbackBeforeRequest, requestResult);
            }
         }
      };

      scheduleRequest(activePersonRequests, RequestType.GetBasicFeedbackSummary, person, taskRunnable);
   }


   private void postProcessGetPersonBasicFeedbackSummary(final PersonalFeedbackPerson person, final boolean hadNoFeedbackBeforeRequest,
                                                         final PersonalFeedbackBasicSummaryResult requestResult)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            postProcessGetPersonBasicFeedbackSummaryEDT(person, hadNoFeedbackBeforeRequest, requestResult);
         }
      });
   }


   private void postProcessGetPersonBasicFeedbackSummaryEDT(final PersonalFeedbackPerson person, final boolean hadNoFeedbackBeforeRequest,
                                                            final PersonalFeedbackBasicSummaryResult requestResult)
   {
      clearRequest(activePersonRequests, RequestType.GetBasicFeedbackSummary);

      if ((requestResult != null) && (requestResult.requestStatus == NetworkRequestStatus.OK))
      {
         if (requestResult.operationStatus == BasicOperationStatus.OK)
         {
            if ((settingsUIManager.isFlashingFeedbackAlertEnabled() == FlashingFeedbackAlertOption.ShowForItemsHavingFeedback) &&
                hadNoFeedbackBeforeRequest && (requestResult.basicSummary.numberOfRatings > 0))
               feedbackUIManager.showFeedbackAlert();
         }
         else
            showWebsiteNotAvailableDialog(person);
      }
   }


   private void handleRequestGetPersonDetailedFeedbackSummary(final PersonalFeedbackPerson person)
   {
      assert SwingUtilities.isEventDispatchThread();

      final Runnable taskRunnable = new Runnable()
      {
         @Override
         final public void run()
         {
            PersonalFeedbackDetailedSummaryResult requestResult = null;

            try
            {
               requestResult = personalFeedbackManager.getPersonDetailedFeedbackSummary(person);
            }
            finally
            {
               postProcessGetPersonDetailedFeedbackSummary(person, requestResult);
            }
         }
      };

      scheduleRequest(activePersonRequests, RequestType.GetDetailedFeedbackSummary, person, taskRunnable);
   }


   private void postProcessGetPersonDetailedFeedbackSummary(final PersonalFeedbackPerson person, final PersonalFeedbackDetailedSummaryResult requestResult)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            postProcessGetPersonDetailedFeedbackSummaryEDT(person, requestResult);
         }
      });
   }


   private void postProcessGetPersonDetailedFeedbackSummaryEDT(final PersonalFeedbackPerson person, final PersonalFeedbackDetailedSummaryResult requestResult)
   {
      clearRequest(activePersonRequests, RequestType.GetDetailedFeedbackSummary);

      if ((requestResult != null) && (requestResult.requestStatus == NetworkRequestStatus.OK) && (requestResult.operationStatus == BasicOperationStatus.Failed))
         showWebsiteNotAvailableDialog(person);
   }


   private void handleRequestGetPersonFeedbackSubmission(final PersonalFeedbackPerson person)
   {
      assert SwingUtilities.isEventDispatchThread();

      final FeedbactoryUserAccount requestingUserAccount = signedInUserAccount;

      final Runnable taskRunnable = new Runnable()
      {
         @Override
         final public void run()
         {
            PersonalFeedbackGetSubmissionResult requestResult = null;

            try
            {
               requestResult = personalFeedbackManager.getPersonFeedbackSubmission(person, requestingUserAccount);
            }
            finally
            {
               postProcessGetPersonFeedbackSubmission(person, requestResult);
            }
         }
      };

      scheduleRequest(activePersonRequests, RequestType.GetFeedbackSubmission, person, taskRunnable);
   }


   private void postProcessGetPersonFeedbackSubmission(final PersonalFeedbackPerson person, final PersonalFeedbackGetSubmissionResult requestResult)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            postProcessGetPersonFeedbackSubmissionEDT(person, requestResult);
         }
      });
   }


   private void postProcessGetPersonFeedbackSubmissionEDT(final PersonalFeedbackPerson person, final PersonalFeedbackGetSubmissionResult requestResult)
   {
      clearRequest(activePersonRequests, RequestType.GetFeedbackSubmission);

      /* It's possible that this request is sent at the same time that a signed in user has requested to sign out of their account.
       * There are two possibilities:
       *
       * 1) This request gets in first, in which case it will eventually grab the session manager's lock, forcing the sign out request to
       *    wait until it's finished. Even so there's still a small (impossible?) chance that after this request has released the user account
       *    session lock and before it submits this post-processing task to Swing, the account sign out request is completely processed.
       *    If that were to happen, the account manager would fire off its signed out notifications and the PersonalFeedbackManager would
       *    blank any attached feedback submission for the person and note the change in user account. The PersonalFeedbackManager has its own
       *    checks for bailing out on setting attached feedback after a user account has changed - see its handleGetPersonFeedbackSubmission() method.
       *    In the event that it does bail out, this post processing task is executed and will push a request to update the UI (which may not still be
       *    displaying this person). Regardless, the checking in PersonalFeedbackManager ensures that whatever PersonalFeedbackPersonUIView is
       *    eventually passed back to the UI will be consistent with the current user account.
       *
       * 2) The sign out task gets in first, in which case it will eventually grab the session manager's lock, forcing this request to
       *    wait until it's finished. When this request tries to resume and send a regular session request, the session manager will throw
       *    an IllegalAccountSessionRequestState since there will no longer be any user account session. This exception will be caught by
       *    the PersonalFeedbackManager which again bails out, but this post processing task will still be executed to ensure that the
       *    UI is updated to the non-busy state.
       */
      if ((requestResult != null) && (requestResult.requestStatus == NetworkRequestStatus.OK) && (requestResult.operationStatus == BasicOperationStatus.Failed))
         showWebsiteNotAvailableDialog(person);
   }


   private void handleRequestAddPersonFeedbackSubmission(final PersonalFeedbackPerson person, final PersonalFeedbackSubmission personalFeedbackSubmission)
   {
      assert SwingUtilities.isEventDispatchThread();

      final Runnable taskRunnable = new Runnable()
      {
         @Override
         final public void run()
         {
            PersonalFeedbackAddSubmissionResult requestResult = null;

            try
            {
               requestResult = personalFeedbackManager.addPersonFeedbackSubmission(person, personalFeedbackSubmission);
            }
            finally
            {
               postProcessAddPersonFeedbackSubmission(person, requestResult);
            }
         }
      };

      if (scheduleRequest(activePersonRequests, RequestType.AddFeedbackSubmission, person, taskRunnable))
      {
         // Locks the entire pad, no other operations (including signing out) are possible once this method is in motion.
         feedbactoryPad.setBusy(true);
      }
   }


   private void postProcessAddPersonFeedbackSubmission(final PersonalFeedbackPerson person,
                                                       final PersonalFeedbackAddSubmissionResult requestResult)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            postProcessAddPersonFeedbackSubmissionEDT(person, requestResult);
         }
      });
   }


   private void postProcessAddPersonFeedbackSubmissionEDT(final PersonalFeedbackPerson person,
                                                          final PersonalFeedbackAddSubmissionResult requestResult)
   {
      try
      {
         clearRequest(activePersonRequests, RequestType.AddFeedbackSubmission);

         // The requestResult may be null if there was a non-network exception.
         if ((requestResult != null) && (requestResult.requestStatus == NetworkRequestStatus.OK))
         {
            if (requestResult.feedbackSubmissionStatus == FeedbackSubmissionStatus.FailedWebsiteNotEnabled)
               showWebsiteNotAvailableDialog(person);
            else if (requestResult.feedbackSubmissionStatus == FeedbackSubmissionStatus.FailedTooManySubmissions)
               feedbactoryPad.showMessageDialog(getAddPersonFeedbackSubmissionFailedDialog(), PresetOptionSelection.OK, false);
         }
      }
      finally
      {
         feedbactoryPad.setBusy(false);
      }
   }


   private MessageDialog getAddPersonFeedbackSubmissionFailedDialog()
   {
      final ArrayList<String> messageBuilder = new ArrayList<String>();
      messageBuilder.add("Sorry, your feedback quota has been reached for now.");
      messageBuilder.add("");
      messageBuilder.add("This is a temporary limitation put in place during this early");
      messageBuilder.add("release period to ensure that the platform is refined and ready");
      messageBuilder.add("for a wider release in the very near future.");
      messageBuilder.add("");

      if (signedInUserAccount.sendEmailAlerts)
      {
         messageBuilder.add("Stay tuned - you will be notified soon via email when rating");
         messageBuilder.add("submission quotas have been increased.");
      }
      else
         messageBuilder.add("Stay tuned - check back with us soon for more announcements.");

      final String[] message = messageBuilder.toArray(new String[messageBuilder.size()]);
      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Information, message, PresetOptionConfiguration.OK);

      return new MessageDialog(builder);
   }


   private void handleRequestRemovePersonFeedbackSubmission(final PersonalFeedbackPerson person)
   {
      assert SwingUtilities.isEventDispatchThread();

      final Runnable taskRunnable = new Runnable()
      {
         @Override
         final public void run()
         {
            PersonalFeedbackBasicSummaryResult requestResult = null;

            try
            {
               requestResult = personalFeedbackManager.removePersonFeedbackSubmission(person);
            }
            finally
            {
               postProcessRemovePersonFeedbackSubmission(person, requestResult);
            }
         }
      };

      if (scheduleRequest(activePersonRequests, RequestType.RemoveFeedbackSubmission, person, taskRunnable))
      {
         // Locks the entire pad, no other operations (including signing out) are possible once this method is in motion.
         feedbactoryPad.setBusy(true);
      }
   }


   private void postProcessRemovePersonFeedbackSubmission(final PersonalFeedbackPerson person, final PersonalFeedbackBasicSummaryResult requestResult)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            postProcessRemovePersonFeedbackSubmissionEDT(person, requestResult);
         }
      });
   }


   private void postProcessRemovePersonFeedbackSubmissionEDT(final PersonalFeedbackPerson person, final PersonalFeedbackBasicSummaryResult requestResult)
   {
      try
      {
         clearRequest(activePersonRequests, RequestType.RemoveFeedbackSubmission);

         // The requestResult may be null if there was a non-network exception.
         if ((requestResult != null) && (requestResult.requestStatus == NetworkRequestStatus.OK) && (requestResult.operationStatus == BasicOperationStatus.Failed))
            showWebsiteNotAvailableDialog(person);
      }
      finally
      {
         feedbactoryPad.setBusy(false);
      }
   }


   private void handleRequestGetNewFeedbackItemsSample(final CriteriaFeedbackFeaturedItemsFilter newItemsFilter)
   {
      assert SwingUtilities.isEventDispatchThread();

      final Runnable taskRunnable = new Runnable()
      {
         @Override
         final public void run()
         {
            PersonalFeedbackFeaturedItemsSampleResult newFeedbackItemsSampleResult = null;

            try
            {
               newFeedbackItemsSampleResult = personalFeedbackManager.getNewFeedbackItemsSample(newItemsFilter);
            }
            finally
            {
               postProcessGetFeaturedFeedbackItemsSample(newItemsFilter, newFeedbackItemsSampleResult, true);
            }
         }
      };

      scheduleRequest(activeFeaturedItemRequests, RequestType.GetNewFeedbackItemsSample, newItemsFilter, taskRunnable);
   }


   private void handleRequestGetHotFeedbackItemsSample(final CriteriaFeedbackFeaturedItemsFilter hotItemsFilter)
   {
      assert SwingUtilities.isEventDispatchThread();

      final Runnable taskRunnable = new Runnable()
      {
         @Override
         final public void run()
         {
            PersonalFeedbackFeaturedItemsSampleResult newFeedbackItemsSampleResult = null;

            try
            {
               newFeedbackItemsSampleResult = personalFeedbackManager.getHotFeedbackItemsSample(hotItemsFilter);
            }
            finally
            {
               postProcessGetFeaturedFeedbackItemsSample(hotItemsFilter, newFeedbackItemsSampleResult, false);
            }
         }
      };

      scheduleRequest(activeFeaturedItemRequests, RequestType.GetHotFeedbackItemsSample, hotItemsFilter, taskRunnable);
   }


   private void postProcessGetFeaturedFeedbackItemsSample(final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter,
                                                          final PersonalFeedbackFeaturedItemsSampleResult featuredFeedbackItemsSampleResult,
                                                          final boolean isNewFeedbackItemsSample)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            postProcessGetFeaturedFeedbackItemsSampleEDT(featuredItemsFilter, featuredFeedbackItemsSampleResult, isNewFeedbackItemsSample);
         }
      });
   }


   private void postProcessGetFeaturedFeedbackItemsSampleEDT(final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter,
                                                             final PersonalFeedbackFeaturedItemsSampleResult featuredFeedbackItemsSampleResult,
                                                             final boolean isNewFeedbackItemsSample)
   {
      final PersonalFeedbackFeaturedItemsPanel targetPanel = activeFeaturedItemsPanels.get(featuredItemsFilter.criteriaType);

      PersonalFeedbackFeaturedItemsSampleResult resultToReturn = null;

      try
      {
         clearRequest(activeFeaturedItemRequests, isNewFeedbackItemsSample ? RequestType.GetNewFeedbackItemsSample : RequestType.GetHotFeedbackItemsSample);

         /* For this request, whether or not there was an error there needs to be a callback since the caller's UI will still be in the 'busy' state.
          * The busy state is usually handled in this class, but not for the featured feedback requests.
          * Note that featuredFeedbackItemsSampleResult may be null if there was a non-network exception; if so convert it to a friendlier value for the recipient.
          */
         if (featuredFeedbackItemsSampleResult != null)
            resultToReturn = featuredFeedbackItemsSampleResult;
         else
            resultToReturn = new PersonalFeedbackFeaturedItemsSampleResult(NetworkRequestStatus.FailedNetworkOther);
      }
      finally
      {
         if (isNewFeedbackItemsSample)
            targetPanel.reportNewItemsSampleLoaded(resultToReturn);
         else
            targetPanel.reportHotItemsSampleLoaded(resultToReturn);
      }
   }


   private void handleCloseFeaturedFeedbackItemsPanel(final PersonalFeedbackFeaturedItemsPanel featuredItemsPanel)
   {
      // Dismiss it but don't remove it from the collection, it should be reused.
      feedbactoryPad.dismissLockingComponent(featuredItemsPanel.getDelegate());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private List<FeedbackMenuItem> handleGetFeedbackMenuItems()
   {
      final FeedbackMenuItem viewPhotographyFeedbackMenuItem = new FeedbackMenuItem(FeedbackCategory.Personal, "Photography Feedback", Byte.toString(PersonalFeedbackCriteriaType.Photography.value));
      return Collections.singletonList(viewPhotographyFeedbackMenuItem);
   }


   private void handleInvokeMenuItem(final FeedbackMenuItem menuItem)
   {
      /* The menu item identifier could be expanded to a more complex format, eg. comma separated parameters.
       * For now it's a good idea to keep the menu item identifier as a simple parsable object such as a string as opposed to eg. an ActionListener,
       * since it's likely that these will eventually be saved to and restored from preferences.
       */
      final byte criteriaTypeByte = Byte.parseByte(menuItem.menuItemIdentifier);
      final PersonalFeedbackCriteriaType criteriaType = PersonalFeedbackCriteriaType.fromValue(criteriaTypeByte);
      PersonalFeedbackFeaturedItemsPanel featuredItemsPanel = activeFeaturedItemsPanels.get(criteriaType);

      if (featuredItemsPanel == null)
      {
         featuredItemsPanel = new PersonalFeedbackFeaturedItemsPanel(this, criteriaType, feedbactoryPad.getImageLoader());
         attachFeaturedItemsPanelCancelKeyBinding(featuredItemsPanel);
         activeFeaturedItemsPanels.put(criteriaType, featuredItemsPanel);
      }

      feedbactoryPad.showFormComponent(featuredItemsPanel.getDelegate(), false);

      // Fire off the request to fetch the featured items.
      featuredItemsPanel.checkFetchActiveFeaturedFeedbackItems();
   }


   private void attachFeaturedItemsPanelCancelKeyBinding(final PersonalFeedbackFeaturedItemsPanel featuredItemsPanel)
   {
      final InputMap inputMap = featuredItemsPanel.getDelegate().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      final ActionMap actionMap = featuredItemsPanel.getDelegate().getActionMap();

      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelPanel");

      actionMap.put("cancelPanel", new AbstractAction()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            closeFeaturedFeedbackItemsPanel(featuredItemsPanel);
         }
      });
   }


   // Non Swing-EDT.
   private PersonalFeedbackBrowsedPageResult handleReportBrowsedPage(final BrowserService browserService)
   {
      final PersonalFeedbackBrowsedPageResult browsedPageResult = browserEventManager.reportBrowsedPage(browserService);

      if (browsedPageResult.getFeedbackItemProfile() != null)
         reportBrowserActiveItemChanged(browsedPageResult.getFeedbackItemProfile());

      return browsedPageResult;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final ManagedPersonalFeedbackPerson getManagedPersonCopy(final PersonalFeedbackPerson person)
   {
      return personalFeedbackManager.getManagedPersonCopy(person);
   }


   final void showItemInBrowser(final PersonalFeedbackPersonProfile personProfile)
   {
      feedbackUIManager.showItemInBrowser(personProfile);
   }


   final void requestGetPersonBasicFeedbackSummary(final PersonalFeedbackPerson person)
   {
      handleRequestGetPersonBasicFeedbackSummary(person);
   }


   final void requestGetPersonDetailedFeedbackSummary(final PersonalFeedbackPerson person)
   {
      handleRequestGetPersonDetailedFeedbackSummary(person);
   }


   final void requestAddPersonFeedbackSubmission(final PersonalFeedbackPerson person, final PersonalFeedbackSubmission personalFeedbackSubmission)
   {
      handleRequestAddPersonFeedbackSubmission(person, personalFeedbackSubmission);
   }


   final void requestGetPersonFeedbackSubmission(final PersonalFeedbackPerson person)
   {
      handleRequestGetPersonFeedbackSubmission(person);
   }


   final void requestRemovePersonFeedbackSubmission(final PersonalFeedbackPerson person)
   {
      handleRequestRemovePersonFeedbackSubmission(person);
   }


   final void requestGetNewFeedbackItemsSample(final CriteriaFeedbackFeaturedItemsFilter newItemsFilter)
   {
      handleRequestGetNewFeedbackItemsSample(newItemsFilter);
   }


   final void requestGetHotFeedbackItemsSample(final CriteriaFeedbackFeaturedItemsFilter hotItemsFilter)
   {
      handleRequestGetHotFeedbackItemsSample(hotItemsFilter);
   }


   final void closeFeaturedFeedbackItemsPanel(final PersonalFeedbackFeaturedItemsPanel featuredItemsPanel)
   {
      handleCloseFeaturedFeedbackItemsPanel(featuredItemsPanel);
   }


   final void showFormSubcomponent(final JComponent component)
   {
      feedbactoryPad.showFormSubcomponent(component, true);
   }


   final void dismissFormSubcomponent(final JComponent component)
   {
      feedbactoryPad.dismissLockingComponent(component);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public FeedbackCategory getFeedbackCategory()
   {
      return FeedbackCategory.Personal;
   }


   @Override
   final public List<FeedbackWebsiteURL> getWebsiteURLs()
   {
      return browserEventManager.getWebsiteURLs();
   }


   @Override
   final public List<FeedbackMenuItem> getFeedbackMenuItems()
   {
      return handleGetFeedbackMenuItems();
   }


   @Override
   final public void invokeMenuItem(final FeedbackMenuItem menuItem)
   {
      handleInvokeMenuItem(menuItem);
   }


   @Override
   final public PersonalFeedbackBrowsedPageResult reportBrowsedPage(final BrowserService browserService)
   {
      return handleReportBrowsedPage(browserService);
   }


   final public boolean isWebsiteEnabled(final PersonalFeedbackWebsite website)
   {
      return personalFeedbackManager.isWebsiteEnabled(website);
   }


   final public void reportBrowserActiveItemChanged(final PersonalFeedbackPersonProfile browsedPersonProfile)
   {
      personalFeedbackManager.addPersonProfileIfAbsent(browsedPersonProfile);
   }


   @Override
   final public FeedbackPanelUIComponent activateFeedbackPanelComponent(final FeedbackPanel feedbackPanel)
   {
      activeRootPanel = new PersonalFeedbackRootPanel(this, feedbackPanel);
      return activeRootPanel;
   }


   @Override
   final public void deactivateFeedbackPanelComponent(final FeedbackPanelUIComponent feedbackPanelUIComponent)
   {
      activeRootPanel.dispose();
      activeRootPanel = null;
   }


   @Override
   final public FeedbackCategoryDataFormatter getFeedbackDataFormatter()
   {
      return stringFormatter;
   }


   @Override
   final public void shutdown()
   {
      ClientUtilities.shutdownAndAwaitTermination(requestTaskExecutor, "PersonalFeedbackUIManager.TaskRequest");
   }
}