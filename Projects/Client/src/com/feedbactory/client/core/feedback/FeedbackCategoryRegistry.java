
package com.feedbactory.client.core.feedback;


import com.feedbactory.client.core.feedback.personal.PersonalFeedbackManager;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.shared.feedback.FeedbackCategory;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;


final public class FeedbackCategoryRegistry
{
   final private Map<FeedbackCategory, FeedbackCategoryHandler> registeredFeedbackCategoryHandlers;


   FeedbackCategoryRegistry(final FeedbackManager feedbackManager, final FeedbackNetworkGatewayManager feedbackNetworkManager,
                            final AccountSessionManager userAccountManager)
   {
      registeredFeedbackCategoryHandlers = initialiseRegistry(feedbackManager, feedbackNetworkManager, userAccountManager);
   }


   private Map<FeedbackCategory, FeedbackCategoryHandler> initialiseRegistry(final FeedbackManager feedbackManager,
                                                                             final FeedbackNetworkGatewayManager feedbackNetworkManager,
                                                                             final AccountSessionManager userAccountManager)
   {
      final Map<FeedbackCategory, FeedbackCategoryHandler> registryBuilder = new EnumMap<FeedbackCategory, FeedbackCategoryHandler>(FeedbackCategory.class);

      registryBuilder.put(FeedbackCategory.Personal, new PersonalFeedbackManager(feedbackManager, feedbackNetworkManager, userAccountManager));

      return Collections.unmodifiableMap(registryBuilder);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public FeedbackCategoryHandler getFeedbackCategoryHandler(final FeedbackCategory feedbackCategory)
   {
      return registeredFeedbackCategoryHandlers.get(feedbackCategory);
   }


   final public Collection<FeedbackCategoryHandler> getRegisteredFeedbackCategoryHandlers()
   {
      return registeredFeedbackCategoryHandlers.values();
   }
}