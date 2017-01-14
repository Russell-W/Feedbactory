
package com.feedbactory.client.ui;


import com.feedbactory.client.ui.component.MessageDialog.MessageType;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;


abstract public class UIUtilities
{
   static final private byte ConvertedSWTImageDepth = 24;
   static final private int ConvertedSWTImageRedMask = 0xff0000;
   static final private int ConvertedSWTImageGreenMask = 0xff00;
   static final private int ConvertedSWTImageBlueMask = 0xff;


   private UIUtilities()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private String handleGetElipsisTruncatedString(final String string, final int maximumLength)
   {
      if (string.length() <= maximumLength)
         return string;
      else
         return string.substring(0, maximumLength - 3) + "...";
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


   static private BufferedImage handleEnsureINTRGBInputImage(final BufferedImage inputImage)
   {
      final ColorModel colourModel = inputImage.getColorModel();

      if ((colourModel instanceof DirectColorModel) && (colourModel.getTransferType() == DataBuffer.TYPE_INT))
         return inputImage;
      else
      {
         final BufferedImage newImage = createIntegerTransferTypeImage(inputImage.getWidth(), inputImage.getHeight(), inputImage.getTransparency());

         final Graphics2D graphics2D = newImage.createGraphics();
         graphics2D.drawImage(inputImage, 0, 0, null);
         graphics2D.dispose();

         return newImage;
      }
   }


   static private BufferedImage createIntegerTransferTypeImage(final int imageWidth, final int imageHeight, final int imageTransparencyType)
   {
      final GraphicsConfiguration graphicsConfiguration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
      final ColorModel colourModel = graphicsConfiguration.getColorModel(imageTransparencyType);

      if ((colourModel instanceof DirectColorModel) && (colourModel.getTransferType() == DataBuffer.TYPE_INT))
         return graphicsConfiguration.createCompatibleImage(imageWidth, imageHeight, imageTransparencyType);
      else
         return new BufferedImage(imageWidth, imageHeight, ((imageTransparencyType == Transparency.TRANSLUCENT) || (imageTransparencyType == Transparency.BITMASK)) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
   }


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


   static private BufferedImage handleGetProportionalScaledImage(final BufferedImage image, final int maximumWidth, final int maximumHeight, final Object interpolationHint)
   {
      final float horizontalGrowthProportion = ((float) maximumWidth) / image.getWidth();
      final float verticalGrowthProportion = ((float) maximumHeight) / image.getHeight();

      final float limitingProportion = Math.min(horizontalGrowthProportion, verticalGrowthProportion);

      return getScaledImage(image, (int) (image.getWidth() * limitingProportion), (int) (image.getHeight() * limitingProportion), interpolationHint);
   }


   static private BufferedImage handleGetSquareCroppedScaledImage(final BufferedImage image, final int size, final Object hint)
   {
      // The original image will be returned if its size matches the target size.
      if ((image.getWidth() == size) && (image.getHeight() == size))
         return image;
      else if (image.getWidth() <= image.getHeight())
      {
         final float growthProportion = ((float) size) / image.getWidth();

         final int scaledHeight = (int) (image.getHeight() * growthProportion);
         final BufferedImage scaledImage = getScaledImage(image, size, scaledHeight, hint);
         final int startYPosition = (scaledHeight - size) / 2;
         return scaledImage.getSubimage(0, startYPosition, size, size);
      }
      else
      {
         final float growthProportion = ((float) size) / image.getHeight();

         final int scaledWidth = (int) (image.getWidth() * growthProportion);
         final BufferedImage scaledImage = getScaledImage(image, scaledWidth, size, hint);
         final int startXPosition = (scaledWidth - size) / 2;
         return scaledImage.getSubimage(startXPosition, 0, size, size);
      }
   }


   /* Implementation of Mario Klingemann's fast box blur.
    * 
    * If the output image is null, a new image is generated. It's permissable for the input image and output image to be the same - in the most miserly/efficient case,
    * this blur doesn't allocate any extra image data aside from temporary arrays for each scanline. Regardless of the combination of parameters, the eventual output image
    * is returned from the method.
    *
    * The input & output images must be alpha premultiplied otherwise the blur will produce dark edges around the crossovers between transparent and non-transparent pixels
    * where the zero red-green-blue (black) of the transparent pixels has been erroneously blended in with the non-transparent. The algorithm also operates on images having
    * an integer transfer type, so the combination of these requirements means that the image type must be BufferedImage.TYPE_INT_ARGB_PRE.
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
    * firstly a purely horizontal blur of the image, and then taking the result of that blur and feeding it into a purely vertical blur. So for the radius of one blur op,
    * the 3 x 3 matrix would now just be a 3 x 1 matrix; the sum of values above and below the current focus pixel would not be taken into account. And the formula for
    * the white pixel example above would now simply be: result = (255 + 255 + 255) / 3, again equal to the original value of 255.
    *
    * The other optimisation is that now that the focus is on tallying up pixels values within a 1D window that slides across each focus pixel, the code shouldn't have to
    * perform the same sums repeatedly when the overall sum is virtually identical as the iterator moves from one pixel to the next. For any sized blur window as it moves
    * across from left to right, the new sum will be equal to the old one, minus the pixel value that's just disappeared off the left edge of the blur window, plus the pixel
    * value that's now appeared on the right edge of the blur window. After the initial calculation of the blur window's sum for the current line, the algorithm can start
    * sliding across. It should be noted that the rolling sum maintained has not yet had the division applied - that can be the final step, and the result is the same.
    * ie. 255/3 + 255/3 + 255/3 is the same as (255 + 255 + 255) / 3.
    *
    * How to deal with the sums for the pixels near the edges of the image? This is where I need to take some liberties. The approach that I'm using here is to assume that
    * the pixels outside of the edge are identical to those on the very outer edges. So, for the radius of one blur, starting at the left edge of a new line of pixels,
    * the rolling sum would be initialised to: (1 * pixelValue[0]) + pixelValue[0] + pixelValue[1]. If I was applying a two radius blur, the sum would be initialised to:
    * (2 * pixelValue[0]) + pixelValue[0] + pixelValue[1] + pixelValue[2]. And so on. I need to be careful of the case where the blur radius exceeds the image width
    * or height, but the actual calculation remains the same, ie. a horizontal 'blur' of radius two on a one pixel wide white image would result in:
    * (2 * 255) + 255 + (2 * 255), eventually divided by 5, which again would be the same 255 result as the original pixel.
    *
    * A final basic optimisation is that the final division operation is replaced by a lookup into a precalculated table. For this scheme to work, a lookup array is allocated
    * which will use as index value all possible sum values for the current blur window size. So again for the two pixel blur radius, the maximum sum of terms would be for
    * five consecutive white pixels: 5 * 255. If I precalculate the division by five for every possible sum of five pixel values, it saves doing a more expensive
    * division op on the fly for every final pixel calculation. The division would need to be done for each colour channel separately, making it worse (and the lookup
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

         /* Begin a new line by initialising the sum of pixel values for each colour channel.
          * Start by initialising the 'left' half of the sliding blur window; for the very edge of the image I take the liberty of assuming that pixels outside the left edge
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
            /* In the case of the blur radius exceeding the width of the image, the sums above will not have included enough pixel values, so
             * as with the leftmost edge where I fabricate the sum of leading pixels, here I need to fill out the remainder of terms by fabricating
             * the sum of trailing pixels where they all are identical to the rightmost edge pixel. So I need to use the most recent pixelValue
             * which has been set above; for width == 1, it will have been inputImagePixels[0], otherwise it will have been inputImagePixels[windowIndex].
             */
            sumAlpha += (radiusExceedsImageWidthBy * ((pixelValue >> 24) & 0xff));
            sumRed += (radiusExceedsImageWidthBy * ((pixelValue >> 16) & 0xff));
            sumGreen += (radiusExceedsImageWidthBy * ((pixelValue >> 8) & 0xff));
            sumBlue += (radiusExceedsImageWidthBy * (pixelValue & 0xff));
         }

         for (int columnIndex = 0; columnIndex < width; columnIndex ++)
         {
            /* Set the blurred value for the focus pixel, using the precalculated division lookup table. If I didn't use the table, I'd have to manually
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


   static private org.eclipse.swt.graphics.ImageData handleSwingImageToSWTImageData(final BufferedImage inputImage)
   {
      final BufferedImage sourceImage = ensureINTRGBInputImage(inputImage);

      final PaletteData paletteData = new PaletteData(ConvertedSWTImageRedMask, ConvertedSWTImageGreenMask, ConvertedSWTImageBlueMask);
      final ImageData destinationImageData = new ImageData(sourceImage.getWidth(), sourceImage.getHeight(), ConvertedSWTImageDepth, paletteData);

      // Initialise pixel data pointers...
      int startOfRowDestinationImageDataIndex = 0;
      int destinationImageDataIndex;
      int startOfRowDestinationImageAlphaDataIndex = 0;
      int destinationImageAlphaDataIndex;

      /* The call to ensureINTRGBInputImage() prior to this method ensures that the input image is of type RGB or ARGB, having integer transfer type.
       * Furthermore, it will always be using a DirectColorModel. The docs for DirectColorModel state that the order of colour components for pixel operations
       * is always red, green, blue, then (if present) alpha, hence I can assume this order for array data returned from methods such as getComponents().
       */
      final ColorModel colourModel = sourceImage.getColorModel();
      final boolean hasAlpha = colourModel.hasAlpha();

      if (hasAlpha)
         destinationImageData.alphaData = new byte[sourceImage.getWidth() * sourceImage.getHeight()];

      final int[] sourceImageRowData = new int[sourceImage.getWidth() * sourceImage.getRaster().getNumDataElements()];
      int sourceImageRowDataIndex;
      int packedPixelValue;
      final int[] pixelComponents = new int[colourModel.getNumComponents()];

      for (int imageYPosition = 0; imageYPosition < sourceImage.getHeight(); imageYPosition ++)
      {
         sourceImage.getRaster().getDataElements(0, imageYPosition, sourceImage.getWidth(), 1, sourceImageRowData);

         destinationImageDataIndex = startOfRowDestinationImageDataIndex;
         destinationImageAlphaDataIndex = startOfRowDestinationImageAlphaDataIndex;

         for (sourceImageRowDataIndex = 0; sourceImageRowDataIndex < sourceImageRowData.length; sourceImageRowDataIndex ++)
         {
            packedPixelValue = sourceImageRowData[sourceImageRowDataIndex];
            colourModel.getComponents(packedPixelValue, pixelComponents, 0);

            destinationImageData.data[destinationImageDataIndex ++] = (byte) pixelComponents[0];
            destinationImageData.data[destinationImageDataIndex ++] = (byte) pixelComponents[1];
            destinationImageData.data[destinationImageDataIndex ++] = (byte) pixelComponents[2];

            if (hasAlpha)
               destinationImageData.alphaData[destinationImageAlphaDataIndex ++] = (byte) pixelComponents[3];
         }

         // Jump the image indices to the start of the next row.
         startOfRowDestinationImageDataIndex += destinationImageData.bytesPerLine;
         startOfRowDestinationImageAlphaDataIndex += destinationImageData.width;
      }

      return destinationImageData;
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


   static private String handleGetPreferencesLocationString(final Point location)
   {
      final StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append(location.x).append(',').append(location.y);

      return stringBuilder.toString();
   }


   static private Point handleParsePreferencesLocationString(final String commaDelimitedBounds) throws NumberFormatException
   {
      final String[] locationElements = commaDelimitedBounds.split(",");

      if (locationElements.length == 2)
         return new Point(Integer.parseInt(locationElements[0]), Integer.parseInt(locationElements[1]));
      else
         throw new IllegalArgumentException("Invalid location string");
   }


   static private String handleGetPreferencesBoundsString(final org.eclipse.swt.graphics.Rectangle bounds)
   {
      final StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append(bounds.x).append(',').append(bounds.y).append(',');
      stringBuilder.append(bounds.width).append(',').append(bounds.height);

      return stringBuilder.toString();
   }


   static private org.eclipse.swt.graphics.Rectangle handleParsePreferencesBoundsString(final String commaDelimitedBounds) throws NumberFormatException
   {
      final String[] boundsElements = commaDelimitedBounds.split(",");

      if (boundsElements.length == 4)
         return new org.eclipse.swt.graphics.Rectangle(Integer.parseInt(boundsElements[0]), Integer.parseInt(boundsElements[1]), Integer.parseInt(boundsElements[2]), Integer.parseInt(boundsElements[3]));
      else
         throw new IllegalArgumentException("Invalid bounds string");
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   static private void handlePlaySound(final String soundLocation) throws UnsupportedAudioFileException, IOException, LineUnavailableException
   {
      final BufferedInputStream bufferedAudioStream = new BufferedInputStream(UIUtilities.class.getResourceAsStream(soundLocation));
      final AudioInputStream sound = AudioSystem.getAudioInputStream(bufferedAudioStream);
      final DataLine.Info info = new DataLine.Info(Clip.class, sound.getFormat());
      final Clip clip = (Clip) AudioSystem.getLine(info);

      clip.addLineListener(new LineListener()
      {
         @Override
         final public void update(final LineEvent event)
         {
            if (event.getType() == LineEvent.Type.STOP)
            {
               clip.drain();
               clip.close();
            }
         }
      });

      clip.open(sound);
      clip.start();
   }


   static private void handlePlayBackgroundSound(final String soundLocation)
   {
      final ExecutorService executorService = Executors.newSingleThreadExecutor();

      executorService.execute(new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               playSound(soundLocation);
            }
            catch (final Exception anyException)
            {
               // Log a warning in development profile, otherwise take no further action.
               Logger.getLogger(getClass().getName()).log(Level.WARNING, "Sound playback exception.", anyException);
            }
         }
      });

      executorService.shutdown();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private MessageType handleNetworkMessageTypeToDialogMessageType(final com.feedbactory.shared.MessageType networkMessageType)
   {
      switch (networkMessageType)
      {
         case InformationMessage:
            return MessageType.Information;

         case WarningMessage:
            return MessageType.Warning;

         case ErrorMessage:
            return MessageType.Error;

         default:
            return MessageType.Plain;
      }
   }


   static private int handleDialogMessageTypeToJOptionPaneMessageType(final MessageType messageType)
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


   static public String getEllipsisTruncatedString(final String string, final int maximumLength)
   {
      return handleGetElipsisTruncatedString(string, maximumLength);
   }


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


   static public BufferedImage ensureINTRGBInputImage(final BufferedImage inputImage)
   {
      return handleEnsureINTRGBInputImage(inputImage);
   }


   static public BufferedImage getScaledImage(final BufferedImage image, final int targetWidth, final int targetHeight, final Object interpolationHint)
   {
      return handleGetScaledImage(image, targetWidth, targetHeight, interpolationHint);
   }


   static public BufferedImage getSimpleScaledImage(final BufferedImage image, final int targetWidth, final int targetHeight, final Object interpolationHint)
   {
      return handleGetSimpleScaledImage(image, targetWidth, targetHeight, interpolationHint);
   }


   static public BufferedImage getProportionalScaledImage(final BufferedImage image, final int maximumWidth, final int maximumHeight, final Object interpolationHint)
   {
      return handleGetProportionalScaledImage(image, maximumWidth, maximumHeight, interpolationHint);
   }


   static public BufferedImage getSquareCroppedScaledImage(final BufferedImage image, final int size, final Object hint)
   {
      return handleGetSquareCroppedScaledImage(image, size, hint);
   }


   static public void copyImageChunk(final BufferedImage sourceImage, final int sourceX, final int sourceY, final int regionWidth, final int regionHeight,
                                     final BufferedImage destinationImage, final int destinationX, final int destinationY)
   {
      destinationImage.getRaster().setDataElements(destinationX, destinationY, sourceImage.getRaster().getDataElements(sourceY, sourceY, regionWidth, regionHeight, null));
   }


   static public BufferedImage boxBlurImage(final BufferedImage inputImage, final BufferedImage outputImage, final int blurRadius, final int blurIterations)
   {
      return handleBoxBlurImage(inputImage, outputImage, blurRadius, blurIterations);
   }


   static public org.eclipse.swt.graphics.ImageData swingImageToSWTImageData(final BufferedImage inputImage)
   {
      return handleSwingImageToSWTImageData(inputImage);
   }


   static public org.eclipse.swt.graphics.Rectangle swingRectangleToSWTRectangle(final Rectangle swingRectangle)
   {
      return new org.eclipse.swt.graphics.Rectangle(swingRectangle.x, swingRectangle.y, swingRectangle.width, swingRectangle.height);
   }


   static public Point getFrameCentredPosition(final JFrame frame)
   {
      return handleGetFrameCentredPosition(frame);
   }


   static public String getPreferencesLocationString(final Point location)
   {
      return handleGetPreferencesLocationString(location);
   }


   static public Point parsePreferencesLocationString(final String commaDelimitedBounds) throws NumberFormatException
   {
      return handleParsePreferencesLocationString(commaDelimitedBounds);
   }


   static public String getPreferencesBoundsString(final org.eclipse.swt.graphics.Rectangle bounds)
   {
      return handleGetPreferencesBoundsString(bounds);
   }


   static public org.eclipse.swt.graphics.Rectangle parsePreferencesBoundsString(final String commaDelimitedBounds) throws NumberFormatException
   {
      return handleParsePreferencesBoundsString(commaDelimitedBounds);
   }


   static public void playSound(final String soundLocation) throws UnsupportedAudioFileException, IOException, LineUnavailableException
   {
      handlePlaySound(soundLocation);
   }


   static public void playBackgroundSound(final String soundLocation)
   {
      handlePlayBackgroundSound(soundLocation);
   }


   static public MessageType networkMessageTypeToDialogMessageType(final com.feedbactory.shared.MessageType networkMessageType)
   {
      return handleNetworkMessageTypeToDialogMessageType(networkMessageType);
   }


   static public int dialogMessageTypeToJOptionPaneMessageType(final MessageType messageType)
   {
      return handleDialogMessageTypeToJOptionPaneMessageType(messageType);
   }
}