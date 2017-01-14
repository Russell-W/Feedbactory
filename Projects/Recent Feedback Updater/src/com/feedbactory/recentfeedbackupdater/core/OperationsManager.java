
package com.feedbactory.recentfeedbackupdater.core;


import com.amazonaws.services.s3.AmazonS3;
import com.feedbactory.recentfeedbackupdater.RecentFeedbackUpdater;
import static com.feedbactory.recentfeedbackupdater.RecentFeedbackUpdater.isDevelopmentProfile;
import com.feedbactory.recentfeedbackupdater.core.feedback.personal.PersonalFeedbackNetworkGateway;
import com.feedbactory.recentfeedbackupdater.core.log.SMSAlertHandler;
import com.feedbactory.recentfeedbackupdater.core.network.NetworkGateway;
import com.feedbactory.recentfeedbackupdater.core.network.NetworkRequestStatus;
import com.feedbactory.recentfeedbackupdater.core.network.ProcessedRequestResult;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackFeaturedPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.imageio.ImageIO;


final class OperationsManager
{
   static final private Logger ApplicationRootLogger = Logger.getLogger(OperationsManager.class.getPackage().getName());
   static final private String LogFileFormat = "Log/Log.%g.txt";
   static final private SMSAlertHandler SmsLogHandler = initialiseSMSAlertHandler();

   static final private int MaximumRecentFeedbackItems = 40;

   static final private int ImageServerConnectionTimeoutMilliseconds = 12000;

   static final private float MaximumImageWidthHeightRatio = 1.8f;
   static final private float MaximumImageHeightWidthRatio = 1.8f;
   static final private int ScaledImageHeight = 400;
   static final private String ScaledImageFormatName = "jpg";
   static final private Path ScaledImageBasePath = HtmlGenerator.HtmlBasePath.resolve(HtmlGenerator.ImageFolderName);

   // Object prefixes are Amazon S3's version of subfolders.
   static final private String WebsitePrefix = "recentFeedback/photography/";
   static final private String WebsiteRootFileKey = WebsitePrefix + "index.html";
   static final private String WebsiteImagesPrefix = WebsitePrefix + HtmlGenerator.ImageFolderName + '/';

   final private PersonalFeedbackNetworkGateway networkGateway = new PersonalFeedbackNetworkGateway(new NetworkGateway());
   final private RecentFeedbackItemImageManager imageManager = new RecentFeedbackItemImageManager();
   final private AwsGateway awsGateway = new AwsGateway();
   final private HtmlGenerator htmlGenerator = new HtmlGenerator();

   private ScheduledExecutorService executorService;
   private long lastRunTime = FeedbactoryConstants.NoTime;


   static
   {
      initialiseLogger();
      Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler());
   }


   static private void initialiseLogger()
   {
      ApplicationRootLogger.setLevel(Level.ALL);
      ApplicationRootLogger.setUseParentHandlers(false);

      if (isDevelopmentProfile)
      {
         final Handler consoleLogHandler = new ConsoleHandler();
         consoleLogHandler.setLevel(Level.ALL);
         ApplicationRootLogger.addHandler(consoleLogHandler);
      }
      else
      {
         try
         {
            // FileHandler defaults to Level.ALL.
            final Handler fileLogHandler = new FileHandler(LogFileFormat, 1024000, 20);
            fileLogHandler.setFormatter(new SimpleFormatter());
            ApplicationRootLogger.addHandler(fileLogHandler);
         }
         catch (final IOException ioException)
         {
            throw new RuntimeException(ioException);
         }
      }
   }


   static private SMSAlertHandler initialiseSMSAlertHandler()
   {
      final SMSAlertHandler smsAlertHandler;

      if (! isDevelopmentProfile)
      {
         smsAlertHandler = new SMSAlertHandler();
         smsAlertHandler.setLevel(Level.SEVERE);
         ApplicationRootLogger.addHandler(smsAlertHandler);
      }
      else
         smsAlertHandler = null;

      return smsAlertHandler;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler
   {
      @Override
      final public void uncaughtException(final Thread thread, final Throwable throwable)
      {
         ApplicationRootLogger.log(Level.SEVERE, "Uncaught exception", throwable);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void handleScheduleUpdateRecentFeedback(final int taskFrequencyMinutes)
   {
      if (! isStarted())
      {
         if (SmsLogHandler != null)
            SmsLogHandler.resetLastSMSSendTime();

         executorService = Executors.newSingleThreadScheduledExecutor();
         executorService.scheduleWithFixedDelay(getUpdateRecentFeedbackTask(), 0, taskFrequencyMinutes, TimeUnit.MINUTES);
      }
   }


   private Runnable getUpdateRecentFeedbackTask()
   {
      return () ->
      {
         try
         {
            lastRunTime = System.currentTimeMillis();

            final NetworkRequestStatus recentFeedbackRequestStatus = updateRecentFeedback();
            if ((recentFeedbackRequestStatus != NetworkRequestStatus.OK) && (recentFeedbackRequestStatus != NetworkRequestStatus.ServerNotAvailable))
            {
               /* Will SMS an alert when the execution profile is production or test.
                * This is extremely handy as it will send the alert if the Feedbactory server is down.
                */
               Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Network request failed with status: {0}", recentFeedbackRequestStatus);
               shutdownExecutor();
            }
         }
         catch (final Exception anyException)
         {
            // Will SMS an alert when the execution profile is production or test.
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, anyException.getMessage(), anyException);
            shutdownExecutor();
         }
      };
   }


   private NetworkRequestStatus updateRecentFeedback() throws IOException
   {
      final ProcessedRequestResult<List<PersonalFeedbackFeaturedPerson>> hotPhotographyFeedbackSampleResponse = networkGateway.sendGetHotPhotographyFeedbackSample();
      if (hotPhotographyFeedbackSampleResponse.requestStatus == NetworkRequestStatus.OK)
      {
         final AmazonS3 s3Client = awsGateway.createAwsClient();

         final List<RecentFeedbackItemImage> recentFeedbackScaledImages = uploadNewScaledImages(hotPhotographyFeedbackSampleResponse.data, s3Client);

         final File htmlIndexFile = htmlGenerator.generateIndexHtmlFile(recentFeedbackScaledImages);

         if (! RecentFeedbackUpdater.isLocalOutput)
            awsGateway.uploadFile(s3Client, WebsiteRootFileKey, htmlIndexFile);

         /* Remove the images that have not been in the recent feedback set for the last two rounds.
          * This allows for some fluctuation amongst the top rated feedback items between calls, during which the images are cached on the remote server.
          * Feedback item images that have not been in the most recent feedback set will still be marked as active at this point...
          */
         removeInactiveImages(s3Client);

         // ...until they are marked as inactive here. If they are not a part of the next top rated feedback set retrieved, they will then be deleted.
         markInactiveImages(recentFeedbackScaledImages);

         imageManager.saveRegistry();
      }

      return hotPhotographyFeedbackSampleResponse.requestStatus;
   }


   private List<RecentFeedbackItemImage> uploadNewScaledImages(final List<PersonalFeedbackFeaturedPerson> hotPhotographyFeedbackSample,
                                                               final AmazonS3 s3Client) throws IOException
   {
      final List<RecentFeedbackItemImage> recentFeedbackScaledImages = new ArrayList<>(hotPhotographyFeedbackSample.size());

      PersonalFeedbackFeaturedPerson featuredItem;

      int itemIndex = 0;
      while ((itemIndex < hotPhotographyFeedbackSample.size()) && (recentFeedbackScaledImages.size() < MaximumRecentFeedbackItems))
      {
         featuredItem = hotPhotographyFeedbackSample.get(itemIndex);
         if (featuredItem.sortValue == FeedbactoryConstants.EndOfDataLong)
            break;

         processRecentFeedbackItem(featuredItem, s3Client, recentFeedbackScaledImages);

         itemIndex ++;
      }

      return recentFeedbackScaledImages;
   }


   private void processRecentFeedbackItem(final PersonalFeedbackFeaturedPerson featuredItem, final AmazonS3 s3Client, final List<RecentFeedbackItemImage> recentFeedbackScaledImages) throws IOException
   {
      ManagedItemImage managedImage = imageManager.getRegisteredItemImage(featuredItem.personProfile.person);
      if (managedImage == null)
      {
         final BufferedImage largeImage = loadItemLargeImage(featuredItem);

         if (largeImage != null)
         {
            if (hasAcceptableGalleryDimensions(largeImage))
            {
               final BufferedImage scaledImage = ImageUtilities.getProportionalScaledImage(largeImage, Integer.MAX_VALUE, ScaledImageHeight, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
               final String scaledImageFileNameKey = imageManager.generateUniqueImageFileNameKey();

               final File outputFile = getImageOutputFile(scaledImageFileNameKey);
               ImageIO.write(scaledImage, ScaledImageFormatName, outputFile);

               if (! RecentFeedbackUpdater.isLocalOutput)
               {
                  awsGateway.uploadFile(s3Client, getImageAWSKey(outputFile.getName()), outputFile);
                  outputFile.delete();
               }

               managedImage = imageManager.addItemImage(featuredItem.personProfile.person, scaledImageFileNameKey, scaledImage.getWidth(), scaledImage.getHeight(), true);
               recentFeedbackScaledImages.add(new RecentFeedbackItemImage(featuredItem, managedImage));
            }
            else
            {
               final String message = "Recent feedback image dimensions are outside of the acceptable gallery range: {0}, width: {1}, height: {2}";
               Logger.getLogger(getClass().getName()).log(Level.WARNING, message, new Object[] {featuredItem.personProfile.person, largeImage.getWidth(), largeImage.getHeight()});
            }
         }
         else
         {
            /* This may occur not only due to a timeout or other connection IOException, but if the ImageIO.read call itself returns null for
             * other reasons, eg. http instead of https used for some image providers.
             */
            final String message = "Recent feedback image read from connection is null: {0}, large image URL: {1}";
            Logger.getLogger(getClass().getName()).log(Level.WARNING, message, new Object[] {featuredItem.personProfile.person, featuredItem.personProfile.getLargeImageURL()});
         }
      }
      else
      {
         // If necessary, mark the image as active to reinstate it.
         if (! managedImage.isActive)
            managedImage = imageManager.addItemImage(featuredItem.personProfile.person, managedImage.imageFileNameKey, managedImage.imageWidth, managedImage.imageHeight, true);

         recentFeedbackScaledImages.add(new RecentFeedbackItemImage(featuredItem, managedImage));
      }
   }


   private BufferedImage loadItemLargeImage(final PersonalFeedbackFeaturedPerson featuredItem)
   {
      InputStream connectionInputStream = null;

      try
      {
         final URL largeImageURL = new URL(featuredItem.personProfile.getLargeImageURL());
         final URLConnection urlConnection = largeImageURL.openConnection();
         urlConnection.setConnectTimeout(ImageServerConnectionTimeoutMilliseconds);
         urlConnection.setReadTimeout(ImageServerConnectionTimeoutMilliseconds);
         connectionInputStream = urlConnection.getInputStream();

         return ImageIO.read(connectionInputStream);
      }
      catch (final IOException ioException)
      {
         // May be SocketTimeoutException, ConnectException, or some other IOException.
         final String message = "Image retrieval connection failed for recent feedback item: " + featuredItem.personProfile.person;
         Logger.getLogger(getClass().getName()).log(Level.WARNING, message, ioException);
      }
      finally
      {
         try
         {
            if (connectionInputStream != null)
               connectionInputStream.close();
         }
         catch (final IOException ioException)
         {
            final String message = "Error while closing recent feedback image connection for item: " + featuredItem.personProfile.person;
            Logger.getLogger(getClass().getName()).log(Level.WARNING, message, ioException);
         }
      }

      return null;
   }


   private boolean hasAcceptableGalleryDimensions(final BufferedImage image)
   {
      return ((image.getWidth() > 0) &&
              (image.getHeight() >= ScaledImageHeight) &&
              ((((float) image.getWidth()) / image.getHeight()) <= MaximumImageWidthHeightRatio) &&
              ((((float) image.getHeight()) / image.getWidth()) <= MaximumImageHeightWidthRatio));
   }


   private File getImageOutputFile(final String imageFileNameKey)
   {
      final String fileName = getImageOutputFileName(imageFileNameKey);
      return ScaledImageBasePath.resolve(fileName).toFile();
   }


   private String getImageOutputFileName(final String imageFileNameKey)
   {
      return imageFileNameKey + '.' + ScaledImageFormatName;
   }


   private String getImageAWSKey(final String imageFileName)
   {
      return WebsiteImagesPrefix + imageFileName;
   }


   private void removeInactiveImages(final AmazonS3 s3Client)
   {
      final List<ManagedItemImage> inactiveItemImages = imageManager.removeInactiveItems();

      for (final ManagedItemImage itemImage : inactiveItemImages)
      {
         if (RecentFeedbackUpdater.isLocalOutput)
            getImageOutputFile(itemImage.imageFileNameKey).delete();
         else
         {
            final String fileName = getImageOutputFileName(itemImage.imageFileNameKey);
            awsGateway.deleteFile(s3Client, getImageAWSKey(fileName));
         }
      }
   }


   private void markInactiveImages(final List<RecentFeedbackItemImage> recentFeedbackItemImages)
   {
      final Set<PersonalFeedbackPerson> recentFeedbackItems = new HashSet<>();
      for (final RecentFeedbackItemImage recentFeedbackItem : recentFeedbackItemImages)
         recentFeedbackItems.add(recentFeedbackItem.featuredItem.personProfile.person);

      final Map<PersonalFeedbackPerson, ManagedItemImage> remainingItems = imageManager.getItems();
      ManagedItemImage itemImage;
      for (final Entry<PersonalFeedbackPerson, ManagedItemImage> itemImageEntry : remainingItems.entrySet())
      {
         itemImage = itemImageEntry.getValue();
         if (! recentFeedbackItems.contains(itemImageEntry.getKey()))
            imageManager.addItemImage(itemImageEntry.getKey(), itemImage.imageFileNameKey, itemImage.imageWidth, itemImage.imageHeight, false);
      }
   }


   private void handleShutdownRecentFeedbackUpdate(final int timeoutSeconds) throws InterruptedException
   {
      if (isStarted())
         shutdownExecutor(timeoutSeconds);
   }


   private void shutdownExecutor(final int timeoutSeconds) throws InterruptedException
   {
      executorService.shutdown();
      executorService.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
      executorService = null;
   }


   private void shutdownExecutor()
   {
      executorService.shutdown();
      executorService = null;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final boolean isStarted()
   {
      return (executorService != null);
   }


   final long getLastRunTime()
   {
      return lastRunTime;
   }


   final void scheduleUpdateRecentFeedback(final int taskFrequencyMinutes)
   {
      handleScheduleUpdateRecentFeedback(taskFrequencyMinutes);
   }


   final void shutdownRecentFeedbackUpdate(final int timeoutSeconds) throws InterruptedException
   {
      handleShutdownRecentFeedbackUpdate(timeoutSeconds);
   }
}