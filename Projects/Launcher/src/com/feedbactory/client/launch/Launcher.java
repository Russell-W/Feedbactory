/* Memos:
 * - When executing the new version of Feedbactory post install (as a separate process), should I pass it the filename of the installer so
 *   that it can be deleted?
 *   - No, permissions file may be different, especially on Windows.
 *
 * - An uninstall mechanism is available in the configuration manager and could be made available via a command line switch to this class to be executed
 *   via a full uninstall process (eg. InnoSetup). However the uninstall only handles the deletion of files for the current user, which in the case of a
 *   Windows uninstall would be limited to an Administrator. A more complete mechanism would have to somehow track the files installed for all users.
 */

package com.feedbactory.client.launch;


import com.feedbactory.client.launch.core.OperationsManager;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;


final public class Launcher
{
   static final private AtomicBoolean singleInstanceExecutionKey = new AtomicBoolean();

   static final private String ForceUpdateCommandSwitch = "-forceUpdate";
   static final private String ClientJVMOptionsCommandSwitch = "-jvmOptions";

   final private OperationsManager operationsManager;


   private Launcher(final LaunchArguments launchArguments)
   {
      operationsManager = new OperationsManager(this, launchArguments);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class LaunchArguments
   {
      final public boolean forceUpdate;
      final public String[] clientJVMOptions;


      private LaunchArguments(final boolean forceUpdate, final String[] clientJVMOptions)
      {
         this.forceUpdate = forceUpdate;
         this.clientJVMOptions = clientJVMOptions;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static private void handleMain(final String[] arguments)
   {
      final LaunchArguments launchArguments = parseLaunchArguments(arguments);

      if (singleInstanceExecutionKey.compareAndSet(false, true))
         new Launcher(launchArguments).launch();
      else
         throw new IllegalStateException("Cannot start more than one JVM instance of the Feedbactory application launcher.");
   }


   static private LaunchArguments parseLaunchArguments(final String[] arguments)
   {
      boolean forceUpdate = false;
      String[] clientJVMOptions = new String[0];

      String argument;
      for (int argumentIndex = 0; argumentIndex < arguments.length; argumentIndex ++)
      {
         argument = arguments[argumentIndex];

         if (argument.equals(ForceUpdateCommandSwitch))
            forceUpdate = true;
         else if (argument.equals(ClientJVMOptionsCommandSwitch))
         {
            clientJVMOptions = Arrays.copyOfRange(arguments, argumentIndex + 1, arguments.length);
            break;
         }
         else
            throw new IllegalArgumentException("Unknown parameter: " + argument);
      }

      return new LaunchArguments(forceUpdate, clientJVMOptions);
   }


   private void launch()
   {
      operationsManager.launch();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public void main(final String... arguments) throws Exception
   {
      handleMain(arguments);
   }
}