
package com.feedbactory.server.core;


import com.feedbactory.server.core.log.SecurityLogLevel;


final public class FeedbactorySecurityException extends SecurityException
{
   final public SecurityLogLevel securityEventLevel;
   final public Class<?> originatingClass;


   public FeedbactorySecurityException(final Class<?> originatingClass, final String message)
   {
      this(SecurityLogLevel.Medium, originatingClass, message);
   }


   public FeedbactorySecurityException(final SecurityLogLevel securityEventLevel, final Class<?> originatingClass, final String message)
   {
      super(message);

      if (securityEventLevel == null)
         throw new IllegalArgumentException("Security log level cannot be null.");
      else if (originatingClass == null)
         throw new IllegalArgumentException("Originating class cannot be null.");
      else if (message == null)
         throw new IllegalArgumentException("Message cannot be null.");

      this.securityEventLevel = securityEventLevel;
      this.originatingClass = originatingClass;
   }
}