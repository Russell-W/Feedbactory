
package com.feedbactory.server.feedback.personal.service;


import com.feedbactory.server.feedback.personal.CriteriaFeedbackWebsiteAttributes;
import com.feedbactory.server.feedback.personal.CriteriaFeedbackWebsiteValidator;
import com.feedbactory.server.feedback.personal.PhotographyWebsiteValidator;
import com.feedbactory.shared.feedback.personal.service.SeventyTwoDPI;


final public class SeventyTwoDPIAttributes implements CriteriaFeedbackWebsiteAttributes
{
   final private CriteriaFeedbackWebsiteValidator validator = new SeventyTwoDPIValidator();


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class SeventyTwoDPIValidator extends PhotographyWebsiteValidator
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
         return (photoURL == null);
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
   final public SeventyTwoDPI getWebsite()
   {
      return SeventyTwoDPI.instance;
   }


   @Override
   final public CriteriaFeedbackWebsiteValidator getValidator()
   {
      return validator;
   }
}