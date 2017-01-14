/* Memos:
 * - This top-level graftable swing framework class (and hidden displayable frame) is required for any Swing controls which are not initialised until they are attached
 *      to a displayable frame. Examples are components using Synth-based UI (Nimbus), where controls such as the combo box's dropdown button, or a popup menu's
 *      scrollbar button size not ready for display until attached to a displayable frame. If we could get around this and manually initialise the UI properties ourself,
 *      it may well be worth considering doing away with this top level framework class altogether.
 *      Refer to SynthStyle.installDefaults(SynthContext context), which is ultimately called via ancestor property change on addNotify.
 */

package com.feedbactory.client.ui.component.graftable;


import java.awt.*;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;


final public class GraftableComponentSwingFramework
{
   final private JFrame backingFrame;


   public GraftableComponentSwingFramework()
   {
      initialiseKeyboardFocusManager();

      backingFrame = new JFrame();

      initialise();
   }


   private void initialiseKeyboardFocusManager()
   {
      /* Changes in DefaultKeyboardFocusManager.dispatchEvent for the WINDOW_GAINED_FOCUS event in JDK7, in particular the check on the Window's isVisible() method,
       * have forced some changes in the framework. We can't simply override our backing frame's isVisible() method with true, since this will actually make it display on
       * the screen. The changes come to a head when dealing with focus gained events. In JDK6 our graftable components could simply receive the focus gained events
       * received from their own dispatchEvent method and the DefaultKeyboardFocusManager would recognise them as the new focus owners, however the JDK7 implementation
       * brings things to a halt by checking the isVisible() state as mentioned above. Hence we need to intercept the peer events received before they are even passed to
       * dispatchEvent(). Thankfully we can do this (see below), at the same time forcing our window to be the focused window when necessary.
       * 
       * An alternative method to this is similar to the isOpaque() hack also for JRE7 on the SwingNimbusFrame, which temporarily sets a flag for the method - we could do
       * the same for isVisible() on an overridden frame while our keyboard focus manager is processing a WINDOW_GAINED_FOCUS event for the backingFrame.
       * This works fine, however I'm not at ease with the possibility that somewhere within the chain of calls while isVisible() is true, the (intentionally) hidden
       * backingFrame itself may be shown on the screen!
       * 
       * Also of note here is the order of operations. If the replacement keyboard focus manager is not installed before the backing frame (and possibly the first
       * application frame), there will be problems with tabbing between focus elements on other windows.
       * 
       * If there are further problems, also consider looking into whether we need to override (and call) any other methods of the DefaultKeyboardFocusManager.
       */

      if (! System.getProperty("java.version").startsWith("1.6"))
         KeyboardFocusManager.setCurrentKeyboardFocusManager(new GraftableComponentSwingFrameworkKeyboardFocusManager());
   }


   private void initialise()
   {
      /* We don't want any double buffering to occur from the Swing end since we are painting the components to image buffers anyway.
       * The double buffering must be disabled from the top of the component hierarchy to prevent components lower in the hierarchy from being double buffered.
       * To verify whether double buffering is occurring on components lower in the hierarchy, try breakpointing on one of the components' paintComponent methods,
       * and examining the call stack...
       */
      backingFrame.getRootPane().setDoubleBuffered(false);
      backingFrame.getLayeredPane().setDoubleBuffered(false);
      ((JComponent) backingFrame.getContentPane()).setDoubleBuffered(false);

      backingFrame.getContentPane().setLayout(null);

      /* Make our root frame "displayable" - ie. peer created and ready to be laid out - but don't display anything.
       * The graftable framework is dead in the water without this little trick...
       */
      backingFrame.pack();

      /* Previously there was a call here to expand the size of the backing frame to the maximum window bounds. This was done for two reasons:
       * 
       * 1) To prevent popup windows from being forced to be rendered as heavyweights, thus appearing outside of our framework (at least on Linux).
       *       Refer to PopupFactory.fitsOnScreen() for the logic... The basic gist is that popups which can't fit within their containing Window/Frame are forced to appear as
       *       heavyweights, which means that our framework was producing a 'ghost' popup for combo boxes. And crashing on Ubuntu.
       * 
       *    - This requirement changed after we updated the graftable popup menu to circumvent Swing's popup system and instead perform its own resizing and laying out.
       *
       * 2) To prevent mouse events from being ignored for combo box pop up menu lists, where the mouse coordinates have extended beyond the limits of this backing frame,
       *       even though they may be well within the SWT peer's bounds. Refer to BasicComboPopup.mouseMoved(),
       *       which makes a check on the list's computeVisibleRect() method.
       *
       *    - This bounds check is still performed, however the graftable popup menu is no longer being attached to this backing frame (except to initialise Synth properties).
       *         So the bounds checking of computeVisibleRect() is done purely on an orphan JPopupMenu, the size of which is manually set by us to be large enough to
       *         handle the bounds of the component.
       *
       * So for now I'll remove the manual bounds set on the backing frame, but if any other problems crop up with other components I may have to temporarily reinstate this
       * code until a more elegant solution is found.
       *
       * final Rectangle maximumScreenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
       * backingFrame.setBounds(maximumScreenBounds);
       */
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class GraftableComponentSwingFrameworkKeyboardFocusManager extends DefaultKeyboardFocusManager
   {
      @Override
      final public boolean dispatchEvent(final AWTEvent awtEvent)
      {
         /* Due to the above-mentioned change in JDK7's DefaultKeyboardFocusManager.dispatchEvent for the WINDOW_GAINED_FOCUS event, we need to intercept
          * that event here otherwise our faux backing frame won't be recognised as having the focus, and in turn our controls won't be focus painted.
          * 
          * The WINDOW_GAINED_FOCUS event is actually dispatched from the DefaultKeyboardFocusManager.dispatchEvent's FOCUS_GAINED case:
          * 
          *    sendMessage(newFocusedWindow, new WindowEvent(newFocusedWindow, WindowEvent.WINDOW_GAINED_FOCUS, currentFocusedWindow));
          * 
          * What was happening in JDK7 was that our window was rejecting the WINDOW_GAINED_FOCUS event because there is a check on the isVisible() method (see above).
          * The follow-up code in the FOCUS_GAINED case checks to see whether rejection has occurred and if it has, bails out. Its method of checking for
          * rejection is the following code immediately after firing the event:
          * 
          * if (newFocusedWindow != getGlobalFocusedWindow())
          * 
          * where the global focused window will still be null or the old value if focus has been rejected. If we can beat this check, we're good to go.
          * Luckily for us we we can intercept the WINDOW_GAINED_FOCUS event right here and manually override the global focused window ourselves.
          * Not only does this pass the check in the FOCUS_GAINED case, but it also allows us to bail out on the WINDOW_GAINED_FOCUS before the
          * isVisible() check is even performed, thanks to the check in the first few lines: if (newFocusedWindow == oldFocusedWindow) break;
          */
         if (awtEvent.getID() == WindowEvent.WINDOW_GAINED_FOCUS)
         {
            final WindowEvent windowEvent = (WindowEvent) awtEvent;

            if (windowEvent.getWindow() == backingFrame)
            {
               setGlobalActiveWindow(backingFrame);
               setGlobalFocusedWindow(backingFrame);
            }
         }

         return super.dispatchEvent(awtEvent);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final public void addTopLevelSwingComponent(final GraftableSwingComponent component)
   {
      backingFrame.getContentPane().add(component.getDelegateComponent());
   }


   final public void removeTopLevelSwingComponent(final GraftableSwingComponent component)
   {
      backingFrame.getContentPane().remove(component.getDelegateComponent());
   }


   final public void dispose()
   {
      backingFrame.dispose();
   }
}