
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.shared.feedback.personal.VeryToNotAtAllFeedbackSubmissionScale;


final class VeryToNotAtAllSubmissionScaleRenderer extends PersonalFeedbackSubmissionScaleRenderer
{
   @Override
   final public String getSubmissionColumnHeader()
   {
      final StringBuilder scaleHeader = new StringBuilder();

      scaleHeader.append("  ");
      scaleHeader.append(VeryToNotAtAllFeedbackSubmissionScale.Very.displayName);
      scaleHeader.append("        ");
      scaleHeader.append(VeryToNotAtAllFeedbackSubmissionScale.Considerably.displayName);
      scaleHeader.append("    ");
      scaleHeader.append(VeryToNotAtAllFeedbackSubmissionScale.Moderately.displayName);
      scaleHeader.append("      ");
      scaleHeader.append(VeryToNotAtAllFeedbackSubmissionScale.NotVery.displayName);
      scaleHeader.append("         ");
      scaleHeader.append(VeryToNotAtAllFeedbackSubmissionScale.NotAtAll.displayName);
      scaleHeader.append("          Pass");

      return scaleHeader.toString();
   }


   @Override
   final public int[] getSubmissionScaleControlGaps()
   {
      return new int[] {12, 55, 55, 55, 55, 55};
   }
}