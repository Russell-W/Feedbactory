/* Memos:
 * - This class is almost entirely a ripped subsection of the Feedbactory server's console.
 */

package com.feedbactory.recentfeedbackupdater.core;


import com.feedbactory.recentfeedbackupdater.RecentFeedbackUpdater;
import com.feedbactory.shared.FeedbactoryConstants;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;


final public class Console
{
   static final private int ConsoleBufferSizeBytes = 256;

   static final private String ConsolePrompt;

   static final private String StatusCommand = "status";

   static final private int DefaultTaskFrequencyMinutes = 15;
   static final private int DefaultTaskStopTimeoutSeconds = 10;
   static final private String StartCommand = "start";
   static final private String ShutdownCommand = "shutdown";

   static final private String ExitCommand = "exit";

   final private Map<String, CommandExecutor> commandExecutors = new HashMap<>();

   final private OperationsManager operationsManager;


   static
   {
      if (RecentFeedbackUpdater.isDevelopmentProfile)
         ConsolePrompt = "feedbactory recent feedback updater development> ";
      else if (RecentFeedbackUpdater.isTestProfile)
         ConsolePrompt = "feedbactory recent feedback updater test> ";
      else if (RecentFeedbackUpdater.isProductionProfile)
         ConsolePrompt = "feedbactory recent feedback updater production> ";
      else
         throw new AssertionError("Unknown or misconfigured execution profile.");
   }


   public Console(final RecentFeedbackUpdater recentFeedbackUpdater)
   {
      validate(recentFeedbackUpdater);

      operationsManager = new OperationsManager();

      initialise();
   }


   private void validate(final RecentFeedbackUpdater recentFeedbackUpdater)
   {
      if (recentFeedbackUpdater == null)
         throw new IllegalArgumentException("Recent feedback updater argument cannot be null.");
   }


   private void initialise()
   {
      initialiseCommandExecutors();
   }


   private void initialiseCommandExecutors()
   {
      initialiseStatusCommand();
      initialiseStartCommand();
      initialiseShutdownCommand();
   }


   private void initialiseStatusCommand()
   {
      commandExecutors.put(StatusCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments)
         {
            processStatusCommand(arguments);
         }
      });
   }


   private void initialiseStartCommand()
   {
      commandExecutors.put(StartCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments)
         {
            processStartCommand(arguments);
         }
      });
   }


   private void initialiseShutdownCommand()
   {
      commandExecutors.put(ShutdownCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments) throws InterruptedException
         {
            processShutdownCommand(arguments);
         }
      });
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static private interface CommandExecutor
   {
      public void executeCommand(final String[] arguments) throws IOException, InterruptedException;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private String[] trimArguments(final String[] arguments)
   {
      return Arrays.copyOfRange(arguments, 1, arguments.length);
   }


   private String getFormattedDateAndTime(final long dateAndTime)
   {
      final Formatter formatter = new Formatter();

      if (dateAndTime == FeedbactoryConstants.NoTime)
         formatter.format("Never");
      else
      {
         formatter.format("%tH:%<tM:%<tS%<tp ", dateAndTime);

         final Calendar displayDateTimeCalendar = Calendar.getInstance();
         displayDateTimeCalendar.setTimeInMillis(dateAndTime);

         final Calendar todayCalendar = Calendar.getInstance();
         final Calendar yesterdayCalendar = Calendar.getInstance();
         yesterdayCalendar.add(Calendar.DAY_OF_YEAR, -1);

         final int displayDateTimeYear = displayDateTimeCalendar.get(Calendar.YEAR);
         final int displayDateTimeDay = displayDateTimeCalendar.get(Calendar.DAY_OF_YEAR);

         if ((displayDateTimeYear == todayCalendar.get(Calendar.YEAR)) &&
             (displayDateTimeDay == todayCalendar.get(Calendar.DAY_OF_YEAR)))
            formatter.format("Today");
         else if ((displayDateTimeYear == yesterdayCalendar.get(Calendar.YEAR)) &&
                  (displayDateTimeDay == yesterdayCalendar.get(Calendar.DAY_OF_YEAR)))
            formatter.format("Yesterday");
         else
            formatter.format("%tA %<td/%<tm/%<tY", dateAndTime);
      }

      return formatter.toString();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processStatusCommand(final String[] arguments)
   {
      if (arguments.length == 0)
         displayStatus();
      else
         System.out.println("Invalid command switch.");
   }


   private void displayStatus()
   {
      final String labelValueFormat = "%-30.30s%s%n";

      System.out.println();
      System.out.format(labelValueFormat, "Time:", getFormattedDateAndTime(System.currentTimeMillis()));
      System.out.format(labelValueFormat, "Last run:", getFormattedDateAndTime(operationsManager.getLastRunTime()));
      System.out.format(labelValueFormat, "Status:", operationsManager.isStarted() ? "Started" : "Stopped");
      System.out.println();
   }


   private void processStartCommand(final String[] arguments)
   {
      if (arguments.length == 0)
         scheduleRecentFeedbackUpdate(DefaultTaskFrequencyMinutes);
      else if (arguments.length == 1)
         scheduleRecentFeedbackUpdate(Integer.parseInt(arguments[0]));
      else
         System.out.println("Invalid command arguments.");
   }


   private void scheduleRecentFeedbackUpdate(final int taskFrequencyMinutes)
   {
      if (! operationsManager.isStarted())
      {
         System.out.println("Starting...");
         operationsManager.scheduleUpdateRecentFeedback(taskFrequencyMinutes);
      }
      else
         System.out.println("Task has already been scheduled.");
   }


   private void processShutdownCommand(final String[] arguments) throws InterruptedException
   {
      if (arguments.length == 0)
         shutdownRecentFeedbackUpdate(DefaultTaskStopTimeoutSeconds);
      else if (arguments.length == 1)
         shutdownRecentFeedbackUpdate(Integer.parseInt(arguments[0]));
      else
         System.out.println("Invalid command arguments.");
   }


   private void shutdownRecentFeedbackUpdate(final int timeoutSeconds) throws InterruptedException
   {
      if (operationsManager.isStarted())
      {
         System.out.println("Shutting down...");
         operationsManager.shutdownRecentFeedbackUpdate(timeoutSeconds);
      }
      else
         System.out.println("Task has not been scheduled.");
   }


   /****************************************************************************
    *
    *
    ***************************************************************************/


   private void handleStart() throws IOException
   {
      Thread.currentThread().setPriority(9);

      try
      (
         final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in), ConsoleBufferSizeBytes);
      )
      {
         for (;;)
         {
            System.out.print(ConsolePrompt);

            try
            {
               if (! processCommandLine(reader.readLine()))
                  break;
            }
            catch (final NumberFormatException numberFormatException)
            {
               // And let's hope that it's the command parsing and not the commands themselves that throw this exception...
               System.out.format("Numeric argument expected: %s%n", numberFormatException.getMessage());
            }
            catch (final Exception anyOtherException)
            {
               System.out.format("Error while processing the command: %s%n", anyOtherException.getMessage());
            }
         }
      }
   }


   private boolean processCommandLine(final String line) throws IOException, InterruptedException
   {
      final String trimmedLine = line.trim();

      if (! trimmedLine.isEmpty())
      {
         final String[] lineTokens = trimmedLine.split("\\s+");
         final String command = lineTokens[0];
         return processCommandString(command, trimArguments(lineTokens));
      }

      return true;
   }


   private boolean processCommandString(final String command, final String[] arguments) throws IOException, InterruptedException
   {
      switch (command)
      {
         case ExitCommand:
            return processExitCommand(arguments);

         default:

            final CommandExecutor commandExecutor = commandExecutors.get(command);

            if (commandExecutor != null)
               commandExecutor.executeCommand(arguments);
            else
               System.out.format("Unrecognised command: %s%n", command);

            return true;
      }
   }


   private boolean processExitCommand(final String[] arguments)
   {
      if (arguments.length > 1)
      {
         System.out.println("Invalid command arguments.");
         return true;
      }
      else if (operationsManager.isStarted())
      {
         System.out.println("The updater task is scheduled or active.");
         return true;
      }
      else
      {
         System.out.println("Exiting...");
         return false;
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void start() throws IOException
   {
      handleStart();
   }
}