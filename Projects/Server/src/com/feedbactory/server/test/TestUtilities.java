
package com.feedbactory.server.test;


import java.util.concurrent.ThreadLocalRandom;


final public class TestUtilities
{
   static final private char[] symbols;

   static
   {
      final StringBuilder builder = new StringBuilder(100);

      for (char x = '0'; x <= '9'; x ++)
         builder.append(x);

      for (char x = 'a'; x <= 'z'; x ++)
      {
         builder.append(x);
         builder.append(Character.toUpperCase(x));
      }

      symbols = builder.toString().toCharArray();
   }


   private TestUtilities()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private String handleCreateRandomAlphanumericString(final int size)
   {
      final char[] charArray = new char[size];
      for (int charIndex = 0; charIndex < charArray.length; charIndex ++)
         charArray[charIndex] = symbols[ThreadLocalRandom.current().nextInt(symbols.length)];

      return new String(charArray);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public String createRandomAlphanumericString(final int size)
   {
      return handleCreateRandomAlphanumericString(size);
   }


   static public String createRandomAlphanumericString(final int minimumSize, final int maximumSize)
   {
      return handleCreateRandomAlphanumericString(ThreadLocalRandom.current().nextInt(minimumSize, maximumSize));
   }
}