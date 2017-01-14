/* Memos:
 * - On the client side and also sent from client to server, an empty filterWebsites variable indicates that there is no filter to be applied, ie. all websites match.
 *   This is the expected case, and also provides the side benefits of reducing network traffic (albeit a measly few bytes per request) as well as allowing slightly
 *   simplified handling on the server network layer.
 *
 * - filterWebsites items are (of course) logically OR'd, whereas filterTags items are logically AND'd.
 *
 * - hashCode() has been implemented for correctness, but this class is not intended to be used as a key for hashing.
 */

package com.feedbactory.shared.feedback.personal;


import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


final public class CriteriaFeedbackFeaturedItemsFilter
{
   final public PersonalFeedbackCriteriaType criteriaType;
   final public Set<PersonalFeedbackWebsite> filterWebsites;
   final public Set<String> filterTags;
   final public long lastRetrievedSortValue;
   final public PersonalFeedbackPerson lastRetrievedItem;


   public CriteriaFeedbackFeaturedItemsFilter(final PersonalFeedbackCriteriaType criteriaType, final Set<PersonalFeedbackWebsite> filterWebsites,
                                              final Set<String> filterTags, final long lastRetrievedSortValue, final PersonalFeedbackPerson lastRetrievedPerson)
   {
      this.criteriaType = criteriaType;
      this.filterWebsites = PersonalFeedbackWebsiteSet.unmodifiableSet(filterWebsites);

      // Avoid the unnecessary allocation of an unmodifiableSet wrapper around emptySet, which is expected to be a very common constructor parameter.
      if (filterTags == Collections.<String>emptySet())
         this.filterTags = Collections.emptySet();
      else
         this.filterTags = Collections.unmodifiableSet(new HashSet<String>(filterTags));

      this.lastRetrievedSortValue = lastRetrievedSortValue;
      this.lastRetrievedItem = lastRetrievedPerson;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private boolean handleEquals(final Object otherObject)
   {
      if (! (otherObject instanceof CriteriaFeedbackFeaturedItemsFilter))
         return false;

      final CriteriaFeedbackFeaturedItemsFilter otherItemFilter = (CriteriaFeedbackFeaturedItemsFilter) otherObject;
      return (criteriaType == otherItemFilter.criteriaType) &&
             (filterWebsites.equals(otherItemFilter.filterWebsites)) &&
             (filterTags.equals(otherItemFilter.filterTags)) &&
             (lastRetrievedSortValue == otherItemFilter.lastRetrievedSortValue) &&
             (((lastRetrievedItem != null) && lastRetrievedItem.equals(otherItemFilter.lastRetrievedItem)) || 
              ((lastRetrievedItem == null) && (otherItemFilter.lastRetrievedItem == null)));
   }


   private int handleHashCode()
   {
      int result = 38;
      result = (31 * result) + criteriaType.hashCode();
      result = (31 * result) + filterWebsites.hashCode();
      result = (31 * result) + filterTags.hashCode();
      result = (31 * result) + ((int) lastRetrievedSortValue);

      if (lastRetrievedItem != null)
         result = (31 * result) + lastRetrievedItem.hashCode();

      return result;
   }


   private String handleToString()
   {
      /* PersonalFeedbackUIManager.TaskRequest on the client is one class that benefits from having a user friendly toString() implementation for both this class
       * and PersonalFeedbackPerson. Refer to its setException() method, where toString() is applied to the task argument.
       */
      final StringBuilder toStringBuilder = new StringBuilder(200);
      toStringBuilder.append("CriteriaFeedbackFeaturedItemsFilter: ");
      toStringBuilder.append("[criteriaType: ");
      toStringBuilder.append(criteriaType.displayName);
      toStringBuilder.append(", filterWebsites: ");
      appendFilterWebsitesValue(toStringBuilder);
      toStringBuilder.append(", filterTags: ");
      appendFilterTagsValue(toStringBuilder);
      toStringBuilder.append(", lastRetrievedSortValue: ");
      toStringBuilder.append(lastRetrievedSortValue);
      toStringBuilder.append(", lastRetrievedItem: ");
      toStringBuilder.append((lastRetrievedItem != null) ? lastRetrievedItem.toString() : "null");
      toStringBuilder.append(']');

      return toStringBuilder.toString();
   }


   private void appendFilterWebsitesValue(final StringBuilder toStringBuilder)
   {
      toStringBuilder.append('[');
      int websiteNumber = 0;

      for (final PersonalFeedbackWebsite website : filterWebsites)
      {
         toStringBuilder.append(website.getName());
         if (websiteNumber != (filterWebsites.size() - 1))
            toStringBuilder.append(", ");

         websiteNumber ++;
      }

      toStringBuilder.append(']');
   }


   private void appendFilterTagsValue(final StringBuilder toStringBuilder)
   {
      toStringBuilder.append('[');
      int websiteNumber = 0;

      for (final String tag : filterTags)
      {
         toStringBuilder.append(tag);
         if (websiteNumber != (filterTags.size() - 1))
            toStringBuilder.append(", ");

         websiteNumber ++;
      }

      toStringBuilder.append(']');
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public boolean equals(final Object otherObject)
   {
      return handleEquals(otherObject);
   }


   @Override
   final public int hashCode()
   {
      return handleHashCode();
   }


   @Override
   final public String toString()
   {
      return handleToString();
   }
}