
package com.feedbactory.client.ui.browser;


import com.feedbactory.client.core.ConfigurationManager;
import com.feedbactory.client.core.FeedbactoryClientConstants;
import com.feedbactory.client.ui.browser.event.BrowserDisposeEvent;
import com.feedbactory.client.ui.browser.event.BrowserDisposeListener;
import com.feedbactory.client.ui.browser.event.BrowserLocationEvent;
import com.feedbactory.client.ui.browser.event.BrowserLocationListener;
import com.feedbactory.client.ui.browser.event.BrowserStatusTextEvent;
import com.feedbactory.client.ui.browser.event.BrowserStatusTextListener;
import com.feedbactory.client.ui.browser.event.BrowserTabEvent;
import com.feedbactory.client.ui.browser.event.BrowserTabListener;
import com.feedbactory.client.ui.browser.event.BrowserTitleEvent;
import com.feedbactory.client.ui.browser.event.BrowserTitleListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.OpenWindowListener;
import org.eclipse.swt.browser.WindowEvent;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolder2Listener;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;


final class BrowserEngineManager implements BrowserUIManagerService
{
   // SWT is not big on immutable value-style objects..
   static final private RGB BrowserSelectedTabColourOne = new RGB(253, 253, 254);
   static final private RGB BrowserSelectedTabColourTwo = new RGB(247, 248, 250);
   static final private RGB BrowserSelectedTabColourThree = new RGB(233, 236, 242);
   static final private RGB BrowserSelectedTabColourFour = new RGB(214, 217, 223);

   static final int[] BrowserSelectedTabColourPaintProportions = new int[] {40, 70, 100};

   static final private RGB BrowserDeselectedTabColourOne = new RGB(200, 200, 210);
   static final private RGB BrowserDeselectedTabColourTwo = new RGB(223, 226, 230);
   static final private RGB BrowserDeselectedTabColourThree = new RGB(235, 235, 235);
   static final private RGB BrowserDeselectedTabColourFour = new RGB(255, 255, 255);

   static final int[] BrowserDeselectedTabColourPaintProportions = new int[] {50, 80, 100};

   static final String BlankPageUrl = "about:blank";

   static final private int BrowserTabTitleMaximumLength = 40;

   static final private long MacOSXMinimumTimeBetweenTabOpen = 200L;

   final private BrowserLocationController browserLocationController;

   final private CTabFolder browserTabFolder;

   final private CTabItem browserNewTabItem;
   final private Font browserNewTabItemFont;

   final private Color[] selectedTabColours = new Color[4];
   final private Color[] deselectedTabColours = new Color[4];

   private BrowserService activeBrowserService;

   private String rightMouseButtonLink;
   private int macOSXLastRightClickTime;

   final private SelectionListener tabSelectionChangedListener = initialiseTabSelectionChangedListener();
   final private CTabFolder2Listener tabCloseListener = initialiseTabCloseListener();
   final private MouseListener mouseDoubleClickNewTabListener = initialiseMouseDoubleClickNewTabListener();

   final private List<BrowserTabListener> tabListeners = new CopyOnWriteArrayList<BrowserTabListener>();

   final private MouseListener newTabMouseListener = initialiseNewTabMouseListener();
   final private KeyListener keyShortcutListener = initialiseKeyShortcutListener();
   final private OpenWindowListener popupRedirectListener = initialisePopupRedirectListener();
   final private Listener rightClickMenuDisableListener = initialiseRightClickMenuDisableListener();


   BrowserEngineManager(final CTabFolder browserTabFolder)
   {
      this(new BrowserLocationController(), browserTabFolder);
   }


   BrowserEngineManager(final BrowserLocationController browserLocationController, final CTabFolder browserTabFolder)
   {
      this.browserLocationController = browserLocationController;
      this.browserTabFolder = browserTabFolder;

      browserNewTabItemFont = initialiseNewBrowserTabFont();
      browserNewTabItem = initialiseNewTabItem();

      initialise();
   }


   private SelectionListener initialiseTabSelectionChangedListener()
   {
      return new SelectionListener()
      {
         @Override
         final public void widgetSelected(final SelectionEvent selectionEvent)
         {
            tabSelectionChanged(selectionEvent);
         }


         @Override
         final public void widgetDefaultSelected(final SelectionEvent selectionEvent)
         {
         }
      };
   }


   private CTabFolder2Listener initialiseTabCloseListener()
   {
      return new CTabFolder2Adapter()
      {
         @Override
         final public void close(final CTabFolderEvent tabFolderEvent)
         {
            tabClosed(tabFolderEvent);
         }
      };
   }


   private MouseListener initialiseMouseDoubleClickNewTabListener()
   {
      return new MouseAdapter()
      {
         @Override
         final public void mouseDoubleClick(final MouseEvent mouseEvent)
         {
            openNewTab(BlankPageUrl, true);
         }
      };
   }


   private MouseListener initialiseNewTabMouseListener()
   {
      // Open links in new window on right mouse button click or left click while CTRL is pressed.
      return new MouseAdapter()
      {
         @Override
         final public void mouseUp(final MouseEvent mouseEvent)
         {
            handleMouseUpEvent(mouseEvent);
         }
      };
   }


   private KeyListener initialiseKeyShortcutListener()
   {
      // Key shortcuts.
      return new KeyAdapter()
      {
         @Override
         final public void keyPressed(final KeyEvent keyEvent)
         {
            // CTRL-T: open new tab.
            if ((keyEvent.stateMask == SWT.CTRL) && (keyEvent.keyCode == 116))
               openNewTab(BlankPageUrl, true);
         }
      };
   }


   private OpenWindowListener initialisePopupRedirectListener()
   {
      // Redirect popups (including shift-click) to open in another browser tab.
      return new OpenWindowListener()
      {
         @Override
         final public void open(final WindowEvent windowEvent)
         {
            redirectPopup(windowEvent);
         }
      };
   }


   private Listener initialiseRightClickMenuDisableListener()
   {
      // Disable right-click menus.
      return new Listener()
      {
         @Override
         final public void handleEvent(final Event event)
         {
            event.doit = false;
         }
      };
   }


   private Font initialiseNewBrowserTabFont()
   {
      final FontData[] newFontData = browserTabFolder.getFont().getFontData();

      for (int fontDataIndex = 0; fontDataIndex < newFontData.length; fontDataIndex ++)
         newFontData[fontDataIndex] = new FontData(newFontData[fontDataIndex].getName(), newFontData[fontDataIndex].getHeight() + 1, SWT.BOLD);

      return new Font(browserTabFolder.getDisplay(), newFontData);
   }


   private CTabItem initialiseNewTabItem()
   {
      final CTabItem tabItem = new CTabItem(browserTabFolder, SWT.NONE);
      tabItem.setFont(browserNewTabItemFont);
      tabItem.setText("  +  ");
      tabItem.setToolTipText("Open a new browser tab");

      return tabItem;
   }


   private void initialise()
   {
      browserTabFolder.setUnselectedCloseVisible(true);

      selectedTabColours[0] = new Color(browserTabFolder.getDisplay(), BrowserSelectedTabColourOne);
      selectedTabColours[1] = new Color(browserTabFolder.getDisplay(), BrowserSelectedTabColourTwo);
      selectedTabColours[2] = new Color(browserTabFolder.getDisplay(), BrowserSelectedTabColourThree);
      selectedTabColours[3] = new Color(browserTabFolder.getDisplay(), BrowserSelectedTabColourFour);

      deselectedTabColours[0] = new Color(browserTabFolder.getDisplay(), BrowserDeselectedTabColourOne);
      deselectedTabColours[1] = new Color(browserTabFolder.getDisplay(), BrowserDeselectedTabColourTwo);
      deselectedTabColours[2] = new Color(browserTabFolder.getDisplay(), BrowserDeselectedTabColourThree);
      deselectedTabColours[3] = new Color(browserTabFolder.getDisplay(), BrowserDeselectedTabColourFour);

      /* The docs on the gradient use aren't so clear, and the output is a little different than you might expect, but it can be interpreted as follows:
       *
       * - The final colour specified in the colour array is used to draw the border around the tab folder.
       * - The percentages array represents the proportions of the colour shifts across the tab.
       * - The colour at position 0 will be drawn at the beginning of the tab.
       * - The first percentage represents the proportion of the tab at which the colour gradient must have fully shifted to the colour specified at index 1.
       * - So specifying a proportion such as {25, 50, 75} will not produce an equal gradient distribution of four colours. We would have to use something like
       *      {33, 66, 100}. The easiest way to think of it is to consider that for four colours to be equally spread across a space, the gradient colour at the halfway
       *      point must be midway between the 2nd and 3rd colours, hence their full colour marker points are equally distant from the 50% point: 33% and 66%.
       */
      browserTabFolder.setSelectionBackground(selectedTabColours, BrowserSelectedTabColourPaintProportions, true);
      browserTabFolder.setBackground(deselectedTabColours, BrowserDeselectedTabColourPaintProportions, true);

      initialiseBrowserTabListeners();
   }


   private void initialiseBrowserTabListeners()
   {
      browserTabFolder.addSelectionListener(tabSelectionChangedListener);
      browserTabFolder.addCTabFolder2Listener(tabCloseListener);
      browserTabFolder.addMouseListener(mouseDoubleClickNewTabListener);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class BrowserEventsHandler implements BrowserLocationListener, BrowserStatusTextListener, BrowserTitleListener, BrowserDisposeListener
   {
      final private CTabItem tabItem;
      final private BrowserEngine browserEngine;


      private BrowserEventsHandler(final CTabItem tabItem, final BrowserEngine browserEngine)
      {
         this.tabItem = tabItem;
         this.browserEngine = browserEngine;
      }


      @Override
      final public boolean browserPageLocationChanging(final BrowserLocationEvent pageLocationChangingEvent)
      {
         /* Set the tab item text to the URL when the title is initially blank.
          * This prevents the newly opened tab from looking peculiar, until the browserTitleTextChanged() event is fired.
          */
         final String existingTabItemText = tabItem.getText();
         if ((existingTabItemText == null) || existingTabItemText.equals(""))
            setTabText(pageLocationChangingEvent.URL);

         /* Return a boolean indicating whether or not the page location change is permitted.
          * I may wish to look further into the existing implementation, since Internet Explorer will allow direct links to local files,
          * eg. executables which if run may do some harm, although there is at least a prominent warning.
          */
         return browserLocationController.browserPageLocationChanging(pageLocationChangingEvent);
      }


      private void setTabText(final String tabText)
      {
         if (tabText.length() <= BrowserTabTitleMaximumLength)
            // Pad the string to ensure that tabs have roughly the same width.
            tabItem.setText(String.format("%1$-" + BrowserTabTitleMaximumLength + 's', tabText));
         else
         {
            tabItem.setText(tabText.substring(0, BrowserTabTitleMaximumLength - 3) + "...");
            tabItem.setToolTipText(tabText);
         }
      }


      @Override
      final public void browserPageLocationChanged(final BrowserLocationEvent pageChangedEvent)
      {
      }


      @Override
      final public void browserStatusTextChanged(final BrowserStatusTextEvent statusTextEvent)
      {
         if (browserEngine == activeBrowserService)
            rightMouseButtonLink = statusTextEvent.statusText;
      }


      @Override
      final public void browserTitleTextChanged(final BrowserTitleEvent titleEvent)
      {
         setTabText(titleEvent.titleText);
      }


      @Override
      final public void browserDisposed(final BrowserDisposeEvent disposalEvent)
      {
         /* This handling is required in addition to the tabClosed() method, specifically for when the underlying browser control is
          * closed via means other than the tab, eg. window.close() in JavaScript.
          *
          * Although I can't find reference to it anywhere in the docs, it seems that widgetDisposed (repackaged by Feedbactory as browserDisposed here)
          * is always called immediately -before- a widget is actually disposed, therefore it's apparently safe to perform some cleanup of objects
          * attached to the widget, eg. listeners - see the tab closed event here.
          */
         tabItem.dispose();
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static class BrowserLocationController
   {
      boolean browserPageLocationChanging(final BrowserLocationEvent pageLocationChangingEvent)
      {
         return true;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void handleMouseUpEvent(final MouseEvent mouseEvent)
   {
      // SWT produces duplicate right click events in Mac OS X as of SWT 4.5
      if (ConfigurationManager.isRunningMacOSX)
      {
         if ((mouseEvent.time - macOSXLastRightClickTime) < MacOSXMinimumTimeBetweenTabOpen)
            return;

         macOSXLastRightClickTime = mouseEvent.time;
      }

      if (((mouseEvent.button == 2) || (mouseEvent.button == 3) || ((mouseEvent.button == 1) && ((mouseEvent.stateMask & SWT.CTRL) != 0))) &&
          BrowserUtilities.isAnHTTPLink(rightMouseButtonLink))
         openNewTab(rightMouseButtonLink, false);
   }


   private void openNewTab(final String url, final boolean switchToTab)
   {
      final BrowserService newBrowserService = createNewBrowserService();

      newBrowserService.openURL(url);

      if (switchToTab)
         switchToBrowserService(newBrowserService);
   }


   private void tabSelectionChanged(final SelectionEvent selectionEvent)
   {
      if (selectionEvent == null)
      {
         final CTabItem tabItem = (CTabItem) browserTabFolder.getSelection();
         activeBrowserService = (BrowserService) tabItem.getData();
      }
      else if (selectionEvent.item != browserNewTabItem)
         activeBrowserService = (BrowserService) ((CTabItem) selectionEvent.item).getData();
      else
      {
         selectionEvent.doit = false;
         openNewTab(BlankPageUrl, true);

         return;
      }

      final BrowserTabEvent browserTabEvent = new BrowserTabEvent(activeBrowserService);

      for (final BrowserTabListener tabListener : tabListeners)
         tabListener.activeBrowserTabChanged(browserTabEvent);
   }


   private void tabClosed(final CTabFolderEvent tabFolderEvent)
   {
      refreshTabControls(false);

      final CTabItem tabItem = (CTabItem) tabFolderEvent.item;

      final BrowserService browserService = (BrowserService) tabItem.getData();
      disconnectBrowserEventListeners((BrowserEngine) browserService);

      final BrowserTabEvent browserTabEvent = new BrowserTabEvent(browserService);

      for (final BrowserTabListener tabListener : tabListeners)
         tabListener.browserTabClosed(browserTabEvent);

      browserService.close();
   }


   private void refreshTabControls(final boolean tabOpened)
   {
      if (tabOpened)
      {
         if (browserTabFolder.getItemCount() > 2)
         {
            for (int tabIndex = 0; tabIndex < browserTabFolder.getItemCount() - 1; tabIndex ++)
               browserTabFolder.getItem(tabIndex).setShowClose(true);
         }
      }
      else
      {
         if (browserTabFolder.getItemCount() == 3)
         {
            for (int tabIndex = 0; tabIndex < browserTabFolder.getItemCount() - 1; tabIndex ++)
               browserTabFolder.getItem(tabIndex).setShowClose(false);
         }
      }
   }


   private void disconnectBrowserEventListeners(final BrowserEngine browserEngine)
   {
      /* If the browser has been directly closed via a JavaScript window.close() call, this method will eventually be called in response to a widgetDisposed call
       * on the browser (see above). Although I can't find reference to it anywhere in the docs, it seems that widgetDisposed (repackaged by Feedbactory
       * as browserDisposed) is always called immediately -before- a widget is actually disposed, therefore it should be safe to perform some cleanup
       * of the attached browser objects, ie. listeners here.
       * Still, I'd prefer to play things safe and perform an isClosed() check here to avoid a WIDGET_DISPOSED exception when attempting to detach the
       * listeners that are directly attached to the SWT browser object.
       */
      if (! browserEngine.isClosed())
      {
         // Clear the listeners which have registered via the BrowserService API. This will include the key shortcut listener.
         browserEngine.clearListeners();

         // Clear the additional listeners which we had directly registered with the Browser control.
         browserEngine.getBrowserControl().removeMouseListener(newTabMouseListener);
         browserEngine.getBrowserControl().removeOpenWindowListener(popupRedirectListener);

         if (! FeedbactoryClientConstants.IsDevelopmentProfile)
            browserEngine.getBrowserControl().removeListener(SWT.MenuDetect, rightClickMenuDisableListener);
      }
   }


   private void redirectPopup(final WindowEvent windowEvent)
   {
      final BrowserEngine newBrowserService = createNewBrowserService();
      windowEvent.browser = newBrowserService.getBrowserControl();
   }


   private boolean isBrowserDisplayThread()
   {
      return (browserTabFolder.getDisplay().getThread() == Thread.currentThread());
   }


   private BrowserEngine createNewBrowserService()
   {
      // Instantiate the browser first so that if it fails due to compatibility reasons, the tab folder is not left with a browser-less tab to deal with during shutdown.
      final Browser targetBrowser = new Browser(browserTabFolder, SWT.NONE);
      final CTabItem tabItem = new CTabItem(browserTabFolder, SWT.NONE, browserTabFolder.getItemCount() - 1);
      final BrowserEngine browserEngine = new BrowserEngine(targetBrowser);

      tabItem.setControl(targetBrowser);
      tabItem.setData(browserEngine);

      initialiseBrowserEventListeners(tabItem, browserEngine, targetBrowser);

      final BrowserTabEvent browserTabEvent = new BrowserTabEvent(browserEngine);
      for (final BrowserTabListener tabListener : tabListeners)
         tabListener.newBrowserTabOpened(browserTabEvent);

      refreshTabControls(true);

      return browserEngine;
   }


   private void initialiseBrowserEventListeners(final CTabItem tabItem, final BrowserEngine browserEngine, final Browser browser)
   {
      final BrowserEventsHandler browserEventsHandler = new BrowserEventsHandler(tabItem, browserEngine);
      browserEngine.addLocationListener(browserEventsHandler);
      browserEngine.addStatusTextEventListener(browserEventsHandler);
      browserEngine.addTitleEventListener(browserEventsHandler);
      browserEngine.addDisposeEventListener(browserEventsHandler);
      browserEngine.addKeyListener(keyShortcutListener);

      browser.addMouseListener(newTabMouseListener);
      browser.addOpenWindowListener(popupRedirectListener);

      // The right-click context menu can be handy for saving/viewing the HTML source during development.
      if (! FeedbactoryClientConstants.IsDevelopmentProfile)
         browser.addListener(SWT.MenuDetect, rightClickMenuDisableListener);
   }


   private void switchToBrowserService(final BrowserService browserService)
   {
      final CTabItem tabItem = getBrowserServiceTab(browserService);

      if ((tabItem != null) && (tabItem != browserTabFolder.getSelection()))
      {
         browserTabFolder.setSelection(tabItem);
         tabSelectionChanged(null);
      }
   }


   private CTabItem getBrowserServiceTab(final BrowserService browserService)
   {
      for (final CTabItem tabItem : browserTabFolder.getItems())
      {
         if (tabItem.getData() == browserService)
            return tabItem;
      }

      return null;
   }


   private void handlePreDispose()
   {
      // Clear the listeners for each browser engine (tab).
      for (final CTabItem tabItem : browserTabFolder.getItems())
      {
         if (tabItem != browserNewTabItem)
         {
            final BrowserEngine browserEngine = (BrowserEngine) tabItem.getData();
            disconnectBrowserEventListeners(browserEngine);
            browserEngine.preDispose();
         }
      }

      /* Finally clear the listeners on our tab control, which notify clients of new & closed tabs as well as the user switching tabs, and a listener
       * which allows the users to open new tabs by double-clicking on the tab control.
       *
       * Without all of these listeners, no client should be able to receive any further browser-related notifications, which means no more
       * events triggered via page loads, etc, which hopefully paves the way for a clean shutdown during the final shutdown phase.
       */
      browserTabFolder.removeSelectionListener(tabSelectionChangedListener);
      browserTabFolder.removeCTabFolder2Listener(tabCloseListener);
      browserTabFolder.removeMouseListener(mouseDoubleClickNewTabListener);
   }


   private void handleDispose()
   {
      browserNewTabItemFont.dispose();

      for (final Color color : selectedTabColours)
         color.dispose();

      for (final Color color : deselectedTabColours)
         color.dispose();
   }


   private BrowserService handleNewBrowserService()
   {
      if (isBrowserDisplayThread())
         return createNewBrowserService();
      else
      {
         /* When called from a non-display thread, if any RuntimeException isn't caught within the Runnable it will be automatically propagated up to the
          * SWT read & dispatch thread, and from there handled like any regular SWT exception, with an alert prompt displayed to the user, etc.
          * But in addition the syncExec() call will then also throw the nested RuntimeException back to the calling thread, which will likely also result
          * in the same handling and alert prompt to the user as above. To avoid this duplication, the RuntimeException is caught and propagated back
          * to the calling thread, without any handling on the SWT read & dispatch thread.
          */
         final AtomicReference<BrowserService> browserReference = new AtomicReference<BrowserService>();
         final AtomicReference<RuntimeException> exceptionReference = new AtomicReference<RuntimeException>();

         invokeAndWait(new Runnable()
         {
            @Override
            final public void run()
            {
               try
               {
                  browserReference.set(createNewBrowserService());
               }
               catch (final RuntimeException runtimeException)
               {
                  exceptionReference.set(runtimeException);
               }
            }
         });

         final RuntimeException runtimeException = exceptionReference.get();
         if (runtimeException != null)
            throw runtimeException;

         return browserReference.get();
      }
   }


   private BrowserService handleGetActiveBrowserService()
   {
      if (isBrowserDisplayThread())
         return activeBrowserService;
      else
      {
         final AtomicReference<BrowserService> browserReference = new AtomicReference<BrowserService>();

         invokeAndWait(new Runnable()
         {
            @Override
            final public void run()
            {
               browserReference.set(activeBrowserService);
            }
         });

         return browserReference.get();
      }
   }


   private void handleSetActiveBrowserService(final BrowserService browserService)
   {
      if (isBrowserDisplayThread())
         switchToBrowserService(browserService);
      else
      {
         invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               switchToBrowserService(browserService);
            }
         });
      }
   }


   private BrowserServicesDebugInformation handleGetBrowserServicesDebugInformation()
   {
      if (isBrowserDisplayThread())
         return handleGetBrowserServicesDebugInformationEDT();
      else
      {
         final AtomicReference<BrowserServicesDebugInformation> browserReference = new AtomicReference<BrowserServicesDebugInformation>();

         invokeAndWait(new Runnable()
         {
            @Override
            final public void run()
            {
               browserReference.set(handleGetBrowserServicesDebugInformationEDT());
            }
         });

         return browserReference.get();
      }
   }


   private BrowserServicesDebugInformation handleGetBrowserServicesDebugInformationEDT()
   {
      if ((activeBrowserService != null) && (! activeBrowserService.isClosed()))
      {
         final String browserUserAgent = (String) activeBrowserService.evaluateJavascript("return navigator.userAgent;");

         final String[] browserServiceURLs = new String[browserTabFolder.getItemCount() - 1];
         int activeBrowserServiceIndex = -1;

         BrowserService browserService;
         for (int tabIndex = 0; tabIndex < browserTabFolder.getItemCount() - 1; tabIndex ++)
         {
            browserService = (BrowserService) browserTabFolder.getItem(tabIndex).getData();
            browserServiceURLs[tabIndex] = browserService.getURL();

            if (browserService == activeBrowserService)
               activeBrowserServiceIndex = tabIndex;
         }

         return new BrowserServicesDebugInformation(browserUserAgent, browserServiceURLs, activeBrowserServiceIndex);
      }
      else // Indicates no active browser, which is possible after initialisation and before any browser instances are created.
         return null;
   }


   private void handleAddBrowserTabEventListener(final BrowserTabListener browserTabEventListener)
   {
      if (isBrowserDisplayThread())
         tabListeners.add(browserTabEventListener);
      else
      {
         invokeAndWait(new Runnable()
         {
            @Override
            final public void run()
            {
               tabListeners.add(browserTabEventListener);
            }
         });
      }
   }


   private void handleRemoveBrowserTabEventListener(final BrowserTabListener browserTabEventListener)
   {
      if (isBrowserDisplayThread())
         tabListeners.remove(browserTabEventListener);
      else
      {
         invokeAndWait(new Runnable()
         {
            @Override
            final public void run()
            {
               tabListeners.remove(browserTabEventListener);
            }
         });
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void preDispose()
   {
      handlePreDispose();
   }


   final void dispose()
   {
      handleDispose();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public BrowserService newBrowserService()
   {
      return handleNewBrowserService();
   }


   @Override
   final public BrowserService getActiveBrowserService()
   {
      return handleGetActiveBrowserService();
   }


   @Override
   final public void setActiveBrowserService(final BrowserService browserService)
   {
      handleSetActiveBrowserService(browserService);
   }


   @Override
   final public boolean isBrowserThread()
   {
      return (browserTabFolder.getDisplay().getThread() == Thread.currentThread());
   }


   @Override
   final public void invokeLater(final Runnable runnable)
   {
      browserTabFolder.getDisplay().asyncExec(runnable);
   }


   @Override
   final public void invokeAndWait(final Runnable runnable)
   {
      browserTabFolder.getDisplay().syncExec(runnable);
   }


   @Override
   final public BrowserServicesDebugInformation getBrowserServicesDebugInformation()
   {
      return handleGetBrowserServicesDebugInformation();
   }


   @Override
   final public void addBrowserTabEventListener(final BrowserTabListener browserTabEventListener)
   {
      handleAddBrowserTabEventListener(browserTabEventListener);
   }


   @Override
   final public void removeBrowserTabEventListener(final BrowserTabListener browserTabEventListener)
   {
      handleRemoveBrowserTabEventListener(browserTabEventListener);
   }


   @Override
   final public void clearBrowserHistory()
   {
      Browser.clearSessions();
   }
}