/* Memos:
 * - The painting of the smiley shape is ripped almost verbatim from the Feedbactory smiley.
 *
 * - Would there ever be any need to animate this, a la the feedback smiley?
 *
 * - Unlike ShadowedProgressBar, the nested progress bar of this component cannot be repainted() without also repainting the parent (in this case over the top of it),
 *      hence the repaint setup is a bit different; when the progress bar is animated, for example, ShadowedProgressBar can push repaint calls directly to the underlying
 *      progress bar without repainting the shadow. We have no such luxury here, a repaint means repainting the entire component although we can at least cache the
 *      overlaid shadow image and clip shape.
 */


package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIUtilities;
import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JProgressBar;


public class SmileyProgressBar extends JComponent
{
   static final private Dimension ProgressBarPaintMargin = new Dimension(2, 2);

   static final private Color InnerShadeBaseColour = new Color(210, 210, 210, 90);

   static final private float LipThicknessPerHeight = 0.5f;
   static final private float LipThinningEffect = 0.09f;

   static final private float InnerShadeLinesPerLipThickness = 0.25f;
   static final private float MaximumInnerShadeLineDimmingFactor = -0.6f;

   static final private float OuterShadeLinesPerLipThickness = 0.3f;
   static final private float MaximumUpperShadeLineBrighteningFactor = 0.20f;
   static final private float MaximumLowerShadeLineDimmingFactor = -0.4f;

   static final private float MaximumCurveControlPointXProportion = 0.1f;

   final private DelegateProgressBar progressBar = new DelegateProgressBar();

   private BufferedImage smileyImage;
   private GeneralPath smileyPaintBoundsShape;

   final private byte smileyValue;


   public SmileyProgressBar()
   {
      this((byte) 50);
   }


   public SmileyProgressBar(final byte smileyValue)
   {
      this.smileyValue = smileyValue;

      initialise();
   }


   private void initialise()
   {
      setLayout(null);
      add(progressBar);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class DelegateProgressBar extends JProgressBar
   {
      @Override
      final public void repaint()
      {
         SmileyProgressBar.this.repaint();
      }


      @Override
      final public void repaint(final long milliseconds)
      {
         SmileyProgressBar.this.repaint(milliseconds);
      }


      @Override
      final public void repaint(final int x, final int y, final int width, final int height)
      {
         SmileyProgressBar.this.repaint(x, y, width, height);
      }


      @Override
      final public void repaint(final Rectangle repaintRectangle)
      {
         SmileyProgressBar.this.repaint(repaintRectangle);
      }


      @Override
      final public void repaint(final long milliseconds, final int x, final int y, final int width, final int height)
      {
         SmileyProgressBar.this.repaint(milliseconds, x, y, width, height);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   @Override
   final public void paintComponent(final Graphics graphics)
   {
      if ((getWidth() == 0) || (getHeight() == 0))
         return;

      if ((smileyImage == null) || (getWidth() != smileyImage.getWidth()) || (getHeight() != smileyImage.getHeight()))
      {
         smileyImage = UIUtilities.createCompatibleImage(getWidth(), getHeight(), Transparency.TRANSLUCENT);
         final Graphics2D graphics2D = smileyImage.createGraphics();
         paintSmileyImage(graphics2D);
         graphics2D.dispose();
      }

      paintProgressBar(graphics);
   }


   private void paintSmileyImage(final Graphics2D graphics2D)
   {
      final float floatValue = (smileyValue - 50) / 50f;
      final float floatWidth = getWidth() - 1;

      final int lipThickness = (int) (getHeight() * LipThicknessPerHeight);

      final float leftControlPointX = MaximumCurveControlPointXProportion * getWidth();
      final float rightControlPointX = floatWidth - leftControlPointX;

      final float smileyMidpointY = (getHeight() / 2f);
      final float neutralUpperCurveControlPointY = smileyMidpointY - (lipThickness / 2f);

      final float pathOriginY = smileyMidpointY - (floatValue * smileyMidpointY);
      final float upperCurveControlPointY = neutralUpperCurveControlPointY + (floatValue * neutralUpperCurveControlPointY);
      final float adjustedSmileyHeight = lipThickness - (LipThinningEffect * Math.abs(floatValue) * lipThickness);
      final float lowerCurveControlPointY = upperCurveControlPointY + adjustedSmileyHeight;

      graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      /* Render the upper and lower (outer) shade lines first, starting from closest to the lips and moving towards the outer bounds.
       * Removing this phase will only affect the shading effect, the rest of the lips will still be drawn as is.
       */
      final int numberOfOuterShadeLines = (int) (lipThickness * OuterShadeLinesPerLipThickness);
      final float outerShadeAlphaShift = getBackground().getAlpha() / (numberOfOuterShadeLines + 1);

      int outerShadeColourAlpha = getBackground().getAlpha();
      Color upperShadeColour = UIUtilities.shiftColourBrightness(getBackground(), MaximumUpperShadeLineBrighteningFactor);
      Color lowerShadeColour = UIUtilities.shiftColourBrightness(getBackground(), MaximumLowerShadeLineDimmingFactor);

      for (int outerShadeLineNumber = 0; outerShadeLineNumber < numberOfOuterShadeLines; outerShadeLineNumber ++)
      {
         final CubicCurve2D outlineShadeUpperCurve = new CubicCurve2D.Float(0f, pathOriginY, leftControlPointX, upperCurveControlPointY - outerShadeLineNumber, rightControlPointX, upperCurveControlPointY - outerShadeLineNumber, floatWidth, pathOriginY);
         final CubicCurve2D outlineShadeLowerCurve = new CubicCurve2D.Float(0f, pathOriginY, leftControlPointX, lowerCurveControlPointY + outerShadeLineNumber, rightControlPointX, lowerCurveControlPointY + outerShadeLineNumber, floatWidth, pathOriginY);

         graphics2D.setColor(upperShadeColour);
         graphics2D.draw(outlineShadeUpperCurve);
         graphics2D.setColor(lowerShadeColour);
         graphics2D.draw(outlineShadeLowerCurve);

         outerShadeColourAlpha -= outerShadeAlphaShift;

         upperShadeColour = new Color(upperShadeColour.getRed(), upperShadeColour.getGreen(), upperShadeColour.getBlue(), outerShadeColourAlpha);
         lowerShadeColour = new Color(lowerShadeColour.getRed(), lowerShadeColour.getGreen(), lowerShadeColour.getBlue(), outerShadeColourAlpha);
      }

      // Render the lips themselves, starting with the fill of the base colour.
      smileyPaintBoundsShape = new GeneralPath();

      smileyPaintBoundsShape.moveTo(0f, pathOriginY);
      smileyPaintBoundsShape.curveTo(leftControlPointX, upperCurveControlPointY, rightControlPointX, upperCurveControlPointY, floatWidth, pathOriginY);
      smileyPaintBoundsShape.curveTo(rightControlPointX, lowerCurveControlPointY, leftControlPointX, lowerCurveControlPointY, 0f, pathOriginY);
      smileyPaintBoundsShape.closePath();

      /* Render the shade lines within the lips, starting from the outer (darker) lines to the inner.
       * The reverse order would give the darker lines drawing precedence.
       */
      final int numberOfInnerShadeLines = (int) (lipThickness * InnerShadeLinesPerLipThickness);
      final float colourDimmingFactor = MaximumInnerShadeLineDimmingFactor / numberOfInnerShadeLines;

      Color previousShadeColour = InnerShadeBaseColour;

      for (int innerShadeLineNumber = numberOfInnerShadeLines; innerShadeLineNumber > 0; innerShadeLineNumber --)
      {
         final GeneralPath innerShadePath = new GeneralPath();

         innerShadePath.moveTo(0f, pathOriginY);
         innerShadePath.curveTo(leftControlPointX, upperCurveControlPointY + innerShadeLineNumber, rightControlPointX, upperCurveControlPointY + innerShadeLineNumber, floatWidth, pathOriginY);
         innerShadePath.curveTo(rightControlPointX, lowerCurveControlPointY - innerShadeLineNumber, leftControlPointX, lowerCurveControlPointY - innerShadeLineNumber, 0f, pathOriginY);
         innerShadePath.closePath();

         previousShadeColour = UIUtilities.shiftColourBrightness(previousShadeColour, colourDimmingFactor);

         graphics2D.setColor(previousShadeColour);
         graphics2D.draw(innerShadePath);
      }

      previousShadeColour = UIUtilities.shiftColourBrightness(previousShadeColour, colourDimmingFactor);
      graphics2D.setColor(previousShadeColour);
      graphics2D.draw(smileyPaintBoundsShape);

      final float halfSmileyHeight = (adjustedSmileyHeight / 2);
      final int smileyUpperY = Math.min((int) pathOriginY, (int) (smileyMidpointY - halfSmileyHeight));
      final int smileyLowerY = Math.max((int) pathOriginY, (int) (smileyMidpointY + halfSmileyHeight));

      progressBar.setBounds(-ProgressBarPaintMargin.width, smileyUpperY - ProgressBarPaintMargin.height, getWidth() + (2 * ProgressBarPaintMargin.width), (smileyLowerY - smileyUpperY) + (2 * ProgressBarPaintMargin.height));
   }


   @Override
   final protected void paintChildren(final Graphics graphics)
   {
      // Our paintComponent() already paints the nested progress bar, which is being treated as part of this component rather than a child component.
   }


   private void paintProgressBar(final Graphics graphics)
   {
      /* The progress bar needs to be a child component for us to be able to resize and place it for correct drawing.
       * So it would ordinarily be automatically painted via paintChildren(), however we wish clients of this class (eg. graftable) to be able to paint the
       * complete component via paintComponent(), so as to avoid the unnecessary overhead of JComponent.paint().
       */
      final Shape originalClip = graphics.getClip();
      graphics.setClip(smileyPaintBoundsShape);
      super.paintChildren(graphics);
      graphics.setClip(originalClip);
      graphics.drawImage(smileyImage, 0, 0, null);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void setMaximum(final int maximum)
   {
      progressBar.setMaximum(maximum);
   }


   final public void setValue(final int value)
   {
      progressBar.setValue(value);
   }


   final public boolean isIndeterminate()
   {
      return progressBar.isIndeterminate();
   }


   final public void setIndeterminate(final boolean isIndeterminate)
   {
      progressBar.setIndeterminate(isIndeterminate);
   }
}