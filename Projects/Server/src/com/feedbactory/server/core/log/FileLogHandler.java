/* Memos:
 * - The FeedbactoryLogger provides a failsafe mechanism if file logging fails due to any IOException. Given this and the fact that the file logging and rotating scheme
 *   is fairly simple and not likely to fail, I'm reluctant to put in anything along the lines of fail-retry code here.
 *
 * - One potential failpoint is the maximum ByteBuffer size which needs to be large enough to contain the largest expected log entry. A massive stack trace could cause
 *   a buffer overflow, so the ByteBuffer needs to be reasonably large in order to prevent the file logger falling back to the FeedbactoryLogger failsafe mentioned above.
 *   I think it's fair enough to capture and log the entire stack trace, however I'm putting a restriction on the size of the request/response buffer data that can be
 *   written to a log entry. Request buffer size is restricted by Feedbactory's asynchronous read handler so its maximum size is known, however response size can be arbitrary
 *   and fairly lengthy in the case of featured feedback item lists. The reasoning behind restricting the buffer output to the log (other than preventing overflow) is that
 *   if something in the request data has caused an error, it's almost certainly something near the start of the buffer, ie. one of the header directives.
 */

package com.feedbactory.server.core.log;


import com.feedbactory.server.core.FeedbactoryServerConstants;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.prefs.Preferences;


final class FileLogHandler implements LogOutputHandler
{
   static final private Path SystemLogFilePath = FeedbactoryServerConstants.LogPath.resolve("System");
   static final private String SystemLogFileNumberPreferencesKey = "SystemLogFileNumber";

   static final private Path SecurityLogFilePath = FeedbactoryServerConstants.LogPath.resolve("Security");
   static final private String SecurityLogFileNumberPreferencesKey = "SecurityLogFileNumber";

   static final private int LogFileSwitchPointSizeBytes = 1024000;

   static final private int MaximumLogFilesRetained = 200;

   final private LogOutputFileManager systemLogFile = new LogOutputFileManager(SystemLogFilePath, SystemLogFileNumberPreferencesKey);
   final private LogOutputFileManager securityLogFile = new LogOutputFileManager(SecurityLogFilePath, SecurityLogFileNumberPreferencesKey);


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static private class LogOutputFileManager extends PrintStreamLogHandler
   {
      final private Path logFilePath;
      final private String logFileNumberPreferencesKey;

      private PrintStream activePrintStream;
      private MeteredOutputStream activeMeteredStream;
      private int activeFileNumber;


      private LogOutputFileManager(final Path logFilePath, final String logFileNumberPreferencesKey)
      {
         this.logFilePath = logFilePath;
         this.logFileNumberPreferencesKey = logFileNumberPreferencesKey;
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private boolean isInitialised()
      {
         return (activePrintStream != null);
      }


      private void initialise()
      {
         restoreFileNumber();
         openLogFile(true);
      }


      private void restoreFileNumber()
      {
         final Preferences feedbactoryCheckpointPreferences = Preferences.userNodeForPackage(getClass());
         activeFileNumber = feedbactoryCheckpointPreferences.getInt(logFileNumberPreferencesKey, 1);
      }


      private void saveFileNumber()
      {
         final Preferences feedbactoryCheckpointPreferences = Preferences.userNodeForPackage(getClass());
         feedbactoryCheckpointPreferences.putInt(logFileNumberPreferencesKey, activeFileNumber);
      }


      private void openLogFile(final boolean append)
      {
         try
         {
            final String outputFileName = Integer.toString(activeFileNumber) + FeedbactoryServerConstants.DataFileExtension;
            final File outputFilePath = logFilePath.resolve(outputFileName).toFile();

            final FileOutputStream fileOutputStream = new FileOutputStream(outputFilePath, append);
            activeMeteredStream = new MeteredOutputStream(fileOutputStream, (append ? (int) outputFilePath.length() : 0));
            final BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(activeMeteredStream);
            activePrintStream = new PrintStream(bufferedOutputStream, false, StandardCharsets.UTF_8.name());
         }
         catch (final IOException ioException)
         {
            // Promote the IOException's message to the system event message, so it can then be included in any report sent via SMS.
            FeedbactoryLogger.reportLogFailureEvent(new SystemEvent(SystemLogLevel.ApplicationError, getClass(), ioException.getMessage(), ioException));
         }
      }


      private void closeLogFile()
      {
         activePrintStream.close();
      }


      private void rotate()
      {
         closeLogFile();

         activeFileNumber ++;
         if (activeFileNumber > MaximumLogFilesRetained)
            activeFileNumber = 1;

         openLogFile(false);
      }


      private void handleLogSystemEvent(final SystemEvent event)
      {
         /* This class is initialised lazily to prevent the possibility of the Feedbactory logging framework (which instantiates this class) from failing
          * with an ExceptionInInitializerError.
          */
         if (! isInitialised())
            initialise();

         super.logSystemEvent(event);
         activePrintStream.flush();

         if (activeMeteredStream.getBytesWritten() >= LogFileSwitchPointSizeBytes)
            rotate();
      }


      private void handleLogSystemEvent(final SecurityEvent event)
      {
         // See comment above.
         if (! isInitialised())
            initialise();

         super.logSecurityEvent(event);
         activePrintStream.flush();

         if (activeMeteredStream.getBytesWritten() >= LogFileSwitchPointSizeBytes)
            rotate();
      }


      private void handleShutdown()
      {
         if (isInitialised())
         {
            closeLogFile();
            saveFileNumber();
         }
      }


      /****************************************************************************
       *
       ***************************************************************************/


      @Override
      synchronized final protected PrintStream getPrintStream()
      {
         return activePrintStream;
      }


      @Override
      synchronized final public void logSystemEvent(final SystemEvent event)
      {
         handleLogSystemEvent(event);
      }


      @Override
      synchronized final public void logSecurityEvent(final SecurityEvent event)
      {
         handleLogSystemEvent(event);
      }


      @Override
      synchronized final public void shutdown()
      {
         handleShutdown();
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   @Override
   final public void logSystemEvent(final SystemEvent event)
   {
      systemLogFile.logSystemEvent(event);
   }


   @Override
   final public void logSecurityEvent(final SecurityEvent event)
   {
      securityLogFile.logSecurityEvent(event);
   }


   @Override
   final public void shutdown()
   {
      systemLogFile.shutdown();
      securityLogFile.shutdown();
   }
}