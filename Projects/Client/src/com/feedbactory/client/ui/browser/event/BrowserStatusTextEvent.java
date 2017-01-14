
package com.feedbactory.client.ui.browser.event;


import com.feedbactory.client.ui.browser.BrowserService;


final public class BrowserStatusTextEvent
{
   final public BrowserService browserService;
   final public String statusText;


   public BrowserStatusTextEvent(final BrowserService browserService, final String statusText)
   {
      this.browserService = browserService;
      this.statusText = statusText;
   }
}