
package com.feedbactory.client.core.feedback.personal;


import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackFeaturedPerson;
import java.util.List;


final public class PersonalFeedbackFeaturedItemsSampleResult
{
   final public NetworkRequestStatus requestStatus;
   final public List<PersonalFeedbackFeaturedPerson> featuredItemsSample;


   public PersonalFeedbackFeaturedItemsSampleResult(final NetworkRequestStatus requestStatus)
   {
      this.requestStatus = requestStatus;
      featuredItemsSample = null;
   }


   PersonalFeedbackFeaturedItemsSampleResult(final NetworkRequestStatus requestStatus, final List<PersonalFeedbackFeaturedPerson> featuredItemsSample)
   {
      this.requestStatus = requestStatus;
      this.featuredItemsSample = featuredItemsSample;
   }
}