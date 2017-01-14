
package com.feedbactory.shared.feedback.personal.service;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import java.util.Collections;
import java.util.Set;


final public class Ipernity extends PersonalFeedbackWebsite
{
   static final public Ipernity instance = new Ipernity();

   static final private Set<String> hostSuffixes = Collections.singleton("ipernity.com");
   static final private Set<PersonalFeedbackCriteriaType> criteriaTypes = Collections.singleton(PersonalFeedbackCriteriaType.Photography);


   private Ipernity()
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


   private String handleGetResolvedThumbnailImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      /* Unfortunately the ipernity thumbnails of 100 pixels (maximum in either vertical or horizontal dimension) are not guaranteed to be square,
       * unlike with most other photography websites.
       *
       * Square thumbnail alternatives that ipernity does provide:
       * .75x.jpg: will produce noticeably blurry thumbnails as they are then scaled up to 100 pixels per dimension.
       * .250x.jpg: will produce good square thumbnails, but the slow loading times (test the loading of a single thumbnail URL in a standalone browser tab) don't
       * inspire confidence, for now.
       */
      return generateBaseImageURL(personProfile).append(".240.jpg").toString();
   }


   private StringBuilder generateBaseImageURL(final PersonalFeedbackPersonProfile personProfile)
   {
      final int firstDelimiterIndex = personProfile.imageURLElements.indexOf('\0');

      /* Since late 2014 Ipernity have (mostly?) switched to a CDN and changed their thumbnail URL format from:
       *
       *   http://u<farmID>.ipernity.com/<pathID>/<photoID>.<secretID>.<resolution>.jpg?<url arguments>
       *   Thumbnail elements string: <farmID>\0<pathID>\0<secretID>
       *
       * to:
       *
       *   http://cdn.ipernity.com/<pathID>/<photoID>.<secretID>.<resolution>.jpg?<url arguments>
       *   Thumbnail elements string: \0<pathID>\0<secretID>
       *
       * Feedbactory is supporting both formats for now, since the old format must still be used in some cases (where the CDN produces dead image links).
       */
      if (firstDelimiterIndex == 0)
         return generateBaseCDNImageURL(personProfile);
      else
         return generateBaseFarmIDImageURL(personProfile, firstDelimiterIndex);
   }


   private StringBuilder generateBaseCDNImageURL(final PersonalFeedbackPersonProfile personProfile)
   {
      final String photoURL = personProfile.imageURLElements;

      final int pathIDEndIndex = photoURL.indexOf('\0', 1);
      final int secretIDStartIndex = pathIDEndIndex + 1;

      final StringBuilder urlBuilder = new StringBuilder(100);
      urlBuilder.append("http://cdn.ipernity.com/");
      urlBuilder.append(photoURL.substring(1, pathIDEndIndex));
      urlBuilder.append('/');
      urlBuilder.append(personProfile.person.getItemID());
      urlBuilder.append('.');
      urlBuilder.append(photoURL.substring(secretIDStartIndex));

      return urlBuilder;
   }


   private StringBuilder generateBaseFarmIDImageURL(final PersonalFeedbackPersonProfile personProfile, final int farmIDEndIndex)
   {
      final String photoURL = personProfile.imageURLElements;

      final int pathIDStartIndex = farmIDEndIndex + 1;
      final int pathIDEndIndex = photoURL.indexOf('\0', pathIDStartIndex);
      final int secretIDStartIndex = pathIDEndIndex + 1;

      final StringBuilder urlBuilder = new StringBuilder(100);
      urlBuilder.append("http://u");
      urlBuilder.append(photoURL.substring(0, farmIDEndIndex));
      urlBuilder.append(".ipernity.com/");
      urlBuilder.append(photoURL.substring(pathIDStartIndex, pathIDEndIndex));
      urlBuilder.append('/');
      urlBuilder.append(personProfile.person.getItemID());
      urlBuilder.append('.');
      urlBuilder.append(photoURL.substring(secretIDStartIndex));

      return urlBuilder;
   }


   private String handleGetResolvedLargeImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      /* Longer dimension has 800 pixels, shorter dimension is proportional.
       * .1024.jpg is available, however for that format the image ID changes to something else, ie. not captured by personProfile.imageURLElements,
       * and the 1024 ID is not easily obtainable from the item's web page source.
       */
      return generateBaseImageURL(personProfile).append(".800.jpg").toString();
   }


   private String generateURL(final PersonalFeedbackPersonProfile personProfile)
   {
      final StringBuilder urlBuilder = new StringBuilder(100);
      urlBuilder.append("http://www.ipernity.com/doc/");
      urlBuilder.append(personProfile.userID);
      urlBuilder.append('/');
      urlBuilder.append(personProfile.person.getItemID());

      return urlBuilder.toString();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public short getID()
   {
      return 7;
   }


   @Override
   final public String getName()
   {
      return "ipernity";
   }


   @Override
   final public String getBaseURL()
   {
      return "http://www.ipernity.com";
   }


   @Override
   final public String getItemBrowseURL()
   {
      return "http://www.ipernity.com/explore/whatshot";
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
      return handleGetResolvedThumbnailImageURLFor(personProfile);
   }


   @Override
   final public String getResolvedLargeImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return handleGetResolvedLargeImageURLFor(personProfile);
   }


   @Override
   final public String getResolvedURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return generateURL(personProfile);
   }
}