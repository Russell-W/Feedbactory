
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.client.ui.UIConstants;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;


class PersonalFeedbackGeneralCellRenderer<T> extends DefaultTableCellRenderer
{
   PersonalFeedbackGeneralCellRenderer()
   {
      initialise();
   }


   private void initialise()
   {
      setFont(UIConstants.RegularFont);
   }


   @Override
   @SuppressWarnings("unchecked")
   final public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column)
   {
      updateValue((T) value);

      if (isSelected)
         setBackground(UIConstants.ListCellSelectionHighlightColour);
      else
         setBackground(((row % 2) == 0) ? UIConstants.ListCellRegularColor : UIConstants.ListCellStripeColour);

      return this;
   }


   protected void updateValue(final T value)
   {
      setValue(value);
   }
}