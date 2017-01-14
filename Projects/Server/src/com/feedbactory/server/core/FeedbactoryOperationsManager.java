/* Memos:
 * - The package-private calls are not threadsafe, the burden of thread safety is on the caller.
 */

package com.feedbactory.server.core;


import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.log.LoggerManager;
import com.feedbactory.server.core.log.SystemLogLevel;
import com.feedbactory.server.feedback.FeedbackManager;
import com.feedbactory.server.network.application.ApplicationRequestManager;
import com.feedbactory.server.network.application.ApplicationServerController;
import com.feedbactory.server.network.application.NetworkServiceManager;
import com.feedbactory.server.network.application.NetworkToApplicationGateway;
import com.feedbactory.server.network.component.IPAddressRequestMonitor;
import com.feedbactory.server.useraccount.UserAccountManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;


final public class FeedbactoryOperationsManager
{
   final private LoggerManager loggerManager = new LoggerManager(this);

   final private UserAccountManager userAccountManager= new UserAccountManager();
   final private FeedbackManager feedbackManager = new FeedbackManager(userAccountManager);

   final private NetworkServiceManager networkServiceManager;

   final private CheckpointManager checkpointManager;
   final private HousekeepingManager housekeepingManager;


   static
   {
      /* Set the locale to ensure that operations such as lowercasing Strings will produce identical results across different environments,
       * especially if saved data from one environment is loaded on a different environment.
       * I hope to have the client handle any locale, but even on the client there are occasions when the locale should be forced
       * to English for various method calls, specifically when transmitting string data to the server that must be identical no matter
       * where it's been sent from, eg. browsed web page data. Refer to the feedback manager, re: item fragmentation.
       */
      Locale.setDefault(Locale.ENGLISH);
   }


   FeedbactoryOperationsManager() throws IOException
   {
      networkServiceManager = new NetworkServiceManager(this, userAccountManager, feedbackManager);
      checkpointManager = new CheckpointManager(userAccountManager, feedbackManager, networkServiceManager);
      housekeepingManager = new HousekeepingManager(userAccountManager, networkServiceManager, feedbackManager);

      initialise();
   }


   private void initialise()
   {
      Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler());
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler
   {
      @Override
      final public void uncaughtException(final Thread thread, final Throwable throwable)
      {
         FeedbactoryLogger.logSystemEvent(SystemLogLevel.ApplicationError, getClass(), "Uncaught exception", throwable);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void handleLoadPrimaryCheckpoint() throws IOException
   {
      validateStateForCheckpointLoading();
      checkpointManager.loadPrimaryCheckpoint();
   }


   private void validateStateForCheckpointLoading()
   {
      if (networkServiceManager.getServerController().isServerStarted())
         throw new IllegalStateException("Checkpoint restoration cannot be performed while the server is active.");
      else if (housekeepingManager.isStarted())
         throw new IllegalStateException("Checkpoint restoration cannot be performed while housekeeping is active.");
   }


   private void handleRestoreFromAutoSaveCheckpoint(final int checkpointNumber) throws IOException
   {
      validateStateForCheckpointLoading();
      checkpointManager.loadAutoSaveCheckpoint(checkpointNumber);
   }


   private void handleRestoreFromSnapshotCheckpoint(final String checkpointName) throws IOException
   {
      validateStateForCheckpointLoading();
      checkpointManager.loadSnapshotCheckpoint(checkpointName);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final LoggerManager getLoggerManager()
   {
      return loggerManager;
   }


   final Path getActiveCheckpointPath()
   {
      return checkpointManager.getActiveCheckpointPath();
   }


   final boolean isAttachedToPrimaryCheckpoint()
   {
      return checkpointManager.isAttachedToPrimaryCheckpoint();
   }


   final void loadPrimaryCheckpoint() throws IOException
   {
      handleLoadPrimaryCheckpoint();
   }


   final void savePrimaryCheckpoint() throws IOException
   {
      // Will throw exception if not currently attached to the primary data checkpoint.
      checkpointManager.savePrimaryCheckpoint();
   }


   final boolean isAutoSaveStarted()
   {
      return checkpointManager.isAutoSaveStarted();
   }


   final void startAutoSave()
   {
      checkpointManager.startAutoSave();
   }


   final void shutdownAutoSave() throws InterruptedException
   {
      checkpointManager.shutdownAutoSave();
   }


   final void loadAutoSaveCheckpoint(final int checkpointNumber) throws IOException
   {
      handleRestoreFromAutoSaveCheckpoint(checkpointNumber);
   }


   final CheckpointManager.AutoSaveMetrics getAutoSaveMetrics()
   {
      return checkpointManager.getAutoSaveMetrics();
   }


   final void loadSnapshotCheckpoint(final String checkpointName) throws IOException
   {
      handleRestoreFromSnapshotCheckpoint(checkpointName);
   }


   final void saveSnapshotCheckpoint(final String checkpointName) throws IOException
   {
      checkpointManager.saveSnapshotCheckpoint(checkpointName);
   }


   final HousekeepingManager getHousekeepingManager()
   {
      return housekeepingManager;
   }


   final ApplicationServerController getServerController()
   {
      return networkServiceManager.getServerController();
   }


   final IPAddressRequestMonitor getRequestMonitor()
   {
      return networkServiceManager.getRequestMonitor();
   }


   final ApplicationRequestManager getRequestManager()
   {
      return networkServiceManager.getRequestManager();
   }


   final NetworkToApplicationGateway getNetworkToApplicationGateway()
   {
      return networkServiceManager.getNetworkToApplicationGateway();
   }


   final UserAccountManager getUserAccountManager()
   {
      return userAccountManager;
   }


   final FeedbackManager getFeedbackManager()
   {
      return feedbackManager;
   }
}