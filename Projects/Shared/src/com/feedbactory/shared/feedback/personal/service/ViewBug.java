
package com.feedbactory.shared.feedback.personal.service;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import java.util.Collections;
import java.util.Set;


final public class ViewBug extends PersonalFeedbackWebsite
{
   static final public ViewBug instance = new ViewBug();

   static final private Set<String> hostSuffixes = Collections.singleton("viewbug.com");
   static final private Set<PersonalFeedbackCriteriaType> criteriaTypes = Collections.singleton(PersonalFeedbackCriteriaType.Photography);


   private ViewBug()
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
      final StringBuilder urlBuilder = new StringBuilder(100);
      urlBuilder.append("http://www.viewbug.com/media/mediafiles/");
      urlBuilder.append(personProfile.imageURLElements);
      urlBuilder.append('/');
      urlBuilder.append(personProfile.person.getItemID());
      urlBuilder.append('_');

      return urlBuilder;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public short getID()
   {
      return 8;
   }


   @Override
   final public String getName()
   {
      return "ViewBug";
   }


   @Override
   final public String getBaseURL()
   {
      return "http://www.viewbug.com";
   }


   @Override
   final public String getItemBrowseURL()
   {
      /* Although http://www.viewbug.com/photo-explore seems to provide a good starting point for browsing photos, most click paths from there will result in the
       * user being locked in to one photographer's photostream as they navigate from one photo to the next.
       * An exception is http://www.viewbug.com/photo/in-contest, which allows a user to at least browse entries from different photographers within the one contest
       * once they click on an initial image. Therefore this URL will provide a more varied sample of photography feedback.
       */
      return "http://www.viewbug.com/photo/in-contest";
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
      /* Note that ViewBug interprets 200x200 as 200 x proportional height or proportional width x 200, depending on which dimension is longer.
       * Unless the image is very wide, this should be enough to get an image at least 100 pixels high.
       * If this format is removed, could use _widepreview as a backup, which is minimum 280 pixels for the longer dimension.
       */
      return generateBaseImageURL(personProfile).append("200x200.jpg").toString();
   }


   @Override
   final public String getResolvedLargeImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      // Width or height of 926 pixels, depending on which is the longer dimension.
      return generateBaseImageURL(personProfile).append("large.jpg").toString();
   }


   @Override
   final public String getResolvedURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return "http://www.viewbug.com/photo/" + personProfile.person.getItemID();
   }
}