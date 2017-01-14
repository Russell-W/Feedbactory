
package com.feedbactory.client.ui.browser.feedback.personal;


import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.feedback.personal.js.SeventyTwoDPIJavaScriptString;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.service.SeventyTwoDPI;


final class SeventyTwoDPIHandler extends PersonalFeedbackWebsiteBrowserHandler
{
   final private PersonalFeedbackBrowserEventManager browserEventManager;


   SeventyTwoDPIHandler(final PersonalFeedbackBrowserEventManager browserEventManager)
   {
      this.browserEventManager = browserEventManager;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private PersonalFeedbackBrowsedPageResult handleBrowserPageLocationChanged(final BrowserService browserService)
   {
      final Object[] rawBrowsedData = (Object[]) browserService.evaluateJavascript(SeventyTwoDPIJavaScriptString.getLiveExecutor());

      if (((Boolean) rawBrowsedData[0]).booleanValue())
      {
         if (rawBrowsedData[1] != null)
            return getBrowsedPhotoProfile((Object[]) rawBrowsedData[1]);
         else
            return PersonalFeedbackBrowsedPageResult.NoPersonProfile;
      }
      else
         return PersonalFeedbackBrowsedPageResult.BrowserPageNotReady;
   }


   private PersonalFeedbackBrowsedPageResult getBrowsedPhotoProfile(final Object[] browsedPhotoData)
   {
      final String photoID = (String) browsedPhotoData[0];
      final String photoUserID = (String) browsedPhotoData[1];
      final String photoDisplayName = getCompoundDisplayName((Object[]) browsedPhotoData[2]);

      if ((photoID.length() <= PersonalFeedbackConstants.MaximumPersonIDLength) &&
          (photoUserID.length() <= PersonalFeedbackConstants.MaximumPersonProfileUserIDLength) &&
          (photoDisplayName.length() <= PersonalFeedbackConstants.MaximumPersonProfileDisplayNameLength))
      {
         final PersonalFeedbackPerson browsedPhoto = new PersonalFeedbackPerson(SeventyTwoDPI.instance, photoID, PersonalFeedbackCriteriaType.Photography);
         return new PersonalFeedbackBrowsedPageResult(new PersonalFeedbackPersonProfile(browsedPhoto, photoUserID, photoDisplayName));
      }

      return PersonalFeedbackBrowsedPageResult.NoPersonProfile;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final PersonalFeedbackWebsite getServiceIdentifier()
   {
      return SeventyTwoDPI.instance;
   }


   @Override
   final PersonalFeedbackBrowsedPageResult browserPageLocationChanged(final BrowserService browserService)
   {
      return handleBrowserPageLocationChanged(browserService);
   }
}