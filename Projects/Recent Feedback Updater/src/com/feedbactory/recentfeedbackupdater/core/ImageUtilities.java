
package com.feedbactory.recentfeedbackupdater.core;


import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;


final class ImageUtilities
{
   static private BufferedImage handleGetScaledImage(final BufferedImage image, final int targetWidth, final int targetHeight, final Object interpolationHint)
   {
      // Will return the original image if the existing dimensions match the target dimensions.
      if ((image.getWidth() == targetWidth) && (image.getHeight() == targetHeight))
         return image;
      else if ((image.getWidth() < targetWidth) || (image.getHeight() < targetHeight))
         return getSimpleScaledImage(image, targetWidth, targetHeight, interpolationHint);

      int previousWidth;
      int previousHeight;
      int width;
      int height;

      if (image.getWidth() > targetWidth)
      {
         width = (image.getWidth() / 2);
         if (width < targetWidth)
            width = targetWidth;
      }
      else
         width = image.getWidth();

      if (image.getHeight() > targetHeight)
      {
         height = (image.getHeight() / 2);
         if (height < targetHeight)
            height = targetHeight;
      }
      else
         height = image.getHeight();

      /* Use a scratchpad image to progressively scale down the original image. The scratchpad image will be copied to itself at position 0, 0 at half size
       * until the target dimensions are reached. Note the Src composite used to ensure that there are no glitches produced by any partially alpha pixels
       * during the scaling; Src will ensure that the source rectangle in drawImage() will completely overwrite the destination.
       */
      BufferedImage scaledImage = new BufferedImage(width, height, image.getType());
      Graphics2D graphics2D = scaledImage.createGraphics();
      graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationHint);
      graphics2D.setComposite(AlphaComposite.Src);
      graphics2D.drawImage(image, 0, 0, width, height, null);

      while ((width != targetWidth) || (height != targetHeight))
      {
         previousWidth = width;
         previousHeight = height;

         if (width > targetWidth)
         {
            width /= 2;
            if (width < targetWidth)
               width = targetWidth;
         }

         if (height > targetHeight)
         {
            height /= 2;
            if (height < targetHeight)
               height = targetHeight;
         }

         graphics2D.drawImage(scaledImage, 0, 0, width, height, 0, 0, previousWidth, previousHeight, null);
      }

      graphics2D.dispose();

      if ((scaledImage.getWidth() == targetWidth) && (scaledImage.getHeight() == targetHeight))
         return scaledImage;
      else
      {
         // May entail a minor memory leak since the subimage returned is sharing the array of a larger image.
         return scaledImage.getSubimage(0, 0, targetWidth, targetHeight);
      }
   }


   static private BufferedImage handleGetSimpleScaledImage(final BufferedImage image, final int targetWidth, final int targetHeight, final Object interpolationHint)
   {
      final BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, image.getType());
      final Graphics2D graphics2D = scaledImage.createGraphics();
      graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationHint);
      graphics2D.drawImage(image, 0, 0, targetWidth, targetHeight, null);
      graphics2D.dispose();

      return scaledImage;
   }


   static private BufferedImage handleGetProportionalScaledImage(final BufferedImage image, final int maximumWidth, final int maximumHeight, final Object interpolationHint)
   {
      final float horizontalGrowthProportion = ((float) maximumWidth) / image.getWidth();
      final float verticalGrowthProportion = ((float) maximumHeight) / image.getHeight();

      final float limitingProportion = Math.min(horizontalGrowthProportion, verticalGrowthProportion);

      return getScaledImage(image, (int) (image.getWidth() * limitingProportion), (int) (image.getHeight() * limitingProportion), interpolationHint);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static BufferedImage getScaledImage(final BufferedImage image, final int targetWidth, final int targetHeight, final Object interpolationHint)
   {
      return handleGetScaledImage(image, targetWidth, targetHeight, interpolationHint);
   }


   static BufferedImage getSimpleScaledImage(final BufferedImage image, final int targetWidth, final int targetHeight, final Object interpolationHint)
   {
      return handleGetSimpleScaledImage(image, targetWidth, targetHeight, interpolationHint);
   }


   static BufferedImage getProportionalScaledImage(final BufferedImage image, final int maximumWidth, final int maximumHeight, final Object interpolationHint)
   {
      return handleGetProportionalScaledImage(image, maximumWidth, maximumHeight, interpolationHint);
   }
}