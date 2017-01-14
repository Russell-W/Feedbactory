
package com.feedbactory.client.ui.feedback;


import com.feedbactory.client.core.feedback.FeedbackCategoryRegistry;
import com.feedbactory.client.core.feedback.personal.PersonalFeedbackManager;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.client.ui.feedback.FeedbackUIManager.BrowserEventManagerClientView;
import com.feedbactory.client.ui.feedback.personal.PersonalFeedbackUIManager;
import com.feedbactory.client.ui.pad.FeedbactoryPadUIView;
import com.feedbactory.client.ui.settings.SettingsUIManager;
import com.feedbactory.shared.feedback.FeedbackCategory;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;


final public class FeedbackCategoryUIRegistry
{
   final private Map<FeedbackCategory, FeedbackCategoryUIManager> registeredFeedbackCategoryUIHandlers;


   public FeedbackCategoryUIRegistry(final FeedbackCategoryRegistry feedbackCategoryRegistry, final AccountSessionManager userAccountManager,
                                     final FeedbackUIManager feedbackUIManager, final BrowserEventManagerClientView browserEventManagerClientView,
                                     final SettingsUIManager settingsUIManager, final FeedbactoryPadUIView feedbactoryPad)
   {
      registeredFeedbackCategoryUIHandlers = initialiseRegistry(feedbackCategoryRegistry, userAccountManager, feedbackUIManager, browserEventManagerClientView,
                                                                settingsUIManager, feedbactoryPad);
   }


   private Map<FeedbackCategory, FeedbackCategoryUIManager> initialiseRegistry(final FeedbackCategoryRegistry feedbackCategoryRegistry,
                                                                               final AccountSessionManager userAccountManager,
                                                                               final FeedbackUIManager feedbackUIManager,
                                                                               final BrowserEventManagerClientView browserEventManagerClientView,
                                                                               final SettingsUIManager settingsUIManager,
                                                                               final FeedbactoryPadUIView feedbactoryPad)
   {
      final Map<FeedbackCategory, FeedbackCategoryUIManager> registryBuilder = new EnumMap<FeedbackCategory, FeedbackCategoryUIManager>(FeedbackCategory.class);

      final PersonalFeedbackManager personalFeedbackCategoryManager = (PersonalFeedbackManager) feedbackCategoryRegistry.getFeedbackCategoryHandler(FeedbackCategory.Personal);
      registryBuilder.put(FeedbackCategory.Personal, new PersonalFeedbackUIManager(personalFeedbackCategoryManager, userAccountManager, feedbackUIManager,
                                                                                   browserEventManagerClientView, settingsUIManager, feedbactoryPad));

      return Collections.unmodifiableMap(registryBuilder);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public FeedbackCategoryUIManager getFeedbackCategoryUIHandler(final FeedbackCategory feedbackCategory)
   {
      return registeredFeedbackCategoryUIHandlers.get(feedbackCategory);
   }


   final public Collection<FeedbackCategoryUIManager> getRegisteredUIHandlers()
   {
      return registeredFeedbackCategoryUIHandlers.values();
   }
}