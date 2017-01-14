
package com.feedbactory.shared;


final public class Message
{
   static final public Message NoMessage = new Message(MessageType.NoMessage, null);

   final public MessageType messageType;
   final public String message;


   public Message(final MessageType messageType, final String message)
   {
      validate(messageType, message);

      this.messageType = messageType;
      this.message = message;
   }


   private void validate(final MessageType messageType, final String message)
   {
      if (messageType == null)
         throw new IllegalArgumentException("Message type cannot be null.");
      else if (messageType != MessageType.NoMessage)
      {
         if (message == null)
            throw new IllegalArgumentException("Message type " + messageType + " cannot have a null message string.");
      }
      else if (message != null)
         throw new IllegalArgumentException("NoMessage type must have a null message string.");
   }


   @Override
   final public boolean equals(final Object otherObject)
   {
      if (! (otherObject instanceof Message))
         return false;

      final Message otherMessage = (Message) otherObject;

      if (messageType != MessageType.NoMessage)
         return ((messageType == otherMessage.messageType) && message.equals(otherMessage.message));
      else
         return (otherMessage.messageType == MessageType.NoMessage);
   }


   @Override
   final public int hashCode()
   {
      int result = 35;

      result = (31 * result) + messageType.hashCode();
      result = (31 * result) + ((message != null) ? message.hashCode() : 0);

      return result;
   }
}