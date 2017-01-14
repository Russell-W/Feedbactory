/* Memos:
 * - JTable is sort of smart. If there are 1000 rows it will call getValueAt() for all of the Strings, but for the ImageIcons it knows better, and
 *   will only call getValueAt() for maybe twice the number of visible rows.
 *   This is a good thing, as it saves from a flood of image load requests from the outset if the user is viewing a long list of feedback items.
 *   I should keep this in mind if I decide to change the column class for the thumbnail column to something else, for which JTable might not be
 *   so clever. In the worst case could the image loading be moved to the renderer? The problem is the fireTableCellUpdated() call after image load,
 *   there is no equivalent method on the renderer.
 */

package com.feedbactory.client.ui.feedback;


import com.feedbactory.client.core.feedback.ItemProfileFeedbackSummary;
import com.feedbactory.client.ui.component.ImageLoader;
import com.feedbactory.client.ui.component.ImageLoader.ImageLoadRequester;
import com.feedbactory.client.ui.UIUtilities;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.table.AbstractTableModel;


final class FeedbackFromUserTableModel extends AbstractTableModel
{
   static final private int MaximumItemThumbnailDimensions = 40;

   static final int ThumbnailColumnIndex = 0;
   static final int DisplayNameColumnIndex = 1;
   static final int WebsiteColumnIndex = 2;
   static final int FeedbackSubmissionSummaryColumnIndex = 3;
   static final int FeedbackSubmissionTimeColumnIndex = 4;
   static final int FeedbackSummaryColumnIndex = 5;
   static final int NumberOfRatingsColumnIndex = 6;

   // Required for blank table cell thumbnails. ImageIcon will fail with an NPE if a null is passed for setImage().
   static final private ImageIcon BlankThumbnailIcon = new ImageIcon();

   final private ImageIcon thumbnailPhotoIcon = new ImageIcon();
   final private ImageLoader imageLoader;

   private List<ItemProfileFeedbackSummary> userFeedbackSubmissions;


   FeedbackFromUserTableModel(final ImageLoader imageLoader)
   {
      this.imageLoader = imageLoader;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class ImageLoadRequesterDelegate implements ImageLoadRequester
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
      /* The table data may have been filtered since the load request was first initiated, make sure that the thumbnail image retrieved (if non-null)
       * is still attached to the reported row index.
       */
      if ((image != null) && (rowIndex < userFeedbackSubmissions.size()) && imageURL.equals(userFeedbackSubmissions.get(rowIndex).itemProfile.getThumbnailImageURL()))
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
         case FeedbackSubmissionSummaryColumnIndex:
            return "Rated by me";
         case FeedbackSubmissionTimeColumnIndex:
            return "On";
         case FeedbackSummaryColumnIndex:
            return "Rating";
         case NumberOfRatingsColumnIndex:
            return "Rated by";

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
         case FeedbackSubmissionSummaryColumnIndex:
            return ItemProfileFeedbackSummary.class;
         case FeedbackSubmissionTimeColumnIndex:
            return Long.class;
         case FeedbackSummaryColumnIndex:
            return ItemProfileFeedbackSummary.class;
         case NumberOfRatingsColumnIndex:
            return Integer.class;

         default:
            throw new IllegalArgumentException();
      }
   }


   private Object handleGetValueAt(final int rowIndex, final int columnIndex)
   {
      final ItemProfileFeedbackSummary itemProfileFeedbackSummary = userFeedbackSubmissions.get(rowIndex);

      switch (columnIndex)
      {
         case ThumbnailColumnIndex:
            return initialiseThumbnailIconForCell(rowIndex, itemProfileFeedbackSummary);
         case DisplayNameColumnIndex:
            return itemProfileFeedbackSummary.itemProfile.getFullName();
         case WebsiteColumnIndex:
            return itemProfileFeedbackSummary.itemProfile.getItem().getWebsite().getName();
         case FeedbackSubmissionSummaryColumnIndex:
            /* For this column, return the entire ItemProfileFeedbackSummary object, as it is needed to fetch the correct FeedbackCategoryTableFormatter
             * object for sorting on this column. The renderer can simply drill down to the itemProfileFeedbackSummary.feedbackSubmissionSummary object.
             */
            return itemProfileFeedbackSummary;
         case FeedbackSubmissionTimeColumnIndex:
            return itemProfileFeedbackSummary.feedbackSubmissionTime;
         case FeedbackSummaryColumnIndex:
            /* For this column, return the entire ItemProfileFeedbackSummary object, as it is again needed to fetch the correct FeedbackCategoryTableFormatter
             * object for sorting on this column.
             */
            return itemProfileFeedbackSummary;
         case NumberOfRatingsColumnIndex:
            return itemProfileFeedbackSummary.feedbackSummary.getNumberOfRatings();

         default:
            throw new IllegalArgumentException();
      }
   }


   private Icon initialiseThumbnailIconForCell(final int rowIndex, final ItemProfileFeedbackSummary itemProfileFeedbackSummary)
   {
      final String photoURL = itemProfileFeedbackSummary.itemProfile.getThumbnailImageURL();
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


   final ItemProfileFeedbackSummary getUserFeedbackSubmission(final int rowIndex)
   {
      return userFeedbackSubmissions.get(rowIndex);
   }


   final void setUserFeedbackSubmissions(final List<ItemProfileFeedbackSummary> feedbackSubmissions)
   {
      this.userFeedbackSubmissions = feedbackSubmissions;
      fireTableDataChanged();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public int getRowCount()
   {
      return userFeedbackSubmissions.size();
   }


   @Override
   final public int getColumnCount()
   {
      return 7;
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