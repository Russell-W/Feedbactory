/* Memos:
 * - Uses an integer bitset to represent a set of PersonalFeedbackWebsites.
 * - Website IDs must be between 0 and 31.
 * - Can obviously extend this to a long - 64 bits - but if the set universe is growing that large it's time to consider alternatives.
 */

package com.feedbactory.shared.feedback.personal;


import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;


public class PersonalFeedbackWebsiteSet implements Set<PersonalFeedbackWebsite>
{
   static final public PersonalFeedbackWebsiteSet EmptySet = new UnmodifiablePersonalFeedbackWebsiteSet();

   /* Need to tread carefully here, re: same issue as mentioned in PersonalFeedbackWebsite. PersonalFeedbackWebsite.getWebsites() will itself create a
    * PersonalFeedbackWebsiteSet however the constructor and add method it uses to initialise the object do not require the use of this uninitialised static field,
    * nor does the constructor used here and the getElementsBitArray method it calls.
    */
   static final private int ValidElementBits = ((PersonalFeedbackWebsiteSet) PersonalFeedbackWebsite.getWebsites()).elementsBitArray;

   private int elementsBitArray;
   private int size;


   public PersonalFeedbackWebsiteSet()
   {
   }


   public PersonalFeedbackWebsiteSet(final PersonalFeedbackWebsiteSet otherSet)
   {
      this.elementsBitArray = otherSet.elementsBitArray;
      this.size = otherSet.size;
   }


   public PersonalFeedbackWebsiteSet(final Set<PersonalFeedbackWebsite> otherSet)
   {
      if (otherSet instanceof PersonalFeedbackWebsiteSet)
      {
         final PersonalFeedbackWebsiteSet otherPersonalFeedbackWebsiteSet = (PersonalFeedbackWebsiteSet) otherSet;
         this.elementsBitArray = otherPersonalFeedbackWebsiteSet.elementsBitArray;
         this.size = otherPersonalFeedbackWebsiteSet.size;
      }
      else
      {
         elementsBitArray = getElementsBitArray(otherSet);
         size = otherSet.size();
      }
   }


   public PersonalFeedbackWebsiteSet(final int elementsBitArray)
   {
      if ((elementsBitArray | ValidElementBits) != ValidElementBits)
         throw new IllegalArgumentException("Invalid personal feedback website bit array: " + elementsBitArray);

      this.elementsBitArray = elementsBitArray;
      size = Integer.bitCount(elementsBitArray);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   class SetIterator implements Iterator<PersonalFeedbackWebsite>
   {
      private int iteratorElementIndex;
      private int removeElementIndex = -1;


      @Override
      final public boolean hasNext()
      {
         if (iteratorElementIndex < 32)
            return isBitOrHigherSet(iteratorElementIndex);

         return false;
      }


      @Override
      final public PersonalFeedbackWebsite next()
      {
         while (iteratorElementIndex < 32)
         {
            if (isBitSet(iteratorElementIndex))
            {
               removeElementIndex = iteratorElementIndex;
               iteratorElementIndex ++;
               return PersonalFeedbackWebsite.fromValue((short) removeElementIndex);
            }

            iteratorElementIndex ++;
         }

         throw new NoSuchElementException();
      }


      @Override
      public void remove()
      {
         if (removeElementIndex != -1)
         {
            clearBit(removeElementIndex);
            removeElementIndex = -1;
         }
         else
            throw new IllegalStateException();
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private boolean checkBitIndex(final int bitIndex)
   {
      return ((bitIndex >= 0) && (bitIndex < 32));
   }


   private boolean isBitOrHigherSet(final int bitIndex)
   {
      assert checkBitIndex(bitIndex);

      return ((elementsBitArray >>> bitIndex) != 0);
   }


   private boolean isBitSet(final int bitIndex)
   {
      assert checkBitIndex(bitIndex);

      return (((1 << bitIndex) & elementsBitArray) != 0);
   }


   private boolean setBit(final int bitIndex)
   {
      assert checkBitIndex(bitIndex);

      final int bitMask = (1 << bitIndex);
      if ((elementsBitArray | bitMask) != elementsBitArray)
      {
         elementsBitArray |= bitMask;
         size ++;

         return true;
      }
      else
         return false;
   }


   private boolean clearBit(final int bitIndex)
   {
      assert checkBitIndex(bitIndex);

      final int bitMask = ~(1 << bitIndex);
      if ((elementsBitArray & bitMask) != elementsBitArray)
      {
         elementsBitArray &= bitMask;
         size --;

         return true;
      }
      else
         return false;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private boolean handleEquals(final Object otherObject)
   {
      /* This implementation follows the contract for Set.equals(), so that sets using this implementation can be compared
       * to sets using other implementations.
       */
      if (otherObject instanceof PersonalFeedbackWebsiteSet)
         return (elementsBitArray == ((PersonalFeedbackWebsiteSet) otherObject).elementsBitArray);
      if (! (otherObject instanceof Set))
         return false;

      final Set<?> otherSet = (Set<?>) otherObject;

      /* In practice, if a set of this implementation is being compared with a different set implementation,
       * it's very likely that otherSet is Collections.emptySet() or Collections.unmodifiableSet().
       * emptySet() will be dealt with quickly, since either the size doesn't match here or (if this set is also empty)
       * isBitOrHigherSet() will cause an immediate bail out. Collections.unmodifiableSet() is likely wrapped around
       * another PersonalFeedbackWebsiteSet, in which case there's no advantage either way in which collection to
       * iterate over; iterating over the other set will result in essentially this same code but in reverse.
       * At least this approach saves an extra object allocation for the iterator.
       */
      if (otherSet.size() == size)
      {
         int bitArrayIndex = 0;
         while ((bitArrayIndex < 32) && isBitOrHigherSet(bitArrayIndex))
         {
            if (isBitSet(bitArrayIndex) && (! otherSet.contains(PersonalFeedbackWebsite.fromValue((short) bitArrayIndex))))
               return false;

            bitArrayIndex ++;
         }

         return true;
      }
      else
         return false;
   }


   private int handleHashCode()
   {
      /* This implementation follows the contract for Set.hashCode(), so that sets using this implementation will generate identical hash codes
       * to sets using other implementations.
       */
      int hashCode = 0;

      int bitArrayIndex = 0;
      while ((bitArrayIndex < 32) && isBitOrHigherSet(bitArrayIndex))
      {
         if (isBitSet(bitArrayIndex))
            hashCode += PersonalFeedbackWebsite.fromValue((short) bitArrayIndex).hashCode();

         bitArrayIndex ++;
      }

      return hashCode;
   }


   private boolean handleContains(final Object object)
   {
      if (object instanceof PersonalFeedbackWebsite)
         return isBitSet(((PersonalFeedbackWebsite) object).getID());
      else
         return false;
   }


   private PersonalFeedbackWebsite[] handleToArray()
   {
      final PersonalFeedbackWebsite[] websiteArray = new PersonalFeedbackWebsite[size];
      int websiteArrayIndex = 0;

      int bitArrayIndex = 0;
      while ((bitArrayIndex < 32) && isBitOrHigherSet(bitArrayIndex))
      {
         if (isBitSet(bitArrayIndex))
         {
            websiteArray[websiteArrayIndex] = PersonalFeedbackWebsite.fromValue((short) bitArrayIndex);
            websiteArrayIndex ++;
         }

         bitArrayIndex ++;
      }

      return websiteArray;
   }


   @SuppressWarnings("unchecked")
   private <T> T[] handleToArray(final T[] suppliedArray)
   {
      final T[] outputArray;
      if (suppliedArray.length >= size)
         outputArray = suppliedArray;
      else
         outputArray = (T[]) Array.newInstance(suppliedArray.getClass().getComponentType(), size);

      int outputArrayIndex = 0;

      int bitArrayIndex = 0;
      while ((bitArrayIndex < 32) && isBitOrHigherSet(bitArrayIndex))
      {
         if (isBitSet(bitArrayIndex))
         {
            outputArray[outputArrayIndex] = (T) PersonalFeedbackWebsite.fromValue((short) bitArrayIndex);
            outputArrayIndex ++;
         }

         bitArrayIndex ++;
      }

      // Add null terminator as required by the toArray() API if the provided array has the space.
      if (suppliedArray.length > size)
         outputArray[size] = null;

      return outputArray;
   }


   private boolean handleContainsAll(final Collection<?> collection)
   {
      if (collection instanceof PersonalFeedbackWebsiteSet)
      {
         final int otherElementsBitArray = ((PersonalFeedbackWebsiteSet) collection).elementsBitArray;
         return ((elementsBitArray | otherElementsBitArray) == elementsBitArray);
      }

      // Can't take shortcuts here based on the collection size; it may be an array or map containing multiple identical elements.
      PersonalFeedbackWebsite website;

      // May throw NullPointerException, as specified by the Set API.
      for (final Object object : collection)
      {
         // May throw ClassCastException, as specified by the Set API.
         website = (PersonalFeedbackWebsite) object;

         // May throw NullPointerException, as specified by the Set API.
         if (! isBitSet(website.getID()))
            return false;
      }

      return true;
   }


   private boolean handleAddAll(final Collection<? extends PersonalFeedbackWebsite> collection)
   {
      boolean hasSetChanged = false;

      if (collection instanceof PersonalFeedbackWebsiteSet)
      {
         final int otherElementsBitArray = ((PersonalFeedbackWebsiteSet) collection).elementsBitArray;
         if ((elementsBitArray | otherElementsBitArray) != elementsBitArray)
         {
            elementsBitArray |= otherElementsBitArray;
            size = Integer.bitCount(elementsBitArray);
            hasSetChanged = true;
         }
      }
      else
      {
         for (final PersonalFeedbackWebsite website : collection)
         {
            if (add(website))
               hasSetChanged = true;
         }
      }

      return hasSetChanged;
   }


   private boolean handleRetainAll(final Collection<?> collection)
   {
      boolean hasSetChanged = false;

      if (collection instanceof PersonalFeedbackWebsiteSet)
      {
         final int otherElementsBitArray = ((PersonalFeedbackWebsiteSet) collection).elementsBitArray;
         if ((elementsBitArray & otherElementsBitArray) != elementsBitArray)
         {
            elementsBitArray &= otherElementsBitArray;
            size = Integer.bitCount(elementsBitArray);
            hasSetChanged = true;
         }
      }
      else
      {
         int bitArrayIndex = 0;
         while ((bitArrayIndex < 32) && isBitOrHigherSet(bitArrayIndex))
         {
            if (isBitSet(bitArrayIndex) && (! collection.contains(PersonalFeedbackWebsite.fromValue((short) bitArrayIndex))))
            {
               clearBit(bitArrayIndex);
               hasSetChanged = true;
            }

            bitArrayIndex ++;
         }
      }

      return hasSetChanged;
   }


   private boolean handleRemoveAll(final Collection<?> collection)
   {
      boolean hasSetChanged = false;

      if (collection instanceof PersonalFeedbackWebsiteSet)
      {
         final int otherElementsBitArray = ((PersonalFeedbackWebsiteSet) collection).elementsBitArray;
         final int elementRemovalMask = ~otherElementsBitArray;
         if ((elementsBitArray & elementRemovalMask) != elementsBitArray)
         {
            elementsBitArray &= elementRemovalMask;
            size = Integer.bitCount(elementsBitArray);
            hasSetChanged = true;
         }
      }
      else
      {
         PersonalFeedbackWebsite website;
         for (final Object object : collection)
         {
            website = (PersonalFeedbackWebsite) object;
            if (clearBit(website.getID()))
               hasSetChanged = true;
         }
      }

      return hasSetChanged;
   }


   static private int handleGetElementsBitArray(final Set<PersonalFeedbackWebsite> set)
   {
      if (set instanceof PersonalFeedbackWebsiteSet)
         return ((PersonalFeedbackWebsiteSet) set).elementsBitArray;

      int elementsBitArray = 0;

      for (final PersonalFeedbackWebsite website : set)
         elementsBitArray |= (1 << website.getID());

      return elementsBitArray;
   }


   static Set<PersonalFeedbackWebsite> handleUnmodifiableSet(final Set<PersonalFeedbackWebsite> otherSet)
   {
      if (otherSet instanceof UnmodifiablePersonalFeedbackWebsiteSet)
         return otherSet;
      else
         return new UnmodifiablePersonalFeedbackWebsiteSet(otherSet);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public boolean equals(final Object otherObject)
   {
      return handleEquals(otherObject);
   }


   @Override
   final public int hashCode()
   {
      return handleHashCode();
   }


   @Override
   final public int size()
   {
      return size;
   }


   @Override
   final public boolean isEmpty()
   {
      return (size == 0);
   }


   @Override
   final public boolean contains(final Object object)
   {
      return handleContains(object);
   }


   @Override
   public Iterator<PersonalFeedbackWebsite> iterator()
   {
      return new SetIterator();
   }


   @Override
   final public PersonalFeedbackWebsite[] toArray()
   {
      return handleToArray();
   }


   @Override
   final public <T> T[] toArray(final T[] suppliedArray)
   {
      return handleToArray(suppliedArray);
   }


   @Override
   public boolean add(final PersonalFeedbackWebsite personalFeedbackWebsite)
   {
      // May throw NullPointerException, as specified by the Set API.
      return setBit(personalFeedbackWebsite.getID());
   }


   @Override
   public boolean remove(final Object object)
   {
      // May throw ClassCastException or NullPointerException, as specified by the Set API.
      return clearBit(((PersonalFeedbackWebsite) object).getID());
   }


   @Override
   final public boolean containsAll(final Collection<?> collection)
   {
      return handleContainsAll(collection);
   }


   @Override
   public boolean addAll(final Collection<? extends PersonalFeedbackWebsite> collection)
   {
      return handleAddAll(collection);
   }


   @Override
   public boolean retainAll(final Collection<?> collection)
   {
      return handleRetainAll(collection);
   }


   @Override
   public boolean removeAll(final Collection<?> collection)
   {
      return handleRemoveAll(collection);
   }


   @Override
   public void clear()
   {
      elementsBitArray = 0;
      size = 0;
   }


   final public int getElementsBitArray()
   {
      return elementsBitArray;
   }


   static public int getElementsBitArray(final Set<PersonalFeedbackWebsite> set)
   {
      return handleGetElementsBitArray(set);
   }


   static public Set<PersonalFeedbackWebsite> unmodifiableSet(final Set<PersonalFeedbackWebsite> otherSet)
   {
      return handleUnmodifiableSet(otherSet);
   }
}