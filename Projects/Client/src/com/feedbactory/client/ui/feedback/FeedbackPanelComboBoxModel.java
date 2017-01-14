
package com.feedbactory.client.ui.feedback;


import com.feedbactory.shared.feedback.FeedbackItem;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;


final class FeedbackPanelComboBoxModel extends AbstractListModel implements ComboBoxModel
{
   // The items collection contains all of the feedback item profiles browsed during the session, but is not directly used by the model's public API.
   final private List<FeedbackItemProfile> items = new ArrayList<FeedbackItemProfile>();

   // The filteredItems is manually populated by the complete items collection, and is always the collection used by the model's public API, ie. size, elementAt, etc.
   final private List<FeedbackItemProfile> filteredItems = new ArrayList<FeedbackItemProfile>();
   final private FeedbackItemComparator itemsComparator = new FeedbackItemComparator();

   private FeedbackItemProfile selectedItem;
   private String activeFilter = "";


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class FeedbackItemComparator implements Comparator<FeedbackItemProfile>
   {
      @Override
      final public int compare(final FeedbackItemProfile itemProfileOne, final FeedbackItemProfile itemProfileTwo)
      {
         return itemProfileOne.getFullName().compareToIgnoreCase(itemProfileTwo.getFullName());
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private boolean handleAddIfAbsent(final FeedbackItemProfile newItemProfile)
   {
      final ListIterator<FeedbackItemProfile> itemsIterator = items.listIterator();
      FeedbackItemProfile itemProfile;
      FeedbackItemProfile existingItemProfile = null;
      boolean updateItemProfile = true;

      /* Add the item if it's absent, and return true.
       * If the item is already present and none of its profile details have changed, do nothing and return false.
       * If the item is already present and some of its profile details have changed, update the old item profile to the new one and
       * again return false but be sure to preserve the item's selection state; if the changed item is selected when the fireContentsChanged()
       * is called, the combo box will detect that it has a 'new' selected item even though the (immutable) objects will be identical
       * aside from the profile details. An itemStateChanged event will be triggered, and this must be treated as a special case,
       * effectively ignored one time in the aftermath of updating the selected item here. The onus is on the parent owner to detect and handle
       * this case.
       *
       * Note that the panel doesn't notify the child panels about the changed profile details, since that happens elsewhere in the pipeline
       * after a browsed page completion notification - see FeedbackCategoryUIManager.reportBrowsedPage().
       */
      while (itemsIterator.hasNext())
      {
         itemProfile = itemsIterator.next();
         if (newItemProfile.getItem().equals(itemProfile.getItem()))
         {
            existingItemProfile = itemProfile;
            if (newItemProfile.equals(itemProfile))
               updateItemProfile = false;
            else
            {
               itemsIterator.remove();

               if (selectedItem == existingItemProfile)
                  selectedItem = newItemProfile;
            }

            break;
         }
      }

      if (updateItemProfile)
      {
         items.add(newItemProfile);
         Collections.sort(items, itemsComparator);

         if (activeFilter.isEmpty())
         {
            /* For the common case of no active filter, this is a quicker way of updating the filtered items list -
             * the sorting has already been done on the master items list.
             */
            clearFilter();
         }
         else if (filterMatches(newItemProfile))
         {
            filteredItems.add(newItemProfile);

            Collections.sort(filteredItems, itemsComparator);
            fireContentsChanged(this, 0, filteredItems.size() - 1);
         }
      }

      return (existingItemProfile == null);
   }


   private boolean filterMatches(final FeedbackItemProfile itemProfile)
   {
      /* Only very basic text substring matching is applied here, since it's the user filtering on their browsed items during the session,
       * not a more comprehensive collection.
       * I considered applying the filter to the item profile tags as well, but I wonder if it might be a bit confusing to the user.
       */
      return itemProfile.getFullName().toLowerCase().contains(activeFilter);
   }


   private boolean handleApplyFilter(final String rawFilter, final boolean narrowSearchFromPrevious)
   {
      activeFilter = rawFilter.toLowerCase();

      final List<FeedbackItemProfile> previousList = new ArrayList<FeedbackItemProfile>(filteredItems);

      if (! narrowSearchFromPrevious)
      {
         filteredItems.clear();
         filteredItems.addAll(items);
      }

      final Iterator<FeedbackItemProfile> iterator = filteredItems.iterator();
      FeedbackItemProfile itemProfile;

      while (iterator.hasNext())
      {
         itemProfile = iterator.next();

         if (! filterMatches(itemProfile))
            iterator.remove();
      }

      final boolean hasListChanged = (! filteredItems.equals(previousList));

      if (hasListChanged)
         fireContentsChanged(this, 0, filteredItems.size() - 1);

      return hasListChanged;
   }


   private void handleClearFilter()
   {
      activeFilter = "";
      filteredItems.clear();
      filteredItems.addAll(items);
      fireContentsChanged(this, 0, filteredItems.size() - 1);
   }


   private void handleSetSelectedItemByItemID(final FeedbackItem itemIDToSelect)
   {
      if (itemIDToSelect == null)
      {
         if (selectedItem != null)
            setSelectedItem(null);
      }
      else if ((selectedItem == null) || (! selectedItem.getItem().equals(itemIDToSelect)))
      {
         for (final FeedbackItemProfile item : items)
         {
            if (item.getItem().equals(itemIDToSelect))
            {
               setSelectedItem(item);
               break;
            }
         }
      }
   }


   private void handleSetSelectedItem(final Object anItem)
   {
      if ((anItem == null) || (anItem instanceof FeedbackItemProfile))
      {
         selectedItem = (FeedbackItemProfile) anItem;
         fireContentsChanged(this, 0, filteredItems.size() - 1);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final boolean addIfAbsent(final FeedbackItemProfile newItemProfile)
   {
      return handleAddIfAbsent(newItemProfile);
   }


   final boolean applyFilter(final String newFilter, final boolean narrowSearchFromPrevious)
   {
      return handleApplyFilter(newFilter, narrowSearchFromPrevious);
   }


   final void clearFilter()
   {
      handleClearFilter();
   }


   final void setSelectedItemByItemID(final FeedbackItem itemIDToSelect)
   {
      handleSetSelectedItemByItemID(itemIDToSelect);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public int getSize()
   {
      return filteredItems.size();
   }


   @Override
   final public FeedbackItemProfile getElementAt(final int index)
   {
      return filteredItems.get(index);
   }


   @Override
   final public FeedbackItemProfile getSelectedItem()
   {
      return selectedItem;
   }


   @Override
   final public void setSelectedItem(final Object anItem)
   {
      handleSetSelectedItem(anItem);
   }
}