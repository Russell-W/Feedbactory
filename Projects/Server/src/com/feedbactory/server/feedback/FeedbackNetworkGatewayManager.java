
package com.feedbactory.server.feedback;


import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.log.SecurityLogLevel;
import com.feedbactory.server.network.application.ProcessedOperationStatus;
import com.feedbactory.server.network.application.RequestUserSession;
import com.feedbactory.server.network.component.buffer.WritableByteBuffer;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.feedback.FeedbackRequestType;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;


final public class FeedbackNetworkGatewayManager
{
   final private FeedbackManager feedbackManager;

   final private Map<FeedbackCategory, FeedbackCategoryNetworkGateway> categoryGateways = new EnumMap<>(FeedbackCategory.class);


   public FeedbackNetworkGatewayManager(final FeedbackManager feedbackManager)
   {
      this.feedbackManager = feedbackManager;

      initialise();
   }


   private void initialise()
   {
      for (final FeedbackCategoryHandler categoryHandler : feedbackManager.getFeedbackCategoryRegistry().getRegisteredHandlers())
         categoryGateways.put(categoryHandler.getCategory(), categoryHandler.getCategoryNetworkGateway());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleProcessHandshakeRequest(final WritableByteBuffer responseBuffer)
   {
      for (final Entry<FeedbackCategory, FeedbackCategoryNetworkGateway> networkGatewayEntry : categoryGateways.entrySet())
      {
         responseBuffer.putShort(networkGatewayEntry.getKey().value);
         networkGatewayEntry.getValue().writeHandshakeResponse(responseBuffer);
      }

      responseBuffer.putShort(FeedbactoryConstants.EndOfDataShort);
   }


   private ProcessedOperationStatus handleProcessFeedbackRequest(final RequestUserSession userSession)
   {
      switch (userSession.sessionRequestType)
      {
         case None:
            return processNoSessionFeedbackRequest(userSession);

         case RegularSessionRequest:
            return processRegularSessionFeedbackRequest(userSession);

         case EncryptedSessionRequest:
            return processEncryptedSessionFeedbackRequest(userSession);

         default:
            throw new AssertionError("Unhandled session request type for feedback request: " + userSession.sessionRequestType);
      }
   }


   private ProcessedOperationStatus processNoSessionFeedbackRequest(final RequestUserSession userSession)
   {
      final byte requestTypeValue = userSession.requestBuffer.get();
      final FeedbackRequestType requestType = FeedbackRequestType.fromValue(requestTypeValue);
      if (requestType == null)
      {
         final String message = "Invalid session-less feedback request type value: " + requestTypeValue;
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

         return ProcessedOperationStatus.ErroneousRequest;
      }

      switch (requestType)
      {
         case GetItemFeedbackSummary:
            return processGetItemFeedbackSummary(userSession);

         case FeedbackCategoryRequest:
            return processFeedbackCategoryRequest(userSession);

         default:

            final String message = "Invalid session-less feedback request type: " + requestType.toString();
            FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

            return ProcessedOperationStatus.ErroneousRequest;
      }
   }


   private ProcessedOperationStatus processGetItemFeedbackSummary(final RequestUserSession userSession)
   {
      final short feedbackCategoryValue = userSession.requestBuffer.getShort();
      final FeedbackCategory feedbackCategory = FeedbackCategory.fromValue(feedbackCategoryValue);

      if (feedbackCategory != null)
      {
         final FeedbackCategoryNetworkGateway categoryGateway = categoryGateways.get(feedbackCategory);
         return categoryGateway.processGetItemFeedbackSummaryRequest(userSession);
      }
      else
      {
         final String message = "Invalid feedback category value for get item feedback summary: " + feedbackCategoryValue;
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

         return ProcessedOperationStatus.ErroneousRequest;
      }
   }


   private ProcessedOperationStatus processFeedbackCategoryRequest(final RequestUserSession userSession)
   {
      final short feedbackCategoryValue = userSession.requestBuffer.getShort();
      final FeedbackCategory feedbackCategory = FeedbackCategory.fromValue(feedbackCategoryValue);

      if (feedbackCategory != null)
      {
         final FeedbackCategoryNetworkGateway categoryGateway = categoryGateways.get(feedbackCategory);
         return categoryGateway.processFeedbackCategoryRequest(userSession);
      }
      else
      {
         final String message = "Invalid feedback category value for feedback category request: " + feedbackCategoryValue;
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

         return ProcessedOperationStatus.ErroneousRequest;
      }
   }


   private ProcessedOperationStatus processRegularSessionFeedbackRequest(final RequestUserSession userSession)
   {
      final byte requestTypeValue = userSession.requestBuffer.get();
      final FeedbackRequestType requestType = FeedbackRequestType.fromValue(requestTypeValue);
      if (requestType == null)
      {
         final String message = "Invalid regular session feedback request type value: " + requestTypeValue;
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

         return ProcessedOperationStatus.ErroneousRequest;
      }

      switch (requestType)
      {
         case GetItemFeedbackSummary:
            return processGetItemFeedbackSummary(userSession);

         case GetItemFeedbackSubmission:
            return processGetItemFeedbackSubmission(userSession);

         case GetUserFeedbackSubmissions:
            return processGetUserFeedbackSubmissionsRequest(userSession);

         case FeedbackCategoryRequest:
            return processFeedbackCategoryRequest(userSession);

         default:

            final String message = "Invalid regular session feedback request type: " + requestType.toString();
            FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

            return ProcessedOperationStatus.ErroneousRequest;
      }
   }


   private ProcessedOperationStatus processGetItemFeedbackSubmission(final RequestUserSession userSession)
   {
      final short feedbackCategoryValue = userSession.requestBuffer.getShort();
      final FeedbackCategory feedbackCategory = FeedbackCategory.fromValue(feedbackCategoryValue);
      
      if (feedbackCategory != null)
      {
         final FeedbackCategoryNetworkGateway categoryGateway = categoryGateways.get(feedbackCategory);
         return categoryGateway.processGetItemFeedbackSubmissionRequest(userSession);
      }
      else
      {
         final String message = "Invalid feedback category value for get item feedback submission: " + feedbackCategoryValue;
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

         return ProcessedOperationStatus.ErroneousRequest;
      }
   }


   private ProcessedOperationStatus processGetUserFeedbackSubmissionsRequest(final RequestUserSession userSession)
   {
      synchronized (userSession.account)
      {
         final Collection<ItemProfileFeedbackSubmission> userAccountFeedback = feedbackManager.getAllUserFeedbackSubmissions(userSession.account);

         /* Writing a list of feedback items will generally take up a lot more space than a regular request, for which there is a default
          * small amount of bytes allocated. The GrowableByteBuffer expands its size very conservatively when this small capacity is exceeded,
          * resulting in the likelihood of several expansion operations if the overall write size is reasonably large. Each of these operations has
          * the overhead of slicing a new (and possibly still not large enough) buffer from the overflow store, transferring the old buffer contents to this,
          * and then trying to reclaim the old buffer. Therefore applying an upfront guesstimate of the (likely) larger capacity needed up front will
          * almost certainly prevent this repeated overhead.
          */
         userSession.responseBuffer.ensureRemainingCapacity(userAccountFeedback.size() * 150);

         userSession.responseBuffer.putInteger(userAccountFeedback.size());

         for (final ItemProfileFeedbackSubmission userAccountSubmission : userAccountFeedback)
         {
            final FeedbackCategory feedbackCategory = userAccountSubmission.getItemProfile().getFeedbackCategory();
            final FeedbackCategoryNetworkGateway categoryGateway = categoryGateways.get(feedbackCategory);

            userSession.responseBuffer.putShort(feedbackCategory.value);
            categoryGateway.writeItemProfile(userAccountSubmission.getItemProfile(), userSession.responseBuffer);
            categoryGateway.writeItemFeedbackSubmissionSummary(userAccountSubmission.getFeedbackSubmission(), userSession.responseBuffer);
            userSession.responseBuffer.putLong(userAccountSubmission.getSubmissionTime());

            // There will be a lock on the item's feedback here, to write the summary of all feedback attached to it.
            categoryGateway.writeItemFeedbackSummary(userAccountSubmission.getItemProfile().getItem(), userSession.responseBuffer);
         }

         return ProcessedOperationStatus.OK;
      }
   }


   private ProcessedOperationStatus processEncryptedSessionFeedbackRequest(final RequestUserSession userSession)
   {
      final byte requestTypeValue = userSession.requestBuffer.get();
      final FeedbackRequestType requestType = FeedbackRequestType.fromValue(requestTypeValue);
      if (requestType == null)
      {
         final String message = "Invalid encrypted session feedback request type value: " + requestTypeValue;
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

         return ProcessedOperationStatus.ErroneousRequest;
      }

      switch (requestType)
      {
         case AddItemFeedbackSubmission:
            return processAddItemFeedbackSubmission(userSession);

         case RemoveItemFeedbackSubmission:
            return processRemoveItemFeedbackSubmission(userSession);

         case FeedbackCategoryRequest:
            return processFeedbackCategoryRequest(userSession);

         default:

            final String message = "Invalid encrypted session feedback request type: " + requestType.toString();
            FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

            return ProcessedOperationStatus.ErroneousRequest;
      }
   }


   private ProcessedOperationStatus processAddItemFeedbackSubmission(final RequestUserSession userSession)
   {
      final short feedbackCategoryValue = userSession.requestBuffer.getShort();
      final FeedbackCategory feedbackCategory = FeedbackCategory.fromValue(feedbackCategoryValue);

      if (feedbackCategory != null)
      {
         final FeedbackCategoryNetworkGateway categoryGateway = categoryGateways.get(feedbackCategory);
         return categoryGateway.processAddItemFeedbackSubmissionRequest(userSession);
      }
      else
      {
         final String message = "Invalid feedback category value for add item feedback submission: " + feedbackCategoryValue;
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

         return ProcessedOperationStatus.ErroneousRequest;
      }
   }


   private ProcessedOperationStatus processRemoveItemFeedbackSubmission(final RequestUserSession userSession)
   {
      final short feedbackCategoryValue = userSession.requestBuffer.getShort();
      final FeedbackCategory feedbackCategory = FeedbackCategory.fromValue(feedbackCategoryValue);

      if (feedbackCategory != null)
      {
         final FeedbackCategoryNetworkGateway categoryGateway = categoryGateways.get(feedbackCategory);
         return categoryGateway.processRemoveItemFeedbackSubmissionRequest(userSession);
      }
      else
      {
         final String message = "Invalid feedback category value for remove item feedback submission: " + feedbackCategoryValue;
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

         return ProcessedOperationStatus.ErroneousRequest;
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void processHandshakeRequest(final WritableByteBuffer responseBuffer)
   {
      handleProcessHandshakeRequest(responseBuffer);
   }


   final public ProcessedOperationStatus processFeedbackRequest(final RequestUserSession userSession)
   {
      return handleProcessFeedbackRequest(userSession);
   }
}