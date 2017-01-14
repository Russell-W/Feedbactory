/* Memos:
 * - Previously I'd statically initialised a final cached unmodifiable set to return for calls to getWebsites(), however this is dangerous because of the circular
 *   static references used within this class and within the individual website classes (because they are subclasses of this class). Depending on the way that
 *   the classes were first referenced, the subclass or the superclass may have been initialised first. In one case, a NullPointerException was thrown
 *   when accessing the cached website set because this superclass had been initialised before Flickr; there was a null in the set where Flickr.instance should have been.
 *   If the Flickr class is initialised first, this problem doesn't occur. It's an 'oldschool' circular reference type problem that Java usually shields the developer
 *   from, but not in this case. It's safest to avoid two-way class dependencies within static initialisers.
 */

package com.feedbactory.shared.feedback.personal;


import com.feedbactory.shared.feedback.FeedbackWebsite;
import com.feedbactory.shared.feedback.personal.service.FiveHundredPX;
import com.feedbactory.shared.feedback.personal.service.Flickr;
import com.feedbactory.shared.feedback.personal.service.Ipernity;
import com.feedbactory.shared.feedback.personal.service.OneX;
import com.feedbactory.shared.feedback.personal.service.PhotoShelter;
import com.feedbactory.shared.feedback.personal.service.Pixoto;
import com.feedbactory.shared.feedback.personal.service.SeventyTwoDPI;
import com.feedbactory.shared.feedback.personal.service.SmugMug;
import com.feedbactory.shared.feedback.personal.service.TripleJ;
import com.feedbactory.shared.feedback.personal.service.ViewBug;
import com.feedbactory.shared.feedback.personal.service.YouPic;
import java.util.Set;


abstract public class PersonalFeedbackWebsite implements FeedbackWebsite
{
   // Lazily initialised sets to work around the circular reference (uninitialised class) issue mentioned above.
   volatile static private Set<PersonalFeedbackWebsite> PersonalFeedbackWebsites;
   volatile static private Set<PersonalFeedbackWebsite> PhotographyWebsites;
   volatile static private Set<PersonalFeedbackWebsite> ProfessionalWebsites;


   /* This default base class functionality for equals() (using the inherited Object deferral to ==) and hashCode() will work for the expected case of subclasses
    * enforcing a policy of using a singleton instance to represent the class. Any subclass that deviates from that policy must provide its own
    * implementation of equals().
    */
   @Override
   final public int hashCode()
   {
      return getID();
   }


   abstract public short getID();
   abstract public Set<String> getHostSuffixes();
   // Return the possible feedback criteria types that apply to this website.
   abstract public Set<PersonalFeedbackCriteriaType> getCriteriaTypes();
   abstract public boolean showFeedbackLessThanMinimumThreshold();


   /* Default implementation for retrieving the short name for a person profile.
    * Subclasses may leave it as is, or override it in the cases where the name may be derived from other profile information, username.
    */
   public String getResolvedShortNameFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return personProfile.nameElements;
   }


   /* Default implementation for retrieving the full name for a person profile.
    * Subclasses may leave it as is, or override it in the cases where the name may be derived from other profile information, username.
    */
   public String getResolvedFullNameFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return personProfile.nameElements;
   }


   /* Default implementation for retrieving the thumbnail image URL for a person profile.
    * Subclasses may leave it as is, or override it in the cases where the thumbnail URL may be derived from other profile information, eg. profile ID and/or username.
    */
   public String getResolvedThumbnailImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return personProfile.imageURLElements;
   }


   /* Default implementation for retrieving the large image URL for a person profile.
    * Subclasses may leave it as is, or override it in the cases where the image URL may be derived from other profile information, eg. profile ID and/or username.
    */
   public String getResolvedLargeImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return personProfile.imageURLElements;
   }


   /* Default implementation for retrieving the profile URL for an item.
    * Subclasses may leave it as is, or override it in the cases where the item URL may be derived from other profile information, eg. profile ID and/or username.
    */
   public String getResolvedURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return personProfile.urlElements;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public Set<PersonalFeedbackWebsite> getWebsites()
   {
      if (PersonalFeedbackWebsites == null)
         initialiseWebsites();

      return PersonalFeedbackWebsites;
   }


   static private void initialiseWebsites()
   {
      // See the note at the top of the class explaining why this is not initialised as a cached static variable.
      final PersonalFeedbackWebsiteSet websitesBuilder = new PersonalFeedbackWebsiteSet();
      websitesBuilder.add(Flickr.instance);
      websitesBuilder.add(FiveHundredPX.instance);
      websitesBuilder.add(OneX.instance);
      websitesBuilder.add(SmugMug.instance);
      websitesBuilder.add(PhotoShelter.instance);
      websitesBuilder.add(SeventyTwoDPI.instance);
      websitesBuilder.add(YouPic.instance);
      websitesBuilder.add(Ipernity.instance);
      websitesBuilder.add(ViewBug.instance);
      websitesBuilder.add(Pixoto.instance);
      websitesBuilder.add(TripleJ.instance);

      // Create an UnmodifiablePersonalFeedbackWebsiteSet rather than Collections.unmodifiableSet to allow optimisations of many set operations.
      PersonalFeedbackWebsites = PersonalFeedbackWebsiteSet.unmodifiableSet(websitesBuilder);
   }


   static public Set<PersonalFeedbackWebsite> getWebsites(final PersonalFeedbackCriteriaType criteriaType)
   {
      switch (criteriaType)
      {
         case Professional:
            return getProfessionalWebsites();

         case Photography:
            return getPhotographyWebsites();

         default:
            return PersonalFeedbackWebsiteSet.EmptySet;
      }
   }


   static private Set<PersonalFeedbackWebsite> getProfessionalWebsites()
   {
      if (ProfessionalWebsites == null)
         initialiseProfessionalWebsites();

      return ProfessionalWebsites;
   }


   static private void initialiseProfessionalWebsites()
   {
      final PersonalFeedbackWebsiteSet websitesBuilder = new PersonalFeedbackWebsiteSet();
      websitesBuilder.add(TripleJ.instance);

      ProfessionalWebsites = PersonalFeedbackWebsiteSet.unmodifiableSet(websitesBuilder);
   }


   static private Set<PersonalFeedbackWebsite> getPhotographyWebsites()
   {
      if (PhotographyWebsites == null)
         initialisePhotographyWebsites();

      return PhotographyWebsites;
   }


   static private void initialisePhotographyWebsites()
   {
      // See the note at the top of the class explaining why this is not initialised as a cached static variable.
      final PersonalFeedbackWebsiteSet websitesBuilder = new PersonalFeedbackWebsiteSet();
      websitesBuilder.add(Flickr.instance);
      websitesBuilder.add(FiveHundredPX.instance);
      websitesBuilder.add(OneX.instance);
      websitesBuilder.add(SmugMug.instance);
      websitesBuilder.add(PhotoShelter.instance);
      websitesBuilder.add(SeventyTwoDPI.instance);
      websitesBuilder.add(YouPic.instance);
      websitesBuilder.add(Ipernity.instance);
      websitesBuilder.add(ViewBug.instance);
      websitesBuilder.add(Pixoto.instance);

      // Create an UnmodifiablePersonalFeedbackWebsiteSet rather than Collections.unmodifiableSet to allow optimisations of many set operations.
      PhotographyWebsites = PersonalFeedbackWebsiteSet.unmodifiableSet(websitesBuilder);
   }


   static public PersonalFeedbackWebsite fromValue(final short value)
   {
      switch (value)
      {
         case 0:
            return Flickr.instance;
         case 1:
            return FiveHundredPX.instance;
         case 2:
            return OneX.instance;
         case 3:
            return SmugMug.instance;
         case 4:
            return PhotoShelter.instance;
         case 5:
            return SeventyTwoDPI.instance;
         case 6:
            return YouPic.instance;
         case 7:
            return Ipernity.instance;
         case 8:
            return ViewBug.instance;
         case 9:
            return Pixoto.instance;
         case 31:
            return TripleJ.instance;

         /* No exception thrown here - let the caller decide how to react to a null value, whether it be throwing a security exception (eg. reading
          * a record on the server), or using it as an indicator to skip the remainder of an unknown record type on the client.
          */
         default:
            return null;
      }
   }
}