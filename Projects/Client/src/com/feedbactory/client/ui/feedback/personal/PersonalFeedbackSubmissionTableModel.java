/* Memos:
 * - Package-private caller is trusted; table data is shared.
 *
 * - All types of criteria feedback are encapsulated by the one model rather than having a separate model for each
 *   (say PersonalFeedbackSubmissionTableModel<T extends PersonalFeedbackCriteria>). Unfortunately switching between table models causes other cosmetic table
 *   configurations to be lost, eg. column widths. These would have to be reset every time the table switched between criteria type.
 */

package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteria;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaAttributes;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleKeyValue;
import java.util.Map;
import javax.swing.table.AbstractTableModel;


final class PersonalFeedbackSubmissionTableModel extends AbstractTableModel
{
   static final int CriteriaColumnIndex = 0;
   static final int CriteriaFeedbackColumnIndex = 1;

   private PersonalFeedbackCriteriaType activeCriteriaType = PersonalFeedbackCriteriaType.None;
   private Map<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> activeFeedback;
   private PersonalFeedbackCriteria[] activeFeedbackCriteria = new PersonalFeedbackCriteria[0];
   private String activeCriteriaSubmissionColumnHeader = "";


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSetActiveFeedback(final PersonalFeedbackCriteriaType newActiveCriteriaType,
                                        final Map<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> newActiveFeedback)
   {
      activeCriteriaType = newActiveCriteriaType;
      activeFeedback = newActiveFeedback;
      activeFeedbackCriteria = new PersonalFeedbackCriteria[newActiveFeedback.size()];
      activeCriteriaSubmissionColumnHeader = PersonalFeedbackSubmissionScaleRenderer.getRendererFor(newActiveCriteriaType.attributes.getSubmissionScaleProfile()).getSubmissionColumnHeader();

      int criteriaIndex = 0;
      for (final PersonalFeedbackCriteria criteria : newActiveFeedback.keySet())
      {
         activeFeedbackCriteria[criteriaIndex] = criteria;
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
         case CriteriaFeedbackColumnIndex:
            return activeCriteriaSubmissionColumnHeader;
         default:
            throw new IllegalArgumentException();
      }
   }


   private Class<?> handleGetColumnClass(final int columnIndex)
   {
      switch (columnIndex)
      {
         case CriteriaColumnIndex:
            return PersonalFeedbackCriteria.class;
         case CriteriaFeedbackColumnIndex:
            return PersonalFeedbackSubmissionScaleKeyValue.class;
         default:
            throw new IllegalArgumentException();
      }
   }


   private Object handleGetValueAt(final int rowIndex, final int columnIndex)
   {
      switch (columnIndex)
      {
         case CriteriaColumnIndex:
            return activeFeedbackCriteria[rowIndex];
         case CriteriaFeedbackColumnIndex:
            return activeFeedback.get(activeFeedbackCriteria[rowIndex]);
         default:
            throw new IllegalArgumentException();
      }
   }


   private boolean handleIsCellEditable(final int columnIndex)
   {
      switch (columnIndex)
      {
         case CriteriaColumnIndex:
            return false;
         case CriteriaFeedbackColumnIndex:
            return true;
         default:
            throw new IllegalArgumentException();
      }
   }


   private void handleSetValueAt(final Object newValue, final int rowIndex, final int columnIndex)
   {
      if (columnIndex == CriteriaFeedbackColumnIndex)
         setValue(rowIndex, (PersonalFeedbackSubmissionScaleKeyValue) newValue, activeCriteriaType.attributes);
      else
         throw new IllegalArgumentException();
   }


   @SuppressWarnings("unchecked")
   private <E extends Enum<E> & PersonalFeedbackCriteria> void setValue(final int rowIndex, final PersonalFeedbackSubmissionScaleKeyValue submissionValue,
                                                                        final PersonalFeedbackCriteriaAttributes<E> criteriaAttributes)
   {
      /* Without an explicit type parameter, the type for this method call cannot be known by the compiler. Javac complains, Netbeans doesn't.
       * By providing the criteriaAttributes (which has a type derived from using a supplied PersonalFeedbackCriteriaType), the compiler can
       * successfully resolve the type and compile this method even though the criteriaResolver is not explicitly used within it.
       */
      final Map<E, PersonalFeedbackSubmissionScaleKeyValue> criteriaFeedback = (Map<E, PersonalFeedbackSubmissionScaleKeyValue>) activeFeedback;
      final E profileCriteria = (E) activeFeedbackCriteria[rowIndex];
      criteriaFeedback.put(profileCriteria, submissionValue);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final Map<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> getActiveFeedback()
   {
      return activeFeedback;
   }


   final void setActiveFeedback(final PersonalFeedbackCriteriaType newActiveCriteriaType,
                                final Map<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> newActiveFeedback)
   {
      handleSetActiveFeedback(newActiveCriteriaType, newActiveFeedback);
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
      return handleGetColumnClass(columnIndex);
   }


   @Override
   final public Object getValueAt(final int rowIndex, final int columnIndex)
   {
      return handleGetValueAt(rowIndex, columnIndex);
   }


   @Override
   final public boolean isCellEditable(final int rowIndex, final int columnIndex)
   {
      return handleIsCellEditable(columnIndex);
   }


   @Override
   final public void setValueAt(final Object newValue, final int rowIndex, final int columnIndex)
   {
      handleSetValueAt(newValue, rowIndex, columnIndex);
   }
}