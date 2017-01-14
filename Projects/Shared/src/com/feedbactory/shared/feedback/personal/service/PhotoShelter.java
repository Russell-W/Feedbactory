
package com.feedbactory.shared.feedback.personal.service;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import java.util.Collections;
import java.util.Set;


final public class PhotoShelter extends PersonalFeedbackWebsite
{
   static final public PhotoShelter instance = new PhotoShelter();

   static final private Set<String> hostSuffixes = Collections.singleton("photoshelter.com");
   static final private Set<PersonalFeedbackCriteriaType> criteriaTypes = Collections.singleton(PersonalFeedbackCriteriaType.Photography);


   private PhotoShelter()
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


   private StringBuilder generateBaseImageURL(final String itemID)
   {
      final StringBuilder urlBuilder = new StringBuilder(100);
      urlBuilder.append("http://cdn.c.photoshelter.com/img-get2/");
      urlBuilder.append(itemID);

      return urlBuilder;
   }


   private String generateItemURL(final PersonalFeedbackPersonProfile personProfile)
   {
      final StringBuilder urlBuilder = new StringBuilder(100);
      urlBuilder.append("http://");
      urlBuilder.append(personProfile.userID);
      urlBuilder.append(".photoshelter.com/image/");
      urlBuilder.append(personProfile.person.getItemID());

      return urlBuilder.toString();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public short getID()
   {
      return 4;
   }


   @Override
   final public String getName()
   {
      return "PhotoShelter";
   }


   @Override
   final public String getBaseURL()
   {
      return "http://www.photoshelter.com";
   }


   @Override
   final public String getItemBrowseURL()
   {
      return "http://www.photoshelter.com/explore/";
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
      /* 350 x 350 square cropped image, a format used by the thumbnails on the Promenade theme.
       * I could also use /fit=1440x250, which is a 250 height x proportional width image and a format used by the gallery thumbnails on the Downtown theme.
       */
      return generateBaseImageURL(personProfile.person.getItemID()).append("/fill=350x350").toString();
   }


   @Override
   final public String getResolvedLargeImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      // This is a very commonly used image URL format on PhotoShelter: the longer dimension is 1440 pixels, with the shorter dimension proportional.
      return generateBaseImageURL(personProfile.person.getItemID()).append("/fit=1440x1440").toString();
   }


   @Override
   final public String getResolvedURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return generateItemURL(personProfile);
   }
}