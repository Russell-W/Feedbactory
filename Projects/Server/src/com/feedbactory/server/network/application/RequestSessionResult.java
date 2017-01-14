
package com.feedbactory.server.network.application;


import com.feedbactory.server.network.component.EntityID;
import com.feedbactory.shared.network.SessionRequestType;


final class RequestSessionResult
{
   static final RequestSessionResult NoSession = new RequestSessionResult(SessionRequestType.None);

   final SessionRequestType requestType;
   final SessionAuthentication authentication;
   final SessionEncryption encryption;

   final EntityID sessionID;


   private RequestSessionResult(final SessionRequestType sessionRequestType)
   {
      this(sessionRequestType, null, null, null);
   }


   RequestSessionResult(final SessionRequestType sessionRequestType, final SessionAuthentication authentication)
   {
      this(sessionRequestType, authentication, null, null);
   }


   RequestSessionResult(final SessionRequestType sessionRequestType, final SessionAuthentication authentication, final SessionEncryption sessionEncryption, final EntityID sessionID)
   {
      validate(sessionRequestType);

      this.requestType = sessionRequestType;
      this.authentication = authentication;
      this.encryption = sessionEncryption;
      this.sessionID = sessionID;
   }


   private void validate(final SessionRequestType sessionRequestType)
   {
      if (sessionRequestType == null)
         throw new IllegalArgumentException("Session request type cannot be null.");
   }
}