
package com.feedbactory.shared.feedback.personal.service;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import java.util.Collections;
import java.util.Set;


final public class YouPic extends PersonalFeedbackWebsite
{
   static final public YouPic instance = new YouPic();

   static final private Set<String> hostSuffixes = Collections.singleton("youpic.com");
   static final private Set<PersonalFeedbackCriteriaType> criteriaTypes = Collections.singleton(PersonalFeedbackCriteriaType.Photography);


   private YouPic()
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


   private String handleGetResolvedImageURLFor(final PersonalFeedbackPersonProfile personProfile, final String imageSizeCode)
   {
      final StringBuilder urlBuilder = new StringBuilder(100);
      urlBuilder.append("https://df0179xsabjj8.cloudfront.net/");
      urlBuilder.append(imageSizeCode);
      urlBuilder.append('/');
      urlBuilder.append(personProfile.userID);
      urlBuilder.append('_');
      urlBuilder.append(personProfile.imageURLElements);

      return urlBuilder.toString();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public short getID()
   {
      return 6;
   }


   @Override
   final public String getName()
   {
      return "YouPic";
   }


   @Override
   final public String getBaseURL()
   {
      return "http://www.youpic.com";
   }


   @Override
   final public String getItemBrowseURL()
   {
      return "https://youpic.com/inspiration";
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
      // Longer dimension has 250 pixels, shorter dimension proportional.
      return handleGetResolvedImageURLFor(personProfile, "small");
   }


   @Override
   final public String getResolvedLargeImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      // Longer dimension has 1000 pixels, shorter dimension proportional.
      return handleGetResolvedImageURLFor(personProfile, "large");
   }


   @Override
   final public String getResolvedURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return "https://youpic.com/image/" + personProfile.person.getItemID();
   }
}