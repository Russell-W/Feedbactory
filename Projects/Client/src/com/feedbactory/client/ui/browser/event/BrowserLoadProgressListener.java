
package com.feedbactory.client.ui.browser.event;


public interface BrowserLoadProgressListener
{
   public void browserPageLoadProgressChanged(final BrowserLoadProgressEvent pageProgressChangedEvent);
   public void browserPageLoadCompleted(final BrowserLoadProgressEvent pageProgressChangedEvent);
}