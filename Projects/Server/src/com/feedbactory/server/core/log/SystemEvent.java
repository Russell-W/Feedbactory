
package com.feedbactory.server.core.log;


import com.feedbactory.server.network.component.ClientIO;
import com.feedbactory.server.network.component.ClientNetworkID;
import com.feedbactory.server.network.application.SessionEncryption;
import com.feedbactory.server.network.component.buffer.ReadableByteBuffer;
import javax.crypto.spec.SecretKeySpec;


final public class SystemEvent
{
   final SystemLogLevel level;
   final Class<?> originatingClass;
   final String message;
   final Throwable throwable;
   private ClientNetworkID clientNetworkID;
   private ReadableByteBuffer requestBuffer;
   private ClientIO clientIO;
   private SecretKeySpec secretKeySpec;
   private SessionEncryption sessionEncryption;


   public SystemEvent(final SystemLogLevel level, final Class<?> originatingClass, final String message)
   {
      validate(level, originatingClass, message);

      this.level = level;
      this.originatingClass = originatingClass;
      this.message = message;
      this.throwable = null;
   }


   public SystemEvent(final SystemLogLevel level, final Class<?> originatingClass, final String message, final Throwable throwable)
   {
      validate(level, originatingClass, message);

      this.level = level;
      this.originatingClass = originatingClass;
      this.message = message;
      this.throwable = throwable;
   }


   private void validate(final SystemLogLevel level, final Class<?> originatingClass, final String message)
   {
      if (level == null)
         throw new IllegalArgumentException("System log level cannot be null.");
      else if (originatingClass == null)
         throw new IllegalArgumentException("Originating class cannot be null.");
      else if (message == null)
         throw new IllegalArgumentException("Message cannot be null.");
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public ClientNetworkID getClientNetworkID()
   {
      return clientNetworkID;
   }


   final public void setClientNetworkID(final ClientNetworkID clientNetworkID)
   {
      this.clientNetworkID = clientNetworkID;
   }


   final public ReadableByteBuffer getRequestBuffer()
   {
      return requestBuffer;
   }


   final public void setRequestBuffer(final ReadableByteBuffer requestBuffer)
   {
      this.requestBuffer = requestBuffer;
   }


   final public ClientIO getClientIO()
   {
      return clientIO;
   }


   final public void setClientIO(final ClientIO clientIO)
   {
      this.clientIO = clientIO;
   }


   final public SecretKeySpec getSecretKeySpec()
   {
      return secretKeySpec;
   }


   final public void setSecretKeySpec(final SecretKeySpec secretKeySpec)
   {
      this.secretKeySpec = secretKeySpec;
   }


   final public SessionEncryption getSessionEncryption()
   {
      return sessionEncryption;
   }


   final public void setSessionEncryption(final SessionEncryption sessionEncryption)
   {
      this.sessionEncryption = sessionEncryption;
   }
}