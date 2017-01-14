/* Memos:
 * - The methods aren't threadsafe, the burden is on the caller to ensure thread safety.
 *
 * - Any class that has a direct reference to this class is a trusted class. Rather than clutter the API with nothing but forwarded network-related calls,
 *   I'd rather provide the trusted callers with direct access to the child objects to query/update them as required. Any calls that inherently require
 *   grouped processing on the network subsystem (eg. housekeeping or checkpointing) should however be catered for by methods on this class.
 */

package com.feedbactory.server.network.application;


import com.feedbactory.server.network.component.IPAddressRequestMonitor;
import com.feedbactory.server.core.FeedbactoryOperationsManager;
import com.feedbactory.server.feedback.FeedbackManager;
import com.feedbactory.server.network.component.buffer.WritableByteBuffer;
import com.feedbactory.server.core.FeedbactoryServerConstants;
import com.feedbactory.server.useraccount.UserAccountManager;
import java.io.IOException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.file.Path;


final public class NetworkServiceManager
{
   static final private int IPMonitorFrequencyMinutes = 32;

   static final private int IPMonitorSpamRequestsBlockThreshold = 5129;
   static final private int IPMonitorErroneousRequestsBlockThreshold = 47;

   static final private int IPMonitorInitialIPAddressCapacity = 10000;

   final private FeedbactoryOperationsManager operationsManager;

   final private ApplicationServerController applicationServerController;
   final private IPAddressRequestMonitor ipRequestMonitor = new IPAddressRequestMonitor(IPMonitorFrequencyMinutes, IPMonitorSpamRequestsBlockThreshold,
                                                                                        IPMonitorErroneousRequestsBlockThreshold, IPMonitorInitialIPAddressCapacity,
                                                                                        FeedbactoryServerConstants.ServerConcurrency);
   final private ApplicationRequestManager requestManager;
   final private NetworkToApplicationGateway networkToApplicationGateway;


   public NetworkServiceManager(final FeedbactoryOperationsManager operationsManager, final UserAccountManager userAccountManager,
                                final FeedbackManager feedbackManager) throws IOException
   {
      this.operationsManager = operationsManager;

      applicationServerController = new ApplicationServerController(this, new ApplicationRequestManagerInterface());

      networkToApplicationGateway = new NetworkToApplicationGateway(userAccountManager, feedbackManager, new BufferProviderInterface());

      requestManager = new ApplicationRequestManager(new ServerControllerInterface(), ipRequestMonitor, networkToApplicationGateway);
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final class ApplicationRequestManagerInterface
   {
      final void newConnectionAccepted(final AsynchronousSocketChannel channel)
      {
         requestManager.newConnectionAccepted(channel);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final public class BufferProviderInterface
   {
      final public WritableByteBuffer allocateByteBuffer()
      {
         return requestManager.takePoolBuffer();
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final class ServerControllerInterface
   {
      final boolean isBusy()
      {
         return applicationServerController.isBusy();
      }


      final void clientConnectionFinished(final AsynchronousSocketChannel channel)
      {
         applicationServerController.clientConnectionFinished(channel);
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private void handleSaveCheckpoint(final Path checkpointPath) throws IOException
   {
      ipRequestMonitor.saveCheckpoint(checkpointPath);
      networkToApplicationGateway.saveCheckpoint(checkpointPath);
   }


   private void handleRestoreFromCheckpoint(final Path checkpointPath) throws IOException
   {
      ipRequestMonitor.restoreFromCheckpoint(checkpointPath);
      networkToApplicationGateway.restoreFromCheckpoint(checkpointPath);
   }


   private void handleStartHousekeeping()
   {
      // Will throw an exception if the housekeeping has already been started.
      ipRequestMonitor.startHousekeeping();
      networkToApplicationGateway.startHousekeeping();
   }


   private void handleShutdownHousekeeping() throws InterruptedException
   {
      ipRequestMonitor.shutdownHousekeeping();
      networkToApplicationGateway.shutdownHousekeeping();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public void saveCheckpoint(final Path checkpointPath) throws IOException
   {
      handleSaveCheckpoint(checkpointPath);
   }


   final public void restoreFromCheckpoint(final Path checkpointPath) throws IOException
   {
      handleRestoreFromCheckpoint(checkpointPath);
   }


   final public void startHousekeeping()
   {
      handleStartHousekeeping();
   }


   final public void shutdownHousekeeping() throws InterruptedException
   {
      handleShutdownHousekeeping();
   }


   final public ApplicationServerController getServerController()
   {
      return applicationServerController;
   }


   final public IPAddressRequestMonitor getRequestMonitor()
   {
      return ipRequestMonitor;
   }


   final public ApplicationRequestManager getRequestManager()
   {
      return requestManager;
   }


   final public NetworkToApplicationGateway getNetworkToApplicationGateway()
   {
      return networkToApplicationGateway;
   }
}