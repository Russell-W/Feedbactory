/* Preconditions:
 * - Nimbus L&F must be active at the creation of the first instance of the frame.
 *
 * Enhancements:
 * - Allow non-resizable.
 * - Customisation of frame control buttons, ie. dialog only, disable resize, no buttons...
 */

package com.feedbactory.client.ui.component;


import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;


public class SWTFrame
{
   final protected Shell frameShell;

   protected Image titleBarIconImage;


   public SWTFrame(final Display display)
   {
      if (display.getThread() != Thread.currentThread())
         throw new SWTException(SWT.ERROR_THREAD_INVALID_ACCESS);

      frameShell = new Shell(display);
   }


   public SWTFrame(final Display display, final int style)
   {
      if (display.getThread() != Thread.currentThread())
         throw new SWTException(SWT.ERROR_THREAD_INVALID_ACCESS);

      frameShell = new Shell(display, style);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleSetFrameIcon(final Image frameIcon)
   {
      frameShell.setImage(frameIcon);
      titleBarIconImage = frameIcon;
   }


   private void handleDispose()
   {
      frameShell.dispose();

      if (titleBarIconImage != null)
         titleBarIconImage.dispose();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   public Shell getShell()
   {
      return frameShell;
   }


   public Composite getFrameRootComponent()
   {
      return frameShell;
   }


   public void setFrameIcon(final Image frameIcon)
   {
      handleSetFrameIcon(frameIcon);
   }


   public void setFrameTitle(final String frameTitle)
   {
      frameShell.setText(frameTitle);
   }


   public Point getMinimumSize()
   {
      return frameShell.getMinimumSize();
   }


   public void setMinimumSize(final int width, final int height)
   {
      frameShell.setMinimumSize(width, height);
   }


   public Rectangle getBounds()
   {
      return frameShell.getBounds();
   }


   public Rectangle getRestoredBounds()
   {
      return frameShell.getBounds();
   }


   public void setRestoredBounds(final Rectangle bounds)
   {
      frameShell.setBounds(bounds);
   }


   public void setRestoredSize(final int width, final int height)
   {
      frameShell.setSize(width, height);
   }


   public boolean isMaximised()
   {
      return frameShell.getMaximized();
   }


   public void setMaximised(final boolean setMaximised)
   {
      frameShell.setMaximized(setMaximised);
   }


   public Point getLocation()
   {
      return frameShell.getLocation();
   }


   public void setRestoredLocation(final int x, final int y)
   {
      frameShell.setLocation(x, y);
   }


   public void setVisible(final boolean isVisible)
   {
      frameShell.setVisible(isVisible);
   }


   public void addControlListener(final ControlListener controlListener)
   {
      frameShell.addControlListener(controlListener);
   }


   public void removeControlListener(final ControlListener controlListener)
   {
      frameShell.removeControlListener(controlListener);
   }


   public void addShellListener(final ShellListener shellListener)
   {
      frameShell.addShellListener(shellListener);
   }


   public void removeShellListener(final ShellListener shellListener)
   {
      frameShell.removeShellListener(shellListener);
   }


   public boolean isDisposed()
   {
      return frameShell.isDisposed();
   }


   public void dispose()
   {
      handleDispose();
   }
}