/* Memos:
 * - Previously I'd noted that ComboBox and possibly other Swing components were only processing button press events generated using the older MouseEvent.xxx_MASK masks
 *   rather than the newer recommended MouseEvent.xxx_DOWN_MASK constants. However there seems to be no problem with them now...? I can always switch them back.
 *
 * - The mappings from SWT to Swing key codes are nowhere near complete - no mappings for function keys or numpad, caps lock, etc.
 */

package com.feedbactory.client.ui.component.graftable;


import com.feedbactory.client.ui.UIUtilities;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import org.eclipse.swt.SWT;



abstract public class GraftableSwingComponent<T extends Component> implements GraftableComponent
{
   // Currently there appears to be no equivalent Swing mapping for the SWT.BUTTON5, so at the moment I'm mapping it to MouseEvent.NOBUTTON.
   static final private int[] SWTToSwingButtonMaskMap = new int[] {MouseEvent.NOBUTTON, MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON2_DOWN_MASK, MouseEvent.BUTTON3_DOWN_MASK, MouseEvent.NOBUTTON};


   static protected int convertSWTToSwingButtonMask(final int swtButton)
   {
      return SWTToSwingButtonMaskMap[swtButton];
   }


   static protected boolean isSingleMouseButtonMask(final int swtEventStateMask)
   {
      final int swtMouseMask = swtEventStateMask & SWT.BUTTON_MASK;

      return (swtMouseMask == SWT.BUTTON1) || (swtMouseMask == SWT.BUTTON2) || (swtMouseMask == SWT.BUTTON3) || (swtMouseMask == SWT.BUTTON4) || (swtMouseMask == SWT.BUTTON5);
   }


   static protected int swtMouseMaskToSwingModifiersEx(final int swtMouseMask)
   {
      if (swtMouseMask == 0)
         return 0;
      else
      {
         int swingButtonMask = 0;

         if ((swtMouseMask & SWT.BUTTON1) != 0)
            swingButtonMask |= MouseEvent.BUTTON1_DOWN_MASK;

         if ((swtMouseMask & SWT.BUTTON2) != 0)
            swingButtonMask |= MouseEvent.BUTTON2_DOWN_MASK;

         if ((swtMouseMask & SWT.BUTTON3) != 0)
            swingButtonMask |= MouseEvent.BUTTON3_DOWN_MASK;

         return swingButtonMask;
      }
   }


   static protected int swtKeyMaskToSwingModifiersEx(final int swtKeyMask)
   {
      if (swtKeyMask == 0)
         return 0;
      else
      {
         int swingModifiersEx = 0;

         if ((swtKeyMask & SWT.ALT) != 0)
            swingModifiersEx |= KeyEvent.ALT_DOWN_MASK;

         if ((swtKeyMask & SWT.SHIFT) != 0)
            swingModifiersEx |= KeyEvent.SHIFT_DOWN_MASK;

         if ((swtKeyMask & SWT.CONTROL) != 0)
            swingModifiersEx |= KeyEvent.CTRL_DOWN_MASK;

         if ((swtKeyMask & SWT.COMMAND) != 0)
            swingModifiersEx |= KeyEvent.META_DOWN_MASK;

         return swingModifiersEx;
      }
   }


   static protected int swtKeyCodeToSwingKeyCode(final int swtKeyCode)
   {
      if ((swtKeyCode >= 'a') && (swtKeyCode <= 'z'))
         return swtKeyCode - 32;
      else
      {
         switch (swtKeyCode)
         {
            case SWT.ALT:
               return KeyEvent.VK_ALT;
            case SWT.CTRL:
               return KeyEvent.VK_CONTROL;
            case SWT.SHIFT:
               return KeyEvent.VK_SHIFT;
            case SWT.COMMAND:
               return KeyEvent.VK_META;
            case SWT.ARROW_LEFT:
               return KeyEvent.VK_LEFT;
            case SWT.ARROW_RIGHT:
               return KeyEvent.VK_RIGHT;
            case SWT.ARROW_UP:
               return KeyEvent.VK_UP;
            case SWT.ARROW_DOWN:
               return KeyEvent.VK_DOWN;
            case SWT.HOME:
               return KeyEvent.VK_HOME;
            case SWT.END:
               return KeyEvent.VK_END;
            case SWT.INSERT:
               return KeyEvent.VK_INSERT;
            case SWT.PAGE_UP:
               return KeyEvent.VK_PAGE_UP;
            case SWT.PAGE_DOWN:
               return KeyEvent.VK_PAGE_DOWN;
            case SWT.CR:
               return KeyEvent.VK_ENTER;

            default:
               return swtKeyCode;
         }
      }
   }


   static protected org.eclipse.swt.events.MouseEvent cloneSwtMouseEvent(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      final org.eclipse.swt.widgets.Event clonedMouseEvent = new org.eclipse.swt.widgets.Event();
      clonedMouseEvent.display = mouseEvent.display;
      clonedMouseEvent.widget = mouseEvent.widget;
      clonedMouseEvent.time = mouseEvent.time;
      clonedMouseEvent.data = mouseEvent.data;
      clonedMouseEvent.button = mouseEvent.button;
      clonedMouseEvent.count = mouseEvent.count;
      clonedMouseEvent.stateMask = mouseEvent.stateMask;
      clonedMouseEvent.x = mouseEvent.x;
      clonedMouseEvent.y = mouseEvent.y;

      return new org.eclipse.swt.events.MouseEvent(clonedMouseEvent);
   }


   // Swing EDT only.
   static protected void adjustMouseEventCoordinateForNestedComponent(final org.eclipse.swt.events.MouseEvent mouseEvent, final Component component)
   {
      assert SwingUtilities.isEventDispatchThread();
      mouseEvent.x -= component.getX();
      mouseEvent.y -= component.getY();
   }


   // Swing EDT only.
   static protected void adjustMouseEventCoordinateForNestedComponent(final org.eclipse.swt.events.MouseEvent mouseEvent, final Component parentComponent, final Component childComponent)
   {
      assert SwingUtilities.isEventDispatchThread();
      mouseEvent.x -= (parentComponent.getX() + childComponent.getX());
      mouseEvent.y -= (parentComponent.getY() + childComponent.getY());
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   protected GraftableComponentPeer graftableComponentPeer;
   protected BufferedImage outputImage;


   /****************************************************************************
    * 
    ***************************************************************************/


   abstract public T getDelegateComponent();


   protected BufferedImage createOutputImage(final int width, final int height)
   {
      return UIUtilities.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
   }


   protected void paintBackground(final Graphics2D graphics2D)
   {
      graphics2D.setComposite(AlphaComposite.Clear);
      graphics2D.fillRect(0, 0, outputImage.getWidth(), outputImage.getHeight());
      graphics2D.setComposite(AlphaComposite.SrcOver);
   }


   protected void paintComponent(final Graphics2D graphics2D)
   {
      getDelegateComponent().paint(graphics2D);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   public boolean isAttachedToPeer()
   {
      return (graftableComponentPeer != null);
   }


   @Override
   public void attachToPeer(final GraftableComponentPeer graftableComponentPeer)
   {
      if (this.graftableComponentPeer == graftableComponentPeer)
         return;
      else if (isAttachedToPeer())
         throw new IllegalArgumentException("The component is already attached to a peer. You must detach it first.");

      this.graftableComponentPeer = graftableComponentPeer;
   }


   @Override
   public void detachFromPeer()
   {
      this.graftableComponentPeer = null;
   }


   @Override
   public void receiveControlResizedEventFromSWTPeer(final org.eclipse.swt.events.ControlEvent controlEvent, final int width, final int height, final boolean deferRepaint)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveControlResizedEventEDT(width, height, deferRepaint);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveControlResizedEventEDT(width, height, deferRepaint);
            }
         });
   }


   private void receiveControlResizedEventEDT(final int width, final int height, final boolean deferRepaint)
   {
      final Component component = getDelegateComponent();

      /* If the component is nested within another GraftableComponent eg. GraftablePanel, which has been resized and laid out, it's possible that
       * this (child) component has already been resized, hence we check the existing dimensions.
       */
      if ((component.getWidth() != width) || (component.getHeight() != height))
         component.setSize(width, height);

      /* The above check to see whether the component has actually changed size doesn't absolve us of the need to repaint the component.
       * Since as already mentioned a Swing layout may have already automatically resized a nested Swing component outside of the graftable framework,
       * we can't rely on a check of the Swing bounds to determine whether or not the component has repainted itself to its graftable peer.
       * Hence the code block below is performed whether or not the Swing bounds have changed.
       */
      if ((width > 0) && (height > 0))
      {
         outputImage = createOutputImage(width, height);

         /* Note that deferring a repaint is different than deferring a peer redraw.
          * If we are only deferring a peer redraw (see repaint()) then we are updating the peer's image data, but we are instructing it to hold off on refreshing its
          * display to the user because there are more image updates to come.
          * 
          * Deferring a repaint means that we should not even update the peer's image data, most likely because a parent or repaint manager is taking care of
          * clearing and repainting the image data.
          */
         if (! deferRepaint)
            repaint(false);
      }
   }


   @Override
   public void receiveFocusGainedEventFromSWTPeer(final org.eclipse.swt.events.FocusEvent focusEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveFocusGainedEventEDT();
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveFocusGainedEventEDT();
            }
         });
   }


   private void receiveFocusGainedEventEDT()
   {
      final Component component = getDelegateComponent();
      component.dispatchEvent(new FocusEvent(component, FocusEvent.FOCUS_GAINED));
   }


   @Override
   public void receiveFocusLostEventFromSWTPeer(final org.eclipse.swt.events.FocusEvent focusEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveFocusLostEventEDT();
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveFocusLostEventEDT();
            }
         });
   }


   private void receiveFocusLostEventEDT()
   {
      final Component component = getDelegateComponent();
      component.dispatchEvent(new FocusEvent(component, FocusEvent.FOCUS_LOST));
   }


   @Override
   public void receiveMouseEnteredEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseEnteredEventEDT(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseEnteredEventEDT(mouseEvent);
            }
         });
   }


   private void receiveMouseEnteredEventEDT(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      final Component component = getDelegateComponent();
      // For mouse enter & exit events, Swing components will sometimes only respond when the state mask is empty.
      component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, mouseEvent.x, mouseEvent.y, 0, false));
   }


   @Override
   public void receiveMouseExitedEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseExitedEventEDT(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseExitedEventEDT(mouseEvent);
            }
         });
   }


   private void receiveMouseExitedEventEDT(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      final Component component = getDelegateComponent();
      // For mouse enter & exit events, Swing components will sometimes only respond when the state mask is empty.
      component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, mouseEvent.x, mouseEvent.y, 0, false));
   }


   @Override
   public void receiveMouseDownEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseDownEventEDT(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseDownEventEDT(mouseEvent);
            }
         });
   }


   private void receiveMouseDownEventEDT(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      final Component component = getDelegateComponent();
      component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), SWTToSwingButtonMaskMap[mouseEvent.button], mouseEvent.x, mouseEvent.y, mouseEvent.count, false, mouseEvent.button));
   }


   @Override
   public void receiveMouseUpEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseUpEventEDT(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseUpEventFromSWTPeer(mouseEvent);
            }
         });
   }


   private void receiveMouseUpEventEDT(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      final Component component = getDelegateComponent();
      component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), SWTToSwingButtonMaskMap[mouseEvent.button], mouseEvent.x, mouseEvent.y, mouseEvent.count, false, mouseEvent.button));
   }


   @Override
   public void receiveMouseMovedEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseMovedEventEDT(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseMovedEventEDT(mouseEvent);
            }
         });
   }


   private void receiveMouseMovedEventEDT(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      final Component component = getDelegateComponent();
      component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), swtMouseMaskToSwingModifiersEx(mouseEvent.stateMask), mouseEvent.x, mouseEvent.y, 0, false));
   }


   @Override
   public void receiveMouseDraggedEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseDraggedEventEDT(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseDraggedEventEDT(mouseEvent);
            }
         });
   }


   private void receiveMouseDraggedEventEDT(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      final Component component = getDelegateComponent();
      component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), swtMouseMaskToSwingModifiersEx(mouseEvent.stateMask), mouseEvent.x, mouseEvent.y, 0, false));
   }


   @Override
   public void receiveMouseWheelScrolledEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseWheelScrolledEventEDT(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseWheelScrolledEventEDT(mouseEvent);
            }
         });
   }


   private void receiveMouseWheelScrolledEventEDT(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      final Component component = getDelegateComponent();
      component.dispatchEvent(new MouseWheelEvent(component, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), swtMouseMaskToSwingModifiersEx(mouseEvent.stateMask), mouseEvent.x, mouseEvent.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, mouseEvent.count, (mouseEvent.count >= 0) ?  1 : -1));
   }


   @Override
   public void receiveKeyPressedEventFromSWTPeer(final org.eclipse.swt.events.KeyEvent keyEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveKeyPressedEventEDT(keyEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveKeyPressedEventEDT(keyEvent);
            }
         });
   }


   private void receiveKeyPressedEventEDT(final org.eclipse.swt.events.KeyEvent keyEvent)
   {
      final Component component = getDelegateComponent();

      if ((keyEvent.keyCode & SWT.MODIFIER_MASK) != 0)
      {
         /* SWT doesn't generate modifier masks for the modifier keys themselves (shift, alt, control), but Swing expects them in a KEY_PRESSED event,
          * so use the SWT keyCode itself to generate the expected Swing modifier mask.
          * Also Swing doesn't generate KEY_TYPED events for modifier keys, so that can be skipped here.
          */
         component.dispatchEvent(new KeyEvent(component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), swtKeyMaskToSwingModifiersEx(keyEvent.keyCode), swtKeyCodeToSwingKeyCode(keyEvent.keyCode), KeyEvent.CHAR_UNDEFINED));
      }
      else
      {
         component.dispatchEvent(new KeyEvent(component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), swtKeyMaskToSwingModifiersEx(keyEvent.stateMask), swtKeyCodeToSwingKeyCode(keyEvent.keyCode), KeyEvent.CHAR_UNDEFINED));

         /* Swing doesn't generate KEY_TYPED events for function keys such as.. well the function keys, Enter, arrow keys: that's the SWT.KEYCODE_BIT check.
          * Unfortunately Swing does send the event for some of the numpad keys, eg. numpad Enter, +, /, so they have to be handled separately.
          * I'm pretty sure that the keyLocation clause is letting through some key typed events that it shouldn't, but unless there are bad side effects it's probably fine.
          */
         if (((keyEvent.keyCode & SWT.KEYCODE_BIT) == 0) || (keyEvent.keyLocation == SWT.KEYPAD))
            component.dispatchEvent(new KeyEvent(component, KeyEvent.KEY_TYPED, System.currentTimeMillis(), swtKeyMaskToSwingModifiersEx(keyEvent.stateMask), KeyEvent.VK_UNDEFINED, keyEvent.character));
      }
   }


   @Override
   public void receiveKeyReleasedEventFromSWTPeer(final org.eclipse.swt.events.KeyEvent keyEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveKeyReleasedEventEDT(keyEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveKeyReleasedEventEDT(keyEvent);
            }
         });
   }


   private void receiveKeyReleasedEventEDT(final org.eclipse.swt.events.KeyEvent keyEvent)
   {
      final Component component = getDelegateComponent();

      // If the key pressed is a modifier (shift, alt, or control), the modifiers mask used by Swing on the KEY_RELEASED event is always empty.
      if ((keyEvent.keyCode & SWT.MODIFIER_MASK) == 0)
         component.dispatchEvent(new KeyEvent(component, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), swtKeyMaskToSwingModifiersEx(keyEvent.stateMask), swtKeyCodeToSwingKeyCode(keyEvent.keyCode), KeyEvent.CHAR_UNDEFINED));
      else
         component.dispatchEvent(new KeyEvent(component, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, swtKeyCodeToSwingKeyCode(keyEvent.keyCode), KeyEvent.CHAR_UNDEFINED));
   }


   @Override
   public void repaint()
   {
      repaint(false);
   }


   @Override
   public void repaint(final boolean deferPeerRedraw)
   {
      assert SwingUtilities.isEventDispatchThread();

      if (isAttachedToPeer() && (outputImage != null))
      {
         final Graphics2D graphics2D = outputImage.createGraphics();

         paintBackground(graphics2D);
         paintComponent(graphics2D);

         graphics2D.dispose();

         final Component component = getDelegateComponent();
         graftableComponentPeer.regionPainted(outputImage, component.getX(), component.getY(), deferPeerRedraw);
      }
   }


   @Override
   public void preDispose()
   {
   }
}