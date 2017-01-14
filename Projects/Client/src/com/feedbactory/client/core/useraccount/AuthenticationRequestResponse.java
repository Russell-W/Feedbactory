
package com.feedbactory.client.core.useraccount;


import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.shared.network.AuthenticationStatus;


final public class AuthenticationRequestResponse
{
   final public NetworkRequestStatus networkStatus;
   final public AuthenticationStatus authenticationResult;


   AuthenticationRequestResponse(final NetworkRequestStatus networkStatus)
   {
      this(networkStatus, null);
   }


   AuthenticationRequestResponse(final NetworkRequestStatus networkStatus, final AuthenticationStatus authenticationResult)
   {
      this.networkStatus = networkStatus;
      this.authenticationResult = authenticationResult;
   }
}