
package com.feedbactory.server.core.log;


public enum SystemLogLevel
{
   Debug((byte) 0),
   ClientStateError((byte) 1),
   ErroneousClientRequest((byte) 2),
   Warning((byte) 3),
   OverloadError((byte) 4),
   ApplicationError((byte) 5),
   None(Byte.MAX_VALUE);

   final public byte value;


   private SystemLogLevel(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public SystemLogLevel fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return Debug;
         case 1:
            return ClientStateError;
         case 2:
            return ErroneousClientRequest;
         case 3:
            return Warning;
         case 4:
            return OverloadError;
         case 5:
            return ApplicationError;
         case Byte.MAX_VALUE:
            return None;

         default:
            // Let the caller decide how to handle an unrecognised value.
            return null;
      }
   }
}