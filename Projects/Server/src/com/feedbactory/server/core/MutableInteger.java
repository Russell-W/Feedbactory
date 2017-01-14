
package com.feedbactory.server.core;


final public class MutableInteger
{
   private int value;


   public MutableInteger(final int value)
   {
      this.value = value;
   }


   final public int get()
   {
      return value;
   }


   final public void set(final int value)
   {
      this.value = value;
   }


   final public void increment()
   {
      value ++;
   }


   final public void decrement()
   {
      value --;
   }
}