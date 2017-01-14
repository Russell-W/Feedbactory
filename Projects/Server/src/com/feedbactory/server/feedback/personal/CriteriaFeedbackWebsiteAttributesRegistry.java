
package com.feedbactory.server.feedback.personal;


import com.feedbactory.server.feedback.personal.service.FiveHundredPXAttributes;
import com.feedbactory.server.feedback.personal.service.FlickrAttributes;
import com.feedbactory.server.feedback.personal.service.IpernityAttributes;
import com.feedbactory.server.feedback.personal.service.OneXAttributes;
import com.feedbactory.server.feedback.personal.service.PhotoShelterAttributes;
import com.feedbactory.server.feedback.personal.service.PixotoAttributes;
import com.feedbactory.server.feedback.personal.service.SeventyTwoDPIAttributes;
import com.feedbactory.server.feedback.personal.service.SmugMugAttributes;
import com.feedbactory.server.feedback.personal.service.TripleJAttributes;
import com.feedbactory.server.feedback.personal.service.ViewBugAttributes;
import com.feedbactory.server.feedback.personal.service.YouPicAttributes;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;


final class CriteriaFeedbackWebsiteAttributesRegistry
{
   final private CriteriaFeedbackWebsiteAttributes[] websiteAttributes = new CriteriaFeedbackWebsiteAttributes[32];


   CriteriaFeedbackWebsiteAttributesRegistry()
   {
      initialise();
   }


   private void initialise()
   {
      addWebsiteAttributes(new FlickrAttributes());
      addWebsiteAttributes(new FiveHundredPXAttributes());
      addWebsiteAttributes(new OneXAttributes());
      addWebsiteAttributes(new SmugMugAttributes());
      addWebsiteAttributes(new PhotoShelterAttributes());
      addWebsiteAttributes(new SeventyTwoDPIAttributes());
      addWebsiteAttributes(new YouPicAttributes());
      addWebsiteAttributes(new IpernityAttributes());
      addWebsiteAttributes(new ViewBugAttributes());
      addWebsiteAttributes(new PixotoAttributes());
      addWebsiteAttributes(new TripleJAttributes());
   }


   private void addWebsiteAttributes(final CriteriaFeedbackWebsiteAttributes websiteAttributes)
   {
      assert (websiteAttributes.getWebsite().getID() < 32);
      this.websiteAttributes[websiteAttributes.getWebsite().getID()] = websiteAttributes;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final CriteriaFeedbackWebsiteAttributes getWebsiteAttributes(final PersonalFeedbackWebsite website)
   {
      return websiteAttributes[website.getID()];
   }
}