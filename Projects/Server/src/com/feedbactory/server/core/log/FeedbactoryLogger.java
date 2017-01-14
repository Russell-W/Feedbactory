/* Memos:
 * - Any items statically initialised here must be failsafe, to prevent the possibility of the Feedbactory logger class failing with an ExceptionInInitializerError and
 *   putting an end to any further logging, including SMSes.
 */

package com.feedbactory.server.core.log;


import com.feedbactory.server.FeedbactoryServer;
import com.feedbactory.server.FeedbactoryServer.ExecutionProfile;
import com.feedbactory.server.network.application.RequestUserSession;
import com.feedbactory.server.network.component.ClientIO;
import java.io.PrintStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;


final public class FeedbactoryLogger
{
   // The active logging levels are best set via a console batch script for the current execution profile.
   static final private SystemLogLevel DefaultSystemLogLevel = FeedbactoryServer.IsDebugMode ? SystemLogLevel.Debug : SystemLogLevel.ClientStateError;
   static final private SecurityLogLevel DefaultSecurityLogLevel = SecurityLogLevel.Medium;

   static final SystemLogLevel SMSAlertSystemLogLevel = initialiseSMSAlertSystemLogLevel();
   static final SecurityLogLevel SMSAlertSecurityLogLevel = initialiseSMSAlertSecurityLogLevel();

   static volatile private SystemLogLevel systemLogLevel = DefaultSystemLogLevel;
   static volatile private SecurityLogLevel securityLogLevel = DefaultSecurityLogLevel;

   static final private Map<SystemLogLevel, AtomicInteger> systemEventsLogged = initialiseSystemEventsLogged();
   static final private Map<SecurityLogLevel, AtomicInteger> securityEventsLogged = initialiseSecurityEventsLogged();

   static final private LogOutputHandler logHandler = initialiseLogHandler();

   static final private SMSAlertManager smsAlertManager = new SMSAlertManager();


   private FeedbactoryLogger()
   {
   }


   static private SystemLogLevel initialiseSMSAlertSystemLogLevel()
   {
      if (FeedbactoryServer.getExecutionProfile() == ExecutionProfile.Development)
         return SystemLogLevel.None;
      else
         return SystemLogLevel.OverloadError;
   }


   static private SecurityLogLevel initialiseSMSAlertSecurityLogLevel()
   {
      if (FeedbactoryServer.getExecutionProfile() == ExecutionProfile.Development)
         return SecurityLogLevel.None;
      else
         return SecurityLogLevel.High;
   }


   static private Map<SystemLogLevel, AtomicInteger> initialiseSystemEventsLogged()
   {
      final Map<SystemLogLevel, AtomicInteger> systemEventsLoggedBuilder = new EnumMap<>(SystemLogLevel.class);

      for (final SystemLogLevel eventLevel : SystemLogLevel.values())
      {
         if (eventLevel != SystemLogLevel.None)
            systemEventsLoggedBuilder.put(eventLevel, new AtomicInteger());
      }

      return systemEventsLoggedBuilder;
   }


   static private Map<SecurityLogLevel, AtomicInteger> initialiseSecurityEventsLogged()
   {
      final Map<SecurityLogLevel, AtomicInteger> securityEventsLoggedBuilder = new EnumMap<>(SecurityLogLevel.class);

      for (final SecurityLogLevel eventLevel : SecurityLogLevel.values())
      {
         if (eventLevel != SecurityLogLevel.None)
            securityEventsLoggedBuilder.put(eventLevel, new AtomicInteger());
      }

      return securityEventsLoggedBuilder;
   }


   static private LogOutputHandler initialiseLogHandler()
   {
      if (FeedbactoryServer.getExecutionProfile() == FeedbactoryServer.ExecutionProfile.Development)
         return getSystemErrLogHandler();
      else
         return new FileLogHandler();
   }


   static private PrintStreamLogHandler getSystemErrLogHandler()
   {
      return new PrintStreamLogHandler()
      {
         @Override
         final protected PrintStream getPrintStream()
         {
            return System.err;
         }
      };
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class EventLogMetrics
   {
      final public Map<SystemLogLevel, Integer> systemEventsLogged;
      final public Map<SecurityLogLevel, Integer> securityEventsLogged;


      private EventLogMetrics(final Map<SystemLogLevel, AtomicInteger> systemEventsLogged, final Map<SecurityLogLevel, AtomicInteger> securityEventsLogged)
      {
         this.systemEventsLogged = initialiseSystemEventsLogged(systemEventsLogged);
         this.securityEventsLogged = initialiseSecurityEventsLogged(securityEventsLogged);
      }


      private Map<SystemLogLevel, Integer> initialiseSystemEventsLogged(final Map<SystemLogLevel, AtomicInteger> systemEventsLogged)
      {
         final Map<SystemLogLevel, Integer> systemEventsLoggedBuilder = new EnumMap<>(SystemLogLevel.class);

         for (final Entry<SystemLogLevel, AtomicInteger> systemEventsEntry : systemEventsLogged.entrySet())
            systemEventsLoggedBuilder.put(systemEventsEntry.getKey(), systemEventsEntry.getValue().get());

         return Collections.unmodifiableMap(systemEventsLoggedBuilder);
      }


      private Map<SecurityLogLevel, Integer> initialiseSecurityEventsLogged(final Map<SecurityLogLevel, AtomicInteger> securityEventsLogged)
      {
         final Map<SecurityLogLevel, Integer> securityEventsLoggedBuilder = new EnumMap<>(SecurityLogLevel.class);

         for (final Entry<SecurityLogLevel, AtomicInteger> securityEventsEntry : securityEventsLogged.entrySet())
            securityEventsLoggedBuilder.put(securityEventsEntry.getKey(), securityEventsEntry.getValue().get());

         return Collections.unmodifiableMap(securityEventsLoggedBuilder);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static private void sendSystemEventSMSAlert(final String alertContext, final SystemEvent systemEvent, final boolean setSMSSendTime)
   {
      final StringBuilder messageBuilder = new StringBuilder(SMSAlertManager.SMSMaximumLength);
      messageBuilder.append(alertContext);
      messageBuilder.append(":\\n");
      messageBuilder.append(systemEvent.level.name());
      messageBuilder.append("\\n");

      // getSimpleName() will be empty for anonymous classes, but getName() includes the full package name which takes up too many characters.
      messageBuilder.append(systemEvent.originatingClass.getSimpleName());
      messageBuilder.append("\\n");

      if (systemEvent.message != null)
         messageBuilder.append(systemEvent.message);

      sendAlertSMS(messageBuilder, setSMSSendTime);
   }


   static private void sendAlertSMS(final StringBuilder alertMessageBuilder, final boolean setSMSSendTime)
   {
      /* I think it's reasonable to have the sms alert manager publicise the maximum message length, as it allows the caller (this class)
       * to decide what information to include.
       * The SMS alert manager applies ISO_8859_1 encoding, so there will always be 1 byte used per message character.
       */
      if (alertMessageBuilder.length() > SMSAlertManager.SMSMaximumLength)
         alertMessageBuilder.setLength(SMSAlertManager.SMSMaximumLength);

      smsAlertManager.sendSystemAlertSMS(alertMessageBuilder.toString(), setSMSSendTime);
   }


   static private void sendSecurityEventSMSAlert(final SecurityEvent securityEvent)
   {
      final StringBuilder messageBuilder = new StringBuilder(SMSAlertManager.SMSMaximumLength);
      messageBuilder.append("Security event:\\n");
      messageBuilder.append(securityEvent.level.name());
      messageBuilder.append("\\n");

      // getSimpleName() will be empty for anonymous classes, but getName() includes the full package name which takes up too many characters.
      messageBuilder.append(securityEvent.originatingClass.getSimpleName());
      messageBuilder.append("\\n");

      if (securityEvent.message != null)
         messageBuilder.append(securityEvent.message);

      sendAlertSMS(messageBuilder, true);
   }


   static private void handleReportLogFailureEvent(final SystemEvent event)
   {
      sendSystemEventSMSAlert("Log failure event", event, true);
   }


   static private void handleLogSystemEvent(final SystemEvent event)
   {
      if (isLoggingSystemEventsAtLevel(event.level))
      {
         logHandler.logSystemEvent(event);

         if ((event.level != SystemLogLevel.None) && (event.level.value >= SMSAlertSystemLogLevel.value))
            sendSystemEventSMSAlert("System event", event, true);

         systemEventsLogged.get(event.level).incrementAndGet();
      }
   }


   static private void handleLogSecurityEvent(final SecurityEvent event)
   {
      if (isLoggingSecurityEventsAtLevel(event.level))
      {
         logHandler.logSecurityEvent(event);

         if ((event.level != SecurityLogLevel.None) && (event.level.value >= SMSAlertSecurityLogLevel.value))
            sendSecurityEventSMSAlert(event);

         securityEventsLogged.get(event.level).incrementAndGet();
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static void reportLogFailureEvent(final SystemEvent event)
   {
      handleReportLogFailureEvent(event);
   }


   static void reportSMSFailed(final SystemEvent systemEvent, final String originalMessage)
   {
      // NOP at this point. Couldn't log it, couldn't send an SMS alert...
   }


   static EventLogMetrics getMetrics()
   {
      return new EventLogMetrics(systemEventsLogged, securityEventsLogged);
   }


   static SystemLogLevel getSystemEventLevel()
   {
      return systemLogLevel;
   }


   static SecurityLogLevel getSecurityEventLevel()
   {
      return securityLogLevel;
   }


   static void setSystemEventLevel(final SystemLogLevel logLevel)
   {
      if (logLevel == null)
         throw new IllegalArgumentException("System event log severity level cannot be null.");

      systemLogLevel = logLevel;
   }


   static void setSecurityEventLevel(final SecurityLogLevel logLevel)
   {
      if (logLevel == null)
         throw new IllegalArgumentException("Security event log severity level cannot be null.");

      securityLogLevel = logLevel;
   }


   static long getSMSLastSentTime()
   {
      return smsAlertManager.getSMSLastSentTime();
   }


   static void resetSMSLastSentTime()
   {
      smsAlertManager.resetSMSLastSentTime();
   }


   static void sendTestSystemEventSMSAlert(final String alertContext, final SystemEvent systemEvent)
   {
      sendSystemEventSMSAlert(alertContext, systemEvent, false);
   }


   static void shutdown()
   {
      logHandler.shutdown();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   static public boolean isLoggingSystemEventsAtLevel(final SystemLogLevel loggingLevel)
   {
      return ((systemLogLevel != SystemLogLevel.None) && (systemLogLevel.value <= loggingLevel.value));
   }


   static public boolean isLoggingSecurityEventsAtLevel(final SecurityLogLevel loggingLevel)
   {
      return ((securityLogLevel != SecurityLogLevel.None) && (securityLogLevel.value <= loggingLevel.value));
   }


   static public void logSystemEvent(final SystemLogLevel level, final Class<?> originatingClass, final String message)
   {
      handleLogSystemEvent(new SystemEvent(level, originatingClass, message));
   }


   static public void logSystemEvent(final SystemLogLevel level, final Class<?> originatingClass, final String message, final Throwable throwable)
   {
      handleLogSystemEvent(new SystemEvent(level, originatingClass, message, throwable));
   }


   static public void logSystemEvent(final SystemEvent event)
   {
      handleLogSystemEvent(event);
   }


   static public void logSecurityEvent(final SecurityLogLevel level, final Class<?> originatingClass, final String eventMessage, final ClientIO clientIO)
   {
      handleLogSecurityEvent(new SecurityEvent(level, originatingClass, eventMessage, clientIO));
   }


   static public void logSecurityEvent(final SecurityLogLevel level, final Class<?> originatingClass, final String eventMessage, final RequestUserSession userSession)
   {
      handleLogSecurityEvent(new SecurityEvent(level, originatingClass, eventMessage, userSession));
   }
}