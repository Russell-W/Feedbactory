
package com.feedbactory.server.feedback.personal.service;


import com.feedbactory.server.feedback.personal.CriteriaFeedbackWebsiteAttributes;
import com.feedbactory.server.feedback.personal.CriteriaFeedbackWebsiteValidator;
import com.feedbactory.server.feedback.personal.PhotographyWebsiteValidator;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.service.SmugMug;


final public class SmugMugAttributes implements CriteriaFeedbackWebsiteAttributes
{
   final private CriteriaFeedbackWebsiteValidator validator = new SmugMugValidator();


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class SmugMugValidator extends PhotographyWebsiteValidator
   {
      @Override
      final public boolean validateUserID(final String userID)
      {
         return isNonEmptyWithNoWhitespace(userID);
      }


      @Override
      final public boolean validateDisplayName(final String displayName)
      {
         // Display name is the empty string, or a non-empty string with no leading or trailing whitespace.
         return ((displayName != null) &&
                 (displayName.isEmpty() ||
                  ((! Character.isWhitespace(displayName.charAt(0))) && (! Character.isWhitespace(displayName.charAt(displayName.length() - 1))))));
      }


      @Override
      final public boolean validatePhotoURL(final String photoURL)
      {
         /* Photo URL string format: <farmID>\0<serverID>\0<secretID>
          * There must not be any whitespaces anywhere in the string, and exactly three non-empty ID elements.
          */
         return validateNonWhitespaceDelimitedField(photoURL, 3);
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
      return SmugMug.instance;
   }


   @Override
   final public CriteriaFeedbackWebsiteValidator getValidator()
   {
      return validator;
   }
}