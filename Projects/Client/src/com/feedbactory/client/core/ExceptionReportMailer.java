
package com.feedbactory.client.core;


import com.feedbactory.client.core.useraccount.FeedbactoryUserAccount;
import com.feedbactory.client.ui.browser.BrowserServicesDebugInformation;
import com.feedbactory.shared.FeedbactoryConstants;
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
   static final private String ErrorReportsFilter = "exceptionreports";
   static final private String BrowserFilter = "browser";
   static final private String ErrorReportsRecipientDomain = "gmail.com";
   static final private String ErrorReportsSender = ErrorReportsRecipient + '@' + ErrorReportsRecipientDomain;
   static final private String SMTPMXServer = "aspmx.l.google.com";


   ExceptionReportMailer()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSendErrorReport(final ExceptionReportAction exceptionReportAction, final ExceptionReportContext exceptionReportContext) throws MessagingException
   {
      if (exceptionReportAction == ExceptionReportAction.DoNotSendExceptionReport)
         throw new IllegalStateException();

      // Locale.US must always be available, according to the docs. That will work fine for the error report emails.
      final StringBuilder stringBuilder = new StringBuilder(5000);
      final Formatter errorFormatter = new Formatter(stringBuilder, Locale.US);

      final String recipientEmail = getRecipientEmail(exceptionReportContext.exceptionContextType);
      final String messageTitle = getMessageTitle(exceptionReportContext.exceptionContextType);

      appendClientVersion(errorFormatter);
      appendCurrentTime(errorFormatter, exceptionReportContext.localErrorTime, exceptionReportContext.approximateServerErrorTime);
      appendEnvironmentAttributes(errorFormatter);
      appendSignedInUserAccount(errorFormatter, exceptionReportContext.signedInUserAccount);
      appendBrowserDebugInformation(errorFormatter, exceptionReportAction, exceptionReportContext.browserDebugInformation);
      appendErrorContext(errorFormatter, exceptionReportContext.contextClass, exceptionReportContext.contextMessage, exceptionReportContext.thread);

      /* The errorFormatter is writing to the stringBuilder. The errorFormatter must have its buffers entirely flushed to the underlying stringBuilder
       * at this point, before the stack trace is appended directly to the stringBuilder.
       */
      errorFormatter.flush();
      appendExceptionStackTrace(stringBuilder, exceptionReportContext.throwable);

      appendExecutionProfile(errorFormatter);
      errorFormatter.flush();

      processSendErrorReport(recipientEmail, messageTitle, errorFormatter.toString());
   }


   private String getRecipientEmail(final ExceptionReportContextType exceptionContextType)
   {
      final StringBuilder emailBuilder = new StringBuilder(100);

      emailBuilder.append(ErrorReportsRecipient);
      emailBuilder.append('+');

      /* When comparing against final booleans (or ints) in this way, the compiler will remove the clauses that do not apply,
       * ie. the development and test error reporting email strings will not be embedded within the class files that are
       * deployed to production.
       */
      if (FeedbactoryClientConstants.IsDevelopmentProfile)
         emailBuilder.append("development.");
      else if (FeedbactoryClientConstants.IsTestProfile)
         emailBuilder.append("test.");

      emailBuilder.append(ErrorReportsFilter);
      if (exceptionContextType == ExceptionReportContextType.NoCompatibleBrowserException)
      {
         emailBuilder.append('.');
         emailBuilder.append(BrowserFilter);
      }

      emailBuilder.append('@');
      emailBuilder.append(ErrorReportsRecipientDomain);

      return emailBuilder.toString();
   }


   private String getMessageTitle(final ExceptionReportContextType exceptionContextType)
   {
      final StringBuilder titleStringBuilder = new StringBuilder(100);

      if (FeedbactoryClientConstants.IsDevelopmentProfile)
         titleStringBuilder.append("[Development] ");
      else if (FeedbactoryClientConstants.IsTestProfile)
         titleStringBuilder.append("[Test] ");

      switch (exceptionContextType)
      {
         case InitialisationException:
            titleStringBuilder.append("Feedbactory Launch Error Report");
            break;

         case UncaughtException:
            titleStringBuilder.append("Feedbactory Error Report");
            break;

         case NoCompatibleBrowserException:
            titleStringBuilder.append("Feedbactory Browser Error Report");
            break;

         default:
            throw new AssertionError("Unhandled exception report context type value: " + exceptionContextType);
      }

      return titleStringBuilder.toString();
   }


   private void appendClientVersion(final Formatter formatter)
   {
      formatter.format("%-25.25s%s%n", "Client version:", FeedbactoryClientConstants.DisplayableVersionID);
      formatter.format("%-25.25s%d%n%n", "", FeedbactoryClientConstants.VersionID);
   }


   private void appendCurrentTime(final Formatter formatter, final long errorTime, final long approximateServerErrorTime)
   {
      final String generalLabelValueFormat = "%-25.25s%s%n";

      formatter.format(generalLabelValueFormat, "Time zone:", TimeZone.getDefault().getID());
      final String timestampLabelValueFormat = "%-25.25s%tH:%<tM:%<tS%<tp %<tZ %<ta %<td/%<tm/%<tY%n";
      formatter.format(timestampLabelValueFormat, "Local error time:", errorTime);
      formatter.format("%25s%d%n", "", errorTime);
      formatter.format("%-25.25s%d%n%n", "UTC offset (ms):", TimeZone.getDefault().getOffset(errorTime));

      if (approximateServerErrorTime != FeedbactoryConstants.NoTime)
         formatter.format(timestampLabelValueFormat, "Approximate server time:", approximateServerErrorTime);
      else
         formatter.format(generalLabelValueFormat, "Approximate server time:", "Unknown (handshake not yet performed)");

      formatter.format("%n");
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


   private void appendSignedInUserAccount(final Formatter formatter, final FeedbactoryUserAccount signedInUserAccount)
   {
      formatter.format("%-25.25s%s%n%n", "Signed in user account:", (signedInUserAccount != null) ? signedInUserAccount.emailAddress : "None");
   }


   private void appendBrowserDebugInformation(final Formatter formatter, final ExceptionReportAction exceptionReportAction, final BrowserServicesDebugInformation browserDebugInformation)
   {
      if (browserDebugInformation != null)
      {
         final String generalLabelValueFormat = "%-25.25s%s%n";

         formatter.format(generalLabelValueFormat, "navigator.userAgent:", browserDebugInformation.browserUserAgent);
         formatter.format("%n");

         if (exceptionReportAction == ExceptionReportAction.SendCompleteExceptionReport)
         {
            formatter.format(generalLabelValueFormat, "Active tab location:", browserDebugInformation.getBrowserURL(browserDebugInformation.activeTabBrowserIndex));

            if (browserDebugInformation.getBrowserCount() > 1)
            {
               formatter.format("%n");
               formatter.format("%d other tab%s:%n", browserDebugInformation.getBrowserCount() - 1, (browserDebugInformation.getBrowserCount() > 2 ? "s" : ""));
               for (int tabIndex = 0; tabIndex < browserDebugInformation.getBrowserCount(); tabIndex ++)
               {
                  if (tabIndex != browserDebugInformation.activeTabBrowserIndex)
                     formatter.format("%25s%s%n", "", browserDebugInformation.getBrowserURL(tabIndex));
               }
            }

            formatter.format("%n");
         }
         else
            formatter.format("Browser locations have not been provided.%n%n");
      }
      else
         formatter.format("No browser windows have been initialised.%n%n");
   }


   private void appendErrorContext(final Formatter formatter, final Class<?> contextClass, final String contextMessage, final Thread thread)
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
      if (FeedbactoryClientConstants.IsDevelopmentProfile)
         formatter.format("%n[Email sent from development profile.]");
      else if (FeedbactoryClientConstants.IsTestProfile)
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


   final public void sendErrorReport(final ExceptionReportAction exceptionReportAction, final ExceptionReportContext exceptionReportContext) throws MessagingException
   {
      handleSendErrorReport(exceptionReportAction, exceptionReportContext);
   }
}