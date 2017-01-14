
package com.feedbactory.server.core.log;


import com.feedbactory.server.network.application.RequestUserSession;
import com.feedbactory.server.network.component.ClientIO;


final public class SecurityEvent
{
   final SecurityLogLevel level;
   final Class<?> originatingClass;
   final String message;
   final ClientIO clientIO;
   final RequestUserSession userSession;


   public SecurityEvent(final SecurityLogLevel level, final Class<?> originatingClass, final String message, final ClientIO clientIO)
   {
      validate(level, originatingClass, message);
      if (clientIO == null)
         throw new IllegalArgumentException("ClientIO cannot be null.");

      this.level = level;
      this.originatingClass = originatingClass;
      this.message = message;
      this.clientIO = clientIO;
      this.userSession = null;
   }


   public SecurityEvent(final SecurityLogLevel level, final Class<?> originatingClass, final String message, final RequestUserSession userSession)
   {
      validate(level, originatingClass, message);
      if (userSession == null)
         throw new IllegalArgumentException("RequestUserSession cannot be null.");

      this.level = level;
      this.originatingClass = originatingClass;
      this.message = message;
      this.clientIO = null;
      this.userSession = userSession;
   }


   private void validate(final SecurityLogLevel level, final Class<?> originatingClass, final String message)
   {
      if (level == null)
         throw new IllegalArgumentException("Security log level cannot be null.");
      else if (originatingClass == null)
         throw new IllegalArgumentException("Originating class cannot be null.");
      else if (message == null)
         throw new IllegalArgumentException("Message cannot be null.");
   }
}