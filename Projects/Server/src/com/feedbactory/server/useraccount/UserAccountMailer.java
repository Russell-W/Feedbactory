/* Memos:
 * - As with the server controller thread pool, there's no explicit limit on the mailer thread pool's queue size. This means that in theory, the queue of waiting tasks
 *   could be backed up until the JVM runs out of memory. In practice, a huge number of mail tasks would have to be submitted to reach this point, maybe only
 *   conceivable if a malicious client continually pushes mail requests or if the SMTP server itself is having problems. In the former case the spamming client would
 *   fairly quickly be cut off from automated requests by the IP request monitor.
 */

package com.feedbactory.server.useraccount;


import com.feedbactory.server.FeedbactoryServer;
import com.feedbactory.server.FeedbactoryServer.ExecutionProfile;
import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.log.SystemLogLevel;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;


final public class UserAccountMailer
{
   static final private String SMTPAuthenticationEmail = "<insert SMTP authentication email here>";
   static final private String SMTPCredentials = "<insert SMTP authentication email password here>";
   static final private String SMTPServer = "<insert SMTP server address here>";

   static final private String SenderEmail = "accounts@feedbactory.com";

   static final private String DevelopmentTestRecipient = "<insert development & test profile gmail recipient (username only) here>";
   static final private String DevelopmentTestRecipientDomain = "gmail.com";
   static final private String DevelopmentTestSenderEmail = DevelopmentTestRecipient + '@' + DevelopmentTestRecipientDomain;
   static final private String DevelopmentRecipientEmail = DevelopmentTestRecipient + "+development.accounts" + '@' + DevelopmentTestRecipientDomain;
   static final private String TestRecipientEmail = DevelopmentTestRecipient + "+test.accounts" + '@' + DevelopmentTestRecipientDomain;

   final private ThreadPoolExecutor mailerExecutor = createMailerThreadPool();


   private ThreadPoolExecutor createMailerThreadPool()
   {
      final RejectedExecutionHandler rejectedExecutionHandler = new RejectedExecutionHandler()
      {
         @Override
         final public void rejectedExecution(final Runnable runnable, final ThreadPoolExecutor executor)
         {
            handleThreadPoolRejectedExecutionException((MailerTask) runnable);
         }
      };

      final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
                                                                   new LinkedBlockingQueue<>(), rejectedExecutionHandler);
      threadPool.allowCoreThreadTimeOut(true);

      return threadPool;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class UserAccountMailerMetrics
   {
      final public int mailerThreadPoolQueueSize;
      final public long mailerThreadPoolTasksCompleted;


      private UserAccountMailerMetrics(final int mailerThreadPoolQueueSize, final long mailerThreadPoolTasksCompleted)
      {
         this.mailerThreadPoolQueueSize = mailerThreadPoolQueueSize;
         this.mailerThreadPoolTasksCompleted = mailerThreadPoolTasksCompleted;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class MailerTask implements Runnable
   {
      final private String recipientEmail;
      final private String subject;
      final private String messageText;


      private MailerTask(final String recipientEmail, final String subject, final String messageText)
      {
         this.recipientEmail = recipientEmail;
         this.subject = subject;
         this.messageText = messageText;
      }


      @Override
      final public void run()
      {
         processSendEmail();
      }


      private void processSendEmail()
      {
         final Properties properties = new Properties();
         properties.put("mail.smtp.auth", "true");
         properties.put("mail.smtp.ssl.enable", "true");
// Disable STARTTLS for Fastmail.
//         properties.put("mail.smtp.starttls.enable", "true");
         properties.put("mail.smtp.host", SMTPServer);
         properties.put("mail.smtp.port", "465");

         final Session session = Session.getInstance(properties);

         try
         {
            final Message message = new MimeMessage(session);
            message.setSentDate(new Date());

            final ExecutionProfile executionProfile = FeedbactoryServer.getExecutionProfile();
            if (executionProfile == ExecutionProfile.Development)
            {
               message.setFrom(new InternetAddress(DevelopmentTestSenderEmail));
               message.setSubject("[Development] " + subject);
               message.setRecipient(Message.RecipientType.TO, new InternetAddress(DevelopmentRecipientEmail));
               message.setText(messageText + "\n[Email sent from development profile, for recipient: " + recipientEmail + ']');
            }
            else if (executionProfile == ExecutionProfile.Test)
            {
               message.setFrom(new InternetAddress(DevelopmentTestSenderEmail));
               message.setSubject("[Test] " + subject);
               message.setRecipient(Message.RecipientType.TO, new InternetAddress(TestRecipientEmail));
               message.setText(messageText + "\n[Email sent from test profile, for recipient: " + recipientEmail + ']');
            }
            else if (executionProfile == ExecutionProfile.Production)
            {
               message.setFrom(new InternetAddress(SenderEmail));
               message.setSubject(subject);
               message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail));
               message.setText(messageText);
            }
            else
               throw new AssertionError("Unknown or misconfigured execution profile.");

            Transport.send(message, SMTPAuthenticationEmail, SMTPCredentials);
         }
         catch (final MessagingException messagingException)
         {
            final String errorString = "Mailer task failed. Recipient: " + recipientEmail + ", subject: " + subject;
            FeedbactoryLogger.logSystemEvent(SystemLogLevel.ApplicationError, getClass(), errorString, messagingException);
         }
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void handleThreadPoolRejectedExecutionException(final MailerTask mailerTask)
   {
      final String errorString = "Could not execute mailer task. Recipient: " + mailerTask.recipientEmail + ", subject: " + mailerTask.subject;
      FeedbactoryLogger.logSystemEvent(SystemLogLevel.OverloadError, getClass(), errorString);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void sendEmail(final String recipientEmail, final String subject, final String messageText)
   {
      mailerExecutor.execute(new MailerTask(recipientEmail, subject, messageText));
   }


   final UserAccountMailerMetrics getMetrics()
   {
      return new UserAccountMailerMetrics(mailerExecutor.getQueue().size(), mailerExecutor.getCompletedTaskCount());
   }


   final void shutdown() throws InterruptedException
   {
      mailerExecutor.shutdown();
      mailerExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
   }
}