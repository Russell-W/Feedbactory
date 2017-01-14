
package com.feedbactory.client.ui.browser.event;


import com.feedbactory.client.ui.browser.BrowserService;


final public class BrowserDisposeEvent
{
   final public BrowserService browserService;


   public BrowserDisposeEvent(final BrowserService browserService)
   {
      this.browserService = browserService;
   }
}