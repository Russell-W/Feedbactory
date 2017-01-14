
package com.feedbactory.shared.network;


public enum ClientCompatibilityStatus
{
   UpToDate((byte) 0),
   UpdateAvailable((byte) 1),
   UpdateRequired((byte) 2);

   final public byte value;


   private ClientCompatibilityStatus(final byte value)
   {
      this.value = value;
   }


   static public ClientCompatibilityStatus fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return UpToDate;
         case 1:
            return UpdateAvailable;
         case 2:
            return UpdateRequired;

         default:
            return null;
      }
   }
}