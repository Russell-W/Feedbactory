
package com.feedbactory.client.ui.feedback.personal;


final class NoCriteriaSubmissionScaleRenderer extends PersonalFeedbackSubmissionScaleRenderer
{
   final int[] emptyControlGap = new int[] {0};


   @Override
   final String getSubmissionColumnHeader()
   {
      return "";
   }


   @Override
   final int[] getSubmissionScaleControlGaps()
   {
      return emptyControlGap;
   }
}