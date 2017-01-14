
package com.feedbactory.client.ui.browser.event;


import com.feedbactory.client.ui.browser.BrowserService;


final public class BrowserTitleEvent
{
   final public BrowserService browserService;
   final public String titleText;


   public BrowserTitleEvent(final BrowserService browserService, final String titleText)
   {
      this.browserService = browserService;
      this.titleText = titleText;
   }
}