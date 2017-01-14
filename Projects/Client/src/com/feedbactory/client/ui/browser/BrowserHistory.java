
package com.feedbactory.client.ui.browser;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


final class BrowserHistory
{
   final private List<BrowserHistoryItem> sortedHistoryItems = new ArrayList<BrowserHistoryItem>();
   final private Set<String> historyURLs = new HashSet<String>();
   final private HistoryItemComparator historyItemComparator = new HistoryItemComparator();


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class HistoryItemComparator implements Comparator<BrowserHistoryItem>
   {
      @Override
      final public int compare(final BrowserHistoryItem itemOne, final BrowserHistoryItem itemTwo)
      {
         if (itemOne.getVisitCount() > itemTwo.getVisitCount())
            return -1;
         else if (itemOne.getVisitCount() < itemTwo.getVisitCount())
            return 1;
         else
            return itemOne.getPageTitle().compareToIgnoreCase(itemTwo.getPageTitle());
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void handleAddToHistory(final String URL, final String pageTitle)
   {
      final BrowserHistoryItem browserHistoryItem;

      if (historyURLs.contains(URL))
      {
         browserHistoryItem = removeSortedHistoryItemURL(URL);

         // To ensure the integrity of the sorted history items list, the page count must only ever be mutated here.
         browserHistoryItem.incrementVisitCount();
         browserHistoryItem.setPageTitle(pageTitle);
      }
      else
      {
         browserHistoryItem = new BrowserHistoryItem(URL, pageTitle);
         historyURLs.add(URL);
      }

      /* Items having different URLs but identical (case insensitive) titles will produce a match for the binary search when using
       * the existing historyItemComparator, so both cases (found, not found) need to be catered for.
       */
      int sortedIndex = Collections.binarySearch(sortedHistoryItems, browserHistoryItem, historyItemComparator);
      if (sortedIndex < 0)
      {
         sortedIndex = -(sortedIndex + 1);
         sortedHistoryItems.add(sortedIndex, browserHistoryItem);
      }
      else
         sortedHistoryItems.add(sortedIndex, browserHistoryItem);
   }


   private BrowserHistoryItem removeSortedHistoryItemURL(final String url)
   {
      final Iterator<BrowserHistoryItem> urlIterator = sortedHistoryItems.iterator();
      BrowserHistoryItem nextItem;

      while (urlIterator.hasNext())
      {
         nextItem = urlIterator.next();
         if (nextItem.getPageURL().equals(url))
         {
            // There will only be one matching URL in the sorted list, so the work is done.
            urlIterator.remove();
            return nextItem;
         }
      }

      // Should not be possible if the historyURLs collection has already indicated that the URL is present in the sorted list.
      assert false;
      return null;
   }


   private void handleClearHistory()
   {
      historyURLs.clear();
      sortedHistoryItems.clear();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void addToHistory(final String URL, final String pageTitle)
   {
      handleAddToHistory(URL, pageTitle);
   }


   final Collection<BrowserHistoryItem> getSortedBrowserHistoryItems()
   {
      // No read-only protection for package-private caller.
      return sortedHistoryItems;
   }


   final void clearHistory()
   {
      handleClearHistory();
   }
}