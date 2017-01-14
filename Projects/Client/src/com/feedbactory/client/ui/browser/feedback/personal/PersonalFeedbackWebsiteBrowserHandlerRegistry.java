
package com.feedbactory.client.ui.browser.feedback.personal;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


final public class PersonalFeedbackWebsiteBrowserHandlerRegistry
{
   final private List<PersonalFeedbackWebsiteBrowserHandler> serviceHandlers;


   PersonalFeedbackWebsiteBrowserHandlerRegistry(final PersonalFeedbackBrowserEventManager browserEventManager)
   {
      serviceHandlers = initialiseServiceHandlers(browserEventManager);
   }


   private List<PersonalFeedbackWebsiteBrowserHandler> initialiseServiceHandlers(final PersonalFeedbackBrowserEventManager browserEventManager)
   {
      final List<PersonalFeedbackWebsiteBrowserHandler> browserHandlersBuilder = new ArrayList<PersonalFeedbackWebsiteBrowserHandler>(11);

      browserHandlersBuilder.add(new FlickrHandler(browserEventManager));
      browserHandlersBuilder.add(new FiveHundredPXHandler(browserEventManager));
      browserHandlersBuilder.add(new OneXHandler(browserEventManager));
      browserHandlersBuilder.add(new SmugMugHandler(browserEventManager));
      browserHandlersBuilder.add(new PhotoShelterHandler(browserEventManager));
      browserHandlersBuilder.add(new SeventyTwoDPIHandler(browserEventManager));
      browserHandlersBuilder.add(new YouPicHandler(browserEventManager));
      browserHandlersBuilder.add(new IpernityHandler(browserEventManager));
      browserHandlersBuilder.add(new ViewBugHandler(browserEventManager));
      browserHandlersBuilder.add(new PixotoHandler(browserEventManager));
      browserHandlersBuilder.add(new TripleJHandler(browserEventManager));

      return Collections.unmodifiableList(browserHandlersBuilder);
   }
         

   final List<PersonalFeedbackWebsiteBrowserHandler> getServiceHandlers()
   {
      return serviceHandlers;
   }
}