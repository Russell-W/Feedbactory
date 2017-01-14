
package com.feedbactory.client.ui.browser;


import com.feedbactory.client.ui.browser.event.BrowserTabListener;


public interface BrowserUIManagerService
{
   public BrowserService newBrowserService();
   public BrowserService getActiveBrowserService();
   public void setActiveBrowserService(final BrowserService browserService);

   public boolean isBrowserThread();
   public void invokeLater(final Runnable runnable);
   public void invokeAndWait(final Runnable runnable);

   public BrowserServicesDebugInformation getBrowserServicesDebugInformation();

   public void addBrowserTabEventListener(final BrowserTabListener browserTabEventListener);
   public void removeBrowserTabEventListener(final BrowserTabListener browserTabEventListener);

   public void clearBrowserHistory();
}