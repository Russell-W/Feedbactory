/* Memos:
 * - If no gradient paint is supplied, the background colour is used.
 *
 */

package com.feedbactory.client.ui.component;


import java.awt.*;
import java.awt.geom.Rectangle2D;
import javax.swing.JComponent;


public class ShadedComponent extends JComponent
{
   private RadialGradientPaintProfile radialGradient;


   public ShadedComponent()
   {
   }


   public ShadedComponent(final RadialGradientPaintProfile radialGradient)
   {
      this.radialGradient = radialGradient;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class RadialGradientPaintProfile
   {
      final private float startXFactor;
      final private float startYFactor;
      final private float widthFactor;
      final private float heightFactor;
      final private Color[] colours;
      final private float[] colourRanges;

      public RadialGradientPaintProfile(final float radialGradientPaintStartXFactor, final float radialGradientPaintStartYFactor,
                                        final float radialGradientPaintWidthFactor, final float radialGradientPaintHeightFactor,
                                        final Color[] radialGradientPaintColours, final float[] radialGradientPaintColourRanges)
      {
         final Color[] coloursCopy = radialGradientPaintColours.clone();
         final float[] colourRangesCopy = radialGradientPaintColourRanges.clone();

         validate(radialGradientPaintWidthFactor, radialGradientPaintHeightFactor,
                     coloursCopy, colourRangesCopy);

         this.startXFactor = radialGradientPaintStartXFactor;
         this.startYFactor = radialGradientPaintStartYFactor;
         this.widthFactor = radialGradientPaintWidthFactor;
         this.heightFactor = radialGradientPaintHeightFactor;
         this.colours = coloursCopy;
         this.colourRanges = colourRangesCopy;
      }


      private void validate(final float radialGradientPaintWidthFactor, final float radialGradientPaintHeightFactor,
                            final Color[] radialGradientPaintColours, final float[] radialGradientPaintColourRanges)
      {
         if (radialGradientPaintWidthFactor < 0f)
            throw new IllegalArgumentException("Radial gradient paint width factor cannot be less than zero: " + radialGradientPaintWidthFactor);
         else if (radialGradientPaintHeightFactor < 0f)
            throw new IllegalArgumentException("Radial gradient paint height factor cannot be less than zero: " + radialGradientPaintHeightFactor);
         else if ((radialGradientPaintColours == null) || (radialGradientPaintColours.length == 0))
            throw new IllegalArgumentException("Radial gradient paint colours cannot be null or empty.");

         for (final Color color : radialGradientPaintColours)
         {
            if (color == null)
               throw new IllegalArgumentException("Radial gradient paint colour cannot be null.");
         }

         if ((radialGradientPaintColourRanges == null) || (radialGradientPaintColourRanges.length == 0))
            throw new IllegalArgumentException("Radial gradient paint colour ranges cannot be null or empty.");
      }


      /****************************************************************************
       * 
       ***************************************************************************/


      final public Color getStartColour()
      {
         return colours[0];
      }


      final public Color getEndColor()
      {
         return colours[colours.length - 1];
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   @Override
   protected void paintComponent(final Graphics graphics)
   {
      final Graphics2D graphics2D = (Graphics2D) graphics;

      if (radialGradient != null)
      {
         final Rectangle2D gradientPaintExtent = new Rectangle2D.Float(radialGradient.startXFactor * getWidth(), radialGradient.startYFactor * getHeight(), radialGradient.widthFactor * getWidth(), radialGradient.heightFactor * getHeight());
         final RadialGradientPaint radialGradientPaint = new RadialGradientPaint(gradientPaintExtent, radialGradient.colourRanges, radialGradient.colours, MultipleGradientPaint.CycleMethod.NO_CYCLE);
         graphics2D.setPaint(radialGradientPaint);
      }
      else
         graphics2D.setColor(getBackground());

      graphics2D.fill(getFillShape());
   }


   protected Shape getFillShape()
   {
      return new Rectangle(0, 0, getWidth(), getHeight());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   public void setRadialGradientPaint(final RadialGradientPaintProfile radialGradient)
   {
      this.radialGradient = radialGradient;
   }
}