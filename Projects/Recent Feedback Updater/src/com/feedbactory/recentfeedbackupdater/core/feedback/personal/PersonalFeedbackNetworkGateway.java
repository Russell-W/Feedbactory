/* Memos:
 * - Some samples taken almost verbatim from the Feedbactory client's PersonalFeedbackNetworkGateway class.
 */

package com.feedbactory.recentfeedbackupdater.core.feedback.personal;


import com.feedbactory.recentfeedbackupdater.core.network.NetworkGateway;
import com.feedbactory.recentfeedbackupdater.core.network.NetworkRequestStatus;
import com.feedbactory.recentfeedbackupdater.core.network.ProcessedRequestResult;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.feedback.FeedbackRequestType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackFeaturedPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackRequestType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.network.RequestGatewayIdentifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


final public class PersonalFeedbackNetworkGateway
{
   final private NetworkGateway networkGateway;


   public PersonalFeedbackNetworkGateway(final NetworkGateway networkGateway)
   {
      this.networkGateway = networkGateway;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private ProcessedRequestResult<List<PersonalFeedbackFeaturedPerson>> handleSendGetHotPhotographyFeedbackSample()
   {
      final ByteBuffer requestBuffer = ByteBuffer.allocate(NetworkGateway.MaximumRequestSizeBytes);

      writeFeedbackRequestHeader(requestBuffer);
      writeHotPhotographyFeedbackRequest(requestBuffer);

      requestBuffer.flip();

      final ProcessedRequestResult<ByteBuffer> response = networkGateway.sendNoSessionRequest(requestBuffer);
      if (response.requestStatus == NetworkRequestStatus.OK)
      {
         final List<PersonalFeedbackFeaturedPerson> hotPhotographyFeedbackSample = readFeaturedPeopleFeedbackSample(response.data);
         return new ProcessedRequestResult<>(response.requestStatus, hotPhotographyFeedbackSample);
      }
      else
         return new ProcessedRequestResult<>(response.requestStatus);
   }


   private void writeFeedbackRequestHeader(final ByteBuffer requestBuffer)
   {
      requestBuffer.put(RequestGatewayIdentifier.Feedback.value);
   }


   private void writeHotPhotographyFeedbackRequest(final ByteBuffer requestBuffer)
   {
      writeCriteriaFeedbackCategoryRequest(requestBuffer);
      writeCriteriaFeedbackGetHotFeedbackRequest(PersonalFeedbackCriteriaType.Photography, requestBuffer);
   }


   private void writeCriteriaFeedbackCategoryRequest(final ByteBuffer requestBuffer)
   {
      requestBuffer.put(FeedbackRequestType.FeedbackCategoryRequest.value);
      requestBuffer.putShort(FeedbackCategory.Personal.value);
   }


   private void writeCriteriaFeedbackGetHotFeedbackRequest(final PersonalFeedbackCriteriaType criteriaType, final ByteBuffer requestBuffer)
   {
      requestBuffer.put(PersonalFeedbackRequestType.GetHotFeedbackItemsSample.value);
      writeCriteriaFeedbackFeaturedItemsFilter(criteriaType, requestBuffer);
   }


   private void writeCriteriaFeedbackFeaturedItemsFilter(final PersonalFeedbackCriteriaType criteriaType, final ByteBuffer requestBuffer)
   {
      requestBuffer.put(criteriaType.value);

      // Filter websites - none specified.
      requestBuffer.putShort((short) 0);

      // Filter tags - none specified.
      requestBuffer.put((byte) 0);

      // Last retrieved item sort value - none specified.
      requestBuffer.putLong(FeedbactoryConstants.NoTime);
   }


   private List<PersonalFeedbackFeaturedPerson> readFeaturedPeopleFeedbackSample(final ByteBuffer responseBuffer)
   {
      final int featuredPeopleSampleSize = responseBuffer.getInt();

      if (featuredPeopleSampleSize == 0)
         return Collections.emptyList();

      final List<PersonalFeedbackFeaturedPerson> featuredPeopleSample = new ArrayList<>(featuredPeopleSampleSize);
      PersonalFeedbackPersonProfile personProfile;
      PersonalFeedbackBasicSummary feedbackSummary;
      long creationTime;
      long sortValue;

      for (int featuredPersonIndex = 0; featuredPersonIndex < featuredPeopleSampleSize; featuredPersonIndex ++)
      {
         sortValue = responseBuffer.getLong();
         if (sortValue != FeedbactoryConstants.EndOfDataLong)
         {
            /* In the case of a slightly outdated client the person profile read in may be null due to an unrecognised website,
             * and should not be added to the featured person sample list. To maintain the integrity of the read process and gracefully
             * skip the record, the remainder of the record must be read as per usual. Refer to the comments in readPersonalFeedbackPerson().
             */
            personProfile = readPersonalFeedbackPersonProfile(responseBuffer);
            feedbackSummary = readPersonalFeedbackBasicSummary(responseBuffer);

            /* For the recent feedback updater this will be incorrect since it's not being adjusted to the server's current time,
             * but the field is not going to be used anyway so it doesn't matter.
             */
            creationTime = responseBuffer.getLong();

            if (personProfile != null)
               featuredPeopleSample.add(new PersonalFeedbackFeaturedPerson(personProfile, feedbackSummary, creationTime, sortValue));
         }
         else
            featuredPeopleSample.add(new PersonalFeedbackFeaturedPerson(FeedbactoryConstants.EndOfDataLong));
      }

      return featuredPeopleSample;
   }


   private PersonalFeedbackPersonProfile readPersonalFeedbackPersonProfile(final ByteBuffer responseBuffer)
   {
      final PersonalFeedbackPerson person = readPersonalFeedbackPerson(responseBuffer);

      final String username = networkGateway.getUTF8EncodedString(responseBuffer);
      final String displayName = networkGateway.getUTF8EncodedString(responseBuffer);
      final String photoURL = networkGateway.getUTF8EncodedString(responseBuffer);
      final String URL = networkGateway.getUTF8EncodedString(responseBuffer);
      final Set<String> keywords = readItemProfileTags(responseBuffer);

      /* Refer to the comment in readPersonalFeedbackPerson().
       * To allow the buffer to gracefully skip to the next record if the person is null, the remainder
       * of the fields should be read, rather than bail out early.
       */
      if (person != null)
         return new PersonalFeedbackPersonProfile(person, username, displayName, photoURL, URL, keywords);
      else
         return null;
   }


   private PersonalFeedbackPerson readPersonalFeedbackPerson(final ByteBuffer responseBuffer)
   {
      /* If the client is slightly out of date, either or both of the website and the criteriaType may be null for unrecognised IDs.
       * Rather than throw an exception, the method should continue reading the record to allow
       * the possibility of gracefully skipping to the following record.
       */
      final PersonalFeedbackWebsite website = PersonalFeedbackWebsite.fromValue(responseBuffer.getShort());
      final String personID = networkGateway.getUTF8EncodedString(responseBuffer);
      final PersonalFeedbackCriteriaType criteriaType = PersonalFeedbackCriteriaType.fromValue(responseBuffer.get());

      if ((website != null) && (criteriaType != null))
         return new PersonalFeedbackPerson(website, personID, criteriaType);
      else
         return null;
   }


   private Set<String> readItemProfileTags(final ByteBuffer responseBuffer)
   {
      /* Currently the server is unconditionally writing an empty keywords list always, since it knows that the client
       * does not need this information until after the item has actually been browsed (at which point it will automatically
       * be updated locally from the web page).
       * In other words this method is always returning Collections.emptySet() for now.
       */
      final byte numberOfKeywords = responseBuffer.get();
      if (numberOfKeywords == 0)
         return Collections.emptySet();

      final Set<String> keywords = new HashSet<>(numberOfKeywords);

      for (byte keywordIndex = 0; keywordIndex < numberOfKeywords; keywordIndex ++)
         keywords.add(networkGateway.getUTF8EncodedString(responseBuffer));

      return keywords;
   }


   private PersonalFeedbackBasicSummary readPersonalFeedbackBasicSummary(final ByteBuffer responseBuffer)
   {
      final int numberOfRatings = responseBuffer.getInt();

      if (numberOfRatings > 0)
      {
         final byte feedbackBasicSummary = responseBuffer.get();
         return new PersonalFeedbackBasicSummary(numberOfRatings, feedbackBasicSummary);
      }
      else
         return PersonalFeedbackBasicSummary.EmptyFeedbackBasicSummary;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public ProcessedRequestResult<List<PersonalFeedbackFeaturedPerson>> sendGetHotPhotographyFeedbackSample()
   {
      return handleSendGetHotPhotographyFeedbackSample();
   }
}