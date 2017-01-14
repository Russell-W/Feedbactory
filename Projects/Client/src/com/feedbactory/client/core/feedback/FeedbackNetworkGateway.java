
package com.feedbactory.client.core.feedback;


import com.feedbactory.shared.feedback.FeedbackItemProfile;
import com.feedbactory.shared.feedback.FeedbackResultSummary;
import java.nio.ByteBuffer;


public interface FeedbackNetworkGateway
{
   public void processFeedbackHandshake(final ByteBuffer handshakeBuffer);
   public FeedbackItemProfile readFeedbackItemProfile(final ByteBuffer responseBuffer);
   public Object readFeedbackSubmissionSummary(final ByteBuffer responseBuffer);
   public FeedbackResultSummary readFeedbackResultSummary(final ByteBuffer responseBuffer);
}