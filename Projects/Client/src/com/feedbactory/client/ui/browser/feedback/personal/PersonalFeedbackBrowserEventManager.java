
package com.feedbactory.client.ui.browser.feedback.personal;


import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.BrowserUtilities;
import com.feedbactory.client.ui.browser.feedback.FeedbackWebsiteURL;
import com.feedbactory.client.ui.feedback.FeedbackUIManager.BrowserEventManagerClientView;
import com.feedbactory.client.ui.feedback.personal.PersonalFeedbackUIManager;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


final public class PersonalFeedbackBrowserEventManager
{
   final private PersonalFeedbackUIManager uiManager;

   final private BrowserEventManagerClientView browserEventManager;

   final private PersonalFeedbackWebsiteBrowserHandlerRegistry serviceHandlerRegistry = new PersonalFeedbackWebsiteBrowserHandlerRegistry(this);

   // A collection initialised and assigned to a final member will be visible (including all of its nested objects, when assigned once on construction) to all subsequent threads.
   final private Map<String, PersonalFeedbackWebsiteBrowserHandler> serviceHandlersByDomainSuffix = new HashMap<String, PersonalFeedbackWebsiteBrowserHandler>(25);


   public PersonalFeedbackBrowserEventManager(final PersonalFeedbackUIManager uiManager, final BrowserEventManagerClientView browserEventManager)
   {
      this.uiManager = uiManager;
      this.browserEventManager = browserEventManager;

      initialise();
   }


   private void initialise()
   {
      registerCategoryBrowserHandlers();
   }


   private void registerCategoryBrowserHandlers()
   {
      for (final PersonalFeedbackWebsiteBrowserHandler handler : serviceHandlerRegistry.getServiceHandlers())
      {
         for (final String handlerDomainSuffix : handler.getServiceIdentifier().getHostSuffixes())
            serviceHandlersByDomainSuffix.put(handlerDomainSuffix, handler);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private List<FeedbackWebsiteURL> handleGetWebsiteURLs()
   {
      final List<FeedbackWebsiteURL> websiteURLs = new ArrayList<FeedbackWebsiteURL>();

      for (final PersonalFeedbackWebsiteBrowserHandler handler : serviceHandlerRegistry.getServiceHandlers())
         websiteURLs.add(new FeedbackWebsiteURL(handler.getName(), handler.getItemBrowseURL()));

      return websiteURLs;
   }


   // Called on the browser thread.
   private PersonalFeedbackBrowsedPageResult handleReportBrowsedPage(final BrowserService browserService)
   {
      final PersonalFeedbackWebsiteBrowserHandler serviceHandler = getWebsiteHandler(browserService.getURL());

      if ((serviceHandler != null) && uiManager.isWebsiteEnabled(serviceHandler.getServiceIdentifier()))
      {
         try
         {
            return serviceHandler.browserPageLocationChanged(browserService);
         }
         catch (final RuntimeException runtimeException)
         {
            /* If an exception occurs - most likely within JavaScript, which is always a possibility since the website content is an element outside
             * the control of Feedbactory - catch it and swallow it within a live environment. Report it in a development environment.
             */
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Exception while reporting page load completion.", runtimeException);
         }
      }

      return PersonalFeedbackBrowsedPageResult.NoPersonProfile;
   }


   private PersonalFeedbackWebsiteBrowserHandler getWebsiteHandler(final String browserURL)
   {
      // Will return an empty string for local file-type URLs, eg. file:///etc.
      String domainNameSuffix = BrowserUtilities.getHostName(browserURL).toLowerCase();
      if (domainNameSuffix.startsWith("www."))
         domainNameSuffix = domainNameSuffix.substring(4);

      PersonalFeedbackWebsiteBrowserHandler serviceHandler;
      int dotIndex = domainNameSuffix.indexOf('.');

      /* Progressively trim the leading segments off the domain name, looking for a matching Feedbactory handler.
       * Bail out once the trimmed domain contains only the one segment (ie. no embedded dot).
       */
      while (dotIndex != -1)
      {
         serviceHandler = serviceHandlersByDomainSuffix.get(domainNameSuffix);
         if (serviceHandler != null)
            return serviceHandler;

         domainNameSuffix = domainNameSuffix.substring(dotIndex + 1);
         dotIndex = domainNameSuffix.indexOf('.');
      }

      return null;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public List<FeedbackWebsiteURL> getWebsiteURLs()
   {
      return handleGetWebsiteURLs();
   }


   final public PersonalFeedbackBrowsedPageResult reportBrowsedPage(final BrowserService browserService)
   {
      return handleReportBrowsedPage(browserService);
   }


   final public void reportBrowserActiveItemChanged(final BrowserService browserService, final PersonalFeedbackPersonProfile personProfile)
   {
      if (personProfile != null)
         uiManager.reportBrowserActiveItemChanged(personProfile);

      browserEventManager.reportBrowsedItem(browserService, personProfile);
   }
}