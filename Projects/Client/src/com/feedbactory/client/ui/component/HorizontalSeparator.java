
package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIUtilities;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import javax.swing.JComponent;


final public class HorizontalSeparator extends JComponent
{
   static final private float MaximumBrightnessShift = 0.3f;


   @Override
   final protected void paintComponent(final Graphics graphics)
   {
      final Graphics2D graphics2D = (Graphics2D) graphics;

      final int verticalMidpoint = getHeight() / 2;
      final Rectangle2D gradientBounds = new Rectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1);
      final RadialGradientPaint upperGradientPaint = new RadialGradientPaint(gradientBounds, new float[] {0f, 1f}, new Color[] {UIUtilities.shiftColourBrightness(getBackground(), MaximumBrightnessShift), getBackground()}, MultipleGradientPaint.CycleMethod.NO_CYCLE);
      graphics2D.setPaint(upperGradientPaint);
      graphics2D.fillRect(0, 0, getWidth() - 1, verticalMidpoint);

      final RadialGradientPaint lowerGradientPaint = new RadialGradientPaint(gradientBounds, new float[] {0f, 1f}, new Color[] {UIUtilities.shiftColourBrightness(getBackground(), -MaximumBrightnessShift), getBackground()}, MultipleGradientPaint.CycleMethod.NO_CYCLE);
      graphics2D.setPaint(lowerGradientPaint);
      graphics2D.fillRect(0, verticalMidpoint, getWidth() - 1, verticalMidpoint);
   }
}