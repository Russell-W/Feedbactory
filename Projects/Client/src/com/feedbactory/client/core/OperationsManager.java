
package com.feedbactory.client.core;


import com.feedbactory.client.FeedbactoryClient;
import com.feedbactory.client.core.feedback.FeedbackManager;
import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.client.core.network.NetworkServiceManager;
import com.feedbactory.client.core.network.ServerAvailability;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.client.ui.UIManager;
import com.feedbactory.shared.Message;
import com.feedbactory.shared.network.ClientCompatibilityStatus;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.swt.widgets.Display;


final public class OperationsManager
{
   static final private Logger ApplicationRootLogger = Logger.getLogger(OperationsManager.class.getPackage().getName());

   static final private String ForceUpdateCommandSwitch = "-forceUpdate";

   static final private int SignOutOfPersistentSessionTimeoutMilliseconds = 5000;

   final private ExceptionReportMailer exceptionReportMailer;

   final private ConfigurationManager configurationManager;
   final private NetworkServiceManager networkServiceManager;
   final private AccountSessionManager userAccountManager;
   final private FeedbackManager feedbackManager;
   final private UIManager uiManager;


   static
   {
      initialiseLogger();
   }


   static void initialiseLogger()
   {
      /* For development logging, install a custom logger and console handler that will be inherited by the entire Feedbactory client codebase.
       * Both objects need to have their logging levels set to whatever is appropriate based on the execution profile indicated in FeedbactoryClientConstants.
       * I think this approach is cleaner than overriding the system root logger, which in theory may be tampered with by other library code;
       * effectively this method leaves the Feedbactory code with its own isolated logging policy and handler.
       * A final step is to prevent the log messages from bubbling up to the system root logger, otherwise the INFO level messages and higher (by default)
       * will be output twice to the console.
       * A hard reference to the Logger object needs to be kept, otherwise the LogManager (which only maintains soft references) may garbage collect it!
       */
      if (FeedbactoryClientConstants.IsDevelopmentProfile)
      {
         ApplicationRootLogger.setLevel(Level.ALL);
         ApplicationRootLogger.setUseParentHandlers(false);

         final ConsoleHandler consoleHandler = new ConsoleHandler();
         consoleHandler.setLevel(Level.ALL);
         ApplicationRootLogger.addHandler(consoleHandler);
      }
      else
         ApplicationRootLogger.setLevel(Level.OFF);
   }


   public OperationsManager(final FeedbactoryClient.LaunchArguments launchArguments)
   {
      validate(launchArguments);

      exceptionReportMailer = new ExceptionReportMailer();
      configurationManager = new ConfigurationManager(launchArguments.launcherFile);
      networkServiceManager = new NetworkServiceManager(this);
      userAccountManager = new AccountSessionManager(this, networkServiceManager);
      feedbackManager = new FeedbackManager(userAccountManager, networkServiceManager);
      uiManager = new UIManager(this, exceptionReportMailer, userAccountManager);

      initialise();
   }


   private void validate(final FeedbactoryClient.LaunchArguments launchArguments)
   {
      if (launchArguments == null)
         throw new IllegalArgumentException("Feedbactory launch arguments cannot be null.");
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


   final private class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler
   {
      @Override
      final public void uncaughtException(final Thread thread, final Throwable throwable)
      {
         ApplicationRootLogger.log(Level.SEVERE, "Uncaught exception", throwable);
         reportUncaughtException(null, null, thread, throwable);
      }
   }


   /****************************************************************************
    *
    *
    * 
    ***************************************************************************/


   private void handleLaunchApplication(final FeedbactoryClient.LaunchArguments launchArguments)
   {
      /* Attempt to initialise SWT on the main thread as early as possible so that there will be no problems with displaying error messages.
       * The unfortunate reason for this is that the JVM command line parameter for running SWT on Mac OS X (-XstartOnFirstThread)
       * will produce deadlock if any Swing forms are displayed before SWT is up and running (which can never happen if it's not OS/arch compatible).
       *
       * If the SWT libraries are not compatible with the current JVM, it may be possible to update to a compatible library: bail out immediately
       * and delegate the update task to the launcher/updater.
       */
      if (uiManager.isCompatibleSWTVersion())
      {
         final ExecutorService launchApplicationExecutorService = Executors.newSingleThreadExecutor();

         // java.lang.UnsatisfiedLinkError if trying to load 64bit libraries on 32bit JVM or vise versa, hence the earlier compatibility check.
         final Display swtDisplay = uiManager.createSWTDisplay();

         launchApplicationExecutorService.execute(new Runnable()
         {
            @Override
            final public void run()
            {
               processLaunchApplicationThread(launchArguments, swtDisplay);
            }
         });

         /* Although the shutdown call for the temporary launching task is placed here, the task can't actually
          * complete until the SWT read & dispatch call below kicks into gear - it is needed to process the
          * SWT calls made within the launch task. So, there must be no call to the executor's awaitTermination() method,
          * at least until after the SWT read & dispatch method has returned (which is when it's time to shutdown the entire client).
          */
         launchApplicationExecutorService.shutdown();

         // From this point the main thread is assigned to the SWT display, as required by the Cocoa display on Mac OS X.
         uiManager.executeSWTReadAndDispatch();
      }
      else if (! launchArguments.applicationUpdated)
      {
         /* This case occurs when the SWT library is outdated or not loadable because the bitness or platform is incorrect.
          * The user may have switched from a 64-bit JVM to a 32-bit JVM or vise versa.
          * The running OS & architecture may not even be supported by SWT, which is something that will be picked up by the launcher/updater
          * at the time of selecting a download, and reported to the user.
          */
         requestUpdateAndRestart();
      }
      else
      {
         /* If the application has only just been updated, prevent an infinite check configuration-download-check configuration-force update cycle
          * between the launcher and the application. Barring a misconfiguration on the server, eg. incorrect bundling of SWT libraries,
          * this situation should never happen. But if it does, make sure that the client bails out gracefully.
          */
         requestShutdown();
      }
   }


   private void processLaunchApplicationThread(final FeedbactoryClient.LaunchArguments launchArguments, final Display swtDisplay)
   {
      try
      {
         final ConfigurationCheckResult configurationCheckResult = configurationManager.checkConfiguration(launchArguments.launcherVersion);

         switch (configurationCheckResult)
         {
            case OK:
               processLaunchApplication(swtDisplay);
               break;

            case InstanceActive:
               uiManager.reportApplicationInstanceActive();
               break;

            case HeadlessModeNotSupported:
            case SupersededJavaVersion:
               /* These possibilities represent cases that are already handled by the launcher, but must be detected here too for the sake of graceful handling.
                * I don't think there's much value in echoing the same error messages here if the user erroneously runs this application directly
                * instead of via the launcher.
                */
               requestShutdown();
               break;

            case ConfigurationError:
               uiManager.reportConfigurationError();
               break;

            case LauncherUpdateRequired:
               // Reserved for future application versions, where the launcher component may be outdated.
               break;

            default:
               throw new AssertionError("Unhandled configuration check result: " + configurationCheckResult);
         }
      }
      catch (final Exception anyException)
      {
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error during application launch.", anyException);
         uiManager.reportLaunchError(anyException);
      }
   }


   private void processLaunchApplication(final Display swtDisplay)
   {
      try
      {
         uiManager.initialise(swtDisplay, feedbackManager);

         uiManager.launch();

         final NetworkRequestStatus handshakeResult = initiateHandshake();

         if (handshakeResult == NetworkRequestStatus.OK)
         {
            if (userAccountManager.hasPersistentSession())
               uiManager.signInToPersistentSession();
         }
      }
      catch (final Exception anyException)
      {
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error during application launch.", anyException);
         uiManager.reportLaunchError(anyException);
      }
   }


   private NetworkRequestStatus initiateHandshake()
   {
      try
      {
         uiManager.setUIBusy(true);

         return networkServiceManager.checkSendHandshakeRequest();
      }
      finally
      {
         uiManager.setUIBusy(false);
      }
   }


   private Future<Boolean> initiateShutdown(final boolean forceShutdownOnError)
   {
      // To prevent possible deadlocks, the shutdown must be performed on a neutral thread, ie. free up the SWT or Swing EDT threads which may have called this.
      final ExecutorService executorService = Executors.newSingleThreadExecutor();

      final Future<Boolean> shutdownTask = executorService.submit(new Callable<Boolean>()
      {
         @Override
         final public Boolean call()
         {
            processInitiateShutdown();

            final boolean successfulShutdown = processCompleteShutdown();

            if ((! successfulShutdown) && forceShutdownOnError)
               System.exit(1);

            return successfulShutdown;
         }
      });

      executorService.shutdown();

      return shutdownTask;
   }


   private void processInitiateShutdown()
   {
      // Immediately interrupt any active requests, and prevent any further aside from higher priority requests.
      networkServiceManager.shutdown();

      if (userAccountManager.hasResolvedSession() && (! userAccountManager.hasPersistentSession()))
      {
         /* Perform a synchronous sign out if the user is signed into a non-persistent session.
          * The request will be exempt from the network service manager's shutdown.
          */
         try
         {
            uiManager.setUIBusy(true);

            userAccountManager.signOutOfPersistentSession(SignOutOfPersistentSessionTimeoutMilliseconds);
         }
         catch (final Exception anyException)
         {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error during signing out of persistent session.", anyException);
         }
         finally
         {
            uiManager.setUIBusy(false);
         }
      }
   }


   private boolean processCompleteShutdown()
   {
      try
      {
         final Thread swingThread = uiManager.getSwingThread();

         uiManager.preShutdown();
         uiManager.shutdown();

         userAccountManager.shutdown();

         /* Java 7u25 on Mac OS X will not cleanly close down the Swing thread when SWT has been active; confirmed from testing that a bare bones
          * test program of non-graftable SWT and Swing forms will produce the same result. For this application, the graftable component backing frame
          * is still active (according to its status) after shutdown, and trying to dispose of it again from a 2nd Swing request results in a deadlock.
          * Tried a lot of different things to jolt the Swing thread including ensuring that all of the global focus owner/windows are cleared
          * (see GraftableComponentSwingFramework) before disposal, interrupting the Swing thread (??), and submitting an arbitrary Swing task after SWT
          * has shutdown. Unfortunately none of those solutions appeared to work, so a last resort is to invoke System.exit(1) if the Swing thread is
          * still active a short time after attempting to dispose of all of its resources. The flag returned here is to indicate whether or not that will
          * be required.
          */
         if (ConfigurationManager.isRunningMacOSX)
         {
            Thread.sleep(1500);
            return (! swingThread.isAlive());
         }
         else
            return true;
      }
      catch (final Exception anyException)
      {
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error during shutdown.", anyException);

         return false;
      }
      finally
      {
         configurationManager.releaseSingleInstanceLock();
      }
   }


   private void handleRequestUpdateAndRestart()
   {
      // To prevent possible deadlocks, the process must be performed on a neutral thread, ie. free up the SWT or Swing EDT threads which may have called this.
      final ExecutorService executorService = Executors.newSingleThreadExecutor();

      executorService.submit(new Runnable()
      {
         @Override
         final public void run()
         {
            processRequestUpdateAndRestart();
         }
      });

      executorService.shutdown();
   }


   private void processRequestUpdateAndRestart()
   {
      final File javaExecutable = configurationManager.getJavaExecutable();
      final File launcherFile = configurationManager.getLauncherFile();

      if ((javaExecutable != null) && (launcherFile != null))
      {
         boolean successfulShutdown;

         try
         {
            final Future<Boolean> shutdownTask = initiateShutdown(false);
            successfulShutdown = shutdownTask.get();
         }
         catch (final Exception anyException)
         {
            /* Any shutdown exception will have already been logged by the shutdown process if logging is enabled.
             * But if there was a problem it needs to be noted since System.exit() should be used to bail out rather
             * than try to allow the JVM finish normally; if the JVM doesn't exit and the updated installer is run,
             * locks on the class files will prevent the old version from being deleted.
             */
            successfulShutdown = false;
         }

         try
         {
            /* Run the updated installer stub as a separate process immediately before this client JVM finishes.
             * Once the installer stub (which has to download the new Feedbactory client data) is ready to
             * delete the old files and install the new ones, this JVM should have shutdown and the classloader
             * released any locks which would otherwise prevent the deletion of superseded files.
             */
            getLauncherProcess(javaExecutable, launcherFile).start();

            /* Only forcefully shutdown the client if there was an error during the UI shutdown AND there
             * was no error when kicking off the launch process. In the latter case, a message dialog
             * should be displayed to the user.
             */
            if (! successfulShutdown)
               System.exit(1);
         }
         catch (final Exception anyException)
         {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error running launcher process during restart.", anyException);

            /* Report the error in attempting to run the application launcher, but not if running Mac OS X and SWT has not yet been initialised.
             * The unfortunate reason for this is that the JVM command line parameter for running SWT on OS X (-XstartOnFirstThread)
             * will produce deadlock if any Swing forms are displayed until SWT is up and running (which can never happen if it's not OS/arch compatible).
             * This case would be extremely rare, eg. user runs launcher on 64-bit JVM, launcher downloads and installs 64-bit SWT libraries,
             * user later switches to a 32-bit JVM (still available on OS X??) AND tries to directly run the application rather than the launcher
             * (which would simply download the correct 32-bit SWT libraries).
             */
            if ((! ConfigurationManager.isRunningMacOSX) || uiManager.isCompatibleSWTVersion())
               uiManager.reportApplicationUpdateError();
            else
               requestShutdown();
         }
      }
      else
      {
         /* This branch will occur if the user directly loads the application jar file, rather than load Feedbactory via the launcher.
          * It also applies in development when virtually the same situation applies, since the application is being run directly.
          */
         Logger.getLogger(getClass().getName()).warning("Can't find launcher and/or Java executable from current context.");

         requestShutdown();
      }
   }


   private ProcessBuilder getLauncherProcess(final File javaExecutable, final File launcherFile)
   {
      final List<String> commands = new ArrayList<String>(4);
      commands.add(javaExecutable.getPath());
      commands.add("-jar");
      commands.add(launcherFile.getPath());
      commands.add(ForceUpdateCommandSwitch);

      return new ProcessBuilder(commands);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void launchApplication(final FeedbactoryClient.LaunchArguments launchArguments)
   {
      handleLaunchApplication(launchArguments);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void processFeedbackHandshake(final ByteBuffer handshakeBuffer)
   {
      feedbackManager.processFeedbackHandshake(handshakeBuffer);
   }


   final public void reportUncaughtException(final Class<?> contextClass, final String contextMessage, final Thread thread, final Throwable throwable)
   {
      uiManager.reportUncaughtException(contextClass, contextMessage, thread, throwable);
   }


   final public void reportIPAddressTemporarilyBlocked()
   {
      uiManager.reportIPAddressTemporarilyBlocked();
   }


   final public void reportApplicationServerStatusUpdated(final ServerAvailability serverAvailability)
   {
      uiManager.reportServerStatusUpdated(serverAvailability);
   }


   final public void reportClientCompatibilityUpdated(final ClientCompatibilityStatus clientCompatibilityStatus)
   {
      /* If there is a persisted session, it may or may not be compatible with the latest release; don't drop it, instead leave the updated client
       * to check the session versioning on restoration and decide whether or not the data is compatible.
       */
      uiManager.reportClientCompatibilityUpdated(clientCompatibilityStatus);
   }


   final public void reportGeneralBroadcastMessage(final Message generalBroadcastMessage)
   {
      uiManager.reportGeneralBroadcastMessage(generalBroadcastMessage);
   }


   final public void reportNetworkFailure(final NetworkRequestStatus networkStatus)
   {
      uiManager.reportNetworkFailure(networkStatus);
   }


   final public void reportNetworkSessionError()
   {
      uiManager.reportNetworkSecurityCondition();
   }


   final public void reportRequestError()
   {
      uiManager.reportRequestError();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public boolean hasSentHandshakeRequest()
   {
      return networkServiceManager.hasSentHandshakeRequest();
   }


   final public long getApproximateServerTime()
   {
      return networkServiceManager.getApproximateServerTime();
   }


   final public void requestUpdateAndRestart()
   {
      handleRequestUpdateAndRestart();
   }


   final public void requestShutdown()
   {
      initiateShutdown(true);
   }
}