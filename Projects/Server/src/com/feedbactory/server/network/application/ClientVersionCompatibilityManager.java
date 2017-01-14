/* Memos:
 * - Here is where the compatibility checking is performed against the version timestamp supplied by the client on each request.
 */

package com.feedbactory.server.network.application;


import com.feedbactory.server.core.FeedbactoryServerConstants;
import com.feedbactory.shared.network.ClientCompatibilityStatus;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;


final class ClientVersionCompatibilityManager
{
   /* MinimumCompatibleClientVersion is the minimum compatible client version as hardcoded into the current codebase.
    * minimumAcceptedClientVersion is the minimum client version that is allowed to connect to the server.
    * latestClientVersion is the most recent available client version.
    *
    * MinimumCompatibleClientVersion serves as not much more than a basic sanity check and provides no functional purpose other than disallowing an earlier latestClientVersion
    * to be set during operation.
    * In contrast the minimumAcceptedClientVersion does enforce the minimum allowable client version that can connect to the server, and it can be updated by
    * the administrator via the console at the same time or sometime after the latestClientVersion is updated.
    *
    * MinimumCompatibleClientVersion should be manually updated in development in step with the most recent client version that can still connect to the server without error.
    * The reasoning behind having the field declared here is that it may be all too easy to copy the server executable between machines while neglecting to copy the
    * associated configuration file for the minimumAcceptedClientVersion. If the MinimumCompatibleClientVersion is detected to be greater than the minimumAcceptedClientVersion
    * read from an outdated configuration file, a console warning can be displayed. This can prevent, for example, a new version of the server erroneously trying to process
    * requests from older and incompatible clients.
    */
   static final long MinimumCompatibleClientVersion = 1462063315361L;
   static final private String ClientCompatibilityFilename = "ClientCompatibility" + FeedbactoryServerConstants.DataFileExtension;

   volatile private long minimumAcceptedClientVersion;
   volatile private long latestClientVersion;


   ClientVersionCompatibilityManager()
   {
      initialise();
   }


   private void initialise()
   {
      readClientCompatibility();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void readClientCompatibility()
   {
      final File clientCompatibilityFile = FeedbactoryServerConstants.ConfigurationPath.resolve(ClientCompatibilityFilename).toFile();

      try
      (
         final DataInputStream dataInputStream = new DataInputStream(new FileInputStream(clientCompatibilityFile));
      )
      {
         minimumAcceptedClientVersion = dataInputStream.readLong();
         latestClientVersion = dataInputStream.readLong();
         if (latestClientVersion < minimumAcceptedClientVersion)
            throw new IllegalStateException("The minimum accepted client version cannot be higher than the latest client version.");
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }
   }


   private void writeClientCompatibility()
   {
      final File clientCompatibilityFile = FeedbactoryServerConstants.ConfigurationPath.resolve(ClientCompatibilityFilename).toFile();

      try
      (
         final DataOutputStream dataOutputStream = new DataOutputStream(new FileOutputStream(clientCompatibilityFile));
      )
      {
         dataOutputStream.writeLong(minimumAcceptedClientVersion);
         dataOutputStream.writeLong(latestClientVersion);
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }
   }


   private void handleSetLatestClientVersion(final long latestClientVersion, final boolean forceMinimumVersion)
   {
      if (latestClientVersion < MinimumCompatibleClientVersion)
         throw new IllegalArgumentException("The latest client version cannot be set to a value lower than the codebase minimum compatible client version.");
      else if (forceMinimumVersion || (latestClientVersion < minimumAcceptedClientVersion))
         minimumAcceptedClientVersion = latestClientVersion;

      this.latestClientVersion = latestClientVersion;

      /* Persist the value immediately, to prevent the possibility of outdated clients being
       * re-allowed in the event of a server crash before the file is written (eg. on shutdown) with the updated values.
       */
      writeClientCompatibility();
   }


   private ClientCompatibilityStatus handleGetClientCompatibility(final long clientVersion)
   {
      if (clientVersion >= latestClientVersion)
         return ClientCompatibilityStatus.UpToDate;
      else if (clientVersion >= minimumAcceptedClientVersion)
         return ClientCompatibilityStatus.UpdateAvailable;
      else
         return ClientCompatibilityStatus.UpdateRequired;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final long getMinimumAcceptedClientVersion()
   {
      return minimumAcceptedClientVersion;
   }


   final long getLatestClientVersion()
   {
      return latestClientVersion;
   }


   final void setLatestClientVersion(final long latestClientVersion, final boolean forceMinimumVersion)
   {
      handleSetLatestClientVersion(latestClientVersion, forceMinimumVersion);
   }


   final ClientCompatibilityStatus getClientCompatibility(final long clientVersion)
   {
      return handleGetClientCompatibility(clientVersion);
   }
}