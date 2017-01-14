
package com.feedbactory.shared.network;


public enum BasicOperationStatus
{
   OK((byte) 0),
   Failed((byte) 1);


   final public byte value;


   private BasicOperationStatus(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public BasicOperationStatus fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return OK;
         case 1:
            return Failed;

         default:
            return null;
      }
   }
}