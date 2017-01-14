
package com.feedbactory.server.feedback.personal;


abstract public class CriteriaFeedbackWebsiteValidator
{
   final protected boolean isNonEmptyWithNoLeadingOrTrailingWhitespace(final String string)
   {
      return ((string != null) && (string.length() > 0) &&
              (! Character.isWhitespace(string.charAt(0))) &&
              ((string.length() == 1) || (! Character.isWhitespace(string.charAt(string.length() - 1)))));
   }


   final protected boolean isNonEmptyWithNoWhitespace(final String string)
   {
      if ((string != null) && (string.length() > 0))
      {
         for (int characterIndex = 0; characterIndex < string.length(); characterIndex ++)
         {
            if (Character.isWhitespace(string.charAt(characterIndex)))
               return false;
         }

         return true;
      }

      return false;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   public boolean validateItemID(final String itemID)
   {
      return isNonEmptyWithNoWhitespace(itemID);
   }


   public boolean validateUserID(final String userID)
   {
      return true;
   }


   public boolean validateDisplayName(final String displayName)
   {
      return true;
   }


   public boolean validatePhotoURL(final String photoURL)
   {
      return true;
   }


   public boolean validateURL(final String URL)
   {
      return true;
   }
}