/* Memos:
 * - Flickr photo IDs are unique, since the photos can be directly browsed to via a URL of the form:
 *   http://www.flickr.com/photo.gne?id=123456789
 */

package com.feedbactory.shared.feedback.personal.service;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import java.util.Collections;
import java.util.Set;


final public class Flickr extends PersonalFeedbackWebsite
{
   static final public Flickr instance = new Flickr();

   static final private Set<String> hostSuffixes = Collections.singleton("flickr.com");
   static final private Set<PersonalFeedbackCriteriaType> criteriaTypes = Collections.singleton(PersonalFeedbackCriteriaType.Photography);


   private Flickr()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private String handleGetResolvedShortNameFor(final String nameElements)
   {
      final int nameEndIndex = nameElements.indexOf('\0');
      if (nameEndIndex == 0)
         return "Untitled";
      else
         return nameElements.substring(0, nameEndIndex);
   }


   private String handleGetResolvedFullNameFor(final String nameElements)
   {
      final int titleEndIndex = nameElements.indexOf('\0');
      final String title;
      final String photographerName;

      if (titleEndIndex != 0)
         title = nameElements.substring(0, titleEndIndex);
      else
         title = "Untitled";

      if (titleEndIndex != (nameElements.length() - 1))
         photographerName = nameElements.substring(titleEndIndex + 1);
      else
         photographerName = "Unknown";

      return title + " by " + photographerName;
   }


   private StringBuilder generateBaseImageURL(final PersonalFeedbackPersonProfile personProfile)
   {
      final String photoURL = personProfile.imageURLElements;

      final int farmIDEndIndex = photoURL.indexOf('\0');
      final int serverIDStartIndex = farmIDEndIndex + 1;
      final int serverIDEndIndex = photoURL.indexOf('\0', serverIDStartIndex);
      final int secretIDStartIndex = serverIDEndIndex + 1;

      final StringBuilder urlBuilder = new StringBuilder(100);
      urlBuilder.append("http://farm");
      urlBuilder.append(photoURL.substring(0, farmIDEndIndex));
      urlBuilder.append(".staticflickr.com/");
      urlBuilder.append(photoURL.substring(serverIDStartIndex, serverIDEndIndex));
      urlBuilder.append('/');
      urlBuilder.append(personProfile.person.getItemID());
      urlBuilder.append('_');
      urlBuilder.append(photoURL.substring(secretIDStartIndex));

      return urlBuilder;
   }


   private String generateItemURL(final PersonalFeedbackPersonProfile personProfile)
   {
      final StringBuilder urlBuilder = new StringBuilder(100);
      urlBuilder.append("http://www.flickr.com/photos/");
      urlBuilder.append(personProfile.userID);
      urlBuilder.append('/');
      urlBuilder.append(personProfile.person.getItemID());
      urlBuilder.append('/');

      return urlBuilder.toString();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public short getID()
   {
      return 0;
   }


   @Override
   final public String getName()
   {
      return "Flickr";
   }


   @Override
   final public String getBaseURL()
   {
      return "http://www.flickr.com";
   }


   @Override
   final public String getItemBrowseURL()
   {
      return "https://www.flickr.com/explore/";
   }


   @Override
   final public Set<String> getHostSuffixes()
   {
      return hostSuffixes;
   }


   @Override
   final public Set<PersonalFeedbackCriteriaType> getCriteriaTypes()
   {
      return criteriaTypes;
   }


   @Override
   final public boolean showFeedbackLessThanMinimumThreshold()
   {
      return true;
   }


   @Override
   final public String getResolvedShortNameFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return handleGetResolvedShortNameFor(personProfile.nameElements);
   }


   @Override
   final public String getResolvedFullNameFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return handleGetResolvedFullNameFor(personProfile.nameElements);
   }


   @Override
   final public String getResolvedThumbnailImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      /* _q.jpg pictures are 150 by 150 pixels.
       * _s.jpg pictures are even smaller, 75 by 75 pixels, but this is too small for some of the Feedbactory display panels.
       */
      return generateBaseImageURL(personProfile).append("_q.jpg").toString();
   }


   @Override
   final public String getResolvedLargeImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      // Longer dimension is 1024 pixels, shorter dimension is proportional.
      return generateBaseImageURL(personProfile).append("_b.jpg").toString();
   }


   @Override
   final public String getResolvedURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return generateItemURL(personProfile);
   }
}