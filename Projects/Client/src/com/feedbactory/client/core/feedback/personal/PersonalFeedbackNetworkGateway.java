
package com.feedbactory.client.core.feedback.personal;


import com.feedbactory.client.core.feedback.FeedbackNetworkGatewayManager;
import com.feedbactory.client.core.feedback.FeedbackNetworkGateway;
import com.feedbactory.client.core.network.ClientNetworkConstants;
import com.feedbactory.client.core.network.NetworkUtilities;
import com.feedbactory.client.core.network.ProcessedRequestBufferResponse;
import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import com.feedbactory.shared.feedback.FeedbackSubmissionStatus;
import com.feedbactory.shared.feedback.personal.CriteriaFeedbackFeaturedItemsFilter;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteria;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaDistribution;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaAttributes;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackDetailedSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackFeaturedPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackRequestType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmission;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleKeyValue;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsiteSet;
import com.feedbactory.shared.network.BasicOperationStatus;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


final public class PersonalFeedbackNetworkGateway implements FeedbackNetworkGateway
{
   final private PersonalFeedbackManager personalFeedbackManager;
   final private FeedbackNetworkGatewayManager feedbackNetworkGateway;


   PersonalFeedbackNetworkGateway(final PersonalFeedbackManager personalFeedbackManager, final FeedbackNetworkGatewayManager feedbackNetworkGateway)
   {
      this.personalFeedbackManager = personalFeedbackManager;
      this.feedbackNetworkGateway = feedbackNetworkGateway;
   }



   /****************************************************************************
    *
    ***************************************************************************/


   private void handleProcessFeedbackHandshake(final ByteBuffer handshakeBuffer)
   {
      final Set<PersonalFeedbackWebsite> enabledWebsites = new PersonalFeedbackWebsiteSet(handshakeBuffer.getInt());
      personalFeedbackManager.setEnabledWebsites(enabledWebsites);
   }


   private PersonalFeedbackPerson readPersonalFeedbackPerson(final ByteBuffer responseBuffer)
   {
      /* If the client is slightly out of date, either or both of the website and the criteriaType may be null for unrecognised IDs.
       * Rather than throw an exception, the method should continue reading the record to allow
       * the possibility of gracefully skipping to the following record.
       */
      final PersonalFeedbackWebsite website = PersonalFeedbackWebsite.fromValue(responseBuffer.getShort());
      final String personID = NetworkUtilities.getUTF8EncodedString(responseBuffer);
      final PersonalFeedbackCriteriaType criteriaType = PersonalFeedbackCriteriaType.fromValue(responseBuffer.get());

      if ((website != null) && (criteriaType != null))
         return new PersonalFeedbackPerson(website, personID, criteriaType);
      else
         return null;
   }


   private void writePersonalFeedbackPerson(final PersonalFeedbackPerson person, final ByteBuffer requestBuffer)
   {
      requestBuffer.putShort(person.getWebsite().getID());
      NetworkUtilities.putUTF8EncodedString(person.getItemID(), requestBuffer);
      requestBuffer.put(person.getCriteriaType().value);
   }


   private PersonalFeedbackPersonProfile readPersonalFeedbackPersonProfile(final ByteBuffer responseBuffer)
   {
      final PersonalFeedbackPerson person = readPersonalFeedbackPerson(responseBuffer);

      final String username = NetworkUtilities.getUTF8EncodedString(responseBuffer);
      final String displayName = NetworkUtilities.getUTF8EncodedString(responseBuffer);
      final String photoURL = NetworkUtilities.getUTF8EncodedString(responseBuffer);
      final String URL = NetworkUtilities.getUTF8EncodedString(responseBuffer);
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

      final Set<String> keywords = new HashSet<String>(numberOfKeywords);

      for (byte keywordIndex = 0; keywordIndex < numberOfKeywords; keywordIndex ++)
         keywords.add(NetworkUtilities.getUTF8EncodedString(responseBuffer));

      return keywords;
   }


   private void writePersonalFeedbackPersonProfile(final PersonalFeedbackPersonProfile personProfile, final ByteBuffer requestBuffer)
   {
      writePersonalFeedbackPerson(personProfile.person, requestBuffer);

      NetworkUtilities.putUTF8EncodedString(personProfile.userID, requestBuffer);
      NetworkUtilities.putUTF8EncodedString(personProfile.nameElements, requestBuffer);
      NetworkUtilities.putUTF8EncodedString(personProfile.imageURLElements, requestBuffer);
      NetworkUtilities.putUTF8EncodedString(personProfile.urlElements, requestBuffer);

      writeItemTags(personProfile.getTags(), requestBuffer);
   }


   private void writeItemTags(final Collection<String> tags, final ByteBuffer requestBuffer)
   {
      requestBuffer.put((byte) tags.size());

      for (final String tag : tags)
         NetworkUtilities.putUTF8EncodedString(tag, requestBuffer);
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


   private byte readPersonalFeedbackSubmissionSummary(final ByteBuffer responseBuffer)
   {
      return responseBuffer.get();
   }


   private PersonalFeedbackSubmission readPersonalFeedbackSubmission(final PersonalFeedbackPerson person, final ByteBuffer responseBuffer)
   {
      final byte overallFeedbackRating;
      final byte numberOfFeedbackCriteria = responseBuffer.get();

      if (numberOfFeedbackCriteria > 0)
         return readProfileCriteriaFeedbackSubmission(numberOfFeedbackCriteria, person.getCriteriaType().attributes, responseBuffer);
      else
      {
         overallFeedbackRating = responseBuffer.get();
         return new PersonalFeedbackSubmission(overallFeedbackRating);
      }
   }


   private <E extends Enum<E> & PersonalFeedbackCriteria> PersonalFeedbackSubmission readProfileCriteriaFeedbackSubmission(final int numberOfFeedbackCriteria,
                                                                                                                           final PersonalFeedbackCriteriaAttributes<E> criteriaAttributes,
                                                                                                                           final ByteBuffer responseBuffer)
   {
      final PersonalFeedbackSubmissionScaleProfile submissionScaleProfile = criteriaAttributes.getSubmissionScaleProfile();
      final EnumMap<E, PersonalFeedbackSubmissionScaleKeyValue> criteriaFeedback = new EnumMap<E, PersonalFeedbackSubmissionScaleKeyValue>(criteriaAttributes.getCriteriaClass());
      E feedbackCriteria;
      byte submissionScaleValue;
      PersonalFeedbackSubmissionScaleKeyValue submissionScale;

      for (byte feedbackCriteriaIndex = 0; feedbackCriteriaIndex < numberOfFeedbackCriteria; feedbackCriteriaIndex ++)
      {
         feedbackCriteria = criteriaAttributes.getCriteriaFromValue(responseBuffer.get());
         submissionScaleValue = responseBuffer.get();
         submissionScale = submissionScaleProfile.fromValue(submissionScaleValue);
         if (submissionScale == null)
            throw new IllegalArgumentException("Invalid value for " + submissionScaleProfile.getDisplayName() + " submission scale: " + submissionScaleValue);

         /* Unrecognised feedback criteria values here (feedbackCriteria == null) will result in a NullPointerException from the EnumMap.
          * If client version handling is performed correctly, ie. forcing users to download to the latest client
          * when significant code changes have been made, this should never happen on the client end.
          * Note that the policy on this is in contrast to the handling of new unrecognised websites above; it seems
          * reasonable to allow users to continue unhindered with an older client if there is support for new
          * websites added, but changes to feedback criteria values & submission scale values - where existing
          * feedback would be affected - is a more serious change and should require a client upgrade.
          * For one thing, users would be attempting to submit feedback for now-invalid feedback criteria,
          * which would result in security exceptions.
          */
         criteriaFeedback.put(feedbackCriteria, submissionScale);
      }

      final boolean isOverallRatingCalculatedFromCriteriaFeedback = NetworkUtilities.getBoolean(responseBuffer);

      if (isOverallRatingCalculatedFromCriteriaFeedback)
         return new PersonalFeedbackSubmission(criteriaFeedback);
      else
      {
         final byte overallFeedbackRating = responseBuffer.get();
         return new PersonalFeedbackSubmission(overallFeedbackRating, criteriaFeedback);
      }
   }


   private void writePersonalFeedbackSubmission(final PersonalFeedbackSubmission feedbackSubmission, final ByteBuffer requestBuffer)
   {
      requestBuffer.put((byte) feedbackSubmission.criteriaSubmissions.size());

      if (feedbackSubmission.criteriaSubmissions.size() > 0)
      {
         for (final Map.Entry<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> criteriaFeedbackEntry : feedbackSubmission.criteriaSubmissions.entrySet())
         {
            requestBuffer.put(criteriaFeedbackEntry.getKey().getValue());
            requestBuffer.put(criteriaFeedbackEntry.getValue().value);
         }

         NetworkUtilities.putBoolean(feedbackSubmission.isOverallRatingCalculatedFromCriteriaFeedback, requestBuffer);
         if (! feedbackSubmission.isOverallRatingCalculatedFromCriteriaFeedback)
            requestBuffer.put(feedbackSubmission.overallFeedbackRating);
      }
      else
         requestBuffer.put(feedbackSubmission.overallFeedbackRating);
   }


   private FeedbackSubmissionStatus readPersonalFeedbackSubmissionStatus(final ByteBuffer responseBuffer)
   {
      final byte feedbackSubmissionStatusValue = responseBuffer.get();
      final FeedbackSubmissionStatus feedbackSubmissionStatus = FeedbackSubmissionStatus.fromValue(feedbackSubmissionStatusValue);
      if (feedbackSubmissionStatus == null)
         throw new IllegalArgumentException("Invalid feedback submission status value: " + feedbackSubmissionStatusValue);

      return feedbackSubmissionStatus;
   }


   private PersonalFeedbackDetailedSummary readPersonalFeedbackDetailedSummary(final PersonalFeedbackPerson person, final ByteBuffer responseBuffer)
   {
      final PersonalFeedbackCriteriaType criteriaType = person.getCriteriaType();
      return readProfileCriteriaFeedback(criteriaType.attributes, responseBuffer);
   }


   private <E extends Enum<E> & PersonalFeedbackCriteria> PersonalFeedbackDetailedSummary readProfileCriteriaFeedback(final PersonalFeedbackCriteriaAttributes<E> criteriaAttributes,
                                                                                                                      final ByteBuffer responseBuffer)
   {
      final byte[] ratingDistributionPercentages = new byte[11];
      for (int ratingIndex = 0; ratingIndex < ratingDistributionPercentages.length; ratingIndex ++)
         ratingDistributionPercentages[ratingIndex] = responseBuffer.get();

      final byte numberOfCriteria = responseBuffer.get();
      if (numberOfCriteria == 0)
         return new PersonalFeedbackDetailedSummary(ratingDistributionPercentages);

      final PersonalFeedbackSubmissionScaleProfile submissionScaleProfile = criteriaAttributes.getSubmissionScaleProfile();

      final EnumMap<E, PersonalFeedbackCriteriaDistribution> criteriaFeedback = new EnumMap<E, PersonalFeedbackCriteriaDistribution>(criteriaAttributes.getCriteriaClass());
      E criteria;
      int numberOfRatings;
      byte averageFeedbackRating;
      byte numberOfFeedbackScaleElements;
      Map<PersonalFeedbackSubmissionScaleKeyValue, Byte> feedbackDistributionPercentages;
      byte feedbackScaleKeyValue;
      PersonalFeedbackSubmissionScaleKeyValue feedbackScaleKey;

      for (byte criteriaNumber = 0; criteriaNumber < numberOfCriteria; criteriaNumber ++)
      {
         /* The EnumMap will throw a NullPointerException if the criteria key is null.
          * Refer to the note in readProfileCriteriaFeedbackSubmission().
          */
         criteria = criteriaAttributes.getCriteriaFromValue(responseBuffer.get());
         numberOfRatings = responseBuffer.getInt();
         averageFeedbackRating = responseBuffer.get();

         if (averageFeedbackRating != PersonalFeedbackCriteriaDistribution.SuppressedLowAverageRating)
         {
            numberOfFeedbackScaleElements = responseBuffer.get();

            feedbackDistributionPercentages = new HashMap<PersonalFeedbackSubmissionScaleKeyValue, Byte>(numberOfFeedbackScaleElements);

            for (byte feedbackScaleElement = 0; feedbackScaleElement < numberOfFeedbackScaleElements; feedbackScaleElement ++)
            {
               feedbackScaleKeyValue = responseBuffer.get();
               feedbackScaleKey = submissionScaleProfile.fromValue(feedbackScaleKeyValue);
               if (feedbackScaleKey == null)
                  throw new IllegalArgumentException("Invalid value for " + submissionScaleProfile.getDisplayName() + " submission scale: " + feedbackScaleKeyValue);

               feedbackDistributionPercentages.put(feedbackScaleKey, responseBuffer.get());
            }

            criteriaFeedback.put(criteria, new PersonalFeedbackCriteriaDistribution(numberOfRatings, averageFeedbackRating, feedbackDistributionPercentages));
         }
         else
            criteriaFeedback.put(criteria, new PersonalFeedbackCriteriaDistribution(numberOfRatings, PersonalFeedbackCriteriaDistribution.SuppressedLowAverageRating));
      }

      return new PersonalFeedbackDetailedSummary(ratingDistributionPercentages, criteriaFeedback);
   }


   private void writeFeaturedItemsFilter(final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter, final ByteBuffer requestBuffer)
   {
      requestBuffer.put(featuredItemsFilter.criteriaType.value);

      requestBuffer.putShort((short) featuredItemsFilter.filterWebsites.size());
      for (final PersonalFeedbackWebsite website : featuredItemsFilter.filterWebsites)
         requestBuffer.putShort(website.getID());

      writeItemTags(featuredItemsFilter.filterTags, requestBuffer);

      requestBuffer.putLong(featuredItemsFilter.lastRetrievedSortValue);
      if (featuredItemsFilter.lastRetrievedSortValue != FeedbactoryConstants.NoTime)
         writePersonalFeedbackPerson(featuredItemsFilter.lastRetrievedItem, requestBuffer);
   }


   private List<PersonalFeedbackFeaturedPerson> readFeaturedPeopleFeedbackSample(final ByteBuffer responseBuffer)
   {
      final int featuredPeopleSampleSize = responseBuffer.getInt();

      if (featuredPeopleSampleSize == 0)
         return Collections.emptyList();

      final List<PersonalFeedbackFeaturedPerson> featuredPeopleSample = new ArrayList<PersonalFeedbackFeaturedPerson>(featuredPeopleSampleSize);
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
            creationTime = responseBuffer.getLong() - feedbackNetworkGateway.getApproximateServerTimeDifference();

            if (personProfile != null)
               featuredPeopleSample.add(new PersonalFeedbackFeaturedPerson(personProfile, feedbackSummary, creationTime, sortValue));
         }
         else
            featuredPeopleSample.add(new PersonalFeedbackFeaturedPerson(FeedbactoryConstants.EndOfDataLong));
      }

      return featuredPeopleSample;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private PersonalFeedbackBasicSummaryResult handleSendGetPersonBasicFeedbackSummaryRequest(final PersonalFeedbackPerson person)
   {
      final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

      writePersonalFeedbackPerson(person, requestBuffer);
      requestBuffer.flip();

      final ProcessedRequestBufferResponse requestResponse = feedbackNetworkGateway.sendGetItemFeedbackSummaryRequest(FeedbackCategory.Personal, requestBuffer);

      if (requestResponse.requestStatus == NetworkRequestStatus.OK)
      {
         final BasicOperationStatus operationStatus = NetworkUtilities.getBasicOperationStatus(requestResponse.data);
         if (operationStatus == BasicOperationStatus.OK)
         {
            final PersonalFeedbackBasicSummary feedbackBasicSummary = readPersonalFeedbackBasicSummary(requestResponse.data);
            return new PersonalFeedbackBasicSummaryResult(requestResponse.requestStatus, operationStatus, feedbackBasicSummary);
         }
         else
            return new PersonalFeedbackBasicSummaryResult(requestResponse.requestStatus, operationStatus);
      }
      else
         return new PersonalFeedbackBasicSummaryResult(requestResponse.requestStatus);
   }


   private PersonalFeedbackGetSubmissionResult handleSendGetPersonFeedbackSubmissionRequest(final PersonalFeedbackPerson person)
   {
      final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

      writePersonalFeedbackPerson(person, requestBuffer);
      requestBuffer.flip();

      final ProcessedRequestBufferResponse requestResponse = feedbackNetworkGateway.sendGetItemFeedbackSubmissionRequest(FeedbackCategory.Personal, requestBuffer);

      if (requestResponse.requestStatus == NetworkRequestStatus.OK)
      {
         final BasicOperationStatus operationStatus = NetworkUtilities.getBasicOperationStatus(requestResponse.data);
         if (operationStatus == BasicOperationStatus.OK)
         {
            final PersonalFeedbackSubmission feedbackSubmission = readPersonalFeedbackSubmission(person, requestResponse.data);
            return new PersonalFeedbackGetSubmissionResult(requestResponse.requestStatus, operationStatus, feedbackSubmission);
         }
         else
            return new PersonalFeedbackGetSubmissionResult(requestResponse.requestStatus, operationStatus);
      }
      else
         return new PersonalFeedbackGetSubmissionResult(requestResponse.requestStatus);
   }


   private PersonalFeedbackAddSubmissionResult handleSendAddPersonFeedbackSubmissionRequest(final PersonalFeedbackPersonProfile personProfile,
                                                                                            final PersonalFeedbackSubmission personalFeedbackSubmission)
   {
      final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

      writePersonalFeedbackPersonProfile(personProfile, requestBuffer);
      writePersonalFeedbackSubmission(personalFeedbackSubmission, requestBuffer);
      requestBuffer.flip();

      final ProcessedRequestBufferResponse requestResponse = feedbackNetworkGateway.sendAddItemFeedbackSubmissionRequest(FeedbackCategory.Personal, requestBuffer);

      if (requestResponse.requestStatus == NetworkRequestStatus.OK)
      {
         final FeedbackSubmissionStatus feedbackSubmissionStatus = readPersonalFeedbackSubmissionStatus(requestResponse.data);

         if (feedbackSubmissionStatus == FeedbackSubmissionStatus.OK)
         {
            final PersonalFeedbackBasicSummary feedbackBasicSummary = readPersonalFeedbackBasicSummary(requestResponse.data);
            return new PersonalFeedbackAddSubmissionResult(requestResponse.requestStatus, feedbackSubmissionStatus, feedbackBasicSummary);
         }
         else
            return new PersonalFeedbackAddSubmissionResult(requestResponse.requestStatus, feedbackSubmissionStatus);
      }
      else
         return new PersonalFeedbackAddSubmissionResult(requestResponse.requestStatus);
   }


   private PersonalFeedbackBasicSummaryResult handleSendRemovePersonFeedbackSubmissionRequest(final PersonalFeedbackPerson person)
   {
      final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

      writePersonalFeedbackPerson(person, requestBuffer);
      requestBuffer.flip();

      final ProcessedRequestBufferResponse requestResponse = feedbackNetworkGateway.sendRemoveItemFeedbackSubmissionRequest(FeedbackCategory.Personal, requestBuffer);

      if (requestResponse.requestStatus == NetworkRequestStatus.OK)
      {
         final BasicOperationStatus operationStatus = NetworkUtilities.getBasicOperationStatus(requestResponse.data);
         if (operationStatus == BasicOperationStatus.OK)
         {
            final PersonalFeedbackBasicSummary feedbackBasicSummary = readPersonalFeedbackBasicSummary(requestResponse.data);
            return new PersonalFeedbackBasicSummaryResult(requestResponse.requestStatus, operationStatus, feedbackBasicSummary);
         }
         else
            return new PersonalFeedbackBasicSummaryResult(requestResponse.requestStatus, operationStatus);
      }
      else
         return new PersonalFeedbackBasicSummaryResult(requestResponse.requestStatus);
   }


   /****************************************************************************
    * The following methods are extended feedback functions, specific to personal feedback.
    ***************************************************************************/


   private PersonalFeedbackDetailedSummaryResult handleSendGetPersonDetailedFeedbackSummaryRequest(final PersonalFeedbackPerson person)
   {
      final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

      requestBuffer.put(PersonalFeedbackRequestType.GetPersonDetailedFeedbackSummary.value);
      writePersonalFeedbackPerson(person, requestBuffer);
      requestBuffer.flip();

      final ProcessedRequestBufferResponse requestResponse = feedbackNetworkGateway.sendFeedbackCategoryRequest(FeedbackCategory.Personal, requestBuffer, false);

      if (requestResponse.requestStatus == NetworkRequestStatus.OK)
      {
         final BasicOperationStatus operationStatus = NetworkUtilities.getBasicOperationStatus(requestResponse.data);
         if (operationStatus == BasicOperationStatus.OK)
         {
            final PersonalFeedbackDetailedSummary feedbackDetailedSummary = readPersonalFeedbackDetailedSummary(person, requestResponse.data);
            return new PersonalFeedbackDetailedSummaryResult(requestResponse.requestStatus, operationStatus, feedbackDetailedSummary);
         }
         else
            return new PersonalFeedbackDetailedSummaryResult(requestResponse.requestStatus, operationStatus);
      }
      else
         return new PersonalFeedbackDetailedSummaryResult(requestResponse.requestStatus);
   }


   private PersonalFeedbackFeaturedItemsSampleResult handleGetFeaturedFeedbackItemsSample(final PersonalFeedbackRequestType requestType,
                                                                                          final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      final ByteBuffer requestBuffer = ByteBuffer.allocate(ClientNetworkConstants.MaximumRequestSizeBytes);

      requestBuffer.put(requestType.value);
      writeFeaturedItemsFilter(featuredItemsFilter, requestBuffer);

      requestBuffer.flip();

      final ProcessedRequestBufferResponse requestResponse = feedbackNetworkGateway.sendFeedbackCategoryRequest(FeedbackCategory.Personal, requestBuffer, false);

      if (requestResponse.requestStatus == NetworkRequestStatus.OK)
      {
         final List<PersonalFeedbackFeaturedPerson> featuredPeopleFeedbackSample = readFeaturedPeopleFeedbackSample(requestResponse.data);
         return new PersonalFeedbackFeaturedItemsSampleResult(requestResponse.requestStatus, featuredPeopleFeedbackSample);
      }
      else
         return new PersonalFeedbackFeaturedItemsSampleResult(requestResponse.requestStatus);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final PersonalFeedbackBasicSummaryResult sendGetPersonBasicFeedbackSummaryRequest(final PersonalFeedbackPerson person)
   {
      return handleSendGetPersonBasicFeedbackSummaryRequest(person);
   }


   final PersonalFeedbackGetSubmissionResult sendGetPersonFeedbackSubmissionRequest(final PersonalFeedbackPerson person)
   {
      return handleSendGetPersonFeedbackSubmissionRequest(person);
   }


   final PersonalFeedbackAddSubmissionResult sendAddPersonFeedbackSubmissionRequest(final PersonalFeedbackPersonProfile personProfile,
                                                                                    final PersonalFeedbackSubmission personalFeedbackSubmission)
   {
      return handleSendAddPersonFeedbackSubmissionRequest(personProfile, personalFeedbackSubmission);
   }


   final PersonalFeedbackBasicSummaryResult sendRemovePersonFeedbackSubmissionRequest(final PersonalFeedbackPerson person)
   {
      return handleSendRemovePersonFeedbackSubmissionRequest(person);
   }


   final PersonalFeedbackDetailedSummaryResult sendGetPersonDetailedFeedbackSummaryRequest(final PersonalFeedbackPerson person)
   {
      return handleSendGetPersonDetailedFeedbackSummaryRequest(person);
   }


   final PersonalFeedbackFeaturedItemsSampleResult getNewFeedbackItemsSample(final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      return handleGetFeaturedFeedbackItemsSample(PersonalFeedbackRequestType.GetNewFeedbackItemsSample, featuredItemsFilter);
   }


   final PersonalFeedbackFeaturedItemsSampleResult getHotFeedbackItemsSample(final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter)
   {
      return handleGetFeaturedFeedbackItemsSample(PersonalFeedbackRequestType.GetHotFeedbackItemsSample, featuredItemsFilter);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public void processFeedbackHandshake(final ByteBuffer handshakeBuffer)
   {
      handleProcessFeedbackHandshake(handshakeBuffer);
   }


   @Override
   final public FeedbackItemProfile readFeedbackItemProfile(final ByteBuffer responseBuffer)
   {
      return readPersonalFeedbackPersonProfile(responseBuffer);
   }


   @Override
   final public Byte readFeedbackSubmissionSummary(final ByteBuffer responseBuffer)
   {
      // Note the auto boxing of the byte return value.
      return readPersonalFeedbackSubmissionSummary(responseBuffer);
   }


   @Override
   final public PersonalFeedbackBasicSummary readFeedbackResultSummary(final ByteBuffer responseBuffer)
   {
      return readPersonalFeedbackBasicSummary(responseBuffer);
   }
}