
package com.feedbactory.server;


import com.feedbactory.server.core.FeedbactoryConsole;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;


final public class FeedbactoryServer
{
   static final private AtomicBoolean singleInstanceExecutionKey = new AtomicBoolean();

   /* Unlike the Feedbactory client, the server's execution profile need not be compiled and hardwired into the code.
    * The profile can be instead set from the command line on startup since the server module is not for public use.
    *
    * The default execution profile is set to 'development', which is a safe option when nothing is specified at the command line.
    * For this reason, don't change the setting here - use the command line.
    *
    * The variable need not be marked as volatile since its final value will be set before subsequent program threads start.
    */
   static private ExecutionProfile executionProfile = ExecutionProfile.Development;

   static final public boolean IsDebugMode = false;


   private FeedbactoryServer()
   {
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static public enum ExecutionProfile
   {
      Development,
      Test,
      Production;


      static private ExecutionProfile fromValue(final String executionProfileString)
      {
         switch (executionProfileString.toLowerCase())
         {
            case "development":
               return Development;
            case "test":
               return Test;
            case "production":
               return Production;
            default:
               throw new IllegalArgumentException("Invalid execution profile value: " + executionProfileString);
         }
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static private void handleMain(final String... arguments) throws IOException
   {
      // Prevent callbacks to static public main().
      if (singleInstanceExecutionKey.compareAndSet(false, true))
      {
         processCommandLineArguments(arguments);

         // Likewise ensure that only one instance of the console can be instantiated by passing it a private instance of this class.
         final FeedbactoryServer feedbactoryServerInstance = new FeedbactoryServer();
         new FeedbactoryConsole(feedbactoryServerInstance).start();
      }
      else
         throw new IllegalStateException("An instance of the Feedbactory server is already running within this JVM.");
   }


   static private void processCommandLineArguments(final String... arguments)
   {
      if (arguments.length > 0)
      {
         if ((arguments.length == 1) && arguments[0].startsWith("-"))
            executionProfile = ExecutionProfile.fromValue(arguments[0].substring(1));
         else
            throw new IllegalArgumentException("Invalid parameters.");
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public ExecutionProfile getExecutionProfile()
   {
      return executionProfile;
   }


   static public void main(final String... arguments) throws IOException
   {
      handleMain(arguments);
   }
}