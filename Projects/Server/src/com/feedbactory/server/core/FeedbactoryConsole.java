/* Memos:
 * - When run from within many IDEs, System.console() is null.
 *
 * - The priority for feedback inspection utilities should be to identify abusers of the feedback system.
 *
 * - I'll add other commands & switches as needed. Some possibilities:
 *   - View detailed item profile feedback submission.
 *   - Remove (or at least disable/lock) account, and remove its feedback.
 *   - Drop account sessions?
 *   - 
 */

package com.feedbactory.server.core;


import com.feedbactory.server.FeedbactoryServer;
import com.feedbactory.server.core.CheckpointManager.AutoSaveMetrics;
import com.feedbactory.server.core.log.FeedbactoryLogger.EventLogMetrics;
import com.feedbactory.server.core.log.LoggerManager;
import com.feedbactory.server.core.log.SecurityLogLevel;
import com.feedbactory.server.core.log.SystemEvent;
import com.feedbactory.server.core.log.SystemLogLevel;
import com.feedbactory.server.feedback.FeedbackManager;
import com.feedbactory.server.feedback.ItemProfileFeedbackSubmission;
import com.feedbactory.server.network.application.ApplicationRequestManager.BufferPoolMetrics;
import com.feedbactory.server.network.application.ApplicationRequestManager.RequestMetrics;
import com.feedbactory.server.network.application.ApplicationServerController;
import com.feedbactory.server.network.application.ApplicationServerController.ServerMetrics;
import com.feedbactory.server.network.application.ApplicationServerController.ServerState;
import com.feedbactory.server.network.application.NetworkToApplicationGateway;
import com.feedbactory.server.network.application.UserAccountSessionManager.AccountSessionMetrics;
import com.feedbactory.server.network.application.UserAccountSessionManager.SessionManagerMetrics;
import com.feedbactory.server.network.component.IPAddressRequestMonitor;
import com.feedbactory.server.network.component.IPAddressRequestMonitor.BlockedIPAddressMetrics;
import com.feedbactory.server.network.component.IPAddressRequestMonitor.IPAddressMetrics;
import com.feedbactory.server.network.component.IPAddressRequestMonitor.IPMonitorMetrics;
import com.feedbactory.server.useraccount.FeedbactoryUserAccountTest;
import com.feedbactory.server.useraccount.FeedbactoryUserAccountView;
import com.feedbactory.server.useraccount.UserAccountMailer.UserAccountMailerMetrics;
import com.feedbactory.server.useraccount.UserAccountManager.UserAccountManagerMetrics;
import com.feedbactory.server.useraccount.UserAccountNetworkGateway.EmailBlockedIPAddressMetrics;
import com.feedbactory.server.useraccount.UserAccountNetworkGateway.EmailFailedAuthenticationMetrics;
import com.feedbactory.server.useraccount.UserAccountNetworkGateway.IPAddressFailedAuthenticationMetrics;
import com.feedbactory.server.useraccount.UserAccountNetworkGateway.IPAuthenticationMetrics;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.Message;
import com.feedbactory.shared.MessageType;
import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.network.FeedbactoryNetworkConstants;
import com.feedbactory.shared.network.IPAddressStanding;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;


final public class FeedbactoryConsole
{
   static final private int ConsoleBufferSizeBytes = 256;

   static final private String ConsolePrompt = "feedbactory " + FeedbactoryServer.getExecutionProfile().name().toLowerCase() + "> ";

   static final private String OnCommandSwitch = "on";
   static final private String OffCommandSwitch = "off";
   static final private String TestCommandSwitch = "test";
   static final private String ResetCommandSwitch = "reset";

   static final private String BatchCommand = "batch";
   static final private Path BatchScriptPath = Paths.get("Batch");
   static final private String BatchScriptExtension = ".txt";

   static final private String StatusSummaryCommand = "status";

   static final private String EventLogCommand = "log";
   static final private String SystemEventLogCommandSwitch = "system";
   static final private String SecurityEventLogCommandSwitch = "security";
   static final private String SMSEventLogCommandSwitch = "sms";

   static final private String SaveCheckpointCommand = "save";
   static final private String LoadCheckpointCommand = "load";
   static final private String PrimaryCheckpointCommandSwitch = "primary";
   static final private String AutoSaveCheckpointCommandSwitch = "autosave";
   static final private String SnapshotCheckpointCommandSwitch = "snapshot";
   static final private String AutoSaveCommand = "autosave";

   static final private String HousekeepingCommand = "housekeeping";

   static final private long DefaultServerShutdownTimeoutMilliseconds = TimeUnit.SECONDS.toMillis(10);
   static final private String ServerCommand = "server";
   static final private String ServerBacklogCommandSwitch = "backlog";
   static final private String ServerReceiveBufferCommandSwitch = "receivebuffer";
   static final private String ServerConnectionLimitsCommandSwitch = "connection";
   static final private String ServerStartCommandSwitch = "start";
   static final private String ServerShutdownCommandSwitch = "shutdown";

   static final private String IPMonitorCommand = "ipmonitor";
   static final private String IPMonitorBlockedCommandSwitch = "blocked";
   static final private String IPMonitorBlacklistedIPCommandSwitch = "blacklisted";
   static final private String IPMonitorBlacklistIPCommandSwitch = "blacklist";
   static final private String IPMonitorParoleIPCommandSwitch = "parole";

   static final private String FeedbactoryAvailableCommand = "feedbactory";
   static final private String RequestCommand = "request";
   static final private String BufferPoolCommand = "bufferpool";

   // Use with great caution!
   static final private String ClientVersionCommand = "clientversion";
   static final private String ForceClientVersionCommandSwitch = "force";

   static final private String BroadcastMessageCommand = "broadcast";

   static final private String SessionCommand = "session";

   static final private String IPAuthenticationCommand = "ipauth";
   static final private String IPAuthenticationBlockedCommandSwitch = "blocked";

   static final private String UserAccountCommand = "account";
   static final private String UserAccountMessageCommandSwitch = "message";

   static final private String MailerCommand = "mailer";

   static final private String FeedbackCommand = "feedback";
   static final private String FeedbackCommandAccountSwitch = "account";

   static final private String ExitCommand = "exit";
   static final private String ExitNoSaveCommandSwitch = "nosave";

   final private FeedbactoryOperationsManager operationsManager;

   final private Map<String, CommandExecutor> commandExecutors = new HashMap<>();


   public FeedbactoryConsole(final FeedbactoryServer feedbactoryServer) throws IOException
   {
      validate(feedbactoryServer);

      operationsManager = new FeedbactoryOperationsManager();

      initialise();
   }


   private void validate(final FeedbactoryServer feedbactoryServer)
   {
      if (feedbactoryServer == null)
         throw new IllegalArgumentException("Feedbactory server argument cannot be null.");
   }


   private void initialise()
   {
      initialiseCommandExecutors();

      checkMinimumCompatibleClientVersion();
   }


   private void initialiseCommandExecutors()
   {
      initialiseStatusCommand();

      initialiseEventLogCommands();

      initialiseHousekeepingCommand();

      initialiseCheckpointCommands();

      initialiseServerCommands();

      initialiseIPMonitorCommands();

      initialiseApplicationCommands();

      initialiseClientVersionCommand();

      initialiseBroadcastMessageCommand();

      initialiseSessionCommands();

      initialiseAccountNetworkCommand();

      initialiseAccountCommands();

      initialiseMailerCommands();

      initialiseFeedbackCommands();
   }


   private void initialiseStatusCommand()
   {
      commandExecutors.put(StatusSummaryCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments) throws IOException
         {
            processStatusCommand(arguments);
         }
      });
   }


   private void initialiseEventLogCommands()
   {
      commandExecutors.put(EventLogCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments)
         {
            processEventLogCommand(arguments);
         }
      });
   }


   private void initialiseCheckpointCommands()
   {
      commandExecutors.put(SaveCheckpointCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments) throws IOException
         {
            processSaveCheckpointCommand(arguments);
         }
      });

      commandExecutors.put(LoadCheckpointCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments) throws IOException
         {
            processLoadCheckpointCommand(arguments);
         }
      });

      commandExecutors.put(AutoSaveCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments) throws InterruptedException
         {
            processAutoSaveCommand(arguments);
         }
      });
   }


   private void initialiseHousekeepingCommand()
   {
      commandExecutors.put(HousekeepingCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments) throws InterruptedException
         {
            processHousekeepingCommand(arguments);
         }
      });
   }


   private void initialiseServerCommands()
   {
      commandExecutors.put(ServerCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments) throws IOException, InterruptedException
         {
            processServerCommand(arguments);
         }
      });
   }


   private void initialiseIPMonitorCommands()
   {
      commandExecutors.put(IPMonitorCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments) throws UnknownHostException
         {
            processIPMonitorCommand(arguments);
         }
      });
   }


   private void initialiseApplicationCommands()
   {
      commandExecutors.put(FeedbactoryAvailableCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments)
         {
            processFeedbactoryAvailableCommand(arguments);
         }
      });

      commandExecutors.put(RequestCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments)
         {
            processRequestCommand(arguments);
         }
      });

      commandExecutors.put(BufferPoolCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments)
         {
            processBufferPoolCommand(arguments);
         }
      });
   }


   private void initialiseClientVersionCommand()
   {
      commandExecutors.put(ClientVersionCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments)
         {
            processClientVersionCommand(arguments);
         }
      });
   }


   private void initialiseBroadcastMessageCommand()
   {
      commandExecutors.put(BroadcastMessageCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments)
         {
            processBroadcastMessageCommand(arguments);
         }
      });
   }


   private void initialiseSessionCommands()
   {
      commandExecutors.put(SessionCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments)
         {
            processSessionCommand(arguments);
         }
      });
   }


   private void initialiseAccountNetworkCommand()
   {
      commandExecutors.put(IPAuthenticationCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments) throws UnknownHostException
         {
            processIPAuthenticationCommand(arguments);
         }
      });
   }


   private void initialiseAccountCommands()
   {
      commandExecutors.put(UserAccountCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments) throws IOException
         {
            processAccountCommand(arguments);
         }
      });
   }


   private void initialiseMailerCommands()
   {
      commandExecutors.put(MailerCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments)
         {
            processMailerCommand(arguments);
         }
      });
   }


   private void initialiseFeedbackCommands()
   {
      commandExecutors.put(FeedbackCommand, new CommandExecutor()
      {
         @Override
         final public void executeCommand(final String[] arguments)
         {
            processFeedbackCommand(arguments);
         }
      });
   }


   private void checkMinimumCompatibleClientVersion()
   {
      /* See the ClientVersionCompatibilityManager class for a detailed rundown of this.
       * A warning is displayed here via stdout rather than via logging in ClientVersionCompatibilityManager because the logging in
       * the test and live environments is channelled straight to files. And although not an error, this is a very important warning!
       */
      final NetworkToApplicationGateway networkGateway = operationsManager.getNetworkToApplicationGateway();
      if (networkGateway.getMinimumCompatibleClientVersion() > networkGateway.getMinimumAcceptedClientVersion())
      {
         final StringBuilder warningMessageBuilder = new StringBuilder(200);
         warningMessageBuilder.append("Warning: The minimum compatible client version in the codebase is higher than the minimum accepted client version ");
         warningMessageBuilder.append("read from the configuration file!\n");
         warningMessageBuilder.append("Does the minimum accepted client version need to be updated before the server is started?\n");

         warningMessageBuilder.append("   Minimum compatible version (codebase): ");
         warningMessageBuilder.append(networkGateway.getMinimumCompatibleClientVersion());
         warningMessageBuilder.append('\n');
         warningMessageBuilder.append("   Minimum accepted version (configuration file): ");
         warningMessageBuilder.append(networkGateway.getMinimumAcceptedClientVersion());
         warningMessageBuilder.append("\n");

         System.out.println(warningMessageBuilder.toString());
      }
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


   static private String pluralise(final long amount)
   {
      return (amount == 1) ? "" : "s";
   }


   private FeedbactoryUserAccountView getUserAccountFrom(final String argument)
   {
      if (argument.indexOf('@') != -1)
         return operationsManager.getUserAccountManager().getAccountView(argument);
      else
         return operationsManager.getUserAccountManager().getAccountView(Integer.parseInt(argument));
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


   static private String getFormattedTimeSpan(final long durationMilliseconds)
   {
      final Formatter formatter = new Formatter();

      long timeSpanRemainder = durationMilliseconds / 1000;
      final long timeSpanSeconds = (timeSpanRemainder % 60);
      timeSpanRemainder /= 60;
      final long timeSpanMinutes = (timeSpanRemainder % 60);
      timeSpanRemainder /= 60;
      final long timeSpanHours = (timeSpanRemainder % 24);
      timeSpanRemainder /= 24;
      final long timeSpanDays = timeSpanRemainder;

      boolean previousFieldDisplayed = false;

      if (timeSpanDays != 0)
      {
         formatter.format("%d day%s, ", timeSpanDays, pluralise(timeSpanDays));
         previousFieldDisplayed = true;
      }

      if (previousFieldDisplayed || (timeSpanHours != 0))
      {
         formatter.format("%d hour%s, ", timeSpanHours, pluralise(timeSpanHours));
         previousFieldDisplayed = true;
      }

      if (previousFieldDisplayed || (timeSpanMinutes != 0))
         formatter.format("%d minute%s, ", timeSpanMinutes, pluralise(timeSpanMinutes));

      formatter.format("%d second%s", timeSpanSeconds, pluralise(timeSpanSeconds));

      return formatter.toString();
   }


   private void printMessageTypes()
   {
      System.out.println("Message type values:");
      System.out.println();

      final String messageTypeFormat = "%-5d%s%n";

      for (final MessageType messageType : MessageType.values())
         System.out.format(messageTypeFormat, messageType.value, messageType);
   }


   private Message parseMessageArguments(final String[] arguments)
   {
      final byte messageTypeValue = Byte.parseByte(arguments[0]);
      final MessageType messageType = MessageType.fromValue(messageTypeValue);
      if (messageType == null)
         throw new IllegalArgumentException("Invalid message type value: " + messageTypeValue);

      if (arguments.length == 1)
      {
         if (messageType == MessageType.NoMessage)
            return Message.NoMessage;
         else
            throw new IllegalArgumentException("Message cannot be empty for type: " + messageType);
      }
      else if (messageType != MessageType.NoMessage)
         return new Message(messageType, reassembleMessageString(trimArguments(arguments)));
      else
         throw new IllegalArgumentException("Invalid message type for non-empty message.");
   }


   private String reassembleMessageString(final String[] arguments)
   {
      final StringBuilder stringBuilder = new StringBuilder();

      String newlineConvertedString;

      for (int argumentIndex = 0; argumentIndex < arguments.length; argumentIndex ++)
      {
         newlineConvertedString = arguments[argumentIndex].replace("\\n", "\n");
         stringBuilder.append(newlineConvertedString);
         if (argumentIndex != (arguments.length - 1))
            stringBuilder.append(' ');
      }

      return stringBuilder.toString();
   }


   private void printTimestampedMessage(final TimestampedMessage timestampedMessage)
   {
      printMessage(timestampedMessage.message);

      System.out.format("Last updated: %s%n", getFormattedDateAndTime(timestampedMessage.messageTime));
   }


   private void printMessage(final Message message)
   {
      if (message.messageType == MessageType.NoMessage)
         System.out.println("No message");
      else
         System.out.format("[%s]: %s%n", message.messageType, message.message);
   }


   private void printFormattedServerStatus(final String labelValueFormat)
   {
      final ServerState serverState = operationsManager.getServerController().getServerState();

      System.out.format(labelValueFormat, "Server status:", serverState);
      if ((serverState == ServerState.Started) || (serverState == ServerState.Shutdown))
      {
         final long serverStartTime = operationsManager.getServerController().getServerStartTime();

         System.out.format(labelValueFormat, "Start time:", getFormattedDateAndTime(serverStartTime));

         if (serverState == ServerState.Shutdown)
         {
            final long serverShutdownTime = operationsManager.getServerController().getServerShutdownTime();

            System.out.format(labelValueFormat, "Up time:", getFormattedTimeSpan(serverShutdownTime - serverStartTime));
            System.out.format(labelValueFormat, "Shutdown time:", getFormattedDateAndTime(serverShutdownTime));
         }
         else
            System.out.format(labelValueFormat, "Up time:", getFormattedTimeSpan(TimeCache.getCurrentTimeMilliseconds() - serverStartTime));
      }
   }


   private void printFormattedFeedbackCategories(final List<FeedbackCategory> feedbackCategories, final String generalFormat, final String numericFormat)
   {
      final Comparator<FeedbackCategory> sortByCategoryValueComparator = new Comparator<FeedbackCategory>()
      {
         @Override
         final public int compare(final FeedbackCategory categoryOne, final FeedbackCategory categoryTwo)
         {
            if (categoryOne.value < categoryTwo.value)
               return -1;
            else if (categoryOne.value > categoryTwo.value)
               return 1;
            else
               return 0;
         }
      };

      Collections.sort(feedbackCategories, sortByCategoryValueComparator);

      System.out.println("Registered feedback categories:");
      System.out.println();
      System.out.format(generalFormat, "Feedback category", "Value");

      for (final FeedbackCategory category : feedbackCategories)
         System.out.format(numericFormat, category, category.value);
   }


   /****************************************************************************
    *
    ***************************************************************************/


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
         case BatchCommand:
            return processBatchCommand(arguments);
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


   /****************************************************************************
    *
    ***************************************************************************/


   private boolean processBatchCommand(final String[] arguments) throws IOException, InterruptedException
   {
      if (arguments.length != 1)
      {
         System.out.println("Invalid command switch.");
         return true;
      }

      final Path batchFileScriptPath = BatchScriptPath.resolve(arguments[0] + BatchScriptExtension);
      final List<String> batchFileLines = Files.readAllLines(batchFileScriptPath, StandardCharsets.UTF_8);

      for (final String commandLine : batchFileLines)
      {
         if (! processCommandLine(commandLine))
            return false;
      }

      return true;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processStatusCommand(final String[] arguments) throws IOException
   {
      if (arguments.length == 0)
         displayStatus();
      else
         System.out.println("Invalid command switch.");
   }


   private void displayStatus() throws IOException
   {
      final LoggerManager loggerManager = operationsManager.getLoggerManager();

      final String labelValueFormat = "%-30.30s%s%n";

      final long serverTime = TimeCache.getCurrentTimeMilliseconds();

      System.out.println();
      System.out.format(labelValueFormat, "Server time:", getFormattedDateAndTime(serverTime));
      System.out.format(labelValueFormat, "", serverTime);

      System.out.format(labelValueFormat, "General event log level:", loggerManager.getSystemEventLevel());
      System.out.format(labelValueFormat, "Security event log level:", loggerManager.getSecurityEventLevel());

      final Path dataFilePath = operationsManager.getActiveCheckpointPath();

      if (dataFilePath != null)
      {
         System.out.format(labelValueFormat, "Active data path:", dataFilePath);
         System.out.format(labelValueFormat, "Last modified:", getFormattedDateAndTime(Files.getLastModifiedTime(dataFilePath).toMillis()));
      }
      else
         System.out.format(labelValueFormat, "Active checkpoint path:", "None");

      System.out.format(labelValueFormat, "Auto save status:", (operationsManager.isAutoSaveStarted() ? "Enabled" : "Disabled"));

      System.out.format(labelValueFormat, "Housekeeping status:", (operationsManager.getHousekeepingManager().isStarted() ? "Enabled" : "Disabled"));

      printFormattedServerStatus(labelValueFormat);

      System.out.format(labelValueFormat, "Feedbactory status:", (operationsManager.getRequestManager().isApplicationAvailable() ? "Available" : "Not available"));
      System.out.println();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processEventLogCommand(final String[] arguments)
   {
      if (arguments.length == 0)
         displayEventLogMetrics();
      else if (arguments[0].equals(SystemEventLogCommandSwitch))
         processSystemEventLogCommandSwitch(trimArguments(arguments));
      else if (arguments[0].equals(SecurityEventLogCommandSwitch))
         processSecurityEventLogCommandSwitch(trimArguments(arguments));
      else if (arguments[0].equals(SMSEventLogCommandSwitch))
         processSMSEventLogCommandSwitch(trimArguments(arguments));
      else
         System.out.println("Invalid command switch.");
   }


   private void displayEventLogMetrics()
   {
      final LoggerManager loggerManager = operationsManager.getLoggerManager();

      final EventLogMetrics eventLogMetrics = loggerManager.getMetrics();

      final String generalLabelValueFormat = "%-35.35s%s%n";
      final String numericLabelValueFormat = "%-35.35s%d%n";

      System.out.println();
      System.out.format(generalLabelValueFormat, "System event log level:", loggerManager.getSystemEventLevel());
      System.out.format(generalLabelValueFormat, "Security event log level:", loggerManager.getSecurityEventLevel());
      System.out.format(generalLabelValueFormat, "System event log SMS level:", loggerManager.getSystemEventSMSLevel());
      System.out.format(generalLabelValueFormat, "Security event log SMS level:", loggerManager.getSecurityEventSMSLevel());
      System.out.format(generalLabelValueFormat, "SMS last sent:", getFormattedDateAndTime(loggerManager.resetSMSLastSentTime()));
      System.out.println();

      System.out.println("System log events:");
      for (final Entry<SystemLogLevel, Integer> systemEvent : eventLogMetrics.systemEventsLogged.entrySet())
         System.out.format(numericLabelValueFormat, systemEvent.getKey() + " events:", systemEvent.getValue().intValue());
      System.out.println();

      System.out.println("Security log events:");
      for (final Entry<SecurityLogLevel, Integer> securityEvent : eventLogMetrics.securityEventsLogged.entrySet())
         System.out.format(numericLabelValueFormat, securityEvent.getKey() + " events:", securityEvent.getValue().intValue());
      System.out.println();
   }


   private void processSystemEventLogCommandSwitch(final String[] arguments)
   {
      if (arguments.length == 0)
         displaySystemEventLogStatus();
      else if (arguments.length == 1)
         setSystemEventLogLevel(SystemLogLevel.fromValue(Byte.parseByte(arguments[0])));
      else
         System.out.println("Invalid command switch.");
   }


   private void displaySystemEventLogStatus()
   {
      System.out.println();
      System.out.format("System event log level: %s%n", operationsManager.getLoggerManager().getSystemEventLevel());
      System.out.println();
      System.out.println("Possible values:");

      final String logLevelFormat = "%-5d%s%n";

      for (final SystemLogLevel level : SystemLogLevel.values())
         System.out.format(logLevelFormat, level.value, level);

      System.out.println();
   }


   private void setSystemEventLogLevel(final SystemLogLevel systemEventLogLevel)
   {
      if (systemEventLogLevel != null)
      {
         operationsManager.getLoggerManager().setSystemEventLevel(systemEventLogLevel);
         System.out.format("New system event log level: %s%n", systemEventLogLevel);
      }
      else
         System.out.println("Invalid system event log level.");
   }


   private void processSecurityEventLogCommandSwitch(final String[] arguments)
   {
      if (arguments.length == 0)
         displaySecurityEventLogStatus();
      else if (arguments.length == 1)
         setSecurityEventLogLevel(SecurityLogLevel.fromValue(Byte.parseByte(arguments[0])));
      else
         System.out.println("Invalid command switch.");
   }


   private void displaySecurityEventLogStatus()
   {
      final String logLevelFormat = "%-5d%s%n";

      System.out.println();
      System.out.format("Security event log level: %s%n", operationsManager.getLoggerManager().getSecurityEventLevel());
      System.out.println();
      System.out.println("Possible values:");

      for (final SecurityLogLevel level : SecurityLogLevel.values())
         System.out.format(logLevelFormat, level.value, level);

      System.out.println();
   }


   private void setSecurityEventLogLevel(final SecurityLogLevel securityEventLogLevel)
   {
      if (securityEventLogLevel != null)
      {
         operationsManager.getLoggerManager().setSecurityEventLevel(securityEventLogLevel);
         System.out.format("New security event log level: %s%n", securityEventLogLevel);
      }
      else
         System.out.println("Invalid security event log level.");
   }


   private void processSMSEventLogCommandSwitch(final String[] arguments)
   {
      if (arguments.length == 0)
         displayEventLogSMSStatus();
      else if ((arguments.length == 1) && arguments[0].equals(ResetCommandSwitch))
         resetSMSLastSendTime();
      else if ((arguments.length == 1) && arguments[0].equals(TestCommandSwitch))
         sendTestSystemEventSMSAlert();
      else
         System.out.println("Invalid command switch.");
   }


   private void displayEventLogSMSStatus()
   {
      final LoggerManager loggerManager = operationsManager.getLoggerManager();
      final String generalLabelValueFormat = "%-35.35s%s%n";

      System.out.println();
      System.out.format(generalLabelValueFormat, "System event log SMS level:", loggerManager.getSystemEventSMSLevel());
      System.out.format(generalLabelValueFormat, "Security event log SMS level:", loggerManager.getSecurityEventSMSLevel());
      System.out.format(generalLabelValueFormat, "SMS last sent:", getFormattedDateAndTime(loggerManager.resetSMSLastSentTime()));
      System.out.println();
   }


   private void resetSMSLastSendTime()
   {
      operationsManager.getLoggerManager().resetLastSMSSendTime();
      System.out.println("SMS last sent time has been reset.");
   }


   private void sendTestSystemEventSMSAlert()
   {
      final SystemEvent systemEvent = new SystemEvent(SystemLogLevel.None, getClass(), "Test SMS");
      operationsManager.getLoggerManager().sendTestSystemEventSMSAlert("Test SMS sent from Feedbactory console", systemEvent);
      System.out.println("Test SMS sent.");
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processSaveCheckpointCommand(final String[] arguments) throws IOException
   {
      if ((arguments.length == 1) && arguments[0].equals(PrimaryCheckpointCommandSwitch))
         savePrimaryCheckpoint();
      else if ((arguments.length == 2) && arguments[0].equals(SnapshotCheckpointCommandSwitch))
         saveSnapshotCheckpoint(arguments[1]);
      else
         System.out.println("Invalid command switch.");
   }


   private void savePrimaryCheckpoint() throws IOException
   {
      if (operationsManager.isAttachedToPrimaryCheckpoint())
      {
         System.out.println("Saving primary checkpoint...");
         operationsManager.savePrimaryCheckpoint();
         System.out.println("Primary checkpoint saved.");
      }
      else
         System.out.println("Not attached to the primary data.");
   }


   private void saveSnapshotCheckpoint(final String snapshotName) throws IOException
   {
      System.out.format("Saving snapshot '%s' checkpoint...%n", snapshotName);
      operationsManager.saveSnapshotCheckpoint(snapshotName);
      System.out.println("Snapshot checkpoint saved.");
   }


   private void processLoadCheckpointCommand(final String[] arguments) throws IOException
   {
      if ((arguments.length == 1) && arguments[0].equals(PrimaryCheckpointCommandSwitch))
         loadPrimaryCheckpoint();
      else if ((arguments.length == 2) && arguments[0].equals(AutoSaveCheckpointCommandSwitch))
         loadAutoSaveCheckpoint(Integer.parseInt(arguments[1]));
      else if ((arguments.length == 2) && arguments[0].equals(SnapshotCheckpointCommandSwitch))
         loadSnapshotCheckpoint(arguments[1]);
      else
         System.out.println("Invalid command switch.");
   }


   private boolean validateStateForLoading()
   {
      if (operationsManager.isAutoSaveStarted())
      {
         System.out.println("Cannot load from a checkpoint while auto save is enabled.");
         return false;
      }
      else if (operationsManager.getHousekeepingManager().isStarted())
      {
         System.out.println("Cannot load from a checkpoint while housekeeping is enabled.");
         return false;
      }
      else if (operationsManager.getServerController().isServerStarted())
      {
         System.out.println("Cannot load from a checkpoint while the server is active.");
         return false;
      }

      return true;
   }


   private void loadPrimaryCheckpoint() throws IOException
   {
      if (validateStateForLoading())
      {
         System.out.println("Loading primary checkpoint...");
         operationsManager.loadPrimaryCheckpoint();
         System.out.println("Primary checkpoint loaded.");
      }
   }


   private void loadAutoSaveCheckpoint(final int autoSaveNumber) throws IOException
   {
      if (validateStateForLoading())
      {
         System.out.format("Loading auto save '%d' checkpoint...%n", autoSaveNumber);
         operationsManager.loadAutoSaveCheckpoint(autoSaveNumber);
         System.out.println("Auto save checkpoint loaded.");
      }
   }


   private void loadSnapshotCheckpoint(final String snapshotName) throws IOException
   {
      if (validateStateForLoading())
      {
         System.out.format("Loading snapshot '%s' checkpoint...%n", snapshotName);
         operationsManager.loadSnapshotCheckpoint(snapshotName);
         System.out.println("Snapshot checkpoint loaded.");
      }
   }


   private void processAutoSaveCommand(final String[] arguments) throws InterruptedException
   {
      if (arguments.length == 0)
         displayAutoSaveMetrics();
      else if ((arguments.length == 1) && arguments[0].equals(OnCommandSwitch))
         enableAutoSave();
      else if ((arguments.length == 1) && arguments[0].equals(OffCommandSwitch))
         disableAutoSave();
      else
         System.out.println("Invalid command switch.");
   }


   private void displayAutoSaveMetrics()
   {
      final AutoSaveMetrics autoSaveMetrics = operationsManager.getAutoSaveMetrics();

      final String labelValue = "%-35.35s";
      final String generalLabelValueFormat = labelValue + "%s%n";
      final String numericLabelValueFormat = labelValue + "%d%n";

      System.out.println();
      System.out.format(generalLabelValueFormat, "Auto save status:", (autoSaveMetrics.isAutoSaveActive ? "Enabled" : "Disabled"));
      System.out.format(labelValue + "%d minute%s%n", "Frequency:", autoSaveMetrics.autoSaveFrequencyMinutes, pluralise(autoSaveMetrics.autoSaveFrequencyMinutes));
      System.out.format(numericLabelValueFormat, "Saves this session:", autoSaveMetrics.autoSavesThisSession);
      System.out.format(numericLabelValueFormat, "Last save number:", autoSaveMetrics.lastAutoSaveNumber);
      System.out.format(generalLabelValueFormat, "Last save start time:", getFormattedDateAndTime(autoSaveMetrics.lastAutoSaveStartTime));

      if (autoSaveMetrics.lastAutoSaveStartTime != FeedbactoryConstants.NoTime)
         System.out.format(generalLabelValueFormat, "Last save duration:", getFormattedTimeSpan(autoSaveMetrics.lastAutoSaveFinishTime - autoSaveMetrics.lastAutoSaveStartTime));

      System.out.println();
   }


   private void enableAutoSave()
   {
      if (operationsManager.isAutoSaveStarted())
         System.out.println("Auto save is already enabled.");
      else if (operationsManager.isAttachedToPrimaryCheckpoint())
      {
         System.out.println("Starting auto save...");
         operationsManager.startAutoSave();
         System.out.println("Auto save enabled.");
      }
      else
         System.out.println("Auto save can only be enabled when the primary checkpoint is loaded.");
   }


   private void disableAutoSave() throws InterruptedException
   {
      if (operationsManager.isAutoSaveStarted())
      {
         System.out.println("Shutting down auto save...");
         operationsManager.shutdownAutoSave();
      }

      System.out.println("Auto save disabled.");
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processHousekeepingCommand(final String[] arguments) throws InterruptedException
   {
      if (arguments.length == 0)
         displayHousekeepingStatus();
      else if ((arguments.length == 1) && arguments[0].equals(OnCommandSwitch))
         enableHousekeeping();
      else if ((arguments.length == 1) && arguments[0].equals(OffCommandSwitch))
         disableHousekeeping();
      else
         System.out.println("Invalid command switch.");
   }


   private void displayHousekeepingStatus()
   {
      if (operationsManager.getHousekeepingManager().isStarted())
         System.out.println("Housekeeping is enabled.");
      else
         System.out.println("Housekeeping is disabled.");
   }


   private void enableHousekeeping()
   {
      if (operationsManager.getHousekeepingManager().isStarted())
         System.out.println("Housekeeping is already enabled.");
      else
      {
         System.out.println("Starting housekeeping...");
         operationsManager.getHousekeepingManager().start();
         System.out.println("Housekeeping enabled.");
      }
   }


   private void disableHousekeeping() throws InterruptedException
   {
      if (operationsManager.getHousekeepingManager().isStarted())
      {
         System.out.println("Shutting down housekeeping...");
         operationsManager.getHousekeepingManager().shutdown();
      }

      System.out.println("Housekeeping disabled.");
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processServerCommand(final String[] arguments) throws IOException, InterruptedException
   {
      if (arguments.length == 0)
         displayServerStatus();
      else
      {
         final String[] trimmedArguments = trimArguments(arguments);

         switch (arguments[0])
         {
            case ServerBacklogCommandSwitch:
               processServerBacklogCommandSwitch(trimmedArguments);
               break;

            case ServerReceiveBufferCommandSwitch:
               processServerReceiveBufferCommandSwitch(trimmedArguments);
               break;

            case ServerConnectionLimitsCommandSwitch:
               processServerConnectionCommandSwitch(trimmedArguments);
               break;

            case ServerStartCommandSwitch:
               processServerStartCommandSwitch(trimmedArguments);
               break;

            case ServerShutdownCommandSwitch:
               processServerShutdownCommandSwitch(trimmedArguments);
               break;

            default:
               System.out.println("Invalid command switch.");
         }
      }
   }


   private void displayServerStatus()
   {
      final String labelFormat = "%-35.35s";
      final String generalLabelValueFormat = labelFormat + "%s%n";
      final String numericLabelValueFormat = labelFormat + "%d%n";

      System.out.println();
      printFormattedServerStatus(generalLabelValueFormat);

      final ServerMetrics serverMetrics = operationsManager.getServerController().getMetrics();

      System.out.println();
      System.out.format(numericLabelValueFormat, "Connection backlog size:", serverMetrics.connectionBacklogSize);
      System.out.format(numericLabelValueFormat, "Receive buffer size:", serverMetrics.receiveBufferSize);
      System.out.format(numericLabelValueFormat, "Connections busy threshold:", serverMetrics.connectionsBusyThreshold);
      System.out.format(numericLabelValueFormat, "Maximum connections permitted:", serverMetrics.maximumConnectionsPermitted);
      System.out.println();
      System.out.format(numericLabelValueFormat, "Active connections:", serverMetrics.activeConnections);
      System.out.format(numericLabelValueFormat, "Most connections:", serverMetrics.highestRecordedConnections);
      System.out.format(generalLabelValueFormat, "Recorded at:", getFormattedDateAndTime(serverMetrics.highestRecordConnectionsTime));
      System.out.println();
      System.out.println("IO thread pool:");
      System.out.println();
      System.out.format(numericLabelValueFormat, "Queue size:", serverMetrics.ioThreadPoolQueueSize);
      System.out.format(numericLabelValueFormat, "Core thread count:", serverMetrics.ioThreadPoolCoreThreadCount);
      System.out.format(numericLabelValueFormat, "Maximum thread count:", serverMetrics.ioThreadPoolMaximumThreadCount);
      System.out.format(numericLabelValueFormat, "Hot thread count:", serverMetrics.ioThreadPoolHotThreadCount);
      System.out.format(numericLabelValueFormat, "Active task count:", serverMetrics.ioThreadPoolActiveTaskCount);
      System.out.format(numericLabelValueFormat, "Tasks submitted:", serverMetrics.ioThreadPoolTasksSubmitted);
      System.out.format(numericLabelValueFormat, "Tasks completed:", serverMetrics.ioThreadPoolTasksCompleted);
      System.out.println();
   }


   private void processServerBacklogCommandSwitch(final String[] arguments)
   {
      if (arguments.length == 0)
         System.out.format("Server connection backlog size: %d%n", operationsManager.getServerController().getConnectionBacklogSize());
      else if (arguments.length == 1)
         setServerBacklogSize(Integer.parseInt(arguments[0]));
      else
         System.out.println("Invalid command switch.");
   }


   private void setServerBacklogSize(final int connectionBacklogSize)
   {
      if (connectionBacklogSize < ApplicationServerController.MinimumConnectionBacklogSize)
         System.out.format("Connection backlog size must be greater than %d connections.%n", ApplicationServerController.MinimumConnectionBacklogSize);
      else if (operationsManager.getServerController().isServerStarted())
         System.out.println("Cannot update the connection backlog size while the server is active.");
      else
      {
         operationsManager.getServerController().setConnectionBacklogSize(connectionBacklogSize);
         System.out.println("Server connection backlog size changed.");
      }
   }


   private void processServerReceiveBufferCommandSwitch(final String[] arguments)
   {
      if (arguments.length == 0)
         System.out.format("Server receive buffer size: %d%n", operationsManager.getServerController().getReceiveBufferSize());
      else if (arguments.length == 1)
         setServerReceiveBufferSize(Integer.parseInt(arguments[0]));
      else
         System.out.println("Invalid command switch.");
   }


   private void setServerReceiveBufferSize(final int receiveBufferSize)
   {
      if (receiveBufferSize < ApplicationServerController.MinimumReceiveBufferSize)
         System.out.format("Receive buffer size must be greater than %d byte%s.%n", ApplicationServerController.MinimumReceiveBufferSize,
                           pluralise(ApplicationServerController.MinimumReceiveBufferSize));
      else if (operationsManager.getServerController().isServerStarted())
         System.out.println("Cannot update the receive buffer size while the server is active.");
      else
      {
         operationsManager.getServerController().setReceiveBufferSize(receiveBufferSize);
         System.out.println("Server receive buffer size changed.");
      }
   }


   private void processServerConnectionCommandSwitch(final String[] arguments)
   {
      if (arguments.length == 0)
         displayConnectionThresholdsStatus();
      else if (arguments.length == 2)
         setConnectionThresholds(Integer.parseInt(arguments[0]), Integer.parseInt(arguments[1]));
      else
         System.out.println("Invalid command switch.");
   }


   private void displayConnectionThresholdsStatus()
   {
      System.out.format("Server connections busy threshold is %d, maximum connections permitted is %d%n",
                        operationsManager.getServerController().getActiveConnectionsBusyThreshold(),
                        operationsManager.getServerController().getMaximumConnectionsPermitted());
   }


   private void setConnectionThresholds(final int connectionsBusyThreshold, final int maximumPermittedConnections)
   {
      if (connectionsBusyThreshold < 0)
         System.out.println("Busy threshold cannot be less than zero.");
      else if (maximumPermittedConnections < 0)
         System.out.println("Maximum connections permitted cannot be less than zero.");
      else if (connectionsBusyThreshold < maximumPermittedConnections)
         System.out.println("Maximum connections permitted cannot be less than the busy threshold.");
      else
      {
         operationsManager.getServerController().setActiveConnectionLimits(connectionsBusyThreshold, maximumPermittedConnections);
         System.out.println("Server connection thresholds have been changed.");
      }
   }


   private void processServerStartCommandSwitch(final String[] arguments) throws IOException
   {
      if (arguments.length == 0)
         startServer(FeedbactoryNetworkConstants.DefaultPortNumber);
      else if (arguments.length == 1)
         startServer(Integer.parseInt(arguments[0]));
      else
         System.out.println("Invalid command switch.");
   }


   private void startServer(final int portNumber) throws IOException
   {
      if ((portNumber < 0) || (portNumber > 65535))
      {
         System.out.println("Server port number must be between 0 and 65535.");
         return;
      }

      if (! operationsManager.getServerController().isServerStarted())
      {
         System.out.println("Starting server...");
         operationsManager.getServerController().startServer(portNumber);
         System.out.println("Server started.");
      }
      else
         System.out.println("An instance of the server is already running.");
   }


   private void processServerShutdownCommandSwitch(final String[] arguments) throws IOException, InterruptedException
   {
      if (arguments.length == 0)
         shutdownServer(DefaultServerShutdownTimeoutMilliseconds);
      else if (arguments.length == 1)
         shutdownServer(Integer.parseInt(arguments[0]) * 1000L);
      else
         System.out.println("Invalid command arguments.");
   }


   private void shutdownServer(final long shutdownTimeoutMilliseconds) throws IOException, InterruptedException
   {
      if (operationsManager.getServerController().isServerStarted())
      {
         System.out.println("Shutting down the server... ");
         operationsManager.getServerController().shutdownServer(shutdownTimeoutMilliseconds);
         System.out.println("Server shut down.");
      }
      else
         System.out.println("The server is not running.");
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processIPMonitorCommand(final String[] arguments) throws UnknownHostException
   {
      if (arguments.length == 0)
         displayAllIPMonitorMetrics();
      else
      {
         final String[] trimmedArguments = trimArguments(arguments);

         switch (arguments[0])
         {
            case IPMonitorBlockedCommandSwitch:
               processTemporarilyBlockedIPAddressesCommandSwitch(trimmedArguments);
               break;

            case IPMonitorBlacklistedIPCommandSwitch:
               processBlacklistedIPAddressCommand(trimmedArguments);
               break;

            case IPMonitorBlacklistIPCommandSwitch:
               processBlacklistIPAddressCommand(trimmedArguments);
               break;

            case IPMonitorParoleIPCommandSwitch:
               processParoleIPAddressCommand(trimmedArguments);
               break;

            default:
               displayIPAddressMonitorMetrics(InetAddress.getByName(arguments[0]));
         }
      }
   }


   private void displayAllIPMonitorMetrics()
   {
      final IPMonitorMetrics ipMonitorMetrics = operationsManager.getRequestMonitor().getMetrics();

      final int totalRequestsThisMonitorPeriod = ipMonitorMetrics.totalLegitimateRequestsThisMonitorPeriod +
                                                 ipMonitorMetrics.totalErroneousRequestsThisMonitorPeriod +
                                                 ipMonitorMetrics.totalDeniedRequestsThisMonitorPeriod;

      final long minutesThisMonitorPeriod = (TimeCache.getCurrentTimeMilliseconds() - ipMonitorMetrics.currentMonitorPeriodStartTime) / 60000;

      final long averageRequestsPerMinute;
      if (minutesThisMonitorPeriod > 0)
         averageRequestsPerMinute = totalRequestsThisMonitorPeriod / minutesThisMonitorPeriod;
      else
         averageRequestsPerMinute = 0;

      final int averageRequestsPerIPAddress;
      if (ipMonitorMetrics.totalIPAddressesTracking > 0)
         averageRequestsPerIPAddress = (totalRequestsThisMonitorPeriod / ipMonitorMetrics.totalIPAddressesTracking);
      else
         averageRequestsPerIPAddress = 0;

      final String labelFormat = "%-37.37s";
      final String generalLabelValueFormat = labelFormat + "%s%n";
      final String numericLabelValueFormat = labelFormat + "%d%n";

      System.out.println();
      System.out.format(generalLabelValueFormat, "Monitor housekeeping status:", (ipMonitorMetrics.isHousekeepingEnabled ? "Enabled" : "Disabled"));
      System.out.format(labelFormat + "%d minute%s%n", "Monitor period:", ipMonitorMetrics.monitorFrequencyMinutes, pluralise(ipMonitorMetrics.monitorFrequencyMinutes));
      System.out.format(generalLabelValueFormat, "Current period start time:", getFormattedDateAndTime(ipMonitorMetrics.currentMonitorPeriodStartTime));
      System.out.format(generalLabelValueFormat, "Current period duration:", getFormattedTimeSpan(TimeCache.getCurrentTimeMilliseconds() - ipMonitorMetrics.currentMonitorPeriodStartTime));
      System.out.println();
      System.out.format(numericLabelValueFormat, "Spam requests block threshold:", ipMonitorMetrics.spamRequestsBlockThreshold);
      System.out.format(numericLabelValueFormat, "Erroneous requests block threshold:", ipMonitorMetrics.erroneousRequestsBlockThreshold);
      System.out.println();
      System.out.format(numericLabelValueFormat, "IP addresses tracking:", ipMonitorMetrics.totalIPAddressesTracking);
      System.out.format(numericLabelValueFormat, "New IP addresses this period:", ipMonitorMetrics.newIPAddressesThisMonitorPeriod);
      System.out.format(numericLabelValueFormat, "Temporarily blocked:", ipMonitorMetrics.temporarilyBlockedIPAddresses);
      System.out.format(numericLabelValueFormat, "Blacklisted:", ipMonitorMetrics.blacklistedIPAddresses);
      System.out.println();
      System.out.format(numericLabelValueFormat, "Total requests this period:", totalRequestsThisMonitorPeriod);
      System.out.format(numericLabelValueFormat, "Legitimate requests:", ipMonitorMetrics.totalLegitimateRequestsThisMonitorPeriod);
      System.out.format(numericLabelValueFormat, "Erroneous requests:", ipMonitorMetrics.totalErroneousRequestsThisMonitorPeriod);
      System.out.format(numericLabelValueFormat, "Denied requests:", ipMonitorMetrics.totalDeniedRequestsThisMonitorPeriod);
      System.out.format(numericLabelValueFormat, "Average requests per minute:", averageRequestsPerMinute);
      System.out.println();
      System.out.format(numericLabelValueFormat, "Average requests per IP address:", averageRequestsPerIPAddress);

      if (ipMonitorMetrics.busiestIPAddressThisMonitorPeriod != null)
         System.out.format(labelFormat + "%s (%d request%s)%n", "Busiest IP address this period:", ipMonitorMetrics.busiestIPAddressThisMonitorPeriod,
                           ipMonitorMetrics.busiestIPAddressRequestsThisMonitorPeriod, pluralise(ipMonitorMetrics.busiestIPAddressRequestsThisMonitorPeriod));
      else
         System.out.format(generalLabelValueFormat, "Busiest IP address this period:", "None");

      System.out.println();
   }


   private void processTemporarilyBlockedIPAddressesCommandSwitch(final String[] arguments)
   {
      if (arguments.length == 0)
         displayTemporarilyBlockedIPAddresses();
      else
         System.out.println("Invalid command arguments.");
   }


   private void displayTemporarilyBlockedIPAddresses()
   {
      final List<IPAddressRequestMonitor.BlockedIPAddressMetrics> blockedIPAddressMetricsList = operationsManager.getRequestMonitor().getTemporarilyBlockedMetrics();

      if (blockedIPAddressMetricsList.isEmpty())
         System.out.println("No temporarily blocked IP addresses.");
      else
      {
         System.out.println();
         displayBlockedIPAddresses(blockedIPAddressMetricsList);
         System.out.println();
         System.out.format("%d temporarily blocked IP address%s.%n%n", blockedIPAddressMetricsList.size(), (blockedIPAddressMetricsList.size() > 1) ? "es" : "");
      }
   }


   private void displayBlockedIPAddresses(final List<IPAddressRequestMonitor.BlockedIPAddressMetrics> blockedIPAddressMetricsList)
   {
      final Comparator<BlockedIPAddressMetrics> lastUpdatedComparator = new Comparator<BlockedIPAddressMetrics>()
      {
         @Override
         final public int compare(final BlockedIPAddressMetrics ipAddressOne, final BlockedIPAddressMetrics ipAddressTwo)
         {
            if (ipAddressOne.ipAddressStandingLastUpdated < ipAddressTwo.ipAddressStandingLastUpdated)
               return -1;
            else if (ipAddressOne.ipAddressStandingLastUpdated > ipAddressTwo.ipAddressStandingLastUpdated)
               return 1;
            else
               return 0;
         }
      };

      Collections.sort(blockedIPAddressMetricsList, lastUpdatedComparator);

      final String headingOutputFormat = "%-20.20s%-40.40s%s%n";
      final String rowOutputFormat = "%-20.20s%-40.40s%d%n";

      System.out.format(headingOutputFormat, "IP address", "Blocked time", "Denied requests");
      System.out.println();

      for (final BlockedIPAddressMetrics blockedIPAddressMetrics : blockedIPAddressMetricsList)
      {
         System.out.format(rowOutputFormat, blockedIPAddressMetrics.ipAddress, getFormattedDateAndTime(blockedIPAddressMetrics.ipAddressStandingLastUpdated),
                           blockedIPAddressMetrics.deniedRequestsThisMonitorPeriod);
      }
   }


   private void processBlacklistedIPAddressCommand(final String[] arguments) throws UnknownHostException
   {
      if (arguments.length == 0)
         displayBlacklistedIPAddresses();
      else
         System.out.println("Invalid command arguments.");
   }


   private void displayBlacklistedIPAddresses()
   {
      final List<IPAddressRequestMonitor.BlockedIPAddressMetrics> blacklistedIPAddressMetricsList = operationsManager.getRequestMonitor().getBlacklistedMetrics();

      if (blacklistedIPAddressMetricsList.isEmpty())
         System.out.println("No blacklisted IP addresses.");
      else
      {
         System.out.println();
         displayBlockedIPAddresses(blacklistedIPAddressMetricsList);
         System.out.println();
         System.out.format("%d blacklisted IP address%s.%n%n", blacklistedIPAddressMetricsList.size(), (blacklistedIPAddressMetricsList.size() > 1) ? "es" : "");
      }
   }


   private void processBlacklistIPAddressCommand(final String[] arguments) throws UnknownHostException
   {
      if (arguments.length == 1)
         blacklistIPAddress(InetAddress.getByName(arguments[0]));
      else
         System.out.println("Invalid command arguments.");
   }


   private void blacklistIPAddress(final InetAddress ipAddressToBlacklist)
   {
      final IPAddressStanding existingStanding = operationsManager.getRequestMonitor().getIPAddressStanding(ipAddressToBlacklist);

      if (existingStanding != IPAddressStanding.Blacklisted)
      {
         operationsManager.getRequestMonitor().setIPAddressBlacklisted(ipAddressToBlacklist);
         System.out.println("The IP address has been blacklisted.");
      }
      else
         System.out.println("The IP address is already blacklisted.");
   }


   private void processParoleIPAddressCommand(final String[] arguments) throws UnknownHostException
   {
      if (arguments.length == 1)
         paroleIPAddress(InetAddress.getByName(arguments[0]));
      else
         System.out.println("Invalid command arguments.");
   }


   private void paroleIPAddress(final InetAddress ipAddressToParole)
   {
      final IPAddressStanding existingStanding = operationsManager.getRequestMonitor().getIPAddressStanding(ipAddressToParole);

      if (existingStanding == IPAddressStanding.Blacklisted)
      {
         /* The result of setIPAddressParoled() cannot be null here to indicate a discarded/unknown record,
          * since the monitor housekeeping will never purge a blacklisted IP.
          */
         final IPAddressStanding newStanding = operationsManager.getRequestMonitor().setIPAddressParoled(ipAddressToParole);

         if (newStanding == IPAddressStanding.OK)
            System.out.println("The IP address has been paroled.");
         else if (newStanding == IPAddressStanding.TemporarilyBlocked)
            System.out.println("The IP address has been paroled but due to continued spam is still temporarily blocked.");
         else
            System.out.println("IP address parole failed for " + ipAddressToParole);
      }
      else
         System.out.println("The IP address is not blacklisted.");
   }


   private void displayIPAddressMonitorMetrics(final InetAddress ipAddress)
   {
      final IPAddressMetrics ipAddressMetrics = operationsManager.getRequestMonitor().getIPAddressMetrics(ipAddress);

      if (ipAddressMetrics != null)
      {
         final int requestsThisMonitorPeriod = ipAddressMetrics.legitimateRequestsThisMonitorPeriod +
                                               ipAddressMetrics.erroneousRequestsThisMonitorPeriod +
                                               ipAddressMetrics.deniedRequestsThisMonitorPeriod;

         final long currentMonitorPeriodStartTime = operationsManager.getRequestMonitor().getMonitorPeriodStartTime();

         final String labelFormat = "%-35.35s";
         final String generalLabelValueFormat = labelFormat + "%s%n";
         final String numericLabelValueFormat = labelFormat + "%d%n";

         System.out.println();
         System.out.format(generalLabelValueFormat, "Monitor period start time:", getFormattedDateAndTime(currentMonitorPeriodStartTime));
         System.out.format(generalLabelValueFormat, "Current period duration:", getFormattedTimeSpan(TimeCache.getCurrentTimeMilliseconds() - currentMonitorPeriodStartTime));
         System.out.println();
         System.out.format(generalLabelValueFormat, "IP address standing:", ipAddressMetrics.ipAddressStanding);
         System.out.format(generalLabelValueFormat, "Last updated:", getFormattedDateAndTime(ipAddressMetrics.ipAddressStandingLastUpdated));
         System.out.println();
         System.out.format(numericLabelValueFormat, "Requests this monitor period:", requestsThisMonitorPeriod);
         System.out.format(numericLabelValueFormat, "Legitimate requests:", ipAddressMetrics.legitimateRequestsThisMonitorPeriod);
         System.out.format(numericLabelValueFormat, "Erroneous requests:", ipAddressMetrics.erroneousRequestsThisMonitorPeriod);
         System.out.format(numericLabelValueFormat, "Denied requests:", ipAddressMetrics.deniedRequestsThisMonitorPeriod);
         System.out.println();
      }
      else
         System.out.println("No recent activity recorded for that IP address.");
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processFeedbactoryAvailableCommand(final String[] arguments)
   {
      if (arguments.length == 0)
         displayFeedbactoryAvailabilityStatus();
      else if ((arguments.length == 1) && arguments[0].equals(OnCommandSwitch))
         setFeedbactoryAvailable();
      else if (arguments[0].equals(OffCommandSwitch))
         setFeedbactoryNotAvailable((arguments.length == 1) ? Message.NoMessage : parseMessageArguments(Arrays.copyOfRange(arguments, 1, arguments.length)));
      else
         System.out.println("Invalid command arguments.");
   }


   private void displayFeedbactoryAvailabilityStatus()
   {
      System.out.println();

      if (operationsManager.getRequestManager().isApplicationAvailable())
         System.out.println("Feedbactory is available.");
      else
      {
         final Message notAvailableMessage = operationsManager.getRequestManager().getApplicationNotAvailableMessage();

         if (notAvailableMessage.messageType == MessageType.NoMessage)
            System.out.println("Feedbactory is not available and users will see the default message.");
         else
         {
            System.out.println("Feedbactory is not available and users will see this message:");
            printMessage(notAvailableMessage);
         }
      }

      System.out.format("Availability status last updated: %s%n", getFormattedDateAndTime(operationsManager.getRequestManager().getApplicationAvailabilityLastUpdated()));
      System.out.println();
   }


   private void setFeedbactoryAvailable()
   {
      if (operationsManager.getRequestManager().isApplicationAvailable())
         System.out.println("Feedbactory is already available.");
      else
      {
         operationsManager.getRequestManager().setApplicationAvailable(true);
         System.out.println("Feedbactory is available.");
      }
   }


   private void setFeedbactoryNotAvailable(final Message newNotAvailableMessage)
   {
      operationsManager.getRequestManager().setApplicationNotAvailableMessage(newNotAvailableMessage);

      if (operationsManager.getRequestManager().isApplicationAvailable())
         operationsManager.getRequestManager().setApplicationAvailable(false);

      if (newNotAvailableMessage.messageType == MessageType.NoMessage)
         System.out.println("Feedbactory status has been changed to not available and users will see the default message.");
      else
      {
         System.out.println("Feedbactory status has been changed to not available and users will see this message:");
         printMessage(newNotAvailableMessage);
      }
   }


   private void processRequestCommand(final String[] arguments)
   {
      if (arguments.length == 0)
         displayRequestMetrics();
      else
         System.out.println("Invalid command switch.");
   }


   private void displayRequestMetrics()
   {
      final RequestMetrics requestMetrics = operationsManager.getRequestManager().getRequestMetrics();

      final long totalRequests = requestMetrics.totalLegitimateRequests + requestMetrics.totalDeniedRequests + requestMetrics.totalErroneousRequests +
                                 requestMetrics.totalOverflowRequests + requestMetrics.totalTimeoutRequests + requestMetrics.totalReadRequestFailures;

      final long averageRequestSize;
      final long averageResponseSize;

      if (requestMetrics.totalLegitimateRequests > 0)
      {
         averageRequestSize = requestMetrics.totalLegitimateRequestBytes / requestMetrics.totalLegitimateRequests;
         averageResponseSize = requestMetrics.totalLegitimateResponseBytes / requestMetrics.totalLegitimateRequests;
      }
      else
      {
         averageRequestSize = 0;
         averageResponseSize = 0;
      }

      final String labelFormat = "%-35.35s";
      final String numericLabelValueFormat = labelFormat + "%d%n";

      System.out.println();
      System.out.format(labelFormat + "%d millisecond%s%n", "Request read timeout:", requestMetrics.requestReadTimeoutMilliseconds,
                        pluralise(requestMetrics.requestReadTimeoutMilliseconds));
      System.out.format(numericLabelValueFormat, "Maximum permitted request size:", requestMetrics.maximumAllowableClientRequestSize);
      System.out.println();
      System.out.format(numericLabelValueFormat, "Total requests:", totalRequests);
      System.out.format(numericLabelValueFormat, "Total legitimate requests:", requestMetrics.totalLegitimateRequests);
      System.out.format(numericLabelValueFormat, "Total denied requests:", requestMetrics.totalDeniedRequests);
      System.out.format(numericLabelValueFormat, "Total erroneous requests:", requestMetrics.totalErroneousRequests);
      System.out.format(numericLabelValueFormat, "Total overflow requests:", requestMetrics.totalOverflowRequests);
      System.out.format(numericLabelValueFormat, "Total timeout requests:", requestMetrics.totalTimeoutRequests);
      System.out.format(numericLabelValueFormat, "Total read request failures:", requestMetrics.totalReadRequestFailures);
      System.out.println();
      System.out.format(numericLabelValueFormat, "Total legitimate request bytes:", requestMetrics.totalLegitimateRequestBytes);
      System.out.format(numericLabelValueFormat, "Total legitimate response bytes:", requestMetrics.totalLegitimateResponseBytes);
      System.out.println();
      System.out.format(numericLabelValueFormat, "Average legitimate request size:", averageRequestSize);
      System.out.format(numericLabelValueFormat, "Average legitimate response size:", averageResponseSize);
      System.out.format(numericLabelValueFormat, "Largest legitimate request size:", requestMetrics.largestLegitimateRequestSize);
      System.out.format(numericLabelValueFormat, "Largest response size:", requestMetrics.largestResponseSize);
      System.out.println();
   }


   private void processBufferPoolCommand(final String[] arguments)
   {
      if (arguments.length == 0)
         displayBufferPoolMetrics();
      else
         System.out.println("Invalid command switch.");
   }


   private void displayBufferPoolMetrics()
   {
      final BufferPoolMetrics bufferPoolMetrics = operationsManager.getRequestManager().getBufferPoolMetrics();

      final String numericLabelValueFormat = "%-35.35s%d%n";

      System.out.println();
      System.out.println("Regular buffer pool:");
      System.out.format(numericLabelValueFormat, "Buffers allocated:", bufferPoolMetrics.bufferPoolCapacity);
      System.out.format(numericLabelValueFormat, "Allocation size per buffer:", bufferPoolMetrics.bufferPoolAllocationSizePerBuffer);
      System.out.format(numericLabelValueFormat, "Buffers available:", bufferPoolMetrics.bufferPoolBuffersAvailable);
      System.out.format(numericLabelValueFormat, "Pooled take requests:", bufferPoolMetrics.bufferPoolPooledTakeRequests);
      System.out.format(numericLabelValueFormat, "Allocated take requests:", bufferPoolMetrics.bufferPoolAllocatedTakeRequests);
      System.out.format(numericLabelValueFormat, "Accepted reclamations:", bufferPoolMetrics.bufferPoolAcceptedReclamations);
      System.out.format(numericLabelValueFormat, "Rejected reclamations:", bufferPoolMetrics.bufferPoolRejectedReclamations);
      System.out.println();
      System.out.println("Oversize buffer pool:");
      System.out.format(numericLabelValueFormat, "Buffers allocated:", bufferPoolMetrics.oversizeBufferPoolCapacity);
      System.out.format(numericLabelValueFormat, "Allocation size per buffer:", bufferPoolMetrics.oversizeBufferPoolAllocationSizePerBuffer);
      System.out.format(numericLabelValueFormat, "Buffers available:", bufferPoolMetrics.oversizeBufferPoolBuffersAvailable);
      System.out.format(numericLabelValueFormat, "Pooled take requests:", bufferPoolMetrics.oversizeBufferPoolPooledTakeRequests);
      System.out.format(numericLabelValueFormat, "Allocated take requests:", bufferPoolMetrics.oversizeBufferPoolAllocatedTakeRequests);
      System.out.format(numericLabelValueFormat, "Accepted reclamations:", bufferPoolMetrics.oversizeBufferPoolAcceptedReclamations);
      System.out.format(numericLabelValueFormat, "Rejected reclamations:", bufferPoolMetrics.oversizeBufferPoolRejectedReclamations);
      System.out.println();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processClientVersionCommand(final String[] arguments)
   {
      if (arguments.length == 0)
         displayClientCompatibility();
      else if (arguments.length == 1)
         setLatestClientVersion(Long.parseLong(arguments[0]), false);
      else if ((arguments.length == 2) && arguments[0].equals(ForceClientVersionCommandSwitch))
         setLatestClientVersion(Long.parseLong(arguments[1]), true);
      else
         System.out.println("Invalid command switch.");
   }


   private void displayClientCompatibility()
   {
      final String numericLabelValueFormat = "%-35.35s%d%n";

      System.out.println();
      System.out.format(numericLabelValueFormat, "Minimum compatible client version:", operationsManager.getNetworkToApplicationGateway().getMinimumCompatibleClientVersion());
      System.out.format(numericLabelValueFormat, "Minimum accepted client version:", operationsManager.getNetworkToApplicationGateway().getMinimumAcceptedClientVersion());
      System.out.format(numericLabelValueFormat, "Latest client version:", operationsManager.getNetworkToApplicationGateway().getLatestClientVersion());
      System.out.println();
   }


   private void setLatestClientVersion(final long newLatestClientVersion, final boolean forceUpdate)
   {
      if (newLatestClientVersion >= operationsManager.getNetworkToApplicationGateway().getMinimumCompatibleClientVersion())
      {
         operationsManager.getNetworkToApplicationGateway().setLatestClientVersion(newLatestClientVersion, forceUpdate);
         displayClientCompatibility();
         System.out.println("Client version(s) updated.");
      }
      else
         System.out.println("The latest client version cannot be set to a value lower than the codebase minimum compatible client version.");
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processBroadcastMessageCommand(final String[] arguments)
   {
      if (arguments.length == 0)
         displayBroadcastMessage();
      else
         setBroadcastMessage(arguments);
   }


   private void displayBroadcastMessage()
   {
      final TimestampedMessage broadcastMessage = operationsManager.getNetworkToApplicationGateway().getBroadcastMessage();

      System.out.println();
      System.out.println("Broadcast message:");
      printTimestampedMessage(broadcastMessage);
      System.out.println();
   }


   private void setBroadcastMessage(final String[] arguments)
   {
      final Message newBroadcastMessage = parseMessageArguments(arguments);

      operationsManager.getNetworkToApplicationGateway().setBroadcastMessage(newBroadcastMessage);

      System.out.println("Broadcast message has been set to:");
      printMessage(newBroadcastMessage);
      System.out.println();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processSessionCommand(final String[] arguments)
   {
      if (arguments.length == 0)
         displaySessionManagerMetrics();
      else if (arguments.length == 1)
         displayUserAccountSessions(arguments[0]);
      else
         System.out.println("Invalid command switch.");
   }


   private void displaySessionManagerMetrics()
   {
      final SessionManagerMetrics sessionMetrics = operationsManager.getNetworkToApplicationGateway().getSessionMetrics();

      final String labelFormat = "%-35.35s";
      final String generalLabelValueFormat = labelFormat + "%s%n";
      final String numericLabelValueFormat = labelFormat + "%d%n";

      System.out.println();
      System.out.format(generalLabelValueFormat, "Session housekeeping status:", (sessionMetrics.isHousekeepingEnabled ? "Enabled" : "Disabled"));
      System.out.format(labelFormat + "%d minute%s%n", "Frequency:", sessionMetrics.housekeepingFrequencyMinutes, pluralise(sessionMetrics.housekeepingFrequencyMinutes));
      System.out.format(generalLabelValueFormat, "Last run time:", getFormattedDateAndTime(sessionMetrics.housekeepingLastRunStartTime));
      System.out.println();
      System.out.format(labelFormat + "%d day%s%n", "Dormant session expiry:", sessionMetrics.dormantSessionExpiryTimeDays,
                        pluralise(sessionMetrics.dormantSessionExpiryTimeDays));
      System.out.format(numericLabelValueFormat, "Sessions permitted per account:", sessionMetrics.sessionsPermittedPerAccount);
      System.out.format(numericLabelValueFormat, "Number of sessions:", sessionMetrics.numberOfSessions);
      System.out.format(numericLabelValueFormat, "Spread of accounts:", sessionMetrics.spreadOfAccounts);
      System.out.println();
      System.out.format(labelFormat + "%d minute%s%n", "Encryption nonce duration:", sessionMetrics.nonceEncryptionExpiryTimeMinutes,
                        pluralise(sessionMetrics.nonceEncryptionExpiryTimeMinutes));
      System.out.format(numericLabelValueFormat, "Number of nonces held:", sessionMetrics.numberOfEncryptionNonces);
      System.out.println();
   }


   private void displayUserAccountSessions(final String argument)
   {
      final FeedbactoryUserAccountView accountView = getUserAccountFrom(argument);

      if (accountView != null)
      {
         final List<AccountSessionMetrics> userAccountSessions = operationsManager.getNetworkToApplicationGateway().getAccountSessionMetrics(accountView.userAccountID);

         if ((userAccountSessions != null) && (! userAccountSessions.isEmpty()))
         {
            final String outputFormat = "%-40.40s%-40.40s%n";

            System.out.println();
            System.out.format("Sessions attached to user %s (ID %d):%n", accountView.email, accountView.userAccountID);
            System.out.println();
            System.out.format(outputFormat, "Session creation time", "Session last resumed time");

            for (final AccountSessionMetrics sessionMetrics : userAccountSessions)
               System.out.format(outputFormat, getFormattedDateAndTime(sessionMetrics.sessionCreationTime), getFormattedDateAndTime(sessionMetrics.sessionLastResumedTime));

            System.out.println();
         }
         else
            System.out.format("No sessions are attached to user %s (ID %d).%n", accountView.email, accountView.userAccountID);
      }
      else
         System.out.println("Unknown user account.");
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processIPAuthenticationCommand(final String[] arguments) throws UnknownHostException
   {
      if (arguments.length == 0)
         displayIPAuthenticationMetrics();
      else if ((arguments.length == 1) && arguments[0].equals(IPAuthenticationBlockedCommandSwitch))
         displayEmailBlockedIPAddressMetrics();
      else if (arguments.length == 1)
         displayIPAddressFailedAuthenticationMetrics(InetAddress.getByName(arguments[0]));
      else
         System.out.println("Invalid command switch.");
   }


   private void displayIPAuthenticationMetrics()
   {
      final IPAuthenticationMetrics failedAuthenticationMetrics = operationsManager.getNetworkToApplicationGateway().getIPAuthenticationMetrics();

      final String labelFormat = "%-40.40s";
      final String generalLabelValueFormat = labelFormat + "%s%n";
      final String numericLabelValueFormat = labelFormat + "%d%n";

      System.out.println();
      System.out.format(generalLabelValueFormat, "IP authentication housekeeping status:", (failedAuthenticationMetrics.isHousekeepingEnabled ? "Enabled" : "Disabled"));
      System.out.format(labelFormat + "%d minute%s%n", "Frequency:", failedAuthenticationMetrics.housekeepingFrequencyMinutes,
                        pluralise(failedAuthenticationMetrics.housekeepingFrequencyMinutes));
      System.out.format(generalLabelValueFormat, "Last run time:", getFormattedDateAndTime(failedAuthenticationMetrics.housekeepingLastRunStartTime));
      System.out.println();
      System.out.format(numericLabelValueFormat, "IP addresses tracking:", failedAuthenticationMetrics.numberOfIPAddressesTracking);
      System.out.println();
      System.out.format(generalLabelValueFormat, "Email lockout threshold:", failedAuthenticationMetrics.emailLockoutThreshold);
      System.out.format(labelFormat + "%d minute%s%n", "Email lockout period:", failedAuthenticationMetrics.emailLockoutPeriodMinutes,
                        pluralise(failedAuthenticationMetrics.emailLockoutPeriodMinutes));
      System.out.format(numericLabelValueFormat, "Email lockouts:", failedAuthenticationMetrics.numberOfEmailLockouts);
      System.out.println();
      System.out.format(generalLabelValueFormat, "IP address lockout threshold:", failedAuthenticationMetrics.ipAddressLockoutThreshold);
      System.out.format(labelFormat + "%d minute%s%n", "IP address lockout period:", failedAuthenticationMetrics.ipAddressLockoutPeriodMinutes,
                        pluralise(failedAuthenticationMetrics.ipAddressLockoutPeriodMinutes));
      System.out.format(numericLabelValueFormat, "IP address lockouts:", failedAuthenticationMetrics.numberOfIPAddressLockouts);
      System.out.println();
   }


   private void displayEmailBlockedIPAddressMetrics()
   {
      final List<EmailBlockedIPAddressMetrics> emailBlockedIPAddressMetrics = operationsManager.getNetworkToApplicationGateway().getEmailBlockedIPAddressMetrics();

      if (emailBlockedIPAddressMetrics.isEmpty())
         System.out.println("No email blocked IP addresses.");
      else
      {
         final String headingOutputFormat = "%-20.20s%-20.20s%s%n";
         final String rowOutputFormat = "%-20.20s%-20d%s%n";

         System.out.println();
         System.out.format(headingOutputFormat, "IP address", "Spread of emails", "Last blocked");
         System.out.println();

         for (final EmailBlockedIPAddressMetrics emailBlockedIPAddress : emailBlockedIPAddressMetrics)
            System.out.format(rowOutputFormat, emailBlockedIPAddress.ipAddress, emailBlockedIPAddress.spreadOfEmails, getFormattedDateAndTime(emailBlockedIPAddress.lastLockoutTime));

         System.out.println();
         System.out.format("%d email blocked IP address%s.%n%n", emailBlockedIPAddressMetrics.size(), (emailBlockedIPAddressMetrics.size() == 1) ? "" : "es");
      }
   }


   private void displayIPAddressFailedAuthenticationMetrics(final InetAddress ipAddress)
   {
      final IPAddressFailedAuthenticationMetrics failedAuthenticationMetrics = operationsManager.getNetworkToApplicationGateway().getIPAddressFailedAuthenticationMetrics(ipAddress);

      final String labelFormat = "%-35.35s";
      final String generalLabelValueFormat = labelFormat + "%s%n";

      System.out.println();
      System.out.format(generalLabelValueFormat, "IP address authentication status:", (failedAuthenticationMetrics.isIPAddressLockedOut ? "Locked out" : "OK"));
      System.out.format(generalLabelValueFormat, "Last locked out:", getFormattedDateAndTime(failedAuthenticationMetrics.lastLockoutTime));
      System.out.println();

      if (failedAuthenticationMetrics.emailFailedAuthentications.isEmpty())
         System.out.println("No failed authentications.");
      else
      {
         final String headingOutputFormat = "%-50.50s%-20.20s%s%n";
         final String rowOutputFormat = "%-50.50s%-20d%s%n";

         System.out.format(headingOutputFormat, "Email", "Failed attempts", "Last failed attempt");
         System.out.println();

         int failedAuthentications = 0;

         for (final EmailFailedAuthenticationMetrics emailFailedAuthentication : failedAuthenticationMetrics.emailFailedAuthentications)
         {
            failedAuthentications += emailFailedAuthentication.failedAuthentications;

            System.out.format(rowOutputFormat, emailFailedAuthentication.email, emailFailedAuthentication.failedAuthentications,
                              getFormattedDateAndTime(emailFailedAuthentication.lastFailedAuthenticationTime));
         }

         System.out.println();
         System.out.format("%d failed authentication%s for %d email%s.%n", failedAuthentications, pluralise(failedAuthentications),
                           failedAuthenticationMetrics.emailFailedAuthentications.size(), pluralise(failedAuthenticationMetrics.emailFailedAuthentications.size()));
      }

      System.out.println();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processAccountCommand(final String[] arguments) throws IOException
   {
      if (arguments.length == 0)
         displayUserAccountManagerMetrics();
      else if (arguments[0].equals(UserAccountMessageCommandSwitch))
         processUserAccountMessageCommandSwitch(trimArguments(arguments));
      else if (arguments[0].equals(TestCommandSwitch))
         processUserAccountTestCommand();
      else if (arguments.length == 1)
         displayUserAccount(arguments[0]);
      else
         System.out.println("Invalid command switch.");
   }


   private void displayUserAccountManagerMetrics()
   {
      final UserAccountManagerMetrics accountManagerMetrics = operationsManager.getUserAccountManager().getAccountManagerMetrics();

      final String labelFormat = "%-35.35s";
      final String generalLabelValueFormat = labelFormat + "%s%n";
      final String numericLabelValueFormat = labelFormat + "%d%n";

      System.out.println();
      System.out.format(generalLabelValueFormat, "User account housekeeping status:", (accountManagerMetrics.isHousekeepingEnabled ? "Enabled" : "Disabled"));
      System.out.format(labelFormat + "%d day%s%n", "Frequency:", accountManagerMetrics.housekeepingFrequencyDays,
                        pluralise(accountManagerMetrics.housekeepingFrequencyDays));
      System.out.format(generalLabelValueFormat, "Last run time:", getFormattedDateAndTime(accountManagerMetrics.housekeepingLastRunStartTime));
      System.out.println();
      System.out.format(numericLabelValueFormat, "Total accounts:", accountManagerMetrics.activatedAccounts + accountManagerMetrics.pendingAccounts);
      System.out.format(numericLabelValueFormat, "Activated accounts:", accountManagerMetrics.activatedAccounts);
      System.out.println();
      System.out.format(labelFormat + "%d day%s%n", "Pending account expiry period:", accountManagerMetrics.pendingAccountExpiryPeriodDays,
                        pluralise(accountManagerMetrics.pendingAccountExpiryPeriodDays));
      System.out.format(numericLabelValueFormat, "Pending accounts:", accountManagerMetrics.pendingAccounts);
      System.out.println();
      System.out.format(labelFormat + "%d day%s%n", "Pending email expiry period:", accountManagerMetrics.pendingEmailExpiryPeriodDays,
                        pluralise(accountManagerMetrics.pendingEmailExpiryPeriodDays));
      System.out.format(numericLabelValueFormat, "Pending emails:", accountManagerMetrics.pendingEmails);
      System.out.println();
      System.out.format(labelFormat + "%d day%s%n", "Password reset expiry period:", accountManagerMetrics.passwordResetCodeExpiryPeriodDays,
                        pluralise(accountManagerMetrics.passwordResetCodeExpiryPeriodDays));
      System.out.format(numericLabelValueFormat, "Active password reset codes:", accountManagerMetrics.activePasswordResetCodes);
      System.out.println();
   }


   private void processUserAccountMessageCommandSwitch(final String[] arguments)
   {
      if (arguments.length == 0)
         displayMessageTypes();
      else
      {
         final FeedbactoryUserAccountView userAccount = getUserAccountFrom(arguments[0]);

         if (userAccount != null)
         {
            if (arguments.length == 1)
               displayUserAccountMessage(userAccount);
            else
            {
               final Message newUserAccountMessage = parseMessageArguments(Arrays.copyOfRange(arguments, 1, arguments.length));
               setUserAccountMessage(userAccount, newUserAccountMessage);
            }
         }
         else
            System.out.println("Unknown user account.");
      }
   }


   private void displayMessageTypes()
   {
      System.out.println();
      printMessageTypes();
      System.out.println();
   }


   private void displayUserAccountMessage(final FeedbactoryUserAccountView userAccount)
   {
      if (userAccount.message.message.messageType == MessageType.NoMessage)
         System.out.format("No pending message for user %s (ID %d).%n", userAccount.email, userAccount.userAccountID);
      else
      {
         System.out.println();
         System.out.format("Pending message for user %s (ID %d):%n", userAccount.email, userAccount.userAccountID);
         printTimestampedMessage(userAccount.message);
         System.out.println();
      }
   }


   private void setUserAccountMessage(final FeedbactoryUserAccountView userAccount, final Message newMessage)
   {
      operationsManager.getUserAccountManager().setAccountMessage(userAccount.userAccountID, newMessage);

      if (newMessage.messageType == MessageType.NoMessage)
         System.out.format("Cleared pending message for user %s (ID %d).%n", userAccount.email, userAccount.userAccountID);
      else
      {
         System.out.println();
         System.out.format("Changed pending message for user %s (ID %d):%n", userAccount.email, userAccount.userAccountID);
         printMessage(newMessage);
         System.out.println();
      }
   }


   private void processUserAccountTestCommand() throws IOException
   {
      if (FeedbactoryServer.getExecutionProfile() != FeedbactoryServer.ExecutionProfile.Production)
         FeedbactoryUserAccountTest.createTestUserAccounts(operationsManager.getUserAccountManager());
      else
         System.out.println("Test cannot be performed when Feedbactory server is using the production profile.");
   }


   private void displayUserAccount(final String argument)
   {
      final FeedbactoryUserAccountView userAccount = getUserAccountFrom(argument);

      if (userAccount != null)
      {
         final String labelFormat = "%-25.25s";
         final String generalLabelValueFormat = labelFormat + "%s%n";
         final String numericLabelValueFormat = labelFormat + "%d%n";

         System.out.println();
         System.out.format(numericLabelValueFormat, "User account ID:", userAccount.userAccountID);
         System.out.println();
         System.out.format(generalLabelValueFormat, "Email:", userAccount.email);
         System.out.format(generalLabelValueFormat, "Pending email:", (userAccount.pendingEmail != null) ? userAccount.pendingEmail : "None");
         System.out.format(generalLabelValueFormat, "Email confirmation code:", (userAccount.emailConfirmationCode != null) ? userAccount.emailConfirmationCode : "None");
         System.out.format(generalLabelValueFormat, "Last updated:", getFormattedDateAndTime(userAccount.emailConfirmationCodeLastUpdatedTime));
         System.out.println();
         System.out.format(generalLabelValueFormat, "Password reset code:", (userAccount.passwordResetCode != null) ? userAccount.passwordResetCode : "None");
         System.out.format(generalLabelValueFormat, "Last updated:", getFormattedDateAndTime(userAccount.passwordResetCodeLastUpdatedTime));
         System.out.println();
         System.out.format(generalLabelValueFormat, "Gender:", userAccount.gender);
         System.out.format(labelFormat + "%td/%<tm/%<tY%n", "Date of birth:", userAccount.dateOfBirth);
         System.out.format(generalLabelValueFormat, "Send email alerts:", userAccount.sendEmailAlerts);
         System.out.println();

         final Message message = userAccount.message.message;
         if (message.messageType == MessageType.NoMessage)
            System.out.format(generalLabelValueFormat, "Pending message:", "None");
         else
         {
            System.out.format(labelFormat + "[%s]: %s%n", "Pending message:", message.messageType, message.message);
            System.out.format(generalLabelValueFormat, "Last updated:", getFormattedDateAndTime(userAccount.message.messageTime));
         }

         System.out.println();
         System.out.format(generalLabelValueFormat, "Last authenticated IP:", userAccount.lastAuthenticatedIPAddress);
         System.out.println();
         System.out.format(generalLabelValueFormat, "Activation state:", userAccount.activationState);
         System.out.println();
         System.out.format(generalLabelValueFormat, "Created:", getFormattedDateAndTime(userAccount.creationTime));
         System.out.println();
      }
      else
         System.out.println("Unknown user account.");
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processMailerCommand(final String[] arguments)
   {
      if (arguments.length == 0)
         displayUserAccountMailerMetrics();
      else
         System.out.println("Invalid command switch.");
   }


   private void displayUserAccountMailerMetrics()
   {
      final UserAccountMailerMetrics mailerMetrics = operationsManager.getUserAccountManager().getAccountMailerMetrics();

      final String labelFormat = "%-35.35s";
      final String numericLabelValueFormat = labelFormat + "%d%n";

      System.out.println();
      System.out.format(numericLabelValueFormat, "Mailer thread queue size:", mailerMetrics.mailerThreadPoolQueueSize);
      System.out.format(numericLabelValueFormat, "Mailer thread tasks completed:", mailerMetrics.mailerThreadPoolTasksCompleted);
      System.out.println();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void processFeedbackCommand(final String[] arguments)
   {
      if (arguments.length == 0)
         displayFeedbackManagerMetrics();
      else if (arguments[0].equals(FeedbackCommandAccountSwitch))
         processUserAccountFeedbackMetrics(trimArguments(arguments));
      else
      {
         final short feedbackCategoryValue = Short.parseShort(arguments[0]);
         final FeedbackCategory feedbackCategory = FeedbackCategory.fromValue(feedbackCategoryValue);
         if (feedbackCategory != null)
            processFeedbackCategoryCommand(feedbackCategory, trimArguments(arguments));
         else
            throw new IllegalArgumentException("Invalid feedback category value: " + feedbackCategoryValue);
      }
   }


   private void displayFeedbackManagerMetrics()
   {
      final FeedbackManager.FeedbackManagerMetrics feedbackManagerMetrics = operationsManager.getFeedbackManager().getFeedbackManagerMetrics();

      final String labelFormat = "%-25.25s";
      final String generalLabelValueFormat = labelFormat + "%s%n";
      final String numericLabelValueFormat = labelFormat + "%d%n";

      System.out.println();
      System.out.format(generalLabelValueFormat, "Feedback housekeeping:", (feedbackManagerMetrics.isHousekeepingEnabled ? "Enabled" : "Disabled"));
      System.out.format(labelFormat + "%d minute%s%n", "Frequency:", feedbackManagerMetrics.housekeepingFrequencyMinutes,
                        pluralise(feedbackManagerMetrics.housekeepingFrequencyMinutes));
      System.out.format(generalLabelValueFormat, "Last run time:", getFormattedDateAndTime(feedbackManagerMetrics.housekeepingLastRunStartTime));
      System.out.println();
      System.out.format(numericLabelValueFormat, "Feedback submissions:", feedbackManagerMetrics.feedbackSubmissions);
      System.out.format(numericLabelValueFormat, "Spread of items:", feedbackManagerMetrics.spreadOfItems);
      System.out.format(numericLabelValueFormat, "Spread of accounts:", feedbackManagerMetrics.spreadOfAccounts);
      System.out.println();
      System.out.format(numericLabelValueFormat, "Cached item profiles:", feedbackManagerMetrics.cachedItemProfiles);
      System.out.println();
      printFormattedFeedbackCategories(feedbackManagerMetrics.registeredFeedbackCategories, generalLabelValueFormat, numericLabelValueFormat);
      System.out.println();
   }


   private void processUserAccountFeedbackMetrics(final String[] arguments)
   {
      if (arguments.length == 1)
         displayUserAccountFeedbackMetrics(getUserAccountFrom(arguments[0]));
      else
         System.out.println("Invalid command switch.");
   }


   private void displayUserAccountFeedbackMetrics(final FeedbactoryUserAccountView userAccount)
   {
      if (userAccount != null)
      {
         final List<ItemProfileFeedbackSubmission> userAccountFeedback = operationsManager.getFeedbackManager().getAllUserFeedbackSubmissions(userAccount);

         if (! userAccountFeedback.isEmpty())
         {
            final Comparator<ItemProfileFeedbackSubmission> sortBySubmissionTimeComparator = new Comparator<ItemProfileFeedbackSubmission>()
            {
               @Override
               final public int compare(final ItemProfileFeedbackSubmission submissionOne, final ItemProfileFeedbackSubmission submissionTwo)
               {
                  if (submissionOne.getSubmissionTime() < submissionTwo.getSubmissionTime())
                     return -1;
                  else if (submissionOne.getSubmissionTime() > submissionTwo.getSubmissionTime())
                     return 1;
                  else
                     return 0;
               }
            };

            Collections.sort(userAccountFeedback, sortBySubmissionTimeComparator);

            System.out.println();
            System.out.format("Feedback submitted by user %s (ID %d):%n", userAccount.email, userAccount.userAccountID);
            System.out.println();
            // Provide spacing but don't truncate any of the output fields.
            final String outputFormat = "%-30s%-30s%-20s%-20s%s%n";
            System.out.format(outputFormat, "Item ID", "Name", "Website", "Feedback summary", "Submitted");
            System.out.println();

            for (final ItemProfileFeedbackSubmission submission : userAccountFeedback)
            {
               System.out.format(outputFormat, submission.getItemProfile().getItem().getItemID(), submission.getItemProfile().getFullName(),
                                 submission.getItemProfile().getItem().getWebsite().getName(), submission.getFeedbackSubmission().getSummary(),
                                 getFormattedDateAndTime(submission.getSubmissionTime()));
            }

            System.out.println();
         }
         else
            System.out.format("No feedback has been submitted by user %s (ID %d).%n", userAccount.email, userAccount.userAccountID);
      }
      else
         System.out.println("Unknown user account.");
   }


   private void processFeedbackCategoryCommand(final FeedbackCategory feedbackCategory, final String[] arguments)
   {
      final String output = operationsManager.getFeedbackManager().processFeedbackCategoryConsoleCommand(feedbackCategory, arguments);

      if (output != null)
         System.out.println(output);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private boolean processExitCommand(final String[] arguments) throws IOException, InterruptedException
   {
      boolean processExit = false;

      if (arguments.length == 0)
      {
         if (checkCanExit())
         {
            if (! operationsManager.isAttachedToPrimaryCheckpoint())
               processExit = true;
            else
               System.out.println("Use '" + ExitCommand + ' ' + ExitNoSaveCommandSwitch + "' to exit without saving when the primary checkpoint is loaded.");
         }
      }
      else if ((arguments.length == 1) && arguments[0].equals(ExitNoSaveCommandSwitch))
         processExit = checkCanExit();
      else
         System.out.println("Invalid command arguments.");

      if (processExit)
      {
         System.out.println("Exiting Feedbactory...");
         processApplicationShutdown();
      }

      return (! processExit);
   }


   private boolean checkCanExit()
   {
      if (operationsManager.getServerController().isServerStarted())
      {
         System.out.println("An instance of the Feedbactory server is active.");
         return false;
      }
      else if (operationsManager.isAutoSaveStarted())
      {
         System.out.println("Auto save must be disabled before exiting.");
         return false;
      }

      return true;
   }


   private void processApplicationShutdown() throws InterruptedException
   {
      operationsManager.getUserAccountManager().shutdown();
      operationsManager.getLoggerManager().shutdown();
   }


   /****************************************************************************
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


   /****************************************************************************
    *
    ***************************************************************************/


   final public void start() throws IOException
   {
      handleStart();
   }
}