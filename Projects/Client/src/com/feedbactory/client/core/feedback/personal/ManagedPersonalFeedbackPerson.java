/* Memos:
 * - There's no validation or state checking within this class; it's considered to be owned and managed by the client class(es).
 */

package com.feedbactory.client.core.feedback.personal;


import com.feedbactory.client.core.network.DataAvailabilityStatus;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackDetailedSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmission;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;


final public class ManagedPersonalFeedbackPerson
{
   private PersonalFeedbackPersonProfile personProfile;

   private DataAvailabilityStatus basicFeedbackSummaryAvailabilityStatus;
   private PersonalFeedbackBasicSummary basicFeedbackSummary;

   private DataAvailabilityStatus detailedFeedbackSummaryAvailabilityStatus;
   private PersonalFeedbackDetailedSummary detailedFeedbackSummary;

   private DataAvailabilityStatus feedbackSubmissionAvailabilityStatus;
   private PersonalFeedbackSubmission feedbackSubmission;


   ManagedPersonalFeedbackPerson()
   {
   }


   ManagedPersonalFeedbackPerson(final ManagedPersonalFeedbackPerson managedPerson)
   {
      this.personProfile = managedPerson.personProfile;
      this.basicFeedbackSummaryAvailabilityStatus = managedPerson.basicFeedbackSummaryAvailabilityStatus;
      this.basicFeedbackSummary = managedPerson.basicFeedbackSummary;
      this.detailedFeedbackSummaryAvailabilityStatus = managedPerson.detailedFeedbackSummaryAvailabilityStatus;
      this.detailedFeedbackSummary = managedPerson.detailedFeedbackSummary;
      this.feedbackSubmissionAvailabilityStatus = managedPerson.feedbackSubmissionAvailabilityStatus;
      this.feedbackSubmission = managedPerson.feedbackSubmission;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public PersonalFeedbackPersonProfile getPersonProfile()
   {
      return personProfile;
   }


   final public void setPersonProfile(final PersonalFeedbackPersonProfile personProfile)
   {
      this.personProfile = personProfile;
   }


   final public DataAvailabilityStatus getBasicFeedbackSummaryAvailabilityStatus()
   {
      return basicFeedbackSummaryAvailabilityStatus;
   }


   final public void setBasicFeedbackSummaryAvailabilityStatus(final DataAvailabilityStatus basicFeedbackFromEveryoneAvailabilityStatus)
   {
      this.basicFeedbackSummaryAvailabilityStatus = basicFeedbackFromEveryoneAvailabilityStatus;
   }


   final public PersonalFeedbackBasicSummary getBasicFeedbackSummary()
   {
      return basicFeedbackSummary;
   }


   final public void setBasicFeedbackSummary(final PersonalFeedbackBasicSummary basicFeedbackFromEveryone)
   {
      this.basicFeedbackSummary = basicFeedbackFromEveryone;
   }


   final public DataAvailabilityStatus getDetailedFeedbackSummaryAvailabilityStatus()
   {
      return detailedFeedbackSummaryAvailabilityStatus;
   }


   final public void setDetailedFeedbackSummaryAvailabilityStatus(final DataAvailabilityStatus detailedFeedbackFromEveryoneAvailabilityStatus)
   {
      this.detailedFeedbackSummaryAvailabilityStatus = detailedFeedbackFromEveryoneAvailabilityStatus;
   }


   final public PersonalFeedbackDetailedSummary getDetailedFeedbackSummary()
   {
      return detailedFeedbackSummary;
   }


   final public void setDetailedFeedbackSummary(final PersonalFeedbackDetailedSummary detailedFeedbackFromEveryone)
   {
      this.detailedFeedbackSummary = detailedFeedbackFromEveryone;
   }


   final public DataAvailabilityStatus getFeedbackSubmissionAvailabilityStatus()
   {
      return feedbackSubmissionAvailabilityStatus;
   }


   final public void setFeedbackSubmissionAvailabilityStatus(final DataAvailabilityStatus detailedFeedbackFromUserAvailabilityStatus)
   {
      this.feedbackSubmissionAvailabilityStatus = detailedFeedbackFromUserAvailabilityStatus;
   }


   final public PersonalFeedbackSubmission getFeedbackSubmission()
   {
      return feedbackSubmission;
   }


   final public void setFeedbackSubmission(final PersonalFeedbackSubmission detailedFeedbackFromUser)
   {
      this.feedbackSubmission = detailedFeedbackFromUser;
   }
}