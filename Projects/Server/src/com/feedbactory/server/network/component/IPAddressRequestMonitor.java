/* Memos:
 * - The client request monitor provides a general mechanism for monitoring requests per IP address over time. The caller can inform the monitor of the requests as they
 *   appear, including whether or not they are legitimate requests for the application. The monitor collates the information and also provides information regarding
 *   the current 'client standing' of each IP address, based on its previous requests. Too many requests either legitimate (spam) or erroneous will result in
 *   the client standing of an IP address being set to 'temporarily blocked'. Further requests from IPs in that state ('denied' requests) can result in them effectively
 *   being blocked for a longer period of time, essentially for as long as they continue to spam.
 *
 * - Core to the monitor operations is a housekeeping task whose job it is to periodically purge the tracked IP addresses (where there are no further incoming requests),
 *   reinstate temporarily blocked IP's, and reset the request counts for every kept IP address. The length of time that IP's are blocked are effectively at the mercy of
 *   how often the housekeeping task runs. If I wanted I could change this by making the housekeeping task run a lot more often (eg. every 5 mins), and
 *   reinstating/paroling IP's based on the timestamp that is attached to them when they are temporarily blocked. This would involve introducing a fixed time length
 *   for enforcing the temp blocking.
 *
 * - There is a manual 'blacklist IP' service available; IP's marked with this are not eligible for reinstatement until manually unblocked by the caller.
 *
 * - The request counts per IP address are used to determine the client standing but they can also be used to calculate overall metrics for the monitor for the most
 *   recent monitor period. I think this will greatly help in determining what numbers are reasonable for temp blocking, both for single IP's as well as shared NAT IP's.
 *   See the getMetrics() method.
 *
 * - What happens on server shutdown & restart? What data is saved? Blacklisted and temp blocked IP's are saved along with their timestamps; the number of requests that
 *   they made during the last monitor period isn't. To play it safe, temp blocked IP's are not immediately cleared on server restart even if enough time has passed
 *   that they might normally be given another chance. The problem is that there is no way to know whether or not they have continued to try to spam the server while it's
 *   been down. So they are placed in their last known state - temp blocked - at least until the first monitor period has passed. I have to keep in mind the fact that as
 *   mentioned above persistent spamming IP's will effectively become semi-permanently blocked since they are only paroled if their request count during the most recent
 *   monitor period hasn't crossed the spam threshold. It's a safer option to continue to track them rather than give them another free swing on restart.
 *
 * - I originally had in place a request reinstatement allowance scheme for temp blocked IP addresses. The idea was that the server shouldn't allow them a clean slate
 *   to spam after every housekeeping run. So a spammy IP would go through a gradual 'request reinstatement' phase until it returned to full standing where it was again
 *   allowed the full number of requests for the following monitor period. However I think that the additional complexity is unwarranted since the use case is fairly
 *   flawed. From the metrics I should be able to gauge very well what is the expected maximum number of requests for any IP address (even many PC's behind a large scale NAT)
 *   within any set period of time. That number of requests will be a reasonable figure, nowhere near high enough to jeopardise the availability of the server. An IP may
 *   safely be granted this amount of requests every monitor period without fear of them doing any harm. If however an IP address is persistently spamming the server beyond
 *   this reasonable threshold, then it should be barred without reinstatement. If NAT is being used by that IP address, then it's the responsibility of that provider to
 *   weed out the offending machine within their network. If they are using large scale NAT then they should have the capability to trace the origins of spam requests.
 * 
 *   Note that this is focused on defence against DOS, NOT against attacks on the Feedbactory ratings system or other Feedbactory users' passwords. For the latter I
 *   do need to employ other safeguards, but here is not the place.
 *
 * - The public checkpointing and housekeeping management methods aren't threadsafe, the caller must carefully coordinate calls to them. For example it's
 *   unsafe to overlap calls to startHousekeeping() and shutdownHousekeeping(), or startHousekeeping() and restoreFromCheckpoint().
 *   It's OK though for a checkpoint to be saved (NOT restored), either periodically or manually, while a housekeeping run is active.
 */

package com.feedbactory.server.network.component;


import com.feedbactory.server.core.FeedbactoryServerConstants;
import com.feedbactory.server.core.TimeCache;
import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.log.SystemLogLevel;
import com.feedbactory.shared.network.IPAddressStanding;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


final public class IPAddressRequestMonitor
{
   static final private String IPAddressRequestMonitorFilename = "IPAddressRequestMonitor" + FeedbactoryServerConstants.DataFileExtension;

   final private int monitorFrequencyMinutes;

   final private int spamRequestsBlockThreshold;
   final private int erroneousRequestsBlockThreshold;

   final private ConcurrentHashMap<InetAddress, IPAddressRequestNode> requestsByIPAddress;

   final private HousekeepingTask housekeepingTask = new HousekeepingTask();


   public IPAddressRequestMonitor(final int monitorFrequencyMinutes, final int spamRequestsBlockThreshold, final int erroneousRequestsBlockThreshold,
                                  final int initialIPAddressCapacity, final int concurrency)
   {
      this.monitorFrequencyMinutes = monitorFrequencyMinutes;
      this.spamRequestsBlockThreshold = spamRequestsBlockThreshold;
      this.erroneousRequestsBlockThreshold = erroneousRequestsBlockThreshold;
      requestsByIPAddress = new ConcurrentHashMap<>(initialIPAddressCapacity, 0.75f, concurrency);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   /* A class encapsulating some useful operational info maintained by the request monitor.
    * Most of the data is calculated on demand, and the values and calculations are related so it makes sense to push them all into the one
    * data 'snapshot' for a comprehensive output display rather than calculate them individually on demand.
    * A client receiving this may also wish to derive some other results, eg. average regular requests per IP address.
    */
   static final public class IPMonitorMetrics
   {
      final public boolean isHousekeepingEnabled;
      final public int monitorFrequencyMinutes;
      final public long currentMonitorPeriodStartTime;

      final public int spamRequestsBlockThreshold;
      final public int erroneousRequestsBlockThreshold;

      final public int totalIPAddressesTracking;
      final public int newIPAddressesThisMonitorPeriod;
      final public int temporarilyBlockedIPAddresses;
      final public int blacklistedIPAddresses;

      final public int totalLegitimateRequestsThisMonitorPeriod;
      final public int totalErroneousRequestsThisMonitorPeriod;
      final public int totalDeniedRequestsThisMonitorPeriod;

      final public InetAddress busiestIPAddressThisMonitorPeriod;
      final public int busiestIPAddressRequestsThisMonitorPeriod;


      private IPMonitorMetrics(final boolean isHousekeepingEnabled, final int monitorFrequencyMinutes, final long currentMonitorPeriodStartTime,
                               final int spamRequestsBlockThreshold, final int erroneousRequestsBlockThreshold,
                               final int totalIPAddressesTracking, final int newIPAddressesThisMonitorPeriod,
                               final int temporarilyBlockedIPAddresses, final int administratorBlacklistedIPAddresses,
                               final int totalLegitimateRequestsThisMonitorPeriod, final int totalErroneousRequestsThisMonitorPeriod,
                               final int totalDeniedRequestsThisMonitorPeriod,
                               final InetAddress busiestIPAddressThisMonitorPeriod, final int busiestIPAddressRequestsThisMonitorPeriod)
      {
         this.isHousekeepingEnabled = isHousekeepingEnabled;
         this.monitorFrequencyMinutes = monitorFrequencyMinutes;
         this.currentMonitorPeriodStartTime = currentMonitorPeriodStartTime;

         this.spamRequestsBlockThreshold = spamRequestsBlockThreshold;
         this.erroneousRequestsBlockThreshold = erroneousRequestsBlockThreshold;

         this.totalIPAddressesTracking = totalIPAddressesTracking;
         this.newIPAddressesThisMonitorPeriod = newIPAddressesThisMonitorPeriod;
         this.temporarilyBlockedIPAddresses = temporarilyBlockedIPAddresses;
         this.blacklistedIPAddresses = administratorBlacklistedIPAddresses;

         this.totalLegitimateRequestsThisMonitorPeriod = totalLegitimateRequestsThisMonitorPeriod;
         this.totalErroneousRequestsThisMonitorPeriod = totalErroneousRequestsThisMonitorPeriod;
         this.totalDeniedRequestsThisMonitorPeriod = totalDeniedRequestsThisMonitorPeriod;

         this.busiestIPAddressThisMonitorPeriod = busiestIPAddressThisMonitorPeriod;
         this.busiestIPAddressRequestsThisMonitorPeriod = busiestIPAddressRequestsThisMonitorPeriod;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class BlockedIPAddressMetrics
   {
      final public InetAddress ipAddress;
      final public long ipAddressStandingLastUpdated;
      final public int deniedRequestsThisMonitorPeriod;


      private BlockedIPAddressMetrics(final InetAddress ipAddress, final long ipAddressStandingLastUpdated, final int deniedRequestsThisMonitorPeriod)
      {
         this.ipAddress = ipAddress;
         this.ipAddressStandingLastUpdated = ipAddressStandingLastUpdated;
         this.deniedRequestsThisMonitorPeriod = deniedRequestsThisMonitorPeriod;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class IPAddressMetrics
   {
      final public IPAddressStanding ipAddressStanding;
      final public long ipAddressStandingLastUpdated;

      final public int legitimateRequestsThisMonitorPeriod;
      final public int erroneousRequestsThisMonitorPeriod;
      final public int deniedRequestsThisMonitorPeriod;


      private IPAddressMetrics(final IPAddressStanding ipAddressStanding, final long ipAddressStandingLastUpdated,
                               final int legitimateRequestsThisMonitorPeriod, final int erroneousRequestsThisMonitorPeriod, final int deniedRequestsThisMonitorPeriod)
      {
         this.ipAddressStanding = ipAddressStanding;
         this.ipAddressStandingLastUpdated = ipAddressStandingLastUpdated;

         this.legitimateRequestsThisMonitorPeriod = legitimateRequestsThisMonitorPeriod;
         this.erroneousRequestsThisMonitorPeriod = erroneousRequestsThisMonitorPeriod;
         this.deniedRequestsThisMonitorPeriod = deniedRequestsThisMonitorPeriod;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final private class IPAddressRequestNode
   {
      private int legitimateRequestsThisMonitorPeriod;
      private int erroneousRequestsThisMonitorPeriod;
      private int deniedRequestsThisMonitorPeriod;

      /* Maintain a client standing rather than just derive it from the above requests counts. This saves repeated calculations when checking the IP's
       * standing, while also making it possible to reset and track the IP's request counts during each monitor period.
       */
      private IPAddressStanding ipAddressStanding;
      private long ipAddressStandingLastUpdated;
      private boolean isDeleted;


      private void incrementLegitimateRequests()
      {
         legitimateRequestsThisMonitorPeriod ++;
      }


      private void incrementErroneousRequests()
      {
         erroneousRequestsThisMonitorPeriod ++;
      }


      private void incrementDeniedRequests()
      {
         deniedRequestsThisMonitorPeriod ++;
      }


      private int totalRequestsThisMonitorPeriod()
      {
         return (legitimateRequestsThisMonitorPeriod + erroneousRequestsThisMonitorPeriod + deniedRequestsThisMonitorPeriod);
      }


      private void resetRequestCounts()
      {
         legitimateRequestsThisMonitorPeriod = 0;
         erroneousRequestsThisMonitorPeriod = 0;
         deniedRequestsThisMonitorPeriod = 0;
      }


      private void setStanding(final IPAddressStanding clientStanding)
      {
         setStanding(clientStanding, TimeCache.getCurrentTimeMilliseconds());
      }


      private void setStanding(final IPAddressStanding clientStanding, final long ipAddressStandingLastUpdated)
      {
         this.ipAddressStanding = clientStanding;
         this.ipAddressStandingLastUpdated = ipAddressStandingLastUpdated;
      }


      private void markAsDeleted()
      {
         isDeleted = true;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class HousekeepingTask implements Runnable, ThreadFactory
   {
      private ScheduledThreadPoolExecutor executor;

      final private AtomicInteger newIPAddressesThisMonitorPeriod = new AtomicInteger();
      private long currentMonitorPeriodStartTime;


      private HousekeepingTask()
      {
         initialise();
      }


      private void initialise()
      {
         resetMonitorPeriod();
      }


      /****************************************************************************
       *
       ***************************************************************************/


      @Override
      final public Thread newThread(final Runnable runnable)
      {
         final Thread thread = new Thread(runnable, "IP address request monitor housekeeping task");
         thread.setDaemon(true);
         return thread;
      }


      @Override
      final public void run()
      {
         try
         {
            updateIPAddressStatuses();
         }
         catch (final Exception anyException)
         {
            /* Exception handling provided for -any- exception, since any exceptions will otherwise be captured
             * by the enclosing FutureTask that is generated when this Runnable is submitted to ScheduledExecutorService.scheduleAtFixedRate().
             * Unhandled exceptions would also prevent further scheduleAtFixedRate() invocations from running.
             */
            FeedbactoryLogger.logSystemEvent(SystemLogLevel.ApplicationError, getClass(), "IP address request monitor housekeeping task failed", anyException);
         }
      }


      synchronized final public void updateIPAddressStatuses()
      {
         /* Lock on the housekeeping instance for the sake of ensuring consistent reporting metrics (see the method for generating that below).
          * The metrics will always be out of date when there are new connections incoming even as they are being generated, but I can at least
          * ensure that the metrics appear consistent within the context of the outdated snapshot. To that end I can prevent the metrics
          * generator from encountering the set of IPAddressRequestNode's just as the housekeeping task has, for example, reset the counts on half of them.
          * Or from encountering a zero or low count for newIPAddressesThisMonitorPeriod when the lastRunTime hasn't yet been set for the current run.
          *
          * It's a small performance hit - the extra locking on the housekeeping task is not required for the other regular operational methods.
          */
         final Iterator<IPAddressRequestNode> ipAddressRequestsIterator = requestsByIPAddress.values().iterator();
         IPAddressRequestNode ipAddressRequests;
         boolean removeRequestNode;

         while (ipAddressRequestsIterator.hasNext())
         {
            ipAddressRequests = ipAddressRequestsIterator.next();
            removeRequestNode = false;

            synchronized (ipAddressRequests)
            {
               switch (ipAddressRequests.ipAddressStanding)
               {
                  case OK:
                     removeRequestNode = ((ipAddressRequests.legitimateRequestsThisMonitorPeriod == 0) &&
                                          (ipAddressRequests.erroneousRequestsThisMonitorPeriod == 0));
                     break;

                  case TemporarilyBlocked:
                     /* Need to count ALL requests not just denied requests, since the IP node may have been set to temporarily blocked
                      * just before the housekeeping run.
                      * Also the IP may be sending erroneous requests once blocked, these should be counted too.
                      */
                     if (ipAddressRequests.totalRequestsThisMonitorPeriod() < spamRequestsBlockThreshold)
                        ipAddressRequests.setStanding(IPAddressStanding.OK);

                     break;

                  case Blacklisted:
                     break;

                  default:
                     throw new AssertionError("Unhandled IP address standing: " + ipAddressRequests.ipAddressStanding);
               }

               if (removeRequestNode)
               {
                  ipAddressRequestsIterator.remove();
                  ipAddressRequests.markAsDeleted();
               }
               else
                  ipAddressRequests.resetRequestCounts();
            }
         }

         resetMonitorPeriod();
      }


      /****************************************************************************
       * 
       ***************************************************************************/


      synchronized private long getMonitorPeriodStartTime()
      {
         return currentMonitorPeriodStartTime;
      }


      synchronized private void resetMonitorPeriod()
      {
         currentMonitorPeriodStartTime = TimeCache.getCurrentTimeMilliseconds();
         newIPAddressesThisMonitorPeriod.set(0);
      }


      private boolean isStarted()
      {
         return (executor != null);
      }


      private void start()
      {
         if (isStarted())
            throw new IllegalStateException("Housekeeping task has already been started.");

         executor = new ScheduledThreadPoolExecutor(1, this);
         executor.setKeepAliveTime(10, TimeUnit.SECONDS);
         executor.allowCoreThreadTimeOut(true);

         /* Unlike other housekeeping tasks which should probably be run immediately on startup, I don't necessarily want to wipe the slate clean
          * for the 'spam' status of IP addresses at startup, particularly for momentary downtime. So, at the request to start the housekeeping,
          * first wait until a full monitor period has elapsed before allowing any IP addresses to be reinstated.
          * There's an argument for re-examining the temp blocked IP addresses on restoration - maybe check their clientStandingUpdated timestamp to
          * see if they're eligible for parole - but I don't feel comfortable with this approach since it will automatically parole any and all
          * temp blocked IP addresses if the server has been down for long enough, even if those IP's have been spamming in the meantime. The safest
          * approach on restoration/restart is to reset them to their last known state - temporarily blocked - and see how they behave for the first
          * monitor period.
          */
         executor.scheduleAtFixedRate(this, monitorFrequencyMinutes, monitorFrequencyMinutes, TimeUnit.MINUTES);
      }


      private void reportNewIPAddress()
      {
         /* Re: reporting, it's no problem at all if this counter is incremented at any time during housekeeping or reporting,
          * as long as the reporting of it in the metrics is consistent with the last run time. See the note above.
          */
         newIPAddressesThisMonitorPeriod.incrementAndGet();
      }


      private void shutdown() throws InterruptedException
      {
         if (executor != null)
         {
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            executor = null;
         }
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private IPAddressRequestNode putIfAbsentIPAddressRequestNode(final InetAddress inetAddress)
   {
      IPAddressRequestNode ipAddressRequestSummary = requestsByIPAddress.get(inetAddress);

      if (ipAddressRequestSummary == null)
      {
         ipAddressRequestSummary = new IPAddressRequestNode();

         // Ensure the visibility of the monitor data to subsequent threads.
         synchronized (ipAddressRequestSummary)
         {
            ipAddressRequestSummary.setStanding(IPAddressStanding.OK);
         }

         final IPAddressRequestNode existingClientRequestSummary = requestsByIPAddress.putIfAbsent(inetAddress, ipAddressRequestSummary);

         if (existingClientRequestSummary != null)
            return existingClientRequestSummary;
         else
         {
            /* If this method is called more than once as part of a concurrency loop, it will be due to the housekeeping task removing
             * IP address nodes simultaneously as the calling method is having to retry an add into the requestsByIPAddress collection.
             * Because the running of the housekeeping task also signals the start of the next monitoring period, it's fair enough to
             * consider the removed & immediately re-added IP address a 'new' one for the next monitoring period, ie. as opposed to
             * using a flag in each loop in the caller to ensure that reportNewIPAddress here is only called the once.
             */
            housekeepingTask.reportNewIPAddress();
         }
      }

      return ipAddressRequestSummary;
   }


   private IPAddressStanding handleGetIPAddressStanding(final InetAddress inetAddress)
   {
      final IPAddressRequestNode ipAddressRequestSummary = requestsByIPAddress.get(inetAddress);

      if (ipAddressRequestSummary == null)
         return IPAddressStanding.OK;
      else
      {
         synchronized (ipAddressRequestSummary)
         {
            return ipAddressRequestSummary.ipAddressStanding;
         }
      }
   }


   private void handleReportLegitimateClientRequest(final InetAddress inetAddress)
   {
      /* Loop to handle the very unlikely but possible case where the housekeeping task has marked the IPAddressRequestNode object as deleted
       * between this method retrieving (or creating) the object and grabbing the lock on it.
       * This ensures for example that an IP address can't get away with flooding the server with a huge number of invalid requests just as the
       * housekeeping task has marked their IP address for deletion from the tracking collection. If not for the isDeleted check and the retry loop,
       * the flood of invalid requests and recording of a temporarily barred IP address would occur on an IPAddressRequestNode that has just been
       * removed by the housekeeper, rendering it lost from the records.
       */
      for (;;)
      {
         final IPAddressRequestNode ipAddressRequestSummary = putIfAbsentIPAddressRequestNode(inetAddress);

         synchronized (ipAddressRequestSummary)
         {
            if (ipAddressRequestSummary.isDeleted)
               continue;

            ipAddressRequestSummary.incrementLegitimateRequests();

            if ((ipAddressRequestSummary.ipAddressStanding == IPAddressStanding.OK) && (ipAddressRequestSummary.legitimateRequestsThisMonitorPeriod >= spamRequestsBlockThreshold))
               ipAddressRequestSummary.setStanding(IPAddressStanding.TemporarilyBlocked);

            break;
         }
      }
   }


   private void handleReportDeniedClientRequest(final InetAddress inetAddress)
   {
      for (;;)
      {
         final IPAddressRequestNode ipAddressRequestSummary = putIfAbsentIPAddressRequestNode(inetAddress);

         synchronized (ipAddressRequestSummary)
         {
            if (ipAddressRequestSummary.isDeleted)
               continue;

            ipAddressRequestSummary.incrementDeniedRequests();
            break;
         }
      }
   }


   private void handleReportErroneousClientRequest(final InetAddress inetAddress)
   {
      for (;;)
      {
         final IPAddressRequestNode ipAddressRequestSummary = putIfAbsentIPAddressRequestNode(inetAddress);

         synchronized (ipAddressRequestSummary)
         {
            if (ipAddressRequestSummary.isDeleted)
               continue;

            ipAddressRequestSummary.incrementErroneousRequests();

            if ((ipAddressRequestSummary.ipAddressStanding == IPAddressStanding.OK) && (ipAddressRequestSummary.erroneousRequestsThisMonitorPeriod >= erroneousRequestsBlockThreshold))
               ipAddressRequestSummary.setStanding(IPAddressStanding.TemporarilyBlocked);

            break;
         }
      }
   }


   private void handleSetBlacklisted(final InetAddress inetAddress)
   {
      for (;;)
      {
         final IPAddressRequestNode ipAddressRequestSummary = putIfAbsentIPAddressRequestNode(inetAddress);

         synchronized (ipAddressRequestSummary)
         {
            if (ipAddressRequestSummary.isDeleted)
               continue;

            ipAddressRequestSummary.setStanding(IPAddressStanding.Blacklisted);
            break;
         }
      }
   }


   private IPAddressStanding handleSetParoled(final InetAddress inetAddress)
   {
      final IPAddressRequestNode ipAddressRequestSummary = requestsByIPAddress.get(inetAddress);

      if (ipAddressRequestSummary != null)
      {
         synchronized (ipAddressRequestSummary)
         {
            /* Not much point in checking the isDeleted status. If it is marked as deleted,
             * it's because the housekeeping task has seen that the IP address has been inactive and can be removed from tracking.
             * If that's the case, the effective IPAddressStanding will be the same as that determined below: OK.
             */
            if (ipAddressRequestSummary.totalRequestsThisMonitorPeriod() >= spamRequestsBlockThreshold)
               ipAddressRequestSummary.setStanding(IPAddressStanding.TemporarilyBlocked);
            else
               ipAddressRequestSummary.setStanding(IPAddressStanding.OK);

            return ipAddressRequestSummary.ipAddressStanding;
         }
      }
      else
         return null;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSaveCheckpoint(final Path checkpointPath) throws IOException
   {
      final File file = checkpointPath.resolve(IPAddressRequestMonitorFilename).toFile();

      try
      (
         final DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
      )
      {
         IPAddressRequestNode ipAddressRequestNode;
         byte[] ipAddress;

         for (final Entry<InetAddress, IPAddressRequestNode> ipAddressRequests : requestsByIPAddress.entrySet())
         {
            ipAddressRequestNode = ipAddressRequests.getValue();

            synchronized (ipAddressRequestNode)
            {
               /* - Only save temp blocked and blacklisted IP addresses for now.
                * - These will never be marked as deleted by the housekeeping task.
                */
               if ((ipAddressRequestNode.ipAddressStanding == IPAddressStanding.Blacklisted) ||
                   (ipAddressRequestNode.ipAddressStanding == IPAddressStanding.TemporarilyBlocked))
               {
                  ipAddress = ipAddressRequests.getKey().getAddress();

                  dataOutputStream.writeByte(ipAddress.length);
                  dataOutputStream.write(ipAddress);
                  dataOutputStream.writeByte(ipAddressRequestNode.ipAddressStanding.value);
                  dataOutputStream.writeLong(ipAddressRequestNode.ipAddressStandingLastUpdated);
               }
            }
         }

         // Write EOF marker.
         dataOutputStream.writeByte(-1);
      }
   }


   private void handleRestoreFromCheckpoint(final Path checkpointPath) throws IOException
   {
      if (isHousekeepingStarted())
         throw new IllegalStateException("Cannot restore from checkpoint while housekeeping task is active.");

      requestsByIPAddress.clear();
      housekeepingTask.resetMonitorPeriod();

      final File file = checkpointPath.resolve(IPAddressRequestMonitorFilename).toFile();

      try
      (
         final DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
      )
      {
         byte ipAddressLength;
         byte[] ipAddress;
         byte ipAddressStandingValue;
         IPAddressStanding ipAddressStanding;
         long ipAddressStandingUpdated;
         IPAddressRequestNode ipAddressRequestSummary;

         while ((ipAddressLength = dataInputStream.readByte()) != -1)
         {
            ipAddress = new byte[ipAddressLength];
            dataInputStream.readFully(ipAddress);

            ipAddressStandingValue = dataInputStream.readByte();
            ipAddressStanding = IPAddressStanding.fromValue(ipAddressStandingValue);
            if (ipAddressStanding == null)
               throw new IllegalArgumentException("Invalid IP address standing value: " + ipAddressStandingValue);

            ipAddressStandingUpdated = dataInputStream.readLong();

            ipAddressRequestSummary = new IPAddressRequestNode();

            // Ensure the visibility of the monitor data to subsequent thread accessors.
            synchronized (ipAddressRequestSummary)
            {
               ipAddressRequestSummary.setStanding(ipAddressStanding, ipAddressStandingUpdated);
            }

            requestsByIPAddress.put(InetAddress.getByAddress(ipAddress), ipAddressRequestSummary);
         }
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private IPMonitorMetrics handleGetMetrics()
   {
      int totalLegalRequestsThisMonitorPeriod = 0;
      int totalErroneousRequestsThisMonitorPeriod = 0;
      int totalDeniedRequestsThisMonitorPeriod = 0;

      int temporarilyBlockedIPAddresses = 0;
      int administratorBlacklistedIPAddresses = 0;

      InetAddress busiestIPAddressThisMonitorPeriod = null;
      int busiestIPAddressRequestsThisMonitorPeriod = 0;

      IPAddressRequestNode ipAddressRequests;
      int ipAddressTotalRequestsThisMonitorPeriod;

      // For details on the locking here, refer to the comments at the housekeeping section.
      synchronized (housekeepingTask)
      {
         for (final Entry<InetAddress, IPAddressRequestNode> ipAddressRequestsEntry : requestsByIPAddress.entrySet())
         {
            ipAddressRequests = ipAddressRequestsEntry.getValue();

            synchronized (ipAddressRequests)
            {
               ipAddressTotalRequestsThisMonitorPeriod = ipAddressRequests.totalRequestsThisMonitorPeriod();

               if (ipAddressTotalRequestsThisMonitorPeriod > busiestIPAddressRequestsThisMonitorPeriod)
               {
                  busiestIPAddressRequestsThisMonitorPeriod = ipAddressTotalRequestsThisMonitorPeriod;
                  busiestIPAddressThisMonitorPeriod = ipAddressRequestsEntry.getKey();
               }

               totalLegalRequestsThisMonitorPeriod += ipAddressRequests.legitimateRequestsThisMonitorPeriod;
               totalErroneousRequestsThisMonitorPeriod += ipAddressRequests.erroneousRequestsThisMonitorPeriod;
               totalDeniedRequestsThisMonitorPeriod += ipAddressRequests.deniedRequestsThisMonitorPeriod;

               switch (ipAddressRequests.ipAddressStanding)
               {
                  case OK:
                     break;

                  case TemporarilyBlocked:
                     temporarilyBlockedIPAddresses ++;
                     break;

                  case Blacklisted:
                     administratorBlacklistedIPAddresses ++;
                     break;

                  default:
                     throw new AssertionError("Unhandled client standing: " + ipAddressRequests.ipAddressStanding);
               }
            }
         }

         return new IPMonitorMetrics(isHousekeepingStarted(), monitorFrequencyMinutes, housekeepingTask.getMonitorPeriodStartTime(),
                                     spamRequestsBlockThreshold, erroneousRequestsBlockThreshold,
                                     requestsByIPAddress.size(), housekeepingTask.newIPAddressesThisMonitorPeriod.get(),
                                     temporarilyBlockedIPAddresses, administratorBlacklistedIPAddresses,
                                     totalLegalRequestsThisMonitorPeriod, totalErroneousRequestsThisMonitorPeriod, totalDeniedRequestsThisMonitorPeriod,
                                     busiestIPAddressThisMonitorPeriod, busiestIPAddressRequestsThisMonitorPeriod);
      }
   }


   private List<BlockedIPAddressMetrics> handleGetBlockedMetrics(final IPAddressStanding targetIPAddressStanding)
   {
      final List<BlockedIPAddressMetrics> blockedIPAddresses = new LinkedList<>();

      IPAddressRequestNode ipAddressRequests;

      for (final Entry<InetAddress, IPAddressRequestNode> ipAddressRequestsEntry : requestsByIPAddress.entrySet())
      {
         ipAddressRequests = ipAddressRequestsEntry.getValue();

         synchronized (ipAddressRequests)
         {
            if (ipAddressRequests.ipAddressStanding == targetIPAddressStanding)
            {
               final BlockedIPAddressMetrics blockedIPAddressMetrics = new BlockedIPAddressMetrics(ipAddressRequestsEntry.getKey(),
                                                                                                   ipAddressRequests.ipAddressStandingLastUpdated,
                                                                                                   ipAddressRequests.deniedRequestsThisMonitorPeriod);
               blockedIPAddresses.add(blockedIPAddressMetrics);
            }
         }
      }

      // Return a mutable list, to allow the caller to sort the collection if desired.
      return blockedIPAddresses;
   }


   private IPAddressMetrics handleGetIPAddressMetrics(final InetAddress ipAddress)
   {
      final IPAddressRequestNode ipAddressRequests = requestsByIPAddress.get(ipAddress);

      if (ipAddressRequests == null)
         return null;

      synchronized (ipAddressRequests)
      {
         return new IPAddressMetrics(ipAddressRequests.ipAddressStanding, ipAddressRequests.ipAddressStandingLastUpdated,
                                     ipAddressRequests.legitimateRequestsThisMonitorPeriod, ipAddressRequests.erroneousRequestsThisMonitorPeriod,
                                     ipAddressRequests.deniedRequestsThisMonitorPeriod);
      }
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public IPAddressStanding getIPAddressStanding(final InetAddress inetAddress)
   {
      return handleGetIPAddressStanding(inetAddress);
   }


   final public void reportLegitimateClientRequest(final InetAddress inetAddress)
   {
      handleReportLegitimateClientRequest(inetAddress);
   }


   final public void reportErroneousClientRequest(final InetAddress inetAddress)
   {
      handleReportErroneousClientRequest(inetAddress);
   }


   final public void reportDeniedClientRequest(final InetAddress inetAddress)
   {
      handleReportDeniedClientRequest(inetAddress);
   }


   final public void setIPAddressBlacklisted(final InetAddress inetAddress)
   {
      handleSetBlacklisted(inetAddress);
   }


   final public IPAddressStanding setIPAddressParoled(final InetAddress inetAddress)
   {
      return handleSetParoled(inetAddress);
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


   final public long getMonitorPeriodStartTime()
   {
      return housekeepingTask.getMonitorPeriodStartTime();
   }


   final public boolean isHousekeepingStarted()
   {
      return (housekeepingTask.isStarted());
   }


   final public void startHousekeeping()
   {
      housekeepingTask.start();
   }


   final public void shutdownHousekeeping() throws InterruptedException
   {
      housekeepingTask.shutdown();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public IPMonitorMetrics getMetrics()
   {
      return handleGetMetrics();
   }


   final public List<BlockedIPAddressMetrics> getTemporarilyBlockedMetrics()
   {
      return handleGetBlockedMetrics(IPAddressStanding.TemporarilyBlocked);
   }


   final public List<BlockedIPAddressMetrics> getBlacklistedMetrics()
   {
      return handleGetBlockedMetrics(IPAddressStanding.Blacklisted);
   }


   final public IPAddressMetrics getIPAddressMetrics(final InetAddress ipAddress)
   {
      return handleGetIPAddressMetrics(ipAddress);
   }
}