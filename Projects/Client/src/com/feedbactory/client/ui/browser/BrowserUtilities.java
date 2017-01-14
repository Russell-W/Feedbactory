
package com.feedbactory.client.ui.browser;


abstract public class BrowserUtilities
{
   private BrowserUtilities()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private String handleGetHostName(final String fullURL)
   {
      int hostNameStartCharacterIndex = fullURL.indexOf("://");

      if (hostNameStartCharacterIndex != -1)
      {
         hostNameStartCharacterIndex += 3;

         final int domainNameEndCharacterIndex = fullURL.indexOf('/', hostNameStartCharacterIndex);

         final String domainString;
         if (domainNameEndCharacterIndex == -1)
            domainString = fullURL.substring(hostNameStartCharacterIndex);
         else
            domainString = fullURL.substring(hostNameStartCharacterIndex, domainNameEndCharacterIndex);

         final int portStartIndex = domainString.indexOf(':');
         if (portStartIndex == -1)
            return domainString;
         else
            return domainString.substring(0, portStartIndex);
      }

      return "";
   }


   static private String handleGetPathName(final String fullURL)
   {
      int pathStartIndex = fullURL.indexOf("://");

      if (pathStartIndex != -1)
      {
         pathStartIndex += 3;
         pathStartIndex = fullURL.indexOf('/', pathStartIndex);

         if (pathStartIndex != -1)
         {
            final int searchStartIndex = fullURL.indexOf('?', pathStartIndex);

            if (searchStartIndex != -1)
               return fullURL.substring(pathStartIndex, searchStartIndex);
            else
               return fullURL.substring(pathStartIndex);
         }
      }

      return "";
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public boolean isAnHTTPLink(final String url)
   {
      final String lowerCasedPossibleLink = url.toLowerCase();
      return lowerCasedPossibleLink.startsWith("http://") || lowerCasedPossibleLink.startsWith("https://");
   }


   static public String getHostName(final String fullURL)
   {
      // Will return an empty string for file-type URLs, eg. file:///path/to/file.ext
      return handleGetHostName(fullURL);
   }


   static public String getPathName(final String fullURL)
   {
      // Path name includes the leading slash, to match JavaScript conventions.
      return handleGetPathName(fullURL);
   }
}