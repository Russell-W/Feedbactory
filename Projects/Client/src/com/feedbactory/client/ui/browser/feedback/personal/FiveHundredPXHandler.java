
package com.feedbactory.client.ui.browser.feedback.personal;


import com.feedbactory.client.core.feedback.personal.PersonalFeedbackUtilities;
import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.feedback.personal.js.FiveHundredPXJavaScriptString;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.service.FiveHundredPX;
import java.util.Set;


final class FiveHundredPXHandler extends PersonalFeedbackWebsiteBrowserHandler
{
   final private PersonalFeedbackBrowserEventManager browserEventManager;


   FiveHundredPXHandler(final PersonalFeedbackBrowserEventManager browserEventManager)
   {
      this.browserEventManager = browserEventManager;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private PersonalFeedbackBrowsedPageResult handleBrowserPageLocationChanged(final BrowserService browserService)
   {
      final Object[] rawBrowsedData = (Object[]) browserService.evaluateJavascript(FiveHundredPXJavaScriptString.getLiveExecutor());

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
      /* I was previously trimming all of the strings, but this will also remove any leading '\0' characters,
       * which are used as delimiters in the display name!
       * The onus is on the JavaScript to produce and pass along the correct strings, whereas the onus is on this class
       * to validate their length.
       */
      final String photoID = (String) browsedPhotoData[0];
      final String photoUserID = (String) browsedPhotoData[1];

      // May still exceed PersonalFeedbackConstants.MaximumPersonProfileDisplayNameLength.
      final String photoDisplayName = getCompoundDisplayName((Object[]) browsedPhotoData[2]);
      final String photoThumbnailURLID = getPhotoThumbnailURL((Object[]) browsedPhotoData[3]);
      final Set<String> photoTags = PersonalFeedbackUtilities.getProcessedTags((Object[]) browsedPhotoData[4]);

      if ((photoID.length() <= PersonalFeedbackConstants.MaximumPersonIDLength) &&
          (photoUserID.length() <= PersonalFeedbackConstants.MaximumPersonProfileUserIDLength) &&
          (photoDisplayName.length() <= PersonalFeedbackConstants.MaximumPersonProfileDisplayNameLength) &&
          (photoThumbnailURLID.length() <= PersonalFeedbackConstants.MaximumPersonProfilePhotoURLLength))
      {
         final PersonalFeedbackPerson browsedPhoto = new PersonalFeedbackPerson(FiveHundredPX.instance, photoID, PersonalFeedbackCriteriaType.Photography);
         return new PersonalFeedbackBrowsedPageResult(new PersonalFeedbackPersonProfile(browsedPhoto, photoUserID, photoDisplayName, photoThumbnailURLID, null, photoTags));
      }

      return PersonalFeedbackBrowsedPageResult.NoPersonProfile;
   }


   private String getPhotoThumbnailURL(final Object[] photoThumbnailURLElements)
   {
      // thumbnailURLKey, largeImageURLKey.
      return ((String) photoThumbnailURLElements[0]) + '\0' + ((String) photoThumbnailURLElements[1]);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final PersonalFeedbackWebsite getServiceIdentifier()
   {
      return FiveHundredPX.instance;
   }


   @Override
   final PersonalFeedbackBrowsedPageResult browserPageLocationChanged(final BrowserService browserService)
   {
      return handleBrowserPageLocationChanged(browserService);
   }
}