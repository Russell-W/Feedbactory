
package com.feedbactory.server.feedback;


import com.feedbactory.server.useraccount.FeedbactoryUserAccount;
import com.feedbactory.shared.feedback.FeedbackSubmission;
import com.feedbactory.shared.feedback.FeedbackItem;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;


public interface FeedbackCategoryManager
{
   public ItemProfileFeedbackSubmission createItemProfileFeedbackSubmission(final FeedbackItemProfile itemProfile, final FeedbackSubmission feedbackSubmission,
                                                                            final long submissionTime);
   public Set<FeedbackItem> getFeedbackItems();
   public Map<FeedbactoryUserAccount, ItemProfileFeedbackSubmission> getItemFeedbackSubmissions(final FeedbackItem item);
   public ItemProfileFeedbackSubmission replaceItemProfile(final FeedbactoryUserAccount account, final FeedbackItemProfile itemProfile);

   public FeedbackItem readFeedbackItem(final DataInputStream dataInputStream) throws IOException;
   public void writeFeedbackItem(final FeedbackItem item, final DataOutputStream dataOutputStream) throws IOException;
   public FeedbackItemProfile readFeedbackItemProfile(final DataInputStream dataInputStream) throws IOException;
   public void writeFeedbackItemProfile(final FeedbackItemProfile itemProfile, final DataOutputStream dataOutputStream) throws IOException;
   public FeedbackSubmission readFeedbackSubmission(final FeedbackItem item, final DataInputStream dataInputStream) throws IOException;
   public void writeFeedbackSubmission(final FeedbackSubmission feedbackSubmission, final DataOutputStream dataOutputStream) throws IOException;

   public void clearItemFeedbackSubmissions();
   public void preCheckpointRestore(final Path checkpointPath) throws IOException;
   public void restoreItemFeedbackSubmission(final FeedbactoryUserAccount userAccount, final ItemProfileFeedbackSubmission restoredItemProfileFeedbackSubmission);
   public void postCheckpointRestore(final Path checkpointPath) throws IOException;

   public void preCheckpointSave(final Path checkpointPath) throws IOException;
   public void postCheckpointSave(final Path checkpointPath) throws IOException;

   public void startHousekeeping();
   public void shutdownHousekeeping() throws InterruptedException;
}