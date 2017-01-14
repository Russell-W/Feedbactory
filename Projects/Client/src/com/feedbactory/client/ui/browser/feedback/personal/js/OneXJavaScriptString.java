
package com.feedbactory.client.ui.browser.feedback.personal.js;


import com.feedbactory.client.ui.browser.feedback.js.SharedJavaScriptString;

abstract public class OneXJavaScriptString
{
   static private final String library;

   static
   {
      final StringBuilder scriptBuilder = new StringBuilder(SharedJavaScriptString.library.length() + 10000);

      scriptBuilder.append("return (function()");
      scriptBuilder.append("{");

      scriptBuilder.append(SharedJavaScriptString.library);

      /* Start paste area.
       */
      scriptBuilder.append("function getActivePhotoData()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var activePhotoData = [null, null, null, null];");

      scriptBuilder.append("   activePhotoData[0] = getPhotoID();");
      scriptBuilder.append("   if (activePhotoData[0] !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      activePhotoData[1] = getPhotographerID(activePhotoData[0]);");
      scriptBuilder.append("      if (activePhotoData[1] !== null)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         activePhotoData[2] = getPhotoDisplayName();");
      scriptBuilder.append("         if (activePhotoData[2] !== null)");
      scriptBuilder.append("            activePhotoData[3] = getPhotoThumbnailID(activePhotoData[0]);");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   if ((activePhotoData[0] !== null) && (activePhotoData[1] !== null) && (activePhotoData[2] !== null) && (activePhotoData[3] !== null))");
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


      scriptBuilder.append("function getPhotographerID(photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photographerElement = document.getElementById('loadimg_userid_' + photoID);");
      scriptBuilder.append("   if ((photographerElement !== null) && (photographerElement.tagName === 'INPUT'))");
      scriptBuilder.append("      return photographerElement.value;");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoDisplayName()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTitleElement = document.getElementById('phototitle');");
      scriptBuilder.append("   if ((photoTitleElement !== null) && (photoTitleElement.tagName === 'SPAN'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photoTitle = textContent(photoTitleElement).trim();");

      scriptBuilder.append("      var slideshowNameElement = document.getElementById('slideshow_name');");
      scriptBuilder.append("      if ((slideshowNameElement !== null) && (slideshowNameElement.tagName === 'DIV'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var photographerName = textContent(slideshowNameElement);");
      scriptBuilder.append("         var photographerNameStartIndex = photographerName.indexOf('by ');");

      scriptBuilder.append("         if (photographerNameStartIndex !== -1)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            photographerNameStartIndex += 3;");
      scriptBuilder.append("            photographerName = photographerName.substring(photographerNameStartIndex).trim();");
      scriptBuilder.append("            return [photoTitle, photographerName];");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoThumbnailID(photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photographerElement = document.getElementById('loadimg_src_ld_' + photoID);");
      scriptBuilder.append("   if ((photographerElement !== null) && (photographerElement.tagName === 'INPUT'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var lowDefinitionPhotoURL = photographerElement.value;");
      scriptBuilder.append("      var startIndex = lowDefinitionPhotoURL.lastIndexOf('/');");
      scriptBuilder.append("      if (startIndex !== -1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         startIndex ++;");
      scriptBuilder.append("         var endIndex = lowDefinitionPhotoURL.indexOf('-ld.jpg', startIndex);");
      scriptBuilder.append("         if (endIndex !== -1)");
      scriptBuilder.append("            return lowDefinitionPhotoURL.substring(startIndex, endIndex);");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function main()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var result = [true, null];");

      scriptBuilder.append("   try");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (hostCheck('1x.com'))");
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


   private OneXJavaScriptString()
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