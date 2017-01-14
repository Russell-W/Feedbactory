
package com.feedbactory.client.launch.core;


import com.feedbactory.client.launch.Launcher;
import com.feedbactory.client.launch.ui.UIManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


final public class OperationsManager
{
   static final private Logger ApplicationRootLogger = Logger.getLogger(Launcher.class.getPackage().getName());

   /* The launcher version command line option provides a future-proof means for newer versions of the application to force the user to manually
    * update outdated version of the launcher. Since the launcher may be installed in a location in which the non-admin user doesn't have write access by
    * default (eg. Program Files on Windows), it's handy to provide this mechanism. If it proves to be unnecessary in future releases, there's
    * no harm done.
    */
   static final private String LauncherVersionCommandSwitch = "-launcherVersion";

   /* The launcher file command line option provides a callback location for the launcher/updater stub, which is required when
    * Feedbactory detects that its version has become outdated or that the wrong SWT library is installed.
    * Unlike the application file which sits within a Feedbactory folder in the user's home folder (where read & write permissions
    * are almost guaranteed), the launcher/updater file may reside anywhere, typically wherever it was copied during the initial installation.
    */
   static final private String LauncherPathCommandSwitch = "-launcherPath";

   /* The updated command line option indicates that the application has just been updated, and no attempt should be performed by the
    * configuration manager to attempt to immediately re-update the application on startup if it detects that the program still can't run.
    * This is unlikely, but a possibility if for example the wrong SWT version is bundled with a Feedbactory app download for a particular OS/architecture,
    * which could lead to an infinite check configuration-download-check configuration-force update cycle between the launcher and the application.
    * This flag does not prevent updates from occurring after the initial startup, ie. when the server explicitly sends an indicator to the
    * client that it requires an update.
    */
   static final private String ApplicationUpdatedCommandSwitch = "-applicationUpdated";

   final private ExceptionReportMailer exceptionReportMailer;

   final private ConfigurationManager updateManager;

   final private UIManager uiManager;

   final private boolean forceUpdate;
   final private String[] clientJVMOptions;


   static
   {
      initialiseLogger();
   }


   static void initialiseLogger()
   {
      if (LauncherConstants.IsDevelopmentProfile)
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


   public OperationsManager(final Launcher launcher, final Launcher.LaunchArguments launchArguments)
   {
      validate(launchArguments);

      this.forceUpdate = launchArguments.forceUpdate;
      this.clientJVMOptions = launchArguments.clientJVMOptions.clone();

      exceptionReportMailer = new ExceptionReportMailer();
      updateManager = new ConfigurationManager();
      uiManager = new UIManager(this, updateManager);

      initialise();
   }


   private void validate(final Launcher.LaunchArguments launchArguments)
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
         reportUncaughtException(new ExceptionReportContext(null, null, thread, throwable));
      }
   }


   /****************************************************************************
    *
    *
    * 
    ***************************************************************************/


   private void handleLaunch()
   {
      try
      {
         final ConfigurationCheckResult checkResult = updateManager.checkConfiguration();

         switch (checkResult)
         {
            case NoUpdateRequired:
               if (forceUpdate)
                  uiManager.initiateApplicationUpdate();
               else
                  startApplication(false);

               break;

            case UpdateRequired:
               uiManager.initiateApplicationUpdate();
               break;

            case SupersededJavaVersion:
               uiManager.reportSupersededJavaVersion();
               break;

            case UnsupportedPlatform:
               uiManager.reportUnsupportedPlatform();
               break;

            case HeadlessModeNotSupported:
               uiManager.reportHeadlessEnvironmentUnsupported();
               break;

            case ConfigurationError:
               /* A configuration error indicates a fundamental problem with the current Feedbactory environment,
                * the cause of which would need intervention by the user before even a new version of Feedbactory
                * could expect to be installed and run successfully.
                * Examples: no write permissions within the user.home folder, or a folder name clash with a file name.
                */
               uiManager.reportConfigurationError();
               break;

            default:
               throw new AssertionError("Unhandled configuration check result: " + checkResult);
         }
      }
      catch (final Exception anyException)
      {
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Launch exception", anyException);
         uiManager.reportUncaughtException(new ExceptionReportContext(getClass(), "Error while initialising Feedbactory", Thread.currentThread(), anyException));
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void startApplication(final boolean applicationUpdated)
   {
      final File javaExecutable = updateManager.getJavaExecutable();
      final File applicationFile = updateManager.getApplicationFile();

      /* The launcherFile will be null if this class is not running from a jar file context, in which case no callback switch to it will be provided
       * to the application. In production this won't happen unless a user extracts the jar or tries to run the application jar file directly.
       * In development the execution is never linked to a jar file and launcherFile will always be null, so it's handy to allow the launcher to
       * application transition to work without it (update callbacks to the launcher will be disabled in this instance).
       */
      final File launcherFile = updateManager.getLauncherFile();

      if ((javaExecutable != null) && (applicationFile != null))
      {
         try
         {
            final Process process = getLauncherProcess(javaExecutable, applicationFile, launcherFile, applicationUpdated).start();

            if (LauncherConstants.IsDebugMode)
               attachProcessOutputListener(process);

            shutdown();
         }
         catch (final Exception anyException)
         {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Feedbactory process launch exception", anyException);
            uiManager.reportUncaughtException(new ExceptionReportContext(getClass(), "Error while initialising Feedbactory", Thread.currentThread(), anyException));
         }
      }
      else
         uiManager.reportConfigurationError();
   }


   private ProcessBuilder getLauncherProcess(final File javaExecutable, final File applicationFile, final File launcherFile, final boolean applicationUpdated)
   {
      final List<String> commands = new ArrayList<String>(10);
      commands.add(javaExecutable.getPath());

      if (ConfigurationManager.isRunningMacOSX)
         commands.add("-XstartOnFirstThread");

      for (final String option : clientJVMOptions)
         commands.add(option);

      commands.add("-jar");
      commands.add(applicationFile.getPath());

      commands.add(LauncherVersionCommandSwitch);
      commands.add(Long.toString(LauncherConstants.VersionID));

      if (launcherFile != null)
      {
         commands.add(LauncherPathCommandSwitch);
         commands.add(launcherFile.toURI().toString());
      }

      if (applicationUpdated)
         commands.add(ApplicationUpdatedCommandSwitch);

      return new ProcessBuilder(commands);
   }


   private void attachProcessOutputListener(final Process process) throws IOException, InterruptedException
   {
      final InputStream is = process.getErrorStream();
      final BufferedReader br = new BufferedReader(new InputStreamReader(is));
      String nextLine;
      while ((nextLine = br.readLine()) != null)
         System.err.println("[Application]: " + nextLine);

      System.err.println("[Application exit status]: " + process.waitFor());
   }


   private void shutdown()
   {
      // Does nothing for now, but is an important placeholder for code that marks a shutdown point.
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public ExceptionReportMailer getExceptionReportMailer()
   {
      return exceptionReportMailer;
   }


   final public void reportUncaughtException(final ExceptionReportContext exceptionReportContext)
   {
      uiManager.reportUncaughtException(exceptionReportContext);
   }


   final public void launch()
   {
      handleLaunch();
   }


   final public void reportApplicationUpdated()
   {
      startApplication(true);
   }


   final public void requestShutdown()
   {
      shutdown();
   }
}