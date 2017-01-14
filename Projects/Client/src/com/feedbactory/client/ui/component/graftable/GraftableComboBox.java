/* Memos:
 * - Unfortunately there's no way of intercepting the creation of the combo box's popup menu and replacing it within our own delegate, as we do with the
 *      text box editor component. The popup is created by the active combo box UI, so we'd have to override that. Not possible with Synth.
 *
 * - Popup notification events (eg. popupWillBecomeVisible) are not fired at the moment.
 *
 * - Assumption: Mouse entered events from our peer are accompanied by mouse move events, ie. in editable mode we can ignore the mouse entered events and
 *   just process the follow-up mouse move events.
 *   Also on mouse enter and exit events, Swing components will sometimes only respond when the state mask is empty, hence the implementation below will pass 0 onto
 *   the components for those events, overriding the SWT stateMask.
 */


package com.feedbactory.client.ui.component.graftable;


import com.feedbactory.client.core.ConfigurationManager;
import javax.swing.ComboBoxEditor;
import java.awt.Font;
import java.awt.Point;
import javax.swing.ListCellRenderer;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.util.EnumSet;
import javax.swing.JPopupMenu;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.JTextField;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Set;
import javax.swing.ComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.*;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.DefaultComboBoxModel;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;


final public class GraftableComboBox extends GraftableSwingComponent<JComboBox>
{
   static final private Set<GraftableComponentEventType> EventsOfInterest = Collections.unmodifiableSet(EnumSet.of(Control, Focus, MouseTrack, MouseClick, MouseMove, MouseDrag, MouseWheel, Key));

   static final private Point PopupMenuOffsetFromBaseOfComboBox = new Point(2, 0);

   final private GraftableComboBoxDelegate delegate;

   private GraftableTextField graftableTextField;
   private GraftableTextFieldDefaultEditor editor;

   // HACK: Assumption of child component ordering.
   final private JButton dropdownButton;

   final private GraftableComboBoxPopupMenu graftablePopupMenu;
   private Rectangle graftablePopupMenuBounds;

   private Object lastMouseRegionComponent;

   private boolean isTextEntryMode;

   final private List<ComboBoxIndexSelectionListener> indexSelectionListeners = new CopyOnWriteArrayList<ComboBoxIndexSelectionListener>();


   public GraftableComboBox(final GraftableComponentSwingFramework graftableFramework)
   {
      this(graftableFramework, new DefaultComboBoxModel());
   }


   public GraftableComboBox(final GraftableComponentSwingFramework graftableFramework, final ComboBoxModel comboBoxModel)
   {
      delegate = new GraftableComboBoxDelegate(comboBoxModel);

      dropdownButton = (JButton) delegate.getComponent(0);

      // HACK: Assumption of child component ordering.
      graftablePopupMenu = new GraftableComboBoxPopupMenu(this, (JPopupMenu) delegate.getUI().getAccessibleChild(delegate, 0), graftableFramework);

      initialise();
   }


   private void initialise()
   {
      editor = new GraftableTextFieldDefaultEditor();
      delegate.setEditor(editor);

      replacePopupTriggerMouseListener();

      installDirtyImageListeners();

      if (ConfigurationManager.isRunningMacOSX)
         replacePopupNavigationActions();
   }


   private void replacePopupTriggerMouseListener()
   {
      // The first listener is the BasicButtonListener, installed by default.
      // The second listener is the UI listener that we need to hijack.
      // This same listener is installed by the Combo Box UI on both the control (for non-editable mode), and the dropdown button (for editable mode).
      // So, we need to intercept both of them.
      final MouseListener existingComboPopUpMouseListener = delegate.getMouseListeners()[1];

      delegate.removeMouseListener(existingComboPopUpMouseListener);
      dropdownButton.removeMouseListener(existingComboPopUpMouseListener);

      final MouseListener replacementMouseListener = new MouseAdapter()
      {
         @Override
         final public void mousePressed(MouseEvent e)
         {
            delegate.setPopupVisible(! delegate.isPopupVisible());
         }


         @Override
         final public void mouseReleased(MouseEvent e)
         {
            existingComboPopUpMouseListener.mouseReleased(e);
         }
      };

      delegate.addMouseListener(replacementMouseListener);
      dropdownButton.addMouseListener(replacementMouseListener);
   }


   private void installDirtyImageListeners()
   {
      delegate.addComponentListener(new ComponentAdapter()
      {
         @Override
         final public void componentResized(final ComponentEvent componentEvent)
         {
            if (graftablePopupMenu.isVisible())
               delegate.setPopupVisible(false, true);
         }


         @Override
         final public void componentMoved(final ComponentEvent componentEvent)
         {
            if (graftablePopupMenu.isVisible())
               delegate.setPopupVisible(false, true);
         }
      });

      delegate.addItemListener(new ItemListener()
      {
         @Override
         final public void itemStateChanged(final ItemEvent itemEvent)
         {
            if (itemEvent.getStateChange() == ItemEvent.SELECTED)
               repaint();
         }
      });
   }


   private void replacePopupNavigationActions()
   {
      /* Mac OS X seems to have different key action bindings than Windows, resulting in the page up & page down key presses executing slightly different
       * actions - see BasicComboBoxUI, specifically the difference between the handling of selectPrevious/UP and selectPrevious2/UP_2.
       * On OS X the result was that the arrow up key would not have any effect because the ui.isPopupVisible(comboBox) call would return false -
       * it uses the UI to check whether or not the popup is visible, rather than the direct call of comboBox.isPopupVisible() used elsewhere
       * which would ultimately check the state of the SWT peer from this class and return the correct result for the graftable popup.
       * I initially tried to overcome this problem by replacing the ComboBox delegate's UI with an overridden SynthComboBoxUI and isPopupVisible()
       * method which would defer to the isPopupVisible() method on this class ie. checking the state of the SWT peer. This works in JRE 7 & 8,
       * however SynthComboBoxUI is a package-private class in JRE 6.
       * So, the approach taken is to set the OS X actions for the combo box to the same ones used in Windows.
       */
      final InputMap inputMap = delegate.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false), "selectPrevious2");
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false), "selectNext2");
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class GraftableComboBoxDelegate extends JComboBox
   {
      private GraftableComboBoxDelegate()
      {
         super();
      }


      private GraftableComboBoxDelegate(final ComboBoxModel comboBoxModel)
      {
         super(comboBoxModel);
      }


      @Override
      final public boolean isShowing()
      {
         return true;
      }


      @Override
      final public void setSelectedIndex(final int anIndex)
      {
         super.setSelectedIndex(anIndex);

         for (final ComboBoxIndexSelectionListener indexSelectionListener : indexSelectionListeners)
            indexSelectionListener.indexSelected(GraftableComboBox.this, anIndex);
      }


      // For future reference...
      // The absence of this overridden method can cause problems when other non-graftable JComboBox instances are created. The problem seems to stem from Swing's
      // focus grabber which will try to automatically set popups to invisible when other combo boxes are activated...?
      // The particular case that affected our graftable combo's was the lose focus code (above) which tries to do the right thing and close the combo pop-up
      // if isPopupVisible() is true. Unfortunately when this method is not overridden, the UI will have reported that the Swing pop-up has been already closed behind the
      // scenes (by the sneaky focus grabber, it turns out), therefore the lose focus event won't try to close the pop-up.. meanwhile, the pop-up peer is still open.
      // So, our remedy is to override this method and return true if the -peer- is still visible - this is probably how it should be anyway.
      // This issue is worth noting because it's a 2nd run-in with the focus grabber, and the causes of the issues have been difficult to isolate.
      @Override
      final public boolean isPopupVisible()
      {
         /* The null check is because on creation this method will be fired off by a superclass. Unfortunately since the combo box is created before the
          * popup menu, the object ref will still be null.
          */
         if (graftablePopupMenu != null)
            return graftablePopupMenu.isVisible();
         else
            return false;
      }


      @Override
      final public void setPopupVisible(final boolean isVisible)
      {
         setPopupVisible(isVisible, true);
      }


      // This method is useful for when we want to change the visibility of the Swing combo box, eg, to refresh its size, while leaving the peer visible.
      final public void setPopupVisible(final boolean isVisible, final boolean setPeerVisibility)
      {
         /* To compare the graftable popup's resizing/laying out performance against Swing's usual popup machinery, comment out the call below and
          * replace with:
          *
          * super.setPopupVisible(isVisible);
          */
         graftablePopupMenu.setVisible(isVisible);

         if (setPeerVisibility)
         {
            graftablePopupMenu.setPeerVisible(isVisible);

            if (isVisible)
            {
               graftablePopupMenu.repaint();

               // Update our mouse bounds.
               graftablePopupMenuBounds = graftablePopupMenu.getDelegateComponent().getBounds();
               graftablePopupMenuBounds.translate(PopupMenuOffsetFromBaseOfComboBox.x, delegate.getHeight() + PopupMenuOffsetFromBaseOfComboBox.y);
            }

            /* If the peer visibility has changed, perform a final repaint on the combo box itself, otherwise the arrow button won't be in sync
             * with the state of the popup.
             */
            GraftableComboBox.this.repaint();
         }
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


   final private class GraftableTextFieldDefaultEditor extends BasicComboBoxEditor implements GraftableComponentPeer
   {
      static final private String SynthComboBoxTextFieldIdentifier = "ComboBox.textField";


      @Override
      final protected JTextField createEditorComponent()
      {
         GraftableComboBox.this.graftableTextField = new GraftableTextField(true);

         final JTextField component = graftableTextField.getDelegateComponent();

         // Set name to enable Nimbus styling.
         component.setName(SynthComboBoxTextFieldIdentifier);

         return component;
      }


      @Override
      final public void setItem(final Object anObject)
      {
         if (! isTextEntryMode)
            editor.setText((anObject != null) ? anObject.toString() : "");
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
      final public void setPeerVisible(final boolean isVisible)
      {
         throw new UnsupportedOperationException("Embedded text field cannot control the visibility of the combo box.");
      }


      @Override
      final public void requestPeerFocus()
      {
         throw new UnsupportedOperationException("Embedded text field cannot control the focus of the combo box.");
      }


      @Override
      final public void setPeerSize(final int width, final int height)
      {
         throw new UnsupportedOperationException("Embedded text field cannot control the size of the combo box.");
      }


      @Override
      final public void setPeerCursor(final GraftableComponentCursorType cursorType)
      {
         graftableComponentPeer.setPeerCursor(cursorType);
      }


      @Override
      final public void setPeerToolTipText(final String toolTipText)
      {
         throw new UnsupportedOperationException("Embedded text field cannot set a tooltip for the combo box.");
      }


      /* Ignore all of the following method invocations which are called from the embedded text field. For our purposes, the editor text field is
       * irrevocably locked in as a child component. This editor class will be disabled/detached when the combo box is set to non-editable, however there's no
       * real gain in trying to remove it completely and GC both it and the graftable text field, since the combo box can't have a null editor even when it's non-editable.
       */
      @Override
      final public void attachComponent(final GraftableComponent graftableComponent)
      {
      }


      @Override
      final public void detachComponent(final GraftableComponent graftableComponent)
      {
      }


      @Override
      final public void registerEventListener(final GraftableComponent graftableComponent, final GraftableComponentEventType eventType)
      {
      }


      @Override
      final public void registerEventListeners(final GraftableComponent graftableComponent, final Set<GraftableComponentEventType> eventTypes)
      {
      }


      @Override
      final public void deregisterEventListener(final GraftableComponent graftableComponent, final GraftableComponentEventType eventType)
      {
      }


      @Override
      final public void deregisterEventListeners(final GraftableComponent graftableComponent, final Set<GraftableComponentEventType> eventTypes)
      {
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static public interface ComboBoxIndexSelectionListener
   {
      public void indexSelected(final GraftableComboBox comboBox, final int selectedIndex);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   @Override
   final public JComboBox getDelegateComponent()
   {
      return delegate;
   }


   @Override
   final public Set<GraftableComponentEventType> getEventsOfInterest()
   {
      return EventsOfInterest;
   }


   @Override
   final public void detachFromPeer()
   {
      this.graftableComponentPeer = null;

      if (delegate.isEditable())
         graftableTextField.detachFromPeer();
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
      if ((delegate.getWidth() != width) || (delegate.getHeight() != height))
         delegate.setSize(width, height);

      if ((width > 0) && (height > 0))
      {
         outputImage = createOutputImage(width, height);

         // Pass the resize onto our child text field, but force it to defer the repaint - we can do it ourself, if it's not already being done by a parent.
         if (delegate.isEditable())
            graftableTextField.receiveControlResizedEventFromSWTPeer(controlEvent, graftableTextField.getDelegateComponent().getWidth(), graftableTextField.getDelegateComponent().getHeight(), true);

         // See GraftableSwingComponent for a note regarding the different beteen defer redraw and defer repaint.
         if (! deferRepaint)
            repaint();
      }
   }


   @Override
   final public void receiveFocusGainedEventFromSWTPeer(final org.eclipse.swt.events.FocusEvent focusEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveFocusGainedEventHandler(focusEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveFocusGainedEventHandler(focusEvent);
            }
         });
   }


   private void receiveFocusGainedEventHandler(final org.eclipse.swt.events.FocusEvent focusEvent)
   {
      if (delegate.isEditable())
         graftableTextField.receiveFocusGainedEventFromSWTPeer(focusEvent);
      else
         delegate.dispatchEvent(new FocusEvent(delegate, FocusEvent.FOCUS_GAINED));

      // If editable the nested text field will accept the focus, but we still need to repaint the focus border at the combo box level.
      repaint();
   }


   @Override
   final public void receiveFocusLostEventFromSWTPeer(final org.eclipse.swt.events.FocusEvent focusEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveFocusLostEventHandler(focusEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveFocusLostEventHandler(focusEvent);
            }
         });
   }


   private void receiveFocusLostEventHandler(final org.eclipse.swt.events.FocusEvent focusEvent)
   {
      if (delegate.isPopupVisible())
         delegate.setPopupVisible(false);

      if (delegate.isEditable())
         graftableTextField.receiveFocusLostEventFromSWTPeer(focusEvent);
      else
         delegate.dispatchEvent(new FocusEvent(delegate, FocusEvent.FOCUS_LOST));

      repaint();
   }

   
   @Override
   final public void receiveMouseEnteredEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveMouseEnteredEventHandler(mouseEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveMouseEnteredEventHandler(mouseEvent);
            }
         });
   }


   private void receiveMouseEnteredEventHandler(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (! delegate.isEditable())
      {
         delegate.dispatchEvent(new MouseEvent(delegate, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, mouseEvent.x, mouseEvent.y, 0, false));
         repaint();
      }
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
      if (! delegate.isEditable())
      {
         delegate.dispatchEvent(new MouseEvent(delegate, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, mouseEvent.x, mouseEvent.y, 0, false));
         repaint();
      }
      else if (lastMouseRegionComponent != null)
      {
         if (lastMouseRegionComponent == graftableTextField)
         {
            adjustMouseEventCoordinateForNestedComponent(mouseEvent, graftableTextField.getDelegateComponent());
            graftableTextField.receiveMouseExitedEventFromSWTPeer(mouseEvent);
         }
         else if (lastMouseRegionComponent == dropdownButton)
         {
            adjustMouseEventCoordinateForNestedComponent(mouseEvent, dropdownButton);
            dropdownButton.dispatchEvent(new MouseEvent(dropdownButton, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, mouseEvent.x, mouseEvent.y, 0, false));
            repaint();
         }

         lastMouseRegionComponent = null;
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
      graftableComponentPeer.requestPeerFocus();

      final int swingMouseButtonMask = convertSWTToSwingButtonMask(mouseEvent.button);

      if (delegate.isEditable())
      {
         if (lastMouseRegionComponent == graftableTextField)
         {
            adjustMouseEventCoordinateForNestedComponent(mouseEvent, graftableTextField.getDelegateComponent());
            graftableTextField.receiveMouseDownEventFromSWTPeer(mouseEvent);
         }
         else if (lastMouseRegionComponent == dropdownButton)
         {
            adjustMouseEventCoordinateForNestedComponent(mouseEvent, dropdownButton);
            dropdownButton.dispatchEvent(new MouseEvent(dropdownButton, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), swingMouseButtonMask, mouseEvent.x, mouseEvent.y, mouseEvent.count, true, mouseEvent.button));
            repaint();
         }
      }
      else
      {
         delegate.dispatchEvent(new MouseEvent(delegate, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), swingMouseButtonMask, mouseEvent.x, mouseEvent.y, mouseEvent.count, false, mouseEvent.button));
         repaint();
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

      if (delegate.isEditable())
      {
         boolean repaintNeeded = false;

         // Set aside a copy of the mouse event, because we may need the original to fire off events to other components.
         final org.eclipse.swt.events.MouseEvent clonedMouseEvent = cloneSwtMouseEvent(mouseEvent);

         if (lastMouseRegionComponent == graftableTextField)
         {
            adjustMouseEventCoordinateForNestedComponent(clonedMouseEvent, graftableTextField.getDelegateComponent());
            graftableTextField.receiveMouseUpEventFromSWTPeer(clonedMouseEvent);
         }
         else if (lastMouseRegionComponent == dropdownButton)
         {
            transferMouseEventToPopup(clonedMouseEvent, false);

            adjustMouseEventCoordinateForNestedComponent(clonedMouseEvent, dropdownButton);
            dropdownButton.dispatchEvent(new MouseEvent(dropdownButton, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), swingMouseButtonMask, clonedMouseEvent.x, clonedMouseEvent.y, clonedMouseEvent.count, false, clonedMouseEvent.button));
            repaintNeeded = true;
         }

         // If there is a mouse up event after a drag operation, and ALL mouse buttons have been released (otherwise it is considered an ongoing drag operation from
         // the originating component), we may need to fire off a couple of events: a mouse exited event for the previous mouse region component,
         // and a mouse entered for the new mouse region component.
         // Confusingly, the SWT mouse event will indicate the button mask immediately BEFORE the latest button release, hence the call to isSingleMouseButtonMask()
         // which for this event type translates to: have all mouse buttons been released.
         if (isSingleMouseButtonMask(mouseEvent.stateMask))
            repaintNeeded = handleEventsForNewMouseRegionComponent(mouseEvent) || repaintNeeded;

         if (repaintNeeded)
            repaint();
      }
      else
      {
         transferMouseEventToPopup(mouseEvent, false);

         delegate.dispatchEvent(new MouseEvent(delegate, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), swingMouseButtonMask, mouseEvent.x, mouseEvent.y, mouseEvent.count, false, mouseEvent.button));
         repaint();
      }
   }


   // Returns: true if a repaint is required, false otherwise.
   private boolean handleEventsForNewMouseRegionComponent(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      if (lastMouseRegionComponent == graftableTextField)
      {
         if (! graftableTextField.getDelegateComponent().getBounds().contains(mouseEvent.x, mouseEvent.y))
         {
            // ASSUMPTION: The text field doesn't question the bounds given in the mouse exited event. We don't adjust them here...
            graftableTextField.receiveMouseExitedEventFromSWTPeer(mouseEvent);

            if (dropdownButton.getBounds().contains(mouseEvent.x, mouseEvent.y))
            {
               lastMouseRegionComponent = dropdownButton;
               adjustMouseEventCoordinateForNestedComponent(mouseEvent, dropdownButton);
               dropdownButton.dispatchEvent(new MouseEvent(dropdownButton, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, mouseEvent.x, mouseEvent.y, 0, false));
               return true;
            }
            else
               lastMouseRegionComponent = null;
         }
      }
      else if (lastMouseRegionComponent == dropdownButton)
      {
         if (! dropdownButton.getBounds().contains(mouseEvent.x, mouseEvent.y))
         {
            // ASSUMPTION: The dropdown button doesn't question the bounds given in the mouse exited event. We don't adjust them here...
            dropdownButton.dispatchEvent(new MouseEvent(dropdownButton, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, mouseEvent.x, mouseEvent.y, 0, false));

            if (graftableTextField.getDelegateComponent().getBounds().contains(mouseEvent.x, mouseEvent.y))
            {
               lastMouseRegionComponent = graftableTextField;
               adjustMouseEventCoordinateForNestedComponent(mouseEvent, graftableTextField.getDelegateComponent());
               graftableTextField.receiveMouseEnteredEventFromSWTPeer(mouseEvent);
            }
            else
               lastMouseRegionComponent = null;

            return true;
         }
      }
      else if (graftableTextField.getDelegateComponent().getBounds().contains(mouseEvent.x, mouseEvent.y))
      {
         lastMouseRegionComponent = graftableTextField;
         adjustMouseEventCoordinateForNestedComponent(mouseEvent, graftableTextField.getDelegateComponent());
         graftableTextField.receiveMouseEnteredEventFromSWTPeer(mouseEvent);
      }
      else if (dropdownButton.getBounds().contains(mouseEvent.x, mouseEvent.y))
      {
         lastMouseRegionComponent = dropdownButton;
         adjustMouseEventCoordinateForNestedComponent(mouseEvent, dropdownButton);
         dropdownButton.dispatchEvent(new MouseEvent(dropdownButton, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, mouseEvent.x, mouseEvent.y, 0, false));
         return true;
      }
      
      return false;
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
      if (delegate.isEditable())
      {
         if (handleEventsForNewMouseRegionComponent(mouseEvent))
            repaint();
      }
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
      if (delegate.isEditable())
      {
         if (lastMouseRegionComponent == graftableTextField)
         {
            adjustMouseEventCoordinateForNestedComponent(mouseEvent, graftableTextField.getDelegateComponent());
            graftableTextField.receiveMouseDraggedEventFromSWTPeer(mouseEvent);
         }
         else if (lastMouseRegionComponent == dropdownButton)
            transferMouseEventToPopup(mouseEvent, true);
      }
      else
         transferMouseEventToPopup(mouseEvent, true);
   }


   private void transferMouseEventToPopup(final org.eclipse.swt.events.MouseEvent mouseEvent, final boolean isMouseDrag)
   {
      if (graftablePopupMenu.isVisible() && graftablePopupMenuBounds.contains(mouseEvent.x, mouseEvent.y))
      {
         final org.eclipse.swt.events.MouseEvent popupMouseEvent = cloneSwtMouseEvent(mouseEvent);

         popupMouseEvent.x -= graftablePopupMenuBounds.x;
         popupMouseEvent.y -= graftablePopupMenuBounds.y;

         if (isMouseDrag)
            graftablePopupMenu.receiveMouseDraggedEventFromSWTPeer(popupMouseEvent);
         else
            graftablePopupMenu.receiveMouseUpEventFromSWTPeer(popupMouseEvent);
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
      if (graftablePopupMenu.isVisible())
         graftablePopupMenu.receiveMouseWheelScrolledEventFromSWTPeer(mouseEvent);
   }


   @Override
   final public void receiveKeyPressedEventFromSWTPeer(final org.eclipse.swt.events.KeyEvent keyEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveKeyPressedEventHandler(keyEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveKeyPressedEventHandler(keyEvent);
            }
         });
   }


   private void receiveKeyPressedEventHandler(final org.eclipse.swt.events.KeyEvent keyEvent)
   {
      if (delegate.isEditable())
         graftableTextField.receiveKeyPressedEventFromSWTPeer(keyEvent);
      else
         super.receiveKeyPressedEventFromSWTPeer(keyEvent);
   }


   @Override
   final public void receiveKeyReleasedEventFromSWTPeer(final org.eclipse.swt.events.KeyEvent keyEvent)
   {
      if (SwingUtilities.isEventDispatchThread())
         receiveKeyReleasedEventHandler(keyEvent);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               receiveKeyReleasedEventHandler(keyEvent);
            }
         });
   }


   private void receiveKeyReleasedEventHandler(final org.eclipse.swt.events.KeyEvent keyEvent)
   {
      if (delegate.isEditable())
         graftableTextField.receiveKeyReleasedEventFromSWTPeer(keyEvent);
      else
         super.receiveKeyReleasedEventFromSWTPeer(keyEvent);
   }


   @Override
   final public void preDispose()
   {
      if (delegate.isEditable())
         graftableTextField.preDispose();

      graftablePopupMenu.preDispose();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSetEditable(final boolean isEditable)
   {
      if (isEditable != delegate.isEditable())
      {
         if (isEditable)
            graftableTextField.attachToPeer(editor);
         else
            graftableTextField.detachFromPeer();

         delegate.setEditable(isEditable);
      }
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public GraftableComboBoxPopupMenu getPopupMenu()
   {
      return graftablePopupMenu;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public ComboBoxModel getModel()
   {
      return delegate.getModel();
   }


   final public void setModel(final ComboBoxModel comboBoxModel)
   {
      delegate.setModel(comboBoxModel);
   }


   final public void setEditable(final boolean isEditable)
   {
      handleSetEditable(isEditable);
   }


   final public ComboBoxEditor getEditor()
   {
      return delegate.getEditor();
   }


   final public ListCellRenderer getRenderer()
   {
      return delegate.getRenderer();
   }


   final public void setRenderer(final ListCellRenderer listCellRenderer)
   {
      delegate.setRenderer(listCellRenderer);
   }


   final public boolean getTextEntryMode()
   {
      return isTextEntryMode;
   }


   final public void setTextEntryMode(final boolean isTextEntryMode)
   {
      this.isTextEntryMode = isTextEntryMode;
   }


   final public void setFont(final Font font)
   {
      delegate.setFont(font);
   }


   final public Dimension getSize()
   {
      return delegate.getSize();
   }


   final public boolean isPopupVisible()
   {
      return delegate.isPopupVisible();
   }


   final public void setPopupVisible(final boolean isVisible)
   {
      delegate.setPopupVisible(isVisible);
   }


   final public void setPopupVisible(final boolean isVisible, final boolean setPeerVisibility)
   {
      delegate.setPopupVisible(isVisible, setPeerVisibility);
   }


   final public int getItemCount()
   {
      return delegate.getItemCount();
   }


   final public int getSelectedIndex()
   {
      return delegate.getSelectedIndex();
   }


   final public Object getSelectedItem()
   {
      return delegate.getSelectedItem();
   }


   final public int getMaximumRowCount()
   {
      return delegate.getMaximumRowCount();
   }


   final public void setMaximumRowCount(final int maximumRowCount)
   {
      delegate.setMaximumRowCount(maximumRowCount);
   }


   final public void addActionListener(final ActionListener actionListener)
   {
      delegate.addActionListener(actionListener);
   }


   final public void removeActionListener(final ActionListener actionListener)
   {
      delegate.removeActionListener(actionListener);
   }


   final public void addItemListener(final ItemListener itemListener)
   {
      delegate.addItemListener(itemListener);
   }


   final public void removeItemListener(final ItemListener itemListener)
   {
      delegate.removeItemListener(itemListener);
   }


   final public void addIndexSelectionListener(final ComboBoxIndexSelectionListener indexSelectionListener)
   {
      indexSelectionListeners.add(indexSelectionListener);
   }
}