
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.shared.feedback.personal.ExcellentToTerribleFeedbackSubmissionScale;


final class ExcellentToTerribleSubmissionScaleRenderer extends PersonalFeedbackSubmissionScaleRenderer
{
   @Override
   final public String getSubmissionColumnHeader()
   {
      final StringBuilder scaleHeader = new StringBuilder();

      scaleHeader.append("  ");
      scaleHeader.append(ExcellentToTerribleFeedbackSubmissionScale.Excellent.displayName);
      scaleHeader.append("          ");
      scaleHeader.append(ExcellentToTerribleFeedbackSubmissionScale.Good.displayName);
      scaleHeader.append("               ");
      scaleHeader.append(ExcellentToTerribleFeedbackSubmissionScale.Fair.displayName);
      scaleHeader.append("                ");
      scaleHeader.append(ExcellentToTerribleFeedbackSubmissionScale.Poor.displayName);
      scaleHeader.append("              ");
      scaleHeader.append(ExcellentToTerribleFeedbackSubmissionScale.Terrible.displayName);
      scaleHeader.append("           Pass");

      return scaleHeader.toString();
   }


   @Override
   final public int[] getSubmissionScaleControlGaps()
   {
      return new int[] {27, 52, 52, 52, 52, 52};
   }
}