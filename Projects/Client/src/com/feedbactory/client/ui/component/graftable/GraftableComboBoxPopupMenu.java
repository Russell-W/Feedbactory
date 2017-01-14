/* Memos:
 * - Popup notification events (eg. popupWillBecomeVisible) are not fired at the moment.
 *
 */

package com.feedbactory.client.ui.component.graftable;


import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.MouseClick;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.MouseDrag;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.MouseMove;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.MouseTrack;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.MouseWheel;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.ComboPopup;


final public class GraftableComboBoxPopupMenu extends GraftableSwingComponent<JPopupMenu>
{
   /* MouseWheel event is needed on the popup for Mac OS X and Linux. On these platforms the source of events changes as the mouse moves between the combo box itself
    * and the popup, meaning that once the mouse is within the popup area, the popup needs to provide all of the input events. On Windows the combo box continues
    * to provide the events even once the mouse cursor has moved into the popup region.
    */
   static final private Set<GraftableComponentEventType> EventsOfInterest = Collections.unmodifiableSet(EnumSet.of(MouseTrack, MouseClick, MouseMove, MouseDrag, MouseWheel));

   final private GraftableComboBox comboBoxOwner;

   private boolean isPeerVisible;

   final private JPopupMenu delegate;
   private JScrollPane scrollPane;
   private JList list;
   private JScrollBar verticalScrollbar;
   private JButton verticalScrollbarLowerButton;
   private JButton verticalScrollbarUpperButton;

   private int lastVerticalScrollbarValue;

   private Component lastMouseRegionComponent;


   public GraftableComboBoxPopupMenu(final GraftableComboBox comboBoxOwner, final JPopupMenu delegate, final GraftableComponentSwingFramework graftableFramework)
   {
      this.comboBoxOwner = comboBoxOwner;
      this.delegate = delegate;

      initialise(graftableFramework);
   }


   private void initialise(final GraftableComponentSwingFramework graftableFramework)
   {
      /* This popup menu component MUST be added to the graftable framework top level before it's first shown, because attaching the popup menu to the
       * displayable top level (invisible) frame causes some Synth/Nimbus properties to be initialised. The scrollbar arrow buttons are otherwise initially the
       * wrong size. It's worth noting because it's quite a difficult trigger to track down.
       *
       * I tried placing this as a one-off initialiser in the graftable framework backing frame, adding & removing a temporary JPopupMenu, however the
       * Synth properties unfortunately don't seem to have been initialised for new instances. Hence this bodgy workaround.
       */
      graftableFramework.addTopLevelSwingComponent(this);
      graftableFramework.removeTopLevelSwingComponent(this);

      list = ((ComboPopup) delegate).getList();
      scrollPane = (JScrollPane) delegate.getComponent(0);
      verticalScrollbar = scrollPane.getVerticalScrollBar();
      verticalScrollbarLowerButton = (JButton) verticalScrollbar.getComponent(0);
      verticalScrollbarUpperButton = (JButton) verticalScrollbar.getComponent(1);

      lastMouseRegionComponent = list;

      installDirtyImageListeners();
   }


   private void installDirtyImageListeners()
   {
      list.addListSelectionListener(new ListSelectionListener()
      {
         @Override
         final public void valueChanged(final ListSelectionEvent listSelectionEvent)
         {
            // Needs this extra kick when changing the selection via arrow keys, possibly others too.
            list.ensureIndexIsVisible(list.getSelectedIndex());

            repaintUpdatedListCells(listSelectionEvent.getFirstIndex(), listSelectionEvent.getLastIndex());
         }
      });

      /* Placing the listener on the model results in the premature repainting of the scrollbar, so we place an adjustment listener which is fired
       * after the scrollbar has been updated.
       */
      verticalScrollbar.addAdjustmentListener(new AdjustmentListener()
      {
         @Override
         final public void adjustmentValueChanged(final AdjustmentEvent adjustmentEvent)
         {
            handleScrollRepaint();
         }
      });
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public JPopupMenu getDelegateComponent()
   {
      return delegate;
   }


   @Override
   final public Set<GraftableComponentEventType> getEventsOfInterest()
   {
      return EventsOfInterest;
   }


   @Override
   final public void receiveMouseExitedEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseExitedEventHandler(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseExitedEventHandler(mouseEvent);
            }
         });
   }


   private void receiveMouseExitedEventHandler(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (lastMouseRegionComponent != list)
      {
         lastMouseRegionComponent.dispatchEvent(new MouseEvent(lastMouseRegionComponent, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), swtMouseMaskToSwingModifiersEx(mouseEvent.stateMask), -1, -1, 0, false));
         lastMouseRegionComponent = list;

         repaintVerticalScrollbar(false);
      }
   }


   @Override
   final public void receiveMouseDownEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseDownEventHandler(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseDownEventHandler(mouseEvent);
            }
         });
   }


   private void receiveMouseDownEventHandler(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      final int swingMouseButtonMask = convertSWTToSwingButtonMask(mouseEvent.button);

      if (lastMouseRegionComponent == list)
      {
         final int scrollPaneOffset = verticalScrollbar.getValue();
         list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), swingMouseButtonMask, mouseEvent.x, mouseEvent.y + scrollPaneOffset, mouseEvent.count, false, mouseEvent.button));
      }
      else
      {
         if (lastMouseRegionComponent == verticalScrollbar)
            adjustMouseEventCoordinateForNestedComponent(mouseEvent, verticalScrollbar);
         else
            adjustMouseEventCoordinateForNestedComponent(mouseEvent, verticalScrollbar, lastMouseRegionComponent);

         lastMouseRegionComponent.dispatchEvent(new MouseEvent(lastMouseRegionComponent, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), swingMouseButtonMask, mouseEvent.x, mouseEvent.y, mouseEvent.count, false, mouseEvent.button));

         repaintVerticalScrollbar(false);
      }
   }


   @Override
   final public void receiveMouseUpEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseUpEventHandler(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseUpEventHandler(mouseEvent);
            }
         });
   }


   private void receiveMouseUpEventHandler(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      final int swingMouseButtonMask = convertSWTToSwingButtonMask(mouseEvent.button);

      if (lastMouseRegionComponent == list)
      {
         final int scrollPaneOffset = verticalScrollbar.getValue();
         list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), swingMouseButtonMask, mouseEvent.x, mouseEvent.y + scrollPaneOffset, mouseEvent.count, false, mouseEvent.button));
      }
      else
      {
         if (lastMouseRegionComponent == verticalScrollbar)
            adjustMouseEventCoordinateForNestedComponent(mouseEvent, verticalScrollbar);
         else
            adjustMouseEventCoordinateForNestedComponent(mouseEvent, verticalScrollbar, lastMouseRegionComponent);

         lastMouseRegionComponent.dispatchEvent(new MouseEvent(lastMouseRegionComponent, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), swingMouseButtonMask, mouseEvent.x, mouseEvent.y, mouseEvent.count, false, mouseEvent.button));

         repaintVerticalScrollbar(false);
      }
   }


   @Override
   final public void receiveMouseMovedEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseMovedEventHandler(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseMovedEventHandler(mouseEvent);
            }
         });
   }


   private void receiveMouseMovedEventHandler(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      boolean scrollbarRepaintNeeded = false;

      final Component targetComponent = adjustMouseEventForDeepestPopupComponentAt(mouseEvent);

      if (targetComponent == null)
         return;

      final int swingMouseButtonMask = swtMouseMaskToSwingModifiersEx(mouseEvent.stateMask);

      if (targetComponent != lastMouseRegionComponent)
      {
         lastMouseRegionComponent.dispatchEvent(new MouseEvent(lastMouseRegionComponent, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), swingMouseButtonMask, -1, -1, 0, false));

         if (targetComponent != list)
            targetComponent.dispatchEvent(new MouseEvent(targetComponent, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), swingMouseButtonMask, mouseEvent.x, mouseEvent.y, 0, false));

         lastMouseRegionComponent = targetComponent;
         scrollbarRepaintNeeded = true;
      }

      if (targetComponent == list)
      {
         int scrollPaneOffset = verticalScrollbar.getValue();
         list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), swingMouseButtonMask, mouseEvent.x, mouseEvent.y + scrollPaneOffset, 0, false));
      }
      else if (targetComponent == verticalScrollbar)
      {
         verticalScrollbar.dispatchEvent(new MouseEvent(verticalScrollbar, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), swingMouseButtonMask, mouseEvent.x, mouseEvent.y, 0, false));
         scrollbarRepaintNeeded = true;
      }

      if (scrollbarRepaintNeeded)
         repaintVerticalScrollbar(false);
   }


   private Component adjustMouseEventForDeepestPopupComponentAt(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      /* Need to check scrollbar bounds first, since the list width often exceed the side of the painted component when the popup's size is small.
       * It could be that the list's width never reduces below its preferred size.
       */
      if (verticalScrollbar.isVisible() && verticalScrollbar.getBounds().contains(mouseEvent.x, mouseEvent.y))
      {
         adjustMouseEventCoordinateForNestedComponent(mouseEvent, verticalScrollbar);

         if (verticalScrollbarUpperButton.getBounds().contains(mouseEvent.x, mouseEvent.y))
         {
            adjustMouseEventCoordinateForNestedComponent(mouseEvent, verticalScrollbarUpperButton);
            return verticalScrollbarUpperButton;
         }
         else if (verticalScrollbarLowerButton.getBounds().contains(mouseEvent.x, mouseEvent.y))
         {
            adjustMouseEventCoordinateForNestedComponent(mouseEvent, verticalScrollbarLowerButton);
            return verticalScrollbarLowerButton;
         }
         else
            return verticalScrollbar;
      }
      else
         return list;
   }


   @Override
   final public void receiveMouseDraggedEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseDraggedEventHandler(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseDraggedEventHandler(mouseEvent);
            }
         });
   }


   private void receiveMouseDraggedEventHandler(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (lastMouseRegionComponent == list)
      {
         final int scrollPaneOffset = verticalScrollbar.getValue();
         list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), swtMouseMaskToSwingModifiersEx(mouseEvent.stateMask), mouseEvent.x, mouseEvent.y + scrollPaneOffset, 0, false));
      }
      else if (lastMouseRegionComponent == verticalScrollbar)
      {
         adjustMouseEventCoordinateForNestedComponent(mouseEvent, verticalScrollbar);
         verticalScrollbar.dispatchEvent(new MouseEvent(verticalScrollbar, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), swtMouseMaskToSwingModifiersEx(mouseEvent.stateMask), mouseEvent.x, mouseEvent.y, 0, false));
      }
   }


   @Override
   final public void receiveMouseWheelScrolledEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseWheelScrolledEventHandler(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseWheelScrolledEventHandler(mouseEvent);
            }
         });
   }


   private void receiveMouseWheelScrolledEventHandler(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      scrollPane.dispatchEvent(new MouseWheelEvent(scrollPane, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), swtMouseMaskToSwingModifiersEx(mouseEvent.stateMask), mouseEvent.x, mouseEvent.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, Math.abs(mouseEvent.count), (mouseEvent.count >= 0) ?  -1 : 1));
   }


   @Override
   final public void repaint(final boolean deferPeerRedraw)
   {
      if (isPeerVisible)
         super.repaint(deferPeerRedraw);
   }


   @Override
   final protected void paintBackground(final Graphics2D graphics2D)
   {
      /* The default GraftableSwingComponent paintComponent() behaviour calls our delegate's paint() method directly, which already clears the background,
       * so we don't need to do anything further here.
       */
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleScrollRepaint()
   {
      if (! isPeerVisible)
         return;

      final int deltaScroll = verticalScrollbar.getValue() - lastVerticalScrollbarValue;

      if (deltaScroll == 0)
         return;

      final int drawRegionOffsetX = scrollPane.getX();
      final int drawRegionOffsetY = scrollPane.getY();

      if (deltaScroll > 0)
      {
         graftableComponentPeer.transferRegion(drawRegionOffsetX, drawRegionOffsetY + deltaScroll, scrollPane.getViewport().getWidth(), scrollPane.getViewport().getHeight() - deltaScroll, drawRegionOffsetX, drawRegionOffsetY, true);
         repaintLowerListPortion(lastVerticalScrollbarValue + scrollPane.getViewport().getHeight());
         repaintVerticalScrollbar(true);
      }
      else
      {
         graftableComponentPeer.transferRegion(drawRegionOffsetX, drawRegionOffsetY, scrollPane.getViewport().getWidth(), scrollPane.getViewport().getHeight() + deltaScroll, drawRegionOffsetX, drawRegionOffsetY - deltaScroll, true);
         repaintUpperListPortion(lastVerticalScrollbarValue - 1);
         repaintVerticalScrollbar(true);
      }

      graftableComponentPeer.redrawRegion(drawRegionOffsetX, drawRegionOffsetY, scrollPane.getWidth(), scrollPane.getHeight());

      lastVerticalScrollbarValue = verticalScrollbar.getValue();
   }


   private void repaintLowerListPortion(final int startingPointY)
   {
      int listIndex = list.locationToIndex(new Point(0, startingPointY));
      Rectangle cellBoundsTranslatedToViewport;

      final Graphics2D graphics2D = outputImage.createGraphics();

      do
      {
         cellBoundsTranslatedToViewport = getCellBoundsTranslatedToViewport(listIndex);

         if ((cellBoundsTranslatedToViewport == null) || (cellBoundsTranslatedToViewport.y >= scrollPane.getViewport().getHeight()))
            break;

         repaintListCell(graphics2D, listIndex, cellBoundsTranslatedToViewport, true);

         listIndex ++;
      }
      while (listIndex < list.getModel().getSize());

      graphics2D.dispose();
   }


   private void repaintUpperListPortion(final int startingPointY)
   {
      int listIndex = list.locationToIndex(new Point(0, startingPointY));
      Rectangle cellBoundsTranslatedToViewport;

      final Graphics2D graphics2D = outputImage.createGraphics();

      do
      {
         cellBoundsTranslatedToViewport = getCellBoundsTranslatedToViewport(listIndex);

         if ((cellBoundsTranslatedToViewport == null) || ((cellBoundsTranslatedToViewport.y + cellBoundsTranslatedToViewport.height) <= 0))
            break;

         repaintListCell(graphics2D, listIndex, cellBoundsTranslatedToViewport, true);

         listIndex --;
      }
      while (listIndex >= 0);

      graphics2D.dispose();
   }


   private Rectangle getCellBoundsTranslatedToViewport(final int listIndex)
   {
      final Rectangle cellBounds = list.getCellBounds(listIndex, listIndex);

      if (cellBounds == null)
         return null;

      cellBounds.translate(0, -verticalScrollbar.getValue());

      return cellBounds;
   }


   /* An older method used here to repaint individual cells was to hijack the list's CellRendererPane and use that to perform the painting.
    * At the moment we're simply resizing and painting the parentless cell. If this causes any problems, eg. where the cell's renderer
    * makes the assumption that it is laid out within a parent, then we can always fall back on the CellRendererPane technique:
    * 
    * final CellRendererPane listCellRendererPane = (CellRendererPane) list.getComponent(0);
    * ...Do the same calculations here to check and restrict the cell's clip if necessary:
    * list.getUI().getCellBounds(list, cellIndex, cellIndex);
    * if ((cellBoundsWithinList == null) || (! scrollPane.getViewport().getViewRect().intersects(cellBoundsWithinList)))
    *    return;
    * ... etc...
    * listCellRendererPane.paintComponent(graphics2D, rendererComponent, list, cellBounds);
    * graftableComponentPeer.regionPainted(outputImage, cellBoundsWithinList.x, cellBoundsWithinList.y, cellBoundsWithinList);
    * 
    * And finally, remove the rubber stamp renderer component(s) that were temporarily added to the CellRendererPane:
    * listCellRendererPane.removeAll();
    * 
    * Note the difference with listCellRendererPane.paintComponent() is that the cell will have been painted into its x, y position within the buffer image,
    * so we had to pass in an offset to the regionPainted() method.
    * 
    * See BasicListUI for more details.
    */
   private void repaintListCell(final Graphics2D graphics2D, final int cellIndex, final Rectangle cellBoundsTranslatedToViewport, final boolean deferRedraw)
   {
      // Clip the bounds for the viewport area taking into account the vertical scrollbar.
      final Rectangle cellBoundsClippedToViewport = scrollPane.getViewport().getBounds().intersection(cellBoundsTranslatedToViewport);

      if (cellBoundsClippedToViewport.isEmpty())
         return;

      graphics2D.setComposite(AlphaComposite.Src);
      graphics2D.setColor(list.getBackground());
      graphics2D.fillRect(0, 0, cellBoundsTranslatedToViewport.width, cellBoundsTranslatedToViewport.height);
      graphics2D.setComposite(AlphaComposite.SrcOver);

      final ListSelectionModel listSelectionModel = list.getSelectionModel();
      final Component rendererComponent = list.getCellRenderer().getListCellRendererComponent(list, list.getModel().getElementAt(cellIndex), cellIndex, listSelectionModel.isSelectedIndex(cellIndex), false);

      rendererComponent.setSize(cellBoundsTranslatedToViewport.width, cellBoundsTranslatedToViewport.height);
      rendererComponent.paint(graphics2D);

      graftableComponentPeer.regionPainted(outputImage, cellBoundsClippedToViewport.x - cellBoundsTranslatedToViewport.x,
                                              cellBoundsClippedToViewport.y - cellBoundsTranslatedToViewport.y,
                                              cellBoundsClippedToViewport.width, cellBoundsClippedToViewport.height,
                                              scrollPane.getX() + cellBoundsClippedToViewport.x,
                                              scrollPane.getY() + cellBoundsClippedToViewport.y,
                                              deferRedraw);
   }


   private void repaintUpdatedListCells(final int firstChangedListCellIndex, final int secondChangedListCellIndex)
   {
      if (! isPeerVisible)
         return;

      final Graphics2D graphics2D = outputImage.createGraphics();

      Rectangle cellBounds = getCellBoundsTranslatedToViewport(firstChangedListCellIndex);
      if (cellBounds != null)
         repaintListCell(graphics2D, firstChangedListCellIndex, cellBounds, false);

      cellBounds = getCellBoundsTranslatedToViewport(secondChangedListCellIndex);
      if (cellBounds != null)
         repaintListCell(graphics2D, secondChangedListCellIndex, cellBounds, false);

      graphics2D.dispose();
   }


   private void repaintVerticalScrollbar(final boolean deferRedraw)
   {
      if (! isPeerVisible)
         return;

      final Graphics2D graphics2D = outputImage.createGraphics();
      verticalScrollbar.paint(graphics2D);
      graphics2D.dispose();

      graftableComponentPeer.regionPainted(outputImage, 0, 0, verticalScrollbar.getWidth(), verticalScrollbar.getHeight(),
                                              scrollPane.getX() + verticalScrollbar.getX(), scrollPane.getY() + verticalScrollbar.getY(), deferRedraw);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSetVisible(final boolean isVisible)
   {
      /* Here we are hiding or showing the Swing portion of the popup, without affecting the visibility of the peer. Essentially this functionality is used by
       * the combobox to ensure that the size of the Swing popup is correct and that it's been laid out correctly, before showing the SWT peer which the
       * user will actually see.
       * 
       * Previously I was calling the combo box's setPopupVisible to allow Swing to perform all of its preparation of the popup menu, however this was fairly
       * expensive, especially when we were doing it many times in succession when this item is being used as part of a combo box history dropdown.
       * Also going down that path there was always the danger that Swing would decide that it would have to show a heavyweight popup, depending on the
       * dimensions of the combo box and the size/locations of taskbars in Windows or Linux. In the case of a heavyweight being shown, it would display alongside
       * our grafted popup, and look a bit ridiculous. It would also usually then crash on Ubuntu. To prevent this situation involved configuring the bounds of
       * the Swing graftable framework's hidden frame specifically to maximise the possible screen usage, while not creeping onto any OS taskbars, etc. It was
       * living more than a little dangerously. FYI, refer to PopupFactory.fitsOnScreen() for the logic...
       * 
       * With this method however there are no such checks for the need to show heavyweights because we're not really using the popup subsystem to resize and layout the
       * component; we're doing all of that ourself, and simply attaching it to our Swing framework as a top level (displayable) component.
       */
      if (isVisible)
         handlePreparePopupToBeShown();
   }


   private void handlePreparePopupToBeShown()
   {
      synchroniseListSelectionWithOwner();

      // Will install the UI (if not done already).
      delegate.setInvoker(comboBoxOwner.getDelegateComponent());

      preparePopupSize();
   }


   private void synchroniseListSelectionWithOwner()
   {
      final int comboBoxSelectionIndex = comboBoxOwner.getSelectedIndex();

      if (comboBoxSelectionIndex == -1)
         list.clearSelection();
      else
      {
         list.setSelectedIndex(comboBoxSelectionIndex);
         list.ensureIndexIsVisible(comboBoxSelectionIndex);
      }
   }


   private void preparePopupSize()
   {
      // The popup width needs to be constrained to the width of the parent combo box.
      final Dimension popupSize = comboBoxOwner.getSize();
      final Insets insets = delegate.getInsets();

      // The extra -3 reduction in width is just a personal pref, at least for Nimbus. The popup otherwise looks like it overshoots the parent width a little.
      popupSize.setSize(popupSize.width - (insets.right + insets.left) - 3, getPopupHeightForRowCount());

      scrollPane.setMaximumSize(popupSize);
      scrollPane.setPreferredSize(popupSize);
      scrollPane.setMinimumSize(popupSize);

      delegate.setSize(delegate.getPreferredSize());

      /* Our popup facade is not attached to any displayable component (eg. the graftable framework root frame), so we have to force the layouts ourself.
       * If this were not the case we could simply call validate() on the topmost delegate component and the layout requests would pass down through the hierarchy.
       * So we have to do extra work here, but if our popup was attached to a top level frame or window there is a major pitfall with the way that mouse
       * events are processed: unless the backing frame is large enough to cover the size of our component, mouse events on our popup list for example will
       * be ignored. Refer to BasicComboPopup's mouseMoved() method, and its use of list.computeVisibleRect().
       */
      delegate.doLayout();
      scrollPane.doLayout();
      scrollPane.getViewport().doLayout();
      list.doLayout();
      verticalScrollbar.doLayout();
   }


   /* Ripped almost verbatim from BasicComboPopup, where it's protected, so unfortunately we can't call it directly on our popup.
    * If we could intercept the creation of the combo box UI's pop up and replace it with a subclass (as we do with the editor textbox),
    * this would be different, and would come in very handy in general.
    */
   private int getPopupHeightForRowCount()
   {
      final int minimumRowCount = Math.min(comboBoxOwner.getMaximumRowCount(), comboBoxOwner.getItemCount());
      final ListCellRenderer renderer = list.getCellRenderer();

      int height = 0;
      Object value;
      Component rendererComponent;

      for (int itemIndex = 0; itemIndex < minimumRowCount; itemIndex ++)
      {
         value = list.getModel().getElementAt(itemIndex);
         rendererComponent = renderer.getListCellRendererComponent(list, value, itemIndex, false, false);
         height += rendererComponent.getPreferredSize().height;
      }

      if (height == 0)
         height = comboBoxOwner.getSize().height;

      final Border viewportBorder = scrollPane.getViewportBorder();

      if (viewportBorder != null)
      {
         final Insets insets = viewportBorder.getBorderInsets(null);
         height += (insets.top + insets.bottom);
      }

      final Border scrollPaneBorder = scrollPane.getBorder();

      if (scrollPaneBorder != null)
      {
         final Insets insets = scrollPaneBorder.getBorderInsets(null);
         height += (insets.top + insets.bottom);
      }

      return height;
   }


   private void handleSetPeerVisible(final boolean isPeerVisible)
   {
      /* This method is overloaded to produce a little more than a simple setVisible op.
       *
       * When the visibility argument is true, we need to:
       * - Resize the peer and the graftable component output image, if the sizes are out of sync.
       * - Set the peer to visible, if it isn't already.
       * - Unless the size and/or visibility has changed, we probably shouldn't presume that the render content has changed, hence we leave the repaint() decision to the client.
       *
       * When the visibility argument is false, we need to:
       * - Set the peer to invisible, if it isn't already.
       * - Null out the (possibly large) graftable image buffer so that it can be GC'd.
       */

      final boolean hasVisibilityChanged = (this.isPeerVisible != isPeerVisible);

      this.isPeerVisible = isPeerVisible;

      if (isPeerVisible)
      {
         final Dimension delegateSize = delegate.getSize();
         final boolean hasSizeChanged = ((outputImage == null) || (outputImage.getWidth() != delegateSize.width) || (outputImage.getHeight() != delegateSize.height));

         if (hasSizeChanged)
         {
            graftableComponentPeer.setPeerSize(delegateSize.width, delegateSize.height);
            outputImage = createOutputImage(delegateSize.width, delegateSize.height);
         }

         /* The setPeerVisible(true) call here should probably be placed after a follow-up repaint by the client to avoid showing the user an outdated SWT peer.
          * If glitches occur, we may have to rework this.
          */
         if (hasVisibilityChanged)
            graftableComponentPeer.setPeerVisible(true);
      }
      else if (hasVisibilityChanged)
      {
         outputImage = null;

         graftableComponentPeer.setPeerVisible(false);
      }
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final boolean isVisible()
   {
      return isPeerVisible;
   }


   final void setVisible(final boolean isVisible)
   {
      handleSetVisible(isVisible);
   }


   final void setPeerVisible(final boolean isPeerVisible)
   {
      handleSetPeerVisible(isPeerVisible);
   }
}