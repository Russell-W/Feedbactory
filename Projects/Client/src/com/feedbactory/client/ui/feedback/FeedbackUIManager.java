
package com.feedbactory.client.ui.feedback;


import com.feedbactory.client.core.feedback.FeedbackCategoryHandler;
import com.feedbactory.client.core.feedback.FeedbackManager;
import com.feedbactory.client.core.feedback.ItemProfileFeedbackSummary;
import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.client.core.network.ProcessedRequestResult;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.client.ui.UIManager;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.BrowserUIManagerService;
import com.feedbactory.client.ui.browser.feedback.FeedbackBrowserEventManager;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.MessageDialog.MessageType;
import com.feedbactory.client.ui.pad.FeedbactoryPadUIView;
import com.feedbactory.client.ui.settings.FlashingFeedbackAlertOption;
import com.feedbactory.client.ui.settings.Setting;
import com.feedbactory.client.ui.settings.SettingChangeListener;
import com.feedbactory.client.ui.settings.SettingsUIManager;
import com.feedbactory.shared.feedback.FeedbackItem;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import com.feedbactory.shared.feedback.FeedbackResultSummary;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;


final public class FeedbackUIManager
{
   final private FeedbackCategoryUIRegistry feedbackCategoryUIRegistry;
   final private FeedbackManager feedbackManager;

   final private UIManager uiManager;
   final private SettingsUIManager settingsUIManager;

   final private FeedbactoryPadUIView feedbactoryPad;

   // For later use - dynamically adding & removing menu links.
   final private List<FeedbackMenuItem> feedbackMenuItems = new ArrayList<FeedbackMenuItem>();
   final private FeedbackPanel feedbackPanel;
   private HierarchyListener feedbackPanelShowingListener;
   private JComponent activeFeedbackFromUserPanel;

   final private FeedbackBrowserEventManager browserEventManager;

   final private List<FeedbackUIEventListener> feedbackAlertListeners = new CopyOnWriteArrayList<FeedbackUIEventListener>();

   private FeedbackItemProfile activeBrowserTabItemProfile;
   private FeedbackResultSummary activeBrowserTabItemFeedbackSummary;


   public FeedbackUIManager(final FeedbackManager feedbackManager, final AccountSessionManager userAccountManager,
                            final UIManager uiManager, final SettingsUIManager settingsUIManager,
                            final FeedbactoryPadUIView feedbactoryPadClientUIView)
   {
      final BrowserEventManagerClientView browserEventManagerClientView = new BrowserEventManagerClientView();
      feedbackCategoryUIRegistry = new FeedbackCategoryUIRegistry(feedbackManager.getFeedbackCategoryRegistry(), userAccountManager,
                                                                  this, browserEventManagerClientView, settingsUIManager, feedbactoryPadClientUIView);

      this.uiManager = uiManager;
      this.settingsUIManager = settingsUIManager;

      this.feedbackManager = feedbackManager;

      this.feedbactoryPad = feedbactoryPadClientUIView;
      feedbackPanel = new FeedbackPanel(userAccountManager, this, feedbactoryPad);

      browserEventManager = new FeedbackBrowserEventManager(this, uiManager.getBrowserManagerService());

      initialise();
   }


   private void initialise()
   {
      initialiseFlashingFeedbackAlert();
   }


   private void initialiseFlashingFeedbackAlert()
   {
      if (settingsUIManager.isFlashingFeedbackAlertEnabled() != FlashingFeedbackAlertOption.Never)
         addFeedbackPanelShowingListener();

      final SettingChangeListener flashingFeedbackAlertSettingListener = new SettingChangeListener()
      {
         @Override
         final public void settingChanged(final Setting setting)
         {
            handleFlashingFeedbackAlertSettingChanged();
         }
      };

      settingsUIManager.addSettingChangeListener(Setting.FlashingFeedbackAlertEnabled, flashingFeedbackAlertSettingListener);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final public class BrowserEventManagerClientView
   {
      /* Provides a callback mechanism for feedback category browser handlers to the global browser event handler,
       * ie. for web pages to flag when an item profile is being displayed, not necessarily after a page load event.
       */
      final public void reportBrowsedItem(final BrowserService browserService, final FeedbackItemProfile itemProfile)
      {
         browserEventManager.reportBrowsedItem(browserService, itemProfile);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void handleFlashingFeedbackAlertSettingChanged()
   {
      if (settingsUIManager.isFlashingFeedbackAlertEnabled() != FlashingFeedbackAlertOption.Never)
         addFeedbackPanelShowingListener();
      else if (settingsUIManager.isFlashingFeedbackAlertEnabled() == FlashingFeedbackAlertOption.Never)
         removeFeedbackPanelShowingListener();
   }


   private void addFeedbackPanelShowingListener()
   {
      if (feedbackPanelShowingListener == null)
      {
         feedbackPanelShowingListener = new HierarchyListener()
         {
            @Override
            final public void hierarchyChanged(final HierarchyEvent hierarchyEvent)
            {
               if (((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) && feedbackPanel.getDelegate().isShowing())
                  notifyFeedbackAlertCancelled();
            }
         };

         feedbackPanel.getDelegate().addHierarchyListener(feedbackPanelShowingListener);
      }
   }


   private void removeFeedbackPanelShowingListener()
   {
      if (feedbackPanelShowingListener != null)
      {
         feedbackPanel.getDelegate().removeHierarchyListener(feedbackPanelShowingListener);
         feedbackPanelShowingListener = null;
      }
   }


   private void notifyActiveFeedbackItemUpdated()
   {
      notifyActiveFeedbackItemProfileUpdated();
      notifyActiveFeedbackItemSummaryUpdated();
      notifyActiveFeedbackItemNumberOfRatingsUpdated();
   }


   private void notifyActiveFeedbackItemProfileUpdated()
   {
      final String feedbackItemDisplayName = (activeBrowserTabItemProfile != null) ? activeBrowserTabItemProfile.getFullName() : "";
      for (final FeedbackUIEventListener listener : feedbackAlertListeners)
         listener.activeFeedbackItemUpdated(feedbackItemDisplayName);
   }


   private void notifyActiveFeedbackItemSummaryUpdated()
   {
      final String feedbackSummaryLabel;
      final String feedbackSummaryString;

      if ((activeBrowserTabItemFeedbackSummary != null) && (activeBrowserTabItemFeedbackSummary.getNumberOfRatings() > 0))
      {
         final FeedbackCategoryUIManager feedbackCategoryUIManager = feedbackCategoryUIRegistry.getFeedbackCategoryUIHandler(activeBrowserTabItemProfile.getFeedbackCategory());
         final FeedbackCategoryDataFormatter dataFormatter = feedbackCategoryUIManager.getFeedbackDataFormatter();
         feedbackSummaryLabel = dataFormatter.getFeedbackSummaryLabel();
         feedbackSummaryString = dataFormatter.getFeedbackSummaryString(activeBrowserTabItemFeedbackSummary);
      }
      else
      {
         feedbackSummaryLabel = (activeBrowserTabItemProfile == null) ? "" : "Not yet rated";
         feedbackSummaryString = "";
      }

      for (final FeedbackUIEventListener listener : feedbackAlertListeners)
         listener.activeFeedbackSummaryUpdated(feedbackSummaryLabel, feedbackSummaryString);
   }


   private void notifyActiveFeedbackItemNumberOfRatingsUpdated()
   {
      final String numberOfRatingsString;

      if ((activeBrowserTabItemFeedbackSummary != null) && (activeBrowserTabItemFeedbackSummary.getNumberOfRatings() > 0))
      {
         final StringBuilder numberOfRatingsStringBuilder = new StringBuilder();
         numberOfRatingsStringBuilder.append(NumberFormat.getIntegerInstance().format(activeBrowserTabItemFeedbackSummary.getNumberOfRatings()));
         numberOfRatingsStringBuilder.append(" rating");
         if (activeBrowserTabItemFeedbackSummary.getNumberOfRatings() > 1)
            numberOfRatingsStringBuilder.append('s');

         numberOfRatingsString = numberOfRatingsStringBuilder.toString();
      }
      else
         numberOfRatingsString = "";

      for (final FeedbackUIEventListener listener : feedbackAlertListeners)
         listener.activeFeedbackNumberOfRatingsUpdated(numberOfRatingsString);
   }


   private void notifyFeedbackAlertActive()
   {
      for (final FeedbackUIEventListener listener : feedbackAlertListeners)
         listener.alertActive();
   }


   private void notifyFeedbackAlertCancelled()
   {
      for (final FeedbackUIEventListener listener : feedbackAlertListeners)
         listener.alertCancelled();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleReportBrowsedItem(final FeedbackItemProfile itemProfile, final BrowserService browserService)
   {
      final boolean isActiveBrowserService = (uiManager.getBrowserManagerService().getActiveBrowserService() == browserService);

      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            handleReportBrowsedItemEDT(itemProfile, isActiveBrowserService);
         }
      });
   }


   private void handleReportBrowsedItemEDT(final FeedbackItemProfile itemProfile, final boolean isActiveBrowserService)
   {
      if (isActiveBrowserService)
      {
         if (itemProfile != null)
         {
            if ((activeBrowserTabItemProfile == null) || (! activeBrowserTabItemProfile.getItem().equals(itemProfile.getItem())))
            {
               activeBrowserTabItemProfile = itemProfile;
               final FeedbackCategoryHandler feedbackCategoryHandler = feedbackManager.getFeedbackCategoryRegistry().getFeedbackCategoryHandler(itemProfile.getFeedbackCategory());
               activeBrowserTabItemFeedbackSummary = feedbackCategoryHandler.getFeedbackResultSummary(itemProfile.getItem());

               notifyActiveFeedbackItemUpdated();
            }
         }
         else if (activeBrowserTabItemProfile != null)
         {
            activeBrowserTabItemProfile = null;
            activeBrowserTabItemFeedbackSummary = null;

            notifyActiveFeedbackItemUpdated();
         }
      }

      if (itemProfile != null)
      {
         // Changed item profiles will be updated on the feedback panel here.
         final boolean isNewItem = feedbackPanel.reportBrowsedItem(itemProfile, isActiveBrowserService);

         // Show an alert if the setting is enabled to show alerts for any recognised item and the item is new.
         if ((settingsUIManager.isFlashingFeedbackAlertEnabled() == FlashingFeedbackAlertOption.ShowForAnyRecognisedItems) && isNewItem)
            showFeedbackAlert();
      }
   }


   private void handleReportBrowserItemSelected(final FeedbackItem browsedItemInActiveTab)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            handleReportBrowserItemSelectedEDT(browsedItemInActiveTab);
         }
      });
   }


   private void handleReportBrowserItemSelectedEDT(final FeedbackItem browsedItemInActiveTab)
   {
      if (browsedItemInActiveTab != null)
      {
         if ((activeBrowserTabItemProfile == null) || (! activeBrowserTabItemProfile.getItem().equals(browsedItemInActiveTab)))
         {
            final FeedbackCategoryHandler feedbackCategoryHandler = feedbackManager.getFeedbackCategoryRegistry().getFeedbackCategoryHandler(browsedItemInActiveTab.getFeedbackCategory());
            activeBrowserTabItemProfile = feedbackCategoryHandler.getFeedbackItemProfile(browsedItemInActiveTab);
            activeBrowserTabItemFeedbackSummary = feedbackCategoryHandler.getFeedbackResultSummary(browsedItemInActiveTab);

            notifyActiveFeedbackItemUpdated();
         }

         feedbackPanel.applicationRequestSelectFeedbackItem(browsedItemInActiveTab);
      }
      else if (activeBrowserTabItemProfile != null)
      {
         activeBrowserTabItemProfile = null;
         activeBrowserTabItemFeedbackSummary = null;

         notifyActiveFeedbackItemUpdated();
      }
   }


   private void handleReportItemProfileUpdated(final FeedbackItemProfile itemProfile)
   {
      if ((activeBrowserTabItemProfile != null) && itemProfile.getItem().equals(activeBrowserTabItemProfile.getItem()))
      {
         activeBrowserTabItemProfile = itemProfile;
         notifyActiveFeedbackItemProfileUpdated();
      }
   }


   private void handleReportItemFeedbackSummaryUpdated(final FeedbackItem feedbackItem, final FeedbackResultSummary feedbackSummary)
   {
      if ((activeBrowserTabItemProfile != null) && activeBrowserTabItemProfile.getItem().equals(feedbackItem))
      {
         // The updated feedback summary shouldn't be null, but check that too just in case...
         if ((feedbackSummary == null) || (activeBrowserTabItemFeedbackSummary == null))
         {
            activeBrowserTabItemFeedbackSummary = feedbackSummary;

            notifyActiveFeedbackItemSummaryUpdated();
            notifyActiveFeedbackItemNumberOfRatingsUpdated();
         }
         else
         {
            final FeedbackResultSummary previousFeedbackSummary = activeBrowserTabItemFeedbackSummary;
            activeBrowserTabItemFeedbackSummary = feedbackSummary;

            if (! previousFeedbackSummary.getFeedbackResultSummary().equals(feedbackSummary.getFeedbackResultSummary()))
               notifyActiveFeedbackItemSummaryUpdated();

            if (previousFeedbackSummary.getNumberOfRatings() != feedbackSummary.getNumberOfRatings())
               notifyActiveFeedbackItemNumberOfRatingsUpdated();
         }
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleShowFeedbackFromUserPanel()
   {
      if (activeFeedbackFromUserPanel != null)
         return;

      if (feedbackManager.hasCachedUserSubmissions())
         showFeedbackFromUserPanel(feedbackManager.getUserFeedbackSubmissions().data);
      else
      {
         feedbactoryPad.setBusy(true);

         final ExecutorService executor = Executors.newSingleThreadExecutor();

         final Runnable requestTask = new Runnable()
         {
            @Override
            final public void run()
            {
               ProcessedRequestResult<List<ItemProfileFeedbackSummary>> userFeedbackSubmissions = null;

               try
               {
                  userFeedbackSubmissions = feedbackManager.getUserFeedbackSubmissions();
               }
               finally
               {
                  postProcessRetrieveUserFeedbackSubmissions(userFeedbackSubmissions);
               }
            }
         };

         executor.execute(requestTask);
         executor.shutdown();
      }
   }


   private void postProcessRetrieveUserFeedbackSubmissions(final ProcessedRequestResult<List<ItemProfileFeedbackSummary>> userFeedbackSubmissions)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            try
            {
               // userFeedbackSubmissions may be null if there was a non-network exception.
               if ((userFeedbackSubmissions != null) && (userFeedbackSubmissions.requestStatus == NetworkRequestStatus.OK))
                  showFeedbackFromUserPanel(userFeedbackSubmissions.data);
            }
            finally
            {
               feedbactoryPad.setBusy(false);
            }
         }
      });
   }


   private void showFeedbackFromUserPanel(final List<ItemProfileFeedbackSummary> userFeedbackSubmissions)
   {
      if (userFeedbackSubmissions.isEmpty())
      {
         final String[] message = {"You haven't submitted any feedback yet."};
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Information, message, MessageDialog.PresetOptionConfiguration.OK);
         feedbactoryPad.showMessageDialog(new MessageDialog(builder), MessageDialog.PresetOptionSelection.OK, true);
      }
      else
      {
         final FeedbackFromUserPanel feedbackFromUserPanel = new FeedbackFromUserPanel(this, uiManager.getImageLoader(), userFeedbackSubmissions);
         activeFeedbackFromUserPanel = feedbackFromUserPanel.getDelegate();
         attachCancelKeyBinding();
         feedbactoryPad.showFormComponent(activeFeedbackFromUserPanel, false);
      }
   }


   private void attachCancelKeyBinding()
   {
      final InputMap inputMap = activeFeedbackFromUserPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      final ActionMap actionMap = activeFeedbackFromUserPanel.getActionMap();

      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelPanel");

      actionMap.put("cancelPanel", new AbstractAction()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            dismissFeedbackFromUserPanel();
         }
      });
   }


   private void handleDismissFeedbackFromUserPanel()
   {
      if (activeFeedbackFromUserPanel != null)
      {
         feedbactoryPad.dismissLockingComponent(activeFeedbackFromUserPanel);
         activeFeedbackFromUserPanel = null;
      }
   }


   private void handleShowItemInBrowser(final FeedbackItemProfile itemProfile)
   {
      final BrowserService browserServiceShowingItem = browserEventManager.getBrowserServiceForItem(itemProfile.getItem());
      BrowserUIManagerService browserManagerService = uiManager.getBrowserManagerService();

      if (browserServiceShowingItem != null)
         browserManagerService.setActiveBrowserService(browserServiceShowingItem);
      else
      {
         final BrowserService newBrowserService = browserManagerService.newBrowserService();
         newBrowserService.openURL(itemProfile.getURL());
         browserManagerService.setActiveBrowserService(newBrowserService);
      }
   }


   private void handleShowFeedbackAlert()
   {
      // Don't allow feedback alerts when the pad is visible with the feedback panel showing.
      if (! (feedbactoryPad.isVisible() && feedbactoryPad.isFeedbackPanelShowing()))
      {
         if (settingsUIManager.isFeedbackAlertSoundEnabled())
            UIUtilities.playBackgroundSound(FeedbackResources.FeedbackSoundPath);

         notifyFeedbackAlertActive();
      }
   }


   private void handleShutdown()
   {
      for (final FeedbackCategoryUIManager categoryUIManager : feedbackCategoryUIRegistry.getRegisteredUIHandlers())
         categoryUIManager.shutdown();

      feedbackPanel.shutdown();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void reportBrowsedItem(final FeedbackItemProfile itemProfile, final BrowserService browserService)
   {
      // SWT EDT.
      handleReportBrowsedItem(itemProfile, browserService);
   }


   final public void reportBrowserItemSelected(final FeedbackItem browsedItemInActiveTab)
   {
      // SWT EDT.
      handleReportBrowserItemSelected(browsedItemInActiveTab);
   }


   final public void reportItemProfileUpdated(final FeedbackItemProfile itemProfile)
   {
      // Swing EDT.
      handleReportItemProfileUpdated(itemProfile);
   }


   final public void reportItemFeedbackSummaryUpdated(final FeedbackItem feedbackItem, final FeedbackResultSummary feedbackSummary)
   {
      // Swing EDT.
      handleReportItemFeedbackSummaryUpdated(feedbackItem, feedbackSummary);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public void reportUncaughtException(final Class<?> contextClass, final String contextMessage, final Thread thread, final Throwable throwable)
   {
      uiManager.reportUncaughtException(contextClass, contextMessage, thread, throwable);
   }


   final public FeedbackCategoryUIRegistry getFeedbackCategoryUIRegistry()
   {
      return feedbackCategoryUIRegistry;
   }


   final public FeedbackPanel getFeedbackPanel()
   {
      return feedbackPanel;
   }


   final public List<FeedbackMenuItem> getFeedbackMenuItems()
   {
      // For now, dynamically build the list on request; later, allow category UI managers to dynamically add & remove menu items from the feedbackMenuItems instance variable.
      final List<FeedbackMenuItem> menuItems = new ArrayList<FeedbackMenuItem>();

      for (final FeedbackCategoryUIManager feedbackCategoryUIManager : feedbackCategoryUIRegistry.getRegisteredUIHandlers())
         menuItems.addAll(feedbackCategoryUIManager.getFeedbackMenuItems());

      return menuItems;
   }


   final public boolean hasFeedbackMenuItem(final FeedbackMenuItem menuItem)
   {
      // For later use - dynamically adding & removing menu links. Requires changes to the FeedbactoryPad.
      return false;
   }


   final public void addFeedbackMenuItem(final FeedbackMenuItem menuItem)
   {
      // For later use - dynamically adding & removing menu links. Requires changes to the FeedbactoryPad.
   }


   final public void removeFeedbackMenuItem(final FeedbackMenuItem menuItem)
   {
      // For later use - dynamically adding & removing menu links. Requires changes to the FeedbactoryPad.
   }


   final public void invokeFeedbackMenuItem(final FeedbackMenuItem menuItem)
   {
      feedbackCategoryUIRegistry.getFeedbackCategoryUIHandler(menuItem.feedbackCategory).invokeMenuItem(menuItem);
   }


   final public void showFeedbackFromUserPanel()
   {
      handleShowFeedbackFromUserPanel();
   }


   final public void dismissFeedbackFromUserPanel()
   {
      handleDismissFeedbackFromUserPanel();
   }


   final public void showItemInBrowser(final FeedbackItemProfile itemProfile)
   {
      handleShowItemInBrowser(itemProfile);
   }


   final public void addFeedbackAlertListener(final FeedbackUIEventListener feedbackAlertListener)
   {
      feedbackAlertListeners.add(feedbackAlertListener);
   }


   final public void removeFeedbackAlertListener(final FeedbackUIEventListener feedbackAlertListener)
   {
      feedbackAlertListeners.remove(feedbackAlertListener);
   }


   final public void showFeedbackAlert()
   {
      handleShowFeedbackAlert();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void preShutdown()
   {
      browserEventManager.shutdown();
   }


   final public void shutdown()
   {
      handleShutdown();
   }
}