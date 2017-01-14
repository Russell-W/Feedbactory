
package com.feedbactory.shared.feedback.personal;


import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.feedback.FeedbackItem;


final public class PersonalFeedbackPerson extends FeedbackItem
{
   final private PersonalFeedbackWebsite website;
   final private String personID;
   final private PersonalFeedbackCriteriaType criteriaType;


   public PersonalFeedbackPerson(final PersonalFeedbackWebsite website, final String personID, final PersonalFeedbackCriteriaType criteriaType)
   {
      if (website == null)
         throw new IllegalArgumentException("Website cannot be null.");
      else if (personID == null)
         throw new IllegalArgumentException("Person ID cannot be null.");
      else if (criteriaType == null)
         throw new IllegalArgumentException("Criteria type cannot be null.");

      this.website = website;
      this.personID = personID;
      this.criteriaType = criteriaType;
   }


   @Override
   final public boolean equals(final Object otherObject)
   {
      if (! (otherObject instanceof PersonalFeedbackPerson))
         return false;

      final PersonalFeedbackPerson otherPerson = (PersonalFeedbackPerson) otherObject;
      return website.equals(otherPerson.website) &&
             personID.equals(otherPerson.personID) &&
             (criteriaType == otherPerson.criteriaType);
   }


   @Override
   final public int hashCode()
   {
      int result = 38;
      result = (31 * result) + website.hashCode();
      result = (31 * result) + personID.hashCode();
      result = (31 * result) + criteriaType.hashCode();

      return result;
   }


   @Override
   final public String toString()
   {
      final StringBuilder toStringBuilder = new StringBuilder();
      toStringBuilder.append("PersonalFeedbackPerson: [website: ");
      toStringBuilder.append(website.getName());
      toStringBuilder.append(", personID: ");
      toStringBuilder.append(personID);
      toStringBuilder.append(", criteriaType: ");
      toStringBuilder.append(criteriaType);
      toStringBuilder.append(']');

      return toStringBuilder.toString();
   }


   @Override
   final public FeedbackCategory getFeedbackCategory()
   {
      return FeedbackCategory.Personal;
   }


   @Override
   final public PersonalFeedbackWebsite getWebsite()
   {
      return website;
   }


   @Override
   final public String getItemID()
   {
      return personID;
   }


   final public PersonalFeedbackCriteriaType getCriteriaType()
   {
      return criteriaType;
   }
}