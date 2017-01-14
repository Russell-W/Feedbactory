/* Memos:
 * - The golden rule for grafting between SWT and Swing: no synchronous calls from SWT to Swing.
 *
 * - Bug: On Windows at least, it seems that at least one key listener must be registered with a graftable SWT control for it to be able to properly handle gaining, losing
 *   and regaining the focus. This may or may not have anything to do with the DefaultKeyboardFocusManager issue (or similar) mentioned in GraftableComponentSwingFramework.
 *
 */

package com.feedbactory.client.ui.component.graftable;


import java.awt.image.BufferedImage;
import java.util.Set;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.events.MouseWheelListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;


final public class GraftableComponentSWTPeer implements GraftableComponentPeer
{
   final private Composite swtPeer;

   final private GraftableComponentPaintReceiver paintReceiver;

   private GraftableComponent topLevelGraftableComponent;

   private ControlListener controlEventListener;
   private FocusListener focusEventListener;
   private MouseListener mouseEventListener;
   private MouseMoveListener mouseMoveAndDragEventListener;
   private MouseTrackListener mouseTrackEventListener;
   private MouseWheelListener mouseWheelEventListener;
   private KeyListener keyEventListener;

   private boolean receiveMouseMoveEvents;
   private boolean receiveMouseDragEvents;


   public GraftableComponentSWTPeer(final Composite swtPeer, final GraftableComponentPaintReceiver paintReceiver)
   {
      if (swtPeer == null)
         throw new IllegalArgumentException("SWT peer cannot be null.");
      else if (paintReceiver == this)
         throw new IllegalArgumentException("Graftable component peer's paint receiver cannot be itself.");

      this.swtPeer = swtPeer;

      this.paintReceiver = paintReceiver;
   }


   @Override
   final public void attachComponent(final GraftableComponent graftableComponent)
   {
      if (swtPeer.getDisplay().getThread() == Thread.currentThread())
         attachComponentEDT(graftableComponent);
      else
      {
         swtPeer.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               attachComponentEDT(graftableComponent);
            }
         });
      }
   }


   private void attachComponentEDT(final GraftableComponent graftableComponent)
   {
      if (graftableComponent == null)
         throw new IllegalArgumentException("Top level component for SWT peer cannot be null.");
      else if (topLevelGraftableComponent == graftableComponent)
         return;
      else if (topLevelGraftableComponent != null)
         throw new IllegalArgumentException("A top level component has already been attached for this SWT peer. You must detach it first.");

      topLevelGraftableComponent = graftableComponent;

      registerEventListenersEDT(topLevelGraftableComponent, topLevelGraftableComponent.getEventsOfInterest());
   }


   @Override
   final public void detachComponent(final GraftableComponent graftableComponent)
   {
      if (swtPeer.getDisplay().getThread() == Thread.currentThread())
         detachComponentEDT(graftableComponent);
      else
      {
         swtPeer.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               detachComponentEDT(graftableComponent);
            }
         });
      }
   }


   private void detachComponentEDT(final GraftableComponent graftableComponent)
   {
      if (topLevelGraftableComponent == null)
         return;
      else if (graftableComponent != topLevelGraftableComponent)
         throw new IllegalArgumentException("Only top level component may register directly on the SWT peer for events.");

      deregisterEventListenersEDT(topLevelGraftableComponent, topLevelGraftableComponent.getEventsOfInterest());

      topLevelGraftableComponent = null;
   }


   @Override
   final public void registerEventListener(final GraftableComponent graftableComponent, final GraftableComponentEventType eventType)
   {
      if (swtPeer.getDisplay().getThread() == Thread.currentThread())
         registerEventListenerEDT(graftableComponent, eventType);
      else
      {
         swtPeer.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               registerEventListenerEDT(graftableComponent, eventType);
            }
         });
      }
   }


   private void registerEventListenerEDT(final GraftableComponent graftableComponent, final GraftableComponentEventType eventType)
   {
      checkTopLevelComponent(graftableComponent);
      registerEventListener(eventType);
   }


   private void checkTopLevelComponent(final GraftableComponent graftableComponent)
   {
      if (topLevelGraftableComponent == null)
         throw new IllegalStateException("No top level component has been attached.");
      else if (topLevelGraftableComponent != graftableComponent)
         throw new IllegalArgumentException("The component has not been attached to the SWT peer.");
   }


   @Override
   final public void registerEventListeners(final GraftableComponent graftableComponent, final Set<GraftableComponentEventType> eventTypes)
   {
      if (swtPeer.getDisplay().getThread() == Thread.currentThread())
         registerEventListenersEDT(graftableComponent, eventTypes);
      else
      {
         swtPeer.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               registerEventListenersEDT(graftableComponent, eventTypes);
            }
         });
      }
   }


   private void registerEventListenersEDT(final GraftableComponent graftableComponent, final Set<GraftableComponentEventType> eventTypes)
   {
      checkTopLevelComponent(graftableComponent);

      for (final GraftableComponentEventType eventType : eventTypes)
         registerEventListener(eventType);
   }


   @Override
   final public void deregisterEventListener(final GraftableComponent graftableComponent, final GraftableComponentEventType eventType)
   {
      if (swtPeer.getDisplay().getThread() == Thread.currentThread())
         deregisterEventListenerEDT(graftableComponent, eventType);
      else
      {
         swtPeer.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               deregisterEventListenerEDT(graftableComponent, eventType);
            }
         });
      }
   }


   private void deregisterEventListenerEDT(final GraftableComponent graftableComponent, final GraftableComponentEventType eventType)
   {
      checkTopLevelComponent(graftableComponent);

      deregisterEventListener(eventType);
   }


   @Override
   final public void deregisterEventListeners(final GraftableComponent graftableComponent, final Set<GraftableComponentEventType> eventTypes)
   {
      if (swtPeer.getDisplay().getThread() == Thread.currentThread())
         deregisterEventListenersEDT(graftableComponent, eventTypes);
      else
      {
         swtPeer.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               deregisterEventListenersEDT(graftableComponent, eventTypes);
            }
         });
      }
   }


   private void deregisterEventListenersEDT(final GraftableComponent graftableComponent, final Set<GraftableComponentEventType> eventTypes)
   {
      checkTopLevelComponent(graftableComponent);

      for (final GraftableComponentEventType eventType : eventTypes)
         deregisterEventListener(eventType);
   }


   private void registerEventListener(final GraftableComponentEventType eventType)
   {
      switch (eventType)
      {
         case Control:
            registerControlEventListener();
            break;
         case Focus:
            registerFocusEventListener();
            break;
         case MouseTrack:
            registerMouseTrackEventListener();
            break;
         case MouseMove:
            receiveMouseMoveEvents = true;
            registerMouseMoveAndDragEventListener();
            break;
         case MouseDrag:
            receiveMouseDragEvents = true;
            registerMouseMoveAndDragEventListener();
            break;
         case MouseClick:
            registerMouseClickEventListener();
            break;
         case MouseWheel:
            registerMouseWheelEventListener();
            break;
         case Key:
            registerKeyEventListener();
            break;
      }
   }


   private void deregisterEventListener(final GraftableComponentEventType eventType)
   {
      switch (eventType)
      {
         case Control:
            deregisterControlEventListener();
            break;
         case Focus:
            deregisterFocusEventListener();
            break;
         case MouseTrack:
            deregisterMouseTrackEventListener();
            break;
         case MouseMove:
            receiveMouseMoveEvents = false;
            deregisterMouseMoveAndDragEventListener();
            break;
         case MouseDrag:
            receiveMouseDragEvents = false;
            deregisterMouseMoveAndDragEventListener();
            break;
         case MouseClick:
            deregisterMouseClickEventListener();
            break;
         case MouseWheel:
            deregisterMouseWheelEventListener();
            break;
         case Key:
            deregisterKeyEventListener();
            break;
      }
   }


   private void registerControlEventListener()
   {
      if (controlEventListener != null)
         return;

      controlEventListener = new ControlAdapter()
      {
         @Override
         final public void controlResized(final ControlEvent controlEvent)
         {
            final Point swtPeerSize = swtPeer.getSize();
            topLevelGraftableComponent.receiveControlResizedEventFromSWTPeer(controlEvent, swtPeerSize.x, swtPeerSize.y, false);
         }
      };

      swtPeer.addControlListener(controlEventListener);
   }


   private void deregisterControlEventListener()
   {
      if (controlEventListener == null)
         return;

      swtPeer.removeControlListener(controlEventListener);
      controlEventListener = null;
   }


   private void registerFocusEventListener()
   {
      if (focusEventListener != null)
         return;

      focusEventListener = new FocusListener()
      {
         @Override
         final public void focusGained(final FocusEvent focusEvent)
         {
            topLevelGraftableComponent.receiveFocusGainedEventFromSWTPeer(focusEvent);
         }


         @Override
         final public void focusLost(final FocusEvent focusEvent)
         {
            topLevelGraftableComponent.receiveFocusLostEventFromSWTPeer(focusEvent);
         }
      };

      swtPeer.addFocusListener(focusEventListener);
   }


   private void deregisterFocusEventListener()
   {
      if (focusEventListener == null)
         return;

      swtPeer.removeFocusListener(focusEventListener);
      focusEventListener = null;
   }


   private void registerMouseClickEventListener()
   {
      if (mouseEventListener != null)
         return;

      mouseEventListener = new MouseAdapter()
      {
         @Override
         final public void mouseDown(final MouseEvent mouseEvent)
         {
            topLevelGraftableComponent.receiveMouseDownEventFromSWTPeer(mouseEvent);
         }


         @Override
         final public void mouseUp(final MouseEvent mouseEvent)
         {
            topLevelGraftableComponent.receiveMouseUpEventFromSWTPeer(mouseEvent);
         }
      };

      swtPeer.addMouseListener(mouseEventListener);
   }

   private void deregisterMouseClickEventListener()
   {
      if (mouseEventListener == null)
         return;

      swtPeer.removeMouseListener(mouseEventListener);
      mouseEventListener = null;
   }


   private void registerMouseMoveAndDragEventListener()
   {
      if (mouseMoveAndDragEventListener != null)
         return;

      mouseMoveAndDragEventListener = new MouseMoveListener()
      {
         @Override
         final public void mouseMove(final MouseEvent mouseEvent)
         {
            final boolean mouseDragged = (mouseEvent.stateMask & SWT.BUTTON_MASK) != 0;

            if (receiveMouseMoveEvents && (! mouseDragged))
               topLevelGraftableComponent.receiveMouseMovedEventFromSWTPeer(mouseEvent);
            else if (receiveMouseDragEvents && mouseDragged)
               topLevelGraftableComponent.receiveMouseDraggedEventFromSWTPeer(mouseEvent);
         }
      };

      swtPeer.addMouseMoveListener(mouseMoveAndDragEventListener);
   }


   private void deregisterMouseMoveAndDragEventListener()
   {
      if (mouseMoveAndDragEventListener == null)
         return;

      if ((! receiveMouseMoveEvents) && (! receiveMouseDragEvents))
      {
         swtPeer.removeMouseMoveListener(mouseMoveAndDragEventListener);
         mouseMoveAndDragEventListener = null;
      }
   }



   private void registerMouseTrackEventListener()
   {
      if (mouseTrackEventListener != null)
         return;

      mouseTrackEventListener = new MouseTrackAdapter()
      {
         @Override
         final public void mouseEnter(final MouseEvent mouseEvent)
         {
            topLevelGraftableComponent.receiveMouseEnteredEventFromSWTPeer(mouseEvent);
         }


         @Override
         final public void mouseExit(final MouseEvent mouseEvent)
         {
            topLevelGraftableComponent.receiveMouseExitedEventFromSWTPeer(mouseEvent);
         }
      };

      swtPeer.addMouseTrackListener(mouseTrackEventListener);
   }


   private void deregisterMouseTrackEventListener()
   {
      if (mouseTrackEventListener == null)
         return;

      swtPeer.removeMouseTrackListener(mouseTrackEventListener);
      mouseTrackEventListener = null;
   }


   private void registerMouseWheelEventListener()
   {
      if (mouseWheelEventListener != null)
         return;

      mouseWheelEventListener = new MouseWheelListener()
      {
         @Override
         final public void mouseScrolled(final MouseEvent mouseEvent)
         {
            topLevelGraftableComponent.receiveMouseWheelScrolledEventFromSWTPeer(mouseEvent);
         }
      };

      swtPeer.addMouseWheelListener(mouseWheelEventListener);
   }


   private void deregisterMouseWheelEventListener()
   {
      if (mouseWheelEventListener == null)
         return;

      swtPeer.removeMouseWheelListener(mouseWheelEventListener);
      mouseWheelEventListener = null;
   }


   private void registerKeyEventListener()
   {
      if (keyEventListener != null)
         return;

      keyEventListener = new KeyListener()
      {
         @Override
         final public void keyPressed(final KeyEvent keyEvent)
         {
            topLevelGraftableComponent.receiveKeyPressedEventFromSWTPeer(keyEvent);
         }


         @Override
         final public void keyReleased(final KeyEvent keyEvent)
         {
            topLevelGraftableComponent.receiveKeyReleasedEventFromSWTPeer(keyEvent);
         }
      };

      swtPeer.addKeyListener(keyEventListener);
   }


   private void deregisterKeyEventListener()
   {
      if (keyEventListener == null)
         return;

      swtPeer.removeKeyListener(keyEventListener);
      keyEventListener = null;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public void setPeerVisible(final boolean isVisible)
   {
      if (swtPeer.getDisplay().getThread() == Thread.currentThread())
         swtPeer.setVisible(isVisible);
      else
      {
         swtPeer.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               swtPeer.setVisible(isVisible);
            }
         });
      }
   }


   @Override
   final public void requestPeerFocus()
   {
      /* Not surprisingly, a more forceful focus set (than swtPeer.setFocus()) is required when our SWT peer has been created using SWT.NO_FOCUS.
       * I'm using SWT.NO_FOCUS because there seems to be no clean way to reject focus gained events, eg. when a user clicks
       * on a non-focusable control within an SWT peer, the focus will still be transferred to the parent peer, when we might like
       * to prevent this.
       */
      if (swtPeer.getDisplay().getThread() == Thread.currentThread())
         swtPeer.forceFocus();
      else
      {
         swtPeer.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               swtPeer.forceFocus();
            }
         });
      }
   }


   @Override
   final public void setPeerSize(final int width, final int height)
   {
      if (swtPeer.getDisplay().getThread() == Thread.currentThread())
         swtPeer.setSize(width, height);
      else
      {
         swtPeer.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               swtPeer.setSize(width, height);
            }
         });
      }
   }


   @Override
   final public void setPeerCursor(final GraftableComponentCursorType cursorType)
   {
      if (swtPeer.getDisplay().getThread() == Thread.currentThread())
         handleSetPeerCursor(cursorType);
      else
      {
         swtPeer.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               handleSetPeerCursor(cursorType);
            }
         });
      }
   }


   private void handleSetPeerCursor(final GraftableComponentCursorType cursorType)
   {
      switch (cursorType)
      {
         case Normal:
            swtPeer.setCursor(swtPeer.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
            break;
         case Busy:
            swtPeer.setCursor(swtPeer.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
            break;
         case Text:
            swtPeer.setCursor(swtPeer.getDisplay().getSystemCursor(SWT.CURSOR_IBEAM));
            break;
      }
   }


   @Override
   final public void setPeerToolTipText(final String toolTipText)
   {
      if (swtPeer.getDisplay().getThread() == Thread.currentThread())
         swtPeer.setToolTipText(toolTipText);
      else
      {
         swtPeer.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               swtPeer.setToolTipText(toolTipText);
            }
         });
      }
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public void regionPainted(final BufferedImage paintedImage, final int outputRegionUpperLeftX, final int outputRegionUpperLeftY, final boolean deferRedraw)
   {
      paintReceiver.regionPainted(paintedImage, outputRegionUpperLeftX, outputRegionUpperLeftY, deferRedraw);
   }


   @Override
   final public void regionPainted(final BufferedImage paintedImage, final int inputImageStartX, final int inputImageStartY,
                                      final int regionWidth, final int regionHeight, final int destinationX, final int destinationY, final boolean deferRedraw)
   {
      paintReceiver.regionPainted(paintedImage, inputImageStartX, inputImageStartY, regionWidth, regionHeight, destinationX, destinationY, deferRedraw);
   }


   @Override
   final public void transferRegion(final int sourceX, final int sourceY, final int regionWidth, final int regionHeight, final int destinationX, final int destinationY,
                                       final boolean deferRedraw)
   {
      paintReceiver.transferRegion(sourceX, sourceY, regionWidth, regionHeight, destinationX, destinationY, deferRedraw);
   }


   @Override
   final public void regionCleared(final int regionX, final int regionY, final int regionWidth, final int regionHeight, final boolean deferRedraw)
   {
      paintReceiver.regionCleared(regionX, regionY, regionWidth, regionHeight, deferRedraw);
   }


   @Override
   final public void redrawRegion(final int regionX, final int regionY, final int regionWidth, final int regionHeight)
   {
      paintReceiver.redrawRegion(regionX, regionY, regionWidth, regionHeight);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public Composite getPeer()
   {
      return swtPeer;
   }
}