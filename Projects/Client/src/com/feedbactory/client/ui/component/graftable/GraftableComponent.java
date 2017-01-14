
package com.feedbactory.client.ui.component.graftable;


import java.util.Set;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseEvent;


public interface GraftableComponent
{
   public Set<GraftableComponentEventType> getEventsOfInterest();

   public boolean isAttachedToPeer();
   public void attachToPeer(final GraftableComponentPeer graftableComponentPeer);
   public void detachFromPeer();

   public void receiveControlResizedEventFromSWTPeer(final ControlEvent controlEvent, final int width, final int height, final boolean suppressRepaint);

   public void receiveFocusGainedEventFromSWTPeer(final FocusEvent focusEvent);
   public void receiveFocusLostEventFromSWTPeer(final FocusEvent focusEvent);

   public void receiveMouseDownEventFromSWTPeer(final MouseEvent mouseEvent);
   public void receiveMouseUpEventFromSWTPeer(final MouseEvent mouseEvent);

   public void receiveMouseMovedEventFromSWTPeer(final MouseEvent mouseEvent);
   public void receiveMouseDraggedEventFromSWTPeer(final MouseEvent mouseEvent);

   public void receiveMouseEnteredEventFromSWTPeer(final MouseEvent mouseEvent);
   public void receiveMouseExitedEventFromSWTPeer(final MouseEvent mouseEvent);

   public void receiveMouseWheelScrolledEventFromSWTPeer(final MouseEvent mouseEvent);

   public void receiveKeyPressedEventFromSWTPeer(final KeyEvent keyEvent);
   public void receiveKeyReleasedEventFromSWTPeer(final KeyEvent keyEvent);

   public void repaint();
   public void repaint(final boolean deferPeerRedraw);

   public void preDispose();
}