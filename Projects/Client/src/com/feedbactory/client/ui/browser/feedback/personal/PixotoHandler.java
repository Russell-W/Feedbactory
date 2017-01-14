
package com.feedbactory.client.ui.browser.feedback.personal;


import com.feedbactory.client.core.feedback.personal.PersonalFeedbackUtilities;
import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.feedback.personal.js.PixotoJavaScriptString;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.service.Pixoto;
import java.util.Set;


final class PixotoHandler extends PersonalFeedbackWebsiteBrowserHandler
{
   final private PersonalFeedbackBrowserEventManager browserEventManager;


   PixotoHandler(final PersonalFeedbackBrowserEventManager browserEventManager)
   {
      this.browserEventManager = browserEventManager;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private PersonalFeedbackBrowsedPageResult handleBrowserPageLocationChanged(final BrowserService browserService)
   {
      final Object[] rawBrowsedData = (Object[]) browserService.evaluateJavascript(PixotoJavaScriptString.getLiveExecutor());

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
      final String photographerID = (String) browsedPhotoData[1];
      final String photoDisplayName = getCompoundDisplayName((Object[]) browsedPhotoData[2]);
      final String photoThumbnailURLID = getPhotoThumbnailURL((Object[]) browsedPhotoData[3]);
      final Set<String> photoTags = PersonalFeedbackUtilities.getProcessedTags((Object[]) browsedPhotoData[4]);

      if ((photoID.length() <= PersonalFeedbackConstants.MaximumPersonIDLength) &&
          (photographerID.length() <= PersonalFeedbackConstants.MaximumPersonProfileUserIDLength) &&
          (photoDisplayName.length() <= PersonalFeedbackConstants.MaximumPersonProfileDisplayNameLength) &&
          (photoThumbnailURLID.length() <= PersonalFeedbackConstants.MaximumPersonProfilePhotoURLLength))
      {
         final PersonalFeedbackPerson browsedPhoto = new PersonalFeedbackPerson(Pixoto.instance, photoID, PersonalFeedbackCriteriaType.Photography);
         return new PersonalFeedbackBrowsedPageResult(new PersonalFeedbackPersonProfile(browsedPhoto, photographerID, photoDisplayName, photoThumbnailURLID, null, photoTags));
      }

      return PersonalFeedbackBrowsedPageResult.NoPersonProfile;
   }


   private String getPhotoThumbnailURL(final Object[] photoThumbnailURLElements)
   {
      // serverID, imageURLKey.
      return ((String) photoThumbnailURLElements[0]) + '\0' + ((String) photoThumbnailURLElements[1]);
   }




   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final PersonalFeedbackWebsite getServiceIdentifier()
   {
      return Pixoto.instance;
   }


   @Override
   final PersonalFeedbackBrowsedPageResult browserPageLocationChanged(final BrowserService browserService)
   {
      return handleBrowserPageLocationChanged(browserService);
   }
}