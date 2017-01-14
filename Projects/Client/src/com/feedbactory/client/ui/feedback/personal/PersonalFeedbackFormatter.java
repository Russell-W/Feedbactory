
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.client.ui.feedback.FeedbackCategoryDataFormatter;
import com.feedbactory.shared.feedback.FeedbackResultSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;
import java.text.NumberFormat;


final class PersonalFeedbackFormatter implements FeedbackCategoryDataFormatter
{
   static final private String SuppressedLowAverageRatingLabel;
   static
   {
      final NumberFormat numberFormat = NumberFormat.getNumberInstance();
      numberFormat.setMinimumFractionDigits(1);
      SuppressedLowAverageRatingLabel = "Less than " + numberFormat.format(PersonalFeedbackBasicSummary.MinimumVisibleAverageRating / 10f);
   }


   private String handleGetFeedbackSummaryString(final FeedbackResultSummary feedbackResultSummary)
   {
      final byte rating = ((PersonalFeedbackBasicSummary) feedbackResultSummary).getFeedbackResultSummary().byteValue();

      if (rating != PersonalFeedbackBasicSummary.SuppressedLowAverageRating)
      {
         final NumberFormat numberFormat = NumberFormat.getNumberInstance();
         numberFormat.setMinimumFractionDigits(1);
         return numberFormat.format(rating / 10f);
      }
      else
         return SuppressedLowAverageRatingLabel;
   }


   private String handleGetFeedbackSubmissionSummaryString(final Object feedbackSubmissionSummary)
   {
      final Byte rating = (Byte) feedbackSubmissionSummary;
      final NumberFormat numberFormat = NumberFormat.getNumberInstance();
      numberFormat.setMinimumFractionDigits(1);
      return numberFormat.format(rating / 10f);
   }


   private byte handleGetSortableFeedbackSummary(final PersonalFeedbackBasicSummary feedbackSummary)
   {
      if (feedbackSummary.averageRating != PersonalFeedbackBasicSummary.SuppressedLowAverageRating)
         return feedbackSummary.averageRating;
      else
         return (PersonalFeedbackBasicSummary.MinimumVisibleAverageRating - 1);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public String getFeedbackSummaryLabel()
   {
      return "Rating (0-10):";
   }


   @Override
   final public String getFeedbackSummaryString(final FeedbackResultSummary feedbackSummary)
   {
      return handleGetFeedbackSummaryString(feedbackSummary);
   }


   @Override
   final public String getFeedbackSubmissionSummaryString(final Object feedbackSubmissionSummary)
   {
      return handleGetFeedbackSubmissionSummaryString(feedbackSubmissionSummary);
   }


   @Override
   final public byte getSortableFeedbackSummary(final FeedbackResultSummary feedbackSummary)
   {
      return handleGetSortableFeedbackSummary((PersonalFeedbackBasicSummary) feedbackSummary);
   }


   @Override
   final public byte getSortableSubmissionSummary(final Object feedbackSubmissionSummary)
   {
      return ((Byte) feedbackSubmissionSummary).byteValue();
   }
}