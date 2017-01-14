
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.shared.feedback.personal.ExcellentToTerribleFeedbackSubmissionScale;
import com.feedbactory.shared.feedback.personal.NoCriteriaFeedbackSubmissionScale;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleProfile;
import com.feedbactory.shared.feedback.personal.VeryToNotAtAllFeedbackSubmissionScale;


abstract class PersonalFeedbackSubmissionScaleRenderer
{
   abstract String getSubmissionColumnHeader();
   abstract int[] getSubmissionScaleControlGaps();


   /****************************************************************************
    *
    ***************************************************************************/


   static PersonalFeedbackSubmissionScaleRenderer getRendererFor(final PersonalFeedbackSubmissionScaleProfile submissionScaleProfile)
   {
      if (submissionScaleProfile == null)
         throw new NullPointerException();
      else if (submissionScaleProfile == ExcellentToTerribleFeedbackSubmissionScale.instance)
         return new ExcellentToTerribleSubmissionScaleRenderer();
      else if (submissionScaleProfile == VeryToNotAtAllFeedbackSubmissionScale.instance)
         return new VeryToNotAtAllSubmissionScaleRenderer();
      else if (submissionScaleProfile == NoCriteriaFeedbackSubmissionScale.instance)
         return new NoCriteriaSubmissionScaleRenderer();
      else
         throw new AssertionError("Unhandled renderer for submission scale profile: " + submissionScaleProfile);
   }
}