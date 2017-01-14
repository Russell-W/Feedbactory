
package com.feedbactory.client.ui.feedback;


import com.feedbactory.client.ui.component.ImageLoader;
import com.feedbactory.client.ui.component.ImageLoader.ImageLoadRequester;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.border.Border;
import javax.swing.plaf.basic.ComboPopup;


final class FeedbackPanelComboBoxRenderer extends DefaultListCellRenderer implements ImageLoadRequester
{
   static final private int MaximumItemThumbnailDimensions = 40;
   static final private int CellMarginSize = 5;

   static final private Border RootCellBorder = BorderFactory.createEmptyBorder(3, CellMarginSize, 3, 3);
   static final private Border ListCellBorder = BorderFactory.createEmptyBorder(3, MaximumItemThumbnailDimensions + (CellMarginSize * 3), 3, 3);

   final private JComboBox comboBox;
   final private ImageLoader imageLoader;

   private boolean isPaintingRootCell;
   private BufferedImage activePaintingItemProfileThumbnail;


   FeedbackPanelComboBoxRenderer(final JComboBox comboBox, final ImageLoader imageLoader)
   {
      this.comboBox = comboBox;
      this.imageLoader = imageLoader;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public String getName()
   {
      // Refer to SynthComboBoxUI source - name needs to be set for the Nimbus styling to be correctly applied.
      final String name = super.getName();
      return (name != null) ? name : "ComboBox.renderer";
   }


   @Override
   final public Dimension getPreferredSize()
   {
      if (isPaintingRootCell)
         return new Dimension(0, 0);
      else
         return new Dimension(0, MaximumItemThumbnailDimensions + (2 * CellMarginSize));
   }


   @Override
   final public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus)
   {
      if (index == -1)
      {
         isPaintingRootCell = true;
         setBorder(RootCellBorder);
      }
      else
      {
         isPaintingRootCell = false;
         setBorder(ListCellBorder);
      }

      if (isSelected)
         setBackground(UIConstants.ListCellSelectionHighlightColour);
      else
         setBackground(((index % 2) == 0) ? UIConstants.ListCellRegularColor : UIConstants.ListCellStripeColour);

      if (value != null)
      {
         final FeedbackItemProfile activePaintingItemProfile = (FeedbackItemProfile) value;
         setText(activePaintingItemProfile.getFullName());

         activePaintingItemProfileThumbnail = null;

         if (! isPaintingRootCell)
         {
            final String photoURL = activePaintingItemProfile.getThumbnailImageURL();
            if (photoURL != null)
            {
               final BufferedImage thumbnailImage = imageLoader.loadImage(photoURL, MaximumItemThumbnailDimensions, MaximumItemThumbnailDimensions, this);
               if (thumbnailImage != null)
               {
                  activePaintingItemProfileThumbnail = UIUtilities.getSquareCroppedScaledImage(thumbnailImage, MaximumItemThumbnailDimensions,
                                                                                               RenderingHints.VALUE_INTERPOLATION_BILINEAR);
               }
            }
         }
      }
      else
      {
         setText("");
         setIcon(null);
      }

      return this;
   }


   @Override
   final public void reportImageLoaded(final String imageURL, final BufferedImage image, final Throwable exception)
   {
      if (image != null)
      {
         /* From the renderer there's no reliable way to trigger a repaint of an individual cell in a ComboBox list,
          * especially when the list contents may have changed between the time of the initial image load request and this method call.
          * Therefore when an image thumbnail is loaded, repaint the entire combo box list.
          * Note that this execution path only occurs when the image isn't cached in the imageloader.
          */
         final JPopupMenu comboBoxPopupMenu = (JPopupMenu) comboBox.getUI().getAccessibleChild(comboBox, 0);
         final JList comboBoxList = ((ComboPopup) comboBoxPopupMenu).getList();

         comboBoxList.repaint();
      }
   }


   @Override
   final protected void paintComponent(final Graphics graphics)
   {
      super.paintComponent(graphics);

      if (! isPaintingRootCell)
      {
         graphics.setColor(UIConstants.ListCellBorderColour);

         final int farBottom = getHeight() - 1;
         final int thumbnailDividerXPosition = MaximumItemThumbnailDimensions + (CellMarginSize * 2);
         graphics.drawLine(thumbnailDividerXPosition, 0, thumbnailDividerXPosition, farBottom);
         graphics.drawLine(0, farBottom, getWidth() - 1, farBottom);

         if (activePaintingItemProfileThumbnail != null)
         {
            final int imageStartX = (thumbnailDividerXPosition - activePaintingItemProfileThumbnail.getWidth()) / 2;
            final int imageStartY = (getHeight() - activePaintingItemProfileThumbnail.getHeight()) / 2;
            graphics.drawImage(activePaintingItemProfileThumbnail, imageStartX, imageStartY, null);
         }
      }
   }
}