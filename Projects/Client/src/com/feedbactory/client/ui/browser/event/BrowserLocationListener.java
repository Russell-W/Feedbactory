
package com.feedbactory.client.ui.browser.event;


public interface BrowserLocationListener
{
   public boolean browserPageLocationChanging(final BrowserLocationEvent pageLocationChangeEvent);
   public void browserPageLocationChanged(final BrowserLocationEvent pageLocationChangeEvent);
}