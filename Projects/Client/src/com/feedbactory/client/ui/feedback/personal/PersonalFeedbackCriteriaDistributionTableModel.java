
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteria;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaDistribution;
import java.util.Map;
import java.util.Map.Entry;
import javax.swing.table.AbstractTableModel;


final public class PersonalFeedbackCriteriaDistributionTableModel extends AbstractTableModel
{
   static final int CriteriaColumnIndex = 0;
   static final int AverageRatingColumnIndex = 1;
   static final int RatingCountColumnIndex = 2;

   private PersonalFeedbackCriteria[] activeFeedbackCriteria = new PersonalFeedbackCriteria[0];
   private PersonalFeedbackCriteriaDistribution[] activeFeedbackDistribution = new PersonalFeedbackCriteriaDistribution[0];


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSwitchToFeedbackCriteria(final Map<? extends PersonalFeedbackCriteria, PersonalFeedbackCriteriaDistribution> newActiveCriteria)
   {
      int criteriaIndex = 0;
      activeFeedbackCriteria = new PersonalFeedbackCriteria[newActiveCriteria.size()];
      activeFeedbackDistribution = new PersonalFeedbackCriteriaDistribution[newActiveCriteria.size()];

      for (final Entry<? extends PersonalFeedbackCriteria, PersonalFeedbackCriteriaDistribution> criteriaFeedbackEntry : newActiveCriteria.entrySet())
      {
         activeFeedbackCriteria[criteriaIndex] = criteriaFeedbackEntry.getKey();
         activeFeedbackDistribution[criteriaIndex] = criteriaFeedbackEntry.getValue();

         criteriaIndex ++;
      }

      fireTableDataChanged();
   }


   private String handleGetColumnName(final int columnIndex)
   {
      switch (columnIndex)
      {
         case CriteriaColumnIndex:
            return "Criteria";
         case AverageRatingColumnIndex:
            return "Rating";
         case RatingCountColumnIndex:
            return "Rated by";

         default:
            throw new IllegalArgumentException();
      }
   }


   private Class<?> handleGetColumnClass(final int columnIndex)
   {
      switch (columnIndex)
      {
         case CriteriaColumnIndex:
            return String.class;
         case AverageRatingColumnIndex:
            return PersonalFeedbackCriteriaDistribution.class;
         case RatingCountColumnIndex:
            return Integer.class;

         default:
            throw new IllegalArgumentException();
      }
   }


   private Object handleGetValueAt(final int rowIndex, final int columnIndex)
   {
      switch (columnIndex)
      {
         case CriteriaColumnIndex:
            return activeFeedbackCriteria[rowIndex].getDisplayName();

         case AverageRatingColumnIndex:
            return activeFeedbackDistribution[rowIndex];

         case RatingCountColumnIndex:
            return activeFeedbackDistribution[rowIndex].numberOfRatings;

         default:
            throw new IllegalArgumentException();
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final PersonalFeedbackCriteria getCriteria(final int rowIndex)
   {
      return activeFeedbackCriteria[rowIndex];
   }


   final PersonalFeedbackCriteriaDistribution getFeedbackDistribution(final int rowIndex)
   {
      return activeFeedbackDistribution[rowIndex];
   }


   final void setFeedbackCriteria(final Map<? extends PersonalFeedbackCriteria, PersonalFeedbackCriteriaDistribution> newActiveCriteria)
   {
      handleSwitchToFeedbackCriteria(newActiveCriteria);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public int getRowCount()
   {
      return activeFeedbackCriteria.length;
   }


   @Override
   final public int getColumnCount()
   {
      return 3;
   }


   @Override
   final public String getColumnName(final int columnIndex)
   {
      return handleGetColumnName(columnIndex);
   }


   @Override
   final public Class<?> getColumnClass(final int columnIndex)
   {
      return handleGetColumnClass(columnIndex);
   }


   @Override
   final public Object getValueAt(final int rowIndex, final int columnIndex)
   {
      return handleGetValueAt(rowIndex, columnIndex);
   }
}