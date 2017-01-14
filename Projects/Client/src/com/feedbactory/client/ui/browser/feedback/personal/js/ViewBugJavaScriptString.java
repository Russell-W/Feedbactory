
package com.feedbactory.client.ui.browser.feedback.personal.js;

import com.feedbactory.client.ui.browser.feedback.js.SharedJavaScriptString;


abstract public class ViewBugJavaScriptString
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
      scriptBuilder.append("   var activePhotoData = [null, null, null, null, null];");

      scriptBuilder.append("   var rootDocument = getIFrameMainPhotoDocumentElement();");
      scriptBuilder.append("   if (rootDocument === null)");
      scriptBuilder.append("      rootDocument = document;");

      scriptBuilder.append("   activePhotoData[0] = getPhotoID(rootDocument);");
      scriptBuilder.append("   if (activePhotoData[0] !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photographerDetailsElement = getPhotographerDetailsElement(rootDocument);");
      scriptBuilder.append("      if (photographerDetailsElement !== null)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         activePhotoData[1] = getPhotographerID(photographerDetailsElement);");
      scriptBuilder.append("         if (activePhotoData[1] !== null)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            activePhotoData[2] = getPhotoDisplayName(rootDocument, photographerDetailsElement);");
      scriptBuilder.append("            if (activePhotoData[2] !== null)");
      scriptBuilder.append("            {");
      scriptBuilder.append("               activePhotoData[3] = getPhotoThumbnailID(rootDocument, activePhotoData[0]);");
      scriptBuilder.append("               if (activePhotoData[3] !== null)");
      scriptBuilder.append("               {");
      scriptBuilder.append("                  activePhotoData[4] = getPhotoTags(rootDocument);");
      scriptBuilder.append("                  if (activePhotoData[4] !== null)");
      scriptBuilder.append("                     return activePhotoData;");
      scriptBuilder.append("               }");
      scriptBuilder.append("            }");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getIFrameMainPhotoDocumentElement()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var iframeElement = document.getElementById('photoframe');");
      scriptBuilder.append("   if ((iframeElement !== null) && (iframeElement.tagName === 'IFRAME'))");
      scriptBuilder.append("      return iframeElement.contentDocument;");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoID(rootDocument)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoWrapperElement = rootDocument.getElementById('photo-wrapper');");
      scriptBuilder.append("   if ((photoWrapperElement !== null) && (photoWrapperElement.tagName === 'DIV'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var protectPhotoElement = getFirstElementByClassNames(['protect-photo'], photoWrapperElement);");
      scriptBuilder.append("      if ((protectPhotoElement !== null) && (protectPhotoElement.tagName === 'DIV'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var photoID = protectPhotoElement.getAttribute('media_id');");
      scriptBuilder.append("         if (photoID !== null)");
      scriptBuilder.append("            return photoID.trim();");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotographerDetailsElement(rootDocument)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photographerDetailsElement = getFirstElementByClassNames(['main_content', 'topphoto', 'profile', 'details'], rootDocument);");
      scriptBuilder.append("   if ((photographerDetailsElement !== null) && (photographerDetailsElement.tagName === 'DIV'))");
      scriptBuilder.append("      return photographerDetailsElement;");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotographerID(photographerDetailsElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photographerLinkElements = photographerDetailsElement.getElementsByTagName('a');");
      scriptBuilder.append("   if (photographerLinkElements.length === 1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var userID = photographerLinkElements[0].getAttribute('user_id');");
      scriptBuilder.append("      if (userID !== null)");
      scriptBuilder.append("         return userID.trim();");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoDisplayName(rootDocument, photographerDetailsElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoElement = rootDocument.getElementById('main_image');");
      scriptBuilder.append("   if ((photoElement !== null) && (photoElement.tagName === 'IMG'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var title = photoElement.alt.trim();");
      scriptBuilder.append("      var photographerElements = photographerDetailsElement.getElementsByTagName('H6');");
      scriptBuilder.append("      if (photographerElements.length === 1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var photographerName = textContent(photographerElements[0]).trim();");
      scriptBuilder.append("         if (photographerName.length > 0)");
      scriptBuilder.append("            return [title, photographerName];");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoThumbnailID(rootDocument, photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoElement = rootDocument.getElementById('main_image');");
      scriptBuilder.append("   if ((photoElement !== null) && (photoElement.tagName === 'IMG'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var rawPhotoURL = trimURLArgument(photoElement.src);");

      scriptBuilder.append("      var thumbnailIDStartIndex = rawPhotoURL.indexOf('/media/mediafiles/');");
      scriptBuilder.append("      if (thumbnailIDStartIndex !== -1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         thumbnailIDStartIndex += 18;");
      scriptBuilder.append("         var thumbnailIDEndIndex = rawPhotoURL.lastIndexOf('/' + photoID + '_');");
      scriptBuilder.append("         if (thumbnailIDEndIndex !== -1)");
      scriptBuilder.append("            return rawPhotoURL.substring(thumbnailIDStartIndex, thumbnailIDEndIndex);");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoTags(rootDocument)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTags = null;");

      scriptBuilder.append("   var photoInfoElement = rootDocument.getElementById('photo-info');");
      scriptBuilder.append("   if ((photoInfoElement !== null) && (photoInfoElement.tagName === 'DIV'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoTags = [];");

      scriptBuilder.append("      var photoTagsParentElement = getFirstElementByClassNames(['tags'], photoInfoElement);");
      scriptBuilder.append("      if ((photoTagsParentElement !== null) && (photoTagsParentElement.tagName === 'DIV'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var photoTagElements = photoTagsParentElement.getElementsByTagName('a');");
      scriptBuilder.append("         var photoTag;");

      scriptBuilder.append("         for (var tagIndex = 0; tagIndex < photoTagElements.length; tagIndex ++)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            photoTag = textContent(photoTagElements[tagIndex]);");
      scriptBuilder.append("            if (photoTag.length > 0)");
      scriptBuilder.append("               photoTags[photoTags.length] = photoTag;");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return photoTags;");
      scriptBuilder.append("}");


      scriptBuilder.append("function main()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var result = [true, null];");

      scriptBuilder.append("   try");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (hostCheck('viewbug.com'))");
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


   private ViewBugJavaScriptString()
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