
package com.feedbactory.client.core.feedback;


import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.client.core.network.NetworkServiceManager;
import com.feedbactory.client.core.network.ProcessedRequestResult;
import com.feedbactory.client.core.useraccount.AccountEventAdapter;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.client.core.useraccount.FeedbactoryUserAccount;
import com.feedbactory.shared.feedback.FeedbackItem;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import com.feedbactory.shared.feedback.FeedbackResultSummary;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


final public class FeedbackManager
{
   final private FeedbackNetworkGatewayManager networkGateway;
   final private FeedbackCategoryRegistry feedbackCategoryRegistry;

   final private Object cachedUserSubmissionsLock = new Object();
   private Map<FeedbackItem, ItemProfileFeedbackSummary> cachedUserSubmissions;


   public FeedbackManager(final AccountSessionManager userAccountManager, final NetworkServiceManager networkServiceManager)
   {
      networkGateway = new FeedbackNetworkGatewayManager(this, userAccountManager, networkServiceManager);
      feedbackCategoryRegistry = new FeedbackCategoryRegistry(this, networkGateway, userAccountManager);

      initialise(userAccountManager);
   }


   private void initialise(final AccountSessionManager userAccountManager)
   {
      userAccountManager.addUserAccountEventListener(new AccountEventAdapter()
      {
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


   private void handleSignedOutOfUserAccount()
   {
      synchronized (cachedUserSubmissionsLock)
      {
         cachedUserSubmissions = null;
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private ProcessedRequestResult<List<ItemProfileFeedbackSummary>> handleGetUserFeedbackSubmissions()
   {
      synchronized (cachedUserSubmissionsLock)
      {
         if (hasCachedUserSubmissions())
            return new ProcessedRequestResult<List<ItemProfileFeedbackSummary>>(NetworkRequestStatus.OK, new ArrayList<ItemProfileFeedbackSummary>(cachedUserSubmissions.values()));
         else
         {
            final ProcessedRequestResult<List<ItemProfileFeedbackSummary>> retrievedUserFeedbackSubmissions = networkGateway.sendGetUserFeedbackSubmissionsRequest();

            if (retrievedUserFeedbackSubmissions.requestStatus == NetworkRequestStatus.OK)
            {
               cacheRetrievedUserFeedbackSubmissions(retrievedUserFeedbackSubmissions.data);
               return new ProcessedRequestResult<List<ItemProfileFeedbackSummary>>(NetworkRequestStatus.OK, new ArrayList<ItemProfileFeedbackSummary>(cachedUserSubmissions.values()));
            }
            else
               return retrievedUserFeedbackSubmissions;
         }
      }
   }


   private void cacheRetrievedUserFeedbackSubmissions(final List<ItemProfileFeedbackSummary> retrievedUserFeedbackSubmissions)
   {
      cachedUserSubmissions = new HashMap<FeedbackItem, ItemProfileFeedbackSummary>(retrievedUserFeedbackSubmissions.size());

      FeedbackCategoryHandler feedbackCategoryHandler;
      FeedbackItemProfile itemProfile;

      for (final ItemProfileFeedbackSummary submission : retrievedUserFeedbackSubmissions)
      {
         feedbackCategoryHandler = feedbackCategoryRegistry.getFeedbackCategoryHandler(submission.itemProfile.getFeedbackCategory());

         /* This call performs two things:
          * - Updates the category handler's feedback summary for the item.
          * - Returns the category handler's most up to date snapshot of the item profile details, which may be different to that saved at the time of the submission.
          *
          * Also note the nested locking that will probably occur here.
          */
         itemProfile = feedbackCategoryHandler.updateItemFeedbackSummary(submission.itemProfile, submission.feedbackSummary);

         if (itemProfile.equals(submission.itemProfile))
            cachedUserSubmissions.put(itemProfile.getItem(), submission);
         else
            cachedUserSubmissions.put(itemProfile.getItem(), new ItemProfileFeedbackSummary(itemProfile, submission.feedbackSubmissionSummary, submission.feedbackSubmissionTime, submission.feedbackSummary));
      }
   }


   private void handleReportUpdatedFeedbackItemProfile(final FeedbackItemProfile updatedFeedbackItemProfile)
   {
      synchronized (cachedUserSubmissionsLock)
      {
         if (hasCachedUserSubmissions())
         {
            final ItemProfileFeedbackSummary existingFeedbackSummary = cachedUserSubmissions.get(updatedFeedbackItemProfile.getItem());

            if ((existingFeedbackSummary != null) && (! existingFeedbackSummary.itemProfile.equals(updatedFeedbackItemProfile)))
            {
               final ItemProfileFeedbackSummary replacementFeedbackSummary = new ItemProfileFeedbackSummary(updatedFeedbackItemProfile,
                                                                                                            existingFeedbackSummary.feedbackSubmissionSummary,
                                                                                                            existingFeedbackSummary.feedbackSubmissionTime,
                                                                                                            existingFeedbackSummary.feedbackSummary);
               cachedUserSubmissions.put(updatedFeedbackItemProfile.getItem(), replacementFeedbackSummary);
            }
         }
      }
   }


   private void handleReportUpdatedFeedbackSummary(final FeedbackItem feedbackItem, final FeedbackResultSummary feedbackResultSummary)
   {
      synchronized (cachedUserSubmissionsLock)
      {
         if (hasCachedUserSubmissions())
         {
            final ItemProfileFeedbackSummary existingFeedbackSummary = cachedUserSubmissions.get(feedbackItem);

            if ((existingFeedbackSummary != null) && (! existingFeedbackSummary.feedbackSummary.equals(feedbackResultSummary)))
            {
               final ItemProfileFeedbackSummary replacementFeedbackSummary = new ItemProfileFeedbackSummary(existingFeedbackSummary.itemProfile,
                                                                                                            existingFeedbackSummary.feedbackSubmissionSummary,
                                                                                                            existingFeedbackSummary.feedbackSubmissionTime,
                                                                                                            feedbackResultSummary);
               cachedUserSubmissions.put(feedbackItem, replacementFeedbackSummary);
            }
         }
      }
   }


   private void handleReportAddedFeedbackSubmission(final FeedbackItemProfile feedbackItemProfile, final Object feedbackSubmissionSummary,
                                                    final FeedbackResultSummary feedbackResultSummary)
   {
      synchronized (cachedUserSubmissionsLock)
      {
         if (hasCachedUserSubmissions())
         {
            final ItemProfileFeedbackSummary newFeedbackSummary = new ItemProfileFeedbackSummary(feedbackItemProfile, feedbackSubmissionSummary, System.currentTimeMillis(), feedbackResultSummary);
            cachedUserSubmissions.put(feedbackItemProfile.getItem(), newFeedbackSummary);
         }
      }
   }


   private void handleReportRemovedFeedbackSubmission(final FeedbackItem feedbackItem)
   {
      synchronized (cachedUserSubmissionsLock)
      {
         if (hasCachedUserSubmissions())
            cachedUserSubmissions.remove(feedbackItem);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public FeedbackCategoryRegistry getFeedbackCategoryRegistry()
   {
      return feedbackCategoryRegistry;
   }


   final public void processFeedbackHandshake(final ByteBuffer handshakeBuffer)
   {
      networkGateway.processFeedbackHandshake(handshakeBuffer);
   }


   final public boolean hasCachedUserSubmissions()
   {
      synchronized (cachedUserSubmissionsLock)
      {
         return (cachedUserSubmissions != null);
      }
   }


   final public ProcessedRequestResult<List<ItemProfileFeedbackSummary>> getUserFeedbackSubmissions()
   {
      return handleGetUserFeedbackSubmissions();
   }


   final public void reportUpdatedFeedbackItemProfile(final FeedbackItemProfile feedbackItemProfile)
   {
      handleReportUpdatedFeedbackItemProfile(feedbackItemProfile);
   }


   final public void reportUpdatedFeedbackSummary(final FeedbackItem feedbackItem, final FeedbackResultSummary feedbackResultSummary)
   {
      handleReportUpdatedFeedbackSummary(feedbackItem, feedbackResultSummary);
   }


   final public void reportAddedFeedbackSubmission(final FeedbackItemProfile feedbackItemProfile, final Object feedbackSubmissionSummary,
                                                   final FeedbackResultSummary feedbackResultSummary)
   {
      handleReportAddedFeedbackSubmission(feedbackItemProfile, feedbackSubmissionSummary, feedbackResultSummary);
   }


   final public void reportRemovedFeedbackSubmission(final FeedbackItem feedbackItem)
   {
      handleReportRemovedFeedbackSubmission(feedbackItem);
   }
}