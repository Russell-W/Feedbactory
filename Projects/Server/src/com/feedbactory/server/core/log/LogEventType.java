
package com.feedbactory.server.core.log;


enum LogEventType
{
   SystemEvent((byte) 0),
   SecurityEvent((byte) 1);

   final byte value;


   private LogEventType(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static LogEventType fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return SystemEvent;
         case 1:
            return SecurityEvent;

         default:
            // Let the caller decide how to handle an unrecognised value.
            return null;
      }
   }
}