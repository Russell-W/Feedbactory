
package com.feedbactory.shared.network;


public enum RequestGatewayIdentifier
{
   Account((byte) 0),
   Feedback((byte) 1);

   final public byte value;


   private RequestGatewayIdentifier(final byte value)
   {
      this.value = value;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   static public RequestGatewayIdentifier fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return Account;
         case 1:
            return Feedback;

         default:
            return null;
      }
   }
}