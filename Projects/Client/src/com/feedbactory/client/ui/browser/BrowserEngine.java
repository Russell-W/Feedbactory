/* Memos:
 * - For broadcasting to the event listeners, I lean towards lists due to the predictability of firing events in a given order. If the application
 *   requires 'core' listeners to be notified before auxilliary listeners, this can be done.
 *
 * - The SWT docs indicate that BrowserFunctions are automatically disposed of when the enclosing browser object is disposed.
 *
 * - Some OS and browser engine combinations won't always produce a page load completed event. To counter this, each browser engine is fitted with a
 *   scheduled task executor that synthesizes a page load completion event at a fixed time after any page load progress event has been received without
 *   receiving a follow-up page load completion event. On Mac OS X especially this mechanism seems to be the difference between Feedbactory appearing
 *   responseless to many feedback pages to actually firing the events in a reasonable timeframe. It also provides a handy kicker on all platforms
 *   when the user navigates between feedback items using the keyboard instead of the mouse.
 */

package com.feedbactory.client.ui.browser;


import com.feedbactory.client.core.ClientUtilities;
import com.feedbactory.client.ui.browser.event.BrowserDisposeEvent;
import com.feedbactory.client.ui.browser.event.BrowserDisposeListener;
import com.feedbactory.client.ui.browser.event.BrowserLoadProgressEvent;
import com.feedbactory.client.ui.browser.event.BrowserLoadProgressListener;
import com.feedbactory.client.ui.browser.event.BrowserLocationEvent;
import com.feedbactory.client.ui.browser.event.BrowserLocationListener;
import com.feedbactory.client.ui.browser.event.BrowserMouseEvent;
import com.feedbactory.client.ui.browser.event.BrowserMouseListener;
import com.feedbactory.client.ui.browser.event.BrowserStatusTextEvent;
import com.feedbactory.client.ui.browser.event.BrowserStatusTextListener;
import com.feedbactory.client.ui.browser.event.BrowserTitleEvent;
import com.feedbactory.client.ui.browser.event.BrowserTitleListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.browser.StatusTextEvent;
import org.eclipse.swt.browser.StatusTextListener;
import org.eclipse.swt.browser.TitleEvent;
import org.eclipse.swt.browser.TitleListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;


final class BrowserEngine implements BrowserService
{
   final private Browser browser;
   final private boolean isInternetExplorer;
   final private boolean isWebKit;

   private String pendingURL = "";
   private String title = "";
   private boolean isLoadingPage;

   final private List<BrowserLocationListener> locationListeners = new CopyOnWriteArrayList<BrowserLocationListener>();
   final private List<BrowserLoadProgressListener> loadProgressListeners = new CopyOnWriteArrayList<BrowserLoadProgressListener>();
   final private List<BrowserStatusTextListener> statusTextListeners = new CopyOnWriteArrayList<BrowserStatusTextListener>();
   final private List<BrowserTitleListener> titleListeners = new CopyOnWriteArrayList<BrowserTitleListener>();
   final private List<KeyListener> keyListeners = new CopyOnWriteArrayList<KeyListener>();
   final private List<BrowserMouseListener> mouseListeners = new CopyOnWriteArrayList<BrowserMouseListener>();
   final private List<BrowserDisposeListener> disposeEventListeners = new CopyOnWriteArrayList<BrowserDisposeListener>();

   final private BrowserEventsDispatcher browserEventsDispatcher = new BrowserEventsDispatcher();

   final private Map<BrowserServiceFunction, BrowserFunction> registeredFunctions = new HashMap<BrowserServiceFunction, BrowserFunction>();


   BrowserEngine(final Browser browser)
   {
      this.browser = browser;
      isInternetExplorer = browser.getBrowserType().equals("ie");
      isWebKit = browser.getBrowserType().equals("webkit");

      initialise();
   }


   private void initialise()
   {
      browser.addLocationListener(browserEventsDispatcher);
      browser.addProgressListener(browserEventsDispatcher);
      browser.addStatusTextListener(browserEventsDispatcher);
      browser.addTitleListener(browserEventsDispatcher);
      browser.addKeyListener(browserEventsDispatcher);
      browser.addMouseListener(browserEventsDispatcher);
      browser.addDisposeListener(browserEventsDispatcher);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class BrowserEventsDispatcher implements LocationListener, ProgressListener, StatusTextListener, TitleListener, KeyListener, MouseListener, DisposeListener
   {
      static final private long SynthesizedLoadProgressCompleteEventDelayMilliseconds = 1100L;

      final private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
      final private Runnable pageLoadCompletionRunnable = new PageLoadCompletionTask();
      private Future<?> pageLoadCompletedTask;


      private BrowserEventsDispatcher()
      {
         initialise();
      }


      private void initialise()
      {
         executor.setKeepAliveTime(10L, TimeUnit.SECONDS);
         executor.allowCoreThreadTimeOut(true);
      }


      /****************************************************************************
       *
       ***************************************************************************/


      @Override
      final public void changing(final LocationEvent event)
      {
         /* For both IE and WebKit this event always appears to be fired with event.top == false, even when the location for the event
          * would seem to indicate a top level page.
          * It shouldn't be used by the UI to indicate whether or not an overall web page is still loading.
          */
         final BrowserLocationEvent locationChangeEvent = new BrowserLocationEvent(BrowserEngine.this, event.top, event.location);

         for (final BrowserLocationListener locationListener : locationListeners)
         {
            if (! locationListener.browserPageLocationChanging(locationChangeEvent))
               event.doit = false;
         }
      }


      @Override
      final public void changed(final LocationEvent event)
      {
         // Unlike the location changing event, this event does appear to correctly indicate whether or not an event relates to a top level page.
         final BrowserLocationEvent browserPageChangeEvent = new BrowserLocationEvent(BrowserEngine.this, event.top, event.location);

         for (final BrowserLocationListener locationListener : locationListeners)
            locationListener.browserPageLocationChanged(browserPageChangeEvent);
      }


      @Override
      final public void changed(final ProgressEvent event)
      {
         if (isInternetExplorer)
         {
            /* IE will often not fire the page load completion event when loading nested page components.
             * A workaround is to look for its progress changed event when both current and total are equal to zero, which appears to
             * indicate that the component has finished loading.
             */
            if ((event.current == 0) && (event.total == 0))
            {
               completed(event);
               return;
            }
         }
         else if (isWebKit)
         {
            /* The old WebKit on Windows at least is prone to following up a full web page completion with residual
             * load progress events, where the current == 0 and total == 100. This event is dropped, to prevent further processing by listeners.
             */
            if ((event.current == 0) && (event.total == 100))
               return;
         }

         processLoadProgressChangedEvent(event);
      }


      private void processLoadProgressChangedEvent(final ProgressEvent event)
      {
         // See class header comments.
         scheduleLoadProgressCompleteEvent();

         updatePageLoadingStatus(true);

         final BrowserLoadProgressEvent browserLoadProgressChangeEvent = new BrowserLoadProgressEvent(BrowserEngine.this, event.current, event.total);

         for (final BrowserLoadProgressListener loadProgressListener : loadProgressListeners)
            loadProgressListener.browserPageLoadProgressChanged(browserLoadProgressChangeEvent);
      }


      private void scheduleLoadProgressCompleteEvent()
      {
         if (! executor.isShutdown())
         {
            cancelPageLoadCompletedTask();
            pageLoadCompletedTask = executor.schedule(pageLoadCompletionRunnable, SynthesizedLoadProgressCompleteEventDelayMilliseconds, TimeUnit.MILLISECONDS);
         }
      }


      private void cancelPageLoadCompletedTask()
      {
         if (pageLoadCompletedTask != null)
         {
            pageLoadCompletedTask.cancel(false);
            pageLoadCompletedTask = null;
         }
      }


      @Override
      final public void completed(final ProgressEvent event)
      {
         cancelPageLoadCompletedTask();
         processLoadProgressCompletedEvent(event.current, event.total);
      }


      private void processLoadProgressCompletedEvent(final int current, final int total)
      {
         updatePageLoadingStatus(false);

         final BrowserLoadProgressEvent browserLoadProgressChangeEvent = new BrowserLoadProgressEvent(BrowserEngine.this, current, total);

         for (final BrowserLoadProgressListener loadProgressListener : loadProgressListeners)
            loadProgressListener.browserPageLoadCompleted(browserLoadProgressChangeEvent);
      }


      @Override
      final public void changed(final StatusTextEvent event)
      {
         final BrowserStatusTextEvent browserStatusTextEvent = new BrowserStatusTextEvent(BrowserEngine.this, event.text);

         for (final BrowserStatusTextListener statusTextEventListener : statusTextListeners)
            statusTextEventListener.browserStatusTextChanged(browserStatusTextEvent);
      }


      @Override
      final public void changed(final TitleEvent event)
      {
         updateTitle(event.title);

         final BrowserTitleEvent browserTitleEvent = new BrowserTitleEvent(BrowserEngine.this, event.title);

         for (final BrowserTitleListener titleEventListener : titleListeners)
            titleEventListener.browserTitleTextChanged(browserTitleEvent);
      }


      @Override
      final public void keyPressed(final KeyEvent keyEvent)
      {
         for (final KeyListener keyListener : keyListeners)
            keyListener.keyPressed(keyEvent);
      }


      @Override
      final public void keyReleased(final KeyEvent keyEvent)
      {
         for (final KeyListener keyListener : keyListeners)
            keyListener.keyReleased(keyEvent);
      }


      @Override
      final public void mouseDoubleClick(final MouseEvent mouseEvent)
      {
      }


      @Override
      final public void mouseDown(final MouseEvent mouseEvent)
      {
         final BrowserMouseEvent browserMouseEvent = new BrowserMouseEvent(BrowserEngine.this, mouseEvent.button, mouseEvent.x, mouseEvent.y);

         for (final BrowserMouseListener mouseEventListener : mouseListeners)
            mouseEventListener.mousePressed(browserMouseEvent);
      }


      @Override
      final public void mouseUp(final MouseEvent mouseEvent)
      {
         final BrowserMouseEvent browserMouseEvent = new BrowserMouseEvent(BrowserEngine.this, mouseEvent.button, mouseEvent.x, mouseEvent.y);

         for (final BrowserMouseListener mouseEventListener : mouseListeners)
            mouseEventListener.mouseReleased(browserMouseEvent);
      }


      @Override
      final public void widgetDisposed(final DisposeEvent disposeEvent)
      {
         final BrowserDisposeEvent browserDisposedEvent = new BrowserDisposeEvent(BrowserEngine.this);

         for (final BrowserDisposeListener disposeEventListener : disposeEventListeners)
            disposeEventListener.browserDisposed(browserDisposedEvent);
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private void synthesizeLoadProgressCompletedEvent(final int current, final int total)
      {
         processLoadProgressCompletedEvent(current, total);
      }


      private boolean isShutdown()
      {
         return executor.isShutdown();
      }


      private void shutdown()
      {
         cancelPageLoadCompletedTask();
         ClientUtilities.shutdownAndAwaitTermination(executor, "BrowserEngine.PageLoadCompletionTask");
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class PageLoadCompletionTask implements Runnable
   {
      final private Runnable pageLoadCompletionRunnableEDT = initialiseRunnable();


      private Runnable initialiseRunnable()
      {
         return new Runnable()
         {
            @Override
            final public void run()
            {
               if (! browserEventsDispatcher.isShutdown())
                  browserEventsDispatcher.synthesizeLoadProgressCompletedEvent(0, 0);
            }
         };
      }


      @Override
      final public void run()
      {
         if (! browserEventsDispatcher.isShutdown())
            browser.getDisplay().asyncExec(pageLoadCompletionRunnableEDT);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void updateTitle(final String title)
   {
      this.title = title;
   }


   private void updatePageLoadingStatus(final boolean isLoadingPage)
   {
      this.isLoadingPage = isLoadingPage;
   }


   private boolean isBrowserDisplayThread()
   {
      return (browser.getDisplay().getThread() == Thread.currentThread());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleClearListeners()
   {
      browser.removeLocationListener(browserEventsDispatcher);
      browser.removeProgressListener(browserEventsDispatcher);
      browser.removeStatusTextListener(browserEventsDispatcher);
      browser.removeTitleListener(browserEventsDispatcher);
      browser.removeKeyListener(browserEventsDispatcher);
      browser.removeMouseListener(browserEventsDispatcher);
      browser.removeDisposeListener(browserEventsDispatcher);
   }


   private void handleOpenURL(final String URL)
   {
      if (URL == null)
         return;

      if (isBrowserDisplayThread())
      {
         pendingURL = URL;
         browser.setUrl(URL);
      }
      else
      {
         browser.getDisplay().asyncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               pendingURL = URL;
               browser.setUrl(URL);
            }
         });
      }
   }


   private String handleGetHTML()
   {
      if (isBrowserDisplayThread())
         return browser.getText();
      else
      {
         /* When called from a non-display thread, if any RuntimeException isn't caught within the Runnable it will be automatically propagated up to the
          * SWT read & dispatch thread, and from there handled like any regular SWT exception, with an alert prompt displayed to the user, etc.
          * But in addition the syncExec() call will then also throw the nested RuntimeException back to the calling thread, which will likely also result
          * in the same handling and alert prompt to the user as above. To avoid this duplication, the RuntimeException is caught and propagated back
          * to the calling thread, without any handling on the SWT read & dispatch thread.
          */
         final AtomicReference<String> browserTextReference = new AtomicReference<String>();
         final AtomicReference<RuntimeException> exceptionReference = new AtomicReference<RuntimeException>();

         browser.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               try
               {
                  browserTextReference.set(browser.getText());
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

         return browserTextReference.get();
      }
   }


   private void handleSetHTML(final String HTML, final boolean isTrusted)
   {
      if (isBrowserDisplayThread())
         browser.setText(HTML, isTrusted);
      else
      {
         browser.getDisplay().asyncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               browser.setText(HTML, isTrusted);
            }
         });
      }
   }


   private void handleExecuteJavaScript(final String javaScript)
   {
      if (isBrowserDisplayThread())
         browser.execute(javaScript);
      else
      {
         browser.getDisplay().asyncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               browser.execute(javaScript);
            }
         });
      }
   }


   private Object handleEvaluateJavaScript(final String javaScript)
   {
      if (isBrowserDisplayThread())
         return browser.evaluate(javaScript);
      else
      {
         // Refer to the comments in the handleGetHTML method regarding the exception handling.
         final AtomicReference<Object> returnValueReference = new AtomicReference<Object>();
         final AtomicReference<RuntimeException> exceptionReference = new AtomicReference<RuntimeException>();

         browser.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               try
               {
                  returnValueReference.set(browser.evaluate(javaScript));
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

         return returnValueReference.get();
      }
   }


   private void handleRegisterFunction(final String functionName, final BrowserServiceFunction function)
   {
      final BrowserFunction functionWrapper = new BrowserFunction(browser, functionName)
      {
         @Override
         final public Object function(final Object[] arguments)
         {
            return function.call(arguments);
         }
      };

      registeredFunctions.put(function, functionWrapper);
   }


   private void handleDeregisterFunction(final BrowserServiceFunction function)
   {
      final BrowserFunction functionWrapper = registeredFunctions.remove(function);
      if (functionWrapper != null)
         functionWrapper.dispose();
   }


   private boolean handleIsBackEnabled()
   {
      if (isBrowserDisplayThread())
         return browser.isBackEnabled();
      else
      {
         // Refer to the comments in the handleGetHTML method regarding the exception handling.
         final AtomicReference<Boolean> returnValueReference = new AtomicReference<Boolean>();
         final AtomicReference<RuntimeException> exceptionReference = new AtomicReference<RuntimeException>();

         browser.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               try
               {
                  returnValueReference.set(browser.isBackEnabled());
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

         return returnValueReference.get();
      }
   }


   private void handleBack()
   {
      if (isBrowserDisplayThread())
         browser.back();
      else
      {
         browser.getDisplay().asyncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               browser.back();
            }
         });
      }
   }


   private boolean handleIsForwardEnabled()
   {
      if (isBrowserDisplayThread())
         return browser.isForwardEnabled();
      else
      {
         // Refer to the comments in the handleGetHTML method regarding the exception handling.
         final AtomicReference<Boolean> returnValueReference = new AtomicReference<Boolean>();
         final AtomicReference<RuntimeException> exceptionReference = new AtomicReference<RuntimeException>();

         browser.getDisplay().syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               try
               {
                  returnValueReference.set(browser.isForwardEnabled());
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

         return returnValueReference.get();
      }
   }


   private void handleForward()
   {
      if (isBrowserDisplayThread())
         browser.forward();
      else
      {
         browser.getDisplay().asyncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               browser.forward();
            }
         });
      }
   }


   private void handleRefresh()
   {
      if (isBrowserDisplayThread())
         browser.refresh();
      else
      {
         browser.getDisplay().asyncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               browser.refresh();
            }
         });
      }
   }


   private void handleStop()
   {
      if (isBrowserDisplayThread())
         browser.stop();
      else
      {
         browser.getDisplay().asyncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               browser.stop();
            }
         });
      }
   }


   private void handleRequestFocus()
   {
      if (isBrowserDisplayThread())
         browser.setFocus();
      else
      {
         browser.getDisplay().asyncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               browser.setFocus();
            }
         });
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final Browser getBrowserControl()
   {
      return browser;
   }


   final void clearListeners()
   {
      handleClearListeners();
   }


   final void preDispose()
   {
      browserEventsDispatcher.shutdown();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public String getPendingURL()
   {
      // Must be called on SWT EDT.
      return pendingURL;
   }


   @Override
   final public String getURL()
   {
      // Must be called on SWT EDT.
      return browser.getUrl();
   }


   @Override
   final public String getTitle()
   {
      // Must be called on SWT EDT.
      return title;
   }


   @Override
   final public boolean isLoadingPage()
   {
      // Must be called on SWT EDT.
      return isLoadingPage;
   }


   @Override
   final public void openURL(final String URL)
   {
      handleOpenURL(URL);
   }


   @Override
   final public String getHTML()
   {
      return handleGetHTML();
   }


   @Override
   final public void setHTML(final String HTML, final boolean isTrusted)
   {
      handleSetHTML(HTML, isTrusted);
   }


   @Override
   final public void executeJavascript(final String javaScript)
   {
      handleExecuteJavaScript(javaScript);
   }


   @Override
   final public Object evaluateJavascript(final String javaScript)
   {
      return handleEvaluateJavaScript(javaScript);
   }


   @Override
   final public void registerFunction(final String functionName, final BrowserServiceFunction function)
   {
      handleRegisterFunction(functionName, function);
   }


   @Override
   final public void deregisterFunction(final BrowserServiceFunction function)
   {
      handleDeregisterFunction(function);
   }


   @Override
   final public boolean isBackEnabled()
   {
      return handleIsBackEnabled();
   }


   @Override
   final public void back()
   {
      handleBack();
   }


   @Override
   final public boolean isForwardEnabled()
   {
      return handleIsForwardEnabled();
   }


   @Override
   final public void forward()
   {
      handleForward();
   }


   @Override
   final public void refresh()
   {
      handleRefresh();
   }


   @Override
   final public void stop()
   {
      handleStop();
   }


   @Override
   final public void requestFocus()
   {
      handleRequestFocus();
   }


   @Override
   final public void addLocationListener(final BrowserLocationListener locationListener)
   {
      locationListeners.add(locationListener);
   }


   @Override
   final public void removeLocationListener(final BrowserLocationListener locationListener)
   {
      locationListeners.remove(locationListener);
   }


   @Override
   final public void addLoadProgressListener(final BrowserLoadProgressListener loadProgressListener)
   {
      loadProgressListeners.add(loadProgressListener);
   }


   @Override
   final public void removeLoadProgressListener(final BrowserLoadProgressListener loadProgressListener)
   {
      loadProgressListeners.remove(loadProgressListener);
   }


   @Override
   final public void addStatusTextEventListener(final BrowserStatusTextListener statusTextEventListener)
   {
      statusTextListeners.add(statusTextEventListener);
   }


   @Override
   final public void removeStatusEventListener(final BrowserStatusTextListener statusTextEventListener)
   {
      statusTextListeners.remove(statusTextEventListener);
   }


   @Override
   final public void addTitleEventListener(final BrowserTitleListener titleEventListener)
   {
      titleListeners.add(titleEventListener);
   }


   @Override
   final public void removeTitleEventListener(final BrowserTitleListener titleEventListener)
   {
      titleListeners.remove(titleEventListener);
   }


   @Override
   final public void addKeyListener(final KeyListener keyListener)
   {
      keyListeners.add(keyListener);
   }


   @Override
   final public void removeKeyListener(final KeyListener keyListener)
   {
      keyListeners.remove(keyListener);
   }


   @Override
   final public void addMouseListener(final BrowserMouseListener mouseListener)
   {
      mouseListeners.add(mouseListener);
   }


   @Override
   final public void removeMouseListener(final BrowserMouseListener mouseListener)
   {
      mouseListeners.remove(mouseListener);
   }


   @Override
   final public void addDisposeEventListener(final BrowserDisposeListener disposeEventListener)
   {
      disposeEventListeners.add(disposeEventListener);
   }


   @Override
   final public void removeDisposeEventListener(final BrowserDisposeListener disposeEventListener)
   {
      disposeEventListeners.remove(disposeEventListener);
   }


   @Override
   final public boolean isClosed()
   {
      return browser.isDisposed();
   }


   @Override
   final public void close()
   {
      preDispose();
      browser.dispose();
   }
}