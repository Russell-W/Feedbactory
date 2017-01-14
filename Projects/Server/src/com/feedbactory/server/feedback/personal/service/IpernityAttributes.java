
package com.feedbactory.server.feedback.personal.service;


import com.feedbactory.server.feedback.personal.CriteriaFeedbackWebsiteAttributes;
import com.feedbactory.server.feedback.personal.CriteriaFeedbackWebsiteValidator;
import com.feedbactory.server.feedback.personal.PhotographyWebsiteValidator;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.service.Ipernity;


final public class IpernityAttributes implements CriteriaFeedbackWebsiteAttributes
{
   final private CriteriaFeedbackWebsiteValidator validator = new IpernityValidator();


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class IpernityValidator extends PhotographyWebsiteValidator
   {
      @Override
      final public boolean validateUserID(final String userID)
      {
         return isNonEmptyWithNoWhitespace(userID);
      }


      @Override
      final public boolean validateDisplayName(final String displayName)
      {
         return validateTitlePhotographerDisplayName(displayName);
      }


      @Override
      final public boolean validatePhotoURL(final String photoURL)
      {
         if ((photoURL == null) || photoURL.isEmpty())
            return false;

         int separatorsFound;
         int lastSeparatorIndex;

         /* Since late 2014 Ipernity have (mostly?) switched to a CDN and changed their thumbnail URL format from:
          *
          *   http://u<farmID>.ipernity.com/<pathID>/<photoID>.<secretID>.<resolution>.jpg?<url arguments>
          *   Thumbnail elements string: <farmID>\0<pathID>\0<secretID>
          *
          * to:
          *
          *   http://cdn.ipernity.com/<pathID>/<photoID>.<secretID>.<resolution>.jpg?<url arguments>
          *   Thumbnail elements string: \0<pathID>\0<secretID>
          *
          * Feedbactory is supporting both formats for now, since the old format must still be used in some cases (where the CDN produces dead image links).
          */
         if (photoURL.charAt(0) == '\0')
         {
            separatorsFound = 1;
            lastSeparatorIndex = 0;
         }
         else
         {
            separatorsFound = 0;
            lastSeparatorIndex = -1;
         }

         for (int characterIndex = 1; characterIndex < photoURL.length(); characterIndex ++)
         {
            if (photoURL.charAt(characterIndex) == '\0')
            {
               separatorsFound ++;

               if ((separatorsFound >= 3) ||
                   ((characterIndex - lastSeparatorIndex) <= 1))
                  return false;

               lastSeparatorIndex = characterIndex;
            }
            else if (Character.isWhitespace(photoURL.charAt(characterIndex)))
               return false;
         }

         // Check that the correct number of ID elements were found, and that the final element is not empty.
         return ((separatorsFound == 2) && (lastSeparatorIndex < (photoURL.length() - 1)));
      }


      @Override
      final public boolean validateURL(final String url)
      {
         return (url == null);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   @Override
   final public PersonalFeedbackWebsite getWebsite()
   {
      return Ipernity.instance;
   }


   @Override
   final public CriteriaFeedbackWebsiteValidator getValidator()
   {
      return validator;
   }
}