
package com.feedbactory.client.ui.browser.feedback.personal;


import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.feedback.personal.js.TripleJJavaScriptString;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.service.TripleJ;


final class TripleJHandler extends PersonalFeedbackWebsiteBrowserHandler
{
   final private PersonalFeedbackBrowserEventManager browserEventManager;


   TripleJHandler(final PersonalFeedbackBrowserEventManager browserEventManager)
   {
      this.browserEventManager = browserEventManager;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private PersonalFeedbackBrowsedPageResult handleBrowserPageLocationChanged(final BrowserService browserService)
   {
      final Object[] rawBrowsedData = (Object[]) browserService.evaluateJavascript(TripleJJavaScriptString.getLiveExecutor());

      if (((Boolean) rawBrowsedData[0]).booleanValue())
      {
         if (rawBrowsedData[1] != null)
            return getBrowsedPersonProfile((Object[]) rawBrowsedData[1]);
         else
            return PersonalFeedbackBrowsedPageResult.NoPersonProfile;
      }
      else
         return PersonalFeedbackBrowsedPageResult.BrowserPageNotReady;
   }


   private PersonalFeedbackBrowsedPageResult getBrowsedPersonProfile(final Object[] browsedPageData)
   {
      final String personID = (String) browsedPageData[0];
      final String displayName = (String) browsedPageData[1];
      final String thumbnailURL = (String) browsedPageData[2];

      if ((personID.length() <= PersonalFeedbackConstants.MaximumPersonIDLength) &&
          (displayName.length() <= PersonalFeedbackConstants.MaximumPersonProfileDisplayNameLength) &&
          ((thumbnailURL == null) || (thumbnailURL.length() <= PersonalFeedbackConstants.MaximumPersonProfilePhotoURLLength)))
      {
         final PersonalFeedbackPerson person = new PersonalFeedbackPerson(TripleJ.instance, personID, PersonalFeedbackCriteriaType.Professional);
         return new PersonalFeedbackBrowsedPageResult(new PersonalFeedbackPersonProfile(person, null, displayName, thumbnailURL, null));
      }

      return PersonalFeedbackBrowsedPageResult.NoPersonProfile;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final PersonalFeedbackWebsite getServiceIdentifier()
   {
      return TripleJ.instance;
   }


   @Override
   final PersonalFeedbackBrowsedPageResult browserPageLocationChanged(final BrowserService browserService)
   {
      return handleBrowserPageLocationChanged(browserService);
   }
}