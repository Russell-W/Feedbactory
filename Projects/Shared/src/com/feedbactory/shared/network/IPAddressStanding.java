
package com.feedbactory.shared.network;


public enum IPAddressStanding
{
   OK((byte) 0),
   TemporarilyBlocked((byte) 1),
   Blacklisted((byte) 2);

   final public byte value;


   private IPAddressStanding(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public IPAddressStanding fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return OK;
         case 1:
            return TemporarilyBlocked;
         case 2:
            return Blacklisted;
         default:
            return null;
      }
   }
}