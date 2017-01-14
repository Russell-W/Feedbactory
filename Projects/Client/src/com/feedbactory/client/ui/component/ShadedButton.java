/* Memos:
 * - The buttons are focusable by default, but not yet painted to indicate it.
 * - They can't belong to a button group, unless I create a custom button grouping class.
 * - Where gradients for painting the various button states are not supplied, the background colour is used.
 * - I may wish to consider allowing manual horizontal & vertical spacing, given that larger images when rotated during alerts will creep outside of the draw area.
 *
 * - The alert task in this class updates its state in a separate thread, and makes calls to repaint() as necessary. I'm
 *   assuming that the repaint() call ultimately results in an async request on the Swing EDT, rather than a sync request, otherwise deadlock would occur since
 *   the repaint() calls are made while the alert lock is being held, and the paintComponent() method (hopefully called asynchronously) is of course on the Swing
 *   EDT and attempts to get a lock on the alert task. Also see the important note in FeedbactoryBrowserWindow for why the repaint requests for animations must be
 *   made while either the same lock applied during dispose is held, or (as I've done with a lot of the other animation classes) simply do away with locks and
 *   execute the animation tasks straight on the Swing EDT. The shutdown/dispose request must then also be on the EDT, but this is the expected case anyway.
 */

package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.core.ClientUtilities;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.*;


public class ShadedButton extends ShadedComponent
{
   static final public int DefaultButtonCornerRadius = 10;

   static final public int HorizontalSpacing = 8;

   // Text-only buttons can look a little squished, and image-only buttons a little too tall, if using the same spacing rules for both button text and images.
   static final public int VerticalTextSpacing = 6;
   static final public int VerticalImageSpacing = 5;

   static final private Color DisabledOverlay = new Color(200, 200, 200, 120);

   final private int cornerRadius;

   private RadialGradientPaintProfile mouseOverGradient;
   private RadialGradientPaintProfile pressedGradient;
   private RadialGradientPaintProfile alertGradient;

   private BufferedImage buttonImage;
   private String buttonText;

   private ButtonState buttonState = ButtonState.Normal;

   final private AlertFlashTask alertFlashTask = new AlertFlashTask();

   private boolean isAlertAction;

   final private Set<ActionListener> registeredActionListeners = new CopyOnWriteArraySet<ActionListener>();


   public ShadedButton()
   {
      this(DefaultButtonCornerRadius);
   }


   public ShadedButton(final int cornerRadius)
   {
      super();

      validateCornerRadius(cornerRadius);

      this.cornerRadius = cornerRadius;

      initialise();
   }


   private void validateCornerRadius(final int cornerRadius)
   {
      if (cornerRadius < 0)
         throw new IllegalArgumentException("Rounded panel corner radius cannot be less than zero: " + cornerRadius);
   }


   private void initialise()
   {
      setFocusable(true);

      addMouseListener(new MouseAdapter()
      {
         @Override
         final public void mouseEntered(final MouseEvent mouseEvent)
         {
            handleMouseEntered();
         }


         @Override
         final public void mouseExited(final MouseEvent mouseEvent)
         {
            handleMouseExited();
         }


         @Override
         final public void mousePressed(final MouseEvent mouseEvent)
         {
            handleMousePressed(mouseEvent);
         }


         @Override
         final public void mouseReleased(final MouseEvent mouseEvent)
         {
            handleMouseReleased(mouseEvent);
         }
      });
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static private enum ButtonState
   {
      Normal,
      MouseOver,
      Pressed,
      Selected,
      Disabled,
      DisabledSelected;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static private enum AlertFlashState
   {
      Inactive,
      FlashingOn,
      FlashingOff,
      PauseBetweenFlashBursts,
      Disposed;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class AlertFlashTask implements Runnable
   {
      static final private long PauseBetweenFlashBurstsMilliseconds = 1000;
      static final private long FlashDurationMilliseconds = 200;
      static final private byte FlashBurstRounds = 2;
      static final private byte FlashesPerBurst = 3;

      private ScheduledExecutorService executorService;
      private Future<?> executorTask;

      private AlertFlashState flashState = AlertFlashState.Inactive;
      private byte flashBurstRound;
      private byte flashBurstCount;


      /****************************************************************************
       *
       ***************************************************************************/


      /* As per the doc on ScheduledExecutorService.scheduleAtFixedRate, tasks submitted to the method may NOT execute concurrently, hence we will never have a new task
       * blocked at the start of the run() method if the previous task consumes a lot of time. Hence a call to ScheduledFuture.cancel() to finish the animation will
       * ensure that no further invocations to run() will be performed.
       *
       * Also, refer to FeedbactoryBrowserWindow for an important note regarding the need for having the repaint call submitted while locks are being held, in the case of
       * independent (non-Swing) animation threads such as this.
       *
       * Repaint() is one of the few Swing calls that doesn't need to be invoked on the Swing thread.
       */
      @Override
      final public void run()
      {
         Thread.currentThread().setName("ShadedButton.AlertFlashTask");

         try
         {
            synchronized (this)
            {
               if (updateState())
               {
                  // Repaint() is one Swing call that supposedly does not need to be invoked on the EDT.
                  repaint();
               }
            }
         }
         catch (final Exception anyException)
         {
            /* Exception handling is performed this way since all of the animation calculations are performed on a separate, non-EDT thread,
             * which will not have the benefit of automatically invoking the default uncaught exception handler if there is a problem.
             */
            Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), anyException);

            // Propagate the exception to prevent further invocations of run() by scheduleAtFixedRate().
            throw new RuntimeException(anyException);
         }
      }


      private boolean updateState()
      {
         switch (flashState)
         {
            case Inactive:
            case Disposed:

               return false;

            case FlashingOn:

               flashBurstCount ++;

               if (flashBurstCount >= FlashesPerBurst)
               {
                  flashBurstRound ++;

                  if (flashBurstRound >= FlashBurstRounds)
                     finish();
                  else
                  {
                     flashState = AlertFlashState.PauseBetweenFlashBursts;

                     cancelActiveTask();
                     executorTask = executorService.schedule(this, PauseBetweenFlashBurstsMilliseconds, TimeUnit.MILLISECONDS);
                  }
               }
               else
                  flashState = AlertFlashState.FlashingOff;

               return true;

            case FlashingOff:

               flashState = AlertFlashState.FlashingOn;

               return true;

            case PauseBetweenFlashBursts:

               initialiseFlashBurstRound();
               executorTask = executorService.scheduleAtFixedRate(this, 0, FlashDurationMilliseconds, TimeUnit.MILLISECONDS);

               return true;

            default:
               throw new AssertionError("Unhandled shaped image button flash state: " + flashState);
         }
      }


      private void initialiseFlashBurstRound()
      {
         flashState = AlertFlashState.FlashingOff;
         flashBurstCount = 0;
      }


      private void finish()
      {
         stop();
      }


      /****************************************************************************
       *
       ***************************************************************************/


      synchronized private boolean isActive()
      {
         return (executorService != null);
      }


      synchronized private boolean isDisposed()
      {
         return (flashState == AlertFlashState.Disposed);
      }


      synchronized private void start()
      {
         if ((! isDisposed()) && (! isActive()))
         {
            flashBurstRound = 0;
            initialiseFlashBurstRound();

            executorService = Executors.newSingleThreadScheduledExecutor();
            executorTask = executorService.scheduleAtFixedRate(this, 0, FlashDurationMilliseconds, TimeUnit.MILLISECONDS);
         }
      }


      synchronized private void stop()
      {
         if ((! isDisposed()) && isActive())
         {
            cancelActiveTask();

            executorService.shutdown();
            executorService = null;

            flashState = AlertFlashState.Inactive;
         }
      }


      synchronized private void cancelActiveTask()
      {
         if (executorTask != null)
         {
            executorTask.cancel(false);
            executorTask = null;
         }
      }


      private void dispose()
      {
         final ExecutorService alertExecutorServiceReference;

         synchronized (this)
         {
            /* As per the docs, invoking shutdown on the executor will allow previously submitted tasks (not periodic tasks) to run before shutting down.
             * If the animation is taking the long pause between flash bursts, it's a good idea to try to cancel this task to avoid the unnecessary wait.
             */
            cancelActiveTask();

            alertExecutorServiceReference = executorService;
            flashState = AlertFlashState.Disposed;
         }

         /* The awaitTermination() call must be made when the alert task lock is not being held,
          * since any lingering wiggle or flash alert tasks will attempt to obtain the lock and must be permitted to
          * do so in order for their executor tasks to finish.
          */
         if (alertExecutorServiceReference != null)
         {
            /* Ensure that even if all Swing tasks haven't fully completed by the time this method returns,
             * there is at least the guarantee that any remaining calls to Swing's invokeLater have been made.
             */
            ClientUtilities.shutdownAndAwaitTermination(alertExecutorServiceReference, "ShadedButton.AlertTask");
         }
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static public interface ActionListener
   {
      public void actionPerformed(final ActionEvent actionEvent);
   }


   static final public class ActionEvent
   {
      final public ShadedButton sourceButton;
      final public boolean isAlertAction;


      private ActionEvent(final ShadedButton sourceButton, final boolean isAlertAction)
      {
         this.sourceButton = sourceButton;
         this.isAlertAction = isAlertAction;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void handleMouseEntered()
   {
      if (buttonState == ButtonState.Normal)
      {
         buttonState = ButtonState.MouseOver;
         repaint();
      }
   }


   private void handleMouseExited()
   {
      if (buttonState == ButtonState.MouseOver)
      {
         buttonState = ButtonState.Normal;
         repaint();
      }
   }


   private void handleMousePressed(final MouseEvent mouseEvent)
   {
      if ((buttonState == ButtonState.MouseOver) && (mouseEvent.getButton() == MouseEvent.BUTTON1))
      {
         buttonState = ButtonState.Pressed;

         // We need to remember whether the button was in an alert state at the time that it was pressed, since we are about to cancel the alert task (if active).
         isAlertAction = alertFlashTask.isActive();

         if (isAlertAction)
            alertFlashTask.stop();

         repaint();
      }
   }


   private void handleMouseReleased(final MouseEvent mouseEvent)
   {
      if ((buttonState == ButtonState.Pressed) && (mouseEvent.getButton() == MouseEvent.BUTTON1))
      {
         if (contains(mouseEvent.getPoint()))
         {
            buttonState = ButtonState.MouseOver;
            repaint();

            handleFireActionEvent();
         }
         else
         {
            buttonState = ButtonState.Normal;
            repaint();
         }
      }
   }


   private void handleFireActionEvent()
   {
      final ActionEvent actionEvent = new ActionEvent(this, isAlertAction);

      for (final ActionListener actionListener : registeredActionListeners)
         actionListener.actionPerformed(actionEvent);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   protected void paintComponent(final Graphics graphics)
   {
      final Graphics2D graphics2D = (Graphics2D) graphics.create();

      graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      switch (buttonState)
      {
         case Normal:
         case MouseOver:
            handlePaintNormalAndMouseOverButton(graphics2D);
            break;

         case Pressed:
         case Selected:
            handlePaintPressedButton(graphics2D);
            break;

         case Disabled:
         case DisabledSelected:
            handlePaintDisabledButton(graphics2D);
            break;

         default:
            throw new AssertionError("Unhandled shaded button paint state: " + buttonState);
      }

      graphics2D.dispose();
   }


   private void handlePaintNormalAndMouseOverButton(final Graphics2D graphics2D)
   {
      final boolean isAlertTaskActive;
      final boolean isAlertFlashing;

      synchronized (alertFlashTask)
      {
         isAlertTaskActive = alertFlashTask.isActive();
         isAlertFlashing = (alertFlashTask.flashState == AlertFlashState.FlashingOn);
      }

      if (isAlertTaskActive)
         handlePaintButtonWithGradientAndBorder(graphics2D, isAlertFlashing ? alertGradient : mouseOverGradient);
      else if (buttonState == ButtonState.Normal)
         handlePaintButtonForeground(graphics2D);
      else
      {
         assert (buttonState == ButtonState.MouseOver) : "Unhandled shaded button paint state in handlePaintNormalAndMouseOverButton: " + buttonState;
         handlePaintButtonWithGradientAndBorder(graphics2D, mouseOverGradient);
      }
   }


   private void handlePaintButtonWithGradientAndBorder(final Graphics2D graphics2D, final RadialGradientPaintProfile gradientPaintProfile)
   {
      super.setRadialGradientPaint(gradientPaintProfile);
      super.paintComponent(graphics2D);
      handlePaintButtonForeground(graphics2D);
      handlePaintMouseOverButtonBorder(graphics2D);
   }


   private void handlePaintPressedButton(final Graphics2D graphics2D)
   {
      super.setRadialGradientPaint(pressedGradient);
      super.paintComponent(graphics2D);
      handlePaintButtonForeground(graphics2D);
      handlePaintPressedButtonBorder(graphics2D);
   }


   private void handlePaintDisabledButton(final Graphics2D graphics2D)
   {
      final BufferedImage bufferedImage = UIUtilities.createCompatibleImage(getWidth(), getHeight(), Transparency.TRANSLUCENT);
      final Graphics2D imageGraphics = bufferedImage.createGraphics();

      imageGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      imageGraphics.setFont(graphics2D.getFont());

      if (buttonState == ButtonState.Disabled)
         handlePaintButtonForeground(imageGraphics);
      else
         handlePaintPressedButton(graphics2D);

      imageGraphics.setComposite(AlphaComposite.SrcAtop);
      imageGraphics.setColor(DisabledOverlay);
      imageGraphics.fillRect(0, 0, getWidth(), getHeight());

      imageGraphics.dispose();

      graphics2D.drawImage(bufferedImage, 0, 0, null);
   }


   private void handlePaintButtonForeground(final Graphics2D graphics2D)
   {
      handlePaintButtonImage(graphics2D);
      handlePaintButtonText(graphics2D);
   }


   private void handlePaintButtonImage(final Graphics2D graphics2D)
   {
      if (buttonImage != null)
      {
         final int imageStartY = (getHeight() - buttonImage.getHeight()) / 2;
         graphics2D.drawImage(buttonImage, HorizontalSpacing, imageStartY, null);
      }
   }


   private void handlePaintButtonText(final Graphics2D graphics2D)
   {
      if (buttonText != null)
      {
         graphics2D.setFont(getFont());
         final FontMetrics fontMetrics = graphics2D.getFontMetrics();
         final int textStartX;

         if (buttonImage != null)
            textStartX = (HorizontalSpacing * 2) + buttonImage.getWidth();
         else
            textStartX = HorizontalSpacing;

         final int textBaseline = (getHeight() - 1 + fontMetrics.getAscent()) / 2;

         graphics2D.setColor(Color.black);
         graphics2D.drawString(buttonText, textStartX, textBaseline);
      }
   }


   private void handlePaintMouseOverButtonBorder(final Graphics2D graphics2D)
   {
      final Color innerBorderColour;
      final Color outerBorderColour;

      if (mouseOverGradient != null)
      {
         innerBorderColour = UIUtilities.shiftColourBrightness(mouseOverGradient.getStartColour(), 0.1f);
         outerBorderColour = UIUtilities.shiftColourBrightness(mouseOverGradient.getEndColor(), -0.3f);
      }
      else
      {
         innerBorderColour = UIUtilities.shiftColourBrightness(getBackground(), 0.1f);
         outerBorderColour = UIUtilities.shiftColourBrightness(getBackground(), -0.3f);
      }

      graphics2D.setColor(innerBorderColour);
      graphics2D.drawRoundRect(0, 1, getWidth() - 1, getHeight() - 2, cornerRadius, cornerRadius);
      graphics2D.setColor(outerBorderColour);
      graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);
   }


   private void handlePaintPressedButtonBorder(final Graphics2D graphics2D)
   {
      final Color baseStartColour;
      final Color baseEndColour;

      if (pressedGradient != null)
      {
         baseStartColour = pressedGradient.getStartColour();
         baseEndColour = pressedGradient.getEndColor();
      }
      else
         baseStartColour = baseEndColour = getBackground();

      final Color outerBorderColour = UIUtilities.shiftColourBrightness(baseStartColour, 0.2f);
      final Color innerBorderColourOne = UIUtilities.shiftColourBrightness(baseEndColour, -0.3f);
      final Color innerBorderColourTwo = UIUtilities.shiftColourBrightness(baseEndColour, -0.2f);
      final Color innerBorderColourThree = UIUtilities.shiftColourBrightness(baseEndColour, -0.1f);

      graphics2D.setColor(outerBorderColour);
      graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);

      graphics2D.setColor(innerBorderColourThree);
      graphics2D.drawRoundRect(0, 2, getWidth() - 1, getHeight() - 4, cornerRadius, cornerRadius);
      graphics2D.setColor(innerBorderColourTwo);
      graphics2D.drawRoundRect(0, 1, getWidth() - 1, getHeight() - 3, cornerRadius, cornerRadius);
      graphics2D.setColor(innerBorderColourOne);
      graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 2, cornerRadius, cornerRadius);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private Dimension handleGetPreferredSize()
   {
      final Dimension preferredDimension = new Dimension(HorizontalSpacing, 0);

      if (buttonText != null)
      {
         final FontMetrics fontMetrics = getFontMetrics(getFont());

         preferredDimension.width += HorizontalSpacing + fontMetrics.stringWidth(buttonText);
         preferredDimension.height = (VerticalTextSpacing * 2) + fontMetrics.getHeight();
      }

      if (buttonImage != null)
      {
         preferredDimension.width += buttonImage.getWidth() + HorizontalSpacing;
         preferredDimension.height = Math.max(preferredDimension.height, (VerticalImageSpacing * 2) + buttonImage.getHeight()) ;
      }

      return preferredDimension;
   }


   private void handleSetEnabled(final boolean isEnabled)
   {
      if (isEnabled != isEnabled())
      {
         super.setEnabled(isEnabled);

         if (isEnabled)
            buttonState = (buttonState == ButtonState.DisabledSelected) ? ButtonState.Selected : ButtonState.Normal;
         else
            buttonState = (buttonState == ButtonState.Selected) ? ButtonState.DisabledSelected : ButtonState.Disabled;

         repaint();
      }
   }


   private boolean handleIsSelected()
   {
      return (buttonState == ButtonState.Selected) || (buttonState == ButtonState.DisabledSelected);
   }


   private void handleSetSelected(final boolean isSelected)
   {
      if (isSelected != isSelected())
      {
         if (isSelected)
            buttonState = (buttonState == ButtonState.Disabled) ? ButtonState.DisabledSelected : ButtonState.Selected;
         else
            buttonState = (buttonState == ButtonState.DisabledSelected) ? ButtonState.Disabled : ButtonState.Normal;
      }

      repaint();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final protected Shape getFillShape()
   {
      // For rounded rectangles a width/height of getWidth()/getHeight() will spill over the edges by one.
      return new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public void setRadialGradientPaint(final RadialGradientPaintProfile radialGradient)
   {
      throw new UnsupportedOperationException("Gradient paint must be specified for mouseover, pressed, or alert states.");
   }


   final public void setMouseOverRadialGradientPaint(final RadialGradientPaintProfile mouseOverGradient)
   {
      this.mouseOverGradient = mouseOverGradient;
   }


   final public void setPressedRadialGradientPaint(final RadialGradientPaintProfile pressedGradient)
   {
      this.pressedGradient = pressedGradient;
   }


   final public void setAlertRadialGradientPaint(final RadialGradientPaintProfile alertGradient)
   {
      this.alertGradient = alertGradient;
   }


   final public String getText()
   {
      return buttonText;
   }


   final public void setText(final String buttonText)
   {
      this.buttonText = buttonText;
   }


   final public void setImage(final BufferedImage buttonImage)
   {
      this.buttonImage = buttonImage;
   }


   @Override
   final public Dimension getPreferredSize()
   {
      return handleGetPreferredSize();
   }


   @Override
   final public void setEnabled(final boolean isEnabled)
   {
      handleSetEnabled(isEnabled);
   }


   final public boolean isSelected()
   {
      return handleIsSelected();
   }


   final public void setSelected(final boolean isSelected)
   {
      handleSetSelected(isSelected);
   }


   final public void addActionListener(final ActionListener actionListener)
   {
      registeredActionListeners.add(actionListener);
   }


   final public void removeActionListener(final ActionListener actionListener)
   {
      registeredActionListeners.remove(actionListener);
   }


   final public void showButtonAlert()
   {
      alertFlashTask.start();
   }


   final public void cancelButtonAlert()
   {
      alertFlashTask.stop();
   }


   final public void dispose()
   {
      alertFlashTask.dispose();
   }
}