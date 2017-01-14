
package com.feedbactory.shared;


public enum MessageType
{
   NoMessage((byte) 0),
   InformationMessage((byte) 1),
   WarningMessage((byte) 2),
   ErrorMessage((byte) 3);

   final public byte value;


   private MessageType(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   static public MessageType fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return NoMessage;
         case 1:
            return InformationMessage;
         case 2:
            return WarningMessage;
         case 3:
            return ErrorMessage;

         default:
            return null;
      }
   }
}