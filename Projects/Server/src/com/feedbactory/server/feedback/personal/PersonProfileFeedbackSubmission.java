
package com.feedbactory.server.feedback.personal;


import com.feedbactory.server.feedback.ItemProfileFeedbackSubmission;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import com.feedbactory.shared.feedback.FeedbackSubmission;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmission;


final class PersonProfileFeedbackSubmission implements ItemProfileFeedbackSubmission
{
   final public PersonalFeedbackPersonProfile personProfile;
   final public PersonalFeedbackSubmission feedbackSubmission;
   final public long submissionTime;


   PersonProfileFeedbackSubmission(final PersonalFeedbackPersonProfile personProfile, final PersonalFeedbackSubmission feedbackSubmission,
                                   final long submissionTime)
   {
      validate(personProfile, feedbackSubmission);

      this.personProfile = personProfile;
      this.feedbackSubmission = feedbackSubmission;
      this.submissionTime = submissionTime;
   }


   private void validate(final FeedbackItemProfile itemProfile, final FeedbackSubmission feedbackSubmission)
   {
      if (itemProfile == null)
         throw new IllegalArgumentException("Profile attached to item feedback submission cannot be null.");
      else if (feedbackSubmission == null)
         throw new IllegalArgumentException("Item feedback submission cannot be null.");

      /* I'm reluctant to enforce any validation on the submission time, eg. if it's set to a future time.
       * What if a switch from daylight savings during data restoration triggers the validation?
       */
   }


   @Override
   final public PersonalFeedbackPersonProfile getItemProfile()
   {
      return personProfile;
   }


   @Override
   final public PersonalFeedbackSubmission getFeedbackSubmission()
   {
      return feedbackSubmission;
   }


   @Override
   final public long getSubmissionTime()
   {
      return submissionTime;
   }
}