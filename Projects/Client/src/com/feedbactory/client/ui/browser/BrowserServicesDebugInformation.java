
package com.feedbactory.client.ui.browser;


final public class BrowserServicesDebugInformation
{
   final public String browserUserAgent;

   final private String[] browserURLs;
   final public int activeTabBrowserIndex;


   BrowserServicesDebugInformation(final String browserUserAgent, final String[] browserURLs, final int activeTabBrowserIndex)
   {
      this.browserUserAgent = browserUserAgent;
      this.browserURLs = browserURLs;
      this.activeTabBrowserIndex = activeTabBrowserIndex;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public int getBrowserCount()
   {
      return browserURLs.length;
   }


   final public String getBrowserURL(final int browserTabIndex)
   {
      return browserURLs[browserTabIndex];
   }
}