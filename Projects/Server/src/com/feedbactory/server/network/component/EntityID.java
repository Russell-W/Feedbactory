
package com.feedbactory.server.network.component;


import java.security.SecureRandom;
import java.util.Arrays;


final public class EntityID
{
   final private byte[] ID;


   public EntityID(final int length)
   {
      ID = new byte[length];
      SecureRandomLazyInitialisationHolder.secureRandom.nextBytes(ID);
   }


   public EntityID(final byte[] ID)
   {
      this.ID = ID.clone();
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final private class SecureRandomLazyInitialisationHolder
   {
      /* Random instances are threadsafe according to the docs.
       * If we experience too much contention, we could consider using a ThreadLocalRandom.. which is not a secure random..
       */
      static final private SecureRandom secureRandom = new SecureRandom();
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final public int getSize()
   {
      return ID.length;
   }


   final public byte[] asByteArray()
   {
      return ID.clone();
   }


   @Override
   final public int hashCode()
   {
      return Arrays.hashCode(ID);
   }


   @Override
   final public boolean equals(final Object otherObject)
   {
      if (otherObject instanceof EntityID)
         return Arrays.equals(this.ID, ((EntityID) otherObject).ID);

      return false;
   }


   @Override
   final public String toString()
   {
      return Arrays.toString(ID);
   }
}