
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackDetailedSummary;
import javax.swing.table.AbstractTableModel;


final public class PersonalFeedbackDistributionTableModel extends AbstractTableModel
{
   static final int RatingColumnIndex = 0;
   static final int DistributionPercentageColumnIndex = 1;

   private byte[] activeFeedbackDistributionPercentages = new byte[0];


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSetFeedbackDistributionPercentages(final byte[] feedbackDistributionPercentages)
   {
      this.activeFeedbackDistributionPercentages = feedbackDistributionPercentages;

      fireTableDataChanged();
   }


   private String handleGetColumnName(final int columnIndex)
   {
      switch (columnIndex)
      {
         case RatingColumnIndex:
            return "Rating";
         case DistributionPercentageColumnIndex:
            return "Rated by (%)";

         default:
            throw new IllegalArgumentException();
      }
   }


   private Object handleGetValueAt(final int rowIndex, final int columnIndex)
   {
      if (columnIndex == RatingColumnIndex)
         return rowIndex;
      else if (columnIndex == DistributionPercentageColumnIndex)
      {
         final byte feedbackDistributionPercentage = activeFeedbackDistributionPercentages[rowIndex];
         if (feedbackDistributionPercentage != PersonalFeedbackDetailedSummary.SuppressedLowAveragePercentage)
            return Byte.toString(feedbackDistributionPercentage) + '%';
         else
            return "-";
      }
      else
         throw new IllegalArgumentException();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void setFeedbackDistributionPercentages(final byte[] feedbackDistributionPercentages)
   {
      handleSetFeedbackDistributionPercentages(feedbackDistributionPercentages);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public int getRowCount()
   {
      return activeFeedbackDistributionPercentages.length;
   }


   @Override
   final public int getColumnCount()
   {
      return 2;
   }


   @Override
   final public String getColumnName(final int columnIndex)
   {
      return handleGetColumnName(columnIndex);
   }


   @Override
   final public Class<?> getColumnClass(final int columnIndex)
   {
      return (columnIndex == RatingColumnIndex) ? Integer.class : String.class;
   }


   @Override
   final public Object getValueAt(final int rowIndex, final int columnIndex)
   {
      return handleGetValueAt(rowIndex, columnIndex);
   }
}