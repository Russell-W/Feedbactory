package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.UIConstants;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import javax.swing.GroupLayout;
import javax.swing.JComponent;


final public class ShadowedComponent extends JComponent
{
   final private JComponent content;
   final private int shadowLength;
   final private int maximumShadowTransparency;


   public ShadowedComponent(final JComponent content)
   {
      this(content, UIConstants.ComponentShadowLength, UIConstants.ComponentMaximumShadowTransparency);
   }


   public ShadowedComponent(final JComponent content, final int shadowLength, final int maximumShadowTransparency)
   {
      this.content = content;
      this.shadowLength = shadowLength;
      this.maximumShadowTransparency = maximumShadowTransparency;

      initialise();
   }


   private void initialise()
   {
      final GroupLayout panelLayout = new GroupLayout(this);
      setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addComponent(content)
         .addGap(shadowLength)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addComponent(content)
         .addGap(shadowLength)
      );
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private BufferedImage createHorizontalGradientImage()
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


   private void drawHorizontalShadow(final Graphics2D graphics2D, final BufferedImage gradientImage)
   {
      final int gradientStartX = shadowLength * 2;
      final int gradientWidth = content.getWidth() - gradientStartX;
      int gradientY = content.getHeight();

      graphics2D.drawImage(gradientImage, gradientStartX, gradientY, gradientWidth, shadowLength, null);
   }


   private BufferedImage createVerticalGradientImage()
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


   private void drawVerticalShadow(final Graphics2D graphics2D, final BufferedImage gradientImage)
   {
      final int gradientStartY = shadowLength * 2;
      final int gradientHeight = content.getHeight() - gradientStartY;
      int gradientX = content.getWidth();

      graphics2D.drawImage(gradientImage, gradientX, gradientStartY, shadowLength, gradientHeight, null);
   }


   private BufferedImage createCornerGradientImage()
   {
      final BufferedImage image = UIUtilities.createCompatibleImage(shadowLength, shadowLength, Transparency.TRANSLUCENT);
      final Graphics2D graphics2D = image.createGraphics();

      final float transparencyShift = maximumShadowTransparency / (float) shadowLength;
      float transparency = 0f;
      Color paintColour = new Color(0, 0, 0, 0);

      for (int lineNumber = shadowLength - 1; lineNumber > 0; lineNumber --)
      {
         graphics2D.setColor(paintColour);

         graphics2D.drawLine(0, lineNumber, lineNumber, lineNumber);
         graphics2D.drawLine(lineNumber, 0, lineNumber, lineNumber - 1);

         transparency += transparencyShift;
         paintColour = new Color(0, 0, 0, (int) (transparency + 0.5f));
      }

      image.setRGB(0, 0, paintColour.getRGB());

      graphics2D.dispose();

      return image;
   }


   private void drawBottomRightCorner(final Graphics2D graphics2D, final BufferedImage image)
   {
      graphics2D.drawImage(image, content.getWidth(), content.getHeight(), null);
   }


   private void drawBottomLeftCorner(final Graphics2D graphics2D, final BufferedImage image)
   {
      final int outputStartX = shadowLength * 2;
      final int outputStartY = content.getHeight();
      final int outputEndX = outputStartX - (image.getWidth() - 1);
      final int outputEndY = outputStartY + (image.getHeight() - 1);

      graphics2D.drawImage(image, outputStartX, outputStartY, outputEndX, outputEndY, 0, 0, image.getWidth() - 1, image.getHeight() - 1, null);
   }


   private void drawTopRightCorner(final Graphics2D graphics2D, final BufferedImage image)
   {
      final int outputStartX = content.getWidth();
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
      final BufferedImage horizontalGradientImage = createHorizontalGradientImage();
      final BufferedImage verticalGradientImage = createVerticalGradientImage();
      final BufferedImage cornerGradientImage = createCornerGradientImage();

      final Graphics2D graphics2D = (Graphics2D) graphics.create();

      drawHorizontalShadow(graphics2D, horizontalGradientImage);

      drawVerticalShadow(graphics2D, verticalGradientImage);

      drawBottomRightCorner(graphics2D, cornerGradientImage);
      drawBottomLeftCorner(graphics2D, cornerGradientImage);
      drawTopRightCorner(graphics2D, cornerGradientImage);

      graphics2D.dispose();
   }
}