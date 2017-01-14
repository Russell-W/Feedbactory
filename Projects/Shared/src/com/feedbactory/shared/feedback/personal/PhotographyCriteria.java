/* Memos:
 * - Add Processing?
 * - Replace Focus with Depth of Field?
 * - YouPic criteria: Composition, Creativity, Technical Quality, Content
 */

package com.feedbactory.shared.feedback.personal;


public enum PhotographyCriteria implements PersonalFeedbackCriteria
{
   Colours((byte) 0, "Colours"),
   Composition((byte) 1, "Composition"),
   Content((byte) 2, "Content"),
   Creativity((byte) 3, "Creativity"),
   Exposure((byte) 4, "Exposure"),
   Focus((byte) 5, "Focus"),
   Lighting((byte) 6, "Lighting"),
   Timing((byte) 7, "Timing"),
   ViewerImpact((byte) 8, "Viewer impact");


   private PhotographyCriteria(final byte value, final String displayName)
   {
      this.value = value;
      this.displayName = displayName;
   }


   final private byte value;
   final private String displayName;


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public byte getValue()
   {
      return value;
   }


   @Override
   final public String getDisplayName()
   {
      return displayName;
   }


   @Override
   final public String toString()
   {
      return displayName;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public PhotographyCriteria fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return Colours;
         case 1:
            return Composition;
         case 2:
            return Content;
         case 3:
            return Creativity;
         case 4:
            return Exposure;
         case 5:
            return Focus;
         case 6:
            return Lighting;
         case 7:
            return Timing;
         case 8:
            return ViewerImpact;

         /* No exception thrown here - let the caller decide how to react to a null value, whether it be throwing a security exception (eg. reading
          * a record on the server), or throwing an illegal state exception or similar on the client.
          */
         default:
            return null;
      }
   }
}
