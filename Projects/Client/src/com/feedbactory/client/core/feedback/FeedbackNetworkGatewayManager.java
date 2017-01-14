
package com.feedbactory.client.core.feedback;


import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.client.core.network.NetworkServiceManager;
import com.feedbactory.client.core.network.ProcessedRequestBufferResponse;
import com.feedbactory.client.core.network.ProcessedRequestResult;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import com.feedbactory.shared.feedback.FeedbackRequestType;
import com.feedbactory.shared.feedback.FeedbackResultSummary;
import com.feedbactory.shared.network.RequestGatewayIdentifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


final public class FeedbackNetworkGatewayManager
{
   final private FeedbackManager feedbackManager;
   final private AccountSessionManager userAccountManager;
   final private NetworkServiceManager networkServiceManager;


   FeedbackNetworkGatewayManager(final FeedbackManager feedbackManager, final AccountSessionManager userAccountManager, final NetworkServiceManager networkServiceManager)
   {
      this.feedbackManager = feedbackManager;
      this.userAccountManager = userAccountManager;
      this.networkServiceManager = networkServiceManager;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleProcessFeedbackHandshake(final ByteBuffer handshakeBuffer)
   {
      short feedbackCategoryValue;
      FeedbackCategory feedbackCategory;
      FeedbackNetworkGateway feedbackCategoryNetworkGateway;

      while ((feedbackCategoryValue = handshakeBuffer.getShort()) != FeedbactoryConstants.EndOfDataShort)
      {
         feedbackCategory = FeedbackCategory.fromValue(feedbackCategoryValue);
         if (feedbackCategory == null)
            throw new IllegalArgumentException("Invalid feedback category value: " + feedbackCategoryValue);

         feedbackCategoryNetworkGateway = feedbackManager.getFeedbackCategoryRegistry().getFeedbackCategoryHandler(feedbackCategory).getNetworkGateway();
         feedbackCategoryNetworkGateway.processFeedbackHandshake(handshakeBuffer);
      }
   }


   private ProcessedRequestResult<List<ItemProfileFeedbackSummary>> handleSendGetUserFeedbackSubmissionsRequest()
   {
      final ByteBuffer requestBuffer = ByteBuffer.allocate(2);
      requestBuffer.put(RequestGatewayIdentifier.Feedback.value);
      requestBuffer.put(FeedbackRequestType.GetUserFeedbackSubmissions.value);
      requestBuffer.flip();

      final ProcessedRequestBufferResponse response = userAccountManager.sendRegularSessionRequest(requestBuffer);

      if (response.requestStatus == NetworkRequestStatus.OK)
         return new ProcessedRequestResult<List<ItemProfileFeedbackSummary>>(response.requestStatus, readUserFeedbackSubmissions(response.data));
      else
         return ProcessedRequestResult.resultForNetworkRequestStatus(response.requestStatus);
   }


   private List<ItemProfileFeedbackSummary> readUserFeedbackSubmissions(final ByteBuffer responseBuffer)
   {
      final FeedbackCategoryRegistry feedbackCategoryRegistry = feedbackManager.getFeedbackCategoryRegistry();

      final int numberOfSubmissions = responseBuffer.getInt();
      final List<ItemProfileFeedbackSummary> accountFeedbackItems = new ArrayList<ItemProfileFeedbackSummary>(numberOfSubmissions);

      FeedbackCategory feedbackCategory;
      FeedbackNetworkGateway feedbackCategoryNetworkHandler;
      FeedbackItemProfile itemProfile;
      Object feedbackSubmissionSummary;
      long feedbackSubmissionTime;
      FeedbackResultSummary feedbackResultSummary;

      for (int feedbackItem = 0; feedbackItem < numberOfSubmissions; feedbackItem ++)
      {
         feedbackCategory = FeedbackCategory.fromValue(responseBuffer.getShort());
         feedbackCategoryNetworkHandler = feedbackCategoryRegistry.getFeedbackCategoryHandler(feedbackCategory).getNetworkGateway();

         /* Item profile may be null if the website is unrecognised by the current version of the client.
          * It's possible that the user has previously submitted feedback for an item attached to a newly supported Feedbactory website
          * via a newer client version. In this case, the item shouldn't be added to the list but the remainder of the record should still
          * be read so that the next record can be processed as per usual.
          */
         itemProfile = readItemProfile(feedbackCategoryNetworkHandler, responseBuffer);
         feedbackSubmissionSummary = feedbackCategoryNetworkHandler.readFeedbackSubmissionSummary(responseBuffer);
         feedbackSubmissionTime = responseBuffer.getLong() - networkServiceManager.getApproximateServerTimeDifference();
         feedbackResultSummary = feedbackCategoryNetworkHandler.readFeedbackResultSummary(responseBuffer);

         if (itemProfile != null)
            accountFeedbackItems.add(new ItemProfileFeedbackSummary(itemProfile, feedbackSubmissionSummary, feedbackSubmissionTime, feedbackResultSummary));
      }

      return accountFeedbackItems;
   }


   private FeedbackItemProfile readItemProfile(final FeedbackNetworkGateway feedbackCategoryNetworkHandler, final ByteBuffer responseBuffer)
   {
      /* If a client is a little outdated but otherwise fully functional (not superseded), there is every chance that during some requests
       * it may receive item profiles from the server for which it has no known website of origin.
       * Fetching all submissions for a user account is one possible place where this may happen, since a user may switch between machines
       * having different versions of Feedbactory. Fetching new or 'hot' feedback submissions from other users is another place where
       * items of unknown origin may occur.
       * These items should simply be skipped over. Handling unknown websites this way is a big win because it allows Feedbactory to not force users
       * into updating their client for each new website that's brought into the fold on the server side.
       */
      return feedbackCategoryNetworkHandler.readFeedbackItemProfile(responseBuffer);
   }


   private void writeStandardFeedbackRequestHeader(final FeedbackRequestType requestType, final FeedbackCategory feedbackCategory, final ByteBuffer requestBuffer)
   {
      requestBuffer.put(RequestGatewayIdentifier.Feedback.value);
      requestBuffer.put(requestType.value);
      requestBuffer.putShort(feedbackCategory.value);
   }


   private ProcessedRequestBufferResponse handleSendGetItemFeedbackSummaryRequest(final FeedbackCategory feedbackCategory, final ByteBuffer requestBuffer)
   {
      final ByteBuffer feedbackRequestBuffer = ByteBuffer.allocate(requestBuffer.remaining() + 4);

      writeStandardFeedbackRequestHeader(FeedbackRequestType.GetItemFeedbackSummary, feedbackCategory, feedbackRequestBuffer);
      feedbackRequestBuffer.put(requestBuffer);
      feedbackRequestBuffer.flip();

      return userAccountManager.sendCurrentSessionStateRequest(feedbackRequestBuffer);
   }


   private ProcessedRequestBufferResponse handleSendGetItemFeedbackSubmissionRequest(final FeedbackCategory feedbackCategory, final ByteBuffer requestBuffer)
   {
      final ByteBuffer feedbackRequestBuffer = ByteBuffer.allocate(requestBuffer.remaining() + 4);

      writeStandardFeedbackRequestHeader(FeedbackRequestType.GetItemFeedbackSubmission, feedbackCategory, feedbackRequestBuffer);
      feedbackRequestBuffer.put(requestBuffer);
      feedbackRequestBuffer.flip();

      return userAccountManager.sendRegularSessionRequest(feedbackRequestBuffer);
   }


   private ProcessedRequestBufferResponse handleSendAddItemFeedbackSubmissionRequest(final FeedbackCategory feedbackCategory, final ByteBuffer requestBuffer)
   {
      final ByteBuffer feedbackRequestBuffer = ByteBuffer.allocate(requestBuffer.remaining() + 4);

      writeStandardFeedbackRequestHeader(FeedbackRequestType.AddItemFeedbackSubmission, feedbackCategory, feedbackRequestBuffer);
      feedbackRequestBuffer.put(requestBuffer);
      feedbackRequestBuffer.flip();

      return userAccountManager.sendEncryptedSessionRequest(feedbackRequestBuffer);
   }


   private ProcessedRequestBufferResponse handleSendRemoveItemFeedbackSubmissionRequest(final FeedbackCategory feedbackCategory, final ByteBuffer requestBuffer)
   {
      final ByteBuffer feedbackRequestBuffer = ByteBuffer.allocate(requestBuffer.remaining() + 4);

      writeStandardFeedbackRequestHeader(FeedbackRequestType.RemoveItemFeedbackSubmission, feedbackCategory, feedbackRequestBuffer);
      feedbackRequestBuffer.put(requestBuffer);
      feedbackRequestBuffer.flip();

      return userAccountManager.sendEncryptedSessionRequest(feedbackRequestBuffer);
   }


   private ProcessedRequestBufferResponse handleSendFeedbackCategoryRequest(final FeedbackCategory feedbackCategory, final ByteBuffer requestBuffer, final boolean encrypt)
   {
      final ByteBuffer feedbackRequestBuffer = ByteBuffer.allocate(requestBuffer.remaining() + 4);

      writeStandardFeedbackRequestHeader(FeedbackRequestType.FeedbackCategoryRequest, feedbackCategory, feedbackRequestBuffer);
      feedbackRequestBuffer.put(requestBuffer);
      feedbackRequestBuffer.flip();

      if (encrypt)
         return userAccountManager.sendEncryptedSessionRequest(feedbackRequestBuffer);
      else
         return userAccountManager.sendCurrentSessionStateRequest(feedbackRequestBuffer);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final ProcessedRequestResult<List<ItemProfileFeedbackSummary>> sendGetUserFeedbackSubmissionsRequest()
   {
      return handleSendGetUserFeedbackSubmissionsRequest();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public long getApproximateServerTimeDifference()
   {
      return networkServiceManager.getApproximateServerTimeDifference();
   }


   final public void processFeedbackHandshake(final ByteBuffer handshakeBuffer)
   {
      handleProcessFeedbackHandshake(handshakeBuffer);
   }


   final public ProcessedRequestBufferResponse sendGetItemFeedbackSummaryRequest(final FeedbackCategory feedbackCategory, final ByteBuffer requestBuffer)
   {
      return handleSendGetItemFeedbackSummaryRequest(feedbackCategory, requestBuffer);
   }


   final public ProcessedRequestBufferResponse sendGetItemFeedbackSubmissionRequest(final FeedbackCategory feedbackCategory, final ByteBuffer requestBuffer)
   {
      return handleSendGetItemFeedbackSubmissionRequest(feedbackCategory, requestBuffer);
   }


   final public ProcessedRequestBufferResponse sendAddItemFeedbackSubmissionRequest(final FeedbackCategory feedbackCategory, final ByteBuffer requestBuffer)
   {
      return handleSendAddItemFeedbackSubmissionRequest(feedbackCategory, requestBuffer);
   }


   final public ProcessedRequestBufferResponse sendRemoveItemFeedbackSubmissionRequest(final FeedbackCategory feedbackCategory, final ByteBuffer requestBuffer)
   {
      return handleSendRemoveItemFeedbackSubmissionRequest(feedbackCategory, requestBuffer);
   }


   final public ProcessedRequestBufferResponse sendFeedbackCategoryRequest(final FeedbackCategory feedbackCategory, final ByteBuffer requestBuffer, final boolean encrypt)
   {
      return handleSendFeedbackCategoryRequest(feedbackCategory, requestBuffer, encrypt);
   }
}