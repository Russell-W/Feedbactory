
package com.feedbactory.shared.feedback.personal;


import com.feedbactory.shared.FeedbactoryConstants;


final public class PersonalFeedbackFeaturedPerson
{
   final public PersonalFeedbackPersonProfile personProfile;
   final public PersonalFeedbackBasicSummary feedbackSummary;
   final public long creationTime;
   final public long sortValue;


   // Constructor used when the caller is generating an object for the purposes of sorting via a Comparator.
   public PersonalFeedbackFeaturedPerson(final long sortValue)
   {
      this.sortValue = sortValue;

      personProfile = null;
      feedbackSummary = null;
      creationTime = FeedbactoryConstants.NoTime;
   }


   // Constructor used when the caller is generating an object for the purposes of sorting via a Comparator.
   public PersonalFeedbackFeaturedPerson(final PersonalFeedbackPersonProfile personProfile, final long sortValue)
   {
      this.personProfile = personProfile;
      this.sortValue = sortValue;

      feedbackSummary = null;
      creationTime = FeedbactoryConstants.NoTime;
   }


   public PersonalFeedbackFeaturedPerson(final PersonalFeedbackPersonProfile personProfile, final PersonalFeedbackBasicSummary feedbackSummary,
                                         final long creationTime, final long sortValue)
   {
      this.personProfile = personProfile;
      this.feedbackSummary = feedbackSummary;
      this.creationTime = creationTime;
      this.sortValue = sortValue;
   }
}