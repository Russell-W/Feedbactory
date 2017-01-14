/* ASSUMPTION: The calling methods perform the necessary locking. This is preferable to enforcing the locking here since from our end there is no way to protect the
 * output image data when we return it to a caller, aside from cloning the output array (which we'd like to avoid, from a performance and memory standpoint).
 */

package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIUtilities;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import javax.swing.SwingUtilities;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;


final public class SWTImageRippler
{
   static final private int RippleHeight = 8192;
   static final private int RippleHeightShift = (int) (Math.log(RippleHeight) / Math.log(2));

   static final private byte SWTImageDepth = 24;
   static final private int SWTRedMask = 0xff0000;
   static final private int SWTGreenMask = 0xff00;
   static final private int SWTBlueMask = 0xff;

   static
   {
      assert (1 << RippleHeightShift) == RippleHeight : "Invalid ripple height.";
   }

   final private int rippleSize;
   final private int rippleDepth;
   final private int rippleFadeFactor;

   final private int imageWidth;
   final private int imageHeight;

   final private boolean hasAlpha;

   final private int halfImageWidth;
   final private int halfImageHeight;

   final private ImageData inputImageData;

   private int[] currentHeightMapBuffer;
   private int[] previousHeightMapBuffer;

   final private ImageData outputImageData;

   private boolean isActive;


   public SWTImageRippler(final int imageWidth, final int imageHeight, final boolean hasAlpha, final int rippleSize, final int rippleDepth, final int rippleDuration)
   {
      this.imageWidth = imageWidth;
      this.imageHeight = imageHeight;

      this.rippleSize = rippleSize;
      this.rippleDepth = rippleDepth;
      this.rippleFadeFactor = rippleDuration;

      halfImageWidth = imageWidth / 2;
      halfImageHeight = imageHeight / 2;

      final PaletteData paletteData = new PaletteData(SWTRedMask, SWTGreenMask, SWTBlueMask);
      inputImageData = new ImageData(imageWidth, imageHeight, SWTImageDepth, paletteData);
      outputImageData = new ImageData(imageWidth, imageHeight, SWTImageDepth, paletteData);

      this.hasAlpha = hasAlpha;

      if (hasAlpha)
      {
         inputImageData.alphaData = new byte[imageWidth * imageHeight];
         outputImageData.alphaData = new byte[imageWidth * imageHeight];
      }

      // Give the height map buffers a one element gap on every side, to prevent waves from flowing on to opposite sides when using our wave height progression formula.
      currentHeightMapBuffer = new int[(imageWidth + 2) * (imageHeight * 2)];
      previousHeightMapBuffer = new int[(imageWidth + 2) * (imageHeight * 2)];
   }


   // Resize constructor, preserving some or all of existing rippler, aligned at the left and top-most sides.
   public SWTImageRippler(final SWTImageRippler imageRippler, final int imageWidth, final int imageHeight)
   {
      this(imageWidth, imageHeight, imageRippler.hasAlpha, imageRippler.rippleSize, imageRippler.rippleDepth, imageRippler.rippleFadeFactor);

      transferImageData(imageRippler.inputImageData, inputImageData);
      transferImageData(imageRippler.outputImageData, outputImageData);

      transferHeightMapBuffer(imageRippler, imageRippler.currentHeightMapBuffer, currentHeightMapBuffer);
      transferHeightMapBuffer(imageRippler, imageRippler.previousHeightMapBuffer, previousHeightMapBuffer);

      isActive = imageRippler.isActive;
   }


   // We could also use Images and GC's to do the transfer - a bit less code, but more data copying, and creation/disposal of resources.
   private void transferImageData(final ImageData sourceImageData, final ImageData targetImageData)
   {
      final int transferImageWidthMaximum = Math.min(sourceImageData.width, targetImageData.width);
      final int transferImageHeightMaximum = Math.min(sourceImageData.height, targetImageData.height);

      int sourceImageDataPointer = 0;
      int sourceImageAlphaDataPointer = 0;
      int destinationImageDataPointer = 0;
      int destinationImageAlphaDataPointer = 0;

      for (int imageYPosition = 0; imageYPosition < transferImageHeightMaximum; imageYPosition ++)
      {
         System.arraycopy(sourceImageData.data, sourceImageDataPointer, targetImageData.data, destinationImageDataPointer, transferImageWidthMaximum * 3);

         if (hasAlpha)
            System.arraycopy(sourceImageData.alphaData, sourceImageAlphaDataPointer, targetImageData.alphaData, destinationImageAlphaDataPointer, transferImageWidthMaximum);

         sourceImageDataPointer += sourceImageData.bytesPerLine;
         sourceImageAlphaDataPointer += sourceImageData.width;

         destinationImageDataPointer += targetImageData.bytesPerLine;
         destinationImageAlphaDataPointer += targetImageData.width;
      }
   }


   private void transferHeightMapBuffer(final SWTImageRippler imageRippler, final int[] sourceHeightMapBuffer, final int[] targetHeightMapBuffer)
   {
      final int transferHeightMapWidthMaximum = Math.min(imageRippler.imageWidth, imageWidth);
      final int transferHeightMapHeightMaximum = Math.min(imageRippler.imageHeight, imageHeight);

      // Start our height map indices at 2nd row, 2nd pixel position.
      int sourceHeightMapPointer = imageRippler.imageWidth + 3;
      int destinationHeightMapPointer = imageWidth + 3;

      for (int imageYPosition = 0; imageYPosition < transferHeightMapHeightMaximum; imageYPosition ++)
      {
         System.arraycopy(sourceHeightMapBuffer, sourceHeightMapPointer, targetHeightMapBuffer, destinationHeightMapPointer, transferHeightMapWidthMaximum);
         sourceHeightMapPointer += (imageRippler.imageWidth + 2);
         destinationHeightMapPointer += (imageWidth + 2);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class ImageTransferRegion
   {
      final private int sourceImageWidth;
      final private int sourceImageHeight;
      private int sourceImageX;
      private int sourceImageY;

      final private int destinationImageWidth;
      final private int destinationImageHeight;
      private int destinationImageX;
      private int destinationImageY;

      private int transferWidth;
      private int transferHeight;


      private ImageTransferRegion(final int sourceImageWidth, final int sourceImageHeight, final int sourceImageX, final int sourceImageY, final int destinationImageWidth,
                                  final int destinationImageHeight, final int destinationImageX, final int destinationImageY, final int transferWidth, final int transferHeight)
      {
         this.sourceImageWidth = sourceImageWidth;
         this.sourceImageHeight = sourceImageHeight;
         this.sourceImageX = sourceImageX;
         this.sourceImageY = sourceImageY;
         this.destinationImageWidth = destinationImageWidth;
         this.destinationImageHeight = destinationImageHeight;
         this.destinationImageX = destinationImageX;
         this.destinationImageY = destinationImageY;
         this.transferWidth = transferWidth;
         this.transferHeight = transferHeight;
      }


      private void correctBounds()
      {
         /* Perform some bounds checking on the image region parameters, starting with the source image start X position and width.
          * The destination X start position may also be shifted right if the source image start X position is negative.
          */
         if (sourceImageX >= 0)
         {
            // Will be negative if sourceImageX is greater than the source image bounds.
            final int maximumWidth = (sourceImageWidth - sourceImageX);

            // Width will be negative if either sourceImageX is greater than the source image bounds, or transferWidth is negative.
            if (transferWidth > maximumWidth)
               transferWidth = maximumWidth;
         }
         else
         {
            // Will be negative if the transferWidth is insufficient to reach the left source image edge from the negative starting X position, or if transferWidth is negative.
            transferWidth += sourceImageX;

            if (transferWidth > sourceImageWidth)
               transferWidth = sourceImageWidth;

            destinationImageX -= sourceImageX;
            sourceImageX = 0;
         }

         // Now bounds check the source image start Y position and height.
         if (sourceImageY >= 0)
         {
            // Will be negative if sourceImageY is greater than the source image bounds.
            final int maximumHeight = (sourceImageHeight - sourceImageY);

            // Width will be negative if either sourceImageY is greater than the source image bounds, or transferHeight is negative.
            if (transferHeight > maximumHeight)
               transferHeight = maximumHeight;
         }
         else
         {
            // Will be negative if the transferHeight is insufficient to reach the top source image edge from the negative starting Y position, or if transferHeight is negative.
            transferHeight += sourceImageY;

            if (transferHeight > sourceImageHeight)
               transferHeight = sourceImageHeight;

            destinationImageY -= sourceImageY;
            sourceImageY = 0;
         }

         // Now bounds check the (possibly modified) destination image start X position and width against the destination image.
         if (destinationImageX >= 0)
         {
            // Will be negative if destinationImageX is greater than the destination image bounds.
            final int maximumOutputWidth = destinationImageWidth - destinationImageX;

            if (transferWidth > maximumOutputWidth)
               transferWidth = maximumOutputWidth;
         }
         else
         {
            // Will be negative if the transferWidth is insufficient to reach the left destination image edge from the negative starting X position.
            transferWidth += destinationImageX;
            if (transferWidth > destinationImageWidth)
               transferWidth = destinationImageWidth;

            destinationImageX = 0;
         }

         // Finally, bounds check the (possibly modified) destination image start Y position and height against the destination image.
         if (destinationImageY >= 0)
         {
            // Will be negative if destinationImageY is greater than the destination image bounds.
            final int maximumOutputHeight = destinationImageHeight - destinationImageY;

            if (transferHeight > maximumOutputHeight)
               transferHeight = maximumOutputHeight;
         }
         else
         {
            // Will be negative if the transferHeight is insufficient to reach the top destination image edge from the negative starting Y position.
            transferHeight += destinationImageY;
            if (transferHeight > destinationImageHeight)
               transferHeight = destinationImageHeight;

            destinationImageY = 0;
         }
      }


      private boolean isEmpty()
      {
         return ((transferWidth <= 0) || (transferHeight <= 0));
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final public void createRipple(final int rippleXCentre, final int rippleYCentre)
   {
      if ((rippleXCentre >= imageWidth) || (rippleYCentre >= imageHeight))
         return;

      int rippleLeftEdge = Math.max(0, rippleXCentre - (rippleSize / 2));
      int rippleUpperEdge = Math.max(0, rippleYCentre - (rippleSize / 2));

      int maximumRippleXSize = Math.min(rippleSize, imageWidth - rippleLeftEdge);
      int maximumRippleYSize = Math.min(rippleSize, imageHeight - rippleUpperEdge);

      int heightMapIndex = (rippleLeftEdge + 1) + ((rippleUpperEdge + 1) * (imageWidth + 2));

      for (int rippleYPosition = 0; rippleYPosition < maximumRippleYSize; rippleYPosition ++)
      {
         for (int rippleXPosition = 0; rippleXPosition < maximumRippleXSize; rippleXPosition ++)
            currentHeightMapBuffer[heightMapIndex ++] += rippleDepth;

         // Jump to left edge of splash on next row, taking int account one pixel image border.
         heightMapIndex += ((imageWidth + 2) - maximumRippleXSize);
      }
   }


   final public boolean incrementRippleEffect()
   {
      int highestRippleHeight = 0;

      // Start our height map index at 2nd row, 2nd pixel position.
      int heightMapPointer = imageWidth + 3;

      int waterHeight;

      int reflectedPixelXPosition;
      int reflectedPixelYPosition;

      int inputImageDataIndex;
      int outputImageDataIndex = 0;
      int outputImageAlphaDataIndex = 0;
      int bytePaddingBetweenRows = outputImageData.bytesPerLine - (imageWidth * 3);

      int[] tempHeightMapBuffer = currentHeightMapBuffer;
      currentHeightMapBuffer = previousHeightMapBuffer;
      previousHeightMapBuffer = tempHeightMapBuffer;

      for (int imageYPosition = 0; imageYPosition < imageHeight; imageYPosition ++)
      {
         for (int imageXPosition = 0; imageXPosition < imageWidth; imageXPosition ++)
         {
            /* Either of these implementations are great, the one with the less pixel calculations definitely produces
             * a slimmer stream effect, whereas the other one produces a more 'full' or square wave from the mouse point.
             * Also I think that the simpler calculation produces slightly more of a pixellated effect on the underlying images.
             */
//            waterHeight = (previousHeightMapBuffer[heightMapPointer - imageWidth - 2] +
//                           previousHeightMapBuffer[heightMapPointer + imageWidth + 2] +
//                           previousHeightMapBuffer[heightMapPointer - 1] +
//                           previousHeightMapBuffer[heightMapPointer + 1]) >> 1;
            waterHeight = (previousHeightMapBuffer[heightMapPointer - imageWidth - 2] +
                           previousHeightMapBuffer[heightMapPointer - imageWidth - 3] +
                           previousHeightMapBuffer[heightMapPointer - imageWidth - 1] +
                           previousHeightMapBuffer[heightMapPointer + imageWidth + 2] +
                           previousHeightMapBuffer[heightMapPointer + imageWidth + 1] +
                           previousHeightMapBuffer[heightMapPointer + imageWidth + 3] +
                           previousHeightMapBuffer[heightMapPointer - 1] +
                           previousHeightMapBuffer[heightMapPointer + 1]) >> 2;

            waterHeight -= currentHeightMapBuffer[heightMapPointer];
            waterHeight -= (waterHeight >> rippleFadeFactor);
            currentHeightMapBuffer[heightMapPointer] = waterHeight;

            if (waterHeight > highestRippleHeight)
               highestRippleHeight = waterHeight;

            waterHeight = (RippleHeight - waterHeight);

            reflectedPixelXPosition = (((imageXPosition - halfImageWidth) * waterHeight) >> RippleHeightShift) + halfImageWidth;
            if (reflectedPixelXPosition < 0)
               reflectedPixelXPosition = 0;
            else if (reflectedPixelXPosition >= imageWidth)
               reflectedPixelXPosition = imageWidth - 1;

            reflectedPixelYPosition = (((imageYPosition - halfImageHeight) * waterHeight) >> RippleHeightShift) + halfImageHeight;
            if (reflectedPixelYPosition < 0)
               reflectedPixelYPosition = 0;
            else if (reflectedPixelYPosition >= imageHeight)
               reflectedPixelYPosition = imageHeight - 1;

            inputImageDataIndex = (reflectedPixelYPosition * inputImageData.bytesPerLine) + (reflectedPixelXPosition * 3);
            outputImageData.data[outputImageDataIndex ++] = inputImageData.data[inputImageDataIndex ++];
            outputImageData.data[outputImageDataIndex ++] = inputImageData.data[inputImageDataIndex ++];
            outputImageData.data[outputImageDataIndex ++] = inputImageData.data[inputImageDataIndex ++];

            // If on a singular instance-level final boolean.. the hotspot will optimise this to a non-check, right?
            if (hasAlpha)
            {
               inputImageDataIndex = (reflectedPixelYPosition * imageWidth) + reflectedPixelXPosition;
               outputImageData.alphaData[outputImageAlphaDataIndex ++] = inputImageData.alphaData[inputImageDataIndex];
            }

            heightMapPointer ++;
         }

         // Jump the one pixel border gap between rows.
         heightMapPointer += 2;

         outputImageDataIndex += bytePaddingBetweenRows;
      }

      isActive = (highestRippleHeight > 0);

      return isActive;
   }


   final public void updateInputImagePixels(final BufferedImage updatedInputImage, final int inputImageStartX, final int inputImageStartY,
                                            final int regionWidth, final int regionHeight, final int destinationX, final int destinationY)
   {
      if ((! hasAlpha) && updatedInputImage.getColorModel().hasAlpha())
         throw new IllegalArgumentException("Instance of image rippler does not support translucent images.");

      final ImageTransferRegion updateRegion = new ImageTransferRegion(updatedInputImage.getWidth(), updatedInputImage.getHeight(), inputImageStartX, inputImageStartY,
                                                                       imageWidth, imageHeight, destinationX, destinationY, regionWidth, regionHeight);
      updateRegion.correctBounds();

      // Bail out if the region is empty.
      if (! updateRegion.isEmpty())
      {
         final BufferedImage compatibleImage = UIUtilities.ensureINTRGBInputImage(updatedInputImage);

         handleUpdateInputImagePixels(compatibleImage, updateRegion);
      }
   }


   private void handleUpdateInputImagePixels(final BufferedImage updatedInputImage, final ImageTransferRegion updateRegion)
   {
      final int updatedInputImageLowerY = updateRegion.sourceImageY + updateRegion.transferHeight;

      // Initialise pixel data pointers...
      int leftMostDestinationImageDataIndex = (inputImageData.bytesPerLine * updateRegion.destinationImageY) + (updateRegion.destinationImageX * 3);
      int destinationImageDataIndex;
      int leftMostDestinationImageAlphaDataIndex = (inputImageData.width * updateRegion.destinationImageY) + updateRegion.destinationImageX;
      int destinationImageAlphaDataIndex;

      int leftMostOutputImageDataIndex = (outputImageData.bytesPerLine * updateRegion.destinationImageY) + (updateRegion.destinationImageX * 3);
      int leftMostOutputImageAlphaDataIndex = (outputImageData.width * updateRegion.destinationImageY) + updateRegion.destinationImageX;

      /* The call to UIUtilities.ensureINTRGBInputImage() prior to this method ensures that the input image is of type RGB or ARGB, having integer transfer type.
       * Furthermore, it will always be using a DirectColorModel. The docs for DirectColorModel state that the order of colour components for pixel operations
       * is always red, green, blue, then (if present) alpha, hence I can assume this order for array data returned from methods such as getComponents().
       */
      final ColorModel colourModel = updatedInputImage.getColorModel();
      final boolean updatedImageHasAlpha = colourModel.hasAlpha();

      final int[] updatedImagePixelData = new int[updateRegion.transferWidth * updatedInputImage.getRaster().getNumDataElements()];
      int updatedImagePixelDataIndex;
      int packedPixelValue;
      final int[] pixelComponents = new int[colourModel.getNumComponents()];

      for (int imageYPosition = updateRegion.sourceImageY; imageYPosition < updatedInputImageLowerY; imageYPosition ++)
      {
         updatedInputImage.getRaster().getDataElements(updateRegion.sourceImageX, imageYPosition, updateRegion.transferWidth, 1, updatedImagePixelData);

         destinationImageDataIndex = leftMostDestinationImageDataIndex;
         destinationImageAlphaDataIndex = leftMostDestinationImageAlphaDataIndex;

         for (updatedImagePixelDataIndex = 0; updatedImagePixelDataIndex < updatedImagePixelData.length; updatedImagePixelDataIndex ++)
         {
            packedPixelValue = updatedImagePixelData[updatedImagePixelDataIndex];
            colourModel.getComponents(packedPixelValue, pixelComponents, 0);

            inputImageData.data[destinationImageDataIndex ++] = (byte) pixelComponents[0];
            inputImageData.data[destinationImageDataIndex ++] = (byte) pixelComponents[1];
            inputImageData.data[destinationImageDataIndex ++] = (byte) pixelComponents[2];

            if (updatedImageHasAlpha)
               inputImageData.alphaData[destinationImageAlphaDataIndex ++] = (byte) pixelComponents[3];
         }

         // If the rippler is not active, ie. output image not distorted by ripples, I can immediately flush through the image changes here. This is a lot more efficient than
         // manually calling incrementRippleEffect() which will traverse the entire height map to potentially update a small portion of it.
         if (! isActive)
         {
            System.arraycopy(inputImageData.data, leftMostDestinationImageDataIndex, outputImageData.data, leftMostOutputImageDataIndex, updatedImagePixelData.length * 3);
            leftMostOutputImageDataIndex += outputImageData.bytesPerLine;

            if (updatedImageHasAlpha)
            {
               System.arraycopy(inputImageData.alphaData, leftMostDestinationImageAlphaDataIndex, outputImageData.alphaData, leftMostOutputImageAlphaDataIndex, updatedImagePixelData.length);
               leftMostOutputImageAlphaDataIndex += outputImageData.width;
            }
         }

         // Jump the image indices to the start of the next row.
         leftMostDestinationImageDataIndex += inputImageData.bytesPerLine;
         leftMostDestinationImageAlphaDataIndex += inputImageData.width;
      }
   }


   final public void transferImagePixels(final int sourceX, final int sourceY, final int regionWidth, final int regionHeight, final int destinationX, final int destinationY)
   {
      final ImageTransferRegion region = new ImageTransferRegion(imageWidth, imageHeight, sourceX, sourceY, imageWidth, imageHeight, destinationX, destinationY, regionWidth, regionHeight);
      region.correctBounds();

      if (! region.isEmpty())
         handleTransferRegion(region);
   }


   private void handleTransferRegion(final ImageTransferRegion region)
   {
      /* Since the intersection of the destination region uses the height & width of the already intersected source region, the resulting height & widths
       * in this rectangle are the ones that apply for the resulting transfer.
       */
      final int regionWidthInBytes = region.transferWidth * 3;

      int sourceDataIndex;
      int sourceAlphaDataIndex;
      int destinationDataIndex;
      int destinationAlphaDataIndex;

      final int dataIndexIncrement;
      final int alphaDataIndexIncrement;

      /* We're copying the image data horizontal line by line, from the same source image array.
       * We need to ensure that the transfer isn't corrupted by lines copying over previous lines, in the case where the source and destination regions
       * overlap. It's safe to copy a lower block to higher region line by line from top to bottom, but to copy a higher block to a lower region
       * we need to copy the lines from bottom to top.
       */
      if (region.sourceImageY > region.destinationImageY)
      {
         sourceDataIndex = (inputImageData.bytesPerLine * region.sourceImageY) + (region.sourceImageX * 3);
         sourceAlphaDataIndex = (inputImageData.width * region.sourceImageY) + region.sourceImageX;

         destinationDataIndex = (inputImageData.bytesPerLine * region.destinationImageY) + (region.destinationImageX * 3);
         destinationAlphaDataIndex = (inputImageData.width * region.destinationImageY) + region.destinationImageX;

         dataIndexIncrement = inputImageData.bytesPerLine;
         alphaDataIndexIncrement = inputImageData.width;
      }
      else
      {
         sourceDataIndex = (inputImageData.bytesPerLine * (region.sourceImageY + region.transferHeight - 1)) + (region.sourceImageX * 3);
         sourceAlphaDataIndex = (inputImageData.width * (region.sourceImageY + region.transferHeight - 1)) + region.sourceImageX;

         destinationDataIndex = (inputImageData.bytesPerLine * (region.destinationImageY + region.transferHeight - 1)) + (region.destinationImageX * 3);
         destinationAlphaDataIndex = (inputImageData.width * (region.destinationImageY + region.transferHeight - 1)) + region.destinationImageX;

         dataIndexIncrement = -inputImageData.bytesPerLine;
         alphaDataIndexIncrement = -inputImageData.width;
      }

      for (int imageYCounter = 0; imageYCounter < region.transferHeight; imageYCounter ++)
      {
         System.arraycopy(inputImageData.data, sourceDataIndex, inputImageData.data, destinationDataIndex, regionWidthInBytes);

         if (hasAlpha)
            System.arraycopy(inputImageData.alphaData, sourceAlphaDataIndex, inputImageData.alphaData, destinationAlphaDataIndex, region.transferWidth);

         // If our rippler is not active, ie. output image not distorted by ripples, we can immediately flush through the image changes here. This is a lot more efficient than
         // manually calling incrementRippleEffect() which will traverse the entire height map to potentially update a small portion of it.
         if (! isActive)
         {
            System.arraycopy(outputImageData.data, sourceDataIndex, outputImageData.data, destinationDataIndex, regionWidthInBytes);

            if (hasAlpha)
               System.arraycopy(outputImageData.alphaData, sourceAlphaDataIndex, outputImageData.alphaData, destinationAlphaDataIndex, region.transferWidth);
         }

         // Jump the image indices to the start of the next row.
         sourceDataIndex += dataIndexIncrement;
         sourceAlphaDataIndex += alphaDataIndexIncrement;

         destinationDataIndex += dataIndexIncrement;
         destinationAlphaDataIndex += alphaDataIndexIncrement;
      }
   }


   final public void clearInputImagePixels(final int regionX, final int regionY, final int regionWidth, final int regionHeight)
   {
      // Do some bounds checking...
      final Rectangle boundsCorrectedDestinationRegion = new Rectangle(regionX, regionY, regionWidth, regionHeight);
      SwingUtilities.computeIntersection(0, 0, imageWidth, imageHeight, boundsCorrectedDestinationRegion);

      if (! boundsCorrectedDestinationRegion.isEmpty())
         handleClearBounds(boundsCorrectedDestinationRegion);
   }


   private void handleClearBounds(final Rectangle destinationRegion)
   {
      int leftMostDestinationImageDataIndex = (inputImageData.bytesPerLine * destinationRegion.y) + (destinationRegion.x * 3);
      int leftMostDestinationImageAlphaDataIndex = (inputImageData.width * destinationRegion.y) + destinationRegion.x;

      final byte[] clearArrayHelper = new byte[destinationRegion.width * 3];

      for (int imageYCounter = 0; imageYCounter < destinationRegion.height; imageYCounter ++)
      {
         // Arrays.fill is slower?
          //Arrays.fill(inputImageData.data, leftMostDestinationImageDataIndex, leftMostDestinationImageDataIndex + (destinationRegion.width * 3), (byte) 0);
         System.arraycopy(clearArrayHelper, 0, inputImageData.data, leftMostDestinationImageDataIndex, clearArrayHelper.length);

         if (hasAlpha)
            System.arraycopy(clearArrayHelper, 0, inputImageData.alphaData, leftMostDestinationImageAlphaDataIndex, destinationRegion.width);

         // If our rippler is not active, ie. output image not distorted by ripples, we can immediately flush through the image changes here. This is a lot more efficient than
         // manually calling incrementRippleEffect() which will traverse the entire height map to potentially update a small portion of it.
         if (! isActive)
         {
            System.arraycopy(clearArrayHelper, 0, outputImageData.data, leftMostDestinationImageDataIndex, clearArrayHelper.length);

            if (hasAlpha)
               System.arraycopy(clearArrayHelper, 0, outputImageData.alphaData, leftMostDestinationImageAlphaDataIndex, destinationRegion.width);
         }

         // Jump the image indices to the start of the next row.
         leftMostDestinationImageDataIndex += inputImageData.bytesPerLine;
         leftMostDestinationImageAlphaDataIndex += inputImageData.width;
      }
   }


   final public ImageData getOutputImageData()
   {
      return outputImageData;
   }


   final public ImageData getOutputSubimageData(final int imageX, final int imageY, final int imageWidth, final int imageHeight)
   {
      final ImageData outputSubimage = new ImageData(imageWidth, imageHeight, SWTImageDepth, outputImageData.palette);

      if (hasAlpha)
         outputSubimage.alphaData = new byte[imageWidth * imageHeight];

      int leftMostSourceImageDataIndex = (outputImageData.bytesPerLine * imageY) + (imageX * 3);
      int leftMostSourceImageAlphaDataIndex = (inputImageData.width * imageY) + imageX;

      int leftMostDestinationImageDataIndex = 0;
      int leftMostDestinationImageAlphaDataIndex = 0;

      for (int imageYPosition = 0; imageYPosition < imageHeight; imageYPosition ++)
      {
         System.arraycopy(outputImageData.data, leftMostSourceImageDataIndex, outputSubimage.data, leftMostDestinationImageDataIndex, imageWidth * 3);

         leftMostSourceImageDataIndex += outputImageData.bytesPerLine;
         leftMostDestinationImageDataIndex += outputSubimage.bytesPerLine;

         if (hasAlpha)
         {
            System.arraycopy(outputImageData.alphaData, leftMostSourceImageAlphaDataIndex, outputSubimage.alphaData, leftMostDestinationImageAlphaDataIndex, imageWidth);

            leftMostSourceImageAlphaDataIndex += outputImageData.width;
            leftMostDestinationImageAlphaDataIndex += imageWidth;
         }
      }

      return outputSubimage;
   }
}