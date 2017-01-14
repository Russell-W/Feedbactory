/* Memos:
 * - Generally the package-private accessor methods are not threadsafe, so the caller must carefully coordinate the thread access.
 *
 * - However manual and autosave checkpoint saving may safely occur at the same time.
 *
 * - The class provides protection against the caller accidentally overwriting the primary data checkpoint with non-primary data. This extends to the
 *   autosaving, which always must operate off the primary data.
 *
 * - There's no reason to be stingey with the number of autosave checkpoints (before recycling). Ideally there are enough data checkpoints so that if something is corrupted,
 *   the problem will become apparent early enough that there will still be uncorrupted checkpoints to roll back to, or at least to rip data from.
 *
 * - The AutoSaveCheckpointPeriodMinutes value should be large enough that it's safe to assume that autosaves could never overlap, even if the ScheduledExecutor
 *   would allow it (it doesn't).
 */

package com.feedbactory.server.core;


import com.feedbactory.server.FeedbactoryServer;
import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.log.SystemLogLevel;
import com.feedbactory.server.feedback.FeedbackManager;
import com.feedbactory.server.network.application.NetworkServiceManager;
import com.feedbactory.server.useraccount.UserAccountManager;
import com.feedbactory.shared.FeedbactoryConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;


final class CheckpointManager
{
   static final private Path PrimaryCheckpointPath = FeedbactoryServerConstants.BaseDataPath.resolve("PrimaryCheckpoint");
   static final private Path AutoSaveCheckpointsBasePath = FeedbactoryServerConstants.BaseDataPath.resolve("AutoSaveCheckpoints");
   static final private Path SnapshotCheckpointsBasePath = FeedbactoryServerConstants.BaseDataPath.resolve("SnapshotCheckpoints");

   static final private int AutoSaveCheckpointFrequencyMinutes;

   static final private int MaximumAutoSavesRetained;

   final private UserAccountManager userAccountManager;
   final private FeedbackManager feedbackManager;
   final private NetworkServiceManager networkServiceManager;

   final private AutoSaveCheckpointingTask autoSaveCheckpointingTask = new AutoSaveCheckpointingTask();

   private Path activeCheckpointPath;


   static
   {
      if (FeedbactoryServer.getExecutionProfile() == FeedbactoryServer.ExecutionProfile.Development)
      {
         AutoSaveCheckpointFrequencyMinutes = 10;
         MaximumAutoSavesRetained = 6;
      }
      else if (FeedbactoryServer.getExecutionProfile() == FeedbactoryServer.ExecutionProfile.Test)
      {
         AutoSaveCheckpointFrequencyMinutes = 480;
         MaximumAutoSavesRetained = 6;
      }
      else if (FeedbactoryServer.getExecutionProfile() == FeedbactoryServer.ExecutionProfile.Production)
      {
         // 12 x autosaves, every 3 hours, 36 hours coverage.
         AutoSaveCheckpointFrequencyMinutes = 180;
         MaximumAutoSavesRetained = 12;
      }
      else
         throw new AssertionError("Unknown or misconfigured execution profile.");
   }


   CheckpointManager(final UserAccountManager userAccountManager, final FeedbackManager feedbackManager, final NetworkServiceManager networkServiceManager)
   {
      this.userAccountManager = userAccountManager;
      this.feedbackManager = feedbackManager;
      this.networkServiceManager = networkServiceManager;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final class AutoSaveMetrics
   {
      final boolean isAutoSaveActive;
      final int autoSaveFrequencyMinutes;
      final int autoSavesThisSession;
      final int lastAutoSaveNumber;
      final long lastAutoSaveStartTime;
      final long lastAutoSaveFinishTime;


      private AutoSaveMetrics(final boolean isAutoSaveActive, final int autoSavesThisSession,
                              final int lastAutoSaveNumber, final long lastAutoSaveStartTime, final long lastAutoSaveFinishTime)
      {
         this.isAutoSaveActive = isAutoSaveActive;
         this.autoSaveFrequencyMinutes = AutoSaveCheckpointFrequencyMinutes;
         this.autoSavesThisSession = autoSavesThisSession;
         this.lastAutoSaveNumber = lastAutoSaveNumber;
         this.lastAutoSaveStartTime = lastAutoSaveStartTime;
         this.lastAutoSaveFinishTime = lastAutoSaveFinishTime;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class AutoSaveCheckpointingTask implements Runnable
   {
      static final private String LastAutoSaveNumberPreferencesKey = "LastAutoSaveNumber";

      /* Calls to isStarted(), start() and shutdown() aren't threadsafe and must be coordinated by the caller
       * to ensure the visibility and consistent state of the executor.
       */
      private ScheduledThreadPoolExecutor executor;

      /* A lock is always used to access these metric variables to ensure their consistency as they are
       * written by the task thread and read by the caller as one atomic unit.
       */
      private int autoSavesThisSession;
      private int lastAutoSaveNumber;
      private long lastAutoSaveStartTime = FeedbactoryConstants.NoTime;
      private long lastAutoSaveFinishTime = FeedbactoryConstants.NoTime;


      private AutoSaveCheckpointingTask()
      {
         initialise();
      }


      private void initialise()
      {
         restoreLastAutoSaveNumber();
      }


      /****************************************************************************
       *
       ***************************************************************************/


      @Override
      final public void run()
      {
         Thread.currentThread().setName("Auto save checkpoint task");

         int nextAutoSaveNumber = 0;

         try
         {
            final Path nextCheckpointPath;

            synchronized (this)
            {
               if (lastAutoSaveNumber < MaximumAutoSavesRetained)
                  nextAutoSaveNumber = lastAutoSaveNumber + 1;
               else
                  nextAutoSaveNumber = 1;

               nextCheckpointPath = AutoSaveCheckpointsBasePath.resolve(Integer.toString(nextAutoSaveNumber));

               // Use the finer grained System.currentTimeMillis rather than the TimeCache for this.
               lastAutoSaveStartTime = System.currentTimeMillis();
            }

            createPathAndSaveCheckpoint(nextCheckpointPath);

            synchronized (this)
            {
               autoSavesThisSession ++;
               lastAutoSaveNumber = nextAutoSaveNumber;
               lastAutoSaveFinishTime = System.currentTimeMillis();
            }
         }
         catch (final Exception anyException)
         {
            /* Exception handling provided for -any- exception (not just IOException), since any exceptions will otherwise be captured
             * by the enclosing FutureTask that is generated when this Runnable is submitted to ScheduledExecutorService.scheduleAtFixedRate().
             * Unhandled exceptions would also prevent further scheduleAtFixedRate() invocations from running.
             */
            FeedbactoryLogger.logSystemEvent(SystemLogLevel.ApplicationError, getClass(), "Auto save task failed, save number: " + nextAutoSaveNumber, anyException);
         }
      }


      /****************************************************************************
       *
       ***************************************************************************/


      synchronized private void restoreLastAutoSaveNumber()
      {
         final Preferences feedbactoryCheckpointPreferences = Preferences.userNodeForPackage(CheckpointManager.class);
         lastAutoSaveNumber = feedbactoryCheckpointPreferences.getInt(LastAutoSaveNumberPreferencesKey, 0);
      }


      synchronized private void saveLastAutoSaveNumber()
      {
         final Preferences feedbactoryCheckpointPreferences = Preferences.userNodeForPackage(CheckpointManager.class);
         feedbactoryCheckpointPreferences.putInt(LastAutoSaveNumberPreferencesKey, lastAutoSaveNumber);
      }


      private boolean isStarted()
      {
         return (executor != null);
      }


      private void start()
      {
         if (isStarted())
            throw new IllegalStateException("Auto save task has already been started.");

         executor = new ScheduledThreadPoolExecutor(1);
         executor.setKeepAliveTime(10, TimeUnit.SECONDS);
         executor.allowCoreThreadTimeOut(true);

         executor.scheduleAtFixedRate(this, AutoSaveCheckpointFrequencyMinutes, AutoSaveCheckpointFrequencyMinutes, TimeUnit.MINUTES);
      }


      synchronized private AutoSaveMetrics getMetrics()
      {
         return new AutoSaveMetrics(isStarted(), autoSavesThisSession,
                                    lastAutoSaveNumber, lastAutoSaveStartTime, lastAutoSaveFinishTime);
      }


      private void shutdown() throws InterruptedException
      {
         if (executor != null)
         {
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            executor = null;

            saveLastAutoSaveNumber();
         }
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void saveCheckpoint(final Path checkpointPath) throws IOException
   {
      networkServiceManager.saveCheckpoint(checkpointPath);
      feedbackManager.saveCheckpoint(checkpointPath);

      // The user account data must always be the last data saved, to ensure that all user account ID references persisted in other subsystems are valid.
      userAccountManager.saveCheckpoint(checkpointPath);
   }


   private void loadCheckpoint(final Path checkpointPath) throws IOException
   {
      if (isAutoSaveStarted())
         throw new IllegalStateException("Cannot load a checkpoint while auto save is active.");

      // The user account data must always be the first data to be restored, to ensure the validity of user account ID lookups from other subsystems during restoration.
      userAccountManager.restoreFromCheckpoint(checkpointPath);

      feedbackManager.restoreFromCheckpoint(checkpointPath);
      networkServiceManager.restoreFromCheckpoint(checkpointPath);
   }


   private void createPathAndSaveCheckpoint(final Path checkpointPath) throws IOException
   {
      if (Files.notExists(checkpointPath))
         Files.createDirectory(checkpointPath);

      saveCheckpoint(checkpointPath);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSavePrimaryCheckpoint() throws IOException
   {
      if (! isAttachedToPrimaryCheckpoint())
         throw new IllegalStateException("Cannot overwrite primary data checkpoint with non-primary data.");

      saveCheckpoint(PrimaryCheckpointPath);
   }


   private void handleStartAutoSave()
   {
      if (! isAttachedToPrimaryCheckpoint())
         throw new IllegalStateException("Cannot start auto saving when not attached to primary data.");

      autoSaveCheckpointingTask.start();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final Path getActiveCheckpointPath()
   {
      return activeCheckpointPath;
   }


   final boolean isAttachedToPrimaryCheckpoint()
   {
      return (PrimaryCheckpointPath.equals(activeCheckpointPath));
   }


   final void loadPrimaryCheckpoint() throws IOException
   {
      loadCheckpoint(PrimaryCheckpointPath);
      activeCheckpointPath = PrimaryCheckpointPath;
   }


   final void savePrimaryCheckpoint() throws IOException
   {
      handleSavePrimaryCheckpoint();
   }


   final void loadAutoSaveCheckpoint(final int checkpointNumber) throws IOException
   {
      final Path checkpointPath = AutoSaveCheckpointsBasePath.resolve(Integer.toString(checkpointNumber));
      loadCheckpoint(checkpointPath);
      activeCheckpointPath = checkpointPath;
   }


   final boolean isAutoSaveStarted()
   {
      return autoSaveCheckpointingTask.isStarted();
   }


   final void startAutoSave()
   {
      handleStartAutoSave();
   }


   final void shutdownAutoSave() throws InterruptedException
   {
      autoSaveCheckpointingTask.shutdown();
   }


   final AutoSaveMetrics getAutoSaveMetrics()
   {
      return autoSaveCheckpointingTask.getMetrics();
   }


   final void loadSnapshotCheckpoint(final String snapshotName) throws IOException
   {
      final Path checkpointPath = SnapshotCheckpointsBasePath.resolve(snapshotName);
      loadCheckpoint(checkpointPath);
      activeCheckpointPath = checkpointPath;
   }


   final void saveSnapshotCheckpoint(final String snapshotName) throws IOException
   {
      createPathAndSaveCheckpoint(SnapshotCheckpointsBasePath.resolve(snapshotName));
   }
}