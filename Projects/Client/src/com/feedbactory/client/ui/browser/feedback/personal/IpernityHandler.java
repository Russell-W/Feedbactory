
package com.feedbactory.client.ui.browser.feedback.personal;


import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.feedback.personal.js.IpernityJavaScriptString;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.service.Ipernity;


final class IpernityHandler extends PersonalFeedbackWebsiteBrowserHandler
{
   final private PersonalFeedbackBrowserEventManager browserEventManager;


   IpernityHandler(final PersonalFeedbackBrowserEventManager browserEventManager)
   {
      this.browserEventManager = browserEventManager;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private PersonalFeedbackBrowsedPageResult handleBrowserPageLocationChanged(final BrowserService browserService)
   {
      final Object[] rawBrowsedData = (Object[]) browserService.evaluateJavascript(IpernityJavaScriptString.getLiveExecutor());

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
      final String photoThumbnailURLID = getPhotoThumbnailURL((Object[]) browsedPhotoData[3]);

      if ((photoID.length() <= PersonalFeedbackConstants.MaximumPersonIDLength) &&
          (photoUserID.length() <= PersonalFeedbackConstants.MaximumPersonProfileUserIDLength) &&
          (photoDisplayName.length() <= PersonalFeedbackConstants.MaximumPersonProfileDisplayNameLength) &&
          (photoThumbnailURLID.length() <= PersonalFeedbackConstants.MaximumPersonProfilePhotoURLLength))
      {
         final PersonalFeedbackPerson browsedPhoto = new PersonalFeedbackPerson(Ipernity.instance, photoID, PersonalFeedbackCriteriaType.Photography);
         return new PersonalFeedbackBrowsedPageResult(new PersonalFeedbackPersonProfile(browsedPhoto, photoUserID, photoDisplayName, photoThumbnailURLID, null));
      }

      return PersonalFeedbackBrowsedPageResult.NoPersonProfile;
   }


   private String getPhotoThumbnailURL(final Object[] photoThumbnailURLElements)
   {
      // farmID, pathID, secretID.
      return ((String) photoThumbnailURLElements[0]) + '\0' + ((String) photoThumbnailURLElements[1]) + '\0' + ((String) photoThumbnailURLElements[2]);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final PersonalFeedbackWebsite getServiceIdentifier()
   {
      return Ipernity.instance;
   }


   @Override
   final PersonalFeedbackBrowsedPageResult browserPageLocationChanged(final BrowserService browserService)
   {
      return handleBrowserPageLocationChanged(browserService);
   }
}