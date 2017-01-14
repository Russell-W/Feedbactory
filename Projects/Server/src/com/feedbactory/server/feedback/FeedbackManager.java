/* Memos:
 * - All methods here that read or write feedback attached to a user account also lock on the user account. As with other sections of the server that deal with user account
 *   related data, the locking is not overly fine-tuned since I can assume that it will be very rare for simultaneous requests to be acting on the same user account.
 *   However I need to keep in mind the periodic checkpointing task which also locks on user accounts.
 *
 * - A simple caching scheme is used when users add feedback for an item profile. As both the FeedbackItem and FeedbackItemProfile are deserialised from the network
 *   or from disk, a completely new copy is created in memory whether or not there already exists an exact byte copy of those same objects. The caching on
 *   feedback submission is designed to eliminate this. Elimination of duplicates is especially important because on each feedback submission the server is saving a
 *   copy of the profile as seen by each individual user, so the waste would be significant. The overwhelming majority of users will of course see exactly the same profile
 *   and submit it as such, hence with the caching there is only the one copy in memory. If one user happens to see a profile differently, a different profile copy will
 *   be saved for them (aka fragmented) but it will be visible to only them when they retrieve their existing feedback submissions. This in part thwarts a rogue client
 *   who may be hoping to upload offensive profile info or even malicious URL redirects for everyone else to see.
 *
 * - Purely sticking browsed item profiles into a cache and retrieving them isn't the perfect solution though, because:
 *   a) The JavaScript browsed item handlers may erroneously report different data if the web page hasn't fully loaded, eg. tags for photos may be retrieved as part of
 *      a separate request; if the request is slow or fails, the JavaScript handler may report that there are no photo tags, whereas other browsers might have no
 *      such problems and report a different profile including all of the tags.
 *   b) Item profiles can be updated over time.
 *
 *   With these in mind, I implemented a housekeeping feature in this class which attempts to detect the 'dominant' item profile based on a sample of the most recent
 *   feedback for each item. If one is found, all other instances of the item profile are updated to the dominant profile. There must be at least
 *   ItemProfileFeedbackDefragmentationSampleSize feedback submissions for an item to be processed for this, and furthermore, the sample will only consider one feedback
 *   profile per IP address, to make it more difficult for a single rogue user to systematically force a dominant profile of their choice for any item. This system
 *   may still be vulnerable to abuse via coordinated attack over multiple IP addresses, but it would take more effort than it's worth especially if the
 *   ItemProfileFeedbackDefragmentationSampleSize and ItemProfileFeedbackDefragmentationThreshold (the tipping point for an item profile to be considered dominant)
 *   are set high enough. The drawback of setting these parameters too high is that the merge task becomes too conservative and will not update items having a small
 *   amount of feedback or where there hasn't been much feedback since an item profile has been updated on a website.
 *
 * - As a final part of its process the housekeeping task also cleans out unused item profiles from the cache.
 *
 * - The cache is a cache and should not be used by any part of the server to assume that it is providing the 'authoritative' or canonical copy of a FeedbackItem,
 *   perhaps on which parts of the app might wish to synchronize, ie. much like what can be done with FeedbactoryUserAccount when using getAccountByID.
 *   I could change this later if it proved to be worthwhile, eg. helped simplify other aspects of the feedback handling.
 */

package com.feedbactory.server.feedback;


import com.feedbactory.server.core.FeedbactoryServerConstants;
import com.feedbactory.server.core.MutableInteger;
import com.feedbactory.server.core.TimeCache;
import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.log.SystemLogLevel;
import com.feedbactory.server.useraccount.FeedbactoryUserAccount;
import com.feedbactory.server.useraccount.FeedbactoryUserAccountView;
import com.feedbactory.server.useraccount.UserAccountManager;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.feedback.FeedbackItem;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import com.feedbactory.shared.feedback.FeedbackSubmission;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;


final public class FeedbackManager
{
   static final private int CachedItemProfileMapInitialCapacity = 10000;

   static final private String FeedbackDataFilename = "FeedbackData" + FeedbactoryServerConstants.DataFileExtension;

   static final private int MaximumFeedbackSubmissionsPerUserAccount = 2283;

   static final private int ItemProfileFeedbackDefragmentationSampleSize = 17;
   static final private int ItemProfileFeedbackDefragmentationThreshold = 13;

   static final private long HousekeepingTaskFrequencyMilliseconds = TimeUnit.MINUTES.toMillis(68);

   final private UserAccountManager userAccountManager;
   final private FeedbackCategoryRegistry feedbackCategoryRegistry;

   final private Map<FeedbactoryUserAccount, Map<FeedbackItem, ItemProfileFeedbackSubmission>> feedbackByUserAccount = new ConcurrentHashMap<>(UserAccountManager.AccountCollectionInitialCapacity, 0.75f, FeedbactoryServerConstants.ServerConcurrency);

   final private ConcurrentHashMap<FeedbackItemProfile, CachedFeedbackItemProfile> cachedFeedbackItemProfiles = new ConcurrentHashMap<>(CachedItemProfileMapInitialCapacity, 0.75f, FeedbactoryServerConstants.ServerConcurrency);

   final private HousekeepingTask housekeepingTask = new HousekeepingTask();


   public FeedbackManager(final UserAccountManager userAccountManager)
   {
      this.userAccountManager = userAccountManager;
      feedbackCategoryRegistry = new FeedbackCategoryRegistry(this, userAccountManager);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final public class FeedbackManagerMetrics
   {
      final public boolean isHousekeepingEnabled;
      final public int housekeepingFrequencyMinutes;
      final public long housekeepingLastRunStartTime;

      final public int feedbackSubmissions;
      final public int spreadOfItems;
      final public int spreadOfAccounts;

      final public int cachedItemProfiles;

      final public List<FeedbackCategory> registeredFeedbackCategories;


      private FeedbackManagerMetrics(final boolean isHousekeepingEnabled, final long housekeepingLastRunStartTime,
                                     final int feedbackSubmissions, final int spreadOfItems, final int spreadOfAccounts, final int cachedItemProfiles,
                                     final List<FeedbackCategory> registeredFeedbackCategories)
      {
         this.isHousekeepingEnabled = isHousekeepingEnabled;
         this.housekeepingFrequencyMinutes = (int) TimeUnit.MILLISECONDS.toMinutes(HousekeepingTaskFrequencyMilliseconds);
         this.housekeepingLastRunStartTime = housekeepingLastRunStartTime;

         this.feedbackSubmissions = feedbackSubmissions;
         this.spreadOfItems = spreadOfItems;
         this.spreadOfAccounts = spreadOfAccounts;

         this.cachedItemProfiles = cachedItemProfiles;

         this.registeredFeedbackCategories = registeredFeedbackCategories;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class CachedFeedbackItemProfile
   {
      final private FeedbackItemProfile itemProfile;
      final private long creationTime;


      private CachedFeedbackItemProfile(final FeedbackItemProfile itemProfile, final long creationTime)
      {
         this.itemProfile = itemProfile;
         this.creationTime = creationTime;
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

      /* This variable is written by the housekeeping thread, and read by the owner thread of the account manager.
       * At the moment there are no other metrics variables with which the housekeeping start time needs to be written atomically,
       * so marking it as volatile rather than using locking is fine.
       */
      volatile private long lastRunStartTime = FeedbactoryConstants.NoTime;


      @Override
      final public Thread newThread(final Runnable runnable)
      {
         final Thread thread = new Thread(runnable, "Feedback manager housekeeping task");
         thread.setDaemon(true);
         return thread;
      }


      @Override
      final public void run()
      {
         lastRunStartTime = TimeCache.getCurrentTimeMilliseconds();

         try
         {
            mergeFragmentedItemProfiles();
         }
         catch (final Exception anyException)
         {
            /* Exception handling provided for -any- exception, since any exceptions will otherwise be captured
             * by the enclosing FutureTask that is generated when this Runnable is submitted to ScheduledExecutorService.scheduleAtFixedRate().
             * Unhandled exceptions would also prevent further scheduleAtFixedRate() invocations from running.
             */
            FeedbactoryLogger.logSystemEvent(SystemLogLevel.ApplicationError, getClass(), "Feedback manager housekeeping task failed", anyException);
         }
      }


      private void mergeFragmentedItemProfiles()
      {
         for (final FeedbackCategoryHandler feedbackCategoryHandler : feedbackCategoryRegistry.getRegisteredHandlers())
            mergeFragmentedItemProfiles(feedbackCategoryHandler);
      }


      private void mergeFragmentedItemProfiles(final FeedbackCategoryHandler feedbackCategoryHandler)
      {
         final FeedbackCategoryManager feedbackCategoryManager = feedbackCategoryHandler.getCategoryManager();
         final Set<FeedbackItemProfile> feedbackCategoryItemProfiles = new HashSet<>(cachedFeedbackItemProfiles.size());

         /* Capture the time immediately prior to taking the snapshot of the feedback category's submissions.
          * This timestamp guards against the possibility of new submission profiles received during the housekeeping process
          * from being removed from the cache, as described before the final process below.
          * I could also use the volatile lastRunStartTime but it would be a hacky overload of its intended purpose (admin console metrics).
          */
         final long snapshotCreationTime = TimeCache.getCurrentTimeMilliseconds();

         Map<FeedbactoryUserAccount, ItemProfileFeedbackSubmission> itemFeedbackSubmissions;
         Map<FeedbactoryUserAccount, ItemProfileFeedbackSubmission> mostRecentItemSubmissions;

         for (final FeedbackItem feedbackItem : feedbackCategoryManager.getFeedbackItems())
         {
            // Get a snapshot of all of the feedbackCategoryManager's feedback submissions for the item.
            itemFeedbackSubmissions = feedbackCategoryManager.getItemFeedbackSubmissions(feedbackItem);

            /* Set aside a sample of the most recent feedback submissions.
             * The attached user account information is used to prevent a sample containing more than one
             * submission for any IP address, effectively making it more difficult for a malicious party
             * to flood an item with erroneous item profiles from different user accounts in an attempt
             * to force it to adopt some item profile.
             */
            mostRecentItemSubmissions = getMostRecentNItemSubmissions(itemFeedbackSubmissions);
            if (mostRecentItemSubmissions.size() >= ItemProfileFeedbackDefragmentationSampleSize)
            {
               final FeedbackItemProfile dominantItemProfile = getMostRecentDominantItemProfile(mostRecentItemSubmissions.values());
               if (dominantItemProfile != null)
               {
                  /* Update both the live feedback database AND the snapshot collection with the dominant item profile.
                   * Updating the snapshot collection is for the benefit of the cache cleaner task, which runs at the end
                   * of this process.
                   */
                  updateToMostRecentDominantItemProfile(feedbackCategoryManager, itemFeedbackSubmissions, dominantItemProfile);
               }
            }

            for (final ItemProfileFeedbackSubmission itemFeedbackSubmissionProfile : itemFeedbackSubmissions.values())
               feedbackCategoryItemProfiles.add(itemFeedbackSubmissionProfile.getItemProfile());
         }

         /* This task is useful for cleaning out the cache, which can otherwise accumulate item profiles that no longer have feedback attached.
          * In theory a malicious party could progressively flood the server cache with different item profile submissions over time,
          * so this is a handy task to have.
          * 
          * If not for the safeguard timestamp above taken at the time prior to extracting the feedback category snapshot, it would be possible due to
          * concurrency issues that this method would actually remove an active feedback profile, under these conditions:
          * 1. The housekeeping task starts and a snapshot of all feedback for a category is obtained (above) from the feedback category manager.
          * 2. A user submits feedback for an item which previously had no feedback attached, ie. no existing item profile for it at the
          *    time of the previous step.
          * 3. Since the item is new and has only one feedback submission it remains unaffected by the merge item profiles task, ie. the
          *    snapshot remains as it was at the time.
          * 4. This final housekeeping task runs and finds no record of the cached item profile in the snapshot of the data gathered previously,
          *    so the item profile is removed from the cache.
          *
          * If another user then submits feedback for the same item with the same item profile (as expected), the profile will then be recreated
          * in the cache but it will not of course be a memory duplicate of the item profile attached to the feedback from step 2.
          *
          * This would not be much of a problem since a) the housekeeping task should not run frequently, so the expected number of affected cases
          * would be very small if any, and b) future runs of the housekeeping task would merge item profile objects once there is enough feedback
          * for the item ID to detect a dominant item profile.
          *
          * Still, the item may never receive enough submissions to reach the merge threshold. Also it's easy enough to guard against the above scenario
          * by providing a safeguard timestamp and ensuring that the process below does not remove any cached item profiles that have been created
          * since the housekeeping snapshot has been taken.
          */
         removeUnusedCachedItemProfiles(feedbackCategoryHandler.getCategory(), snapshotCreationTime, feedbackCategoryItemProfiles);
      }


      private Map<FeedbactoryUserAccount, ItemProfileFeedbackSubmission> getMostRecentNItemSubmissions(final Map<FeedbactoryUserAccount, ItemProfileFeedbackSubmission> itemFeedbackSubmissions)
      {
         // Covers the do-while loop below.
         assert ItemProfileFeedbackDefragmentationSampleSize > 0;

         if (itemFeedbackSubmissions.size() < ItemProfileFeedbackDefragmentationSampleSize)
            return Collections.emptyMap();

         final Set<InetAddress> submissionIPAddresses = new HashSet<>(ItemProfileFeedbackDefragmentationSampleSize);
         final Map<FeedbactoryUserAccount, ItemProfileFeedbackSubmission> mostRecentFeedbackSubmissions = new HashMap<>(ItemProfileFeedbackDefragmentationSampleSize);
         ItemProfileFeedbackSubmission mostRecentFeedbackSubmission;
         FeedbactoryUserAccount mostRecentFeedbackSubmissionAccount;

         ItemProfileFeedbackSubmission feedbackSubmission;
         FeedbactoryUserAccount feedbackSubmissionAccount;

         do
         {
            mostRecentFeedbackSubmission = null;
            mostRecentFeedbackSubmissionAccount = null;

            for (final Entry<FeedbactoryUserAccount, ItemProfileFeedbackSubmission> itemProfileFeedbackSubmission : itemFeedbackSubmissions.entrySet())
            {
               feedbackSubmissionAccount = itemProfileFeedbackSubmission.getKey();
               feedbackSubmission = itemProfileFeedbackSubmission.getValue();

               if (((mostRecentFeedbackSubmission == null) || (feedbackSubmission.getSubmissionTime() > mostRecentFeedbackSubmission.getSubmissionTime())) &&
                   (! submissionIPAddresses.contains(feedbackSubmissionAccount.getLastAuthenticatedIPAddress())))
               {
                  mostRecentFeedbackSubmission = feedbackSubmission;
                  mostRecentFeedbackSubmissionAccount = feedbackSubmissionAccount;
               }
            }

            if (mostRecentFeedbackSubmission != null)
            {
               mostRecentFeedbackSubmissions.put(mostRecentFeedbackSubmissionAccount, mostRecentFeedbackSubmission);
               itemFeedbackSubmissions.remove(mostRecentFeedbackSubmissionAccount);

               // Not possible for the reference to be null, if mostRecentFeedbackSubmission has been set.
               submissionIPAddresses.add(mostRecentFeedbackSubmissionAccount.getLastAuthenticatedIPAddress());
            }
         }
         while ((mostRecentFeedbackSubmissions.size() < ItemProfileFeedbackDefragmentationSampleSize) && (! itemFeedbackSubmissions.isEmpty()) &&
                (mostRecentFeedbackSubmission != null));

         // Re-add the most recent submissions that were progressively removed during the search process.
         itemFeedbackSubmissions.putAll(mostRecentFeedbackSubmissions);

         return mostRecentFeedbackSubmissions;
      }


      private FeedbackItemProfile getMostRecentDominantItemProfile(final Collection<ItemProfileFeedbackSubmission> mostRecentItemSubmissions)
      {
         /* As the method name suggests, the target is a dominant item profile, ie. one having a submission frequency of at least a little
          * more than half of the most recent submissions. This algorithm assumes that the parameters are initialised to values that
          * support finding an item profile that represents the majority of submissions, if it exists.
          */
         assert (((float) ItemProfileFeedbackDefragmentationThreshold) / ItemProfileFeedbackDefragmentationSampleSize) > 0.5f;

         // Expect only one version of an item profile on average.
         final Map<FeedbackItemProfile, MutableInteger> itemProfileCounts = new HashMap<>(1);
         MutableInteger itemProfileCount;

         for (final ItemProfileFeedbackSubmission feedbackSubmissionProfile : mostRecentItemSubmissions)
         {
            itemProfileCount = itemProfileCounts.get(feedbackSubmissionProfile.getItemProfile());
            if (itemProfileCount == null)
               itemProfileCounts.put(feedbackSubmissionProfile.getItemProfile(), new MutableInteger(1));
            else
            {
               itemProfileCount.increment();

               // See the assertion comments above; if a profile count is beyond the threshold, it must be present in the majority of submissions for the item.
               if (itemProfileCount.get() >= ItemProfileFeedbackDefragmentationThreshold)
                  return feedbackSubmissionProfile.getItemProfile();
            }
         }

         return null;
      }


      private void updateToMostRecentDominantItemProfile(final FeedbackCategoryManager feedbackCategoryManager,
                                                         final Map<FeedbactoryUserAccount, ItemProfileFeedbackSubmission> itemFeedbackSubmissions,
                                                         final FeedbackItemProfile dominantItemProfile)
      {
         final Iterator<Entry<FeedbactoryUserAccount, ItemProfileFeedbackSubmission>> itemFeedbackSubmissionIterator = itemFeedbackSubmissions.entrySet().iterator();
         Entry<FeedbactoryUserAccount, ItemProfileFeedbackSubmission> itemFeedbackSubmissionsEntry;

         FeedbactoryUserAccount account;
         ItemProfileFeedbackSubmission snapshotFeedbackSubmission;
         Map<FeedbackItem, ItemProfileFeedbackSubmission> currentAccountFeedbackSubmissions;
         ItemProfileFeedbackSubmission currentFeedbackSubmission;
         ItemProfileFeedbackSubmission updatedFeedbackSubmission;

         while (itemFeedbackSubmissionIterator.hasNext())
         {
            itemFeedbackSubmissionsEntry = itemFeedbackSubmissionIterator.next();

            account = itemFeedbackSubmissionsEntry.getKey();
            snapshotFeedbackSubmission = itemFeedbackSubmissionsEntry.getValue();

            /* Summary of the process actions to this point:
             * - A snapshot of all of the submissions for a feedback item ID was taken from that item's feedback category manager.
             * - The submissions for that item were examined to find a 'dominant' item profile from those that had most recently been submitted.
             *   To reduce fragmentation and ensure consistency across the Feedbactory database, this dominant item profile now needs to replace
             *   any variant of the profile for that item ID. Note that the dominant item profile will have already been cached from the time that
             *   it was first submitted, so there's no extra work to be done there.
             * - Since the time of grabbing the snapshot of all feedback submissions for the item, doing the above processing, and arriving at this
             *   point, feedback submissions may have been added and removed so that the snapshot is slightly out of date.
             *
             * The steps from here:
             * - Merge the dominant item profile into all of the feedback submissions where the earlier snapshot of the submissions indicated that the
             *   attached item profile was different to the dominant item profile. For this case, I need to handle the following possibilities:
             *
             *   1. The feedback submission hasn't changed, so the update needs to go ahead.
             *   2. The feedback submission has since been changed and the item profile is still different to the dominant one, so the update needs to go ahead.
             *   3. The feedback submission has since been changed and the item profile has been updated to the dominant one, so no work needs to be done.
             *   4. The feedback submission has since been removed, so no work needs to be done.
             *
             * - The other cases to consider involve the item profile attached to the earlier snapshot of the feedback submission being identical to
             *   the dominant item profile. Of course this may no longer be the case on the live data - the user may have since changed the feedback, uploading
             *   a variant of the profile, among other possibilities - however all of these possibilities can be dealt with by simply leaving them as they are
             *   for this run of the process. It's very unlikely that any of these cases will occur since the capturing of the earlier snapshot of the submissions,
             *   and even less likely that the same things will happen for the very same submissions (if they are still present) during the next run of
             *   the merge process. So, leave them (possibly) temporarily fragmented until the next run, where the dominant profile will again be extracted
             *   and -then- the changed feedback submission profile will be different to that and therefore updated.
             *   The incentive to apply this lazier policy is to avoid the hit of unconditionally re-grabbing the lock for EVERY user account feedback submission
             *   in the entire database - as the database grows much larger it would be a huge amount of needless locking.
             *
             * - Finally, this method also mutates the snapshot collection as feedback submission profiles are updated, in preparation for the
             *   cache cleaner task which will run immediately afterwards.
             */

            /* Without this first check, the process would still work correctly and possibly catch more cases that need to be updated eventually,
             * but there'd be a significant performance penalty.
             * The reference equality is used because the dominant item profile was extracted from the snapshot.
             */
            if (snapshotFeedbackSubmission.getItemProfile() != dominantItemProfile)
            {
               synchronized (account)
               {
                  currentAccountFeedbackSubmissions = feedbackByUserAccount.get(account);

                  // Check to see whether the user still has any feedback submissions. Case 4 in the comments above.
                  if (currentAccountFeedbackSubmissions != null)
                  {
                     currentFeedbackSubmission = currentAccountFeedbackSubmissions.get(dominantItemProfile.getItem());

                     // Check that the user still has a feedback submission for the target item. Case 4 in the comments above.
                     if (currentFeedbackSubmission != null)
                     {
                        /* Referring to the comments above, for cases 1 & 2 the replaced item profile returned here from the feedback category manager
                         * (always the live ItemProfileFeedbackSubmission) will have changed from that stored in this parent feedback manager's database,
                         * which will need to be updated at a later step.
                         * For case 3, the feedback category manager will not update the object and the live ItemProfileFeedbackSubmission returned
                         * will already be reference equal to the object stored in this parent feedback manager's database - no work to be done.
                         */
                        updatedFeedbackSubmission = feedbackCategoryManager.replaceItemProfile(account, dominantItemProfile);

                        /* While a lock on the user account is being held, it should not be possible for a feedback submission
                         * to be recorded here in the parent feedback manager and not within the feedbackCategoryManager.
                         * It would indicate a corrupted feedback database.
                         */
                        assert (updatedFeedbackSubmission != null);

                        /* Avoid extra work for case 3: everything's already up to date, the submission profile here does not need to be updated.
                         * Note the reference equality check, which can apply since item profiles are cached as they are submitted or restored.
                         */
                        if (currentFeedbackSubmission != updatedFeedbackSubmission)
                           currentAccountFeedbackSubmissions.put(dominantItemProfile.getItem(), updatedFeedbackSubmission);

                        // Need to update the snapshot item profile, as it will be checked later by the cache cleaner.
                        itemFeedbackSubmissionsEntry.setValue(updatedFeedbackSubmission);
                     }
                     else // Remove the submission from the snapshot, as it will be checked later by the cache cleaner.
                        itemFeedbackSubmissionIterator.remove();
                  }
                  else // Remove the submission from the snapshot, as it will be checked later by the cache cleaner.
                     itemFeedbackSubmissionIterator.remove();
               }
            }
         }
      }


      private void removeUnusedCachedItemProfiles(final FeedbackCategory feedbackCategory, final long snapshotCreationTime, final Set<FeedbackItemProfile> feedbackCategoryItemProfiles)
      {
         final Iterator<Entry<FeedbackItemProfile, CachedFeedbackItemProfile>> cachedFeedbackItemProfilesIterator = cachedFeedbackItemProfiles.entrySet().iterator();
         Entry<FeedbackItemProfile, CachedFeedbackItemProfile> cachedFeedbackItemProfileEntry;

         while (cachedFeedbackItemProfilesIterator.hasNext())
         {
            cachedFeedbackItemProfileEntry = cachedFeedbackItemProfilesIterator.next();

            if ((cachedFeedbackItemProfileEntry.getKey().getFeedbackCategory() == feedbackCategory) &&
                (cachedFeedbackItemProfileEntry.getValue().creationTime < snapshotCreationTime) &&
                (! feedbackCategoryItemProfiles.contains(cachedFeedbackItemProfileEntry.getKey())))
               cachedFeedbackItemProfilesIterator.remove();
         }
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private boolean isHousekeepingStarted()
      {
         return (executor != null);
      }


      private void start()
      {
         if (isHousekeepingStarted())
            throw new IllegalStateException("Housekeeping task has already been started.");

         executor = new ScheduledThreadPoolExecutor(1, this);
         executor.setKeepAliveTime(10, TimeUnit.SECONDS);
         executor.allowCoreThreadTimeOut(true);

         executor.scheduleAtFixedRate(this, 0, HousekeepingTaskFrequencyMilliseconds, TimeUnit.MILLISECONDS);
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


   private FeedbackItemProfile cacheFeedbackItemProfile(final FeedbackItemProfile feedbackItemProfile)
   {
      CachedFeedbackItemProfile cachedFeedbackItemProfile = cachedFeedbackItemProfiles.get(feedbackItemProfile);

      if (cachedFeedbackItemProfile == null)
      {
         cachedFeedbackItemProfile = new CachedFeedbackItemProfile(feedbackItemProfile, TimeCache.getCurrentTimeMilliseconds());
         final CachedFeedbackItemProfile existingFeedbackItemProfile = cachedFeedbackItemProfiles.putIfAbsent(feedbackItemProfile, cachedFeedbackItemProfile);
         if (existingFeedbackItemProfile != null)
            cachedFeedbackItemProfile = existingFeedbackItemProfile;
      }

      return cachedFeedbackItemProfile.itemProfile;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private ItemProfileFeedbackSubmission handleAddFeedbackSubmission(final FeedbactoryUserAccount userAccount, final FeedbackItemProfile itemProfile, final FeedbackSubmission feedbackSubmission)
   {
      synchronized (userAccount)
      {
         Map<FeedbackItem, ItemProfileFeedbackSubmission> userFeedbackSubmissions = feedbackByUserAccount.get(userAccount);

         /* Don't allow the number of feedback submissions per user account to exceed MaximumFeedbackSubmissionsPerUserAccount, however if this figure is
          * later adjusted to a lower number and the user's existing feedback submissions exceeds it, at least let them replace an existing submission.
          */
         if (userFeedbackSubmissions == null)
         {
            userFeedbackSubmissions = new HashMap<>(1);
            feedbackByUserAccount.put(userAccount, userFeedbackSubmissions);
         }
         else if ((userFeedbackSubmissions.size() >= MaximumFeedbackSubmissionsPerUserAccount) && (! userFeedbackSubmissions.containsKey(itemProfile.getItem())))
            return null;

         final FeedbackItemProfile cachedFeedbackItemProfile = cacheFeedbackItemProfile(itemProfile);
         final FeedbackItem cachedFeedbackItem = cachedFeedbackItemProfile.getItem();

         final FeedbackCategoryManager categoryManager = feedbackCategoryRegistry.getFeedbackCategoryHandler(cachedFeedbackItem.getFeedbackCategory()).getCategoryManager();
         final ItemProfileFeedbackSubmission newSubmission = categoryManager.createItemProfileFeedbackSubmission(cachedFeedbackItemProfile, feedbackSubmission, TimeCache.getCurrentTimeMilliseconds());
         userFeedbackSubmissions.put(cachedFeedbackItem, newSubmission);

         return newSubmission;
      }
   }


   private FeedbackSubmission handleGetFeedbackSubmission(final FeedbactoryUserAccount userAccount, final FeedbackItem item)
   {
      synchronized (userAccount)
      {
         final Map<FeedbackItem, ItemProfileFeedbackSubmission> userFeedbackSubmissions = feedbackByUserAccount.get(userAccount);

         if (userFeedbackSubmissions != null)
         {
            final ItemProfileFeedbackSubmission itemProfileFeedbackSubmission = userFeedbackSubmissions.get(item);

            if (itemProfileFeedbackSubmission != null)
               return itemProfileFeedbackSubmission.getFeedbackSubmission();
         }

         return null;
      }
   }


   private boolean handleRemoveFeedbackSubmission(final FeedbactoryUserAccount userAccount, final FeedbackItem item)
   {
      synchronized (userAccount)
      {
         final Map<FeedbackItem, ItemProfileFeedbackSubmission> userFeedbackSubmissions = feedbackByUserAccount.get(userAccount);

         if (userFeedbackSubmissions != null)
         {
            if (userFeedbackSubmissions.remove(item) != null)
            {
               if (userFeedbackSubmissions.isEmpty())
                  feedbackByUserAccount.remove(userAccount);

               return true;
            }
         }

         return false;
      }
   }


   private Collection<ItemProfileFeedbackSubmission> handleGetAllUserFeedbackSubmissions(final FeedbactoryUserAccount userAccount)
   {
      synchronized (userAccount)
      {
         final Map<FeedbackItem, ItemProfileFeedbackSubmission> userFeedbackSubmissions = feedbackByUserAccount.get(userAccount);

         /* For efficiency, a read-only view of the user's feedback submissions is returned.
          * For this view to be threadsafe to the caller, the caller must have a lock on the user account.
          */
         return (userFeedbackSubmissions != null) ? Collections.unmodifiableCollection(userFeedbackSubmissions.values()) : Collections.<ItemProfileFeedbackSubmission>emptyList();
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSaveCheckpoint(final Path checkpointPath) throws IOException
   {
      /* Allow feedback category managers the chance to perform any tasks prior to the parent feedback manager saving a checkpoint.
       * Note that at this point the category managers cannot expect to save (and later successfully restore) any critical data that links back to records in the parent
       * feedback manager, since feedback operations are still active at the same time as this process. It's possible for example that immediately after this
       * step has been processed, all feedback for an item has been removed.
       *
       * These pre- & post-checkpoint steps are designed to allow feedback category managers to perform any persistence of other data that might be
       * specific to the feedback category.
       */
      feedbackCategoryPreCheckpointSave(checkpointPath);

      // Checkpoint the data from this class - the parent feedback manager, containing the primary records.
      saveFeedbackManagerCheckpointData(checkpointPath);

      /* Allow feedback category managers the chance to perform any tasks after the parent feedback manager has saved a checkpoint.
       * Again note that at this point the category managers cannot expect to save (and later successfully restore) any critical data that links back to records in the parent
       * feedback manager.
       */
      feedbackCategoryPostCheckpointSave(checkpointPath);
   }


   private void feedbackCategoryPreCheckpointSave(final Path checkpointPath) throws IOException
   {
      for (final FeedbackCategoryHandler handler : feedbackCategoryRegistry.getRegisteredHandlers())
         handler.getCategoryManager().preCheckpointSave(checkpointPath);
   }


   private void saveFeedbackManagerCheckpointData(final Path checkpointPath) throws IOException
   {
      final File feedbackDataFile = checkpointPath.resolve(FeedbackDataFilename).toFile();

      try
      (
         final DataOutputStream feedbackDataStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(feedbackDataFile)));
      )
      {
         Map<FeedbackItem, ItemProfileFeedbackSubmission> userAccountFeedback;
         FeedbackCategory targetServiceCategory;
         FeedbackCategoryManager targetServiceCategoryManager;

         for (final Entry<FeedbactoryUserAccount, Map<FeedbackItem, ItemProfileFeedbackSubmission>> feedbackByUserAccountEntry : feedbackByUserAccount.entrySet())
         {
            synchronized (feedbackByUserAccountEntry.getKey())
            {
               userAccountFeedback = feedbackByUserAccountEntry.getValue();

               feedbackDataStream.writeInt(feedbackByUserAccountEntry.getKey().getID().intValue());
               feedbackDataStream.writeInt(userAccountFeedback.size());

               for (final ItemProfileFeedbackSubmission userAccountFeedbackSubmission : userAccountFeedback.values())
               {
                  targetServiceCategory = userAccountFeedbackSubmission.getItemProfile().getFeedbackCategory();
                  feedbackDataStream.writeShort(targetServiceCategory.value);

                  targetServiceCategoryManager = feedbackCategoryRegistry.getFeedbackCategoryHandler(targetServiceCategory).getCategoryManager();

                  targetServiceCategoryManager.writeFeedbackItemProfile(userAccountFeedbackSubmission.getItemProfile(), feedbackDataStream);
                  targetServiceCategoryManager.writeFeedbackSubmission(userAccountFeedbackSubmission.getFeedbackSubmission(), feedbackDataStream);
                  feedbackDataStream.writeLong(userAccountFeedbackSubmission.getSubmissionTime());
               }
            }
         }

         // Finalise the output with a -1 for the EOF, which is an invalid user account ID.
         feedbackDataStream.writeInt(-1);
      }
   }


   private void feedbackCategoryPostCheckpointSave(final Path checkpointPath) throws IOException
   {
      for (final FeedbackCategoryHandler handler : feedbackCategoryRegistry.getRegisteredHandlers())
         handler.getCategoryManager().postCheckpointSave(checkpointPath);
   }


   private void handleRestoreFromCheckpoint(final Path checkpointPath) throws IOException
   {
      clearFeedbackCategoryCheckpointData();

      feedbackCategoryPreCheckpointRestore(checkpointPath);

      restoreFeedbackManagerCheckpointData(checkpointPath);

      feedbackCategoryPostCheckpointRestore(checkpointPath);
   }


   private void clearFeedbackCategoryCheckpointData()
   {
      for (final FeedbackCategoryHandler handler : feedbackCategoryRegistry.getRegisteredHandlers())
         handler.getCategoryManager().clearItemFeedbackSubmissions();
   }


   private void feedbackCategoryPreCheckpointRestore(final Path checkpointPath) throws IOException
   {
      for (final FeedbackCategoryHandler handler : feedbackCategoryRegistry.getRegisteredHandlers())
         handler.getCategoryManager().preCheckpointRestore(checkpointPath);
   }


   private void restoreFeedbackManagerCheckpointData(final Path checkpointPath) throws IOException
   {
      feedbackByUserAccount.clear();
      cachedFeedbackItemProfiles.clear();

      final File feedbackDataFile = checkpointPath.resolve(FeedbackDataFilename).toFile();

      try
      (
         final DataInputStream feedbackDataStream = new DataInputStream(new BufferedInputStream(new FileInputStream(feedbackDataFile)));
      )
      {
         int userAccountID;
         FeedbactoryUserAccount userAccount;
         int numberOfUserAccountSubmissions;
         Map<FeedbackItem, ItemProfileFeedbackSubmission> userAccountFeedback;
         short feedbackCategoryValue;
         FeedbackCategory feedbackCategory;
         FeedbackCategoryManager feedbackCategoryManager;
         FeedbackItemProfile itemProfile;
         FeedbackSubmission feedbackSubmission;
         long submissionTime;
         ItemProfileFeedbackSubmission restoredItemProfileFeedbackSubmission;

         while ((userAccountID = feedbackDataStream.readInt()) != -1)
         {
            userAccount = userAccountManager.getAccountByID(userAccountID);

            // Ensure the visibility of the feedback data to subsequent threads. Also will throw an exception if there is no such user account.
            synchronized (userAccount)
            {
               numberOfUserAccountSubmissions = feedbackDataStream.readInt();
               userAccountFeedback = new HashMap<>(numberOfUserAccountSubmissions);

               for (int submissionIndex = 0; submissionIndex < numberOfUserAccountSubmissions; submissionIndex ++)
               {
                  feedbackCategoryValue = feedbackDataStream.readShort();
                  feedbackCategory = FeedbackCategory.fromValue(feedbackCategoryValue);
                  if (feedbackCategory == null)
                     throw new IllegalArgumentException("Invalid feedback category value: " + feedbackCategoryValue);

                  feedbackCategoryManager = feedbackCategoryRegistry.getFeedbackCategoryHandler(feedbackCategory).getCategoryManager();

                  itemProfile = feedbackCategoryManager.readFeedbackItemProfile(feedbackDataStream);
                  itemProfile = cacheFeedbackItemProfile(itemProfile);

                  feedbackSubmission = feedbackCategoryManager.readFeedbackSubmission(itemProfile.getItem(), feedbackDataStream);
                  submissionTime = feedbackDataStream.readLong();

                  restoredItemProfileFeedbackSubmission = feedbackCategoryManager.createItemProfileFeedbackSubmission(itemProfile, feedbackSubmission, submissionTime);

                  userAccountFeedback.put(itemProfile.getItem(), restoredItemProfileFeedbackSubmission);

                  feedbackCategoryManager.restoreItemFeedbackSubmission(userAccount, restoredItemProfileFeedbackSubmission);
               }
            }

            feedbackByUserAccount.put(userAccount, userAccountFeedback);
         }
      }
   }


   private void feedbackCategoryPostCheckpointRestore(final Path checkpointPath) throws IOException
   {
      for (final FeedbackCategoryHandler handler : feedbackCategoryRegistry.getRegisteredHandlers())
         handler.getCategoryManager().postCheckpointRestore(checkpointPath);
   }


   private void handleStartHousekeeping()
   {
      housekeepingTask.start();

      for (final FeedbackCategoryHandler handler : feedbackCategoryRegistry.getRegisteredHandlers())
         handler.getCategoryManager().startHousekeeping();
   }


   private void handleShutdownHousekeeping() throws InterruptedException
   {
      housekeepingTask.shutdown();

      for (final FeedbackCategoryHandler handler : feedbackCategoryRegistry.getRegisteredHandlers())
         handler.getCategoryManager().shutdownHousekeeping();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private FeedbackManagerMetrics handleGetFeedbackManagerMetrics()
   {
      final int numberOfCachedFeedbackItemProfiles = cachedFeedbackItemProfiles.size();

      int feedbackSubmissions = 0;
      final Set<FeedbackItem> spreadOfItems = new HashSet<>(numberOfCachedFeedbackItemProfiles);
      int spreadOfAccounts = 0;

      for (final Entry<FeedbactoryUserAccount, Map<FeedbackItem, ItemProfileFeedbackSubmission>> accountFeedbackEntry : feedbackByUserAccount.entrySet())
      {
         spreadOfAccounts ++;

         synchronized (accountFeedbackEntry.getKey())
         {
            for (final Entry<FeedbackItem, ItemProfileFeedbackSubmission> accountFeedbackSubmissionEntry : accountFeedbackEntry.getValue().entrySet())
            {
               spreadOfItems.add(accountFeedbackSubmissionEntry.getKey());
               feedbackSubmissions ++;
            }
         }
      }

      final List<FeedbackCategory> registeredFeedbackCategories = new LinkedList<>();

      for (final FeedbackCategoryHandler handler : feedbackCategoryRegistry.getRegisteredHandlers())
         registeredFeedbackCategories.add(handler.getCategory());

      return new FeedbackManagerMetrics(housekeepingTask.isHousekeepingStarted(), housekeepingTask.lastRunStartTime,
                                        feedbackSubmissions, spreadOfItems.size(), spreadOfAccounts, numberOfCachedFeedbackItemProfiles, registeredFeedbackCategories);
   }


   private List<ItemProfileFeedbackSubmission> handleGetAllUserFeedbackSubmissions(final FeedbactoryUserAccountView userAccountView)
   {
      final FeedbactoryUserAccount userAccount = userAccountManager.getAccountByID(userAccountView.userAccountID);

      synchronized (userAccount)
      {
         final Map<FeedbackItem, ItemProfileFeedbackSubmission> userFeedbackSubmissions = feedbackByUserAccount.get(userAccount);

         if (userFeedbackSubmissions != null)
            return new ArrayList<>(userFeedbackSubmissions.values());
         else
            return Collections.emptyList();
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final FeedbackCategoryRegistry getFeedbackCategoryRegistry()
   {
      return feedbackCategoryRegistry;
   }


   final Collection<ItemProfileFeedbackSubmission> getAllUserFeedbackSubmissions(final FeedbactoryUserAccount userAccount)
   {
      return handleGetAllUserFeedbackSubmissions(userAccount);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public ItemProfileFeedbackSubmission addFeedbackSubmission(final FeedbactoryUserAccount userAccount, final FeedbackItemProfile itemProfile, final FeedbackSubmission feedbackSubmission)
   {
      return handleAddFeedbackSubmission(userAccount, itemProfile, feedbackSubmission);
   }


   final public FeedbackSubmission getFeedbackSubmission(final FeedbactoryUserAccount userAccount, final FeedbackItem item)
   {
      return handleGetFeedbackSubmission(userAccount, item);
   }


   final public boolean removeFeedbackSubmission(final FeedbactoryUserAccount userAccount, final FeedbackItem item)
   {
      return handleRemoveFeedbackSubmission(userAccount, item);
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


   /****************************************************************************
    *
    ***************************************************************************/


   final public FeedbackManagerMetrics getFeedbackManagerMetrics()
   {
      return handleGetFeedbackManagerMetrics();
   }


   final public List<ItemProfileFeedbackSubmission> getAllUserFeedbackSubmissions(final FeedbactoryUserAccountView userAccount)
   {
      return handleGetAllUserFeedbackSubmissions(userAccount);
   }


   final public String processFeedbackCategoryConsoleCommand(final FeedbackCategory feedbackCategory, final String[] arguments)
   {
      return feedbackCategoryRegistry.getFeedbackCategoryHandler(feedbackCategory).processConsoleCommand(arguments);
   }
}