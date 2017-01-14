/* Memos:
 * - Creating a FeedbactoryClient instance allows for setting up the default uncaught exception handler before the operations manager is initialised,
 *   while being able to reference the operations manager as a final variable to report exceptions. If an error occurs during initialisation of
 *   the operations manager, the reference will be null and no message can be displayed via the UI.
 * 
 * - The LaunchArguments object is effectively used as a single instance key to the operations manager's public launch method, since LaunchArguments can only
 *   be instantiated by this class. This doesn't prevent direct callbacks to this class' main method, which the AtomicBoolean singleInstanceKey is used to prevent.
 *   Outside of this JVM the single instance mechanism is enforced by the operations manager using file locking.
 */

package com.feedbactory.client;


import com.feedbactory.client.core.OperationsManager;
import com.feedbactory.shared.FeedbactoryConstants;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;


final public class FeedbactoryClient
{
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
    *
    * This flag could also be used to invoke an initialisation procedure when updating to a newer version.
    */
   static final private String ApplicationUpdatedCommandSwitch = "-applicationUpdated";

   static final private AtomicBoolean SingleInstanceKey = new AtomicBoolean();

   final private OperationsManager operationsManager;


   private FeedbactoryClient(final LaunchArguments launchArguments)
   {
      operationsManager = new OperationsManager(launchArguments);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class LaunchArguments
   {
      final public long launcherVersion;
      final public File launcherFile;
      final public boolean applicationUpdated;


      private LaunchArguments(final long launcherVersion, final File launcherFile, final boolean applicationUpdated)
      {
         this.launcherVersion = launcherVersion;
         this.launcherFile = launcherFile;
         this.applicationUpdated = applicationUpdated;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static private void handleMain(final String[] arguments) throws URISyntaxException
   {
      final LaunchArguments launchArguments = parseLaunchArguments(arguments);

      if (SingleInstanceKey.compareAndSet(false, true))
         new FeedbactoryClient(launchArguments).launch(launchArguments);
      else
         throw new IllegalStateException("Cannot start more than one JVM instance of the Feedbactory client.");
   }


   static private LaunchArguments parseLaunchArguments(final String[] arguments) throws URISyntaxException
   {
      long launcherVersion = FeedbactoryConstants.NoTime;
      File launcherFile = null;
      boolean applicationUpdated = false;

      int argumentIndex = 0;
      while (argumentIndex < arguments.length)
      {
         if (arguments[argumentIndex].equals(LauncherVersionCommandSwitch))
         {
            argumentIndex ++;
            if (argumentIndex < arguments.length)
               launcherVersion = Long.parseLong(arguments[argumentIndex]);
            else
               throw new IllegalArgumentException("Missing launcher version argument.");
         }
         else if (arguments[argumentIndex].equals(LauncherPathCommandSwitch))
         {
            argumentIndex ++;
            if (argumentIndex < arguments.length)
            {
               final URI launcherFileURI = new URI(arguments[argumentIndex]);
               launcherFile = new File(launcherFileURI);
            }
            else
               throw new IllegalArgumentException("Missing launcher file argument.");
         }
         else if (arguments[argumentIndex].equals(ApplicationUpdatedCommandSwitch))
            applicationUpdated = true;
         else
            throw new IllegalArgumentException("Invalid arguments.");

         argumentIndex ++;
      }

      return new LaunchArguments(launcherVersion, launcherFile, applicationUpdated);
   }


   private void launch(final LaunchArguments launchArguments)
   {
      operationsManager.launchApplication(launchArguments);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public void main(final String... arguments) throws URISyntaxException
   {
      handleMain(arguments);
   }
}