
package com.feedbactory.shared.feedback.personal.service;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import java.util.Collections;
import java.util.Set;


final public class TripleJ extends PersonalFeedbackWebsite
{
   static final public TripleJ instance = new TripleJ();


   private TripleJ()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private String handleGetResolvedThumbnailImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      if (personProfile.imageURLElements != null)
         return "http://www.abc.net.au/triplej/people/img/extras/" + personProfile.imageURLElements + ".jpg";
      else
         return "http://www.abc.net.au/triplej/people/img/main/" + personProfile.person.getItemID() + ".jpg";
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public short getID()
   {
      return 31;
   }


   @Override
   final public String getName()
   {
      return "triple j";
   }


   @Override
   final public String getBaseURL()
   {
      return "http://abc.net.au/triplej";
   }


   @Override
   final public String getItemBrowseURL()
   {
      return "http://www.abc.net.au/triplej/people/";
   }


   @Override
   final public Set<String> getHostSuffixes()
   {
      return Collections.singleton("abc.net.au");
   }


   @Override
   final public Set<PersonalFeedbackCriteriaType> getCriteriaTypes()
   {
      return Collections.singleton(PersonalFeedbackCriteriaType.Professional);
   }


   @Override
   final public boolean showFeedbackLessThanMinimumThreshold()
   {
      return false;
   }


   @Override
   final public String getResolvedShortNameFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return personProfile.nameElements;
   }


   @Override
   final public String getResolvedFullNameFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return "Triple J's " + personProfile.nameElements;
   }


   @Override
   final public String getResolvedThumbnailImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return handleGetResolvedThumbnailImageURLFor(personProfile);
   }


   @Override
   final public String getResolvedLargeImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return handleGetResolvedThumbnailImageURLFor(personProfile);
   }


   @Override
   final public String getResolvedURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return "http://www.abc.net.au/triplej/people/" + personProfile.person.getItemID() + ".htm";
   }
}