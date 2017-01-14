/* Memos:
 * - The implementation of equals() & hashCode() in this class is required for the server's FeedbackManager, which attempts to cache identical
 *   feedback submission profiles in an effort to reduce the memory footprint.
 *
 *   - It's also required for PersonalFeedbackManager.
 *
 * - I spent some time profiling the memory usage of this class when it was using different ways to store the tags: list, set, and the current array.
 *   For a class that's going to be instantiated many times and kept in memory on the server, this seemed a worthwhile exercise.
 *   As expected there's not much difference in memory requirements between array lists and arrays (~4%), but there's a substantial difference between
 *   array lists and sets (~30%). The sets have the advantage of simplicity when it comes to the tags - add & forget - not to mention possibly
 *   faster lookup times even for such small collections, vs binary search (or even vanilla search) using an array list. However that difference in
 *   memory requirement reflects the fact that the HashSets aren't too lightweight and it seems a bit extreme to use one for every item profile.
 *   And if the decision comes down to array list vs raw array, the raw array has zero extra overhead and it's cheap to provide clients with an immutable list
 *   view of the array.
 */

package com.feedbactory.shared.feedback.personal;


import com.feedbactory.shared.feedback.FeedbackItemProfile;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;


final public class PersonalFeedbackPersonProfile extends FeedbackItemProfile
{
   static final private String[] EmptyTags = {};

   final public PersonalFeedbackPerson person;

   final public String userID;
   final public String nameElements;
   final public String imageURLElements;
   final public String urlElements;
   final private String[] tags;


   public PersonalFeedbackPersonProfile(final PersonalFeedbackPerson person)
   {
      this(person, null, null);
   }


   public PersonalFeedbackPersonProfile(final PersonalFeedbackPerson person, final String userID, final String name)
   {
      this(person, userID, name, null, null);
   }


   public PersonalFeedbackPersonProfile(final PersonalFeedbackPerson person, final String userID, final String name, final String imageURLElements, final String urlElements)
   {
      this(person, userID, name, imageURLElements, urlElements, Collections.<String>emptySet());
   }


   public PersonalFeedbackPersonProfile(final PersonalFeedbackPerson person, final String userID, final String name, final String imageURLElements, final String urlElements,
                                        final Set<String> tags)
   {
      validate(person, tags);

      this.person = person;
      this.userID = userID;
      this.nameElements = name;
      this.imageURLElements = imageURLElements;
      this.urlElements = urlElements;

      /* The burden is on the caller to ensure that all keywords are lowercased, and that the number of keywords supplied falls within
       * an acceptable range.
       */
      this.tags = (tags.isEmpty() ? EmptyTags : getSortedTagsArray(tags));
   }


   private void validate(final PersonalFeedbackPerson person, final Set<String> tags)
   {
      if (person == null)
         throw new IllegalArgumentException("Person cannot be null.");
      else if (tags == null)
         throw new IllegalArgumentException("Tags set cannot be null.");
   }


   private String[] getSortedTagsArray(final Set<String> tagsSet)
   {
      final String[] sortedTagsArray = new String[tagsSet.size()];
      tagsSet.toArray(sortedTagsArray);
      Arrays.sort(sortedTagsArray);

      return sortedTagsArray;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public boolean equals(final Object otherFeedbackItemProfile)
   {
      if (! (otherFeedbackItemProfile instanceof PersonalFeedbackPersonProfile))
         return false;

      final PersonalFeedbackPersonProfile otherProfile = (PersonalFeedbackPersonProfile) otherFeedbackItemProfile;

      return person.equals(otherProfile.person) &&
             nullableStringEquals(userID, otherProfile.userID) &&
             nullableStringEquals(nameElements, otherProfile.nameElements) &&
             nullableStringEquals(imageURLElements, otherProfile.imageURLElements) &&
             nullableStringEquals(urlElements, otherProfile.urlElements) &&
             Arrays.equals(tags, otherProfile.tags);
   }


   private boolean nullableStringEquals(final String stringOne, final String stringTwo)
   {
      /* Use the alternate string equals idiom, which is slightly faster than the following if it's often the case that the string references are identical:
       *
       * (stringOne != null) ? stringOne.equals(stringTwo) : (stringTwo == null)
       *
       * This should pay off on the server in particular, where the feedback housekeeping is performed to merge value-identical profile objects.
       * Once the references to different but otherwise value-identical objects are merged to point to the one object, the string reference
       * equality check will make for a very quick comparison in other general and housekeeping tasks.
       */
      return ((stringOne == stringTwo) || ((stringOne != null) && stringOne.equals(stringTwo)));
   }


   @Override
   final public int hashCode()
   {
      int result = 35;
      result = (31 * result) + person.hashCode();
      result = (31 * result) + ((userID != null) ? userID.hashCode() : 0);
      result = (31 * result) + ((nameElements != null) ? nameElements.hashCode() : 0);
      result = (31 * result) + ((imageURLElements != null) ? imageURLElements.hashCode() : 0);
      result = (31 * result) + ((urlElements != null) ? urlElements.hashCode() : 0);
      result = (31 * result) + Arrays.hashCode(tags);

      return result;
   }



   /****************************************************************************
    *
    ***************************************************************************/


   final public PersonalFeedbackWebsite getWebsite()
   {
      return person.getWebsite();
   }


   @Override
   final public PersonalFeedbackPerson getItem()
   {
      return person;
   }


   @Override
   final public String getShortName()
   {
      return person.getWebsite().getResolvedShortNameFor(this);
   }


   @Override
   final public String getFullName()
   {
      return person.getWebsite().getResolvedFullNameFor(this);
   }


   @Override
   final public String getThumbnailImageURL()
   {
      return person.getWebsite().getResolvedThumbnailImageURLFor(this);
   }


   @Override
   final public String getLargeImageURL()
   {
      return person.getWebsite().getResolvedLargeImageURLFor(this);
   }


   @Override
   final public String getURL()
   {
      return person.getWebsite().getResolvedURLFor(this);
   }


   @Override
   final public boolean hasTag(final String tag)
   {
      return (Arrays.binarySearch(tags, tag) >= 0);
   }


   @Override
   final public List<String> getTags()
   {
      return Collections.unmodifiableList(Arrays.asList(tags));
   }
}