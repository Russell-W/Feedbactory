
package com.feedbactory.client.ui.browser;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;


final class BrowserHistoryComboBoxModel extends AbstractListModel implements ComboBoxModel
{
   final private BrowserHistory browserHistory;

   final private List<BrowserHistoryItem> filteredBrowserHistory;

   private Object selectedItem;


   BrowserHistoryComboBoxModel(final BrowserHistory browserHistory)
   {
      this.browserHistory = browserHistory;
      filteredBrowserHistory = new ArrayList<BrowserHistoryItem>(browserHistory.getSortedBrowserHistoryItems());
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleRefreshFromBrowserHistory()
   {
      filteredBrowserHistory.clear();
      filteredBrowserHistory.addAll(browserHistory.getSortedBrowserHistoryItems());
      fireContentsChanged(this, 0, filteredBrowserHistory.size() - 1);
   }


   private boolean handleApplyFilter(final String newFilter, final boolean narrowSearchFromPrevious)
   {
      final String lowercaseFilter = newFilter.toLowerCase();

      final List<BrowserHistoryItem> previousList = new ArrayList<BrowserHistoryItem>(filteredBrowserHistory);

      if (! narrowSearchFromPrevious)
      {
         filteredBrowserHistory.clear();
         filteredBrowserHistory.addAll(browserHistory.getSortedBrowserHistoryItems());
      }

      final Iterator<BrowserHistoryItem> iterator = filteredBrowserHistory.iterator();
      BrowserHistoryItem browserHistoryItem;

      while (iterator.hasNext())
      {
         browserHistoryItem = iterator.next();

         if ((! browserHistoryItem.getPageURL().toLowerCase().contains(lowercaseFilter)) &&
             (! browserHistoryItem.getPageTitle().toLowerCase().contains(lowercaseFilter)))
            iterator.remove();
      }

      final boolean hasListChanged = (! filteredBrowserHistory.equals(previousList));

      if (hasListChanged)
         fireContentsChanged(this, 0, filteredBrowserHistory.size() - 1);

      return hasListChanged;
   }


   private void handleSetSelectedItem(final Object selectedItem)
   {
      // The equals check isn't really required, but it saves from processing a lot of redundant refreshes fired by page load events.
      if (((selectedItem != null) && (! selectedItem.equals(this.selectedItem))) ||
          ((this.selectedItem != null) && (! this.selectedItem.equals(selectedItem))))
      {
         this.selectedItem = selectedItem;
         fireContentsChanged(this, 0, filteredBrowserHistory.size() - 1);
      }
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final void refreshFromBrowserHistory()
   {
      handleRefreshFromBrowserHistory();
   }


   final boolean applyFilter(final String newFilter, final boolean narrowSearchFromPrevious)
   {
      return handleApplyFilter(newFilter, narrowSearchFromPrevious);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public void setSelectedItem(final Object selectedItem)
   {
      handleSetSelectedItem(selectedItem);
   }


   @Override
   final public Object getSelectedItem()
   {
      return selectedItem;
   }


   @Override
   final public int getSize()
   {
      return filteredBrowserHistory.size();
   }


   @Override
   final public Object getElementAt(final int index)
   {
      return filteredBrowserHistory.get(index);
   }
}