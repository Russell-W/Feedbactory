
package com.feedbactory.client.ui.browser.feedback.personal.js;


import com.feedbactory.client.ui.browser.feedback.js.SharedJavaScriptString;

abstract public class SeventyTwoDPIJavaScriptString
{
   static private final String library;

   static
   {
      final StringBuilder scriptBuilder = new StringBuilder(SharedJavaScriptString.library.length() + 5000);

      scriptBuilder.append("return (function()");
      scriptBuilder.append("{");

      scriptBuilder.append(SharedJavaScriptString.library);

      /* Start paste area.
       */
      scriptBuilder.append("function getActivePhotoData()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var activePhotoData = [null, null, null];");

      scriptBuilder.append("   activePhotoData[0] = getPhotoID();");
      scriptBuilder.append("   if (activePhotoData[0] !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var headerElement = document.getElementsByTagName('head')[0];");
      scriptBuilder.append("      var metaTags = headerElement.getElementsByTagName('meta');");

      scriptBuilder.append("      if (checkMetaTagPhotoID(metaTags, activePhotoData[0]))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         activePhotoData[1] = getPhotographerID();");
      scriptBuilder.append("         if (activePhotoData[1] !== null)");
      scriptBuilder.append("            activePhotoData[2] = getPhotoDisplayName();");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   if ((activePhotoData[0] !== null) && (activePhotoData[1] !== null) && (activePhotoData[2] !== null))");
      scriptBuilder.append("      return activePhotoData;");
      scriptBuilder.append("   else");
      scriptBuilder.append("      return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoID()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var pathname = window.location.pathname;");

      scriptBuilder.append("   if (pathname.indexOf('/photo/') === 0)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photoIDStartIndex = 7;");
      scriptBuilder.append("      var photoIDEndIndex = pathname.indexOf('/', photoIDStartIndex);");
      scriptBuilder.append("      if (photoIDEndIndex === -1)");
      scriptBuilder.append("         return pathname.substring(photoIDStartIndex);");
      scriptBuilder.append("      else");
      scriptBuilder.append("         return pathname.substring(photoIDStartIndex, photoIDEndIndex);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function checkMetaTagPhotoID(metaTags, photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var metaTagElement;");

      scriptBuilder.append("   for (var tagIndex = 0; tagIndex < metaTags.length; tagIndex ++)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      metaTagElement = metaTags[tagIndex];");
      scriptBuilder.append("      if ((metaTagElement.getAttribute('property') === 'og:url') &&");
      scriptBuilder.append("          (metaTagElement.content.endsWith('/' + photoID) || (metaTagElement.content.indexOf('/' + photoID + '/') !== -1)))");
      scriptBuilder.append("         return true;");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return false;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotographerID()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var nameLinkElement = getFirstElementByClassNames(['namelink']);");
      scriptBuilder.append("   if ((nameLinkElement !== null) && (nameLinkElement.tagName === 'A'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photographerIDStartIndex = nameLinkElement.href.lastIndexOf('gallery/');");
      scriptBuilder.append("      if (photographerIDStartIndex !== -1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         photographerIDStartIndex += 8;");
      scriptBuilder.append("         var photographerID = nameLinkElement.href.substring(photographerIDStartIndex);");
      scriptBuilder.append("         if (photographerID.endsWith('/'))");
      scriptBuilder.append("            photographerID = photographerID.substring(0, photographerID.length - 1);");

      scriptBuilder.append("         return photographerID;");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoDisplayName()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTitleElement = getFirstElementByClassNames(['phototitle']);");
      scriptBuilder.append("   if ((photoTitleElement !== null) && (photoTitleElement.tagName === 'TD'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var rawDisplayName = textContent(photoTitleElement);");

      scriptBuilder.append("      var photographerNameElement = getFirstElementByClassNames(['medtxt'], photoTitleElement);");
      scriptBuilder.append("      if ((photographerNameElement !== null) && (photographerNameElement.tagName === 'SPAN'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var photographerName = textContent(photographerNameElement);");
      scriptBuilder.append("         var photographerNameStartIndex = rawDisplayName.indexOf(photographerName);");
      scriptBuilder.append("         if (photographerNameStartIndex !== -1)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            var photoTitle = rawDisplayName.substring(0, photographerNameStartIndex).trim();");
      scriptBuilder.append("            if (photoTitle.indexOf('\"') === 0)");
      scriptBuilder.append("               photoTitle = photoTitle.substring(1);");

      scriptBuilder.append("            if (photoTitle.endsWith('\"'))");
      scriptBuilder.append("               photoTitle = photoTitle.substring(0, photoTitle.length - 1);");

      scriptBuilder.append("            photoTitle = photoTitle.trim();");

      scriptBuilder.append("            if (photoTitle === 'Untitled')");
      scriptBuilder.append("               photoTitle = '';");

      scriptBuilder.append("            var copyrightIndex = photographerName.indexOf('© ');");
      scriptBuilder.append("            if (copyrightIndex !== -1)");
      scriptBuilder.append("            {");
      scriptBuilder.append("               photographerName = photographerName.substring(copyrightIndex + 2).trim();");
      scriptBuilder.append("               return [photoTitle, photographerName];");
      scriptBuilder.append("            }");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function main()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var result = [true, null];");

      scriptBuilder.append("   try");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (hostCheck('72dpi.com'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         if (document.readyState === 'complete')");
      scriptBuilder.append("            result[1] = getActivePhotoData();");
      scriptBuilder.append("         else");
      scriptBuilder.append("            result[0] = false;");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");
      scriptBuilder.append("   catch (exception)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (debug)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         if (objectExists(exception, 'stack'))");
      scriptBuilder.append("            consoleError(exception.stack.toString());");
      scriptBuilder.append("         else");
      scriptBuilder.append("            consoleError(exception.toString());");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return result;");
      scriptBuilder.append("}");
      /* End paste area.
       */

      scriptBuilder.append("return main();");

      scriptBuilder.append("})();");

      library = scriptBuilder.toString();
   }


   private SeventyTwoDPIJavaScriptString()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public String getLiveExecutor()
   {
      return library;
   }
}