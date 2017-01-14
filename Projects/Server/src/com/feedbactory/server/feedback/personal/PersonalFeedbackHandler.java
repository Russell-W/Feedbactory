
package com.feedbactory.server.feedback.personal;


import com.feedbactory.server.feedback.FeedbackManager;
import com.feedbactory.server.feedback.FeedbackCategoryHandler;
import com.feedbactory.server.feedback.FeedbackCategoryManager;
import com.feedbactory.server.feedback.FeedbackCategoryNetworkGateway;
import com.feedbactory.server.useraccount.UserAccountManager;
import com.feedbactory.shared.feedback.FeedbackCategory;
import java.util.Arrays;


final public class PersonalFeedbackHandler extends FeedbackCategoryHandler
{
   final private FeedbackManager feedbackManager;

   final private PersonalFeedbackManager personalFeedbackManager;
   final private PersonalFeedbackNetworkGateway personalFeedbackNetworkGateway;
   final private UserAccountManager userAccountManager;


   public PersonalFeedbackHandler(final FeedbackManager feedbackManager, final UserAccountManager userAccountManager)
   {
      this.feedbackManager = feedbackManager;
      personalFeedbackManager = new PersonalFeedbackManager(feedbackManager, userAccountManager);
      personalFeedbackNetworkGateway = new PersonalFeedbackNetworkGateway(personalFeedbackManager);
      this.userAccountManager = userAccountManager;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private String handleProcessConsoleCommand(final String[] command)
   {
      if ((command.length > 0) && command[0].equals("test"))
         return PhotographyFeedbackTest.processConsoleCommand(feedbackManager, personalFeedbackManager, userAccountManager, Arrays.copyOfRange(command, 1, command.length));
      else
         return personalFeedbackNetworkGateway.processConsoleCommand(command);
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
   final public FeedbackCategoryManager getCategoryManager()
   {
      return personalFeedbackManager;
   }


   @Override
   final public FeedbackCategoryNetworkGateway getCategoryNetworkGateway()
   {
      return personalFeedbackNetworkGateway;
   }


   @Override
   final public String processConsoleCommand(final String[] command)
   {
      return handleProcessConsoleCommand(command);
   }
}