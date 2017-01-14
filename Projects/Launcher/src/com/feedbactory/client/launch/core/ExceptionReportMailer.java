
package com.feedbactory.client.launch.core;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;


final public class ExceptionReportMailer
{
   /* The exception report mailer makes use of Gmail's restricted SMTP access, which allows any IP address to send mail using its SMTP server
    * as long as the email is being sent to a Gmail or Google Apps recipient.
    */
   static final private String ErrorReportsRecipient = "<insert error report gmail recipient (username only)>";
   static final private String ErrorReportsLauncherFilter = "exceptionreports.launcher";
   static final private String ErrorReportsRecipientDomain = "gmail.com";
   static final private String ErrorReportsSender = ErrorReportsRecipient + '@' + ErrorReportsRecipientDomain;
   static final private String SMTPMXServer = "aspmx.l.google.com";


   ExceptionReportMailer()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSendErrorReport(final ExceptionReportContext exceptionReportContext) throws MessagingException
   {
      // Locale.US must always be available, according to the docs. That will work fine for the error report emails.
      final StringBuilder stringBuilder = new StringBuilder(5000);
      final Formatter errorFormatter = new Formatter(stringBuilder, Locale.US);

      final String recipientEmail = getRecipientEmail();
      final String messageTitle = getMessageTitle();

      appendLauncherVersion(errorFormatter);
      appendErrorTime(errorFormatter, exceptionReportContext.localErrorTime);
      appendEnvironmentAttributes(errorFormatter);
      appendErrorExecutionContext(errorFormatter, exceptionReportContext.contextClass, exceptionReportContext.contextMessage, exceptionReportContext.thread);

      /* The errorFormatter is writing to the stringBuilder. The errorFormatter must have its buffers entirely flushed to the underlying stringBuilder
       * at this point, before the stack trace is appended directly to the stringBuilder.
       */
      errorFormatter.flush();
      appendExceptionStackTrace(stringBuilder, exceptionReportContext.throwable);

      appendExecutionProfile(errorFormatter);
      errorFormatter.flush();

      processSendErrorReport(recipientEmail, messageTitle, errorFormatter.toString());
   }


   private String getRecipientEmail()
   {
      final StringBuilder emailBuilder = new StringBuilder(100);

      emailBuilder.append(ErrorReportsRecipient);
      emailBuilder.append('+');

      /* When comparing against final booleans (or ints) in this way, the compiler will remove the clauses that do not apply,
       * ie. the development and test error reporting email strings will not be embedded within the class files that are
       * deployed to production.
       */
      if (LauncherConstants.IsDevelopmentProfile)
         emailBuilder.append("development.");
      else if (LauncherConstants.IsTestProfile)
         emailBuilder.append("test.");

      emailBuilder.append(ErrorReportsLauncherFilter);
      emailBuilder.append('@');
      emailBuilder.append(ErrorReportsRecipientDomain);

      return emailBuilder.toString();
   }


   private String getMessageTitle()
   {
      final StringBuilder titleStringBuilder = new StringBuilder(100);

      if (LauncherConstants.IsDevelopmentProfile)
         titleStringBuilder.append("[Development] ");
      else if (LauncherConstants.IsTestProfile)
         titleStringBuilder.append("[Test] ");

      titleStringBuilder.append("Feedbactory Launcher Error Report");

      return titleStringBuilder.toString();
   }


   private void appendLauncherVersion(final Formatter formatter)
   {
      formatter.format("%-25.25s%s%n", "Launcher version:", LauncherConstants.DisplayableVersionID);
      formatter.format("%-25.25s%d%n%n", "", LauncherConstants.VersionID);
   }


   private void appendErrorTime(final Formatter formatter, final long localErrorTime)
   {
      formatter.format("%-25.25s%s%n", "Time zone:", TimeZone.getDefault().getID());
      formatter.format("%-25.25s%tH:%<tM:%<tS%<tp %<tZ %<ta %<td/%<tm/%<tY%n", "Local error time:", localErrorTime);
      formatter.format("%25s%d%n", "", localErrorTime);
      formatter.format("%-25.25s%d%n%n", "UTC offset (ms):", TimeZone.getDefault().getOffset(localErrorTime));
   }


   private void appendEnvironmentAttributes(final Formatter formatter)
   {
      final Properties properties = System.getProperties();

      final String generalLabelValueFormat = "%-25.25s%s%n";

      formatter.format("Local environment attributes:%n%n");

      formatter.format(generalLabelValueFormat, "user.country:", properties.getProperty("user.country"));
      formatter.format(generalLabelValueFormat, "user.language:", properties.getProperty("user.language"));
      // Note os.arch actually returns the running JVM's bit version, not that of the OS.
      formatter.format(generalLabelValueFormat, "os.name:", properties.getProperty("os.name"));
      formatter.format(generalLabelValueFormat, "os.arch:", properties.getProperty("os.arch"));
      formatter.format(generalLabelValueFormat, "os.version:", properties.getProperty("os.version"));
      formatter.format(generalLabelValueFormat, "java.vendor:", properties.getProperty("java.vendor"));
      formatter.format(generalLabelValueFormat, "java.vm.name:", properties.getProperty("java.vm.name"));
      formatter.format(generalLabelValueFormat, "java.version:", properties.getProperty("java.version"));
      formatter.format(generalLabelValueFormat, "java.runtime.version:", properties.getProperty("java.runtime.version"));
      formatter.format("%n");
   }


   private void appendErrorExecutionContext(final Formatter formatter, final Class<?> contextClass, final String contextMessage, final Thread thread)
   {
      final String generalLabelValueFormat = "%-25.25s%s%n";

      formatter.format("Exception context:%n%n");
      formatter.format(generalLabelValueFormat, "Context class:", (contextClass != null) ? contextClass.getName() : "None");
      formatter.format(generalLabelValueFormat, "Context message:", (contextMessage != null) ? contextMessage : "None");
      formatter.format(generalLabelValueFormat, "Thread name:", thread.getName());
      formatter.format("%n");
   }


   private void appendExceptionStackTrace(final StringBuilder stringBuilder, final Throwable throwable)
   {
      stringBuilder.append("Exception stack trace:\n\n");
      final StringWriter stringWriter = new StringWriter(1000);
      final PrintWriter printWriter = new PrintWriter(stringWriter);
      throwable.printStackTrace(printWriter);
      printWriter.flush();
      printWriter.close();

      stringBuilder.append(stringWriter.toString());
   }


   private void appendExecutionProfile(final Formatter formatter)
   {
      if (LauncherConstants.IsDevelopmentProfile)
         formatter.format("%n[Email sent from development profile.]");
      else if (LauncherConstants.IsTestProfile)
         formatter.format("%n[Email sent from test profile.]");
   }


   private void processSendErrorReport(final String recipientEmail, final String messageSubject, final String messageText) throws MessagingException
   {
      final Properties properties = new Properties();
      properties.put("mail.smtp.host", SMTPMXServer);

      final Session session = Session.getInstance(properties);

      final Message message = new MimeMessage(session);
      message.setFrom(new InternetAddress(ErrorReportsSender));
      message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail));
      message.setSubject(messageSubject);
      message.setSentDate(new Date());
      message.setText(messageText);

      Transport.send(message);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void sendErrorReport(final ExceptionReportContext errorReportContext) throws MessagingException
   {
      handleSendErrorReport(errorReportContext);
   }
}