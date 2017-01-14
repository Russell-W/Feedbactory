
package com.feedbactory.client.ui.component;


import com.feedbactory.client.core.ClientUtilities;
import com.feedbactory.client.ui.UIConstants;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;


final public class FrameResizeAnimator
{
   final private SwingNimbusFrame targetFrame;

   final private ResizeAnimationTask resizeAnimationTask = new ResizeAnimationTask();


   public FrameResizeAnimator(final SwingNimbusFrame targetFrame)
   {
      this.targetFrame = targetFrame;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   /* Override the methods that would provide unnecessary overhead for the temporary panel that's shown as the frame resizes.
    * Refer to DefaultTableCellRenderer, which applies the same optimisations for rubber-stamping a component into cells in a JTable.
    *
    * I initially also overrode the repaint() methods but (maybe not surprisingly) the animation seemed more jerky. The methods related
    * to laying out the panel can definitely be done away with though.
    */
   static final private class ResizeAnimationPanel extends JPanel
   {
      @Override
      final public void validate()
      {
      }


      @Override
      final public void invalidate()
      {
      }


      @Override
      final public void revalidate()
      {
      }


      @Override
      final protected void firePropertyChange(final String propertyName, final Object oldValue, final Object newValue)
      {
      }


      @Override
      final public void firePropertyChange(final String propertyName, final boolean oldValue, final boolean newValue)
      {
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class ResizeAnimationTask implements Runnable
   {
      static final private int AnimationFramesPerSecond = 120;
      static final private int MaximumFrameSizeIncrement = 20;
      static final private int MinimumFrameSizeIncrement = 2;
      static final private int DecelerationProximityThreshold = 100;
      static final private int DecelerationRate = 3;

      private Component originalFrameComponent;

      private int targetFrameWidth;
      private int targetFrameHeight;

      private int frameXVelocity;
      private int frameYVelocity;

      private ScheduledExecutorService executorService;

      private boolean isShutdown;


      private void handleAnimateToTargetSize()
      {
         originalFrameComponent = targetFrame.getContent();

         final JComponent resizeAnimationPanel = new ResizeAnimationPanel();
         resizeAnimationPanel.setBackground(UIConstants.LighterPanelColour);

         targetFrame.setContent(resizeAnimationPanel);

         frameXVelocity = calculateInitialVelocity(Math.abs(targetFrame.getWidth() - targetFrameWidth));
         frameYVelocity = calculateInitialVelocity(Math.abs(targetFrame.getHeight() - targetFrameHeight));

         executorService = Executors.newSingleThreadScheduledExecutor();
         executorService.scheduleAtFixedRate(this, 0, 1000 / AnimationFramesPerSecond, TimeUnit.MILLISECONDS);
      }


      private int calculateInitialVelocity(final int distance)
      {
         if (distance >= DecelerationProximityThreshold)
            return MaximumFrameSizeIncrement;
         else
         {
            // This is not nearly correct, but it's good enough.
            final int decelerations = (DecelerationProximityThreshold - distance) / MaximumFrameSizeIncrement;
            final int velocity = MaximumFrameSizeIncrement - (DecelerationRate * decelerations);
            return Math.max(MinimumFrameSizeIncrement, velocity);
         }
      }


      @Override
      final public void run()
      {
         Thread.currentThread().setName("FrameResizeAnimator.ResizeAnimationTask");

         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               if ((! isShutdown) && isExecutorServiceInitialised())
                  animateFrameSize();
            }
         });
      }


      private void animateFrameSize()
      {
         final int currentFrameWidth = targetFrame.getWidth();
         final int currentFrameHeight = targetFrame.getHeight();

         int adjustedWidth = currentFrameWidth;
         int adjustedHeight = currentFrameHeight;

         if (adjustedWidth < targetFrameWidth)
         {
            adjustedWidth += frameXVelocity;
            if (adjustedWidth > targetFrameWidth)
               adjustedWidth = targetFrameWidth;
            else if (((targetFrameWidth - adjustedWidth) < DecelerationProximityThreshold) && (frameXVelocity > MinimumFrameSizeIncrement))
               frameXVelocity = Math.max(MinimumFrameSizeIncrement, frameXVelocity - DecelerationRate);
         }
         else if (adjustedWidth > targetFrameWidth)
         {
            adjustedWidth -= frameXVelocity;
            if (adjustedWidth < targetFrameWidth)
               adjustedWidth = targetFrameWidth;
            else if (((adjustedWidth - targetFrameWidth) < DecelerationProximityThreshold) && (frameXVelocity > MinimumFrameSizeIncrement))
               frameXVelocity = Math.max(MinimumFrameSizeIncrement, frameXVelocity - DecelerationRate);
         }

         if (adjustedHeight < targetFrameHeight)
         {
            adjustedHeight += frameYVelocity;
            if (adjustedHeight > targetFrameHeight)
               adjustedHeight = targetFrameHeight;
            else if (((targetFrameHeight - adjustedHeight) < DecelerationProximityThreshold) && (frameYVelocity > MinimumFrameSizeIncrement))
               frameYVelocity = Math.max(MinimumFrameSizeIncrement, frameYVelocity - DecelerationRate);
         }
         else if (adjustedHeight > targetFrameHeight)
         {
            adjustedHeight -= frameYVelocity;
            if (adjustedHeight < targetFrameHeight)
               adjustedHeight = targetFrameHeight;
            else if (((adjustedHeight - targetFrameHeight) < DecelerationProximityThreshold) && (frameYVelocity > MinimumFrameSizeIncrement))
               frameYVelocity = Math.max(MinimumFrameSizeIncrement, frameYVelocity - DecelerationRate);
         }

         if ((adjustedWidth != currentFrameWidth) || (adjustedHeight != currentFrameHeight))
            targetFrame.setSize(adjustedWidth, adjustedHeight);
         else
            finishAnimateToTargetSize();
      }


      private void finishAnimateToTargetSize()
      {
         stopTask();

         targetFrame.setContent(originalFrameComponent);

         // Restore the focus but only if the parent window is already the focus owner.
         if (targetFrame.getContent().requestFocusInWindow())
            KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(targetFrame.getContent());
      }


      private void stopTask()
      {
         executorService.shutdown();
         executorService = null;
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private boolean isExecutorServiceInitialised()
      {
         assert SwingUtilities.isEventDispatchThread();

         return (executorService != null);
      }


      private void animateToSize(final int targetFrameWidth, final int targetFrameHeight)
      {
         assert SwingUtilities.isEventDispatchThread();

         this.targetFrameWidth = targetFrameWidth;
         this.targetFrameHeight = targetFrameHeight;

         if ((! isShutdown) && (! isExecutorServiceInitialised()) && (((targetFrame.getWidth() != targetFrameWidth) || (targetFrame.getHeight() != targetFrameHeight))))
            handleAnimateToTargetSize();
      }


      private void shutdown()
      {
         assert SwingUtilities.isEventDispatchThread();

         // Prevents further calls to animateToSize() from triggering the frame animation.
         isShutdown = true;

         if (isExecutorServiceInitialised())
         {
            /* Ensure that even if all Swing tasks haven't fully completed by the time this method returns,
             * there is at least the guarantee that any remaining calls to Swing's invokeLater have been made.
             */
            ClientUtilities.shutdownAndAwaitTermination(executorService, "FrameResizeAnimator.ResizeAnimationTask");
         }
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final public void animateToSize(final int targetFrameWidth, final int targetFrameHeight)
   {
      resizeAnimationTask.animateToSize(targetFrameWidth, targetFrameHeight);
   }


   final public void shutdown()
   {
      resizeAnimationTask.shutdown();
   }
}