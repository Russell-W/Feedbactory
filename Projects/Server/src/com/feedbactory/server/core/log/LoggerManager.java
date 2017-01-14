
package com.feedbactory.server.core.log;


import com.feedbactory.server.core.FeedbactoryOperationsManager;


final public class LoggerManager
{
   public LoggerManager(final FeedbactoryOperationsManager operationsManager)
   {
      validate(operationsManager);
   }


   private void validate(final FeedbactoryOperationsManager operationsManager)
   {
      if (operationsManager == null)
         throw new IllegalArgumentException("Feedbactory operations manager argument cannot be null.");
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public FeedbactoryLogger.EventLogMetrics getMetrics()
   {
      return FeedbactoryLogger.getMetrics();
   }


   final public SystemLogLevel getSystemEventLevel()
   {
      return FeedbactoryLogger.getSystemEventLevel();
   }


   final public void setSystemEventLevel(final SystemLogLevel logLevel)
   {
      FeedbactoryLogger.setSystemEventLevel(logLevel);
   }


   final public SystemLogLevel getSystemEventSMSLevel()
   {
      return FeedbactoryLogger.SMSAlertSystemLogLevel;
   }


   final public SecurityLogLevel getSecurityEventLevel()
   {
      return FeedbactoryLogger.getSecurityEventLevel();
   }


   final public void setSecurityEventLevel(final SecurityLogLevel logLevel)
   {
      FeedbactoryLogger.setSecurityEventLevel(logLevel);
   }


   final public SecurityLogLevel getSecurityEventSMSLevel()
   {
      return FeedbactoryLogger.SMSAlertSecurityLogLevel;
   }


   final public long resetSMSLastSentTime()
   {
      return FeedbactoryLogger.getSMSLastSentTime();
   }


   final public void resetLastSMSSendTime()
   {
      FeedbactoryLogger.resetSMSLastSentTime();
   }


   final public void sendTestSystemEventSMSAlert(final String alertContext, final SystemEvent systemEvent)
   {
      FeedbactoryLogger.sendTestSystemEventSMSAlert(alertContext, systemEvent);
   }


   final public void shutdown()
   {
      FeedbactoryLogger.shutdown();
   }
}