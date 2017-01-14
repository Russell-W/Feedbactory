
package com.feedbactory.server.network.application;


import com.feedbactory.server.network.component.ClientIO;
import com.feedbactory.server.core.TimestampedMessage;
import com.feedbactory.server.core.TimeCache;
import com.feedbactory.shared.network.FeedbactoryNetworkConstants;
import com.feedbactory.shared.Message;
import com.feedbactory.shared.network.ClientCompatibilityStatus;


final class ClientRequestHeaderHandler
{
   final private ClientVersionCompatibilityManager clientVersionCompatibilityManager = new ClientVersionCompatibilityManager();
   final private BroadcastMessageManager broadcastMessageManager = new BroadcastMessageManager();


   private RequestHeaderResult handleProcessHeader(final ClientIO clientIO)
   {
      /* Enforce a Feedbactory-specific header prefix in all requests to weed out genuinely erroneous or even malicious requests from those
       * which have nothing at all to do with the platform. The latter may be of interest to log from a DOS perspective but not as much so as
       * logging the byte content of requests which at least look to be Feedbactory traffic of some sort.
       * Handling any exception during the attempted header prefix read allows an immediate bail out (without logging) if some alien traffic is detected.
       */
      if (clientIO.requestBuffer.getInteger() != FeedbactoryNetworkConstants.FeedbactoryRequestIdentifier)
         return RequestHeaderResult.BadHeader;

      final long clientVersion = clientIO.requestBuffer.getLong();
      final ClientCompatibilityStatus clientCompatibilityStatus = clientVersionCompatibilityManager.getClientCompatibility(clientVersion);
      clientIO.responseBuffer.put(clientCompatibilityStatus.value);

      if ((clientCompatibilityStatus == ClientCompatibilityStatus.UpToDate) || (clientCompatibilityStatus == ClientCompatibilityStatus.UpdateAvailable))
      {
         // Write the server's current time to the response.
         clientIO.responseBuffer.putLong(TimeCache.getCurrentTimeMilliseconds());

         // Read the client's last request time and write the server broadcast message to the response if the client hasn't already seen it.
         broadcastMessageManager.writeBroadcastMessageToBuffer(clientIO.requestBuffer.getLong(), clientIO.responseBuffer);
         return RequestHeaderResult.OK;
      }
      else
         return RequestHeaderResult.SupersededClient;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final RequestHeaderResult processRequestHeader(final ClientIO clientIO)
   {
      return handleProcessHeader(clientIO);
   }


   final long getMinimumAcceptedClientVersion()
   {
      return clientVersionCompatibilityManager.getMinimumAcceptedClientVersion();
   }


   final long getLatestClientVersion()
   {
      return clientVersionCompatibilityManager.getLatestClientVersion();
   }


   final void setLatestClientVersion(final long latestClientVersion, final boolean forceMinimumVersion)
   {
      clientVersionCompatibilityManager.setLatestClientVersion(latestClientVersion, forceMinimumVersion);
   }


   final TimestampedMessage getBroadcastMessage()
   {
      return broadcastMessageManager.getBroadcastMessage();
   }


   final void setBroadcastMessage(final Message broadcastMessage)
   {
      broadcastMessageManager.setBroadcastMessage(broadcastMessage);
   }
}