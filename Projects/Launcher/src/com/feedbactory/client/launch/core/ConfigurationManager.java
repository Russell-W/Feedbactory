/* Memos:
 * - This class has a mechanism for uninstalling application files for the currently executing user (may be Admin), but see the comment in the
 *   main launcher class regarding the limitations of this for a global uninstall.
 *
 * - Create digest of all files & folders created.
 * - MD5 hash/checksum for files to ensure that the same ones are being deleted? Or timestamp?
 * - When removing files, progressively backtrack through parent folders, checking whether they are empty; if so, remove it.
 * - DataOutputStream for writing the file.
 *
 * Install process:
 * - Obtain file lock.
 *   - Prompt user for retry if unable to obtain lock.
 * 
 * - Uninstall previous files:
 *   - Read existing install manifest file into a Manifest object.
 *   - Loop over object, delete files where the checksum matches, remove entry from Manifest object.
 *   - Manifest object is left with only files that were modified externally.
 *
 * - Download zip file.
 *   - Retry on failure.
 *
 * - Build install manifest from zip file entries.
 *
 * - Loop over install manifest, check for existing files; alert user with prompt to overwrite.
 *   - Callback needs to be able to resume state.
 */

package com.feedbactory.client.launch.core;


import java.awt.GraphicsEnvironment;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


final public class ConfigurationManager
{
   // Assign to final variables to encourage code inlining for each platform.
   static final public boolean isRunningWindows = System.getProperty("os.name").startsWith("Win");
   static final public boolean isRunningMacOSX = System.getProperty("os.name").startsWith("Mac OS");

   static final private Set<String> AvailableSWTPlatforms;

   static final private String InstanceLockFilename = "Lock";

   static final private String ApplicationDownloadsHost;
   static final private String ApplicationDownloadsFolder = "active";
   static final private String ApplicationArchiveFilename = "Feedbactory.zip";
   static final private String ApplicationInstallManifestFilename = "Manifest";

   static final private String ApplicationInstallationFolder = ".feedbactory";
   static final private String ApplicationStartFilename = "Feedbactory.jar";

   static final private int DownloadConnectionTimeoutMilliseconds = 5000;

   static
   {
      if (LauncherConstants.IsDevelopmentProfile || LauncherConstants.IsTestProfile)
         ApplicationDownloadsHost = "<insert AWS S3 development and test host>";
      else if (LauncherConstants.IsProductionProfile)
         ApplicationDownloadsHost = "<insert AWS S3 production host>";
      else
         throw new AssertionError("Unknown or misconfigured execution profile.");

      final Set<String> swtPlatformsBuilder = new HashSet<String>(13);
      swtPlatformsBuilder.add("win32-x86");
      swtPlatformsBuilder.add("win32-x86_64");
      swtPlatformsBuilder.add("linux-x86");
      swtPlatformsBuilder.add("linux-x86_64");
      swtPlatformsBuilder.add("linux-ppc");
      swtPlatformsBuilder.add("linux-ppc64");
      swtPlatformsBuilder.add("linux-ppc64le");
      swtPlatformsBuilder.add("solaris-sparc");
      swtPlatformsBuilder.add("solaris-x86");
      swtPlatformsBuilder.add("hpux-ia64");
      swtPlatformsBuilder.add("aix-ppc");
      swtPlatformsBuilder.add("aix-ppc64");
      swtPlatformsBuilder.add("macosx-x86_64");

      AvailableSWTPlatforms = Collections.unmodifiableSet(swtPlatformsBuilder);
   }


   ConfigurationManager()
   {
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class FolderTree
   {
      final private Map<File, FolderTree> children = new HashMap<File, FolderTree>(5);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private ConfigurationCheckResult handleCheckConfiguration()
   {
      if (GraphicsEnvironment.isHeadless())
         return ConfigurationCheckResult.HeadlessModeNotSupported;
      else if (! isSWTPlatformSupported(getTargetSWTPlatform()))
      {
         /* Check that the OS & architecture combination is supported by SWT at all, before checking for a superseded Java version.
          * It's also possible that a library file for the wrong JVM architecture is currently installed, eg. if the user switches from a 32-bit to a 64-bit JVM.
          * This will be detected by the application rather than here via the SWT.isLoadable() method, leading to this launcher being invoked with a
          * command-line switch to force an update.
          * I could pre-emptively check for the version mismatch here, possibly by embedding attributes in the JAR manifest, but it would be an unnecessary double
          * check when the main application already takes care of it.
          */
         return ConfigurationCheckResult.UnsupportedPlatform;
      }
      else if (! isSupportedJavaVersion())
         return ConfigurationCheckResult.SupersededJavaVersion;
      else
         return checkApplicationFiles();
   }


   private String getTargetSWTPlatform()
   {
      return getCanonicalOSName() + '-' + getCanonicalOSArchitecture();
   }


   private String getCanonicalOSName()
   {
      final String rawOSName = System.getProperty("os.name").toLowerCase(Locale.US);

      // Logic derived from SWT's Library.java class.
      if (rawOSName.startsWith("win"))
         return "win32";
      else if (rawOSName.equals("mac os x"))
         return "macosx";
      else if (rawOSName.equals("linux"))
         return "linux";
      else if (rawOSName.equals("sunos") || rawOSName.equals("solaris"))
         return "solaris";
      else if (rawOSName.equals("hp-ux"))
         return "hpux";
      else if (rawOSName.equals("aix"))
         return "aix";
      else
         return rawOSName;
   }


   private String getCanonicalOSArchitecture()
   {
      final String rawOSArchitecture = System.getProperty("os.arch").toLowerCase(Locale.US);

      /* Logic derived from SWT's Library.java class.
       * 
       * SWT has added support for linux-ppc64le, however up until quite recently the OpenJDK was apparently still reporting
       * os.arch as ppc64 instead of ppc64le. This may cause problems and the wrong SWT bundle to be downloaded..?
       */
      if (rawOSArchitecture.equals("amd64"))
         return "x86_64";
      else if (rawOSArchitecture.equals("i386") || rawOSArchitecture.equals("i686"))
         return "x86";
      else if (rawOSArchitecture.equals("ia64w"))
         return "ia64";
      else
         return rawOSArchitecture;
   }


   private boolean isSWTPlatformSupported(final String targetSWTPlatform)
   {
      return AvailableSWTPlatforms.contains(targetSWTPlatform);
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


   private ConfigurationCheckResult checkApplicationFiles()
   {
      final File userFeedbactoryInstallationFolder = getUserFeedbactoryInstallationFolder();
      final File manifestFile = getManifestFile(userFeedbactoryInstallationFolder);
      final File lockFile = getApplicationInstanceLockFile(userFeedbactoryInstallationFolder);

      // Checks succeed if the files/folders exist and are writable, or don't yet exist but can be created.
      if (checkUserFeedbactoryInstallationFolder(userFeedbactoryInstallationFolder) &&
          checkApplicationFile(manifestFile) && checkApplicationFile(lockFile))
      {
         final File applicationStartFile = getApplicationStartFile(userFeedbactoryInstallationFolder);
         if (applicationStartFile.exists())
         {
            /* For simply running the app, I could get away with a canRead() check on the startup file,
             * but a genuine configuration check should also make sure that the file is updatable.
             */
            if (applicationStartFile.isFile() && applicationStartFile.canWrite())
               return ConfigurationCheckResult.NoUpdateRequired;
            else
               return ConfigurationCheckResult.ConfigurationError;
         }
         else
            return ConfigurationCheckResult.UpdateRequired;
      }

      return ConfigurationCheckResult.ConfigurationError;
   }


   private File getUserFeedbactoryInstallationFolder()
   {
      final File userHomeFolder = new File(System.getProperty("user.home"));
      return new File(userHomeFolder, ApplicationInstallationFolder);
   }


   private boolean checkUserFeedbactoryInstallationFolder(final File userFeedbactoryInstallationFolder)
   {
      final File checkFolder = userFeedbactoryInstallationFolder.exists() ? userFeedbactoryInstallationFolder : userFeedbactoryInstallationFolder.getParentFile();
      return (checkFolder.isDirectory() && checkFolder.canWrite());
   }


   private File getApplicationInstanceLockFile(final File userFeedbactoryInstallationFolder)
   {
      return new File(userFeedbactoryInstallationFolder, InstanceLockFilename);
   }


   private File getManifestFile(final File userFeedbactoryInstallationFolder)
   {
      return new File(userFeedbactoryInstallationFolder, ApplicationInstallManifestFilename);
   }


   private boolean checkApplicationFile(final File applicationFile)
   {
      if (applicationFile.exists())
         return (applicationFile.isFile() && applicationFile.canWrite());
      else
         return true;
   }


   private File getApplicationStartFile(final File userFeedbactoryInstallationFolder)
   {
      return new File(userFeedbactoryInstallationFolder, ApplicationStartFilename);
   }


   /****************************************************************************
    *
    ***************************************************************************/


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

      return null;
   }


   private File handleGetApplicationFile()
   {
      final File applicationStartFile = getApplicationStartFile(getUserFeedbactoryInstallationFolder());
      if (applicationStartFile.isFile() && applicationStartFile.canWrite())
         return applicationStartFile;
      else
         return null;
   }


   private File handleGetLauncherFile()
   {
      try
      {
         // Return the current execution context, which is expected to be a jar file and not a folder.
         final URI executionContextURI = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
         final File executionContextFile = new File(executionContextURI);
         if (executionContextFile.isFile())
            return executionContextFile;
      }
      catch (final Exception anyException)
      {
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Get launcher file exception", anyException);
      }

      return null;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private ApplicationUpdateResult handleUpdateApplication() throws IOException
   {
      ApplicationUpdateResult updateResult = null;
      FileLock fileLock = null;

      try
      {
         final File userFeedbactoryInstallationFolder = checkCreateUserFeedbactoryInstallationFolder();

         fileLock = takeApplicationFileLock(userFeedbactoryInstallationFolder);

         if (fileLock != null)
         {
            /* Don't check the result of the uninstall process, and definitely don't allow a failure during that process to impede an attempt at a new installation.
             * It can be considered a best effort to clean up any previous installation, not a showstopper for installing a new version.
             */
            tryDeleteManifestListedFiles(userFeedbactoryInstallationFolder);

            updateResult = downloadApplicationArchive(userFeedbactoryInstallationFolder);

            if (updateResult == ApplicationUpdateResult.Success)
               updateResult = extractApplicationArchive(userFeedbactoryInstallationFolder);
         }
         else
            updateResult = ApplicationUpdateResult.InstanceActive;
      }
      finally
      {
         if (fileLock != null)
            releaseApplicationFileLock(fileLock);
      }

      return updateResult;
   }


   private File checkCreateUserFeedbactoryInstallationFolder()
   {
      final File feedbactoryInstallationFolder = getUserFeedbactoryInstallationFolder();

      if (! feedbactoryInstallationFolder.exists())
         feedbactoryInstallationFolder.mkdir();

      return feedbactoryInstallationFolder;
   }


   private FileLock takeApplicationFileLock(final File userFeedbactoryInstallationFolder) throws IOException
   {
      FileChannel lockFileChannel = null;
      FileLock fileLock = null;

      try
      {
         final File applicationLockFile = getApplicationInstanceLockFile(userFeedbactoryInstallationFolder);
         lockFileChannel = new RandomAccessFile(applicationLockFile, "rw").getChannel();
         fileLock = lockFileChannel.tryLock();

         return fileLock;
      }
      finally
      {
         if ((fileLock == null) && (lockFileChannel != null))
            lockFileChannel.close();
      }
   }


   private void releaseApplicationFileLock(final FileLock fileLock) throws IOException
   {
      fileLock.release();
      fileLock.channel().close();
   }


   private ApplicationUninstallResult tryDeleteManifestListedFiles(final File userFeedbactoryInstallationFolder)
   {
      try
      {
         return deleteManifestListedFiles(userFeedbactoryInstallationFolder);
      }
      catch (final Exception anyException)
      {
         Logger.getLogger(getClass().getName()).log(Level.WARNING, "Delete manifest listed files exception", anyException);
         return ApplicationUninstallResult.UninstallError;
      }
   }


   private ApplicationUninstallResult deleteManifestListedFiles(final File userFeedbactoryInstallationFolder) throws IOException
   {
      final File manifestFile = getManifestFile(userFeedbactoryInstallationFolder);
      ApplicationUninstallResult uninstallResult = ApplicationUninstallResult.Success;
      DataInputStream dataInputStream = null;

      try
      {
         if (! manifestFile.exists())
            return ApplicationUninstallResult.NotInstalled;

         dataInputStream = new DataInputStream(new FileInputStream(manifestFile));

         String applicationFileName;
         long installedFileSize;
         long installedModificationTime;
         File applicationFile;

         final FolderTree applicationFolders = new FolderTree();

         while (dataInputStream.readBoolean())
         {
            applicationFileName = dataInputStream.readUTF();
            installedFileSize = dataInputStream.readLong();
            installedModificationTime = dataInputStream.readLong();

            applicationFile = new File(applicationFileName);

            if (isRelativeFile(applicationFile))
            {
               applicationFile = new File(userFeedbactoryInstallationFolder, applicationFileName);

               if (applicationFile.exists())
               {
                  if ((applicationFile.length() == installedFileSize) && (applicationFile.lastModified() == installedModificationTime))
                  {
                     if (applicationFile.delete())
                        addToUninstallFolders(applicationFolders, getFolderHierarchy(userFeedbactoryInstallationFolder, applicationFile));
                     else
                        uninstallResult = ApplicationUninstallResult.IncompleteUninstall;
                  }
                  else
                  {
                     Logger.getLogger(getClass().getName()).log(Level.WARNING, "Application file has been modified: {0}", applicationFile);
                     uninstallResult = ApplicationUninstallResult.IncompleteUninstall;
                     // And don't attempt to remove the containing folder if the file has been modified (and therefore won't be deleted).
                  }
               }
               else
               {
                  Logger.getLogger(getClass().getName()).log(Level.WARNING, "Application file does not exist: {0}", applicationFile);

                  // If the file has already been removed outside of Feedbactory, the uninstaller can still attempt to remove its folder if it's empty.
                  addToUninstallFolders(applicationFolders, getFolderHierarchy(userFeedbactoryInstallationFolder, applicationFile));
               }
            }
            else
               Logger.getLogger(getClass().getName()).log(Level.WARNING, "Erroneous application file path: {0}", applicationFile);
         }

         // Delete all of the (empty) installation folder paths once the application files have been removed.
         if (! deleteEmptyApplicationFolders(null, applicationFolders))
            uninstallResult = ApplicationUninstallResult.IncompleteUninstall;
      }
      finally
      {
         if (dataInputStream != null)
         {
            dataInputStream.close();

            // Delete the manifest file itself.
            if (! manifestFile.delete())
            {
               Logger.getLogger(getClass().getName()).log(Level.WARNING, "Could not delete manifest file");
               uninstallResult = ApplicationUninstallResult.IncompleteUninstall;
            }
         }
      }

      return uninstallResult;
   }


   private boolean isRelativeFile(final File file)
   {
      return ((! file.isAbsolute()) && (! file.getPath().contains("..")));
   }


   private List<File> getFolderHierarchy(final File userFeedbactoryInstallationFolder, final File applicationFile)
   {
      /* Create an ordered list of a single application file's folders, beginning at the root installation folder.
       * Storing string objects in the list rather than files could work in this collection, however the File objects eventually
       * need to be used anyway to ensure the correct filename case handling on different OSes: see the addToUninstallFolders() method.
       */
      final List<File> folderHierarchy = new LinkedList<File>();
      File parentFolder = applicationFile.getParentFile();

      while (! userFeedbactoryInstallationFolder.equals(parentFolder))
      {
         folderHierarchy.add(parentFolder);
         parentFolder = parentFolder.getParentFile();
      }

      Collections.reverse(folderHierarchy);

      return folderHierarchy;
   }


   private void addToUninstallFolders(final FolderTree applicationFolders, final List<File> folderHierarchy)
   {
      FolderTree folderNode = applicationFolders;
      FolderTree childFolderNode;

      // Progressively add the folder hierarchy to the existing application folder hierarchy.
      for (final File folder : folderHierarchy)
      {
         /* The correct filename case handling for the local OS is automatically taken care of here by the File object in
          * calculating the hash code and determining equality.
          */
         childFolderNode = folderNode.children.get(folder);

         if (childFolderNode == null)
         {
            childFolderNode = new FolderTree();
            folderNode.children.put(folder, childFolderNode);
         }

         folderNode = childFolderNode;
      }
   }


   private boolean deleteEmptyApplicationFolders(final File folder, final FolderTree subfolderTree)
   {
      boolean deletionSuccessful = true;

      /* Recursively delete the application folders, starting with the tail end of each path to maximise the chance of success.
       * This tree approach allows for easy recursion through the collection while ensuring that there's a deterministic
       * point at which each tail end folder should be deleted, ie. no looping over a list of paths, some of which may overlap: if
       * a non-empty folder can't be deleted is it because there's a mystery file there or is it because there is an overlapping
       * application file path that is still yet to be deleted? In the same way, the tree approach also prevents double-processing
       * of listing files & subfolders within each folder, since each folder is only processed once, immediately after all of its subfolders
       * have been dealt with. Because of this, it's also possible to return a value at the end indicating whether or not the entire
       * folder deletion could be completed.
       */
      for (final Entry<File, FolderTree> folderEntry : subfolderTree.children.entrySet())
      {
         if (folderEntry.getKey().exists() && (! deleteEmptyApplicationFolders(folderEntry.getKey(), folderEntry.getValue())))
            deletionSuccessful = false;
      }

      // Don't attempt to delete the current folder if at least one of the subfolders could not be deleted.
      if ((folder != null) && deletionSuccessful)
      {
         final String[] childFiles = folder.list();

         if (childFiles == null)
         {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Could not read files of subfolder: {0}", folder);
            deletionSuccessful = false;
         }
         else if (childFiles.length == 0)
         {
            if (! folder.delete())
            {
               Logger.getLogger(getClass().getName()).log(Level.WARNING, "Could not delete subfolder: {0}", folder);
               deletionSuccessful = false;
            }
         }
         else
         {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Could not delete non-empty subfolder: {0}", folder);
            deletionSuccessful = false;
         }
      }

      return deletionSuccessful;
   }


   private ApplicationUpdateResult downloadApplicationArchive(final File userFeedbactoryInstallationFolder) throws IOException
   {
      final File archiveDestinationFile = new File(userFeedbactoryInstallationFolder, ApplicationArchiveFilename);

      final String targetSWTPlatform = getTargetSWTPlatform();

      // This method must only be called if the running Java platform is known to be supported.
      if (! isSWTPlatformSupported(targetSWTPlatform))
         throw new IllegalStateException();

      ApplicationUpdateResult updateResult = null;
      FileChannel applicationFileChannel = null;

      try
      {
         final String platformDownloadFile = getPlatformDownloadPath(targetSWTPlatform);
         final URL launcherURL = new URL("http", ApplicationDownloadsHost, platformDownloadFile);
         final HttpURLConnection connection = (HttpURLConnection) launcherURL.openConnection();
         connection.setConnectTimeout(DownloadConnectionTimeoutMilliseconds);
         connection.setReadTimeout(DownloadConnectionTimeoutMilliseconds);

         final int responseCode = connection.getResponseCode();

         if (responseCode == HttpURLConnection.HTTP_OK)
         {
            applicationFileChannel = new FileOutputStream(archiveDestinationFile).getChannel();

            final ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
            final ReadableByteChannel byteChannel = Channels.newChannel(connection.getInputStream());

            while (byteChannel.read(buffer) != -1)
            {
               buffer.flip();

               while (buffer.hasRemaining())
                  applicationFileChannel.write(buffer);

               buffer.clear();
            }

            byteChannel.close();

            updateResult = ApplicationUpdateResult.Success;
         }
         else
            updateResult = ApplicationUpdateResult.ServerUnavailable;
      }
      catch (final SocketTimeoutException socketTimeoutException)
      {
         updateResult = ApplicationUpdateResult.ServerUnavailable;
      }
      finally
      {
         if (applicationFileChannel != null)
            applicationFileChannel.close();
      }

      return updateResult;
   }


   private String getPlatformDownloadPath(final String targetSWTPlatform)
   {
      final StringBuffer platformFile = new StringBuffer(100);
      platformFile.append('/');
      platformFile.append(ApplicationDownloadsFolder);
      platformFile.append('/');
      platformFile.append(targetSWTPlatform);
      platformFile.append('/');
      platformFile.append(ApplicationArchiveFilename);

      return platformFile.toString();
   }


   private ApplicationUpdateResult extractApplicationArchive(final File userFeedbactoryInstallationFolder) throws IOException
   {
      File downloadedArchiveFile = new File(userFeedbactoryInstallationFolder, ApplicationArchiveFilename);
      ZipFile zipFile = null;
      InputStream zipEntryInputStream = null;
      FileOutputStream applicationFileOutputStream = null;
      DataOutputStream manifestStream = null;

      try
      {
         zipFile = new ZipFile(downloadedArchiveFile);

         final Enumeration<? extends ZipEntry> zipEntryEnumeration = zipFile.entries();
         ZipEntry zipEntry;
         File zipEntryFile;
         final byte[] buffer = new byte[8192];
         int bytesRead;

         final File manifestFile = getManifestFile(userFeedbactoryInstallationFolder);
         manifestStream = new DataOutputStream(new FileOutputStream(manifestFile));

         while (zipEntryEnumeration.hasMoreElements())
         {
            zipEntry = zipEntryEnumeration.nextElement();

            /* Disallow (hacked?) zip entries that might be absolute or contain parent folder references, potentially overwriting files outside of the extraction subfolder.
             * I confirmed with a hex editor that this is possible without the check here in place. FYI, the built-in zip handler in Windows quietly
             * suppresses such dangerous entries.
             * This is more about following good practice than genuine paranoia since I'm providing the Feedbactory zip files, handy if I use this code elsewhere though.
             */
            if (! isRelativeFile(new File(zipEntry.getName())))
               continue;

            zipEntryFile = new File(userFeedbactoryInstallationFolder, zipEntry.getName());

            if (zipEntry.isDirectory())
               zipEntryFile.mkdirs();
            else
            {
               zipEntryFile.getParentFile().mkdirs();

               zipEntryInputStream = zipFile.getInputStream(zipEntry);
               applicationFileOutputStream = new FileOutputStream(zipEntryFile);

               while ((bytesRead = zipEntryInputStream.read(buffer)) != -1)
                  applicationFileOutputStream.write(buffer, 0, bytesRead);

               zipEntryInputStream.close();
               applicationFileOutputStream.close();

               manifestStream.writeBoolean(true);
               manifestStream.writeUTF(zipEntry.getName());
               manifestStream.writeLong(zipEntryFile.length());
               manifestStream.writeLong(zipEntryFile.lastModified());
            }
         }

         manifestStream.writeBoolean(false);

         return ApplicationUpdateResult.Success;
      }
      finally
      {
         if (zipFile != null)
         {
            zipFile.close();

            // I think zipFile.close() will already take care of this, for all entry InputStreams created...
            if (zipEntryInputStream != null)
               zipEntryInputStream.close();

            if (applicationFileOutputStream != null)
               applicationFileOutputStream.close();

            if (manifestStream != null)
               manifestStream.close();
         }

         downloadedArchiveFile.delete();
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final ConfigurationCheckResult checkConfiguration()
   {
      return handleCheckConfiguration();
   }


   final File getJavaExecutable()
   {
      return handleGetJavaExecutable();
   }


   final File getApplicationFile()
   {
      return handleGetApplicationFile();
   }


   final File getLauncherFile()
   {
      return handleGetLauncherFile();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public ApplicationUpdateResult updateApplication() throws IOException
   {
      return handleUpdateApplication();
   }
}