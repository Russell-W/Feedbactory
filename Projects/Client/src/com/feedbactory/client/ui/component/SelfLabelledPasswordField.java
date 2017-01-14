
package com.feedbactory.client.ui.component;


import java.awt.*;
import javax.swing.JPasswordField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


final public class SelfLabelledPasswordField extends JPasswordField
{
   static final private int HorizontalMarginSize = 12;

   final private boolean isNonCopyableTextField;

   private String label = "";
   private boolean hasTextBeenUpdated;


   public SelfLabelledPasswordField()
   {
      this(false);
   }


   public SelfLabelledPasswordField(final boolean isNonCopyableTextField)
   {
      this.isNonCopyableTextField = isNonCopyableTextField;

      initialise();
   }


   private void initialise()
   {
      if (isNonCopyableTextField)
         setEchoChar('\0');

      initialiseListeners();
   }


   private void initialiseListeners()
   {
      getDocument().addDocumentListener(new DocumentListener()
      {
         @Override
         final public void insertUpdate(final DocumentEvent documentEvent)
         {
            handleTextFieldUpdate();
         }


         @Override
         final public void removeUpdate(final DocumentEvent documentEvent)
         {
            handleTextFieldUpdate();
         }


         @Override
         final public void changedUpdate(final DocumentEvent documentEvent)
         {
            handleTextFieldUpdate();
         }
      });
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleTextFieldUpdate()
   {
      if (getDocument().getLength() == 0)
         hasTextBeenUpdated = false;
      else if (! hasTextBeenUpdated)
         hasTextBeenUpdated = true;
   }


   private void handleSetLabel(final String label)
   {
      this.label = label;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public Dimension getPreferredSize()
   {
      final FontMetrics fontMetrics = getFontMetrics(getFont());
      final int preferredWidth = fontMetrics.stringWidth(label) + (2 * HorizontalMarginSize);

      final Dimension preferredSize = super.getPreferredSize();
      preferredSize.width = Math.max(preferredWidth, preferredSize.width);

      return preferredSize;
   }


   @Override
   final protected void paintComponent(final Graphics graphics)
   {
      super.paintComponent(graphics);

      if (! hasTextBeenUpdated)
      {
         final Graphics2D graphics2D = (Graphics2D) graphics.create();

         graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
         graphics2D.setFont(getFont());
         graphics2D.setColor(Color.gray);
         graphics2D.drawString(label, 7, getBaseline(getWidth(), getHeight()));

         graphics2D.dispose();
      }
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public void setLabel(final String label)
   {
      handleSetLabel(label);
   }
}