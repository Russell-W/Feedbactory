
package com.feedbactory.client.ui.browser.event;


import com.feedbactory.client.ui.browser.BrowserService;


final public class BrowserLoadProgressEvent
{
   final public BrowserService browserService;
   final public int loadProgress;
   final public int loadTarget;


   public BrowserLoadProgressEvent(final BrowserService browserService, final int loadProgress, final int loadTarget)
   {
      this.browserService = browserService;
      this.loadProgress = loadProgress;
      this.loadTarget = loadTarget;
   }
}