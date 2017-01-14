
package com.feedbactory.shared.network;


public enum FeedbactoryApplicationServerStatus
{
   Available((byte) 0),
   Busy((byte) 1),
   NotAvailable((byte) 2);

   final public byte value;


   private FeedbactoryApplicationServerStatus(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public FeedbactoryApplicationServerStatus fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return Available;
         case 1:
            return Busy;
         case 2:
            return NotAvailable;

         default:
            return null;
      }
   }
}