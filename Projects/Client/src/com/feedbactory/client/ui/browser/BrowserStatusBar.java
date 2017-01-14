
package com.feedbactory.client.ui.browser;


import com.feedbactory.client.core.ConfigurationManager;
import com.feedbactory.client.ui.browser.event.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;


final class BrowserStatusBar
{
   static final private int StatusBarTextFontSize = ConfigurationManager.isRunningMacOSX ? 12 : 9;
   static final Point StatusBarTextDrawOffset = new Point(8, 0);

   final private Canvas delegate;

   final private Color gradientStartColour;
   final private Color gradientEndColour;

   final private Font statusBarFont;

   private String statusBarText = "";


   BrowserStatusBar(final Composite parentComposite, final BrowserUIManagerService browserManagerService, final RGB gradientStartColour, final RGB gradientEndColour)
   {
      delegate = new Canvas(parentComposite, SWT.DOUBLE_BUFFERED);

      this.gradientStartColour = new Color(parentComposite.getDisplay(), gradientStartColour);
      this.gradientEndColour = new Color(parentComposite.getDisplay(), gradientEndColour);

      statusBarFont = initialiseDelegateFont(parentComposite);

      initialise(browserManagerService);
   }


   private Font initialiseDelegateFont(final Composite parentComposite)
   {
      final FontData[] newFontData = parentComposite.getDisplay().getSystemFont().getFontData();

      for (int fontDataIndex = 0; fontDataIndex < newFontData.length; fontDataIndex ++)
         newFontData[fontDataIndex] = new FontData(newFontData[fontDataIndex].getName(), StatusBarTextFontSize, SWT.NORMAL);

      return new Font(parentComposite.getDisplay(), newFontData);
   }


   private void initialise(final BrowserUIManagerService browserManagerService)
   {
      delegate.setFont(statusBarFont);

      initialiseDelegatePaintListener();

      browserManagerService.addBrowserTabEventListener(new BrowserEventsHandler());
   }


   private void initialiseDelegatePaintListener()
   {
      delegate.addPaintListener(new PaintListener()
      {
         @Override
         final public void paintControl(final PaintEvent paintEvent)
         {
            paintEvent.gc.setTextAntialias(SWT.ON);

            final Point controlPanelSize = delegate.getSize();

            paintEvent.gc.setForeground(gradientStartColour);
            paintEvent.gc.setBackground(gradientEndColour);
            paintEvent.gc.fillGradientRectangle(0, 0, controlPanelSize.x, controlPanelSize.y, true);

            paintEvent.gc.setForeground(delegate.getDisplay().getSystemColor(SWT.COLOR_BLACK));
            paintEvent.gc.drawString(statusBarText, StatusBarTextDrawOffset.x, StatusBarTextDrawOffset.y, true);
         }
      });
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class BrowserEventsHandler implements BrowserStatusTextListener, BrowserTabListener
   {
      private BrowserService activeBrowserService;


      @Override
      final public void newBrowserTabOpened(final BrowserTabEvent browserTabEvent)
      {
         browserTabEvent.browserService.addStatusTextEventListener(this);
      }


      @Override
      final public void activeBrowserTabChanged(final BrowserTabEvent browserTabEvent)
      {
         activeBrowserService = browserTabEvent.browserService;

         /* Don't switch to the status bar text for the current browser service, as this is affected by: 1) the BrowserEngine's StatusTextEvent which is
          * is often fired with a blank string for the browser service as the user switches between tabs, and 2) The status text is always updated to
          * whatever the mouse pointer is/was hovering over; it would be wrong to switch it back to that on switching tabs.
          */
         setStatusBarText("");
      }


      @Override
      final public void browserTabClosed(final BrowserTabEvent browserTabEvent)
      {
         browserTabEvent.browserService.removeStatusEventListener(this);
      }


      @Override
      final public void browserStatusTextChanged(final BrowserStatusTextEvent statusTextEvent)
      {
         if (statusTextEvent.browserService == activeBrowserService)
            setStatusBarText(statusTextEvent.statusText);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void setStatusBarText(final String statusBarText)
   {
      this.statusBarText = (statusBarText != null) ? statusBarText : "";
      delegate.redraw();
   }


   private void handleDispose()
   {
      gradientStartColour.dispose();
      gradientEndColour.dispose();

      statusBarFont.dispose();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final Control getDelegate()
   {
      return delegate;
   }


   final void dispose()
   {
      handleDispose();
   }
}