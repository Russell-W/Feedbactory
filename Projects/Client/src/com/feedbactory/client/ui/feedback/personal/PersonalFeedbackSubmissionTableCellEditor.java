

package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleKeyValue;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleProfile;
import java.awt.Component;
import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;


final class PersonalFeedbackSubmissionTableCellEditor extends AbstractCellEditor implements TableCellEditor
{
   final private PersonalFeedbackSubmissionTableCellDelegate delegateEditor = new PersonalFeedbackSubmissionTableCellDelegate();


   PersonalFeedbackSubmissionTableCellEditor()
   {
      initialise();
   }


   private void initialise()
   {
      delegateEditor.setPaintProperties(true, 0);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final protected void fireEditingStopped()
   {
      super.fireEditingStopped();
   }


   @Override
   final public PersonalFeedbackSubmissionScaleKeyValue getCellEditorValue()
   {
      return delegateEditor.getValue();
   }


   @Override
   final public Component getTableCellEditorComponent(final JTable table, final Object value, final boolean isSelected, final int row, final int column)
   {
      delegateEditor.setValue((PersonalFeedbackSubmissionScaleKeyValue) value);

      return delegateEditor.getRendererComponent();
   }


   final void switchToSubmissionScaleProfile(final PersonalFeedbackSubmissionScaleProfile submissionScaleProfile)
   {
      delegateEditor.switchToSubmissionScaleProfile(submissionScaleProfile);
   }


   final void addSelectionChangeListener(final PersonalFeedbackSubmissionSelectionChangeListener selectionChangeListener)
   {
      delegateEditor.addSelectionChangeListener(selectionChangeListener);
   }


   final void removeSelectionChangeListener(final PersonalFeedbackSubmissionSelectionChangeListener selectionChangeListener)
   {
      delegateEditor.removeSelectionChangeListener(selectionChangeListener);
   }
}