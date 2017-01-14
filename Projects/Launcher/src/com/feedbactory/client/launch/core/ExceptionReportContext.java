
package com.feedbactory.client.launch.core;


final public class ExceptionReportContext
{
   final public long localErrorTime;
   final public Class<?> contextClass;
   final public String contextMessage;
   final public Thread thread;
   final public Throwable throwable;


   public ExceptionReportContext(final Class<?> contextClass, final String contextMessage, final Thread thread, final Throwable throwable)
   {
      this(System.currentTimeMillis(), contextClass, contextMessage, thread, throwable);
   }


   public ExceptionReportContext(final long localErrorTime, final Class<?> contextClass, final String contextMessage, final Thread thread, final Throwable throwable)
   {
      this.localErrorTime = localErrorTime;
      this.contextClass = contextClass;
      this.contextMessage = contextMessage;
      this.thread = thread;
      this.throwable = throwable;
   }
}