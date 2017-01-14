
package com.feedbactory.recentfeedbackupdater.core;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackFeaturedPerson;


final class RecentFeedbackItemImage
{
   final PersonalFeedbackFeaturedPerson featuredItem;
   final ManagedItemImage itemImage;


   RecentFeedbackItemImage(final PersonalFeedbackFeaturedPerson featuredItem, final ManagedItemImage itemImage)
   {
      this.featuredItem = featuredItem;
      this.itemImage = itemImage;
   }
}