
package com.feedbactory.server.feedback.personal.service;


import com.feedbactory.server.feedback.personal.CriteriaFeedbackWebsiteAttributes;
import com.feedbactory.server.feedback.personal.CriteriaFeedbackWebsiteValidator;
import com.feedbactory.server.feedback.personal.PhotographyWebsiteValidator;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.service.FiveHundredPX;


final public class FiveHundredPXAttributes implements CriteriaFeedbackWebsiteAttributes
{
   final private CriteriaFeedbackWebsiteValidator validator = new FiveHundredPXValidator();


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class FiveHundredPXValidator extends PhotographyWebsiteValidator
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
         /* Photo URL string format: <thumbnailURLID>\0<largeImageURLID>
          * There must not be any whitespaces anywhere in the string, and exactly two non-empty ID elements.
          */
         return validateNonWhitespaceDelimitedField(photoURL, 2);
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
      return FiveHundredPX.instance;
   }


   @Override
   final public CriteriaFeedbackWebsiteValidator getValidator()
   {
      return validator;
   }
}