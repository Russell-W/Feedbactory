/* LIMITATIONS:
 * - The caret blinking doesn't work as well as the regular Swing handling, especially once the caret moves around. Swing always paints the new caret position, we don't.
 */


package com.feedbactory.client.ui.component.graftable;


import com.feedbactory.client.core.ClientUtilities;
import com.feedbactory.client.ui.UIConstants;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.Control;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.Focus;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.Key;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.MouseClick;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.MouseDrag;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.MouseTrack;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;


final public class GraftableTextField extends GraftableSwingComponent<JTextField>
{
   static final private Set<GraftableComponentEventType> EventsOfInterest = Collections.unmodifiableSet(EnumSet.of(Control, Focus, MouseTrack, MouseClick, MouseDrag, Key));

   static final private Point InnerContentPaintOffset = new Point(3, 3);

   final private GraftableTextFieldDelegate delegate = new GraftableTextFieldDelegate();

   final private boolean isComboBoxChild;

   private Rectangle innerContentPaintBounds;

   final private CaretPainterTask caretPainterTask = new CaretPainterTask();


   public GraftableTextField()
   {
      this(false);
   }


   public GraftableTextField(final boolean isComboBoxChild)
   {
      this.isComboBoxChild = isComboBoxChild;

      initialise();
   }


   private void initialise()
   {
      initialiseDelegate();
   }


   private void initialiseDelegate()
   {
      // Thanks Swing, we'll handle it from here...
      delegate.getCaret().setBlinkRate(0);
      delegate.setCaretColor(UIConstants.ClearColour);

      initialiseDirtyImageListeners();
   }


   private void initialiseDirtyImageListeners()
   {
      delegate.addFocusListener(new FocusListener()
      {
         @Override
         final public void focusGained(final FocusEvent focusEvent)
         {
            if (! isComboBoxChild)
               repaint(false, true);

            caretPainterTask.start();
         }


         @Override
         final public void focusLost(final FocusEvent focusEvent)
         {
            if (! isComboBoxChild)
               repaint(false, true);

            caretPainterTask.stop();
         }
      });

      delegate.getDocument().addDocumentListener(new DocumentListener()
      {
         @Override
         final public void insertUpdate(final DocumentEvent documentEvent)
         {
            repaint(false, false);
         }


         @Override
         final public void removeUpdate(final DocumentEvent documentEvent)
         {
            repaint(false, false);
         }


         @Override
         final public void changedUpdate(final DocumentEvent documentEvent)
         {
            repaint(false, false);
         }
      });

      delegate.addCaretListener(new CaretListener()
      {
         @Override
         final public void caretUpdate(final CaretEvent caretEvent)
         {
            repaint(false, false);
         }
      });

      delegate.addKeyListener(new KeyAdapter()
      {
         // This extra kick is needed to jump to the text field beginning or end, might have more to do with the paint timing than anything else.
         @Override
         final public void keyReleased(final KeyEvent keyEvent)
         {
            final int keyCode = keyEvent.getKeyCode();
            if ((keyCode == KeyEvent.VK_HOME) || (keyCode == KeyEvent.VK_END))
               repaint(false, false);
         }


         @Override
         final public void keyTyped(final KeyEvent keyEvent)
         {
            repaint(false, false);
         }
      });

      delegate.addMouseMotionListener(new MouseMotionAdapter()
      {
         @Override
         final public void mouseDragged(final MouseEvent mouseEvent)
         {
            repaint(false, false);
         }
      });
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class GraftableTextFieldDelegate extends JTextField
   {
      @Override
      final public boolean isShowing()
      {
         return true;
      }


      @Override
      final protected void paintBorder(final Graphics graphics)
      {
         super.paintBorder(graphics);
      }


      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         super.paintComponent(graphics);
      }


      // Methods overriden to prevent needless calls to RepaintManager - we're doing our own painting.
      @Override
      final public void repaint()
      {
      }


      @Override
      final public void repaint(final long milliseconds)
      {
      }


      @Override
      final public void repaint(final int x, final int y, final int width, final int height)
      {
      }


      @Override
      final public void repaint(final Rectangle repaintRectangle)
      {
      }


      @Override
      final public void repaint(final long milliseconds, final int x, final int y, final int width, final int height)
      {
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class CaretPainterTask implements Runnable
   {
      static final private int CaretBlinkRateMilliseconds = 400;

      private ScheduledExecutorService executorService;
      final private Runnable blinkCaretRunnable = createSwingCaretBlinkRunnable();
      private boolean isShutdown;


      private Runnable createSwingCaretBlinkRunnable()
      {
         return new Runnable()
         {
            @Override
            final public void run()
            {
               if ((! isActive()) || isShutdown)
                  return;

               try
               {
                  final Rectangle caretPaintRegion = delegate.modelToView(delegate.getCaretPosition());

                  // Returned null on an occasion when the window minimum size was set to well beyond the screen size.
                  if (caretPaintRegion != null)
                     paintCursor(caretPaintRegion.x, caretPaintRegion.y, caretPaintRegion.height);
               }
               catch (final BadLocationException badLocationException)
               {
                  stop();
                  throw new RuntimeException(badLocationException);
               }
            }
         };
      }


      @Override
      final public void run()
      {
         Thread.currentThread().setName("GraftableTextField.CaretPainterTask");
         SwingUtilities.invokeLater(blinkCaretRunnable);
      }


      private boolean isActive()
      {
         return (executorService != null);
      }


      private void start()
      {
         if ((! isActive()) && (! isShutdown))
         {
            executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.scheduleAtFixedRate(this, 0, CaretBlinkRateMilliseconds, TimeUnit.MILLISECONDS);
         }
      }


      private void stop()
      {
         if (isActive())
         {
            executorService.shutdown();
            executorService = null;
         }
      }


      private void shutdown()
      {
         isShutdown = true;

         if (isActive())
         {
            /* Ensure that even if all Swing tasks haven't completed by the time this method returns,
             * there is at least the guarantee that any remaining calls to Swing's invokeLater have been made.
             */
            ClientUtilities.shutdownAndAwaitTermination(executorService, "GraftableTextField.CaretPainterTask");
         }
      }
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public void receiveControlResizedEventFromSWTPeer(final org.eclipse.swt.events.ControlEvent controlEvent, final int width, final int height, final boolean deferRepaint)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveControlResizedEventEDT(controlEvent, width, height, deferRepaint);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveControlResizedEventEDT(controlEvent, width, height, deferRepaint);
            }
         });
   }


   private void receiveControlResizedEventEDT(final org.eclipse.swt.events.ControlEvent controlEvent, final int width, final int height, final boolean deferRepaint)
   {
      super.receiveControlResizedEventFromSWTPeer(controlEvent, width, height, deferRepaint);

      if ((width > 0) && (height > 0))
      {
         outputImage = createOutputImage(width, height);
         innerContentPaintBounds = new Rectangle(delegate.getX() + InnerContentPaintOffset.x, delegate.getY() + InnerContentPaintOffset.y, width - (InnerContentPaintOffset.x * 2), height - (InnerContentPaintOffset.y * 2));

         // See GraftableSwingComponent for a note regarding the different beteen defer redraw and defer repaint.
         if (! deferRepaint)
            repaint(false, true);
      }
   }


   @Override
   final public JTextField getDelegateComponent()
   {
      return delegate;
   }


   @Override
   final public Set<GraftableComponentEventType> getEventsOfInterest()
   {
      return EventsOfInterest;
   }


   @Override
   final public void receiveMouseEnteredEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      setPeerCursor(GraftableComponentCursorType.Text);
   }


   private void setPeerCursor(final GraftableComponentCursorType cursorType)
   {
      // graftableComponentPeer must be accessed on the Swing thread.
      if (SwingUtilities.isEventDispatchThread())
         graftableComponentPeer.setPeerCursor(cursorType);
      else
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               graftableComponentPeer.setPeerCursor(cursorType);
            }
         });
      }
   }


   @Override
   final public void receiveMouseExitedEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      setPeerCursor(GraftableComponentCursorType.Normal);
   }


   @Override
   final public void repaint(final boolean deferPeerRedraw)
   {
      repaint(true, deferPeerRedraw);
   }


   private void repaint(final boolean deferPeerRedraw, final boolean repaintBorder)
   {
      assert SwingUtilities.isEventDispatchThread();

      if ((! isAttachedToPeer()) || (outputImage == null))
         return;

      final Graphics2D graphics2D = outputImage.createGraphics();

      if (! isComboBoxChild)
         paintBackground(graphics2D);

      delegate.paintComponent(graphics2D);
      if (repaintBorder)
         delegate.paintBorder(graphics2D);

      graphics2D.dispose();

      // If we're only updating the text area, or our text field is embedded within a combo box, don't overrite the combo's focus painting around the border.
      if ((! repaintBorder) || isComboBoxChild)
         graftableComponentPeer.regionPainted(outputImage, InnerContentPaintOffset.x, InnerContentPaintOffset.y,
                                              innerContentPaintBounds.width, innerContentPaintBounds.height, innerContentPaintBounds.x, innerContentPaintBounds.y, deferPeerRedraw);
      else
         graftableComponentPeer.regionPainted(outputImage, delegate.getX(), delegate.getY(), deferPeerRedraw);
   }


   private void paintCursor(final int cursorXPosition, final int cursorYPosition, final int cursorHeight)
   {
      if ((! isAttachedToPeer()) || (outputImage == null))
         return;

      final Graphics2D graphics2D = outputImage.createGraphics();
      graphics2D.setXORMode(Color.black);
      graphics2D.drawLine(cursorXPosition, cursorYPosition, cursorXPosition, cursorYPosition + cursorHeight - 1);
      graphics2D.dispose();

      graftableComponentPeer.regionPainted(outputImage, cursorXPosition, cursorYPosition, 1, cursorHeight, delegate.getX() + cursorXPosition, delegate.getY() + cursorYPosition, false);
   }


   @Override
   final public void preDispose()
   {
      caretPainterTask.shutdown();
   }
}