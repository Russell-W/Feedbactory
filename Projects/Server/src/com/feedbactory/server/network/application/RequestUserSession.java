
package com.feedbactory.server.network.application;


import com.feedbactory.server.network.component.ClientIO;
import com.feedbactory.server.network.component.EntityID;
import com.feedbactory.server.network.component.buffer.ReadableByteBuffer;
import com.feedbactory.server.network.component.buffer.WritableByteBuffer;
import com.feedbactory.server.useraccount.FeedbactoryUserAccount;
import com.feedbactory.shared.network.SessionRequestType;


final public class RequestUserSession
{
   final public ClientIO clientIO;

   final public ReadableByteBuffer requestBuffer;
   final public WritableByteBuffer responseBuffer;

   final public SessionRequestType sessionRequestType;
   final public EntityID sessionID;
   final public FeedbactoryUserAccount account;


   RequestUserSession(final ClientIO clientIO, final ReadableByteBuffer requestBuffer, final WritableByteBuffer responseBuffer, final SessionRequestType sessionRequestType)
   {
      this(clientIO, requestBuffer, responseBuffer, sessionRequestType, null, null);
   }


   RequestUserSession(final ClientIO clientIO, final ReadableByteBuffer requestBuffer, final WritableByteBuffer responseBuffer, final SessionRequestType sessionRequestType,
                      final EntityID sessionID, final FeedbactoryUserAccount userAccount)
   {
      validate(clientIO, requestBuffer, responseBuffer, sessionRequestType);

      this.clientIO = clientIO;

      this.requestBuffer = requestBuffer;
      this.responseBuffer = responseBuffer;

      this.sessionRequestType = sessionRequestType;
      this.sessionID = sessionID;
      this.account = userAccount;
   }


   private void validate(final ClientIO clientIO, final ReadableByteBuffer requestBuffer, final WritableByteBuffer responseBuffer, final SessionRequestType sessionRequestType)
   {
      if (clientIO == null)
         throw new IllegalArgumentException("Client IO cannot be null.");
      else if (requestBuffer == null)
         throw new IllegalArgumentException("Request buffer cannot be null.");
      else if (responseBuffer == null)
         throw new IllegalArgumentException("Response buffer cannot be null.");
      else if (sessionRequestType == null)
         throw new IllegalArgumentException("Session request type cannot be null.");
   }
}