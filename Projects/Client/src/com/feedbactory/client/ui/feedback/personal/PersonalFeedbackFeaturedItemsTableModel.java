/* Memos:
 * - JTable is sort of smart. If there are 1000 rows it will call getValueAt() for all of the Strings, but for the ImageIcons it knows better, and
 *   will only call getValueAt() for maybe twice the number of visible rows.
 *   This is a good thing, as it saves from a flood of image load requests from the outset if the user is viewing a long list of feedback items.
 *   I should keep this in mind if I decide to change the column class for the thumbnail column to something else, for which JTable might not be
 *   so clever. In the worst case could the image loading be moved to the renderer? The problem is the fireTableCellUpdated() call after image load,
 *   there is no equivalent method on the renderer.
 */

package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.client.ui.component.ImageLoader;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackFeaturedPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.table.AbstractTableModel;


final class PersonalFeedbackFeaturedItemsTableModel extends AbstractTableModel
{
   static final private int MaximumItemThumbnailDimensions = 40;
   static final private String SuppressedLowAverageRatingLabel;

   static
   {
      final NumberFormat numberFormat = NumberFormat.getNumberInstance();
      numberFormat.setMinimumFractionDigits(1);
      SuppressedLowAverageRatingLabel = "Less than " + numberFormat.format(PersonalFeedbackBasicSummary.MinimumVisibleAverageRating / 10f);
   }

   static final int ThumbnailColumnIndex = 0;
   static final int DisplayNameColumnIndex = 1;
   static final int WebsiteColumnIndex = 2;
   static final int FeedbackSummaryColumnIndex = 3;
   static final int NumberOfRatingsColumnIndex = 4;
   static final int FirstRatedColumnIndex = 5;

   // Required for blank table cell thumbnails. ImageIcon will fail with an NPE if a null is passed for setImage().
   static final private ImageIcon BlankThumbnailIcon = new ImageIcon();

   private List<PersonalFeedbackFeaturedPerson> featuredFeedbackItems = Collections.emptyList();

   final private ImageIcon thumbnailPhotoIcon = new ImageIcon();
   final private ImageLoader imageLoader;


   PersonalFeedbackFeaturedItemsTableModel(final List<PersonalFeedbackFeaturedPerson> featuredFeedbackItems, final ImageLoader imageLoader)
   {
      this.featuredFeedbackItems = featuredFeedbackItems;
      this.imageLoader = imageLoader;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class ImageLoadRequesterDelegate implements ImageLoader.ImageLoadRequester
   {
      final private int rowIndex;


      private ImageLoadRequesterDelegate(final int rowIndex)
      {
         this.rowIndex = rowIndex;
      }


      @Override
      final public void reportImageLoaded(final String imageURL, final BufferedImage image, final Throwable exception)
      {
         handleImageLoaded(rowIndex, imageURL, image);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void handleImageLoaded(final int rowIndex, final String imageURL, final BufferedImage image)
   {
      if ((image != null) && (rowIndex < featuredFeedbackItems.size()) && featuredFeedbackItems.get(rowIndex).personProfile.getThumbnailImageURL().equals(imageURL))
         fireTableCellUpdated(rowIndex, ThumbnailColumnIndex);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private String handleGetColumnName(final int columnIndex)
   {
      switch (columnIndex)
      {
         case ThumbnailColumnIndex:
            return "";
         case DisplayNameColumnIndex:
            return "Name";
         case WebsiteColumnIndex:
            return "Website";
         case FeedbackSummaryColumnIndex:
            return "Rating";
         case NumberOfRatingsColumnIndex:
            return "Rated by";
         case FirstRatedColumnIndex:
            return "First rated";

         default:
            throw new IllegalArgumentException();
      }
   }


   private Class<?> handleGetColumnClass(final int columnIndex)
   {
      switch (columnIndex)
      {
         case ThumbnailColumnIndex:
            return ImageIcon.class;
         case DisplayNameColumnIndex:
            return String.class;
         case WebsiteColumnIndex:
            return String.class;
         case FeedbackSummaryColumnIndex:
            return String.class;
         case NumberOfRatingsColumnIndex:
            return Integer.class;
         case FirstRatedColumnIndex:
            return Long.class;

         default:
            throw new IllegalArgumentException();
      }
   }


   private Object handleGetValueAt(final int rowIndex, final int columnIndex)
   {
      final PersonalFeedbackFeaturedPerson featuredFeedbackItem = featuredFeedbackItems.get(rowIndex);

      switch (columnIndex)
      {
         case ThumbnailColumnIndex:
            return initialiseThumbnailIconForCell(rowIndex, featuredFeedbackItem.personProfile);
         case DisplayNameColumnIndex:
            return featuredFeedbackItem.personProfile.getFullName();
         case WebsiteColumnIndex:
            return featuredFeedbackItem.personProfile.getWebsite().getName();
         case FeedbackSummaryColumnIndex:
            final byte rating = featuredFeedbackItem.feedbackSummary.getFeedbackResultSummary().byteValue();
            if (rating != PersonalFeedbackBasicSummary.SuppressedLowAverageRating)
            {
               final NumberFormat numberFormat = NumberFormat.getNumberInstance();
               numberFormat.setMinimumFractionDigits(1);
               return numberFormat.format(rating / 10f);
            }
            else
               return SuppressedLowAverageRatingLabel;

         case NumberOfRatingsColumnIndex:
            return featuredFeedbackItem.feedbackSummary.getNumberOfRatings();
         case FirstRatedColumnIndex:
            return featuredFeedbackItem.creationTime;

         default:
            throw new IllegalArgumentException();
      }
   }


   private Icon initialiseThumbnailIconForCell(final int rowIndex, final PersonalFeedbackPersonProfile personProfile)
   {
      final String photoURL = personProfile.getThumbnailImageURL();
      if (photoURL != null)
      {
         final BufferedImage thumbnailPhoto = imageLoader.loadImage(photoURL, MaximumItemThumbnailDimensions, MaximumItemThumbnailDimensions,
                                                                    new ImageLoadRequesterDelegate(rowIndex));
         if (thumbnailPhoto != null)
         {
            final BufferedImage thumbnailScaledImage = UIUtilities.getSquareCroppedScaledImage(thumbnailPhoto, MaximumItemThumbnailDimensions,
                                                                                               RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            thumbnailPhotoIcon.setImage(thumbnailScaledImage);
            return thumbnailPhotoIcon;
         }
      }

      // If there's no image, don't return the thumbnailPhotoIcon which likely still has a thumbnail image for a previous table cell attached to it.
      return BlankThumbnailIcon;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void setFeaturedFeedbackItems(final List<PersonalFeedbackFeaturedPerson> featuredFeedbackItems)
   {
      this.featuredFeedbackItems = featuredFeedbackItems;
      reportFeaturedFeedbackItemsUpdated();
   }


   final void reportFeaturedFeedbackItemUpdated(final int rowIndex)
   {
      fireTableRowsUpdated(rowIndex, rowIndex);
   }


   final void reportFeaturedFeedbackItemRemoved(final int rowIndex)
   {
      fireTableRowsDeleted(rowIndex, rowIndex);
   }


   final void reportFeaturedFeedbackItemsUpdated()
   {
      fireTableDataChanged();
   }


   final PersonalFeedbackFeaturedPerson getFeaturedItem(final int rowIndex)
   {
      return featuredFeedbackItems.get(rowIndex);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public int getRowCount()
   {
      return featuredFeedbackItems.size();
   }


   @Override
   final public int getColumnCount()
   {
      return 6;
   }


   @Override
   final public String getColumnName(final int columnIndex)
   {
      return handleGetColumnName(columnIndex);
   }


   @Override
   final public Class<?> getColumnClass(final int columnIndex)
   {
      return handleGetColumnClass(columnIndex);
   }



   @Override
   final public Object getValueAt(final int rowIndex, final int columnIndex)
   {
      return handleGetValueAt(rowIndex, columnIndex);
   }
}