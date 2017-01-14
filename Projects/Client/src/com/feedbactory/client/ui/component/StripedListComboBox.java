
package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIConstants;
import java.awt.Component;
import java.awt.Graphics;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;


public class StripedListComboBox extends JComboBox
{
   final protected JLabel rendererComponent = new StripedListComboBoxCellRenderer();


   public StripedListComboBox()
   {
      super();

      initialise();
   }


   public StripedListComboBox(final ComboBoxModel comboBoxModel)
   {
      super(comboBoxModel);

      initialise();
   }


   private void initialise()
   {
      setRenderer((ListCellRenderer) rendererComponent);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class StripedListComboBoxCellRenderer extends DefaultListCellRenderer
   {
      private boolean isPaintingRootCell;


      private StripedListComboBoxCellRenderer()
      {
         initialise();
      }


      private void initialise()
      {
         /* Setting/overriding the getName() method to return "ComboBox.renderer" will ensure that the Nimbus styling is correctly applied
          * including some default cell spacing however the vertical spacing is definitely a little squishy, so it's set manually here.
          */
         setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 3));
      }


      @Override
      final public String getName()
      {
         // Refer to SynthComboBoxUI source - name needs to be set for the Nimbus styling to be correctly applied.
         final String name = super.getName();
         return (name != null) ? name : "ComboBox.renderer";
      }


      @Override
      final public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus)
      {
         isPaintingRootCell = (index == -1);

         updateRendererFor(value, index, isSelected);

         return this;
      }


      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         super.paintComponent(graphics);

         if (! isPaintingRootCell)
         {
            graphics.setColor(UIConstants.ListCellBorderColour);

            final int farRight = getWidth() - 1;
            final int farBottom = getHeight() - 1;

            graphics.drawLine(0, farBottom, farRight, farBottom);
            graphics.drawLine(farRight, 0, farRight, farBottom);
         }
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   protected String getLabelFor(final Object value)
   {
      return (value != null) ? value.toString() : "";
   }


   protected void updateRendererFor(final Object value, final int index, final boolean isSelected)
   {
      rendererComponent.setText(getLabelFor(value));

      if (isSelected)
         rendererComponent.setBackground(UIConstants.ListCellSelectionHighlightColour);
      else
         rendererComponent.setBackground(((index % 2) == 0) ? UIConstants.ListCellRegularColor : UIConstants.ListCellStripeColour);
   }
}