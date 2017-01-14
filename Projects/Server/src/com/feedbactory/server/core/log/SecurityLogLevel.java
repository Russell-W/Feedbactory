
package com.feedbactory.server.core.log;


public enum SecurityLogLevel
{
   Low((byte) 0),
   Medium((byte) 1),
   High((byte) 2),
   None(Byte.MAX_VALUE);

   final public byte value;


   private SecurityLogLevel(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public SecurityLogLevel fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return Low;
         case 1:
            return Medium;
         case 2:
            return High;
         case Byte.MAX_VALUE:
            return None;

         default:
            // Let the caller decide how to handle an unrecognised value.
            return null;
      }
   }
}