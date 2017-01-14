/* Memos:
 * - Provides a mechanism to split web traffic based on whether or not a URL is loaded within Feedbactory.
 * - URLs loaded from within Feedbactory are forwarded to an encoded URL, otherwise they will be handled by Feedbactory's 'traffic cop' html page.
 */

package com.feedbactory.client.ui.browser;


import com.feedbactory.client.core.FeedbactoryClientConstants;
import com.feedbactory.client.ui.browser.BrowserEngineManager.BrowserLocationController;
import com.feedbactory.client.ui.browser.event.BrowserLocationEvent;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.logging.Level;
import java.util.logging.Logger;


final class FeedbactoryBrowserLocationController extends BrowserLocationController
{
   // For non-development, the traffic cop URL must comprise the full path and not simply be a suffix of a longer path.
   static final private String TrafficCopURL = "/utility/trafficCop.html";
   static final private String EncodedURLParameterKey = "url=";


   private boolean handleBrowserPageLocationChanging(final BrowserLocationEvent pageLocationChangingEvent)
   {
      if (FeedbactoryClientConstants.IsDevelopmentProfile)
         return processDevelopmentTrafficCop(pageLocationChangingEvent);
      else
         return processTrafficCop(pageLocationChangingEvent);
   }


   private boolean processDevelopmentTrafficCop(final BrowserLocationEvent pageLocationChangingEvent)
   {
      final String url = pageLocationChangingEvent.URL;

      if (url.startsWith("file:///"))
      {
         final int urlSearchStartIndex = url.indexOf('?');
         if (urlSearchStartIndex != -1)
         {
            final int trafficCopMarkerStartIndex = urlSearchStartIndex - TrafficCopURL.length();
            if ((trafficCopMarkerStartIndex >= 0) && (url.indexOf(TrafficCopURL, trafficCopMarkerStartIndex) == trafficCopMarkerStartIndex))
            {
               final int urlParameterIndex = url.indexOf(EncodedURLParameterKey, urlSearchStartIndex + 1);
               if (urlParameterIndex != -1)
               {
                  final String decodedURL = getDecodedURL(url.substring(urlParameterIndex + EncodedURLParameterKey.length()));
                  pageLocationChangingEvent.browserService.openURL(decodedURL);
                  return false;
               }
            }
         }
      }

      return true;
   }


   private boolean processTrafficCop(final BrowserLocationEvent pageLocationChangingEvent)
   {
      final String url = pageLocationChangingEvent.URL;

      String domainNameSuffix = BrowserUtilities.getHostName(url).toLowerCase();
      if (domainNameSuffix.startsWith("www."))
         domainNameSuffix = domainNameSuffix.substring(4);

      if (domainNameSuffix.equals("feedbactory.com"))
      {
         final String pathName = BrowserUtilities.getPathName(url);
         if (pathName.equals(TrafficCopURL))
         {
            final int urlSearchStartIndex = url.indexOf('?');
            if (urlSearchStartIndex != -1)
            {
               final int urlParameterIndex = url.indexOf(EncodedURLParameterKey, urlSearchStartIndex + 1);
               if (urlParameterIndex != -1)
               {
                  final String decodedURL = getDecodedURL(url.substring(urlParameterIndex + EncodedURLParameterKey.length()));
                  pageLocationChangingEvent.browserService.openURL(decodedURL);
                  return false;
               }
            }
         }
      }

      return true;
   }


   private String getDecodedURL(final String encodedURL)
   {
      try
      {
         return URLDecoder.decode(encodedURL, "utf-8");
      }
      catch (final UnsupportedEncodingException unsupportedEncodingException)
      {
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Redirect URL decoding error", unsupportedEncodingException);
         return BrowserEngineManager.BlankPageUrl;
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final boolean browserPageLocationChanging(final BrowserLocationEvent pageLocationChangingEvent)
   {
      return handleBrowserPageLocationChanging(pageLocationChangingEvent);
   }
}