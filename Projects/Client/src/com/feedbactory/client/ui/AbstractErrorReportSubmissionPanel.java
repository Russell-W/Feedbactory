
package com.feedbactory.client.ui;


import com.feedbactory.client.core.ConfigurationManager;
import com.feedbactory.client.core.ExceptionReportContext;
import com.feedbactory.client.core.ExceptionReportMailer;
import java.util.ArrayList;


class AbstractErrorReportSubmissionPanel
{
   final UIManager uiManager;
   final ExceptionReportMailer exceptionReportMailer;
   final ExceptionReportContext exceptionReportContext;


   AbstractErrorReportSubmissionPanel(final UIManager uiManager, final ExceptionReportMailer exceptionReportMailer, final ExceptionReportContext exceptionReportContext)
   {
      this.uiManager = uiManager;
      this.exceptionReportMailer = exceptionReportMailer;
      this.exceptionReportContext = exceptionReportContext;
   }


   final String getDialogTitle()
   {
      switch (exceptionReportContext.exceptionContextType)
      {
         case InitialisationException:
         case NoCompatibleBrowserException:
            return "Feedbactory Initialisation Error";
         case UncaughtException:
            return "Feedbactory Error";
         default:
            throw new AssertionError("Unhandled exception report context type value: " + exceptionReportContext.exceptionContextType);
      }
   }


   final String[] getDialogMessage()
   {
      switch (exceptionReportContext.exceptionContextType)
      {
         case InitialisationException:
            return getInitialisationExceptionMessage();
         case NoCompatibleBrowserException:
            return getNoCompatibleBrowserExceptionMessage();
         case UncaughtException:
            return new String[] {"An error has occurred in Feedbactory."};

         default:
            throw new AssertionError("Unhandled exception report context type value: " + exceptionReportContext.exceptionContextType);
      }
   }


   private String[] getInitialisationExceptionMessage()
   {
      final ArrayList<String> message = new ArrayList<String>();
      message.add("An error occurred during the initialisation of Feedbactory.");
      message.add("");
      message.add("If the error persists please try closing the program and");
      message.add("deleting the Feedbactory application folder, located under");

      if (ConfigurationManager.isRunningWindows)
         message.add("your home folder (eg. C:\\Users\\YourName\\.feedbactory).");
      else if (ConfigurationManager.isRunningMacOSX)
         message.add("your home folder (eg. /Users/YourName/.feedbactory).");
      else
         message.add("your home folder (eg. /home/YourName/.feedbactory).");

      return message.toArray(new String[message.size()]);
   }


   private String[] getNoCompatibleBrowserExceptionMessage()
   {
      final ArrayList<String> message = new ArrayList<String>();
      message.add("Sorry, Feedbactory could not find a compatible browser on your system.");

      if (! (ConfigurationManager.isRunningWindows || ConfigurationManager.isRunningMacOSX))
      {
         message.add("");
         message.add("On GTK+ Linux systems Feedbactory attempts to use WebKit by default.");
         message.add("WebKitGTK+ 1.2.0 or newer must be in the library load path.");
         message.add("Mozilla cannot be used with the current release of Feedbactory on");
         message.add("Linux as Mozilla XULRunner has not yet been ported to GTK+ 3.");
      }

      message.add("");
      message.add("Sending an error report will help us know more about which devices are");
      message.add("being used with Feedbactory so that we can improve the compatibility");
      message.add("of future releases.");

      return message.toArray(new String[message.size()]);
   }
}