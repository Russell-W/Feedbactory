
package com.feedbactory.server.feedback.personal.service;


import com.feedbactory.server.feedback.personal.CriteriaFeedbackWebsiteValidator;
import com.feedbactory.server.feedback.personal.CriteriaFeedbackWebsiteAttributes;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.service.TripleJ;


final public class TripleJAttributes implements CriteriaFeedbackWebsiteAttributes
{
   final private CriteriaFeedbackWebsiteValidator validator = new TripleJValidator();


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class TripleJValidator extends CriteriaFeedbackWebsiteValidator
   {
      @Override
      final public boolean validateUserID(final String userID)
      {
         return (userID == null);
      }


      @Override
      final public boolean validateDisplayName(final String displayName)
      {
         return isNonEmptyWithNoLeadingOrTrailingWhitespace(displayName);
      }


      @Override
      final public boolean validatePhotoURL(final String photoURL)
      {
         if (photoURL != null)
            return isNonEmptyWithNoWhitespace(photoURL);
         else
            return true;
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
      return TripleJ.instance;
   }


   @Override
   final public CriteriaFeedbackWebsiteValidator getValidator()
   {
      return validator;
   }
}