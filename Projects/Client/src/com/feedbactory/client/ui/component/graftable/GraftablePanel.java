/* Memos:
 * - Our repainting scheme (see receiveControlResizedEventFromSWTPeer()) after resizing only caters for simple layouts, specifically where child components do not overlap.
 *
 */


package com.feedbactory.client.ui.component.graftable;


import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.Control;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.Focus;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.Key;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.MouseClick;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.MouseDrag;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.MouseMove;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.MouseTrack;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.MouseWheel;
import java.awt.Component;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;


final public class GraftablePanel extends GraftableSwingComponent<JPanel> implements GraftableComponentPeer
{
   static final private GraftableComponentNode NullEventSubscription = new GraftableComponentNode(null, EnumSet.noneOf(GraftableComponentEventType.class));

   final private JPanel delegate = new GraftablePanelDelegate();

   final private Map<GraftableComponent, GraftableComponentNode> registeredComponents = new LinkedHashMap<GraftableComponent, GraftableComponentNode>();

   final private Map<GraftableComponentEventType, Integer> eventSubscriptionCounts = new EnumMap<GraftableComponentEventType, Integer>(GraftableComponentEventType.class);

   final private List<GraftableComponentNode> focusListeners = new ArrayList<GraftableComponentNode>();
   final private List<GraftableComponentNode> mouseTrackAndMotionListeners = new ArrayList<GraftableComponentNode>();

   private boolean hasFocus;
   private GraftableComponentNode focusedComponent = NullEventSubscription;
   private GraftableComponentNode lastMouseRegionComponent = NullEventSubscription;

   private GraftableComponentNode defaultFocusComponent = NullEventSubscription;


   public GraftablePanel()
   {
      initialise();
   }


   private void initialise()
   {
      initialiseEventSubscriptionCounts();
   }


   private void initialiseEventSubscriptionCounts()
   {
      eventSubscriptionCounts.put(Control, 0);
      eventSubscriptionCounts.put(Focus, 0);
      eventSubscriptionCounts.put(MouseTrack, 0);
      eventSubscriptionCounts.put(MouseMove, 0);
      eventSubscriptionCounts.put(MouseDrag, 0);
      eventSubscriptionCounts.put(MouseClick, 0);
      eventSubscriptionCounts.put(MouseWheel, 0);
      eventSubscriptionCounts.put(Key, 0);
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final private class GraftablePanelDelegate extends JPanel
   {
      private GraftablePanelDelegate()
      {
         /* By default, JFrames are double buffered, but we don't want any double buffering to occur from the Swing end since we are painting the components to image buffers anyway.
          * The double buffering must be disabled from the top of the component hierarchy to prevent components lower in the hierarchy from being double buffered.
          * See GraftableComponentSwingFramework.
          */
         super(null, false);
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


   static final private class GraftableComponentNode
   {
      final GraftableSwingComponent graftableComponent;

      final boolean receivesControlEvents;
      final boolean receivesFocusEvents;
      final boolean receivesMouseTrackEvents;
      final boolean receivesMouseMotionEvents;
      final boolean receivesMouseDragEvents;
      final boolean receivesMouseClickEvents;
      final boolean receivesMouseWheelEvents;
      final boolean receivesKeyEvents;

      Rectangle lastResizeBounds;


      private GraftableComponentNode(final GraftableSwingComponent graftableComponent, final Set<GraftableComponentEventType> eventTypes)
      {
         this.graftableComponent = graftableComponent;

         receivesControlEvents = eventTypes.contains(Control);
         receivesFocusEvents = eventTypes.contains(Focus);
         receivesMouseTrackEvents = eventTypes.contains(MouseTrack);
         receivesMouseMotionEvents = eventTypes.contains(MouseMove);
         receivesMouseDragEvents = eventTypes.contains(MouseDrag);
         receivesMouseClickEvents = eventTypes.contains(MouseClick);
         receivesMouseWheelEvents = eventTypes.contains(MouseWheel);
         receivesKeyEvents = eventTypes.contains(Key);
      }


      private Set<GraftableComponentEventType> toSet()
      {
         final Set<GraftableComponentEventType> set = EnumSet.noneOf(GraftableComponentEventType.class);

         if (receivesControlEvents)
            set.add(Control);

         if (receivesFocusEvents)
            set.add(Focus);

         if (receivesMouseTrackEvents)
            set.add(MouseTrack);

         if (receivesMouseMotionEvents)
            set.add(MouseMove);

         if (receivesMouseDragEvents)
            set.add(MouseDrag);

         if (receivesMouseClickEvents)
            set.add(MouseClick);

         if (receivesMouseWheelEvents)
            set.add(MouseWheel);

         if (receivesKeyEvents)
            set.add(Key);

         return set;
      }


      private void setLastResizeBounds(final Rectangle rectangle)
      {
         lastResizeBounds = rectangle;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   @Override
   final public void attachComponent(final GraftableComponent graftableComponent)
   {
      assert SwingUtilities.isEventDispatchThread();

      if (! (graftableComponent instanceof GraftableSwingComponent))
         throw new IllegalArgumentException("Only Swing components may register for events on a GraftablePanel.");
      else if (registeredComponents.containsKey(graftableComponent))
         return;

      final GraftableSwingComponent graftableSwingComponent = (GraftableSwingComponent) graftableComponent;

      registeredComponents.put(graftableComponent, new GraftableComponentNode(graftableSwingComponent, EnumSet.noneOf(GraftableComponentEventType.class)));

      registerEventListeners(graftableComponent, graftableComponent.getEventsOfInterest());

      graftableComponent.receiveControlResizedEventFromSWTPeer(null, graftableSwingComponent.getDelegateComponent().getWidth(), graftableSwingComponent.getDelegateComponent().getHeight(), false);
   }


   @Override
   final public void detachComponent(final GraftableComponent graftableComponent)
   {
      assert SwingUtilities.isEventDispatchThread();

      if (registeredComponents.containsKey(graftableComponent))
      {
         deregisterEventListeners(graftableComponent, graftableComponent.getEventsOfInterest());

         registeredComponents.remove(graftableComponent);
      }
   }


   private void refreshEventSubscriptions(final GraftableSwingComponent graftableComponent, final GraftableComponentNode oldEventSubscriptions, final Set<GraftableComponentEventType> newEventTypes)
   {
      if (oldEventSubscriptions != null)
      {
         if (oldEventSubscriptions.receivesFocusEvents)
            focusListeners.remove(oldEventSubscriptions);

         if (oldEventSubscriptions.receivesMouseTrackEvents || oldEventSubscriptions.receivesMouseMotionEvents)
            mouseTrackAndMotionListeners.remove(oldEventSubscriptions);
      }

      final GraftableComponentNode newEventSubscriptions = new GraftableComponentNode(graftableComponent, newEventTypes);
      registeredComponents.put(graftableComponent, newEventSubscriptions);

      if (newEventTypes.contains(Focus))
         focusListeners.add(newEventSubscriptions);

      if (newEventTypes.contains(MouseTrack) || newEventTypes.contains(MouseMove))
         mouseTrackAndMotionListeners.add(newEventSubscriptions);
   }


   private void checkClearComponentEventState(final GraftableSwingComponent graftableComponent, final GraftableComponentNode oldEventSubscriptions, final Set<GraftableComponentEventType> newEventTypes)
   {
      if (oldEventSubscriptions != null)
      {
         if (oldEventSubscriptions.receivesFocusEvents && (! newEventTypes.contains(Focus)))
         {
            if (hasFocus && (focusedComponent.graftableComponent == graftableComponent))
            {
               hasFocus = false;
               focusedComponent = NullEventSubscription;
            }

            if ((defaultFocusComponent != NullEventSubscription) && (defaultFocusComponent.graftableComponent == graftableComponent))
               defaultFocusComponent = NullEventSubscription;
         }

         if (oldEventSubscriptions.receivesMouseTrackEvents && (lastMouseRegionComponent.graftableComponent == graftableComponent) &&
             (! newEventTypes.contains(MouseTrack)) && (! newEventTypes.contains(MouseMove)))
            lastMouseRegionComponent = NullEventSubscription;
      }
   }


   @Override
   final public void registerEventListener(final GraftableComponent graftableComponent, final GraftableComponentEventType newEventType)
   {
      assert SwingUtilities.isEventDispatchThread();

      if (! registeredComponents.containsKey(graftableComponent))
         throw new IllegalArgumentException("Component is not attached to this peer.");

      final GraftableComponentNode currentEventSubscriptions = registeredComponents.get(graftableComponent);
      final Set<GraftableComponentEventType> eventsSet = currentEventSubscriptions.toSet();

      if (eventsSet.contains(newEventType))
         return;

      registerEventListenerHandler(newEventType);
      eventsSet.add(newEventType);

      refreshEventSubscriptions((GraftableSwingComponent) graftableComponent, currentEventSubscriptions, eventsSet);
   }


   @Override
   final public void registerEventListeners(final GraftableComponent graftableComponent, final Set<GraftableComponentEventType> newEventTypes)
   {
      assert SwingUtilities.isEventDispatchThread();

      if (! registeredComponents.containsKey(graftableComponent))
         throw new IllegalArgumentException("Component is not attached to this peer.");

      final GraftableComponentNode currentEventSubscriptions = registeredComponents.get(graftableComponent);
      final Set<GraftableComponentEventType> eventsSet = currentEventSubscriptions.toSet();

      boolean isEventSubscriptionUpdated = false;
      for (final GraftableComponentEventType newEventType : newEventTypes)
      {
         if (! eventsSet.contains(newEventType))
         {
            registerEventListenerHandler(newEventType);
            eventsSet.add(newEventType);
            isEventSubscriptionUpdated = true;
         }
      }

      if (isEventSubscriptionUpdated)
         refreshEventSubscriptions((GraftableSwingComponent) graftableComponent, currentEventSubscriptions, eventsSet);
   }


   private void registerEventListenerHandler(final GraftableComponentEventType eventTypeToRegister)
   {
      final int countOfEventSubscribers;

      synchronized (eventSubscriptionCounts)
      {
         countOfEventSubscribers = eventSubscriptionCounts.get(eventTypeToRegister) + 1;

         eventSubscriptionCounts.put(eventTypeToRegister, countOfEventSubscribers);
      }

      /* Perform the call to the peer outside of the lock, to avoid potential deadlock and bad practice in general. This is confirmed as a problem if an SWT peer attaches/detaches
       * a component and makes a call to getEventsOfInterest() (which requires the lock) on the SWT thread at the same time as our graftableComponentPeer.registerEventListener()
       * tries to invokeAndWait() on the SWT thread.
       */
      if ((countOfEventSubscribers == 1) && (graftableComponentPeer != null))
         graftableComponentPeer.registerEventListener(this, eventTypeToRegister);
   }
   

   @Override
   final public void deregisterEventListener(final GraftableComponent graftableComponent, final GraftableComponentEventType eventTypeToDeregister)
   {
      assert SwingUtilities.isEventDispatchThread();

      if (! registeredComponents.containsKey(graftableComponent))
         throw new IllegalArgumentException("Component is not attached to this peer.");

      final GraftableComponentNode currentEventSubscriptions = registeredComponents.get(graftableComponent);
      final Set<GraftableComponentEventType> eventsSet = currentEventSubscriptions.toSet();

      if (! eventsSet.contains(eventTypeToDeregister))
         return;

      deregisterEventListenerHandler(eventTypeToDeregister);
      eventsSet.remove(eventTypeToDeregister);

      checkClearComponentEventState((GraftableSwingComponent) graftableComponent, currentEventSubscriptions, eventsSet);
      refreshEventSubscriptions((GraftableSwingComponent) graftableComponent, currentEventSubscriptions, eventsSet);
   }


   @Override
   final public void deregisterEventListeners(final GraftableComponent graftableComponent, final Set<GraftableComponentEventType> eventTypesToDeregister)
   {
      assert SwingUtilities.isEventDispatchThread();

      if (! registeredComponents.containsKey(graftableComponent))
         throw new IllegalArgumentException("Component is not attached to this peer.");

      final GraftableComponentNode currentEventSubscriptions = registeredComponents.get(graftableComponent);
      final Set<GraftableComponentEventType> eventsSet = currentEventSubscriptions.toSet();

      boolean isEventSubscriptionUpdated = false;
      for (final GraftableComponentEventType eventTypeToDeregister : eventTypesToDeregister)
      {
         if (eventsSet.contains(eventTypeToDeregister))
         {
            deregisterEventListenerHandler(eventTypeToDeregister);
            eventsSet.remove(eventTypeToDeregister);
            isEventSubscriptionUpdated = true;
         }
      }

      if (isEventSubscriptionUpdated)
      {
         checkClearComponentEventState((GraftableSwingComponent) graftableComponent, currentEventSubscriptions, eventsSet);
         refreshEventSubscriptions((GraftableSwingComponent) graftableComponent, currentEventSubscriptions, eventsSet);
      }
   }


   private void deregisterEventListenerHandler(final GraftableComponentEventType eventTypeToDeregister)
   {
      final int countOfEventSubscribers;

      synchronized (eventSubscriptionCounts)
      {
         countOfEventSubscribers = eventSubscriptionCounts.get(eventTypeToDeregister) - 1;
         eventSubscriptionCounts.put(eventTypeToDeregister, countOfEventSubscribers);
      }

      /* Perform the call to the peer outside of the lock, to avoid potential deadlock and bad practice in general. This is confirmed as a problem if an SWT peer attaches/detaches
       * a component and makes a call to getEventsOfInterest() (which requires the lock) on the SWT thread at the same time as our graftableComponentPeer.deregisterEventListener()
       * tries to invokeAndWait() on the SWT thread.
       */
      if (countOfEventSubscribers == 0)
         graftableComponentPeer.deregisterEventListener(this, eventTypeToDeregister);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public void setPeerVisible(final boolean isVisible)
   {
      graftableComponentPeer.setPeerVisible(isVisible);
   }


   @Override
   final public void requestPeerFocus()
   {
      graftableComponentPeer.requestPeerFocus();
   }


   @Override
   final public void setPeerSize(final int width, final int height)
   {
      graftableComponentPeer.setPeerSize(width, height);
   }


   @Override
   final public void setPeerCursor(final GraftableComponentCursorType cursorType)
   {
      graftableComponentPeer.setPeerCursor(cursorType);
   }


   @Override
   final public void setPeerToolTipText(final String toolTipText)
   {
      graftableComponentPeer.setPeerToolTipText(toolTipText);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public void regionPainted(final BufferedImage paintedImage, final int outputRegionUpperLeftX, final int outputRegionUpperLeftY, final boolean deferRedraw)
   {
      graftableComponentPeer.regionPainted(paintedImage, outputRegionUpperLeftX + delegate.getX(), outputRegionUpperLeftY + delegate.getY(), deferRedraw);
   }


   @Override
   final public void regionPainted(final BufferedImage paintedImage, final int inputImageStartX, final int inputImageStartY,
                                      final int regionWidth, final int regionHeight, final int destinationX, final int destinationY, final boolean deferRedraw)
   {
      graftableComponentPeer.regionPainted(paintedImage, inputImageStartX, inputImageStartY, regionWidth, regionHeight, destinationX + delegate.getX(), destinationY + delegate.getY(), deferRedraw);
   }


   @Override
   final public void transferRegion(final int sourceX, final int sourceY, final int regionWidth, final int regionHeight, final int destinationX, final int destinationY,
                                       final boolean deferRedraw)
   {
      graftableComponentPeer.transferRegion(sourceX + delegate.getX(), sourceY + delegate.getY(), regionWidth, regionHeight, destinationX + delegate.getX(), destinationY + delegate.getY(), deferRedraw);
   }


   @Override
   final public void regionCleared(final int regionX, final int regionY, final int regionWidth, final int regionHeight, final boolean deferRedraw)
   {
      graftableComponentPeer.regionCleared(regionX + delegate.getX(), regionY + delegate.getY(), regionWidth, regionHeight, deferRedraw);
   }


   @Override
   final public void redrawRegion(final int regionX, final int regionY, final int regionWidth, final int regionHeight)
   {
      graftableComponentPeer.redrawRegion(regionX, regionY, regionWidth, regionHeight);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public JPanel getDelegateComponent()
   {
      return delegate;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public Set<GraftableComponentEventType> getEventsOfInterest()
   {
      final Set<GraftableComponentEventType> eventsOfInterest = EnumSet.noneOf(GraftableComponentEventType.class);

      synchronized (eventSubscriptionCounts)
      {
         for (final Entry<GraftableComponentEventType, Integer> eventEntry : eventSubscriptionCounts.entrySet())
         {
            if (eventEntry.getValue() > 0)
               eventsOfInterest.add(eventEntry.getKey());
         }
      }

      return eventsOfInterest;
   }


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
      delegate.setSize(width, height);

      /* We need to manually force a validate() to lay out the components after the resize.
       * The validate() will trigger the chain of doLayout() calls down the hierarchy, but only if our delegate panel is attached to a displayable root frame.
       * Otherwise we would have to manually call the doLayout()'s ourself.
       */
      delegate.validate();

      /* Now we need to clear the spaces where components have moved. We go through all of them and clear the spaces on the peer where necessary, being careful to hold
       * off on a redraw until the components have been repainted in their new positions and sizes.
       */
      Rectangle newComponentBounds;

      for (final GraftableComponentNode component : registeredComponents.values())
      {
         newComponentBounds = component.graftableComponent.getDelegateComponent().getBounds();

         if (! newComponentBounds.equals(component.lastResizeBounds))
         {
            component.graftableComponent.receiveControlResizedEventFromSWTPeer(controlEvent, newComponentBounds.width, newComponentBounds.height, true);

            /* If the repaint request is deferred/suppressed for our panel, it means that our parent is taking care of the space clearing and repainting,
             * just as we are doing in this method with our child components.
             * Otherwise we clear the space where each component used to be, but we defer the redraw() of our peer until after everything has been repainted.
             */
            if ((! deferRepaint) && (component.lastResizeBounds != null))
               regionCleared(component.lastResizeBounds.x, component.lastResizeBounds.y, component.lastResizeBounds.width, component.lastResizeBounds.height, true);

            component.setLastResizeBounds(newComponentBounds);
         }
      }

      // Finally repaint the components in their new positions/sizes and flush the changes via a redraw to the peer.
      if (! deferRepaint)
         repaint();
   }


   @Override
   final public void receiveFocusGainedEventFromSWTPeer(final org.eclipse.swt.events.FocusEvent focusEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveFocusGainedEventEDT(focusEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveFocusGainedEventEDT(focusEvent);
            }
         });
   }


   private void receiveFocusGainedEventEDT(final org.eclipse.swt.events.FocusEvent focusEvent)
   {
      if (hasFocus)
         return;

      if (defaultFocusComponent != NullEventSubscription)
      {
         hasFocus = true;
         focusedComponent = defaultFocusComponent;
         focusedComponent.graftableComponent.receiveFocusGainedEventFromSWTPeer(focusEvent);
      }
   }


   @Override
   final public void receiveFocusLostEventFromSWTPeer(final org.eclipse.swt.events.FocusEvent focusEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveFocusLostEventEDT(focusEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveFocusLostEventEDT(focusEvent);
            }
         });
   }


   private void receiveFocusLostEventEDT(final org.eclipse.swt.events.FocusEvent focusEvent)
   {
      if (! hasFocus)
         return;

      focusedComponent.graftableComponent.receiveFocusLostEventFromSWTPeer(focusEvent);
      focusedComponent = NullEventSubscription;
      hasFocus = false;
   }


   @Override
   final public void receiveMouseEnteredEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
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
      if (lastMouseRegionComponent.receivesMouseTrackEvents)
      {
         if (lastMouseRegionComponent.graftableComponent.getDelegateComponent().getBounds().contains(mouseEvent.x, mouseEvent.y))
         {
            adjustMouseEventCoordinateForNestedComponent(mouseEvent, lastMouseRegionComponent.graftableComponent.getDelegateComponent());
            lastMouseRegionComponent.graftableComponent.receiveMouseEnteredEventFromSWTPeer(mouseEvent);
            return;
         }
      }

      handleEventsForNewMouseRegionComponent(mouseEvent);
   }


   private void handleEventsForNewMouseRegionComponent(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      Component component;
      for (final GraftableComponentNode componentEventSubscription : mouseTrackAndMotionListeners)
      {
         if (componentEventSubscription == lastMouseRegionComponent)
            continue;

         component = componentEventSubscription.graftableComponent.getDelegateComponent();
         if (component.getBounds().contains(mouseEvent.x, mouseEvent.y))
         {
            lastMouseRegionComponent = componentEventSubscription;

            if (lastMouseRegionComponent.receivesMouseTrackEvents)
            {
               adjustMouseEventCoordinateForNestedComponent(mouseEvent, component);
               lastMouseRegionComponent.graftableComponent.receiveMouseEnteredEventFromSWTPeer(mouseEvent);
            }

            return;
         }
      }

      lastMouseRegionComponent = NullEventSubscription;
   }


   @Override
   final public void receiveMouseExitedEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
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
      if (lastMouseRegionComponent.receivesMouseTrackEvents)
      {
         adjustMouseEventCoordinateForNestedComponent(mouseEvent, lastMouseRegionComponent.graftableComponent.getDelegateComponent());
         lastMouseRegionComponent.graftableComponent.receiveMouseExitedEventFromSWTPeer(mouseEvent);
      }
   }


   @Override
   final public void receiveMouseDownEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
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
      if (hasFocus && (focusedComponent != lastMouseRegionComponent) && lastMouseRegionComponent.receivesFocusEvents)
      {
         focusedComponent.graftableComponent.receiveFocusLostEventFromSWTPeer(null);
         focusedComponent = lastMouseRegionComponent;
         lastMouseRegionComponent.graftableComponent.receiveFocusGainedEventFromSWTPeer(null);
      }

      if (lastMouseRegionComponent.receivesMouseClickEvents)
      {
         adjustMouseEventCoordinateForNestedComponent(mouseEvent, lastMouseRegionComponent.graftableComponent.getDelegateComponent());
         lastMouseRegionComponent.graftableComponent.receiveMouseDownEventFromSWTPeer(mouseEvent);
      }
   }


   @Override
   final public void receiveMouseUpEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
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
      if (lastMouseRegionComponent.receivesMouseClickEvents)
      {
         // Set aside a copy of the mouse event, because we may need the original to fire off events to other components.
         final org.eclipse.swt.events.MouseEvent clonedMouseEvent = cloneSwtMouseEvent(mouseEvent);
         adjustMouseEventCoordinateForNestedComponent(clonedMouseEvent, lastMouseRegionComponent.graftableComponent.getDelegateComponent());
         lastMouseRegionComponent.graftableComponent.receiveMouseUpEventFromSWTPeer(clonedMouseEvent);
      }

      /* If there is a mouse up event after a drag operation, and ALL mouse buttons have been released (otherwise it is considered an ongoing drag operation from
       * the originating component), we may need to fire off a couple of events: a mouse exited event for the previous mouse region component,
       * and a mouse entered for the new mouse region component.
       * Confusingly, the SWT mouse event will indicate the button mask immediately BEFORE the latest button release, hence the call to isSingleMouseButtonMask()
       * which for this event type translates to: have all mouse buttons been released.
       */
      if (isSingleMouseButtonMask(mouseEvent.stateMask) && ((lastMouseRegionComponent == NullEventSubscription) ||
                                                            (! lastMouseRegionComponent.graftableComponent.getDelegateComponent().getBounds().contains(mouseEvent.x, mouseEvent.y))))
      {
         if (lastMouseRegionComponent.receivesMouseTrackEvents)
         {
            final org.eclipse.swt.events.MouseEvent clonedMouseEvent = cloneSwtMouseEvent(mouseEvent);
            adjustMouseEventCoordinateForNestedComponent(clonedMouseEvent, lastMouseRegionComponent.graftableComponent.getDelegateComponent());
            lastMouseRegionComponent.graftableComponent.receiveMouseExitedEventFromSWTPeer(clonedMouseEvent);
         }

         handleEventsForNewMouseRegionComponent(mouseEvent);
      }
   }


   @Override
   final public void receiveMouseMovedEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
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
      if (lastMouseRegionComponent.receivesMouseTrackEvents || lastMouseRegionComponent.receivesMouseMotionEvents)
      {
         if (lastMouseRegionComponent.graftableComponent.getDelegateComponent().getBounds().contains(mouseEvent.x, mouseEvent.y))
         {
            if (lastMouseRegionComponent.receivesMouseMotionEvents)
            {
               adjustMouseEventCoordinateForNestedComponent(mouseEvent, lastMouseRegionComponent.graftableComponent.getDelegateComponent());
               lastMouseRegionComponent.graftableComponent.receiveMouseMovedEventFromSWTPeer(mouseEvent);
            }

            return;
         }
         else if (lastMouseRegionComponent.receivesMouseTrackEvents)
         {
            final org.eclipse.swt.events.MouseEvent mouseExitedEvent = cloneSwtMouseEvent(mouseEvent);
            adjustMouseEventCoordinateForNestedComponent(mouseExitedEvent, lastMouseRegionComponent.graftableComponent.getDelegateComponent());
            lastMouseRegionComponent.graftableComponent.receiveMouseExitedEventFromSWTPeer(mouseExitedEvent);
         }
      }

      handleEventsForNewMouseRegionComponent(mouseEvent);
   }


   @Override
   final public void receiveMouseDraggedEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
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
      /* Mouse region is not updated during dragging - events are fire back to the originating click region.
       * This means that when the user releases ALL mouse buttons, we may have to fire a mouse exited and entered event.
       */
      if (lastMouseRegionComponent.receivesMouseDragEvents)
      {
         adjustMouseEventCoordinateForNestedComponent(mouseEvent, lastMouseRegionComponent.graftableComponent.getDelegateComponent());
         lastMouseRegionComponent.graftableComponent.receiveMouseDraggedEventFromSWTPeer(mouseEvent);
      }
   }


   @Override
   final public void receiveMouseWheelScrolledEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
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
      if (focusedComponent.receivesMouseWheelEvents)
         focusedComponent.graftableComponent.receiveMouseWheelScrolledEventFromSWTPeer(mouseEvent);
   }


   @Override
   final public void receiveKeyPressedEventFromSWTPeer(final org.eclipse.swt.events.KeyEvent keyEvent)
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
      if (focusedComponent.receivesKeyEvents)
         focusedComponent.graftableComponent.receiveKeyPressedEventFromSWTPeer(keyEvent);
   }


   @Override
   final public void receiveKeyReleasedEventFromSWTPeer(final org.eclipse.swt.events.KeyEvent keyEvent)
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
      if (focusedComponent.receivesKeyEvents)
         focusedComponent.graftableComponent.receiveKeyReleasedEventFromSWTPeer(keyEvent);
   }


   @Override
   final public void repaint(final boolean deferPeerRedraw)
   {
      for (final GraftableComponent component : registeredComponents.keySet())
         component.repaint(true);

      if (! deferPeerRedraw)
         graftableComponentPeer.redrawRegion(0, 0, delegate.getWidth(), delegate.getHeight());
   }


   @Override
   final public void preDispose()
   {
      for (final GraftableComponent component : registeredComponents.keySet())
         component.preDispose();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public void setLayout(final LayoutManager layoutManager)
   {
      delegate.setLayout(layoutManager);
   }


   final public void setDefaultFocusComponent(final GraftableSwingComponent defaultFocusComponent)
   {
      for (final GraftableComponentNode componentEventSubscription : focusListeners)
      {
         if (componentEventSubscription.graftableComponent == defaultFocusComponent)
         {
            this.defaultFocusComponent = componentEventSubscription;
            return;
         }
      }

      throw new IllegalArgumentException("Component is not registered as a focus listener");
   }
}