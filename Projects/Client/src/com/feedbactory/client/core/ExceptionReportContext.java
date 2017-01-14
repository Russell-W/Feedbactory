
package com.feedbactory.client.core;


import com.feedbactory.client.core.useraccount.FeedbactoryUserAccount;
import com.feedbactory.client.ui.browser.BrowserServicesDebugInformation;


final public class ExceptionReportContext
{
   final public ExceptionReportContextType exceptionContextType;

   final public Class<?> contextClass;
   final public String contextMessage;
   final public Thread thread;
   final public Throwable throwable;

   final public long localErrorTime;
   final public long approximateServerErrorTime;
   final public FeedbactoryUserAccount signedInUserAccount;
   final public BrowserServicesDebugInformation browserDebugInformation;


   public ExceptionReportContext(final ExceptionReportContextType exceptionContextType, final Class<?> contextClass, final String contextMessage, final Thread thread, final Throwable throwable, final long approximateServerErrorTime)
   {
      this(exceptionContextType, contextClass, contextMessage, thread, throwable, approximateServerErrorTime, null, null);
   }


   public ExceptionReportContext(final ExceptionReportContextType exceptionContextType, final Class<?> contextClass, final String contextMessage, final Thread thread, final Throwable throwable, final long approximateServerErrorTime, final FeedbactoryUserAccount signedInUserAccount, final BrowserServicesDebugInformation browserDebugInformation)
   {
      this(exceptionContextType, contextClass, contextMessage, thread, throwable, System.currentTimeMillis(), approximateServerErrorTime, signedInUserAccount, browserDebugInformation);
   }


   public ExceptionReportContext(final ExceptionReportContextType exceptionContextType, final Class<?> contextClass, final String contextMessage, final Thread thread, final Throwable throwable, final long localErrorTime, final long approximateServerErrorTime, final FeedbactoryUserAccount signedInUserAccount, final BrowserServicesDebugInformation browserDebugInformation)
   {
      this.exceptionContextType = exceptionContextType;
      this.contextClass = contextClass;
      this.contextMessage = contextMessage;
      this.thread = thread;
      this.throwable = throwable;
      this.localErrorTime = localErrorTime;
      this.approximateServerErrorTime = approximateServerErrorTime;
      this.signedInUserAccount = signedInUserAccount;
      this.browserDebugInformation = browserDebugInformation;
   }
}