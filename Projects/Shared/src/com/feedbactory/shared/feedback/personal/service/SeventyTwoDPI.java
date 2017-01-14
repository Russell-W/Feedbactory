
package com.feedbactory.shared.feedback.personal.service;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import java.util.Collections;
import java.util.Set;


final public class SeventyTwoDPI extends PersonalFeedbackWebsite
{
   static final public SeventyTwoDPI instance = new SeventyTwoDPI();

   static final private Set<String> hostSuffixes = Collections.singleton("72dpi.com");
   static final private Set<PersonalFeedbackCriteriaType> criteriaTypes = Collections.singleton(PersonalFeedbackCriteriaType.Photography);


   private SeventyTwoDPI()
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


   private String handleGetResolvedImageURLFor(final String itemID, final String imageSizeCode)
   {
      final StringBuilder urlBuilder = new StringBuilder(100);
      urlBuilder.append("http://www.72dpi.com/");
      urlBuilder.append(imageSizeCode);
      urlBuilder.append('/');
      urlBuilder.append(itemID);
      urlBuilder.append(".jpg");

      return urlBuilder.toString();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public short getID()
   {
      return 5;
   }


   @Override
   final public String getName()
   {
      return "72dpi";
   }


   @Override
   final public String getBaseURL()
   {
      return "http://www.72dpi.com";
   }


   @Override
   final public String getItemBrowseURL()
   {
      return "http://www.72dpi.com/new-photos";
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
      // 100 x 100 square cropped image.
      return handleGetResolvedImageURLFor(personProfile.person.getItemID(), "p100");
   }


   @Override
   final public String getResolvedLargeImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      // Longer dimension has 900 pixels, shorter dimension is proportional.
      return handleGetResolvedImageURLFor(personProfile.person.getItemID(), "p900");
   }


   @Override
   final public String getResolvedURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return "http://www.72dpi.com/photo/" + personProfile.person.getItemID();
   }
}