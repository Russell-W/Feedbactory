
package com.feedbactory.client.core.network;


import com.feedbactory.shared.network.FeedbactoryApplicationServerStatus;
import com.feedbactory.shared.Message;


final public class ServerAvailability
{
   static final public ServerAvailability NotAvailable = new ServerAvailability(FeedbactoryApplicationServerStatus.NotAvailable, Message.NoMessage);

   final public FeedbactoryApplicationServerStatus serverStatus;
   final public Message unavailableMessage;


   ServerAvailability(final FeedbactoryApplicationServerStatus serverStatus)
   {
      this(serverStatus, Message.NoMessage);
   }


   ServerAvailability(final FeedbactoryApplicationServerStatus serverStatus, final Message unavailableMessage)
   {
      validate(serverStatus, unavailableMessage);

      this.serverStatus = serverStatus;
      this.unavailableMessage = unavailableMessage;
   }


   private void validate(final FeedbactoryApplicationServerStatus serverStatus, final Message unavailableMessage)
   {
      if (serverStatus == null)
         throw new IllegalArgumentException("Server status cannot be null.");
      else if (unavailableMessage == null)
         throw new IllegalArgumentException("Message cannot be null.");
   }


   @Override
   final public boolean equals(final Object otherObject)
   {
      if (! (otherObject instanceof ServerAvailability))
         return false;

      final ServerAvailability otherServerAvailability = (ServerAvailability) otherObject;

      return ((serverStatus == otherServerAvailability.serverStatus) && (unavailableMessage.equals(otherServerAvailability.unavailableMessage)));
   }



   @Override
   final public int hashCode()
   {
      int result = 35;

      result = (31 * result) + serverStatus.hashCode();
      result = (31 * result) + unavailableMessage.hashCode();

      return result;
   }
}