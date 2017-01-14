/* Memos:
 * - JLabel is subclassed instead of JComponent, for the convenience of being able to inherit some of the common text functionality such as
 *   the calculation of the preferred size and the baseline.
 *
 * - The animation in this class is structured quite differently to that of other animated components in Feedbactory. The reason essentially boils down
 *   to the need to measure the label text once the component has been sized and a graphics context is available. This is done within the paintComponent()
 *   method, and the unique twist is that the animation (if any) is actually kicked off from there. Why does it have to be done this way? Can't I just
 *   get a temporary Graphics instance via getGraphics at the time of calling showAnimatedText() and go from there? I originally did this but there is the
 *   problem that the component still needs to be resized before measuring the text, so as to correctly apply any '...' truncation. There is also the bigger
 *   problem that getGraphics() will return null when there is no peer context, eg. if the component has been temporarily removed from the visible hierarchy
 *   as is commonly the case within Feedbactory.
 *
 * - Another possibility is to initialise and start the animation at the time of the showAnimatedText() call, and then lazily compute the text metrics as/when
 *   the Graphics become available. But then.. does the animation task keep running periodically until the component is switched to the visible hierarchy and
 *   the Graphics become available? Or bail out instantly and simply set the text conventionally if the Graphics isn't available from its first iteration?
 *   When experimenting with the former approach I also stumbled across a subtle bug that convinced me to try a different way: if the component was animated
 *   (or at least the Executor was started) but not visible at the time of Feedbactory shutting down, the Executor will continue running because the repaint()
 *   would simply be ignored and the Graphics would therefore never be initialised. This is if the component was not disposed of properly at the time of shutdown.
 *
 * - An advantage of the current approach is that the animation is triggered when a control becomes visible, which produces a nice effect.
 *
 * - The basic gist is that when the label is to be animated, the visibleLabelText variable needs to be initialised (via a Graphics context). A null visibleLabelText
 *   is an indicator to the paintComponent() method that it is ready to be initialised for the animation. Once the animation has completed, visibleLabelText
 *   must be left as-is until the caller wishes to perform another animation. A non-null (including empty) visibleLabelText is an indicator to paintComponent()
 *   that the animation is already underway, has finished and shouldn't be restarted, or there is to be no animation to be performed; in the latter two cases,
 *   it entails deferring to the parent (JLabel's) paintComponent() for a static paint.
 */

package com.feedbactory.client.ui.component;


import com.feedbactory.client.core.ClientUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;


public class BouncingTextLabel extends JLabel
{
   static final private int[] textVerticalOffsets = {15, 14, 13, 12, 10, 9, 8, 6, 5, 4, 3, 1, 0, 0, -1, -2, -2, -3, -3, -3, -4, -4, -4, -4, -4, -3, -3, -3, -3, -2, -2, -2, -1, -1, -1, -1, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};

   /* 1/5/2016 - This field was previously marked as final, however this caused a serious problem due to the dead code analysis of ProGuard: the if-null condition in
    * the handleSetText() method is seemingly removed by ProGuard when the field is final, which causes the component to fail because the parent JLabel always calls setText()
    * on construction and this textBounceTask field is in fact always null during that small window of time.
    */
   private BouncingTextTask textBounceTask = new BouncingTextTask();

   /* Long text labels may be truncated with '...'. This variable will track the actual string displayed in the label.
    * This value may temporarily be out of step with the labelText string value, due to the need to wait until a
    * graphics context becomes available in paintComponent(), in order to generate the (possibly truncated) text.
    */
   private String visibleLabelText = "";


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class BouncingTextTask implements Runnable
   {
      static final private int AnimationFramesPerSecond = 140;

      private ScheduledExecutorService executor;

      private int textVerticalOffsetIndex;

      private boolean isDisposed;


      /****************************************************************************
       *
       ***************************************************************************/


      @Override
      final public void run()
      {
         Thread.currentThread().setName("BouncingTextLabel.BouncingTextTask");

         updateState();
      }


      synchronized private void updateState()
      {
         if ((! isAnimationActive()) || isDisposed)
            return;
         else if (needToInitialiseVisibleText())
         {
            /* If the visibleLabelText variable has not yet been initialised, bail out.
             * If the UI has shutdown immediately after kicking off this task without disposing of this component, or this component is not within the visible hierarchy,
             * the repaint() call will be ignored leading to this task potentially being needlessly called repeatedly while it's not visible.
             * It's better to simply kill off the task here and restart if/when a repaint() is successfully called.
             */
            finish();
            return;
         }

         textVerticalOffsetIndex ++;
         if (textVerticalOffsetIndex >= textVerticalOffsets.length)
            finish();

         repaint();
      }


      private void finish()
      {
         textVerticalOffsetIndex = 0;

         if (isAnimationActive())
         {
            executor.shutdown();
            executor = null;
         }
      }


      /****************************************************************************
       *
       ***************************************************************************/


      synchronized private boolean needToInitialiseVisibleText()
      {
         return (visibleLabelText == null);
      }


      synchronized private boolean isAnimationActive()
      {
         return (executor != null);
      }


      synchronized private int getTextVerticalOffsetIndex()
      {
         return textVerticalOffsetIndex;
      }


      synchronized private void resetAnimation()
      {
         visibleLabelText = null;
         textVerticalOffsetIndex = 0;
      }


      synchronized private void startAnimation()
      {
         if ((! isDisposed) && (! isAnimationActive()))
         {
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(this, 0, 1000 / AnimationFramesPerSecond, TimeUnit.MILLISECONDS);
         }
      }


      synchronized private void stopAnimation()
      {
         visibleLabelText = "";
         finish();
      }


      private void dispose()
      {
         final ExecutorService executorServiceReference;

         synchronized (this)
         {
            executorServiceReference = executor;
            isDisposed = true;
         }

         /* The awaitTermination() call must be made when the text jumper task lock is not being held,
          * since any lingering task will attempt to obtain the lock and must be permitted to
          * do so in order for it to finish.
          */
         if (executorServiceReference != null)
         {
            /* Ensure that even if all Swing repaint() requests haven't fully completed by the time this method returns,
             * there is at least the guarantee that there will be no further repaint() requests incoming.
             */
            ClientUtilities.shutdownAndAwaitTermination(executorServiceReference, "BouncingTextLabel.BouncingTextTask");
         }
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   @Override
   final public Dimension getPreferredSize()
   {
      final Dimension preferredSize = super.getPreferredSize();
      if ((preferredSize.width == 0) && (preferredSize.height == 0))
         return preferredSize;
      else
      {
         /* In theory a lot more leeway should be added to the height to account for the maximum bounce of the text (-4 pixels, so 8 pixels in total to ensure
          * enough space when the text is centred and there is an equal spread). However 2 pixels extra appears to be enough for the fonts that Feedbactory uses
          * without causing any clipping during the bounce, while not allowing labels to take up an unnecessary amount of vertical space.
          */
         preferredSize.height += 2;
         return preferredSize;
      }
   }


   @Override
   protected void paintComponent(final Graphics graphics)
   {
      final Graphics2D graphics2D = (Graphics2D) graphics;
      graphics2D.setFont(getFont());
      graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      final boolean isAnimating;
      final String visibleText;
      int characterYPositionIndex;

      synchronized (textBounceTask)
      {
         /* For efficiency during the animations, the (possibly truncated) text to be displayed is only calculated the first time.
          * However, perform the calculation for every regular repaint request so that the correct text can be displayed in the label, eg. after a resize
          * when the visible text has been reduced/truncated with '...'.
          */
         if (textBounceTask.needToInitialiseVisibleText())
         {
            initialiseVisibleLabelText(graphics2D);
            textBounceTask.startAnimation();
         }
         else if (! textBounceTask.isAnimationActive())
            initialiseVisibleLabelText(graphics2D);

         isAnimating = textBounceTask.isAnimationActive();
         visibleText = visibleLabelText;
         characterYPositionIndex = textBounceTask.getTextVerticalOffsetIndex();
      }

      if (isAnimating)
      {
         final int labelBaseline = getBaseline(getWidth(), getHeight());
         if (characterYPositionIndex < textVerticalOffsets.length)
         {
            final int blackAlphaValue = ((int) ((((float) characterYPositionIndex) / textVerticalOffsets.length) * 255)) << 24;
            graphics2D.setColor(new Color(blackAlphaValue, true));
            graphics2D.drawString(visibleText, 0, labelBaseline + textVerticalOffsets[characterYPositionIndex]);
         }
         else
         {
            graphics2D.setColor(Color.black);
            graphics2D.drawString(visibleText, 0, labelBaseline);
         }
      }
      else
         super.paintComponent(graphics);
   }


   private void initialiseVisibleLabelText(final Graphics2D graphics2D)
   {
      final FontMetrics fontMetrics = graphics2D.getFontMetrics();
      final Rectangle viewRectangle = getBounds();
      final Rectangle iconRectangle = new Rectangle();
      final Rectangle textRectangle = new Rectangle();

      visibleLabelText = SwingUtilities.layoutCompoundLabel(this, fontMetrics, getText(), null, SwingConstants.CENTER, SwingConstants.LEADING,
                                                            SwingConstants.CENTER, SwingConstants.TRAILING, viewRectangle, iconRectangle, textRectangle, 0);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSetText(final String text)
   {
      // This check must be performed because the JLabel constructor calls this method before this subclass has been initialised.
      if (textBounceTask != null)
         textBounceTask.stopAnimation();

      updateLabelText(text);
   }


   private void updateLabelText(final String text)
   {
      // JLabel's setText() won't trigger a repaint if the text is unchanged, have to force it.
      if (text.equals(getText()))
         repaint();
      else
         super.setText(text);
   }


   private void handleShowAnimatedText(final String text)
   {
      if ((text != null) && (! text.equals("")))
      {
         textBounceTask.resetAnimation();
         updateLabelText(text);
      }
      else
      {
         // Invoke this class' setText() which will first cancel any active animation before deferring to the superclass setText().
         setText(text);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public void setText(final String text)
   {
      handleSetText(text);
   }


   final public void showAnimatedText(final String text)
   {
      handleShowAnimatedText(text);
   }


   final public void dispose()
   {
      textBounceTask.dispose();
   }
}