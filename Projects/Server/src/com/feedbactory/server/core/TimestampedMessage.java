
package com.feedbactory.server.core;


import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.Message;


final public class TimestampedMessage
{
   static final public TimestampedMessage NoMessage = new TimestampedMessage(Message.NoMessage, FeedbactoryConstants.NoTime);

   final public Message message;
   final public long messageTime;


   public TimestampedMessage(final Message message, final long messageTime)
   {
      this.message = message;
      this.messageTime = messageTime;
   }
}