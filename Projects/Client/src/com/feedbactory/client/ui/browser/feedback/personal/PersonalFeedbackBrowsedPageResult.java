
package com.feedbactory.client.ui.browser.feedback.personal;


import com.feedbactory.client.ui.browser.feedback.BrowsedPageResult;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;


final public class PersonalFeedbackBrowsedPageResult implements BrowsedPageResult
{
   static final public PersonalFeedbackBrowsedPageResult BrowserPageNotReady = new PersonalFeedbackBrowsedPageResult(false);
   static final public PersonalFeedbackBrowsedPageResult NoPersonProfile = new PersonalFeedbackBrowsedPageResult(true);

   final private boolean isBrowserReadyStateComplete;
   final private PersonalFeedbackPersonProfile personProfile;


   public PersonalFeedbackBrowsedPageResult(final PersonalFeedbackPersonProfile personProfile)
   {
      this.isBrowserReadyStateComplete = true;
      this.personProfile = personProfile;
   }


   private PersonalFeedbackBrowsedPageResult(final boolean isBrowserReadyStateComplete)
   {
      this.isBrowserReadyStateComplete = isBrowserReadyStateComplete;
      this.personProfile = null;
   }


   @Override
   final public boolean isBrowserReadyStateComplete()
   {
      return isBrowserReadyStateComplete;
   }


   @Override
   final public PersonalFeedbackPersonProfile getFeedbackItemProfile()
   {
      return personProfile;
   }
}