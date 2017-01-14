

package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleKeyValue;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleProfile;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;


final class PersonalFeedbackSubmissionTableCellRenderer implements TableCellRenderer
{
   final private PersonalFeedbackSubmissionTableCellDelegate delegateRenderer = new PersonalFeedbackSubmissionTableCellDelegate();


   @Override
   final public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column)
   {
      delegateRenderer.setValue((PersonalFeedbackSubmissionScaleKeyValue) value);
      delegateRenderer.setPaintProperties(isSelected, row);

      return delegateRenderer.getRendererComponent();
   }


   final void switchToSubmissionScaleProfile(final PersonalFeedbackSubmissionScaleProfile submissionScaleProfile)
   {
      delegateRenderer.switchToSubmissionScaleProfile(submissionScaleProfile);
   }
}