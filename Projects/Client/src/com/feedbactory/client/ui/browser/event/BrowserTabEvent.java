
package com.feedbactory.client.ui.browser.event;


import com.feedbactory.client.ui.browser.BrowserService;


final public class BrowserTabEvent
{
   final public BrowserService browserService;


   public BrowserTabEvent(final BrowserService browserService)
   {
      this.browserService = browserService;
   }
}