/* ENHANCEMENTS:
 * - Implement a repaint manager type mechanism to hold off on immediate repaints while a series of image transfers are pending, eg. on a resize. Could possibly use a
 *    progressively growing Rectangle to add() the affected paint and clear regions.
 *
 * - It would be nice to delegate the regionPainted calls to worker threads, however I would need to lock changes to the source image.
 *
 * - Similarly it would be nice to multi-thread regionCleared calls, however the danger here is not protecting the source image parameter (there is none), but the sequence
 *    of method calls. If we're not careful, it's proven that regionCleared and regionPainted calls may happen out of sequence, resulting in blank image areas. I thought
 *    that the remedy for this might simply be to make use of a newSingleThreadExecutor which is guaranteed to execute tasks sequentially, but from memory it just made
 *    the image updates appear sluggish and messy. Possibly because there would be situations where the regionCleared executor task is doing its thing meanwhile the Swing
 *    EDT moves onto the regionPainted call which will pause for a lengthy time until regionCleared returns and the singular executor task is freed up. Maybe this enhancement
 *    is something to be tackled in tandem with the hold & flush repaint manager mechanism alluded to above, ie. perhaps it's the timing of the SWT redraw that causes problems.
 *    In any case there's still the problem of having to protect the source images during regionPainted calls, ie. would need to be locked in client methods of this class,
 *    or entirely duplicated for each paint.
 * 
 * - generateRipple is a candidate for multithreading though, if responsiveness suffers. Its workload is relatively minuscule however.
 */

package com.feedbactory.client.ui.component;


import com.feedbactory.client.core.ClientUtilities;
import com.feedbactory.client.ui.component.graftable.GraftableComponentPaintReceiver;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Composite;


public class WateryPanel implements GraftableComponentPaintReceiver
{
   final private Composite composite;

   private SWTImageRippler imageRippler;

   final private int rippleSize;
   final private int rippleDepth;
   final private int rippleDuration;

   final private MouseMoveListener waterEffectMouseMotionListener = initialiseWaterEffectMouseMotionListener();;
   private boolean isWaterEffectEnabled;

   final private RipplePropagationTask ripplePropagationTask = new RipplePropagationTask();

   final private Runnable redrawTask = initialiseRedrawTask();


   public WateryPanel(final Composite composite, final int rippleSize, final int rippleDepth, final int rippleDuration)
   {
      this.composite = composite;

      this.rippleSize = rippleSize;
      this.rippleDepth = rippleDepth;
      this.rippleDuration = rippleDuration;

      initialise();
   }


   private Runnable initialiseRedrawTask()
   {
      return new Runnable()
      {
         @Override
         final public void run()
         {
            composite.redraw();
         }
      };
   }


   private void initialise()
   {
      initialiseControlListener();
      initialisePaintListener();
   }


   private void initialiseControlListener()
   {
      composite.addControlListener(new ControlAdapter()
      {
         @Override
         final public void controlResized(final ControlEvent controlEvent)
         {
            final Point compositeSize = composite.getSize();

            if ((compositeSize.x <= 0) || (compositeSize.y <= 0))
               return;

            synchronized (ripplePropagationTask)
            {
               if (imageRippler == null)
                  imageRippler = new SWTImageRippler(compositeSize.x, compositeSize.y, true, rippleSize, rippleDepth, rippleDuration);
               else
               {
                  // Generate a new image rippler of the necessary size, and transfer the current ripple state from the old one.
                  imageRippler = new SWTImageRippler(imageRippler, compositeSize.x, compositeSize.y);
               }
            }
         }
      });
   }


   private void initialisePaintListener()
   {
      composite.addPaintListener(new PaintListener()
      {
         @Override
         final public void paintControl(final PaintEvent paintEvent)
         {
            final Image image;

            synchronized (ripplePropagationTask)
            {
               // The ImageData provided to the Image constructor is copied, so we can immediately release the lock once the object has been constructed;
               final Point compositeSize = composite.getSize();
               final ImageData imageData;

               if ((paintEvent.x == 0) && (paintEvent.y == 0) && (paintEvent.width == compositeSize.x) && (paintEvent.height == compositeSize.y))
                  imageData = imageRippler.getOutputImageData();
               else
                  imageData = imageRippler.getOutputSubimageData(paintEvent.x, paintEvent.y, paintEvent.width, paintEvent.height);

               image = new Image(paintEvent.display, imageData);
            }

            paintEvent.gc.drawImage(image, paintEvent.x, paintEvent.y);

            image.dispose();
         }
      });
   }


   private MouseMoveListener initialiseWaterEffectMouseMotionListener()
   {
      return new MouseMoveListener()
      {
         @Override
         final public void mouseMove(final org.eclipse.swt.events.MouseEvent mouseEvent)
         {
            generateNewRipple(mouseEvent.x, mouseEvent.y);
         }
      };
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static private enum RippleEnabledState
   {
      Disabled,
      Enabled,
      Shutdown;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static private enum RippleTaskState
   {
      Idle,
      Active;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   // Refer to FeedbactoryBrowserWindow for an important note regarding the need to make the redraw call while holding the same lock used for the synchronous shutdown.
   final private class RipplePropagationTask implements Runnable
   {
      static final private long IdleWaterAnimationTimeoutMilliseconds = 5000L;
      static final private long TargetFramesPerSecond = 60L;

      private ScheduledThreadPoolExecutor executorService;
      private ScheduledFuture<?> scheduledRippleTask;
      private RippleEnabledState enabledState = RippleEnabledState.Disabled;
      private RippleTaskState taskState = RippleTaskState.Idle;


      @Override
      final public void run()
      {
         try
         {
            synchronized (this)
            {
               if (enabledState == RippleEnabledState.Shutdown)
                  return;

               Thread.currentThread().setName("WateryPanel.RipplePropagationTask");

               if (! imageRippler.incrementRippleEffect())
               {
                  scheduledRippleTask.cancel(false);

                  taskState = RippleTaskState.Idle;

                  if (enabledState == RippleEnabledState.Disabled)
                     shutdownExecutorService();
               }
            }

            redrawComposite();
         }
         catch (final Exception anyException)
         {
            /* Exception handling is performed this way since no default exception handling is otherwise provided.
             * This Runnable is wrapped by a FutureTask by the ScheduledExecutorService.scheduleAtFixedRate() method, and the FutureTask
             * will capture any unhandled exceptions without reporting them until the Future.get() method is called, which it generally never is
             * with fire-and-forget UI tasks. Without some manual exception handling, the only indication that a regular scheduled task has
             * encountered a problem is that no further invocations will be made.
             */
            Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), anyException);

            // Propagate the exception to prevent further invocations of run() by scheduleAtFixedRate().
            throw new RuntimeException(anyException);
         }
      }


      synchronized private void startTask()
      {
         if ((enabledState == RippleEnabledState.Enabled) && (taskState == RippleTaskState.Idle))
         {
            taskState = RippleTaskState.Active;
            scheduledRippleTask = executorService.scheduleAtFixedRate(ripplePropagationTask, 0L, 1000L / TargetFramesPerSecond, TimeUnit.MILLISECONDS);
         }
      }


      synchronized private void setEnabled()
      {
         if (enabledState == RippleEnabledState.Disabled)
         {
            /* The ripple task should reuse the executor of a previously active state if the rippler has been disabled and re-enabled in quick succession,
             * ie. if the ripples are still in motion via the previous executor. Otherwise, create a new executor service.
             */
            if (taskState == RippleTaskState.Idle)
            {
               executorService = new ScheduledThreadPoolExecutor(1);
               executorService.setKeepAliveTime(IdleWaterAnimationTimeoutMilliseconds, TimeUnit.MILLISECONDS);
               executorService.allowCoreThreadTimeOut(true);
            }

            enabledState = RippleEnabledState.Enabled;
         }
      }


      synchronized private void setDisabled()
      {
         enabledState = RippleEnabledState.Disabled;
      }


      synchronized private void shutdownExecutorService()
      {
         if (executorService != null)
            executorService.shutdown();
      }


      private void shutdown()
      {
         final ExecutorService executorServiceReference;

         synchronized (this)
         {
            enabledState = RippleEnabledState.Shutdown;
            executorServiceReference = executorService;
         }

         if (executorServiceReference != null)
         {
            /* Ensure that even if all rippler update and SWT redrawComposite() tasks haven't completed by the time this method returns,
             * there is at least the guarantee that any remaining calls to SWT's asyncExec have been called.
             * Note that the wait must occur outside of the rippler lock, since the ripper task will also need the lock to complete.
             */
            ClientUtilities.shutdownAndAwaitTermination(executorServiceReference, "WateryPanel.RipplePropagationTask");
         }
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void generateNewRipple(final int rippleCentreXCoordinate, final int rippleCentreYCoordinate)
   {
      synchronized (ripplePropagationTask)
      {
         imageRippler.createRipple(rippleCentreXCoordinate, rippleCentreYCoordinate);

         ripplePropagationTask.startTask();
      }
   }


   private void handlePreDispose()
   {
      ripplePropagationTask.shutdown();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   // SWT EDT.
   final public void setWaterEffectEnabled(final boolean isWaterEffectEnabled)
   {
      synchronized (ripplePropagationTask)
      {
         if (this.isWaterEffectEnabled != isWaterEffectEnabled)
         {
            this.isWaterEffectEnabled = isWaterEffectEnabled;

            if (this.isWaterEffectEnabled)
            {
               ripplePropagationTask.setEnabled();
               composite.addMouseMoveListener(waterEffectMouseMotionListener);
            }
            else
            {
               composite.removeMouseMoveListener(waterEffectMouseMotionListener);
               ripplePropagationTask.setDisabled();
            }
         }
      }
   }


   @Override
   final public void regionPainted(final BufferedImage paintedImage, final int outputRegionUpperLeftX, final int outputRegionUpperLeftY, final boolean deferRedraw)
   {
      regionPainted(paintedImage, 0, 0, paintedImage.getWidth(), paintedImage.getHeight(), outputRegionUpperLeftX, outputRegionUpperLeftY, deferRedraw);
   }


   @Override
   final public void regionPainted(final BufferedImage paintedImage, final int inputImageStartX, final int inputImageStartY,
                                   final int regionWidth, final int regionHeight, final int destinationX, final int destinationY, final boolean deferRedraw)
   {
      // THREADING: It would be nice to delegate the update pixels call to another thread, however we would need to lock changes to paintedImage in the client, or
      //    duplicate the image.
      synchronized (ripplePropagationTask)
      {
         if (imageRippler != null)
            imageRippler.updateInputImagePixels(paintedImage, inputImageStartX, inputImageStartY, regionWidth, regionHeight, destinationX, destinationY);
      }

      if (! deferRedraw)
         redrawComposite(destinationX, destinationY, regionWidth, regionHeight);
   }


   @Override
   final public void transferRegion(final int sourceX, final int sourceY, final int regionWidth, final int regionHeight, final int destinationX, final int destinationY,
                                       final boolean deferRedraw)
   {
      synchronized (ripplePropagationTask)
      {
         if (imageRippler != null)
            imageRippler.transferImagePixels(sourceX, sourceY, regionWidth, regionHeight, destinationX, destinationY);
      }

      if (! deferRedraw)
         redrawComposite(destinationX, destinationY, regionWidth, regionHeight);
   }


   @Override
   final public void regionCleared(final int regionX, final int regionY, final int regionWidth, final int regionHeight, final boolean deferRedraw)
   {
      synchronized (ripplePropagationTask)
      {
         if (imageRippler != null)
            imageRippler.clearInputImagePixels(regionX, regionY, regionWidth, regionHeight);
      }

      if (! deferRedraw)
         redrawComposite(regionX, regionY, regionWidth, regionHeight);
   }


   @Override
   final public void redrawRegion(final int regionX, final int regionY, final int regionWidth, final int regionHeight)
   {
      redrawComposite(regionX, regionY, regionWidth, regionHeight);
   }


   private void redrawComposite()
   {
      /* With the two-phase synchronous shutdown in mind, this should work fine IF preDispose() for this class is called on the first phase AND
       * asyncExec() submitted from thread A executes before syncExec() submitted from thread B.
       * The preDispose's call to the ripper task shutdown also invokes the executor's awaitTermination(), which guarantees that any lingering tasks must first
       * invoke this asyncExec() before the shutdown thread can continue.
       */
      composite.getDisplay().asyncExec(redrawTask);
   }


   private void redrawComposite(final int leftMostXPosition, final int upperMostYPosition, final int width, final int height)
   {
      composite.getDisplay().asyncExec(new Runnable()
      {
         @Override
         final public void run()
         {
            composite.redraw(leftMostXPosition, upperMostYPosition, width, height, false);
         }
      });
   }


   final public void preDispose()
   {
      handlePreDispose();
   }
}