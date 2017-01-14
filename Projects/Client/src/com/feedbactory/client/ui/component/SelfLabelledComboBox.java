/* Memos:
 * - At this time, the model used must be DefaultComboBoxModel.
 */

package com.feedbactory.client.ui.component;


import java.awt.Color;
import javax.swing.*;


final public class SelfLabelledComboBox extends StripedListComboBox
{
   private void handleSetLabel(final String label)
   {
      final DefaultComboBoxModel model = (DefaultComboBoxModel) getModel();
      model.insertElementAt(label, 0);
      model.setSelectedItem(label);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final protected void updateRendererFor(final Object value, final int index, final boolean isSelected)
   {
      super.updateRendererFor(value, index, isSelected);

      if (value != null)
      {
         final DefaultComboBoxModel model = (DefaultComboBoxModel) getModel();

         /* Paint the label item grey, which includes the cases where it's within the list as well as the case where it's
          * the currently displayed root item. Note that the item that needs to be checked against is the list model's selected item,
          * not the list popup's selected item, which changes as the user moves the mouse/keys.
          */
         if ((index == 0) || ((index == -1) && (model.getSelectedItem() == model.getElementAt(0))))
            rendererComponent.setForeground(Color.gray);
         else
            rendererComponent.setForeground(Color.black);
      }
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public void setModel(final ComboBoxModel aModel)
   {
      if (aModel instanceof DefaultComboBoxModel)
         super.setModel(aModel);
      else
         throw new IllegalArgumentException();
   }


   final public void setLabel(final String label)
   {
      handleSetLabel(label);
   }
}