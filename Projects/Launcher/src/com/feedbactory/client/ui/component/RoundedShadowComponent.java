package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.UIConstants;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.GroupLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;


final public class RoundedShadowComponent extends JComponent
{
   final private JFrame parentFrame;
   final private JComponent component;
   final private int maximumShadowTransparency;
   final private int activeShadowLength;
   final private int inactiveShadowLength;


   public RoundedShadowComponent(final JComponent component)
   {
      this(component, UIConstants.ComponentShadowLength, UIConstants.ComponentMaximumShadowTransparency);
   }


   public RoundedShadowComponent(final JComponent component, final int shadowLength, final int maximumShadowTransparency)
   {
      validate(shadowLength, shadowLength, maximumShadowTransparency);

      this.parentFrame = null;
      this.component = component;
      this.maximumShadowTransparency = maximumShadowTransparency;
      this.activeShadowLength = shadowLength;
      this.inactiveShadowLength = 0;

      initialise();
   }


   public RoundedShadowComponent(final JFrame parent, final JComponent component, final int activeShadowLength, final int inactiveShadowLength, final int maximumShadowTransparency)
   {
      validate(activeShadowLength, inactiveShadowLength, maximumShadowTransparency);

      this.parentFrame = parent;
      this.component = component;
      this.maximumShadowTransparency = maximumShadowTransparency;
      this.activeShadowLength = activeShadowLength;
      this.inactiveShadowLength = inactiveShadowLength;

      initialise();
   }


   private void validate(final int activeShadowLength, final int inactiveShadowLength, final int maximumShadowTransparency)
   {
      if (activeShadowLength < 0)
         throw new IllegalArgumentException("Shadow length cannot be less than zero: " + activeShadowLength);
      else if (inactiveShadowLength < 0)
         throw new IllegalArgumentException("Shadow length cannot be less than zero: " + inactiveShadowLength);
      else if ((maximumShadowTransparency < 0) || (maximumShadowTransparency > 255))
         throw new IllegalArgumentException("Maximum shadow transparency must be between 0 and 255: " + maximumShadowTransparency);
   }


   private void initialise()
   {
      final int frameShadowLength = Math.max(activeShadowLength, inactiveShadowLength);

      final GroupLayout panelLayout = new GroupLayout(this);
      setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addComponent(component)
         .addGap(frameShadowLength)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addComponent(component)
         .addGap(frameShadowLength)
      );
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private BufferedImage createHorizontalGradientImage(final int shadowLength)
   {
      final BufferedImage gradientImage = UIUtilities.createCompatibleImage(1, shadowLength, Transparency.TRANSLUCENT);

      final float transparencyShift = maximumShadowTransparency / (float) shadowLength;
      float transparency = 0f;
      Color paintColour = new Color(0, 0, 0, 0);

      for (int lineNumber = shadowLength - 1; lineNumber >= 0; lineNumber --)
      {
         gradientImage.setRGB(0, lineNumber, paintColour.getRGB());
         transparency += transparencyShift;
         paintColour = new Color(0, 0, 0, (int) (transparency + 0.5f));
      }

      return gradientImage;
   }


   private void drawHorizontalShadow(final Graphics2D graphics2D, final int shadowLength, final BufferedImage gradientImage)
   {
      final int gradientStartX = shadowLength * 2;
      final int gradientWidth = component.getWidth() - gradientStartX - shadowLength;
      int gradientY = component.getHeight();

      graphics2D.drawImage(gradientImage, gradientStartX, gradientY, gradientWidth, shadowLength, null);
   }


   private BufferedImage createVerticalGradientImage(final int shadowLength)
   {
      final BufferedImage gradientImage = UIUtilities.createCompatibleImage(shadowLength, 1, Transparency.TRANSLUCENT);

      final float transparencyShift = maximumShadowTransparency / (float) shadowLength;
      float transparency = 0f;
      Color paintColour = new Color(0, 0, 0, 0);

      for (int lineNumber = shadowLength - 1; lineNumber >= 0; lineNumber --)
      {
         gradientImage.setRGB(lineNumber, 0, paintColour.getRGB());
         transparency += transparencyShift;
         paintColour = new Color(0, 0, 0, (int) (transparency + 0.5f));
      }

      return gradientImage;
   }


   private void drawVerticalShadow(final Graphics2D graphics2D, final int shadowLength, final BufferedImage gradientImage)
   {
      final int gradientStartY = shadowLength * 2;
      final int gradientHeight = component.getHeight() - gradientStartY - shadowLength;
      int gradientX = component.getWidth();

      graphics2D.drawImage(gradientImage, gradientX, gradientStartY, shadowLength, gradientHeight, null);
   }


   private BufferedImage createCornerArcGradientImage(final int shadowLength)
   {
      final BufferedImage image = UIUtilities.createCompatibleImage(shadowLength * 2, shadowLength * 2, Transparency.TRANSLUCENT);
      final Graphics2D graphics2D = image.createGraphics();
      graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      final float transparencyShift = maximumShadowTransparency / (float) shadowLength;
      float transparency = 0f;
      Color paintColour = new Color(0, 0, 0, 0);
      int circleTopLeft = -shadowLength * 2;
      int circleSize = shadowLength * 4 - 1;

      /* We do one extra iteration to help fill in any leading gaps. The more rounded that a frame corner is, the more extra iterations we would
       * require to ensure that the gap is filled. We would also have to be mindful that our colour's alpha doesn't spill over the maximum due
       * to the additional iterations.
       */
      for (int lineNumber = 0; lineNumber <= shadowLength; lineNumber ++)
      {
         graphics2D.setColor(paintColour);
         graphics2D.drawArc(circleTopLeft, circleTopLeft, circleSize, circleSize, 0, -90);

         circleTopLeft ++;
         circleSize -= 2;
         transparency += transparencyShift;
         paintColour = new Color(0, 0, 0, (int) (transparency + 0.5f));
      }

      graphics2D.dispose();

      return image;
   }


   private void drawBottomRightCornerArc(final Graphics2D graphics2D, final int shadowLength, final BufferedImage image)
   {
      graphics2D.drawImage(image, component.getWidth() - shadowLength, component.getHeight() - shadowLength, null);
   }


   private void drawBottomLeftCornerArc(final Graphics2D graphics2D, final int shadowLength, final BufferedImage image)
   {
      final int outputStartX = shadowLength * 2;
      final int outputStartY = component.getHeight() - shadowLength;
      final int outputEndX = outputStartX - (image.getWidth() - 1);
      final int outputEndY = outputStartY + (image.getHeight() - 1);

      graphics2D.drawImage(image, outputStartX, outputStartY, outputEndX, outputEndY, 0, 0, image.getWidth() - 1, image.getHeight() - 1, null);
   }


   private void drawTopRightCornerArc(final Graphics2D graphics2D, final int shadowLength, final BufferedImage image)
   {
      final int outputStartX = component.getWidth() - shadowLength;
      final int outputStartY = shadowLength * 2;
      final int outputEndX = outputStartX + (image.getWidth() - 1);
      final int outputEndY = outputStartY - (image.getHeight() - 1);

      graphics2D.drawImage(image, outputStartX, outputStartY, outputEndX, outputEndY, 0, 0, image.getWidth() - 1, image.getHeight() - 1, null);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final protected void paintComponent(final Graphics graphics)
   {
      final int shadowLength;

      if (parentFrame != null)
      {
         if ((parentFrame.getExtendedState() == Frame.MAXIMIZED_BOTH) || (! TranslucencyUtilities.isTranslucencySupported()))
            return;

         shadowLength = parentFrame.isActive() ? activeShadowLength : inactiveShadowLength;
      }
      else
         shadowLength = activeShadowLength;

      final BufferedImage horizontalGradientImage = createHorizontalGradientImage(shadowLength);
      final BufferedImage verticalGradientImage = createVerticalGradientImage(shadowLength);
      final BufferedImage cornerGradientImage = createCornerArcGradientImage(shadowLength);

      final Graphics2D graphics2D = (Graphics2D) graphics.create();

      drawHorizontalShadow(graphics2D, shadowLength, horizontalGradientImage);

      drawVerticalShadow(graphics2D, shadowLength, verticalGradientImage);

      drawBottomRightCornerArc(graphics2D, shadowLength, cornerGradientImage);
      drawBottomLeftCornerArc(graphics2D, shadowLength, cornerGradientImage);
      drawTopRightCornerArc(graphics2D, shadowLength, cornerGradientImage);

      graphics2D.dispose();
   }
}