
package com.feedbactory.client.ui.browser;


final class BrowserHistoryItem
{
   final private String pageURL;
   private String pageTitle;
   private int visitCount;


   BrowserHistoryItem(final String pageURL, final String pageTitle)
   {
      this(pageURL, pageTitle, 1);
   }


   BrowserHistoryItem(final String pageURL, final String pageTitle, final int initialVisitCount)
   {
      this.pageURL = pageURL;
      this.pageTitle = pageTitle;
      this.visitCount = initialVisitCount;
   }


   final String getPageURL()
   {
      return pageURL;
   }


   final String getPageTitle()
   {
      return pageTitle;
   }


   final void setPageTitle(final String pageTitle)
   {
      this.pageTitle = pageTitle;
   }


   final int getVisitCount()
   {
      return visitCount;
   }


   final void incrementVisitCount()
   {
      visitCount ++;
   }


   @Override
   final public String toString()
   {
      return pageURL;
   }
}