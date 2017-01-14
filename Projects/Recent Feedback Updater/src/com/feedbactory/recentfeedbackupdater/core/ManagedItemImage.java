
package com.feedbactory.recentfeedbackupdater.core;


final class ManagedItemImage
{
   final String imageFileNameKey;
   final int imageWidth;
   final int imageHeight;
   final boolean isActive;


   ManagedItemImage(final String imageFileNameKey, final int cachedImageWidth, final int cachedImageHeight, final boolean isActive)
   {
      this.imageFileNameKey = imageFileNameKey;
      this.imageWidth = cachedImageWidth;
      this.imageHeight = cachedImageHeight;
      this.isActive = isActive;
   }
}