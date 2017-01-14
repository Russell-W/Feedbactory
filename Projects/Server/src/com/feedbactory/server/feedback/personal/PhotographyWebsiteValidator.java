
package com.feedbactory.server.feedback.personal;


abstract public class PhotographyWebsiteValidator extends CriteriaFeedbackWebsiteValidator
{
   final protected boolean validateTitlePhotographerDisplayName(final String displayName)
   {
      /* Display name string format: <photoTitle>\0<photographerDisplayName>
       *
       * photoTitle may be empty, in which case it can be resolved to 'Untitled'.
       * photographerDisplayName may be empty, in which case it can be resolved to 'Unknown'.
       *
       * First check the minimum possible length of the displayName, and for illegal leading spaces for the photo title, and
       * illegal trailing spaces for the user display name.
       * Note that the separator character (\0) is not whitespace.
       */
      if ((displayName != null) && (displayName.length() >= 1) &&
          (! Character.isWhitespace(displayName.charAt(0))) &&
          (! Character.isWhitespace(displayName.charAt(displayName.length() - 1))))
      {
         final int separatorCharacterIndex = displayName.indexOf('\0');

         /* Check that:
          * - There is one and only one separator character.
          * - There is no trailing whitespace after the photo title (if it's non-empty), and no leading whitespace before the user display name (if it's non-empty).
          */
         return ((separatorCharacterIndex >= 0) && (displayName.indexOf('\0', separatorCharacterIndex + 1) == -1) &&
                 ((separatorCharacterIndex == 0) || (! Character.isWhitespace(displayName.charAt(separatorCharacterIndex - 1)))) &&
                 ((separatorCharacterIndex == (displayName.length() - 1)) || (! Character.isWhitespace(displayName.charAt(separatorCharacterIndex + 1)))));
      }

      return false;
   }


   final protected boolean validateNonWhitespaceDelimitedField(final String fieldString, final int elementCount)
   {
      if (fieldString != null)
      {
         int separatorsFound = 0;
         int lastSeparatorIndex = -1;

         for (int characterIndex = 0; characterIndex < fieldString.length(); characterIndex ++)
         {
            if (fieldString.charAt(characterIndex) == '\0')
            {
               separatorsFound ++;

               if ((separatorsFound >= elementCount) ||
                   ((characterIndex - lastSeparatorIndex) <= 1))
                  return false;

               lastSeparatorIndex = characterIndex;
            }
            else if (Character.isWhitespace(fieldString.charAt(characterIndex)))
               return false;
         }

         // Check that the correct number of ID elements were found, and that the final element is not empty.
         return ((separatorsFound == (elementCount - 1)) && (lastSeparatorIndex < (fieldString.length() - 1)));
      }

      return false;
   }
}