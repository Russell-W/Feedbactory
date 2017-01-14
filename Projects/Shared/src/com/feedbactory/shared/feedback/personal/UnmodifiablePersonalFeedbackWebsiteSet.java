
package com.feedbactory.shared.feedback.personal;


import java.util.Collection;
import java.util.Iterator;
import java.util.Set;


final class UnmodifiablePersonalFeedbackWebsiteSet extends PersonalFeedbackWebsiteSet
{
   UnmodifiablePersonalFeedbackWebsiteSet()
   {
      super();
   }


   UnmodifiablePersonalFeedbackWebsiteSet(final Set<PersonalFeedbackWebsite> otherSet)
   {
      super(otherSet);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class UnmodifiableSetIterator extends PersonalFeedbackWebsiteSet.SetIterator
   {
      @Override
      final public void remove()
      {
         throw new UnsupportedOperationException();
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   @Override
   final public boolean add(final PersonalFeedbackWebsite personalFeedbackWebsite)
   {
      throw new UnsupportedOperationException();
   }


   @Override
   final public boolean remove(final Object object)
   {
      throw new UnsupportedOperationException();
   }


   @Override
   final public boolean addAll(final Collection<? extends PersonalFeedbackWebsite> collection)
   {
      throw new UnsupportedOperationException();
   }


   @Override
   final public boolean retainAll(final Collection<?> collection)
   {
      throw new UnsupportedOperationException();
   }


   @Override
   final public boolean removeAll(final Collection<?> collection)
   {
      throw new UnsupportedOperationException();
   }


   @Override
   final public void clear()
   {
      throw new UnsupportedOperationException();
   }


   @Override
   final public Iterator<PersonalFeedbackWebsite> iterator()
   {
      return new UnmodifiableSetIterator();
   }
}