/* Memos:
 * - The updating of an item's basic feedback summary is handled a little differently to that of their detailed feedback summary or feedback submission.
 *   The reason for this is that the basic feedback summary is a data element that is often updated as a side-effect of many requests, such as submitting or
 *   removing feedback, retrieving a list of all an account's feedback submissions, or retrieving a list of new or 'hot' feedback items.
 *
 */

package com.feedbactory.client.core.feedback.personal;


import com.feedbactory.client.core.network.DataAvailabilityStatus;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.client.core.feedback.FeedbackNetworkGatewayManager;
import com.feedbactory.client.core.feedback.FeedbackCategoryHandler;
import com.feedbactory.client.core.feedback.FeedbackManager;
import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.client.core.useraccount.FeedbactoryUserAccount;
import com.feedbactory.client.core.useraccount.AccountEventAdapter;
import com.feedbactory.client.core.useraccount.IllegalAccountSessionRequestState;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.feedback.FeedbackItem;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import com.feedbactory.shared.feedback.FeedbackResultSummary;
import com.feedbactory.shared.feedback.FeedbackSubmissionStatus;
import com.feedbactory.shared.feedback.personal.CriteriaFeedbackFeaturedItemsFilter;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteria;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaAttributes;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackDetailedSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackFeaturedPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmission;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleKeyValue;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsiteSet;
import com.feedbactory.shared.network.BasicOperationStatus;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


final public class PersonalFeedbackManager extends FeedbackCategoryHandler
{
   final private FeedbackManager feedbackManager;

   final private PersonalFeedbackNetworkGateway personalFeedbackNetworkGateway;

   final private AccountSessionManager userAccountManager;
   private FeedbactoryUserAccount signedInUserAccount;
   final private ReadWriteLock userAccountLock = new ReentrantReadWriteLock();

   final private ConcurrentHashMap<PersonalFeedbackPerson, ManagedPersonalFeedbackPerson> personalFeedbackNodes = new ConcurrentHashMap<PersonalFeedbackPerson, ManagedPersonalFeedbackPerson>(16, 0.75f, 2);

   final private List<PersonalFeedbackPersonChangeListener> changeListeners = new CopyOnWriteArrayList<PersonalFeedbackPersonChangeListener>();

   final private Set<PersonalFeedbackWebsite> enabledWebsites = new PersonalFeedbackWebsiteSet();
   final private ReadWriteLock enabledWebsitesLock = new ReentrantReadWriteLock();


   public PersonalFeedbackManager(final FeedbackManager feedbackManager, final FeedbackNetworkGatewayManager feedbackNetworkManager, final AccountSessionManager userAccountManager)
   {
      this.feedbackManager = feedbackManager;
      personalFeedbackNetworkGateway = new PersonalFeedbackNetworkGateway(this, feedbackNetworkManager);
      this.userAccountManager = userAccountManager;

      initialise();
   }


   private void initialise()
   {
      userAccountManager.addUserAccountEventListener(new AccountEventAdapter()
      {
         @Override
         final public void signedInToUserAccount(final FeedbactoryUserAccount userAccount)
         {
            handleUserAccountDetailsUpdated(userAccount);
         }


         @Override
         final public void userAccountDetailsUpdated(final FeedbactoryUserAccount userAccount)
         {
            handleUserAccountDetailsUpdated(userAccount);
         }


         @Override
         final public void signedOutOfUserAccount(final FeedbactoryUserAccount userAccount)
         {
            handleSignedOutOfUserAccount();
         }
      });
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleUserAccountDetailsUpdated(final FeedbactoryUserAccount userAccount)
   {
      try
      {
         userAccountLock.writeLock().lock();

         signedInUserAccount = userAccount;
      }
      finally
      {
         userAccountLock.writeLock().unlock();
      }
   }


   private void handleSignedOutOfUserAccount()
   {
      try
      {
         userAccountLock.writeLock().lock();

         signedInUserAccount = null;

         for (final ManagedPersonalFeedbackPerson managedPerson: personalFeedbackNodes.values())
            updateFeedbackSubmission(managedPerson, DataAvailabilityStatus.NotAvailable, PersonalFeedbackSubmission.EmptyPersonalFeedbackSubmission);
      }
      finally
      {
         userAccountLock.writeLock().unlock();
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void setWebsiteEnabled(final PersonalFeedbackWebsite website, final boolean isEnabled)
   {
      try
      {
         enabledWebsitesLock.writeLock().lock();

         if (isEnabled)
            enabledWebsites.add(website);
         else
            enabledWebsites.remove(website);
      }
      finally
      {
         enabledWebsitesLock.writeLock().unlock();
      }
   }


   private void handleSetEnabledWebsites(final Set<PersonalFeedbackWebsite> enabledWebsites)
   {
      try
      {
         enabledWebsitesLock.writeLock().lock();

         this.enabledWebsites.clear();
         this.enabledWebsites.addAll(enabledWebsites);
      }
      finally
      {
         enabledWebsitesLock.writeLock().unlock();
      }
   }


   private boolean handleIsWebsiteEnabled(final PersonalFeedbackWebsite website)
   {
      try
      {
         enabledWebsitesLock.readLock().lock();

         return enabledWebsites.contains(website);
      }
      finally
      {
         enabledWebsitesLock.readLock().unlock();
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void updateProfileDetails(final ManagedPersonalFeedbackPerson managedPerson, final PersonalFeedbackPersonProfile personProfile)
   {
      boolean updated = false;

      synchronized (managedPerson)
      {
         if (! managedPerson.getPersonProfile().equals(personProfile))
         {
            managedPerson.setPersonProfile(personProfile);
            updated = true;
         }
      }

      if (updated)
      {
         for (final PersonalFeedbackPersonChangeListener listener : changeListeners)
            listener.personProfileDetailsChanged(personProfile);
      }
   }


   private void updateBasicFeedbackSummaryAvailabilityStatus(final ManagedPersonalFeedbackPerson managedPerson,
                                                             final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus)
   {
      PersonalFeedbackPerson person = null;
      boolean updated = false;

      synchronized (managedPerson)
      {
         if (managedPerson.getBasicFeedbackSummaryAvailabilityStatus() != basicFeedbackSummaryAvailabilityStatus)
         {
            managedPerson.setBasicFeedbackSummaryAvailabilityStatus(basicFeedbackSummaryAvailabilityStatus);
            person = managedPerson.getPersonProfile().person;
            updated = true;
         }
      }

      if (updated)
      {
         for (final PersonalFeedbackPersonChangeListener listener : changeListeners)
            listener.basicFeedbackSummaryAvailabilityStatusChanged(person, basicFeedbackSummaryAvailabilityStatus);
      }
   }


   private void updateBasicFeedbackSummary(final ManagedPersonalFeedbackPerson managedPerson, final DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus,
                                           final PersonalFeedbackBasicSummary basicFeedbackSummary)
   {
      PersonalFeedbackPerson person = null;
      boolean updated = false;

      synchronized (managedPerson)
      {
         if ((managedPerson.getBasicFeedbackSummaryAvailabilityStatus() != basicFeedbackSummaryAvailabilityStatus) ||
             (! managedPerson.getBasicFeedbackSummary().equals(basicFeedbackSummary)))
         {
            managedPerson.setBasicFeedbackSummary(basicFeedbackSummary);
            managedPerson.setBasicFeedbackSummaryAvailabilityStatus(basicFeedbackSummaryAvailabilityStatus);
            person = managedPerson.getPersonProfile().person;
            updated = true;
         }
      }

      if (updated)
      {
         for (final PersonalFeedbackPersonChangeListener listener : changeListeners)
            listener.basicFeedbackSummaryChanged(person, basicFeedbackSummaryAvailabilityStatus, basicFeedbackSummary);
      }
   }


   private void updateDetailedFeedbackSummaryAvailabilityStatus(final ManagedPersonalFeedbackPerson managedPerson,
                                                                final DataAvailabilityStatus detailedFeedbackAvailabilityStatus)
   {
      PersonalFeedbackPerson person = null;
      boolean updated = false;

      synchronized (managedPerson)
      {
         if (managedPerson.getDetailedFeedbackSummaryAvailabilityStatus() != detailedFeedbackAvailabilityStatus)
         {
            managedPerson.setDetailedFeedbackSummaryAvailabilityStatus(detailedFeedbackAvailabilityStatus);
            person = managedPerson.getPersonProfile().person;
            updated = true;
         }
      }

      if (updated)
      {
         for (final PersonalFeedbackPersonChangeListener listener : changeListeners)
            listener.detailedFeedbackSummaryAvailabilityStatusChanged(person, detailedFeedbackAvailabilityStatus);
      }
   }


   private void updateDetailedFeedbackSummary(final ManagedPersonalFeedbackPerson managedPerson, final DataAvailabilityStatus detailedFeedbackAvailabilityStatus,
                                              final PersonalFeedbackDetailedSummary detailedFeedbackSummary)
   {
      PersonalFeedbackPerson person = null;
      boolean updated = false;

      synchronized (managedPerson)
      {
         if ((managedPerson.getDetailedFeedbackSummaryAvailabilityStatus() != detailedFeedbackAvailabilityStatus) ||
             (! managedPerson.getDetailedFeedbackSummary().equals(detailedFeedbackSummary)))
         {
            managedPerson.setDetailedFeedbackSummary(detailedFeedbackSummary);
            managedPerson.setDetailedFeedbackSummaryAvailabilityStatus(detailedFeedbackAvailabilityStatus);
            person = managedPerson.getPersonProfile().person;
            updated = true;
         }
      }

      if (updated)
      {
         for (final PersonalFeedbackPersonChangeListener listener : changeListeners)
            listener.detailedFeedbackSummaryChanged(person, detailedFeedbackAvailabilityStatus, detailedFeedbackSummary);
      }
   }


   private void updateFeedbackSubmissionAvailabilityStatus(final ManagedPersonalFeedbackPerson managedPerson,
                                                           final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus)
   {
      PersonalFeedbackPerson person = null;
      boolean updated = false;

      synchronized (managedPerson)
      {
         if (managedPerson.getFeedbackSubmissionAvailabilityStatus() != feedbackSubmissionAvailabilityStatus)
         {
            managedPerson.setFeedbackSubmissionAvailabilityStatus(feedbackSubmissionAvailabilityStatus);
            person = managedPerson.getPersonProfile().person;
            updated = true;
         }
      }

      if (updated)
      {
         for (final PersonalFeedbackPersonChangeListener listener : changeListeners)
            listener.feedbackSubmissionAvailabilityStatusChanged(person, feedbackSubmissionAvailabilityStatus);
      }
   }


   private void updateFeedbackSubmission(final ManagedPersonalFeedbackPerson managedPerson, final DataAvailabilityStatus feedbackSubmissionAvailabilityStatus,
                                         final PersonalFeedbackSubmission feedbackSubmission)
   {
      PersonalFeedbackPerson person = null;
      boolean updated = false;

      synchronized (managedPerson)
      {
         if ((managedPerson.getFeedbackSubmissionAvailabilityStatus() != feedbackSubmissionAvailabilityStatus) ||
             (! managedPerson.getFeedbackSubmission().equals(feedbackSubmission)))
         {
            managedPerson.setFeedbackSubmission(feedbackSubmission);
            managedPerson.setFeedbackSubmissionAvailabilityStatus(feedbackSubmissionAvailabilityStatus);
            person = managedPerson.getPersonProfile().person;
            updated = true;
         }
      }

      if (updated)
      {
         for (final PersonalFeedbackPersonChangeListener listener : changeListeners)
            listener.feedbackSubmissionChanged(person, feedbackSubmissionAvailabilityStatus, feedbackSubmission);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private ManagedPersonalFeedbackPerson handleGetManagedPersonCopy(final PersonalFeedbackPerson person)
   {
      final ManagedPersonalFeedbackPerson managedPerson = personalFeedbackNodes.get(person);
      if (managedPerson != null)
      {
         synchronized (managedPerson)
         {
            return new ManagedPersonalFeedbackPerson(managedPerson);
         }
      }
      else
         return null;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleAddPersonProfileIfAbsent(final PersonalFeedbackPersonProfile personProfile)
   {
      /* Add the profile or fill in any changed profile fields. Changing updated fields should happen rarely when re-browsing the same items during a session,
       * but it will be more common when featured (new or hot) items are pulled from the server first before being browsed.
       * The server data may be out of date. Either way it's important to pick up any changes to minimise item fragmentation on the server.
       *
       * There's the assumption here that if an item profile associated with any website would become invalid by nulling one or more of its fields
       * (eg. mandatory user ID was non-null after an earlier browse but a more recent browse indicates that it's null) then no 'updated' item profile
       * would be created for it, and this method would likewise not be called with that invalid profile.
       */

      /* Whether or not the item is new to or changed in this class, push the feedback profile to the parent feedback manager
       * so that it may update it at its end. In practice this probably only needs to be done when the feedback profile
       * has changed within this class, but it's best not to make assumptions. Let the parent feedback manager decide how to
       * handle the update.
       */
      feedbackManager.reportUpdatedFeedbackItemProfile(personProfile);

      ManagedPersonalFeedbackPerson managedPerson = personalFeedbackNodes.get(personProfile.person);

      if (managedPerson == null)
      {
         managedPerson = new ManagedPersonalFeedbackPerson();

         // Ensure the visibility of the non-final field data to subsequent threads.
         synchronized (managedPerson)
         {
            managedPerson.setPersonProfile(personProfile);
            managedPerson.setBasicFeedbackSummaryAvailabilityStatus(DataAvailabilityStatus.NotAvailable);
            managedPerson.setBasicFeedbackSummary(PersonalFeedbackBasicSummary.EmptyFeedbackBasicSummary);
            managedPerson.setDetailedFeedbackSummaryAvailabilityStatus(DataAvailabilityStatus.NotAvailable);
            managedPerson.setDetailedFeedbackSummary(PersonalFeedbackDetailedSummary.EmptyFeedbackSummary);
            managedPerson.setFeedbackSubmissionAvailabilityStatus(DataAvailabilityStatus.NotAvailable);
            managedPerson.setFeedbackSubmission(PersonalFeedbackSubmission.EmptyPersonalFeedbackSubmission);
         }

         final ManagedPersonalFeedbackPerson existingManagedPerson = personalFeedbackNodes.putIfAbsent(personProfile.person, managedPerson);

         if (existingManagedPerson != null)
         {
            managedPerson = existingManagedPerson;
            updateProfileDetails(managedPerson, personProfile);
         }
      }
      else
         updateProfileDetails(managedPerson, personProfile);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private PersonalFeedbackBasicSummaryResult handleGetPersonBasicFeedbackSummary(final PersonalFeedbackPerson person)
   {
      final ManagedPersonalFeedbackPerson managedPerson = personalFeedbackNodes.get(person);

      if (managedPerson == null)
         return new PersonalFeedbackBasicSummaryResult(NetworkRequestStatus.Consumed);

      final DataAvailabilityStatus originalDataStatus;

      synchronized (managedPerson)
      {
         originalDataStatus = managedPerson.getBasicFeedbackSummaryAvailabilityStatus();

         if (originalDataStatus == DataAvailabilityStatus.Retrieving)
            return new PersonalFeedbackBasicSummaryResult(NetworkRequestStatus.Consumed);

         updateBasicFeedbackSummaryAvailabilityStatus(managedPerson, DataAvailabilityStatus.Retrieving);
      }

      PersonalFeedbackBasicSummaryResult requestResult = null;

      try
      {
         requestResult = personalFeedbackNetworkGateway.sendGetPersonBasicFeedbackSummaryRequest(person);
      }
      finally
      {
         // If there was a non-network exception don't catch it but be sure to set the failed state of the data.
         postProcessGetPersonBasicFeedbackSummary(person, managedPerson, requestResult, originalDataStatus);
      }

      return requestResult;
   }


   private void postProcessGetPersonBasicFeedbackSummary(final PersonalFeedbackPerson person, final ManagedPersonalFeedbackPerson managedPerson,
                                                         final PersonalFeedbackBasicSummaryResult requestResult, final DataAvailabilityStatus originalDataStatus)
   {
      if ((requestResult != null) && (requestResult.requestStatus == NetworkRequestStatus.OK))
      {
         if (requestResult.operationStatus == BasicOperationStatus.OK)
         {
            setWebsiteEnabled(person.getWebsite(), true);

            updateBasicFeedbackSummary(managedPerson, DataAvailabilityStatus.Available, requestResult.basicSummary);

            feedbackManager.reportUpdatedFeedbackSummary(person, requestResult.basicSummary);
         }
         else
         {
            setWebsiteEnabled(person.getWebsite(), false);
            // Restore the original status.
            updateBasicFeedbackSummaryAvailabilityStatus(managedPerson, originalDataStatus);
         }
      }
      else
         updateBasicFeedbackSummary(managedPerson, DataAvailabilityStatus.Failed, PersonalFeedbackBasicSummary.EmptyFeedbackBasicSummary);
   }


   private PersonalFeedbackDetailedSummaryResult handleGetPersonDetailedFeedbackSummary(final PersonalFeedbackPerson person)
   {
      final ManagedPersonalFeedbackPerson managedPerson = personalFeedbackNodes.get(person);
      if (managedPerson == null)
         return new PersonalFeedbackDetailedSummaryResult(NetworkRequestStatus.Consumed);

      final DataAvailabilityStatus originalDataStatus;

      synchronized (managedPerson)
      {
         originalDataStatus = managedPerson.getDetailedFeedbackSummaryAvailabilityStatus();

         if (originalDataStatus == DataAvailabilityStatus.Retrieving)
            return new PersonalFeedbackDetailedSummaryResult(NetworkRequestStatus.Consumed);

         updateDetailedFeedbackSummaryAvailabilityStatus(managedPerson, DataAvailabilityStatus.Retrieving);
      }

      PersonalFeedbackDetailedSummaryResult requestResult = null;

      try
      {
         requestResult = personalFeedbackNetworkGateway.sendGetPersonDetailedFeedbackSummaryRequest(person);
      }
      finally
      {
         postProcessGetPersonDetailedFeedbackSummary(person, managedPerson, requestResult, originalDataStatus);
      }

      return requestResult;
   }


   private void postProcessGetPersonDetailedFeedbackSummary(final PersonalFeedbackPerson person, final ManagedPersonalFeedbackPerson managedPerson,
                                                            final PersonalFeedbackDetailedSummaryResult requestResult, final DataAvailabilityStatus originalDataStatus)
   {
      if ((requestResult != null) && (requestResult.requestStatus == NetworkRequestStatus.OK))
      {
         if (requestResult.operationStatus == BasicOperationStatus.OK)
         {
            setWebsiteEnabled(person.getWebsite(), true);
            updateDetailedFeedbackSummary(managedPerson, DataAvailabilityStatus.Available, requestResult.detailedSummary);
         }
         else
         {
            setWebsiteEnabled(person.getWebsite(), false);
            // Restore the original status.
            updateDetailedFeedbackSummaryAvailabilityStatus(managedPerson, originalDataStatus);
         }
      }
      else
      {
         // If there was a non-network exception don't catch it but be sure to set the failed state of the data.
         updateDetailedFeedbackSummary(managedPerson, DataAvailabilityStatus.Failed, PersonalFeedbackDetailedSummary.EmptyFeedbackSummary);
      }
   }


   private PersonalFeedbackGetSubmissionResult handleGetPersonFeedbackSubmission(final PersonalFeedbackPerson person,
                                                                                 final FeedbactoryUserAccount requestingUserAccount)
   {
      /* It's possible that this request is sent at the same time that a signed in user has requested to sign out of their account.
       * There are two possibilities:
       *
       * 1) This request gets in first, in which case it will eventually grab the session manager's lock (note: NOT the local userAccountLock
       *    used by this instance), forcing the sign out request to wait until it's finished. Even so there's still a small (impossible?) chance
       *    that after this request has released the user account session lock and before it can continue processing this method, the account sign out
       *    request is completely processed. If that were to happen, the account manager would fire off its signed out notifications and the registered
       *    listener of this class would blank any attached feedback submission for the person and note the change in user account, all while holding
       *    the userAccountLock.
       *
       * 2) The sign out task gets in first, in which case it will eventually grab the session manager's lock, forcing this request to
       *    wait until it's finished. When this request tries to resume and send a regular session request, the session manager will throw
       *    an IllegalAccountSessionRequestState since there will no longer be any user account session. This exception will be caught here
       *    and no further processing will be done.
       *
       * Note that in both of the above cases, the AccountEventAdapter registered on this class will be fired on sign out, resulting in every listed
       * person node's feedback submission & its data availability status to be reset to a blank state. It's because of that reset state
       * that this method should always bail out of performing its own post-processing if it detects that the user account has changed.
       *
       * Note also that as tempting as it may be to try a different approach and simply lock on the AccountSessionManager's session lock from the
       * outset, this would completely choke the flow of requests, eg. user would be left hanging on a Sign Out request while there is an
       * active getPersonFeedbackSubmission() request running in the background after a page load.
       * Another possibility, essentially identical to the solution here, is for AccountSessionManager to give out its lock and this class
       * simply replaces all use of its own local userAccountLock with that provided by AccountSessionManager. I think it's safer to apply the locking on
       * a local level rather than have the AccountSessionManager give out its private session lock though.
       */
      final ManagedPersonalFeedbackPerson managedPerson = personalFeedbackNodes.get(person);
      if (managedPerson == null)
         return new PersonalFeedbackGetSubmissionResult(NetworkRequestStatus.Consumed);

      final DataAvailabilityStatus originalDataStatus;

      try
      {
         userAccountLock.readLock().lock();

         if (requestingUserAccount == signedInUserAccount)
         {
            synchronized (managedPerson)
            {
               originalDataStatus = managedPerson.getFeedbackSubmissionAvailabilityStatus();

               if (originalDataStatus == DataAvailabilityStatus.Retrieving)
                  return new PersonalFeedbackGetSubmissionResult(NetworkRequestStatus.Consumed);

               updateFeedbackSubmissionAvailabilityStatus(managedPerson, DataAvailabilityStatus.Retrieving);
            }
         }
         else
            return new PersonalFeedbackGetSubmissionResult(NetworkRequestStatus.Consumed);
      }
      finally
      {
         userAccountLock.readLock().unlock();
      }

      PersonalFeedbackGetSubmissionResult requestResult = null;

      try
      {
         requestResult = personalFeedbackNetworkGateway.sendGetPersonFeedbackSubmissionRequest(person);
         // Concurrency point referred to by point 1) above - after the request and lock have been released, but before post-request processing.
      }
      catch (final IllegalAccountSessionRequestState illegalAccountSessionRequestState)
      {
         /* Related to the comment above (possibility number 2 in this instance), the user may have signed out by the time that this request is processed.
          * In this instance, let the post-processing method below do its thing, including returning a result of 'Consumed'.
          */
      }
      finally
      {
         /* Note the possibility of overriding the original requestResult by the post processing method here.
          * This is different to the rare (and dangerous) practice of overturning a thrown and uncaught exception by
          * returning a value from the finally clause; if there is an exception thrown here other than IllegalAccountSessionRequestState,
          * the post-process method will still be executed but the exception will still be thrown.
          */
         requestResult = postProcessGetPersonFeedbackSubmission(person, managedPerson, requestingUserAccount, requestResult, originalDataStatus);
      }

      return requestResult;
   }


   private PersonalFeedbackGetSubmissionResult postProcessGetPersonFeedbackSubmission(final PersonalFeedbackPerson person,
                                                                                      final ManagedPersonalFeedbackPerson managedPerson,
                                                                                      final FeedbactoryUserAccount requestingUserAccount,
                                                                                      final PersonalFeedbackGetSubmissionResult requestResult,
                                                                                      final DataAvailabilityStatus originalDataStatus)
   {
      try
      {
         userAccountLock.readLock().lock();

         /* If the user account changes in the tiny window between completing the network request and reaching here,
          * this processing needs to bail out with a 'Consumed' result.
          */
         if (requestingUserAccount == signedInUserAccount)
         {
            // If there was a non-network exception don't catch it but be sure to set the failed state of the data.
            if ((requestResult != null) && (requestResult.requestStatus == NetworkRequestStatus.OK))
            {
               if (requestResult.operationStatus == BasicOperationStatus.OK)
               {
                  setWebsiteEnabled(person.getWebsite(), true);
                  updateFeedbackSubmission(managedPerson, DataAvailabilityStatus.Available, requestResult.submission);
               }
               else
               {
                  setWebsiteEnabled(person.getWebsite(), false);
                  // Restore the original status.
                  updateFeedbackSubmissionAvailabilityStatus(managedPerson, originalDataStatus);
               }
            }
            else
               updateFeedbackSubmission(managedPerson, DataAvailabilityStatus.Failed, PersonalFeedbackSubmission.EmptyPersonalFeedbackSubmission);
         }
         else
            return new PersonalFeedbackGetSubmissionResult(NetworkRequestStatus.Consumed);
      }
      finally
      {
         userAccountLock.readLock().unlock();
      }

      return requestResult;
   }


   private PersonalFeedbackAddSubmissionResult handleAddPersonFeedbackSubmission(final PersonalFeedbackPerson person,
                                                                                 final PersonalFeedbackSubmission personalFeedbackSubmission)
   {
      final ManagedPersonalFeedbackPerson managedPerson = personalFeedbackNodes.get(person);
      if (managedPerson == null)
         return new PersonalFeedbackAddSubmissionResult(NetworkRequestStatus.Consumed);

      final DataAvailabilityStatus originalDataStatus;
      final PersonalFeedbackPersonProfile personProfile;

      synchronized (managedPerson)
      {
         originalDataStatus = managedPerson.getFeedbackSubmissionAvailabilityStatus();

         if (originalDataStatus == DataAvailabilityStatus.Retrieving)
            return new PersonalFeedbackAddSubmissionResult(NetworkRequestStatus.Consumed);

         updateFeedbackSubmissionAvailabilityStatus(managedPerson, DataAvailabilityStatus.Retrieving);

         personProfile = managedPerson.getPersonProfile();
      }

      final PersonalFeedbackCriteriaType criteriaType = person.getCriteriaType();
      final PersonalFeedbackSubmission compressedFeedbackSubmission = getCompressedFeedbackSubmission(personalFeedbackSubmission, criteriaType.attributes);

      PersonalFeedbackAddSubmissionResult requestResult = null;

      try
      {
         requestResult = personalFeedbackNetworkGateway.sendAddPersonFeedbackSubmissionRequest(personProfile, compressedFeedbackSubmission);
      }
      finally
      {
         postProcessAddPersonFeedbackSubmission(personProfile, managedPerson, compressedFeedbackSubmission, requestResult, originalDataStatus);
      }

      return requestResult;
   }


   @SuppressWarnings("unchecked")
   private <E extends Enum<E> & PersonalFeedbackCriteria> PersonalFeedbackSubmission getCompressedFeedbackSubmission(final PersonalFeedbackSubmission feedbackSubmission,
                                                                                                                     final PersonalFeedbackCriteriaAttributes<E> criteriaAttributes)
   {
      final Map<E, PersonalFeedbackSubmissionScaleKeyValue> sourceCriteriaFeedback = (Map<E, PersonalFeedbackSubmissionScaleKeyValue>) feedbackSubmission.criteriaSubmissions;
      final EnumMap<E, PersonalFeedbackSubmissionScaleKeyValue> compressedCriteriaFeedback = new EnumMap<E, PersonalFeedbackSubmissionScaleKeyValue>(criteriaAttributes.getCriteriaClass());

      for (final Entry<E, PersonalFeedbackSubmissionScaleKeyValue> criteriaEntry : sourceCriteriaFeedback.entrySet())
      {
         // Eliminate any NoRating criteria values.
         if (criteriaEntry.getValue().value != PersonalFeedbackSubmission.NoRatingValue)
            compressedCriteriaFeedback.put(criteriaEntry.getKey(), criteriaEntry.getValue());
      }

      if (feedbackSubmission.isOverallRatingCalculatedFromCriteriaFeedback)
         return new PersonalFeedbackSubmission(compressedCriteriaFeedback);
      else
         return new PersonalFeedbackSubmission(feedbackSubmission.overallFeedbackRating, compressedCriteriaFeedback);
   }


   private void postProcessAddPersonFeedbackSubmission(final PersonalFeedbackPersonProfile personProfile, final ManagedPersonalFeedbackPerson managedPerson,
                                                       final PersonalFeedbackSubmission compressedFeedbackSubmission,
                                                       final PersonalFeedbackAddSubmissionResult requestResult, final DataAvailabilityStatus originalDataStatus)
   {
      if ((requestResult != null) && (requestResult.requestStatus == NetworkRequestStatus.OK))
      {
         if (requestResult.feedbackSubmissionStatus == FeedbackSubmissionStatus.OK)
         {
            setWebsiteEnabled(personProfile.getWebsite(), true);

            // These three updates don't have to be done atomically, but there's probably less overhead in grabbing the managedPerson lock just the once.
            synchronized (managedPerson)
            {
               updateFeedbackSubmission(managedPerson, DataAvailabilityStatus.Available, compressedFeedbackSubmission);

               // The basic feedback from everyone will have been updated as part of the feedback submission request.
               updateBasicFeedbackSummary(managedPerson, DataAvailabilityStatus.Available, requestResult.basicSummary);

               // The detailed feedback from everyone will now be unavailable until the user next tries to view it.
               updateDetailedFeedbackSummary(managedPerson, DataAvailabilityStatus.NotAvailable, PersonalFeedbackDetailedSummary.EmptyFeedbackSummary);
            }

            feedbackManager.reportAddedFeedbackSubmission(personProfile, compressedFeedbackSubmission.overallFeedbackRating, requestResult.basicSummary);
         }
         else
         {
            setWebsiteEnabled(personProfile.getWebsite(), false);
            // Restore the original status.
            updateFeedbackSubmissionAvailabilityStatus(managedPerson, originalDataStatus);
         }
      }
      else
      {
         // Restore the original status.
         updateFeedbackSubmissionAvailabilityStatus(managedPerson, originalDataStatus);
      }
   }


   private PersonalFeedbackBasicSummaryResult handleRemovePersonFeedbackSubmission(final PersonalFeedbackPerson person)
   {
      final ManagedPersonalFeedbackPerson managedPerson = personalFeedbackNodes.get(person);
      if (managedPerson == null)
         return new PersonalFeedbackBasicSummaryResult(NetworkRequestStatus.Consumed);

      final DataAvailabilityStatus originalDataStatus;

      synchronized (managedPerson)
      {
         originalDataStatus = managedPerson.getFeedbackSubmissionAvailabilityStatus();

         if (originalDataStatus == DataAvailabilityStatus.Retrieving)
            return new PersonalFeedbackBasicSummaryResult(NetworkRequestStatus.Consumed);

         updateFeedbackSubmissionAvailabilityStatus(managedPerson, DataAvailabilityStatus.Retrieving);
      }

      PersonalFeedbackBasicSummaryResult requestResult = null;

      try
      {
         requestResult = personalFeedbackNetworkGateway.sendRemovePersonFeedbackSubmissionRequest(person);
      }
      finally
      {
         postProcessRemovePersonFeedbackSubmission(person, managedPerson, requestResult, originalDataStatus);
      }

      return requestResult;
   }


   private void postProcessRemovePersonFeedbackSubmission(final PersonalFeedbackPerson person, final ManagedPersonalFeedbackPerson managedPerson,
                                                          final PersonalFeedbackBasicSummaryResult requestResult, final DataAvailabilityStatus originalDataStatus)
   {
      if ((requestResult != null) && (requestResult.requestStatus == NetworkRequestStatus.OK))
      {
         if (requestResult.operationStatus == BasicOperationStatus.OK)
         {
            setWebsiteEnabled(person.getWebsite(), true);

            // These three updates don't have to be done atomically, but there's probably less overhead in grabbing the managedPerson lock just the once.
            synchronized (managedPerson)
            {
               updateFeedbackSubmission(managedPerson, DataAvailabilityStatus.Available, PersonalFeedbackSubmission.EmptyPersonalFeedbackSubmission);

               // The basic feedback from everyone will have been updated as part of the feedback submission request.
               updateBasicFeedbackSummary(managedPerson, DataAvailabilityStatus.Available, requestResult.basicSummary);

               // The detailed feedback from everyone will now be unavailable until the user next tries to view it.
               updateDetailedFeedbackSummary(managedPerson, DataAvailabilityStatus.NotAvailable, PersonalFeedbackDetailedSummary.EmptyFeedbackSummary);
            }

            feedbackManager.reportRemovedFeedbackSubmission(person);
         }
         else
         {
            setWebsiteEnabled(person.getWebsite(), false);
            // Restore the original status.
            updateFeedbackSubmissionAvailabilityStatus(managedPerson, originalDataStatus);
         }
      }
      else
      {
         // Restore the original status.
         updateFeedbackSubmissionAvailabilityStatus(managedPerson, originalDataStatus);
      }
   }


   private PersonalFeedbackFeaturedItemsSampleResult handleGetNewFeedbackItemsSample(final CriteriaFeedbackFeaturedItemsFilter newItemsFilter)
   {
      final PersonalFeedbackFeaturedItemsSampleResult requestResult = personalFeedbackNetworkGateway.getNewFeedbackItemsSample(newItemsFilter);

      if (requestResult.requestStatus == NetworkRequestStatus.OK)
         updateBasicFeedbackSummaries(requestResult.featuredItemsSample);

      return requestResult;
   }


   private PersonalFeedbackFeaturedItemsSampleResult handleGetHotFeedbackItemsSample(final CriteriaFeedbackFeaturedItemsFilter hotItemsFilter)
   {
      final PersonalFeedbackFeaturedItemsSampleResult requestResult = personalFeedbackNetworkGateway.getHotFeedbackItemsSample(hotItemsFilter);

      if (requestResult.requestStatus == NetworkRequestStatus.OK)
         updateBasicFeedbackSummaries(requestResult.featuredItemsSample);

      return requestResult;
   }


   private void updateBasicFeedbackSummaries(final List<PersonalFeedbackFeaturedPerson> featuredPeopleFeedbackSample)
   {
      for (final PersonalFeedbackFeaturedPerson featuredPersonFeedback : featuredPeopleFeedbackSample)
      {
         if (featuredPersonFeedback.sortValue != FeedbactoryConstants.EndOfDataLong)
            updateBasicFeedbackSummary(featuredPersonFeedback.personProfile, featuredPersonFeedback.feedbackSummary, true);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private PersonalFeedbackPersonProfile handleGetPersonProfile(final PersonalFeedbackPerson person)
   {
      final ManagedPersonalFeedbackPerson managedPerson = personalFeedbackNodes.get(person);
      if (managedPerson != null)
      {
         synchronized (managedPerson)
         {
            return managedPerson.getPersonProfile();
         }
      }
      else
         return null;
   }


   private PersonalFeedbackBasicSummary handleGetBasicFeedbackSummary(final PersonalFeedbackPerson person)
   {
      final ManagedPersonalFeedbackPerson managedPerson = personalFeedbackNodes.get(person);
      if (managedPerson != null)
      {
         synchronized (managedPerson)
         {
            return managedPerson.getBasicFeedbackSummary();
         }
      }
      else
         return null;
   }


   private PersonalFeedbackPersonProfile updateBasicFeedbackSummary(final PersonalFeedbackPersonProfile personProfile, final PersonalFeedbackBasicSummary basicFeedbackSummary,
                                                                    final boolean notifyParentFeedbackManager)
   {
      /* This method is called by:
       * - The methods that retrieve 'featured' feedback, eg. new and hot rated feedback.
       * - The parent feedback manager when a user retrieves the summary of all their feedback submissions.
       * 
       * The person profile argument is pulled from server data which was correct at the time of the submission, but may now be out of date.
       * So, unlike the addPersonProfileIfAbsent() method where profile fields are progressively updated if necessary, this method assumes
       * that any existing profile fields (possibly browsed during this session, possibly also pulled from the server via a previous request) should
       * not be overwritten by the data retrieved from the server.
       */

      /* Whether or not the item is new to or changed in this class, push the feedback summary update to the parent feedback manager
       * if the notifyParentFeedbackManager is set. In practice this probably only needs to be done when the feedback summary data
       * has changed within this class, but it's best not to make assumptions. Let the parent feedback manager decide how to
       * handle the update.
       */
      if (notifyParentFeedbackManager)
         feedbackManager.reportUpdatedFeedbackSummary(personProfile.person, basicFeedbackSummary);

      ManagedPersonalFeedbackPerson managedPerson = personalFeedbackNodes.get(personProfile.person);

      if (managedPerson == null)
      {
         managedPerson = new ManagedPersonalFeedbackPerson();

         // Ensure the visibility of the non-final field data to subsequent threads.
         synchronized (managedPerson)
         {
            managedPerson.setPersonProfile(personProfile);
            managedPerson.setBasicFeedbackSummaryAvailabilityStatus(DataAvailabilityStatus.Available);
            managedPerson.setBasicFeedbackSummary(basicFeedbackSummary);
            managedPerson.setDetailedFeedbackSummaryAvailabilityStatus(DataAvailabilityStatus.NotAvailable);
            managedPerson.setDetailedFeedbackSummary(PersonalFeedbackDetailedSummary.EmptyFeedbackSummary);
            managedPerson.setFeedbackSubmissionAvailabilityStatus(DataAvailabilityStatus.NotAvailable);
            managedPerson.setFeedbackSubmission(PersonalFeedbackSubmission.EmptyPersonalFeedbackSubmission);
         }

         final ManagedPersonalFeedbackPerson existingManagedPerson = personalFeedbackNodes.putIfAbsent(personProfile.person, managedPerson);

         if (existingManagedPerson != null)
         {
            /* If this code branch just missed out on adding the new managed person, eg. due to a browser event getting in first, the feedback summary
             * still needs to be updated on the existing managed person object.
             */
            managedPerson = existingManagedPerson;
            updateBasicFeedbackSummary(managedPerson, DataAvailabilityStatus.Available, basicFeedbackSummary);
         }
      }
      else
         updateBasicFeedbackSummary(managedPerson, DataAvailabilityStatus.Available, basicFeedbackSummary);

      synchronized (managedPerson)
      {
         // Return this manager's most up-to-date version of the profile details.
         return managedPerson.getPersonProfile();
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void setEnabledWebsites(final Set<PersonalFeedbackWebsite> enabledWebsites)
   {
      handleSetEnabledWebsites(enabledWebsites);
   }


   final public boolean isWebsiteEnabled(final PersonalFeedbackWebsite website)
   {
      return handleIsWebsiteEnabled(website);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void addPersonalFeedbackPersonChangeListener(final PersonalFeedbackPersonChangeListener changeListener)
   {
      changeListeners.add(changeListener);
   }


   final public void removePersonalFeedbackPersonChangeListener(final PersonalFeedbackPersonChangeListener changeListener)
   {
      changeListeners.remove(changeListener);
   }


   final public ManagedPersonalFeedbackPerson getManagedPersonCopy(final PersonalFeedbackPerson person)
   {
      return handleGetManagedPersonCopy(person);
   }


   final public void addPersonProfileIfAbsent(final PersonalFeedbackPersonProfile profile)
   {
      handleAddPersonProfileIfAbsent(profile);
   }


   final public PersonalFeedbackBasicSummaryResult getPersonBasicFeedbackSummary(final PersonalFeedbackPerson person)
   {
      return handleGetPersonBasicFeedbackSummary(person);
   }


   final public PersonalFeedbackDetailedSummaryResult getPersonDetailedFeedbackSummary(final PersonalFeedbackPerson person)
   {
      return handleGetPersonDetailedFeedbackSummary(person);
   }


   final public PersonalFeedbackGetSubmissionResult getPersonFeedbackSubmission(final PersonalFeedbackPerson person,
                                                                                final FeedbactoryUserAccount requestingUserAccount)
   {
      return handleGetPersonFeedbackSubmission(person, requestingUserAccount);
   }


   final public PersonalFeedbackAddSubmissionResult addPersonFeedbackSubmission(final PersonalFeedbackPerson person,
                                                                                final PersonalFeedbackSubmission personalFeedbackSubmission)
   {
      return handleAddPersonFeedbackSubmission(person, personalFeedbackSubmission);
   }


   final public PersonalFeedbackBasicSummaryResult removePersonFeedbackSubmission(final PersonalFeedbackPerson person)
   {
      return handleRemovePersonFeedbackSubmission(person);
   }


   final public PersonalFeedbackFeaturedItemsSampleResult getNewFeedbackItemsSample(final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      return handleGetNewFeedbackItemsSample(featuredItemsFilter);
   }


   final public PersonalFeedbackFeaturedItemsSampleResult getHotFeedbackItemsSample(final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      return handleGetHotFeedbackItemsSample(featuredItemsFilter);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public FeedbackCategory getCategory()
   {
      return FeedbackCategory.Personal;
   }


   @Override
   final public PersonalFeedbackNetworkGateway getNetworkGateway()
   {
      return personalFeedbackNetworkGateway;
   }


   @Override
   final public PersonalFeedbackPersonProfile getFeedbackItemProfile(final FeedbackItem feedbackItem)
   {
      return handleGetPersonProfile((PersonalFeedbackPerson) feedbackItem);
   }


   @Override
   final public PersonalFeedbackBasicSummary getFeedbackResultSummary(final FeedbackItem feedbackItem)
   {
      return handleGetBasicFeedbackSummary((PersonalFeedbackPerson) feedbackItem);
   }


   @Override
   final public PersonalFeedbackPersonProfile updateItemFeedbackSummary(final FeedbackItemProfile itemProfile, final FeedbackResultSummary feedbackResultSummary)
   {
      return updateBasicFeedbackSummary((PersonalFeedbackPersonProfile) itemProfile, (PersonalFeedbackBasicSummary) feedbackResultSummary, false);
   }
}