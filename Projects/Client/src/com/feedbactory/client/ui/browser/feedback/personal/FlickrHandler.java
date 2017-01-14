
package com.feedbactory.client.ui.browser.feedback.personal;


import com.feedbactory.client.core.feedback.personal.PersonalFeedbackUtilities;
import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.feedback.personal.js.FlickrJavaScriptString;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.service.Flickr;
import java.util.Set;


final class FlickrHandler extends PersonalFeedbackWebsiteBrowserHandler
{
   final private PersonalFeedbackBrowserEventManager browserEventManager;


   FlickrHandler(final PersonalFeedbackBrowserEventManager browserEventManager)
   {
      this.browserEventManager = browserEventManager;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private PersonalFeedbackBrowsedPageResult handleBrowserPageLocationChanged(final BrowserService browserService)
   {
      final Object[] rawBrowsedData = (Object[]) browserService.evaluateJavascript(FlickrJavaScriptString.getLiveExecutor());

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
      /* No trimming performed here - the onus is on the JavaScript to produce and pass along the correct strings, whereas the onus is on this class
       * to validate their length.
       */
      final String photoID = (String) browsedPhotoData[0];
      final String photoUsername = (String) browsedPhotoData[1];
      final String photoDisplayName = getCompoundDisplayName((Object[]) browsedPhotoData[2]);
      final String photoThumbnailURLElements = getPhotoThumbnailURL((Object[]) browsedPhotoData[3]);
      final Set<String> photoTags = PersonalFeedbackUtilities.getProcessedTags((Object[]) browsedPhotoData[4]);

      if ((photoID.length() <= PersonalFeedbackConstants.MaximumPersonIDLength) &&
          (photoUsername.length() <= PersonalFeedbackConstants.MaximumPersonProfileUserIDLength) &&
          (photoDisplayName.length() <= PersonalFeedbackConstants.MaximumPersonProfileDisplayNameLength) &&
          (photoThumbnailURLElements.length() <= PersonalFeedbackConstants.MaximumPersonProfilePhotoURLLength))

      {
         final PersonalFeedbackPerson browsedPerson = new PersonalFeedbackPerson(Flickr.instance, photoID, PersonalFeedbackCriteriaType.Photography);
         return new PersonalFeedbackBrowsedPageResult(new PersonalFeedbackPersonProfile(browsedPerson, photoUsername, photoDisplayName, photoThumbnailURLElements, null, photoTags));
      }

      return PersonalFeedbackBrowsedPageResult.NoPersonProfile;
   }


   private String getPhotoThumbnailURL(final Object[] photoThumbnailURLElements)
   {
      // farmID, serverID, and secretID.
      return ((String) photoThumbnailURLElements[0]) + '\0' + ((String) photoThumbnailURLElements[1]) + '\0' + ((String) photoThumbnailURLElements[2]);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final PersonalFeedbackWebsite getServiceIdentifier()
   {
      return Flickr.instance;
   }


   @Override
   final PersonalFeedbackBrowsedPageResult browserPageLocationChanged(final BrowserService browserService)
   {
      return handleBrowserPageLocationChanged(browserService);
   }
}