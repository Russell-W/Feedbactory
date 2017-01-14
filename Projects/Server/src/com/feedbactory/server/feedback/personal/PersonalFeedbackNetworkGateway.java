
package com.feedbactory.server.feedback.personal;


import com.feedbactory.server.core.FeedbactorySecurityException;
import com.feedbactory.server.core.log.FeedbactoryLogger;
import com.feedbactory.server.core.log.SecurityLogLevel;
import com.feedbactory.server.feedback.FeedbackCategoryNetworkGateway;
import com.feedbactory.server.feedback.personal.PersonalFeedbackManager.PersonalFeedbackNode;
import com.feedbactory.server.network.application.ProcessedOperationStatus;
import com.feedbactory.server.network.application.RequestUserSession;
import com.feedbactory.server.network.component.buffer.ReadableByteBuffer;
import com.feedbactory.server.network.component.buffer.WritableByteBuffer;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.feedback.FeedbackItem;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import com.feedbactory.shared.feedback.FeedbackSubmission;
import com.feedbactory.shared.feedback.FeedbackSubmissionStatus;
import com.feedbactory.shared.feedback.personal.CriteriaFeedbackFeaturedItemsFilter;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteria;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaAttributes;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaDistribution;
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
import com.feedbactory.shared.network.SessionRequestType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


final class PersonalFeedbackNetworkGateway implements FeedbackCategoryNetworkGateway
{
   static final private String WebsiteFeedbackOnCommandSwitch = "on";
   static final private String WebsiteFeedbackOffCommandSwitch = "off";

   final private PersonalFeedbackManager personalFeedbackManager;

   final private PersonalFeedbackWebsiteSet enabledWebsites = new PersonalFeedbackWebsiteSet();
   final private ReadWriteLock enabledWebsitesLock = new ReentrantReadWriteLock();

   final private CriteriaFeedbackWebsiteAttributesRegistry websiteAttributesRegistry = new CriteriaFeedbackWebsiteAttributesRegistry();


   PersonalFeedbackNetworkGateway(final PersonalFeedbackManager personalFeedbackManager)
   {
      this.personalFeedbackManager = personalFeedbackManager;

      initialise();
   }


   private void initialise()
   {
      for (final PersonalFeedbackWebsite website : PersonalFeedbackWebsite.getWebsites())
         enabledWebsites.add(website);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private boolean isWebsiteEnabled(final PersonalFeedbackWebsite website)
   {
      try
      {
         enabledWebsitesLock.readLock().lock();
         return enabledWebsites.contains(website);
      }
      finally
      {
         enabledWebsitesLock.readLock().unlock();
      }
   }


   private boolean setWebsiteEnabled(final PersonalFeedbackWebsite website, final boolean isEnabled)
   {
      try
      {
         enabledWebsitesLock.writeLock().lock();
         return (isEnabled ? enabledWebsites.add(website) : enabledWebsites.remove(website));
      }
      finally
      {
         enabledWebsitesLock.writeLock().unlock();
      }
   }


   private Set<PersonalFeedbackWebsite> getEnabledWebsites()
   {
      try
      {
         enabledWebsitesLock.readLock().lock();
         return new PersonalFeedbackWebsiteSet(enabledWebsites);
      }
      finally
      {
         enabledWebsitesLock.readLock().unlock();
      }
   }


   private void handleWriteHandshakeResponse(final WritableByteBuffer responseBuffer)
   {
      try
      {
         enabledWebsitesLock.readLock().lock();

         responseBuffer.putInteger(enabledWebsites.getElementsBitArray());
      }
      finally
      {
         enabledWebsitesLock.readLock().unlock();
      }
   }


   private void writeBasicOperationStatus(final BasicOperationStatus operationStatus, final WritableByteBuffer responseBuffer)
   {
      responseBuffer.put(operationStatus.value);
   }


   private PersonalFeedbackPerson readPersonalFeedbackPerson(final ReadableByteBuffer requestBuffer)
   {
      final short websiteValue = requestBuffer.getShort();
      final PersonalFeedbackWebsite website = PersonalFeedbackWebsite.fromValue(websiteValue);
      if (website == null)
         throw new FeedbactorySecurityException(getClass(), "Invalid personal feedback website value: " + websiteValue);

      final String personID = requestBuffer.getUTF8EncodedString();

      final byte criteriaTypeValue = requestBuffer.get();
      final PersonalFeedbackCriteriaType criteriaType = PersonalFeedbackCriteriaType.fromValue(criteriaTypeValue);
      if (criteriaType == null)
         throw new FeedbactorySecurityException(getClass(), "Invalid personal feedback criteria type value: " + criteriaTypeValue);
      else if (! website.getCriteriaTypes().contains(criteriaType))
         throw new FeedbactorySecurityException(getClass(), "Website " + website.getName() + " does not support criteria feedback type: " + criteriaType.displayName);

      if (! validateFieldLength(personID, PersonalFeedbackConstants.MaximumPersonIDLength))
         throw new FeedbactorySecurityException(getClass(), generateFailedValidationMessage("item ID", personID));

      if (! websiteAttributesRegistry.getWebsiteAttributes(website).getValidator().validateItemID(personID))
         throw new FeedbactorySecurityException(getClass(), generateWebsiteValidationErrorMessage(website, "item ID", personID));

      return new PersonalFeedbackPerson(website, personID, criteriaType);
   }


   private boolean validateFieldLength(final String stringField, final int maximumAllowableLength)
   {
      return ((stringField == null) || (stringField.length() <= maximumAllowableLength));
   }


   private String generateFailedValidationMessage(final String fieldName, final String fieldValue)
   {
      final StringBuilder failedValidationMessage = new StringBuilder(200);
      failedValidationMessage.append("Criteria feedback item/profile length exceeded for ");
      failedValidationMessage.append(fieldName);
      failedValidationMessage.append(", length was: ");
      failedValidationMessage.append(fieldValue.length());
      failedValidationMessage.append(", value was: '");
      failedValidationMessage.append(fieldValue);
      failedValidationMessage.append('\'');

      return failedValidationMessage.toString();
   }


   private String generateWebsiteValidationErrorMessage(final PersonalFeedbackWebsite website, final String fieldName, final String fieldValue)
   {
      final StringBuilder messageBuilder = new StringBuilder(200);
      messageBuilder.append("Invalid ");
      messageBuilder.append(fieldName);
      messageBuilder.append(" for website ");
      messageBuilder.append(website.getName());
      messageBuilder.append(": ");

      if (fieldValue != null)
      {
         // Enclose in quotes so that illegal leading or trailing whitespace will be visible.
         messageBuilder.append('\'');
         messageBuilder.append(fieldValue);
         messageBuilder.append('\'');
      }
      else
         messageBuilder.append("null");

      return messageBuilder.toString();
   }


   private void writePersonalFeedbackPerson(final PersonalFeedbackPerson person, final WritableByteBuffer responseBuffer)
   {
      responseBuffer.putShort(person.getWebsite().getID());
      responseBuffer.putUTF8EncodedString(person.getItemID());
      responseBuffer.put(person.getCriteriaType().value);
   }


   private PersonalFeedbackPersonProfile readPersonalFeedbackPersonProfile(final ReadableByteBuffer requestBuffer)
   {
      final PersonalFeedbackPerson person = readPersonalFeedbackPerson(requestBuffer);

      final String userID = requestBuffer.getUTF8EncodedString();
      final String displayName = requestBuffer.getUTF8EncodedString();
      final String photoURL = requestBuffer.getUTF8EncodedString();
      final String url = requestBuffer.getUTF8EncodedString();
      final Set<String> tags = readItemTags(requestBuffer, PersonalFeedbackConstants.MaximumPersonProfileTags);

      if (! validateFieldLength(userID, PersonalFeedbackConstants.MaximumPersonProfileUserIDLength))
         throw new FeedbactorySecurityException(getClass(), generateFailedValidationMessage("user ID", userID));
      else if (! validateFieldLength(displayName, PersonalFeedbackConstants.MaximumPersonProfileDisplayNameLength))
         throw new FeedbactorySecurityException(getClass(), generateFailedValidationMessage("display name", displayName));
      else if (! validateFieldLength(photoURL, PersonalFeedbackConstants.MaximumPersonProfilePhotoURLLength))
         throw new FeedbactorySecurityException(getClass(), generateFailedValidationMessage("photo URL", photoURL));
      else if (! validateFieldLength(url, PersonalFeedbackConstants.MaximumPersonProfileURLLength))
         throw new FeedbactorySecurityException(getClass(), generateFailedValidationMessage("URL", url));

      final CriteriaFeedbackWebsiteValidator websiteValidator = websiteAttributesRegistry.getWebsiteAttributes(person.getWebsite()).getValidator();
      if (! websiteValidator.validateUserID(userID))
         throw new FeedbactorySecurityException(getClass(), generateWebsiteValidationErrorMessage(person.getWebsite(), "user ID", userID));
      else if (! websiteValidator.validateDisplayName(displayName))
         throw new FeedbactorySecurityException(getClass(), generateWebsiteValidationErrorMessage(person.getWebsite(), "display name", displayName));
      else if (! websiteValidator.validatePhotoURL(photoURL))
         throw new FeedbactorySecurityException(getClass(), generateWebsiteValidationErrorMessage(person.getWebsite(), "photo URL", photoURL));
      else if (! websiteValidator.validateURL(url))
         throw new FeedbactorySecurityException(getClass(), generateWebsiteValidationErrorMessage(person.getWebsite(), "URL", url));

      return new PersonalFeedbackPersonProfile(person, userID, displayName, photoURL, url, tags);
   }


   private Set<String> readItemTags(final ReadableByteBuffer requestBuffer, final int maximumAllowableTags)
   {
      final byte numberOfTags = requestBuffer.get();
      if (numberOfTags == 0)
         return Collections.emptySet();
      else if ((numberOfTags < 0) || (numberOfTags > maximumAllowableTags))
         throw new FeedbactorySecurityException(getClass(), "Invalid number of item profile or filter tags: " + numberOfTags);

      final Set<String> tags = new HashSet<>(numberOfTags);
      String tag;

      for (byte tagIndex = 0; tagIndex < numberOfTags; tagIndex ++)
      {
         tag = requestBuffer.getUTF8EncodedString();

         if ((tag == null) ||
             (tag.length() < PersonalFeedbackConstants.MinimumPersonProfileTagLength) ||
             (tag.length() > PersonalFeedbackConstants.MaximumPersonProfileTagLength) ||
             (! isAlphaNumeric(tag)))
         {
            final StringBuilder messageBuilder = new StringBuilder(100);
            messageBuilder.append("Invalid item profile or filter tag: ");

            if (tag == null)
               messageBuilder.append("null");
            else
            {
               // Enclose in quotes so that illegal leading or trailing whitespace will be visible.
               messageBuilder.append('\'');
               messageBuilder.append(tag);
               messageBuilder.append("' (length ");
               messageBuilder.append(tag.length());
               messageBuilder.append(')');
            }

            throw new FeedbactorySecurityException(getClass(), messageBuilder.toString());
         }

         /* Many keywords will be heavily duplicated across item profiles and other requests, so intern() them here to save memory.
          * When this method is reading tags as part of a filter, this has the added benefit that those tags will be reference-equal
          * to the tags stored against the item profiles.
          * As of JDK 7 & 8 intern()ed strings are placed in the heap so they are eligible for garbage collection, hence there is no
          * concern of a deluge of otherwise unused filter tags being read in and retained in memory forever; if the string isn't referenced elsewhere,
          * it can be garbage collected as with any other object.
          *
          * For the intern() collection to stay efficient as the number of items (and keywords) grows, it's probably worthwhile to use
          * the -XX:StringTableSize=n JVM switch, where n is a large prime number. Use -XX:+PrintStringTableStatistics to output
          * the usage stats of the String intern table, or the jmap command utility: 'jmap -heap <java process ID>'.
          *
          * Also note that the toLowerCase() assumes that the server locale has already been set to a specific value, which is the same
          * locale value that will be used when clients apply toLowerCase() to tags that will be sent to the server and ultimately processed
          * by this method. If the clients didn't enforce the same locale at those critical points, they could potentially send different
          * tag strings and lowercasing them here does no good, eg. lowercasing 'I' on the client within a Turkish locale would normally
          * produce a dotless 'ı', and re-lowercasing that here is no good since it again produces 'ı'. The English lowercasing has to be
          * done from the client end using toLowerCase(Locale.ENGLISH).
          */
         tag = tag.toLowerCase().intern();

         if (tags.contains(tag))
            throw new FeedbactorySecurityException(getClass(), "Duplicate tag supplied for item profile or filter: " + tag);

         if (! PersonalFeedbackServerConstants.FeaturedItemsExcludedTags.contains(tag))
            tags.add(tag);
      }

      return tags;
   }


   final protected boolean isAlphaNumeric(final String string)
   {
      char character;

      for (int characterIndex = 0; characterIndex < string.length(); characterIndex ++)
      {
         character = string.charAt(characterIndex);
         if ((! Character.isLetterOrDigit(character)) && (PersonalFeedbackConstants.PermittedTagNonAlphaNumericCharacters.indexOf(character) == -1))
            return false;
      }

      return true;
   }


   private void writePersonalFeedbackPersonProfile(final PersonalFeedbackPersonProfile personProfile, final WritableByteBuffer responseBuffer)
   {
      writePersonalFeedbackPerson(personProfile.person, responseBuffer);

      responseBuffer.putUTF8EncodedString(personProfile.userID);
      responseBuffer.putUTF8EncodedString(personProfile.nameElements);
      responseBuffer.putUTF8EncodedString(personProfile.imageURLElements);
      responseBuffer.putUTF8EncodedString(personProfile.urlElements);

      /* For now, unconditionally send the clients an empty list of item profile keywords since they don't need access to that information
       * until once the item has actually been browsed, at which point it will locally be updated by the client anyway.
       * This is obviously presuming more than the server should know about the client just for the sake of saving a few bytes of output,
       * it's no problem to write the keywords here if it turns out that the client does need them immediately.
       */
      responseBuffer.put((byte) 0);
   }


   private PersonalFeedbackSubmission readPersonalFeedbackSubmission(final PersonalFeedbackPerson person, final ReadableByteBuffer requestBuffer)
   {
      final byte overallFeedbackRating;
      final byte numberOfFeedbackCriteria = requestBuffer.get();

      if (numberOfFeedbackCriteria > 0)
      {
         if (person.getCriteriaType() == PersonalFeedbackCriteriaType.None)
            throw new FeedbactorySecurityException(getClass(), "Non-empty criteria feedback specified when criteria type is NoCriteria.");

         return readProfileCriteriaFeedbackSubmission(numberOfFeedbackCriteria, person.getCriteriaType().attributes, requestBuffer);
      }
      else
      {
         overallFeedbackRating = requestBuffer.get();

         if (overallFeedbackRating == PersonalFeedbackSubmission.NoRatingValue)
            throw new FeedbactorySecurityException(getClass(), "No overall rating provided for personal feedback submission.");

         return new PersonalFeedbackSubmission(overallFeedbackRating);
      }
   }


   private <E extends Enum<E> & PersonalFeedbackCriteria> PersonalFeedbackSubmission readProfileCriteriaFeedbackSubmission(final int numberOfFeedbackCriteria,
                                                                                                                           final PersonalFeedbackCriteriaAttributes<E> criteriaAttributes,
                                                                                                                           final ReadableByteBuffer requestBuffer)
   {
      final PersonalFeedbackSubmissionScaleProfile submissionScaleProfile = criteriaAttributes.getSubmissionScaleProfile();
      final EnumMap<E, PersonalFeedbackSubmissionScaleKeyValue> criteriaFeedback = new EnumMap<>(criteriaAttributes.getCriteriaClass());
      byte feedbackCriteriaValue;
      E feedbackCriteria;
      byte submissionScaleValue;
      PersonalFeedbackSubmissionScaleKeyValue submissionScale;

      for (byte feedbackCriteriaIndex = 0; feedbackCriteriaIndex < numberOfFeedbackCriteria; feedbackCriteriaIndex ++)
      {
         feedbackCriteriaValue = requestBuffer.get();
         feedbackCriteria = criteriaAttributes.getCriteriaFromValue(feedbackCriteriaValue);
         if (feedbackCriteria == null)
            throw new FeedbactorySecurityException(getClass(), "Invalid value for " + criteriaAttributes.getCriteriaType().displayName + "feedback criteria: " + feedbackCriteriaValue);

         submissionScaleValue = requestBuffer.get();
         submissionScale = submissionScaleProfile.fromValue(submissionScaleValue);
         if (submissionScale == null)
            throw new FeedbactorySecurityException(getClass(), "Invalid value for " + submissionScaleProfile.getDisplayName() + " submission scale: " + submissionScaleValue);
         else if (submissionScale.value == PersonalFeedbackSubmission.NoRatingValue)
            throw new FeedbactorySecurityException(getClass(), "Criteria feedback submission contains 'no rating' criteria.");
         else if (criteriaFeedback.containsKey(feedbackCriteria))
            throw new FeedbactorySecurityException(getClass(), "Criteria feedback submission contains duplicate criteria.");

         criteriaFeedback.put(feedbackCriteria, submissionScale);
      }

      final boolean isOverallRatingCalculatedFromCriteriaFeedback = requestBuffer.getBoolean();

      if (isOverallRatingCalculatedFromCriteriaFeedback)
         return new PersonalFeedbackSubmission(criteriaFeedback);
      else
      {
         final byte overallFeedbackRating = requestBuffer.get();
         return new PersonalFeedbackSubmission(overallFeedbackRating, criteriaFeedback);
      }
   }


   private void writeFeedbackSubmissionStatus(final FeedbackSubmissionStatus submissionStatus, final WritableByteBuffer responseBuffer)
   {
      responseBuffer.put(submissionStatus.value);
   }


   private void writePersonalFeedbackSubmission(final PersonalFeedbackSubmission feedbackSubmission, final WritableByteBuffer responseBuffer)
   {
      responseBuffer.put((byte) feedbackSubmission.criteriaSubmissions.size());

      if (feedbackSubmission.criteriaSubmissions.size() > 0)
      {
         for (final Entry<? extends PersonalFeedbackCriteria, PersonalFeedbackSubmissionScaleKeyValue> criteriaFeedbackEntry : feedbackSubmission.criteriaSubmissions.entrySet())
         {
            responseBuffer.put(criteriaFeedbackEntry.getKey().getValue());
            responseBuffer.put(criteriaFeedbackEntry.getValue().value);
         }

         responseBuffer.putBoolean(feedbackSubmission.isOverallRatingCalculatedFromCriteriaFeedback);
         if (! feedbackSubmission.isOverallRatingCalculatedFromCriteriaFeedback)
            responseBuffer.put(feedbackSubmission.overallFeedbackRating);
      }
      else
         responseBuffer.put(feedbackSubmission.overallFeedbackRating);
   }


   private void writePersonalFeedbackSubmissionSummary(final PersonalFeedbackSubmission feedbackSubmission, final WritableByteBuffer responseBuffer)
   {
      responseBuffer.put(feedbackSubmission.overallFeedbackRating);
   }


   private void writePersonalFeedbackBasicSummary(final PersonalFeedbackBasicSummary feedbackBasicSummary, final WritableByteBuffer responseBuffer)
   {
      responseBuffer.putInteger(feedbackBasicSummary.numberOfRatings);
      if (feedbackBasicSummary.numberOfRatings > 0)
         responseBuffer.put(feedbackBasicSummary.averageRating);
   }


   private void writePersonalFeedbackDetailedSummary(final PersonalFeedbackDetailedSummary feedbackDetailedSummary, final WritableByteBuffer responseBuffer)
   {
      final byte[] ratingDistributionPercentages = feedbackDetailedSummary.getRatingDistributionPercentages();
      for (final byte ratingDistributionPercentage : ratingDistributionPercentages)
         responseBuffer.put(ratingDistributionPercentage);

      responseBuffer.put((byte) feedbackDetailedSummary.criteriaFeedback.size());

      PersonalFeedbackCriteriaDistribution feedbackDistribution;

      for (final Entry<? extends PersonalFeedbackCriteria, PersonalFeedbackCriteriaDistribution> criteriaFeedbackEntry : feedbackDetailedSummary.criteriaFeedback.entrySet())
      {
         feedbackDistribution = criteriaFeedbackEntry.getValue();

         // Assumption here: the number of criteria for criteria set will never exceed 256 elements.
         responseBuffer.put(criteriaFeedbackEntry.getKey().getValue());
         responseBuffer.putInteger(feedbackDistribution.numberOfRatings);
         responseBuffer.put(feedbackDistribution.averageFeedbackRating);

         if (feedbackDistribution.averageFeedbackRating != PersonalFeedbackCriteriaDistribution.SuppressedLowAverageRating)
         {
            // Assumption here: the feedback scale (eg. Excellent, Good, Fair, etc) for any type of criteria will never exceed 256 elements.
            responseBuffer.put((byte) feedbackDistribution.feedbackDistributionPercentages.size());

            for (final Entry<PersonalFeedbackSubmissionScaleKeyValue, Byte> feedbackDistributionPercentageEntry : feedbackDistribution.feedbackDistributionPercentages.entrySet())
            {
               responseBuffer.put(feedbackDistributionPercentageEntry.getKey().value);
               responseBuffer.put(feedbackDistributionPercentageEntry.getValue().byteValue());
            }
         }
      }
   }


   private void writePersonFeedbackSummary(final PersonalFeedbackPerson person, final WritableByteBuffer responseBuffer)
   {
      final PersonalFeedbackBasicSummary feedbackBasicSummary = personalFeedbackManager.getPersonalFeedbackBasicSummary(person);
      writePersonalFeedbackBasicSummary(feedbackBasicSummary, responseBuffer);
   }


   private void writeFeaturedFeedbackItemsSample(final List<PersonalFeedbackFeaturedPerson> featuredPeopleSample, final WritableByteBuffer responseBuffer)
   {
      /* Writing a list of feedback items will generally take up a lot more space than a regular request, for which there is a default
       * small amount of bytes allocated. The GrowableByteBuffer expands its size very conservatively when this small capacity is exceeded,
       * resulting in the likelihood of several expansion operations if the overall write size is reasonably large. Each of these operations has
       * the overhead of slicing a new (and possibly still not large enough) buffer from the overflow store, transferring the old buffer contents to this,
       * and then trying to reclaim the old buffer. Therefore applying an upfront guesstimate of the (likely) larger capacity needed up front will
       * almost certainly prevent this repeated overhead.
       */
      responseBuffer.ensureRemainingCapacity(featuredPeopleSample.size() * 150);

      // This could probably be safely cast and written as a single byte for now, but not if I want to provide much larger sample sizes.
      responseBuffer.putInteger(featuredPeopleSample.size());

      for (final PersonalFeedbackFeaturedPerson featuredPerson : featuredPeopleSample)
      {
         responseBuffer.putLong(featuredPerson.sortValue);
         if (featuredPerson.sortValue != FeedbactoryConstants.EndOfDataLong)
         {
            writePersonalFeedbackPersonProfile(featuredPerson.personProfile, responseBuffer);
            writePersonalFeedbackBasicSummary(featuredPerson.feedbackSummary, responseBuffer);
            responseBuffer.putLong(featuredPerson.creationTime);
         }
      }
   }


   private CriteriaFeedbackFeaturedItemsFilter readFeaturedFeedbackItemsFilter(final ReadableByteBuffer requestBuffer)
   {
      final byte criteriaTypeValue = requestBuffer.get();
      final PersonalFeedbackCriteriaType criteriaType = PersonalFeedbackCriteriaType.fromValue(criteriaTypeValue);
      if (criteriaType == null)
         throw new FeedbactorySecurityException(getClass(), "Invalid personal feedback criteria type value: " + criteriaTypeValue);

      final Set<PersonalFeedbackWebsite> filterWebsites = readFilterWebsites(criteriaType, requestBuffer);
      final Set<String> filterTags = readItemTags(requestBuffer, PersonalFeedbackConstants.MaximumPersonProfileSearchableTags);
      final long lastRetrievedSortValue = requestBuffer.getLong();
      final PersonalFeedbackPerson lastRetrievedItem;
      if (lastRetrievedSortValue != FeedbactoryConstants.NoTime)
         lastRetrievedItem = readPersonalFeedbackPerson(requestBuffer);
      else
         lastRetrievedItem = null;

      return new CriteriaFeedbackFeaturedItemsFilter(criteriaType, filterWebsites, filterTags, lastRetrievedSortValue, lastRetrievedItem);
   }


   private Set<PersonalFeedbackWebsite> readFilterWebsites(final PersonalFeedbackCriteriaType criteriaType, final ReadableByteBuffer requestBuffer)
   {
      /* Only featured items for enabled websites should be retrieved. This also explains why using an empty set to indicate 'no filter' won't work
       * between this network layer and the feedback manager.
       */
      final Set<PersonalFeedbackWebsite> criteriaTypeWebsites = PersonalFeedbackWebsite.getWebsites(criteriaType);
      final short numberOfFilterWebsites = requestBuffer.getShort();
      if ((numberOfFilterWebsites < 0) || (numberOfFilterWebsites > criteriaTypeWebsites.size()))
         throw new FeedbactorySecurityException(getClass(), "Invalid number of criteria feedback filter websites: " + numberOfFilterWebsites + " for criteria type: " + criteriaType);

      final Set<PersonalFeedbackWebsite> enabledWebsitesCopy = getEnabledWebsites();
      final Set<PersonalFeedbackWebsite> filterWebsites;

      if (numberOfFilterWebsites > 0)
      {
         filterWebsites = new PersonalFeedbackWebsiteSet();
         short websiteValue;
         PersonalFeedbackWebsite website;

         for (short websiteNumber = 0; websiteNumber < numberOfFilterWebsites; websiteNumber ++)
         {
            websiteValue = requestBuffer.getShort();
            website = PersonalFeedbackWebsite.fromValue(websiteValue);

            if (website == null)
               throw new FeedbactorySecurityException(getClass(), "Unknown personal feedback website value: " + websiteValue);
            else if (! website.getCriteriaTypes().contains(criteriaType))
               throw new FeedbactorySecurityException(getClass(), "Invalid criteria feedback filter website: " + website + " for criteria type: " + criteriaType);
            else if (filterWebsites.contains(website))
               throw new FeedbactorySecurityException(getClass(), "Duplicate website for criteria feedback featured items filter: " + website.getName());

            if (enabledWebsitesCopy.contains(website))
               filterWebsites.add(website);
         }
      }
      else
      {
         enabledWebsitesCopy.retainAll(criteriaTypeWebsites);
         filterWebsites = enabledWebsitesCopy;
      }

      return filterWebsites;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private ProcessedOperationStatus handleProcessGetPersonFeedbackSummaryRequest(final RequestUserSession userSession)
   {
      final PersonalFeedbackPerson person = readPersonalFeedbackPerson(userSession.requestBuffer);

      if (isWebsiteEnabled(person.getWebsite()))
      {
         writeBasicOperationStatus(BasicOperationStatus.OK, userSession.responseBuffer);
         writePersonFeedbackSummary(person, userSession.responseBuffer);
      }
      else
         writeBasicOperationStatus(BasicOperationStatus.Failed, userSession.responseBuffer);

      return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus handleProcessGetPersonFeedbackSubmissionRequest(final RequestUserSession userSession)
   {
      final PersonalFeedbackPerson person = readPersonalFeedbackPerson(userSession.requestBuffer);

      if (isWebsiteEnabled(person.getWebsite()))
      {
         writeBasicOperationStatus(BasicOperationStatus.OK, userSession.responseBuffer);
         final PersonalFeedbackSubmission feedbackSubmission = personalFeedbackManager.getPersonalFeedbackSubmission(userSession.account, person);
         writePersonalFeedbackSubmission(feedbackSubmission, userSession.responseBuffer);
      }
      else
         writeBasicOperationStatus(BasicOperationStatus.Failed, userSession.responseBuffer);

      return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus handleProcessAddPersonFeedbackSubmissionRequest(final RequestUserSession userSession)
   {
      final PersonalFeedbackPersonProfile personProfile = readPersonalFeedbackPersonProfile(userSession.requestBuffer);

      if (isWebsiteEnabled(personProfile.getWebsite()))
      {
         final PersonalFeedbackSubmission feedbackSubmission = readPersonalFeedbackSubmission(personProfile.person, userSession.requestBuffer);

         /* Get a lock on the submitting user account here, to prevent the state of the feedback submissions from the Feedbactory user account for this user from possibly
          * changing between the submission and the subsequent calculation of the basic summary. Meanwhile feedback submissions from other Feedbactory user accounts for
          * the person are unaffected, and any that occur may or may not be reflected in the returned feedback summary.
          */
         synchronized (userSession.account)
         {
            final PersonalFeedbackNode personFeedbackNode = personalFeedbackManager.addPersonalFeedbackSubmission(userSession.account, personProfile, feedbackSubmission);

            if (personFeedbackNode != null)
            {
               writeFeedbackSubmissionStatus(FeedbackSubmissionStatus.OK, userSession.responseBuffer);
               final PersonalFeedbackBasicSummary feedbackBasicSummary = personalFeedbackManager.getPersonalFeedbackBasicSummary(personFeedbackNode, personProfile.person.getWebsite().showFeedbackLessThanMinimumThreshold());

               writePersonalFeedbackBasicSummary(feedbackBasicSummary, userSession.responseBuffer);
            }
            else
               writeFeedbackSubmissionStatus(FeedbackSubmissionStatus.FailedTooManySubmissions, userSession.responseBuffer);
         }
      }
      else
         writeFeedbackSubmissionStatus(FeedbackSubmissionStatus.FailedWebsiteNotEnabled, userSession.responseBuffer);

      return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus handleProcessRemovePersonFeedbackSubmissionRequest(final RequestUserSession userSession)
   {
      final PersonalFeedbackPerson person = readPersonalFeedbackPerson(userSession.requestBuffer);

      if (isWebsiteEnabled(person.getWebsite()))
      {
         writeBasicOperationStatus(BasicOperationStatus.OK, userSession.responseBuffer);

         /* Get a lock on the submitting user account here, to prevent the state of the feedback submissions from the Feedbactory user account for this user from possibly
          * changing between the submission removal and the subsequent calculation of the basic summary. Meanwhile feedback submissions from other Feedbactory user accounts
          * for the person are unaffected, and any that occur may or may not be reflected in the returned feedback summary.
          */
         synchronized (userSession.account)
         {
            final PersonalFeedbackNode personFeedbackNode = personalFeedbackManager.removePersonalFeedbackSubmission(userSession.account, person);

            final PersonalFeedbackBasicSummary feedbackBasicSummary;

            if (personFeedbackNode != null)
               feedbackBasicSummary = personalFeedbackManager.getPersonalFeedbackBasicSummary(personFeedbackNode, person.getWebsite().showFeedbackLessThanMinimumThreshold());
            else
               feedbackBasicSummary = PersonalFeedbackBasicSummary.EmptyFeedbackBasicSummary;

            writePersonalFeedbackBasicSummary(feedbackBasicSummary, userSession.responseBuffer);
         }
      }
      else
         writeBasicOperationStatus(BasicOperationStatus.Failed, userSession.responseBuffer);

      return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus handleProcessFeedbackCategoryRequest(final RequestUserSession userSession)
   {
      final byte requestTypeValue = userSession.requestBuffer.get();
      final PersonalFeedbackRequestType requestType = PersonalFeedbackRequestType.fromValue(requestTypeValue);
      if (requestType == null)
      {
         final String message = "Invalid personal feedback request type value: " + requestTypeValue;
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

         return ProcessedOperationStatus.ErroneousRequest;
      }

      switch (requestType)
      {
         case GetPersonDetailedFeedbackSummary:
            return processGetPersonDetailedFeedbackSummary(userSession);
         case GetNewFeedbackItemsSample:
         case GetHotFeedbackItemsSample:
            return processGetFeaturedFeedbackItemsSample(requestType, userSession);

         default:
            throw new AssertionError("Unhandled personal feedback request type: " + requestType);
      }
   }


   private ProcessedOperationStatus processGetPersonDetailedFeedbackSummary(final RequestUserSession userSession)
   {
      if (userSession.sessionRequestType == SessionRequestType.EncryptedSessionRequest)
      {
         final String message = "Encrypted session used for get detailed personal feedback summary request";
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

         return ProcessedOperationStatus.ErroneousRequest;
      }

      final PersonalFeedbackPerson person = readPersonalFeedbackPerson(userSession.requestBuffer);

      if (isWebsiteEnabled(person.getWebsite()))
      {
         writeBasicOperationStatus(BasicOperationStatus.OK, userSession.responseBuffer);

         final PersonalFeedbackDetailedSummary detailedFeedbackSummary = personalFeedbackManager.getPersonalFeedbackDetailedSummary(person);
         writePersonalFeedbackDetailedSummary(detailedFeedbackSummary, userSession.responseBuffer);
      }
      else
         writeBasicOperationStatus(BasicOperationStatus.Failed, userSession.responseBuffer);

      return ProcessedOperationStatus.OK;
   }


   private ProcessedOperationStatus processGetFeaturedFeedbackItemsSample(final PersonalFeedbackRequestType requestType, final RequestUserSession userSession)
   {
      if (userSession.sessionRequestType == SessionRequestType.EncryptedSessionRequest)
      {
         final String message = "Encrypted session used for get featured feedback request";
         FeedbactoryLogger.logSecurityEvent(SecurityLogLevel.Medium, getClass(), message, userSession);

         return ProcessedOperationStatus.ErroneousRequest;
      }

      final CriteriaFeedbackFeaturedItemsFilter featuredItemsFilter = readFeaturedFeedbackItemsFilter(userSession.requestBuffer);

      final List<PersonalFeedbackFeaturedPerson> nextFeaturedItemsSample;
      if (requestType == PersonalFeedbackRequestType.GetNewFeedbackItemsSample)
         nextFeaturedItemsSample = personalFeedbackManager.getNextNewItemsSample(featuredItemsFilter);
      else if (requestType == PersonalFeedbackRequestType.GetHotFeedbackItemsSample)
         nextFeaturedItemsSample = personalFeedbackManager.getNextHotItemsSample(featuredItemsFilter);
      else
         throw new AssertionError("Unhandled criteria feedback featured items request type: " + requestType);

      writeFeaturedFeedbackItemsSample(nextFeaturedItemsSample, userSession.responseBuffer);

      return ProcessedOperationStatus.OK;
   }


   private String handleProcessConsoleCommand(final String[] arguments)
   {
      if (arguments.length == 0)
         return processDisplayWebsiteValues();
      else
      {
         final short websiteValue = Short.parseShort(arguments[0]);
         final PersonalFeedbackWebsite website = PersonalFeedbackWebsite.fromValue(websiteValue);
         if (website == null)
            throw new IllegalArgumentException("Invalid personal feedback website value: " + websiteValue);
         else if (arguments.length == 1)
            return processDisplayWebsiteFeedbackStatus(website);
         else if ((arguments.length == 2) && arguments[1].equals(WebsiteFeedbackOnCommandSwitch))
            return processEnableWebsiteForFeedback(website);
         else if ((arguments.length == 2) && arguments[1].equals(WebsiteFeedbackOffCommandSwitch))
            return processDisableWebsiteForFeedback(website);
         else
            return "Invalid command switch.";
      }
   }


   private String processDisplayWebsiteValues()
   {
      final Formatter formatter = new Formatter();
      final String headerOutputFormat = "%-30s%-10s%s%n%n";
      final String rowOutputFormat = "%-30s%-10d%s%n";

      formatter.format("%s%n%n", "Registered feedback categories:");

      formatter.format(headerOutputFormat, "Website", "Value", "Enabled");

      for (final PersonalFeedbackWebsite website : PersonalFeedbackWebsite.getWebsites())
         formatter.format(rowOutputFormat, website.getName(), website.getID(), (isWebsiteEnabled(website) ? "Yes" : "No"));

      return formatter.toString();
   }


   private String processDisplayWebsiteFeedbackStatus(final PersonalFeedbackWebsite website)
   {
      if (isWebsiteEnabled(website))
         return website.getName() + " is enabled for feedback.";
      else
         return website.getName() + " has been disabled for feedback.";
   }


   private String processEnableWebsiteForFeedback(final PersonalFeedbackWebsite website)
   {
      final boolean wasAdded = setWebsiteEnabled(website, true);

      if (wasAdded)
         return website.getName() + " has been enabled for feedback.";
      else
         return website.getName() + " is already enabled for feedback.";
   }


   private String processDisableWebsiteForFeedback(final PersonalFeedbackWebsite website)
   {
      final boolean wasRemoved = setWebsiteEnabled(website, false);

      if (wasRemoved)
         return website.getName() + " has been disabled for feedback.";
      else
         return website.getName() + " has already been disabled for feedback.";
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public void writeHandshakeResponse(final WritableByteBuffer responseBuffer)
   {
      handleWriteHandshakeResponse(responseBuffer);
   }


   @Override
   final public void writeItem(final FeedbackItem item, final WritableByteBuffer responseBuffer)
   {
      writePersonalFeedbackPerson((PersonalFeedbackPerson) item, responseBuffer);
   }


   @Override
   final public void writeItemProfile(final FeedbackItemProfile itemProfile, final WritableByteBuffer responseBuffer)
   {
      writePersonalFeedbackPersonProfile((PersonalFeedbackPersonProfile) itemProfile, responseBuffer);
   }


   @Override
   final public void writeItemFeedbackSubmission(final FeedbackSubmission feedbackSubmission, final WritableByteBuffer responseBuffer)
   {
      writePersonalFeedbackSubmission((PersonalFeedbackSubmission) feedbackSubmission, responseBuffer);
   }


   @Override
   final public void writeItemFeedbackSubmissionSummary(final FeedbackSubmission feedbackSubmission, final WritableByteBuffer responseBuffer)
   {
      writePersonalFeedbackSubmissionSummary((PersonalFeedbackSubmission) feedbackSubmission, responseBuffer);
   }


   @Override
   final public void writeItemFeedbackSummary(final FeedbackItem item, final WritableByteBuffer responseBuffer)
   {
      writePersonFeedbackSummary((PersonalFeedbackPerson) item, responseBuffer);
   }


   @Override
   final public ProcessedOperationStatus processGetItemFeedbackSummaryRequest(final RequestUserSession userSession)
   {
      return handleProcessGetPersonFeedbackSummaryRequest(userSession);
   }


   @Override
   final public ProcessedOperationStatus processGetItemFeedbackSubmissionRequest(final RequestUserSession userSession)
   {
      return handleProcessGetPersonFeedbackSubmissionRequest(userSession);
   }


   @Override
   final public ProcessedOperationStatus processAddItemFeedbackSubmissionRequest(final RequestUserSession userSession)
   {
      return handleProcessAddPersonFeedbackSubmissionRequest(userSession);
   }


   @Override
   final public ProcessedOperationStatus processRemoveItemFeedbackSubmissionRequest(final RequestUserSession userSession)
   {
      return handleProcessRemovePersonFeedbackSubmissionRequest(userSession);
   }


   @Override
   final public ProcessedOperationStatus processFeedbackCategoryRequest(final RequestUserSession userSession)
   {
      return handleProcessFeedbackCategoryRequest(userSession);
   }


   final public String processConsoleCommand(final String[] arguments)
   {
      return handleProcessConsoleCommand(arguments);
   }
}