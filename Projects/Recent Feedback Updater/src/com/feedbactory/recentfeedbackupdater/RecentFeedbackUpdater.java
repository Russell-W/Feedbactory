
package com.feedbactory.recentfeedbackupdater;


import com.feedbactory.recentfeedbackupdater.core.Console;
import java.io.IOException;


final public class RecentFeedbackUpdater
{
   static final public long VersionID = 1462063315361L;

   /* Execution profile switches, one and only one must be true. Do not change to an Enum. See the notes in the Feedbactory client's constant manager
    * for the explanation and benefits of this approach.
    *
    * I'm using the same paradigm as the client here because the smart compilation will prevent the AWS credentials (embedded in code for development)
    * from appearing in code compiled for production and test. The production and test profiles authenticate via the role that is attached to the Amazon EC2
    * server instance that the code is running on.
    */
   static final public boolean isDevelopmentProfile = true;
   static final public boolean isTestProfile = false;
   static final public boolean isProductionProfile = false;

   /* There is enough of an important distinction between the development & debug states to keep the flags separate;
    * almost always this should be set to false even during development, and it may sometimes be useful in the test environment.
    */
   static final public boolean isDebugMode = false && isDevelopmentProfile;

   // As a safeguard, local output is only available in the development execution profile.
   static final public boolean isLocalOutput = true && isDevelopmentProfile;


   private RecentFeedbackUpdater()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public void main(final String... args) throws IOException
   {
      new Console(new RecentFeedbackUpdater()).start();
   }
}