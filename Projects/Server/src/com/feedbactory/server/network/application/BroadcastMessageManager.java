
package com.feedbactory.server.network.application;


import com.feedbactory.server.core.TimestampedMessage;
import com.feedbactory.shared.Message;
import com.feedbactory.server.core.TimeCache;
import com.feedbactory.server.network.component.buffer.WritableByteBuffer;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.MessageType;


final class BroadcastMessageManager
{
   volatile private TimestampedMessage broadcastMessage = TimestampedMessage.NoMessage;


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleSetBroadcastMessage(final Message broadcastMessage)
   {
      if (broadcastMessage == null)
         throw new IllegalArgumentException("Broadcast message cannot be null.");

      this.broadcastMessage = new TimestampedMessage(broadcastMessage, TimeCache.getCurrentTimeMilliseconds());
   }


   private void handleWriteGeneralBroadcastMessageToBuffer(final long clientLastRequestTime, final WritableByteBuffer buffer)
   {
      final TimestampedMessage activeBroadcastMessage = broadcastMessage;

      /* It's important to use the <= comparison here on the timestamps, since they are taken from a cache where identical values will be returned to
       * sequential calls within a short time frame. Consider the following sequence of events:
       * 
       * 1) A user's first request, gets response of server time A; no broadcast message currently set.
       * 2) Admin sets a broadcast message immediately after 1), and because of the time cache effect the broadcast message is also stamped with server time A.
       * 3) The user makes a follow-up request, using timestamp A as an indicator of its last contact with the server. If the < operator is used here,
       *    the user misses out on the new broadcast message since it's believed that they must have already received it.
       *
       * Note that in the response header the server's time cache value A is grabbed and written before the check for the broadcast message here is performed.
       * So if the broadcast message (and its timestamp B) are updated after A is written but before this method is called, the message will be available
       * and written; and if the broadcast message and timestamp B are updated by admin just after the active broadcast message has been fetched by the request here
       * (first line), the timestamp A must be <= B, and the message will be available on the user's next request.
       */
      if ((activeBroadcastMessage.message.messageType != MessageType.NoMessage) &&
         ((clientLastRequestTime == FeedbactoryConstants.NoTime) || (clientLastRequestTime <= activeBroadcastMessage.messageTime)))
      {
         buffer.put(activeBroadcastMessage.message.messageType.value);
         buffer.putUTF8EncodedString(activeBroadcastMessage.message.message);
      }
      else
         buffer.put(Message.NoMessage.messageType.value);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final TimestampedMessage getBroadcastMessage()
   {
      return broadcastMessage;
   }


   final void setBroadcastMessage(final Message broadcastMessage)
   {
      handleSetBroadcastMessage(broadcastMessage);
   }


   final void writeBroadcastMessageToBuffer(final long clientLastRequestTime, final WritableByteBuffer buffer)
   {
      handleWriteGeneralBroadcastMessageToBuffer(clientLastRequestTime, buffer);
   }
}