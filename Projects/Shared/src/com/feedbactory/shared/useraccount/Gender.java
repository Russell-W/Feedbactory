
package com.feedbactory.shared.useraccount;


public enum Gender
{
   Male((byte) 0),
   Female((byte) 1);

   final public byte value;


   private Gender(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   static public Gender fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return Male;
         case 1:
            return Female;

         default:
            return null;
      }
   }
}