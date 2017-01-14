/* Memos:
 * - This image loader provides the means to impose a limit on the number of queued load requests, where the oldest load requests in the queue
 *   will automatically be cancelled so that priority is given to a) load requests that are already in progress, and b) the newest load requests.
 *   This strategy suits most UI operations in Swing such as within tables and lists where the user can scroll quickly past items having thumbnails.
 *   The table rendering code in Swing will end up placing load requests for many items that the user is not going to see as they scroll
 *   past them. As the scrolling position settles to a final position, it's those final bunch of image load requests that are needed.
 *   Rather than allow the earlier load requests to back up and slow down the loading of the newer items that they are interested in,
 *   eg. unconditionally firing off new SwingWorker requests as they arrive with no other logic imposed on earlier requests, this class makes
 *   use of a regulated queue executor service which will try to automatically cancel the older tasks.
 *
 * - The scheme relies on the number of allowed queued requests being at least as many as that needed to completely load a visible form/table of
 *   images at any moment. So for example a limit of three images would not work where the Feedbactory UI is displaying tables that might display
 *   a dozen or more thumbnail images at any given time.
 */

package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.component.RegulatedQueueExecutorService.RegulatedQueueExecutorServiceConsumer;
import java.awt.Dimension;
import java.awt.RenderingHints;
import java.awt.color.CMMException;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;
import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.util.ImagingListener;
import javax.swing.SwingUtilities;


final public class ImageLoader
{
   static final private int MaximumActiveRequests = 4;
   static final private long InactiveRequestThreadTimeoutMilliseconds = 3000L;

   // Adjust to a higher value if images on panels are not loading due to requests being dropped.
   static final private int MaximumQueuedRequests = 15;

   /* Without an explicit timeout provided, ImageIO.read(URL) has been known to hang for a long period of time, at least on Windows.
    * This can especially become a problem during shutdown, when the shutdownAndAwaitTermination() will exceed its timeout and even its
    * attempt to interrupt the IO on the task (shutdownNow()) will be unsuccessful.
    */
   static final private int ImageLoadTimeoutMilliseconds = 7000;

   static final private Dimension MaximumCachedImageSize = new Dimension(250, 250);

   final private Map<String, ImageLoadHandler> imageLoadHandlers = new HashMap<String, ImageLoadHandler>();

   final private RegulatedQueueExecutorService<BufferedImage> executor = new RegulatedQueueExecutorService<BufferedImage>(MaximumActiveRequests, InactiveRequestThreadTimeoutMilliseconds, MaximumQueuedRequests);


   public ImageLoader()
   {
      initialise();
   }


   private void initialise()
   {
      ImageIO.setUseCache(false);

      JAI.getDefaultInstance().setImagingListener(new ImagingListener()
      {
         @Override
         final public boolean errorOccurred(final String message, final Throwable thrown, final Object where, final boolean isRetryable) throws RuntimeException
         {
            return handleJAIErrorOccurred(thrown);
         }
      });
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final private class ImageLoadRequest
   {
      final private ImageLoadRequester requester;
      final private Dimension requestedSize;

      private ImageLoadRequest(final ImageLoadRequester requester, final Dimension requestedSize)
      {
         this.requester = requester;
         this.requestedSize = requestedSize;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class ImageLoadHandler implements Callable<BufferedImage>, RegulatedQueueExecutorServiceConsumer<BufferedImage>
   {
      final private String imageURL;
      final private Set<ImageLoadRequest> imageRequesters = new HashSet<ImageLoadRequest>();

      private Reference<BufferedImage> cachedImageReference;
      private Dimension originalImageSize;

      private boolean isLoaderActive;


      private ImageLoadHandler(final String imageURL)
      {
         this.imageURL = imageURL;
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private BufferedImage handleAddImageLoadRequest(final ImageLoadRequest request)
      {
         if (cachedImageReference == null)
            processAddImageLoadRequest(request);
         else
         {
            final BufferedImage cachedImage = cachedImageReference.get();

            /* Place a new image load request if:
             * - Our cached image reference has expired, or
             * - The cached image has been scaled down from the original, and the original may be scaled proportionally to a size larger than
             *   that of the cached image. The proportional scaling is important: if either the cached image's width or height is already
             *   greater than or equal to the corresponding requested width or height, there is no point in trying to reload and rescale the image,
             *   since it will only be scaled proportionally, ie. not stretched in one direction.
             */
            if ((cachedImage == null) ||
                (((originalImageSize.width > cachedImage.getWidth()) && (originalImageSize.height > cachedImage.getHeight())) &&
                ((request.requestedSize.width > cachedImage.getWidth()) && (request.requestedSize.height > cachedImage.getHeight()))))
               processAddImageLoadRequest(request);
            else
               return cachedImage;
         }

         return null;
      }


      private void processAddImageLoadRequest(final ImageLoadRequest request)
      {
         imageRequesters.add(request);

         if (! isLoaderActive)
         {
            isLoaderActive = true;
            executor.execute(this, this);
         }
      }


      private BufferedImage handleCall() throws Exception
      {
         InputStream connectionInputStream = null;

         try
         {
            final URL url = new URL(imageURL);
            final URLConnection urlConnection = url.openConnection();
            urlConnection.setConnectTimeout(ImageLoadTimeoutMilliseconds);
            urlConnection.setReadTimeout(ImageLoadTimeoutMilliseconds);
            connectionInputStream = urlConnection.getInputStream();

            return ImageIO.read(connectionInputStream);
         }
         catch (final CMMException cmmException)
         {
            /* Unfortunately ImageIO produces CMMExceptions for some JPEG's that don't conform strictly to the spec.
             * These images are usually handled fine on the web and elsewhere. JAI seems to handle them more successfully, so
             * use that as a fallback.
             * Note that at present, the JAI used is the pure Java implementation, not using native platform acceleration.
             *
             * Apparently the CMMException has been resolved in Java 8, so I could consider removing JAI entirely.
             */
            final RenderedOp renderedOp = JAI.create("url", new URL(imageURL));
            return renderedOp.getAsBufferedImage();
         }
         catch (final ConnectException connectException)
         {
            return null;
         }
         catch (final SocketTimeoutException socketTimeoutException)
         {
            return null;
         }
         finally
         {
            if (connectionInputStream != null)
               connectionInputStream.close();
         }
      }


      private void handleRequestCompleted(final BufferedImage image, final Throwable exceptionDuringLoading)
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               handleRequestCompletedEDT(image, exceptionDuringLoading);
            }
         });
      }


      private void handleRequestCompletedEDT(final BufferedImage image, final Throwable exception)
      {
         // Perform no callbacks or further processing on shutdown.
         if (executor.isShutdown())
            return;

         isLoaderActive = false;

         for (final ImageLoadRequest request : imageRequesters)
            request.requester.reportImageLoaded(imageURL, image, exception);

         imageRequesters.clear();

         if (image != null)
         {
            if ((image.getWidth() > MaximumCachedImageSize.width) || (image.getHeight() > MaximumCachedImageSize.height))
            {
               final BufferedImage downscaledImage = UIUtilities.getProportionalScaledImage(image, MaximumCachedImageSize.width, MaximumCachedImageSize.height,
                                                                                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
               cachedImageReference = new SoftReference<BufferedImage>(downscaledImage);
            }
            else
               cachedImageReference = new SoftReference<BufferedImage>(image);

            originalImageSize = new Dimension(image.getWidth(), image.getHeight());
         }
      }


      private void handleRequestCancelled()
      {
         handleRequestCompleted(null, null);
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private BufferedImage addImageLoadRequest(final ImageLoadRequest request)
      {
         return handleAddImageLoadRequest(request);
      }


      @Override
      final public BufferedImage call() throws Exception
      {
         return handleCall();
      }


      @Override
      final public void requestCompleted(final BufferedImage image, final Throwable exception)
      {
         handleRequestCompleted(image, exception);
      }


      @Override
      final public void requestCancelled()
      {
         handleRequestCancelled();
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static public interface ImageLoadRequester
   {
      public void reportImageLoaded(final String imageURL, final BufferedImage image, final Throwable exception);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private boolean handleJAIErrorOccurred(final Throwable thrown)
   {
      /* The default JAI ImagingListener automatically spits out error messages internally for some exceptions, eg. unknown host exceptions.
       * This doesn't fit well with the rest of the Feedbactory client, so override its default exception handling behaviour.
       *
       * Docs taken from ImagingListener:
       *
       * "For backward compatibility, an instance of a simple implementation of this interface is used as the default in all the JAI instances.
       * It re-throws the Throwable if it is a RuntimeException. For the other types of Throwable, it only prints the message and the stack trace to the stream
       * System.err, and returns false. To process the reported errors or warnings an alternate implementation of ImagingListener should be written."
       */
      if (thrown instanceof RuntimeException)
         throw ((RuntimeException) thrown);
      else
         return false;
   }


   private BufferedImage handleLoadImage(final String imageURL, final int requestedWidth, final int requestedHeight, final ImageLoadRequester requester)
   {
      assert SwingUtilities.isEventDispatchThread();

      if (imageURL == null)
         throw new IllegalArgumentException("Requested image URL cannot be null.");
      else if ((requestedWidth < 1) || (requestedHeight < 1))
         throw new IllegalArgumentException("Invalid image dimensions: " + requestedWidth + ", " + requestedHeight);
      else if (requester == null)
         throw new IllegalArgumentException("Image requester cannot be null.");
      else if (executor.isShutdown())
         return null;

      ImageLoadHandler imageLoadHandler;

      imageLoadHandler = imageLoadHandlers.get(imageURL);

      if (imageLoadHandler == null)
      {
         imageLoadHandler = new ImageLoadHandler(imageURL);
         imageLoadHandlers.put(imageURL, imageLoadHandler);
      }

      return imageLoadHandler.addImageLoadRequest(new ImageLoadRequest(requester, new Dimension(requestedWidth, requestedHeight)));
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public BufferedImage loadImage(final String imageURL, final int requestedWidth, final int requestedHeight, final ImageLoadRequester requester)
   {
      return handleLoadImage(imageURL, requestedWidth, requestedHeight, requester);
   }


   final public void shutdown()
   {
      executor.shutdown(true);
   }
}