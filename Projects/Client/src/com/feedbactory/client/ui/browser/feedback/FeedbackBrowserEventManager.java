/* Memos:
 * - It would be nice to have this class do a lot of the heavy lifting in directly selecting a page load handler based on the page URL, however
 *   it's difficult to do this cleanly when:
 *
 *   - There is not necessarily a one-to-zero-or-one relationship between domains and feedback handlers; there may be more than one per domain.
 *   - Each individual service handler may have more than one domain name suffix that satisfies its criteria.
 *   - It's possible for a feedback category to wish a handler to be invoked for a string pattern of domain names, or even all domains.
 *   - A feedback category UI manager may wish to perform other operations based on browsed data, and not just look for a browsed item profile, eg.
 *     public contact emails (now defunct but a good example).
 *
 *   With those constraints in mind, it's better to perform a high-level call to every category UI manager every time that a page is loaded; let it decide
 *   how to handle the rest. The only thing that this class is interested in is whether or not an item profile was detected on that page. There is a data model flaw
 *   in that this class only tracks one feedback item profile per browser tab/service, so if there are multiple matching candidates (as reported by the feedback category
 *   UI managers) then only one can be stored per browser tab.
 *
 * - The feedback item update check after a user mouse click can seemingly be broken on WebKit on Windows when there is a nested iframe.
 *   An example is loading individual photos from ViewBug's fame page: http://www.viewbug.com/fame .
 *   The problem doesn't appear to effect WebKit on Mac OS X.
 */

package com.feedbactory.client.ui.browser.feedback;


import com.feedbactory.client.core.ClientUtilities;
import com.feedbactory.client.core.FeedbactoryClientConstants;
import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.BrowserServiceFunction;
import com.feedbactory.client.ui.browser.BrowserUIManagerService;
import com.feedbactory.client.ui.browser.event.BrowserLoadProgressEvent;
import com.feedbactory.client.ui.browser.event.BrowserLoadProgressListener;
import com.feedbactory.client.ui.browser.event.BrowserMouseEvent;
import com.feedbactory.client.ui.browser.event.BrowserMouseListener;
import com.feedbactory.client.ui.browser.event.BrowserTabEvent;
import com.feedbactory.client.ui.browser.event.BrowserTabListener;
import com.feedbactory.client.ui.feedback.FeedbackCategoryUIManager;
import com.feedbactory.client.ui.feedback.FeedbackUIManager;
import com.feedbactory.shared.feedback.FeedbackItem;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


final public class FeedbackBrowserEventManager
{
   static final private int FeedbackItemCheckExecutorCoreThreads = 8;

   // The short delay is very brief, keeping in mind that the browser engine has a synthesized page load completed event that itself is already delayed.
   static final private long FeedbackItemCheckShortDelayMilliseconds = 200L;
   static final private long FeedbackItemCheckLongDelayMilliseconds = 2250L;

   final private FeedbackUIManager feedbackUIManager;
   final private BrowserUIManagerService browserUIManagerService;

   final private Map<BrowserService, BrowserNode> browserNodes = new HashMap<BrowserService, BrowserNode>();
   final private Map<FeedbackItem, BrowserNode> browsersByItem = new HashMap<FeedbackItem, BrowserNode>();

   final private Collection<FeedbackCategoryUIManager> feedbackCategoryUIManagers;

   final private ScheduledThreadPoolExecutor delayedFeedbackItemUpdateExecutor = new ScheduledThreadPoolExecutor(FeedbackItemCheckExecutorCoreThreads);


   public FeedbackBrowserEventManager(final FeedbackUIManager feedbackUIManager, final BrowserUIManagerService browserUIManagerService)
   {
      this.feedbackUIManager = feedbackUIManager;
      this.browserUIManagerService = browserUIManagerService;

      feedbackCategoryUIManagers = feedbackUIManager.getFeedbackCategoryUIRegistry().getRegisteredUIHandlers();

      initialise();
   }


   private void initialise()
   {
      browserUIManagerService.addBrowserTabEventListener(new BrowserTabEventListener());

      delayedFeedbackItemUpdateExecutor.setKeepAliveTime(10L, TimeUnit.SECONDS);
      delayedFeedbackItemUpdateExecutor.allowCoreThreadTimeOut(true);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class BrowserTabEventListener implements BrowserTabListener
   {
      @Override
      final public void newBrowserTabOpened(final BrowserTabEvent browserTabEvent)
      {
         final BrowserNode browserNode = new BrowserNode(browserTabEvent.browserService);
         browserNodes.put(browserTabEvent.browserService, browserNode);
      }


      @Override
      final public void activeBrowserTabChanged(final BrowserTabEvent browserTabEvent)
      {
         final FeedbackItem browsedItemInActiveTab;

         // May be null.
         browsedItemInActiveTab = getBrowserServiceFeedbackItem(browserTabEvent.browserService);
         feedbackUIManager.reportBrowserItemSelected(browsedItemInActiveTab);
      }


      @Override
      final public void browserTabClosed(final BrowserTabEvent browserTabEvent)
      {
         final BrowserService browserService = browserTabEvent.browserService;
         final BrowserNode browserNode = browserNodes.remove(browserService);
         browserNode.shutdown();
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class BrowserNode implements BrowserLoadProgressListener, BrowserMouseListener
   {
      final private BrowserService browserService;
      final private BrowserNodeDelayedFeedbackItemCheckTask feedbackItemCheckTask = new BrowserNodeDelayedFeedbackItemCheckTask(this);
      private FeedbackItem feedbackItem;
      private Future<?> shortDelayFeedbackCheckTask;
      private Future<?> longDelayFeedbackCheckTask;


      private BrowserNode(final BrowserService browserService)
      {
         this.browserService = browserService;
         initialise();
      }


      private void initialise()
      {
         browserService.addLoadProgressListener(this);
         browserService.addMouseListener(this);

         if (FeedbactoryClientConstants.IsDevelopmentProfile)
         {
            registerConsoleLogFunction(browserService, System.out, "consoleLog");
            registerConsoleLogFunction(browserService, System.err, "consoleError");
         }
      }


      private void registerConsoleLogFunction(final BrowserService browserService, final PrintStream targetPrintStream, final String functionName)
      {
         /* This callback function provides a convenient means for all JavaScript handlers to display a console.log() style message
          * to Java's stdout or stderr. The function is registered once for each browser instance, staying resident across all web pages until the
          * enclosing browser instance is disposed of - the SWT docs indicate that BrowserFunctions are automatically disposed of
          * at that point if they haven't been already.
          */
         final BrowserServiceFunction function = new BrowserServiceFunction()
         {
            @Override
            final public Object call(final Object[] arguments)
            {
               if ((arguments != null) && (arguments.length == 1))
                  targetPrintStream.println("[JavaScript]: " + arguments[0]);
               else
               {
                  /* Don't bother with dumping a stack trace here, it will trace back to the SWT read & dispatch loop rather than
                   * the calling JavaScript context.
                   */
                  System.err.println("Invalid argument(s) to " + functionName + " function.");
               }

               return null;
            }
         };

         browserService.registerFunction(functionName, function);
      }


      /****************************************************************************
       *
       ***************************************************************************/


      @Override
      final public void browserPageLoadProgressChanged(final BrowserLoadProgressEvent pageProgressChangedEvent)
      {
      }


      @Override
      final public void browserPageLoadCompleted(final BrowserLoadProgressEvent pageProgressChangedEvent)
      {
         scheduleFeedbackItemUpdateCheck();
      }


      @Override
      final public void mousePressed(final BrowserMouseEvent mouseEvent)
      {
      }


      @Override
      final public void mouseReleased(final BrowserMouseEvent mouseEvent)
      {
         if (mouseEvent.button == 1)
            scheduleFeedbackItemUpdateCheck();
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private void scheduleFeedbackItemUpdateCheck()
      {
         // SWT Thread.
         if (! delayedFeedbackItemUpdateExecutor.isShutdown())
         {
            cancelUpdateFeedbackItemTasks();

            shortDelayFeedbackCheckTask = delayedFeedbackItemUpdateExecutor.schedule(feedbackItemCheckTask, FeedbackItemCheckShortDelayMilliseconds, TimeUnit.MILLISECONDS);
            longDelayFeedbackCheckTask = delayedFeedbackItemUpdateExecutor.schedule(feedbackItemCheckTask, FeedbackItemCheckLongDelayMilliseconds, TimeUnit.MILLISECONDS);
         }
      }


      private void cancelUpdateFeedbackItemTasks()
      {
         if (shortDelayFeedbackCheckTask != null)
         {
            shortDelayFeedbackCheckTask.cancel(false);
            shortDelayFeedbackCheckTask = null;
         }

         if (longDelayFeedbackCheckTask != null)
         {
            longDelayFeedbackCheckTask.cancel(false);
            longDelayFeedbackCheckTask = null;
         }
      }


      private void setFeedbackItem(final FeedbackItem itemID)
      {
         feedbackItem = itemID;
      }


      private void shutdown()
      {
         cancelUpdateFeedbackItemTasks();
         clearBrowserNodeFeedbackItem(this);
         browserService.removeLoadProgressListener(this);
         browserService.removeMouseListener(this);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class BrowserNodeDelayedFeedbackItemCheckTask implements Runnable
   {
      final private BrowserNode browserNode;


      private BrowserNodeDelayedFeedbackItemCheckTask(final BrowserNode browserNode)
      {
         this.browserNode = browserNode;
      }


      @Override
      final public void run()
      {
         browserUIManagerService.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               /* This task is executed on the browser display thread, the same thread that is used via a synchronous invokeAndWait
                * to shutdown this class instance during Feedbactory's pre-shutdown phase.
                * If this task executes before the shutdown task, and depending on the state of the data, a Swing task may
                * eventually be invoked on the parent feedback UI manager. This won't cause problems during shutdown since that
                * Swing task is then guaranteed to execute before the Swing components are actually shutdown on Feedbactory's 2nd
                * shutdown phase (keeping in mind the invokeAndWait used during shutdown of this class instance).
                * If this task executes after the shutdown task, the delayedFeedbackItemUpdateExecutor.isShutdown() check will
                * ensure that it bails out.
                */
               if ((! delayedFeedbackItemUpdateExecutor.isShutdown()) && (! browserNode.browserService.isClosed()))
                  processUpdateBrowserFeedbackItem(browserNode);
            }
         });
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private FeedbackItem getBrowserServiceFeedbackItem(final BrowserService browserService)
   {
      final BrowserNode browserNode = browserNodes.get(browserService);
      return (browserNode != null) ? browserNode.feedbackItem : null;
   }


   private void setBrowserNodeFeedbackItem(final BrowserNode browserNode, final FeedbackItem itemID)
   {
      browserNode.setFeedbackItem(itemID);
      browsersByItem.put(itemID, browserNode);
   }


   private void clearBrowserNodeFeedbackItem(final BrowserNode browserNode)
   {
      final FeedbackItem itemID = browserNode.feedbackItem;
      if (itemID != null)
      {
         browserNode.setFeedbackItem(null);
         browsersByItem.remove(itemID);
      }
   }


   private void processUpdateBrowserFeedbackItem(final BrowserNode browserNode)
   {
      final BrowserService browserService = browserNode.browserService;
      final BrowsedPageResult browsedPageResult = getBrowsedPageResult(browserService);

      if ((browsedPageResult != null) && browsedPageResult.isBrowserReadyStateComplete() && (browsedPageResult.getFeedbackItemProfile() != null))
      {
         if (! browsedPageResult.getFeedbackItemProfile().getItem().equals(browserNode.feedbackItem))
         {
            clearBrowserNodeFeedbackItem(browserNode);
            setBrowserNodeFeedbackItem(browserNode, browsedPageResult.getFeedbackItemProfile().getItem());
         }

         // Report the item profile to the feedback UI manager even if the item ID matches; the profile may have been updated.
         feedbackUIManager.reportBrowsedItem(browsedPageResult.getFeedbackItemProfile(), browserService);
      }
      else if ((browsedPageResult == null) || ((browsedPageResult.getFeedbackItemProfile() == null) && browsedPageResult.isBrowserReadyStateComplete()))
      {
         boolean reportToFeedbackUIManager = false;
         if (browserNode.feedbackItem != null)
         {
            clearBrowserNodeFeedbackItem(browserNode);
            reportToFeedbackUIManager = true;
         }

         if (reportToFeedbackUIManager)
            feedbackUIManager.reportBrowsedItem(null, browserService);
      }
      else if (! browsedPageResult.isBrowserReadyStateComplete())
      {
         // Reschedule the active feedback item check, in the meantime retaining any existing active feedback item for that browser instance.
         browserNode.scheduleFeedbackItemUpdateCheck();
      }
   }


   private BrowsedPageResult getBrowsedPageResult(final BrowserService browserService)
   {
      /* The page handling will stop at the first feedback category handler that returns an item for the page.
       * This logic is a little flawed if I wanted to be able to allow multiple handlers per website.
       */
      BrowsedPageResult browsedPageResult;
      for (final FeedbackCategoryUIManager feedbackCategoryUIManager : feedbackCategoryUIManagers)
      {
         browsedPageResult = feedbackCategoryUIManager.reportBrowsedPage(browserService);
         if (browsedPageResult != null)
            return browsedPageResult;
      }

      return null;
   }


   private BrowserService handleGetBrowserServiceForItem(final FeedbackItem itemID)
   {
      if (browserUIManagerService.isBrowserThread())
         return handleGetBrowserServiceForItemEDT(itemID);
      else
      {
         final AtomicReference<BrowserService> browserServiceReference = new AtomicReference<BrowserService>();
         browserUIManagerService.invokeAndWait(new Runnable()
         {
            @Override
            final public void run()
            {
               browserServiceReference.set(handleGetBrowserServiceForItemEDT(itemID));
            }
         });

         return browserServiceReference.get();
      }
   }


   private BrowserService handleGetBrowserServiceForItemEDT(final FeedbackItem itemID)
   {
      final BrowserNode feedbackItemBrowserNode = browsersByItem.get(itemID);
      return (feedbackItemBrowserNode != null) ? feedbackItemBrowserNode.browserService : null;
   }


   private void handleReportBrowsedItem(final BrowserService browserService, final FeedbackItemProfile itemProfile)
   {
      /* This method was previously providing a callback mechanism from JavaScript to Java when it was detected that the user was
       * browsing a new feedback item, or nothing after previously browsing a feedback item. It was used in conjunction with
       * the BrowserService.registerFunction() method, which provided the callback means from JavaScript to Java.
       * This mechanism has been superseded by the Java-level click listener installed on every browser page, which schedules the
       * equivalent checks after a couple of short delays.
       * If I reinstate this callback mechanism, I need to adapt it slightly to hook into the processUpdateBrowserFeedbackItem()
       * method, which has since been updated to avoid duplicate feedback item notifications.
       */
//      browserNode.clearEntriesForBrowserService(browserService);
//
//      if (itemProfile != null)
//         browserNode.setBrowserServiceItem(browserService, itemProfile.getItem());
//
//      feedbackUIManager.reportBrowsedItem(itemProfile, browserService);
   }


   private void handleShutdown()
   {
      /* The invokeAndWait() ensures that the task will have to wait for the completion of any active processUpdateBrowserFeedbackItem() calls,
       * at least until that call hands off to a Swing invokeLater() task. Therefore the latter task will always be invoked before any
       * Swing disposal tasks during the 2nd phase of the Feedbactory shutdown.
       * Note the importance of the delayedFeedbackItemUpdateExecutor.isShutdown() check in the getDelayedBrowserFeedbackItemUpdateTask() method,
       * which comes into play if that task on the browser thread executes immediately after this shutdown task has run.
       */
      browserUIManagerService.invokeAndWait(new Runnable()
      {
         @Override
         final public void run()
         {
            for (final BrowserNode browserNode : browserNodes.values())
               browserNode.shutdown();

            ClientUtilities.shutdownAndAwaitTermination(delayedFeedbackItemUpdateExecutor, "FeedbackBrowserEventManager.BrowserNodeDelayedFeedbackItemCheckTask");
         }
      });
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public BrowserService getBrowserServiceForItem(final FeedbackItem itemID)
   {
      return handleGetBrowserServiceForItem(itemID);
   }


   final public void reportBrowsedItem(final BrowserService browserService, final FeedbackItemProfile itemProfile)
   {
      handleReportBrowsedItem(browserService, itemProfile);
   }


   final public void shutdown()
   {
      handleShutdown();
   }
}