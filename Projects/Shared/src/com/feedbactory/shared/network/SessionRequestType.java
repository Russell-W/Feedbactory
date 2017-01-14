
package com.feedbactory.shared.network;


public enum SessionRequestType
{
   None((byte) 0),
   InitiateSession((byte) 1),
   RegularSessionRequest((byte) 2),
   EncryptedSessionRequest((byte) 3),
   ResumeSession((byte) 4),
   EndSession(Byte.MAX_VALUE);

   final public byte value;


   private SessionRequestType(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   static public SessionRequestType fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return None;
         case 1:
            return InitiateSession;
         case 2:
            return RegularSessionRequest;
         case 3:
            return EncryptedSessionRequest;
         case 4:
            return ResumeSession;
         case Byte.MAX_VALUE:
            return EndSession;

         default:
            return null;
      }
   }
}