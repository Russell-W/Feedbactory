
package com.feedbactory.shared.feedback.personal.service;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import java.util.Collections;
import java.util.Set;


final public class Pixoto extends PersonalFeedbackWebsite
{
   static final public Pixoto instance = new Pixoto();

   static final private Set<String> hostSuffixes = Collections.singleton("pixoto.com");
   static final private Set<PersonalFeedbackCriteriaType> criteriaTypes = Collections.singleton(PersonalFeedbackCriteriaType.Photography);


   private Pixoto()
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
      final int serverIDEndIndex = photoURL.indexOf('\0');

      final StringBuilder urlBuilder = new StringBuilder(120);
      urlBuilder.append("http://lh");
      urlBuilder.append(photoURL.substring(0, serverIDEndIndex));

      // As of mid 2015 it seems this has only recently switched over from ggpht to googleusercontent.
      urlBuilder.append(".googleusercontent.com/");
      urlBuilder.append(photoURL.substring(serverIDEndIndex + 1));

      return urlBuilder;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public short getID()
   {
      return 9;
   }


   @Override
   final public String getName()
   {
      return "Pixoto";
   }


   @Override
   final public String getBaseURL()
   {
      return "http://www.pixoto.com";
   }


   @Override
   final public String getItemBrowseURL()
   {
      return "http://www.pixoto.com/images-photography";
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
      // 100 x 100 pixels square cropped image.
      return generateBaseImageURL(personProfile).append("=s100-c").toString();
   }


   @Override
   final public String getResolvedLargeImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      // Longer dimension has 900 pixels, shorter dimension is proportional.
      return generateBaseImageURL(personProfile).append("=s900").toString();
   }


   @Override
   final public String getResolvedURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return "http://www.pixoto.com/images/" + personProfile.person.getItemID();
   }
}