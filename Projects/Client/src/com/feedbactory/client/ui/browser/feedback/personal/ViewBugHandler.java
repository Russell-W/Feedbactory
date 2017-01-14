
package com.feedbactory.client.ui.browser.feedback.personal;


import com.feedbactory.client.core.feedback.personal.PersonalFeedbackUtilities;
import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.feedback.personal.js.ViewBugJavaScriptString;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.service.ViewBug;
import java.util.Set;


final class ViewBugHandler extends PersonalFeedbackWebsiteBrowserHandler
{
   final private PersonalFeedbackBrowserEventManager browserEventManager;


   ViewBugHandler(final PersonalFeedbackBrowserEventManager browserEventManager)
   {
      this.browserEventManager = browserEventManager;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private PersonalFeedbackBrowsedPageResult handleBrowserPageLocationChanged(final BrowserService browserService)
   {
      final Object[] rawBrowsedData = (Object[]) browserService.evaluateJavascript(ViewBugJavaScriptString.getLiveExecutor());

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
      final String photoThumbnailURLID = (String) browsedPhotoData[3];
      final Set<String> photoTags = PersonalFeedbackUtilities.getProcessedTags((Object[]) browsedPhotoData[4]);

      if ((photoID.length() <= PersonalFeedbackConstants.MaximumPersonIDLength) &&
          (photoUserID.length() <= PersonalFeedbackConstants.MaximumPersonProfileUserIDLength) &&
          (photoDisplayName.length() <= PersonalFeedbackConstants.MaximumPersonProfileDisplayNameLength) &&
          (photoThumbnailURLID.length() <= PersonalFeedbackConstants.MaximumPersonProfilePhotoURLLength))
      {
         final PersonalFeedbackPerson browsedPhoto = new PersonalFeedbackPerson(ViewBug.instance, photoID, PersonalFeedbackCriteriaType.Photography);
         return new PersonalFeedbackBrowsedPageResult(new PersonalFeedbackPersonProfile(browsedPhoto, photoUserID, photoDisplayName, photoThumbnailURLID, null, photoTags));
      }

      return PersonalFeedbackBrowsedPageResult.NoPersonProfile;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final PersonalFeedbackWebsite getServiceIdentifier()
   {
      return ViewBug.instance;
   }


   @Override
   final PersonalFeedbackBrowsedPageResult browserPageLocationChanged(final BrowserService browserService)
   {
      return handleBrowserPageLocationChanged(browserService);
   }
}