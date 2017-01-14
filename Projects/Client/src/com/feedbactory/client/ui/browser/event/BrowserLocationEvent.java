
package com.feedbactory.client.ui.browser.event;


import com.feedbactory.client.ui.browser.BrowserService;


final public class BrowserLocationEvent
{
   final public BrowserService browserService;
   final public boolean isTopFrame;
   final public String URL;


   public BrowserLocationEvent(final BrowserService browserService, final boolean isTopFrame, final String URL)
   {
      this.browserService = browserService;
      this.isTopFrame = isTopFrame;
      this.URL = URL;
   }
}