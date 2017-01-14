
package com.feedbactory.server.feedback.personal.service;


import com.feedbactory.server.feedback.personal.CriteriaFeedbackWebsiteAttributes;
import com.feedbactory.server.feedback.personal.CriteriaFeedbackWebsiteValidator;
import com.feedbactory.server.feedback.personal.PhotographyWebsiteValidator;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.service.YouPic;


final public class YouPicAttributes implements CriteriaFeedbackWebsiteAttributes
{
   final private CriteriaFeedbackWebsiteValidator validator = new YouPicValidator();


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class YouPicValidator extends PhotographyWebsiteValidator
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
         return isNonEmptyWithNoWhitespace(photoURL);
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
      return YouPic.instance;
   }


   @Override
   final public CriteriaFeedbackWebsiteValidator getValidator()
   {
      return validator;
   }
}