
package com.feedbactory.server.core;


import com.feedbactory.server.feedback.FeedbackManager;
import com.feedbactory.server.network.application.NetworkServiceManager;
import com.feedbactory.server.useraccount.UserAccountManager;


final class HousekeepingManager
{
   final private UserAccountManager userAccountManager;
   final private NetworkServiceManager networkServiceManager;
   final private FeedbackManager feedbackManager;

   private boolean isActive;


   HousekeepingManager(final UserAccountManager userAccountManager, final NetworkServiceManager networkServiceManager, final FeedbackManager feedbackManager)
   {
      this.userAccountManager = userAccountManager;
      this.networkServiceManager = networkServiceManager;
      this.feedbackManager = feedbackManager;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleStart()
   {
      if (isActive)
         throw new IllegalStateException("Housekeeping is already active.");

      userAccountManager.startHousekeeping();
      networkServiceManager.startHousekeeping();
      feedbackManager.startHousekeeping();

      isActive = true;
   }


   private void handleShutdown() throws InterruptedException
   {
      if (isActive)
      {
         userAccountManager.shutdownHousekeeping();
         networkServiceManager.shutdownHousekeeping();
         feedbackManager.shutdownHousekeeping();

         isActive = false;
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final boolean isStarted()
   {
      return isActive;
   }


   final void start()
   {
      handleStart();
   }


   final void shutdown() throws InterruptedException
   {
      handleShutdown();
   }
}