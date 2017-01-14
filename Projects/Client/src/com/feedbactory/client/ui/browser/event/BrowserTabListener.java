
package com.feedbactory.client.ui.browser.event;


public interface BrowserTabListener
{
   public void newBrowserTabOpened(final BrowserTabEvent browserTabEvent);
   public void activeBrowserTabChanged(final BrowserTabEvent browserTabEvent);
   public void browserTabClosed(final BrowserTabEvent browserTabEvent);
}