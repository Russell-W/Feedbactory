
package com.feedbactory.client.core;


import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.logging.Level;
import java.util.logging.Logger;


final public class ConfigurationManager
{
   // Assign to final variables to encourage code inlining for each platform.
   static final public boolean isRunningWindows = System.getProperty("os.name").startsWith("Win");
   static final public boolean isRunningMacOSX = System.getProperty("os.name").startsWith("Mac OS");

   static final private String ApplicationInstallationFolder = ".feedbactory";
   static final private String InstanceLockFilename = "Lock";

   // Will be null if the client is run standalone, without the launcher/updater.
   final private File launcherFile;

   final private Object singleInstanceLock = new Object();
   private FileLock instanceFileLock;


   ConfigurationManager(final File launcherFile)
   {
      this.launcherFile = launcherFile;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private ConfigurationCheckResult handleCheckConfiguration(final long launcherVersion)
   {
      /* The ordering of clauses here is not as crucial as in the launcher which performs some of the same checks and will be the 'front-line' in
       * taking required actions, ie. displaying messages to prompt the user to update Java or performing updates of Feedbactory.
       * Note that the launcher is also able to detect whether a given OS & architecture is supported by SWT and is able to display alert messages
       * accordingly.
       *
       * The unused launcherVersion argument is for detecting an outdated launcher version, and may be used in future releases.
       */
      if (GraphicsEnvironment.isHeadless())
         return ConfigurationCheckResult.HeadlessModeNotSupported;
      else if (! isSupportedJavaVersion())
         return ConfigurationCheckResult.SupersededJavaVersion;
      else if (FeedbactoryClientConstants.EnforceSingleInstance)
         return claimSingleInstanceLock();
      else
         return ConfigurationCheckResult.OK;
   }


   private boolean isSupportedJavaVersion()
   {
      /* Minimum supported Java version for Feedbactory is 1.6.0_10, when both the Nimbus look & feel and frame translucency features appeared.
       * A helpful reference for the Java version convention: "J2SE SDK/JRE Version String Naming Convention", currently at:
       * http://www.oracle.com/technetwork/java/javase/versioning-naming-139433.html
       */
      final String javaVersion = System.getProperty("java.version");

      int identifierEndIndex = javaVersion.indexOf('.');
      if (identifierEndIndex != -1)
      {
         final int generationVersion = Integer.parseInt(javaVersion.substring(0, identifierEndIndex));

         if (generationVersion > 1)
            return true;

         int identifierStartIndex = identifierEndIndex + 1;
         identifierEndIndex = javaVersion.indexOf('.', identifierStartIndex);

         final int majorVersion = Integer.parseInt(javaVersion.substring(identifierStartIndex, identifierEndIndex));

         if (majorVersion > 6)
            return true;
         else if (majorVersion == 6)
         {
            identifierStartIndex = javaVersion.indexOf('_', identifierEndIndex);
            if (identifierStartIndex != -1)
            {
               identifierStartIndex ++;
               identifierEndIndex = javaVersion.indexOf('-', identifierStartIndex);
               if (identifierEndIndex == -1)
                  identifierEndIndex = javaVersion.length();

               final int updateRelease = Integer.parseInt(javaVersion.substring(identifierStartIndex, identifierEndIndex));
               return (updateRelease >= 10);
            }
            else
               return true;
         }
      }

      return false;
   }


   private ConfigurationCheckResult claimSingleInstanceLock()
   {
      try
      {
         final File userFeedbactoryInstallationFolder = getUserFeedbactoryInstallationFolder();
         final File singleInstanceLockFile = getApplicationInstanceLockFile(userFeedbactoryInstallationFolder);

         if (checkUserFeedbactoryInstallationFolder(userFeedbactoryInstallationFolder) &&
             checkApplicationInstanceLockFile(singleInstanceLockFile))
         {
            synchronized (singleInstanceLock)
            {
               final RandomAccessFile file = new RandomAccessFile(singleInstanceLockFile, "rw");
               instanceFileLock = file.getChannel().tryLock();

               if (instanceFileLock != null)
                  return ConfigurationCheckResult.OK;
               else
               {
                  file.close();
                  return ConfigurationCheckResult.InstanceActive;
               }
            }
         }
         else
            return ConfigurationCheckResult.ConfigurationError;
      }
      catch (final IOException ioException)
      {
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Single instance lock claim exception.", ioException);

         return ConfigurationCheckResult.ConfigurationError;
      }
   }


   private File getUserFeedbactoryInstallationFolder()
   {
      /* For a production instance, Feedbactory expects the execution context to be a JAR file sitting within a .feedbactory folder in the user's home folder,
       * so that read & write access is almost guaranteed. The lock file sits under this folder and is used by both the launcher and application to
       * ensure exclusive access.
       *
       * For development, the FeedbactoryClient.EnforceSingleInstance flag can be set to false to skip taking the lock (which would fail if the .feedbactory
       * folder has not yet been created under the user's home folder).
       */
      final File userHomeFolder = new File(System.getProperty("user.home"));
      return new File(userHomeFolder, ApplicationInstallationFolder);
   }


   private File getApplicationInstanceLockFile(final File userFeedbactoryInstallationFolder)
   {
      return new File(userFeedbactoryInstallationFolder, InstanceLockFilename);
   }


   private boolean checkUserFeedbactoryInstallationFolder(final File userFeedbactoryInstallationFolder)
   {
      return (userFeedbactoryInstallationFolder.isDirectory() && userFeedbactoryInstallationFolder.canWrite());
   }


   private boolean checkApplicationInstanceLockFile(final File applicationFile)
   {
      if (applicationFile.exists())
         return (applicationFile.isFile() && applicationFile.canWrite());
      else
         return true;
   }


   private void handleReleaseSingleInstanceLock()
   {
      if (FeedbactoryClientConstants.EnforceSingleInstance)
      {
         try
         {
            synchronized (singleInstanceLock)
            {
               if (instanceFileLock != null)
               {
                  instanceFileLock.release();
                  instanceFileLock.channel().close();
               }
            }
         }
         catch (final IOException ioException)
         {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Single instance lock release exception.", ioException);
         }
      }
   }


   private File handleGetJavaExecutable()
   {
      final File javaHome = new File(System.getProperty("java.home"));
      final File binFolder = new File(javaHome, "bin");

      File javaExecutable = new File(binFolder, "javaw.exe");

      if (javaExecutable.exists())
         return javaExecutable;
      else
      {
         javaExecutable = new File(binFolder, "javaw");
         if (javaExecutable.exists())
            return javaExecutable;
         else
         {
            javaExecutable = new File(binFolder, "java");
            if (javaExecutable.exists())
               return javaExecutable;
         }
      }

      Logger.getLogger(getClass().getName()).severe("Cannot locate Java executable for the active JVM instance.");

      return null;
   }


   private File handleGetLauncherFile()
   {
      if ((launcherFile != null) && launcherFile.isFile() && launcherFile.canRead())
         return launcherFile;

      return null;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final ConfigurationCheckResult checkConfiguration(final long launcherVersion)
   {
      return handleCheckConfiguration(launcherVersion);
   }


   final void releaseSingleInstanceLock()
   {
      handleReleaseSingleInstanceLock();
   }


   final File getJavaExecutable()
   {
      return handleGetJavaExecutable();
   }


   final File getLauncherFile()
   {
      return handleGetLauncherFile();
   }
}