
package com.feedbactory.recentfeedbackupdater.core;


import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.NumberFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


final class HtmlGenerator
{
   static final String ImageFolderName = "img";
   static final Path HtmlBasePath = Paths.get("Html");

   static final private Path IndexHtmlFilePreGallerySectionPath = HtmlBasePath.resolve("index pre gallery.html");
   static final private Path IndexHtmlFilePostGallerySectionPath = HtmlBasePath.resolve("index post gallery.html");
   static final private Path IndexHtmlFilePath = HtmlBasePath.resolve("index.html");

   static final private String TrafficCopURLPrefix = "../../utility/trafficCop.html?url=";


   /****************************************************************************
    *
    ***************************************************************************/


   private File handleGenerateIndexHtmlFile(final List<RecentFeedbackItemImage> recentFeedbackScaledImages) throws IOException
   {
      try
      (
         final FileChannel indexHtmlFileChannel = FileChannel.open(IndexHtmlFilePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      )
      {
         writeIndexHtmlFileSection(IndexHtmlFilePreGallerySectionPath, indexHtmlFileChannel);
         writeIndexHtmlFileImageGallerySection(recentFeedbackScaledImages, indexHtmlFileChannel);
         writeIndexHtmlFileSection(IndexHtmlFilePostGallerySectionPath, indexHtmlFileChannel);
      }

      return IndexHtmlFilePath.toFile();
   }


   private void writeIndexHtmlFileSection(final Path indexHtmlFileSection, final FileChannel indexHtmlFileChannel) throws IOException
   {
      try
      (
         final FileChannel indexHtmlSectionFileChannel = FileChannel.open(indexHtmlFileSection, StandardOpenOption.READ);
      )
      {
         long inputFileSize = indexHtmlSectionFileChannel.size();
         long filePosition = 0L;

         while (filePosition < inputFileSize)
            filePosition += indexHtmlSectionFileChannel.transferTo(filePosition, 8192, indexHtmlFileChannel);
      }
   }


   private void writeIndexHtmlFileImageGallerySection(final List<RecentFeedbackItemImage> recentFeedbackScaledImages, final FileChannel indexHtmlFileChannel) throws IOException
   {
      for (final RecentFeedbackItemImage itemImage : recentFeedbackScaledImages)
         writeFileChannelString(indexHtmlFileChannel, getFormattedRecentFeedbackItemHtml(itemImage));
   }


   private String getFormattedRecentFeedbackItemHtml(final RecentFeedbackItemImage itemImage)
   {
      final StringBuilder galleryBuilder = new StringBuilder(2000);

      final String trafficCopImageURL = getTrafficCopURL(itemImage.featuredItem.personProfile.getURL());
      final String trafficCopItemBrowseURL = getTrafficCopURL(itemImage.featuredItem.personProfile.getWebsite().getItemBrowseURL());
      final NumberFormat numberFormat = NumberFormat.getNumberInstance();
      numberFormat.setMinimumFractionDigits(1);

      galleryBuilder.append("\n                  <div class=\"photoContainer\" data-image-source=\"");
      galleryBuilder.append(ImageFolderName);
      galleryBuilder.append("/");
      galleryBuilder.append(itemImage.itemImage.imageFileNameKey);
      galleryBuilder.append(".jpg\" data-original-width=\"");
      galleryBuilder.append(itemImage.itemImage.imageWidth);
      galleryBuilder.append("\">\n");

      galleryBuilder.append("                     <a class=\"photoLink\" href=\"");
      galleryBuilder.append(itemImage.featuredItem.personProfile.getURL());
      galleryBuilder.append("\"></a>\n");

      galleryBuilder.append("                     <div class=\"photoInformation\">\n");

      galleryBuilder.append("                        <div class=\"photoTitle\">\n");
      galleryBuilder.append("                           <a href=\"");
      galleryBuilder.append(trafficCopImageURL);
      galleryBuilder.append("\">");
      galleryBuilder.append(getSafeEncodedHTMLElementContent(itemImage.featuredItem.personProfile.getShortName()));
      galleryBuilder.append("</a>\n");
      galleryBuilder.append("                        </div>\n");

      galleryBuilder.append("                        <div class=\"photoOrigin\">\n");
      galleryBuilder.append("                           <a href=\"");
      galleryBuilder.append(trafficCopItemBrowseURL);
      galleryBuilder.append("\">");
      galleryBuilder.append(itemImage.featuredItem.personProfile.person.getWebsite().getName());
      galleryBuilder.append("</a>\n");
      galleryBuilder.append("                        </div>\n");

      galleryBuilder.append("                        <div class=\"photoRating\">\n");
      galleryBuilder.append("                           <a href=\"");
      galleryBuilder.append(trafficCopImageURL);
      galleryBuilder.append("\">");
      galleryBuilder.append(numberFormat.format(itemImage.featuredItem.feedbackSummary.averageRating / 10f));
      galleryBuilder.append("</a>\n");
      galleryBuilder.append("                        </div>\n");

      galleryBuilder.append("                     </div>\n");
      galleryBuilder.append("                  </div>\n");

      return galleryBuilder.toString();
   }


   private String getTrafficCopURL(final String targetURL)
   {
      try
      {
         return (TrafficCopURLPrefix + URLEncoder.encode(targetURL, StandardCharsets.UTF_8.name()));
      }
      catch (final UnsupportedEncodingException unsupportedEncodingException)
      {
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Traffic cop URL encoding error", unsupportedEncodingException);
         return targetURL;
      }
   }


   private String getSafeEncodedHTMLElementContent(final String elementContent)
   {
      final StringBuilder safeElementContent = new StringBuilder(elementContent.length());
      char character;

      for (int characterIndex = 0; characterIndex < elementContent.length(); characterIndex ++)
      {
         character = elementContent.charAt(characterIndex);
         if ((character == '&') || (character == '<') || (character == '>') || (character == '\"') || (character == '\'') || (character == '/'))
         {
            safeElementContent.append("&#");
            safeElementContent.append((int) character);
            safeElementContent.append(';');
         }
         else
            safeElementContent.append(character);
      }

      return safeElementContent.toString();
   }


   private void writeFileChannelString(final FileChannel indexHtmlFileChannel, final String string) throws IOException
   {
      byte[] encodedBytes = string.getBytes(StandardCharsets.UTF_8);
      ByteBuffer wrappedEncodedBytes = ByteBuffer.wrap(encodedBytes);

      while (wrappedEncodedBytes.hasRemaining())
         indexHtmlFileChannel.write(wrappedEncodedBytes);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final File generateIndexHtmlFile(final List<RecentFeedbackItemImage> recentFeedbackScaledImages) throws IOException
   {
      return handleGenerateIndexHtmlFile(recentFeedbackScaledImages);
   }
}