
package com.feedbactory.shared.feedback.personal.service;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import java.util.Collections;
import java.util.Set;


final public class SmugMug extends PersonalFeedbackWebsite
{
   static final public SmugMug instance = new SmugMug();

   static final private Set<String> hostSuffixes = Collections.singleton("smugmug.com");
   static final private Set<PersonalFeedbackCriteriaType> criteriaTypes = Collections.singleton(PersonalFeedbackCriteriaType.Photography);


   private SmugMug()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private String handleGetResolvedShortNameFor(final String nameElements)
   {
      return (nameElements.isEmpty() ? "Untitled" : nameElements);
   }


   private String handleGetResolvedFullNameFor(final PersonalFeedbackPersonProfile personProfile)
   {
      if (! personProfile.nameElements.isEmpty())
         return personProfile.nameElements + " by " + personProfile.userID;
      else
         return "Untitled by " + personProfile.userID;
   }


   private String handleGetResolvedImageURLFor(final PersonalFeedbackPersonProfile personProfile, final String imageSizeCode)
   {
      final String imageURLElements = personProfile.imageURLElements;
      final int albumEndIndex = imageURLElements.indexOf('\0');
      final int filenameEndIndex = imageURLElements.indexOf('\0', albumEndIndex + 1);

      /* Some SmugMug thumbnails will fail if http instead of https is used by the ImageIO reader,
       * eg. http://tomblandford.smugmug.com/Scenic-Landscapes/i-7GQhWd5/0/Th/IMG_1092_edited-2-Th.jpg .
       */
      final StringBuilder urlBuilder = new StringBuilder(100);
      urlBuilder.append("https://");
      urlBuilder.append(personProfile.userID);
      urlBuilder.append(".smugmug.com/");
      urlBuilder.append(imageURLElements.substring(0, albumEndIndex));
      urlBuilder.append("/i-");
      urlBuilder.append(personProfile.person.getItemID());
      urlBuilder.append("/0/");
      urlBuilder.append(imageSizeCode);
      urlBuilder.append('/');
      urlBuilder.append(imageURLElements.substring(albumEndIndex + 1, filenameEndIndex));
      urlBuilder.append('-');
      urlBuilder.append(imageSizeCode);
      urlBuilder.append('.');
      urlBuilder.append(imageURLElements.substring(filenameEndIndex + 1));

      return urlBuilder.toString();
   }


   private String generateItemURL(final PersonalFeedbackPersonProfile personProfile)
   {
      final int albumEndIndex = personProfile.imageURLElements.indexOf('\0');

      final StringBuilder urlBuilder = new StringBuilder(100);
      urlBuilder.append("http://");
      urlBuilder.append(personProfile.userID);
      urlBuilder.append(".smugmug.com/");
      urlBuilder.append(personProfile.imageURLElements.substring(0, albumEndIndex));
      urlBuilder.append("/i-");
      urlBuilder.append(personProfile.person.getItemID());

      return urlBuilder.toString();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public short getID()
   {
      return 3;
   }


   @Override
   final public String getName()
   {
      return "SmugMug";
   }


   @Override
   final public String getBaseURL()
   {
      return "http://www.smugmug.com";
   }


   @Override
   final public String getItemBrowseURL()
   {
      return "http://www.smugmug.com/browse/";
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
      return handleGetResolvedFullNameFor(personProfile);
   }


   @Override
   final public String getResolvedThumbnailImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      /* Usually 150 x 150 image but often something like 150 x 100 or 150 x 113.
       * 'Ti' is also a size option that I was using before but the images are sometimes slightly too small, eg. 100 x 75 or 75 x 100, or even 100 x 67.
       */
      return handleGetResolvedImageURLFor(personProfile, "Th");
   }


   @Override
   final public String getResolvedLargeImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      /* The size of this image seems to be quite variable, with the smallest I've seen for it being 450 x 600 pixels.
       * I think the algorithm for it is this: if the image is wider than it is high, the returned scaled image will be
       * minimum width 800 with proportional height, and if the image is taller than it is wide, the returned scaled image
       * will be minimum height 600 with proportional width.
       */
      return handleGetResolvedImageURLFor(personProfile, "L");
   }


   @Override
   final public String getResolvedURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return generateItemURL(personProfile);
   }
}