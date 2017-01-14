
package com.feedbactory.client.ui.browser.feedback.personal;


import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;


abstract class PersonalFeedbackWebsiteBrowserHandler
{
   final String getName()
   {
      return getServiceIdentifier().getName();
   }


   final String getItemBrowseURL()
   {
      return getServiceIdentifier().getItemBrowseURL();
   }


   abstract PersonalFeedbackWebsite getServiceIdentifier();

   abstract PersonalFeedbackBrowsedPageResult browserPageLocationChanged(final BrowserService browserService);


   final String getCompoundDisplayName(final Object[] photoNameElements)
   {
      /* If necessary, truncate the photo title element of the returned overall display name.
       * The photographer's name is never truncated and is therefore used to gauge the remaining maximum allowable length of the photo title.
       * Since the photo owner's name isn't truncated, the string returned may still be longer than the overall allowable
       * PersonalFeedbackConstants.MaximumPersonProfileDisplayNameLength.
       */
      String photoTitle = (String) photoNameElements[0];
      final String photographerName = (String) photoNameElements[1];

      final int allowablePhotoTitleLength = (PersonalFeedbackConstants.MaximumPersonProfileDisplayNameLength - photographerName.length() - 1);

      /* Only proceed with the ellipsis truncation if the existing photo title length is greater than the remaining allowable length
       * and the remaining allowable length is > 3 characters - firstly it guards against an invalid substring index within
       * UIUtilities.getEllipsisTruncatedString, which assumes that its max length parameter is >= 3, and it also guards against truncating
       * something like 'XXXXX' to '...' when there are only 3 or less remaining characters allowed for the photo title.
       * Four characters remaining is barely acceptable, eg. 'XXXXX' -> 'X...'.
       */
      if ((photoTitle.length() > allowablePhotoTitleLength) && (allowablePhotoTitleLength > 3))
         photoTitle = UIUtilities.getEllipsisTruncatedString(photoTitle, allowablePhotoTitleLength);

      // May still exceed PersonalFeedbackConstants.MaximumPersonProfileDisplayNameLength.
      return photoTitle + '\0' + photographerName;
   }
}