
package com.feedbactory.shared.feedback.personal.service;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import java.util.Collections;
import java.util.Set;


final public class FiveHundredPX extends PersonalFeedbackWebsite
{
   static final public FiveHundredPX instance = new FiveHundredPX();

   static final private Set<String> hostSuffixes = Collections.singleton("500px.com");
   static final private Set<PersonalFeedbackCriteriaType> criteriaTypes = Collections.singleton(PersonalFeedbackCriteriaType.Photography);


   private FiveHundredPX()
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
      // 500px uses unique keys for each size of the image URL - grab the thumbnail key.
      final int thumbnailIDEndIndex = personProfile.imageURLElements.indexOf('\0');

      // 140 x 140.
      final StringBuilder urlBuilder = generateBaseImageURL(personProfile);
      urlBuilder.append("/q%3D50_w%3D140_h%3D140/");
      urlBuilder.append(personProfile.imageURLElements.substring(0, thumbnailIDEndIndex));

      return urlBuilder.toString();
   }


   private StringBuilder generateBaseImageURL(final PersonalFeedbackPersonProfile personProfile)
   {
      final StringBuilder urlBuilder = new StringBuilder(100);
      urlBuilder.append("http://drscdn.500px.org/photo/");
      urlBuilder.append(personProfile.person.getItemID());

      return urlBuilder;
   }


   private String handleGetResolvedLargeImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      // 500px uses unique keys for each size of the image URL - grab the large image key.
      final int largeImageIDStartIndex = personProfile.imageURLElements.indexOf('\0') + 1;

      /* Longer dimension has 900 pixels, shorter dimension is proportional.
       * An alternative size - 1080 - is sometimes but not always present. See the JavaScript ripper for more details.
       */
      final StringBuilder urlBuilder = generateBaseImageURL(personProfile);
      urlBuilder.append("/m%3D900/");
      urlBuilder.append(personProfile.imageURLElements.substring(largeImageIDStartIndex));

      return urlBuilder.toString();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public short getID()
   {
      return 1;
   }


   @Override
   final public String getName()
   {
      return "500px";
   }


   @Override
   final public String getBaseURL()
   {
      return "http://www.500px.com";
   }


   @Override
   final public String getItemBrowseURL()
   {
      return "http://500px.com/popular";
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
      return "http://500px.com/photo/" + personProfile.person.getItemID();
   }
}