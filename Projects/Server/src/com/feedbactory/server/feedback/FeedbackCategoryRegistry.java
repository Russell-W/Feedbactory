
package com.feedbactory.server.feedback;


import com.feedbactory.server.feedback.personal.PersonalFeedbackHandler;
import com.feedbactory.server.useraccount.UserAccountManager;
import com.feedbactory.shared.feedback.FeedbackCategory;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;


final class FeedbackCategoryRegistry
{
   final private Map<FeedbackCategory, FeedbackCategoryHandler> registeredFeedbackCategoryHandlers;


   FeedbackCategoryRegistry(final FeedbackManager feedbackManager, final UserAccountManager userAccountManager)
   {
      registeredFeedbackCategoryHandlers = initialiseRegistry(feedbackManager, userAccountManager);
   }


   private Map<FeedbackCategory, FeedbackCategoryHandler> initialiseRegistry(final FeedbackManager feedbackManager, final UserAccountManager userAccountManager)
   {
      final Map<FeedbackCategory, FeedbackCategoryHandler> registryBuilder = new EnumMap<>(FeedbackCategory.class);

      registryBuilder.put(FeedbackCategory.Personal, new PersonalFeedbackHandler(feedbackManager, userAccountManager));

      return Collections.unmodifiableMap(registryBuilder);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public FeedbackCategoryHandler getFeedbackCategoryHandler(final FeedbackCategory feedbackCategory)
   {
      return registeredFeedbackCategoryHandlers.get(feedbackCategory);
   }


   final public Collection<FeedbackCategoryHandler> getRegisteredHandlers()
   {
      return registeredFeedbackCategoryHandlers.values();
   }
}