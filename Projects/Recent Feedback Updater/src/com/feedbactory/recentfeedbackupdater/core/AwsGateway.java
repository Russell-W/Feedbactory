
package com.feedbactory.recentfeedbackupdater.core;


import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.feedbactory.recentfeedbackupdater.RecentFeedbackUpdater;
import java.io.File;


final class AwsGateway
{
   static final private Region AWSRegion = Region.getRegion(Regions.US_WEST_1);
   static final private String BucketName;

   static
   {
      if (RecentFeedbackUpdater.isDevelopmentProfile)
         BucketName = "<insert AWS S3 development bucket>";
      else if (RecentFeedbackUpdater.isTestProfile)
         BucketName = "<insert AWS S3 test bucket>";
      else if (RecentFeedbackUpdater.isProductionProfile)
         BucketName = "<insert AWS S3 production bucket>";
      else
         throw new AssertionError("Unhandled or misconfigured execution profile.");

      // Hostname to IP address lookup cache time-to-live property set as per advice here: http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/java-dg-jvm-ttl.html
      java.security.Security.setProperty("networkaddress.cache.ttl", "60");
   }


   private AmazonS3 handleCreateAWSClient()
   {
      if (RecentFeedbackUpdater.isDevelopmentProfile)
      {
         final AmazonS3 s3Client = new AmazonS3Client(new BasicAWSCredentials("<insert AWS access key>", "<insert AWS secret key>"));
         s3Client.setRegion(AWSRegion);
         return s3Client;
      }
      else
         return AWSRegion.createClient(AmazonS3Client.class, new InstanceProfileCredentialsProvider(), null);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final AmazonS3 createAwsClient()
   {
      return handleCreateAWSClient();
   }


   final void uploadFile(final AmazonS3 s3Client, final String key, final File file)
   {
      s3Client.putObject(BucketName, key, file);
   }


   final void deleteFile(final AmazonS3 s3Client, final String key)
   {
      s3Client.deleteObject(BucketName, key);
   }
}