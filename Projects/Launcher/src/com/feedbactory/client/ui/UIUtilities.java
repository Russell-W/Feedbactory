
package com.feedbactory.client.ui;


import com.feedbactory.client.ui.component.MessageDialog;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.JOptionPane;


abstract public class UIUtilities
{
   private UIUtilities()
   {
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   static private Color handleShiftColourBrightness(final Color baseColour, final float brightnessShift)
   {
      final float[] baseColourHSB = Color.RGBtoHSB(baseColour.getRed(), baseColour.getGreen(), baseColour.getBlue(), null);

      // HSB: the third element is brightness.
      baseColourHSB[2] += brightnessShift;

      if (baseColourHSB[2] < 0f)
         baseColourHSB[2] = 0f;
      else if (baseColourHSB[2] > 1f)
         baseColourHSB[2] = 1f;

      final Color hsbColour = Color.getHSBColor(baseColourHSB[0], baseColourHSB[1], baseColourHSB[2]);

      if (baseColour.getAlpha() == 255)
         return hsbColour;
      else
         return new Color(hsbColour.getRed(), hsbColour.getGreen(), hsbColour.getBlue(), baseColour.getAlpha());
   }


   static private int handleGetShiftForColourMask(final int mask)
   {
      for (int shift = 31; shift >= 0; shift --)
      {
         if (((mask >> shift) & 0x1) != 0)
            return shift - 7;
      }

      return 32;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


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
      BufferedImage scaledImage = createCompatibleImage(width, height, image.getTransparency());
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
      final BufferedImage scaledImage = createCompatibleImage(targetWidth, targetHeight, image.getTransparency());
      final Graphics2D graphics2D = scaledImage.createGraphics();
      graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationHint);
      graphics2D.drawImage(image, 0, 0, targetWidth, targetHeight, null);
      graphics2D.dispose();

      return scaledImage;
   }


   static private BufferedImage handleGetProportionalScaledImage(final BufferedImage image, final int maximumWidth, final int maximumHeight, final Object hint)
   {
      final float horizontalGrowthProportion = ((float) maximumWidth) / image.getWidth();
      final float verticalGrowthProportion = ((float) maximumHeight) / image.getHeight();

      final float limitingProportion = Math.min(horizontalGrowthProportion, verticalGrowthProportion);

      return getScaledImage(image, (int) (image.getWidth() * limitingProportion), (int) (image.getHeight() * limitingProportion), hint);
   }


   /* Implementation of Mario Klingemann's fast box blur.
    * 
    * If the output image is null, a new image is generated. It's permissable for the input image and output image to be the same - in the most miserly case, this blur doesn't
    * allocate any extra image data aside from temporary arrays for each scanline. Regardless of the combination of parameters, the eventual output image is returned from the
    * method.
    *
    * The input & output images must be alpha premultiplied otherwise the blur will produce dark edges around the crossovers between transparent and non-transparent pixels
    * where the zero red-green-blue (black) of the transparent pixels has been erroneously blended in with the non-transparent. Our algorithm also operates on images having
    * an integer transfer type, so the combination of these requirements means that we must use the BufferedImage.TYPE_INT_ARGB_PRE image type.
    *
    * The gist of the algorithm itself is fairly straightforward although the bit-shifting and pre-calculations here make it look a little more complex than it is.
    * A blur op basically involves each pixel (each red, green, blue, and alpha component of each pixel) being replaced by a sum of terms based on a simple calculation
    * of neighbouring pixels. For example a blur of radius one uses a window size, or matrix size, of 3 x 3, where the pixel data to be replaced - the focus pixel - is at
    * the centre of the matrix. The formula is very simple: the new blurred pixel value is set to the sum of pixel values surrounding the focus pixel, divided by the total
    * number of pixels within the blur radius including the focus pixel itself. So for the 3 x 3 matrix, the sum is divided by 9. If for example all of the pixels were white,
    * the sum for any of the red, green, blue, or alpha channels would be: (255 + 255 + 255 + .... + 255) / 9, which would resolve to the original focus pixel's value of 255.
    *
    * It would be extremely taxing to iterate over every focus pixel and recalculate the entire sum of terms for each. Thankfully there are a couple of properties of box
    * blurring which allow for the optimisation of the algorithm. It turns out that the same output can be achieved by performing two compound blurs one after the other:
    * firstly a purely horizontal blur of the image, and then taking the result of that blur and feeding it into a purely vertical blur. So for our radius of one blur op,
    * the 3 x 3 matrix would now just be a 3 x 1 matrix; the sum of values above and below the current focus pixel would not be taken into account. And the formula for
    * the white pixel example above would now simply be: result = (255 + 255 + 255) / 3, again equal to the original value of 255.
    *
    * The other optimisation is that now that we're dealing with tallying up pixels values within a 1D window that slides across each focus pixel, we shouldn't have to
    * perform the same sums repeatedly when the overall sum is virtually identical as the iterator moves from one pixel to the next. For any sized blur window as it moves
    * across from left to right, the new sum will be equal to the old one, minus the pixel value that's just disappeared off the left edge of the blur window, plus the pixel
    * value that's now appeared on the right edge of the blur window. Once we initially calculate the blur window's sum for the current line, we can start rolling forward.
    * It should be noted that the rolling sum maintained has not yet had the division applied - that can be the final step, and the result is the same. ie. 255/3 + 255/3 + 255/3
    * is the same as (255 + 255 + 255) / 3.
    *
    * How to deal with the sums for the pixels near the edges of the image? This is where we need to take some liberties. The approach that we use here is to assume that
    * the pixels outside of the edge are identical to those on the very outer edges. So, for our radius of one blur, starting at the left edge of a new line of pixels,
    * our rolling sum would be initialised to: (1 * pixelValue[0]) + pixelValue[0] + pixelValue[1]. If we were applying a two radius blur, the sum would be initialised to:
    * (2 * pixelValue[0]) + pixelValue[0] + pixelValue[1] + pixelValue[2]. And so on. We need to be careful of the case where the blur radius exceeds the image width
    * or height, but the actual calculation remains the same, ie. a horizontal 'blur' of radius two on a one pixel wide white image would result in:
    * (2 * 255) + 255 + (2 * 255), eventually divided by 5, which again would be the same 255 result as the original pixel.
    *
    * A final basic optimisation is that the final division operation is replaced by a lookup into a precalculated table. For this scheme to work, a lookup array is allocated
    * which will use as index value all possible sum values for the current blur window size. So again for the two pixel blur radius, the maximum sum of terms would be for
    * five consecutive white pixels: 5 * 255. If we precalculate the division by five for every possible sum of five pixel values, it saves us from doing a more expensive
    * division op on the fly for every final pixel calculation. The division would need to be done for each colour channel separately, making it worse (and our lookup
    * method an even better alternative).
    *
    * The number of blur iterations parameter is used to provide a more pronounced (and less square-ish) blur effect using smaller radii. It turns out that given a
    * sequence of enough box blurs on an image, the resulting output will approach the same as that applied to the original input image by a more sophisticated Gaussian blur.
    *
    * One more thing on the implementation here: I'm not sure how efficient the inputImage.getRaster().getDataElements() and inputImage.getRaster().setDataElements()
    * methods are when operating in the vertical direction, one pixel wide at a time. My gut feeling is that a call to fill an array using something like
    * image.getRaster().getDataElements(columnIndex, 0, 1, height, inputImagePixels) would involve a lot more jumping around and potentially be a lot more slow than
    * the equivalent call on the horizontal scanline where the pixels are likely already lined up in sequence as they need to appear in the output array.
    */
   static private BufferedImage handleBoxBlurImage(final BufferedImage inputImage, final BufferedImage outputImage, final int blurRadius, final int blurIterations)
   {
      if (blurRadius <= 0)
         throw new IllegalArgumentException("Blur radius must be greater than zero.");
      else if (blurIterations <= 0)
         throw new IllegalArgumentException("Number of blur iterations must be greater than zero.");
      else if (inputImage.getType() != BufferedImage.TYPE_INT_ARGB_PRE)
         throw new IllegalArgumentException("The input image must be of type TYPE_INT_ARGB_PRE.");
      else if ((outputImage != null) && (outputImage.getType() != BufferedImage.TYPE_INT_ARGB_PRE))
         throw new IllegalArgumentException("The output image must be of type TYPE_INT_ARGB_PRE.");

      final BufferedImage blurCompatibleOutputImage;

      if (outputImage == null)
         blurCompatibleOutputImage = new BufferedImage(inputImage.getWidth(), inputImage.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
      else
         blurCompatibleOutputImage = outputImage;

      final int windowSize = (blurRadius * 2) + 1;

      // Precalculate all possible divisions of the sum terms, for a sliding blur window of this size.
      final int[] divisionTermLookupTable = new int[256 * windowSize];

      for (int divisionTermIndex = 0; divisionTermIndex < divisionTermLookupTable.length; divisionTermIndex ++)
         divisionTermLookupTable[divisionTermIndex] = divisionTermIndex / windowSize;

      /* Perform the initial blur, pushing the data from the input image to the output image, which thereafter
       * becomes the new input image itself.
       */
      boxBlurImageHorizontalHelper(inputImage, blurCompatibleOutputImage, blurRadius, divisionTermLookupTable);
      boxBlurImageVerticalHelper(blurCompatibleOutputImage, blurRadius, divisionTermLookupTable);

      for (int blurIteration = 1; blurIteration < blurIterations; blurIteration ++)
      {
         boxBlurImageHorizontalHelper(blurCompatibleOutputImage, blurCompatibleOutputImage, blurRadius, divisionTermLookupTable);
         boxBlurImageVerticalHelper(blurCompatibleOutputImage, blurRadius, divisionTermLookupTable);
      }

      return blurCompatibleOutputImage;
   }


   static private void boxBlurImageHorizontalHelper(final BufferedImage inputImage, final BufferedImage outputImage, final int blurRadius, final int[] divisionTermLookupTable)
   {
      final int width = inputImage.getWidth();
      final int height = inputImage.getHeight();
      final int radiusPlusOne = blurRadius + 1;
      final int radiusExceedsImageWidthBy = (radiusPlusOne - width);

      int sumAlpha;
      int sumRed;
      int sumGreen;
      int sumBlue;

      final int[] inputImagePixels = new int[width];
      final int[] outputImagePixels = new int[width];
      int pixelValue;

      int windowIndexLimit;

      int nextPixelIndex;
      int previousPixelIndex;
      int nextPixel;
      int previousPixel;

      for (int rowIndex = 0; rowIndex < height; rowIndex ++)
      {
         // Grab the next scanline of pixel values.
         inputImage.getRaster().getDataElements(0, rowIndex, width, 1, inputImagePixels);

         /* Begin a new line by initialising our sum of pixel values for each colour channel.
          * Start by initialising the 'left' half of the sliding blur window; for the very edge of the image we take the liberty of assuming that pixels outside the left edge
          * are set to the same value as the very leftmost pixel. Hence the sum value for that pixel is its individual colour component values multiplied by
          * radius (the number of imaginary pixels to the left having the same colour value), plus one more to include the leftmost pixel itself.
          */
         pixelValue = inputImagePixels[0];
         sumAlpha = radiusPlusOne * ((pixelValue >> 24) & 0xff);
         sumRed = radiusPlusOne * ((pixelValue >> 16) & 0xff);
         sumGreen = radiusPlusOne * ((pixelValue >> 8) & 0xff);
         sumBlue = radiusPlusOne * (pixelValue & 0xff);

         /* Initialise pixel sums for the second (right) half of the sliding window, being careful not to step outside of the image bounds if the
          * blur radius is large enough.
          * The sums from above are simply added to with the sum of pixel values trailing to the right of the leftmost pixel, until the edge of
          * the blur radius (or image, whichever comes first) is encountered.
          */
         windowIndexLimit = Math.min(width, radiusPlusOne);

         for (int windowIndex = 1; windowIndex < windowIndexLimit; windowIndex ++)
         {
            pixelValue = inputImagePixels[windowIndex];

            sumAlpha += (pixelValue >> 24) & 0xff;
            sumRed += (pixelValue >> 16) & 0xff;
            sumGreen += (pixelValue >> 8) & 0xff;
            sumBlue += pixelValue & 0xff;
         }

         if (radiusExceedsImageWidthBy > 0)
         {
            /* In the case of the blur radius exceeding the width of the image, our sums above will not have included enough pixel values, so
             * as with the leftmost edge where we fabricate the sum of leading pixels, here we fill out the remainder of terms by fabricating
             * the sum of trailing pixels where they all are identical to the rightmost edge pixel. So we need to use the most recent pixelValue
             * which has been set above; for width == 1, it will have been inputImagePixels[0], otherwise it will have been inputImagePixels[windowIndex].
             */
            sumAlpha += (radiusExceedsImageWidthBy * ((pixelValue >> 24) & 0xff));
            sumRed += (radiusExceedsImageWidthBy * ((pixelValue >> 16) & 0xff));
            sumGreen += (radiusExceedsImageWidthBy * ((pixelValue >> 8) & 0xff));
            sumBlue += (radiusExceedsImageWidthBy * (pixelValue & 0xff));
         }

         for (int columnIndex = 0; columnIndex < width; columnIndex ++)
         {
            /* Set the blurred value for the focus pixel, using the precalculated division lookup table. If we didn't use the table, we would have to manually
             * divide each colour component by the number of terms in the sum: always equal to windowSize == (blurRadius * 2) + 1.
             */
            outputImagePixels[columnIndex] = (divisionTermLookupTable[sumAlpha] << 24) |
                                             (divisionTermLookupTable[sumRed] << 16) |
                                             (divisionTermLookupTable[sumGreen] << 8) |
                                              divisionTermLookupTable[sumBlue];

            // Progress the rightmost edge of the blur window
            nextPixelIndex = columnIndex + radiusPlusOne;
            if (nextPixelIndex >= width)
               nextPixelIndex = width - 1;

            // Progress the leftmost edge of the blur window.
            previousPixelIndex = columnIndex - blurRadius;
            if (previousPixelIndex < 0)
               previousPixelIndex = 0;

            /* The new pixel value that has appeared at the rightmost window edge. Or, if the window has already reached it, the pixel which is sitting at the rightmost
             * edge of the image. The colour component values of this need to be added to the rolling sum.
             */
            nextPixel = inputImagePixels[nextPixelIndex];

            /* The pixel value that has just disappeared from the left edge of the blur window. Or, if the left edge of the window hasn't yet reached it, the
             * image's leftmost pixel value. The colour component values of this need to be subtracted from the rolling sum.
             */
            previousPixel = inputImagePixels[previousPixelIndex];

            sumAlpha += ((nextPixel >> 24) & 0xff);
            sumAlpha -= ((previousPixel >> 24) & 0xff);

            sumRed += ((nextPixel >> 16) & 0xff);
            sumRed -= ((previousPixel >> 16) & 0xff);

            sumGreen += ((nextPixel >> 8) & 0xff);
            sumGreen -= ((previousPixel >> 8) & 0xff);

            sumBlue += (nextPixel & 0xff);
            sumBlue -= (previousPixel & 0xff);
         }

         // Replace the scanline of pixel values with the blurred output.
         outputImage.getRaster().setDataElements(0, rowIndex, width, 1, outputImagePixels);
      }
   }


   static private void boxBlurImageVerticalHelper(final BufferedImage image, final int blurRadius, final int[] divisionTermLookupTable)
   {
      final int width = image.getWidth();
      final int height = image.getHeight();
      final int radiusPlusOne = blurRadius + 1;
      final int radiusExceedsImageHeightBy = (radiusPlusOne - height);

      int sumAlpha;
      int sumRed;
      int sumGreen;
      int sumBlue;

      final int[] inputImagePixels = new int[height];
      final int[] outputImagePixels = new int[height];

      int pixelValue;

      int windowIndexLimit;

      int nextPixelIndex;
      int previousPixelIndex;
      int nextPixel;
      int previousPixel;

      for (int columnIndex = 0; columnIndex < width; columnIndex++)
      {
         image.getRaster().getDataElements(columnIndex, 0, 1, height, inputImagePixels);

         pixelValue = inputImagePixels[0];
         sumAlpha = radiusPlusOne * ((pixelValue >> 24) & 0xff);
         sumRed = radiusPlusOne * ((pixelValue >> 16) & 0xff);
         sumGreen = radiusPlusOne * ((pixelValue >> 8) & 0xff);
         sumBlue = radiusPlusOne * (pixelValue & 0xff);

         windowIndexLimit = Math.min(height, radiusPlusOne);

         for (int windowIndex = 1; windowIndex < windowIndexLimit; windowIndex ++)
         {
            pixelValue = inputImagePixels[windowIndex];

            sumAlpha += (pixelValue >> 24) & 0xff;
            sumRed += (pixelValue >> 16) & 0xff;
            sumGreen += (pixelValue >> 8) & 0xff;
            sumBlue += pixelValue & 0xff;
         }

         if (radiusExceedsImageHeightBy > 0)
         {
            sumAlpha += (radiusExceedsImageHeightBy * ((pixelValue >> 24) & 0xff));
            sumRed += (radiusExceedsImageHeightBy * ((pixelValue >> 16) & 0xff));
            sumGreen += (radiusExceedsImageHeightBy * ((pixelValue >> 8) & 0xff));
            sumBlue += (radiusExceedsImageHeightBy * (pixelValue & 0xff));
         }

         for (int rowIndex = 0; rowIndex < height; rowIndex++)
         {
            outputImagePixels[rowIndex] = (divisionTermLookupTable[sumAlpha] << 24) |
                                          (divisionTermLookupTable[sumRed] << 16) |
                                          (divisionTermLookupTable[sumGreen] << 8) |
                                           divisionTermLookupTable[sumBlue];

            nextPixelIndex = rowIndex + radiusPlusOne;
            if (nextPixelIndex >= height)
               nextPixelIndex = height - 1;

            previousPixelIndex = rowIndex - blurRadius;
            if (previousPixelIndex < 0)
               previousPixelIndex = 0;

            nextPixel = inputImagePixels[nextPixelIndex];
            previousPixel = inputImagePixels[previousPixelIndex];

            sumAlpha += ((nextPixel >> 24) & 0xff);
            sumAlpha -= ((previousPixel >> 24) & 0xff);

            sumRed += ((nextPixel >> 16) & 0xff);
            sumRed -= ((previousPixel >> 16) & 0xff);

            sumGreen += ((nextPixel >> 8) & 0xff);
            sumGreen -= ((previousPixel >> 8) & 0xff);

            sumBlue += (nextPixel & 0xff);
            sumBlue -= (previousPixel & 0xff);
         }

         image.getRaster().setDataElements(columnIndex, 0, 1, height, outputImagePixels);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private Point handleGetFrameCentredPosition(final JFrame frame)
   {
      /* Return the frame's centre position for its attached graphics configuration: typically but not necessarily the primary monitor display.
       * After some reading I'm not confident that this is the same as centreing a frame using frame.setLocationRelativeTo(null),
       * which uses the GraphicsEnvironment's getCenterPosition(), which uses getMaximumWindowBounds(), which according to the docs will
       * return the entire display area on multi-screen systems where the windows "should (???) be centred across all displays".
       *
       * Also, note that this calculation will be slightly off centre for SwingNimbusFrame's due to the shadow.
       */
      final Rectangle screenBounds = frame.getGraphicsConfiguration().getBounds();
      final Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(frame.getGraphicsConfiguration());

      // Remember, screen bounds may have negative origins in multi-monitor configurations.
      screenBounds.x += screenInsets.left;
      screenBounds.y += screenInsets.top;
      screenBounds.width -= (screenInsets.left + screenInsets.right);
      screenBounds.height -= (screenInsets.top + screenInsets.bottom);

      final int centreX = screenBounds.x + ((screenBounds.width - frame.getWidth()) / 2);
      final int centreY = screenBounds.y + ((screenBounds.height - frame.getHeight()) / 2);

      return new Point(centreX, centreY);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   static private int handleDialogMessageTypeToJOptionPaneMessageType(final MessageDialog.MessageType messageType)
   {
      switch (messageType)
      {
         case Information:
            return JOptionPane.INFORMATION_MESSAGE;

         case Warning:
            return JOptionPane.WARNING_MESSAGE;

         case Error:
            return JOptionPane.ERROR_MESSAGE;

         case Question:
            return JOptionPane.QUESTION_MESSAGE;

         case Plain:
         default:
            return JOptionPane.PLAIN_MESSAGE;
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public Color shiftColourBrightness(final Color baseColour, final float brightnessShift)
   {
      return handleShiftColourBrightness(baseColour, brightnessShift);
   }


   static public int getShiftForColourMask(final int mask)
   {
      return handleGetShiftForColourMask(mask);
   }


   static public BufferedImage createCompatibleImage(final int imageWidth, final int imageHeight, final int transparencyType)
   {
      return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(imageWidth, imageHeight, transparencyType);
   }


   static public BufferedImage getScaledImage(final BufferedImage image, final int targetWidth, final int targetHeight, final Object interpolationHint)
   {
      return handleGetScaledImage(image, targetWidth, targetHeight, interpolationHint);
   }


   static public BufferedImage getSimpleScaledImage(final BufferedImage image, final int targetWidth, final int targetHeight, final Object interpolationHint)
   {
      return handleGetSimpleScaledImage(image, targetWidth, targetHeight, interpolationHint);
   }


   static public BufferedImage getProportionalScaledImage(final BufferedImage image, final int maximumWidth, final int maximumHeight, final Object hint)
   {
      return handleGetProportionalScaledImage(image, maximumWidth, maximumHeight, hint);
   }


   static public BufferedImage boxBlurImage(final BufferedImage inputImage, final BufferedImage outputImage, final int blurRadius, final int blurIterations)
   {
      return handleBoxBlurImage(inputImage, outputImage, blurRadius, blurIterations);
   }


   static public Point getFrameCentredPosition(final JFrame frame)
   {
      return handleGetFrameCentredPosition(frame);
   }


   static public int dialogMessageTypeToJOptionPaneMessageType(final MessageDialog.MessageType messageType)
   {
      return handleDialogMessageTypeToJOptionPaneMessageType(messageType);
   }
}