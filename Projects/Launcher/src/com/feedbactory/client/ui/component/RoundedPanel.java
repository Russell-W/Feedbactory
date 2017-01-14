/* Memos:
 * - The background colour is used to derive the colour of the border, including its upper and lower shading.
 */

package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIUtilities;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.border.AbstractBorder;


public class RoundedPanel extends ShadedComponent
{
   static final private int DefaultRoundedPanelCornerRadius = 10;

   final private int cornerRadius;


   public RoundedPanel()
   {
      this(DefaultRoundedPanelCornerRadius, null);
   }


   public RoundedPanel(final int cornerRadius)
   {
      this(cornerRadius, null);
   }


   public RoundedPanel(final RadialGradientPaintProfile radialGradient)
   {
      this(DefaultRoundedPanelCornerRadius, radialGradient);
   }


   public RoundedPanel(final int cornerRadius, final RadialGradientPaintProfile radialGradient)
   {
      super(radialGradient);

      validate(cornerRadius);

      this.cornerRadius = cornerRadius;

      initialise();
   }


   private void validate(final int cornerRadius)
   {
      if (cornerRadius < 0)
         throw new IllegalArgumentException("Rounded panel corner radius cannot be less than zero: " + cornerRadius);
   }


   private void initialise()
   {
      // Already set for JComponent, uncomment if we later shift ShadedComponent to subclass JPanel.
      //setOpaque(false);
      setBorder(new RoundedBorder());
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class RoundedBorder extends AbstractBorder
   {
      @Override
      final public void paintBorder(final Component component, final Graphics graphics, final int x, final int y, final int width, final int height)
      {
         final Graphics2D graphics2D = (Graphics2D) graphics.create();

         graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

         final Color outerBorderColour = UIUtilities.shiftColourBrightness(component.getBackground(), -0.3f);

         final Color innerColourOne = new Color(outerBorderColour.getRed(), outerBorderColour.getGreen(), outerBorderColour.getBlue(), 45);
         final Color innerColourTwo = new Color(outerBorderColour.getRed(), outerBorderColour.getGreen(), outerBorderColour.getBlue(), 90);

         graphics2D.setColor(innerColourOne);
         graphics2D.drawRoundRect(x, y + 2, width - 1, height - 5, cornerRadius, cornerRadius);
         graphics2D.setColor(innerColourTwo);
         graphics2D.drawRoundRect(x, y + 1, width - 1, height - 3, cornerRadius, cornerRadius); 
         graphics2D.setColor(outerBorderColour);
         graphics2D.drawRoundRect(x, y, width - 1, height - 1, cornerRadius, cornerRadius);

         graphics2D.dispose();            
      }


      @Override
      final public Insets getBorderInsets(final Component component)
      {
         /* Given that the rounded corner size is customisable, there's the potential for cutting off the corners of contained children.
          * It would be more correct to provide a scaled getBorderInset() based on the corner size, but I also hate the thought of
          * losing large chunks of real estate (essentially an enforced margin) on the flat edges to cater for larger rounded corners.
          * I'd rather allow the application-specific components control over how large the margins should be.
          * So, a small inset of 3 pixels each way is a small concession.
          */
         return new Insets(3, 3, 3, 3);
      }
   }


   /****************************************************************************
    *
    *
    * 
    ***************************************************************************/


   @Override
   final protected Shape getFillShape()
   {
      // For rounded rectangles a width/height of getWidth()/getHeight() will spill over the edges by one.
      return new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);
   }
}