
package com.feedbactory.client.ui.browser.feedback.personal.js;


import com.feedbactory.client.ui.browser.feedback.js.SharedJavaScriptString;

abstract public class PixotoJavaScriptString
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
      scriptBuilder.append("   if (isStandalonePhotoPage())");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var rootElement = getStandalonePhotoRootElement();");
      scriptBuilder.append("      if (rootElement !== null)");
      scriptBuilder.append("         return getStandaloneActivePhotoData(rootElement);");
      scriptBuilder.append("   }");
      scriptBuilder.append("   else");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var overlaidPhotoElement = getOverlaidPhotoRootElement();");
      scriptBuilder.append("      if (overlaidPhotoElement !== null)");
      scriptBuilder.append("         return getOverlaidActivePhotoData(overlaidPhotoElement);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function isStandalonePhotoPage()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return hasClassName(document.body, 'image-detail-pg');");
      scriptBuilder.append("}");


      scriptBuilder.append("function getStandalonePhotoRootElement()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var rootElement = document.getElementById('main');");
      scriptBuilder.append("   if ((rootElement !== null) && (rootElement.tagName === 'DIV'))");
      scriptBuilder.append("      return rootElement;");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getStandaloneActivePhotoData(rootElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var activePhotoData = [null, null, null, null, null];");

      scriptBuilder.append("   activePhotoData[0] = getPhotoID();");
      scriptBuilder.append("   if (activePhotoData[0] !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var containerElement = document.getElementById('container');");
      scriptBuilder.append("      if ((containerElement !== null) && (containerElement.tagName === 'DIV'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var photographerInformationElement = getPhotographerInformationElement(containerElement);");
      scriptBuilder.append("         if (photographerInformationElement !== null)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            activePhotoData[1] = getPhotographerID(photographerInformationElement);");
      scriptBuilder.append("            if (activePhotoData[1] !== null)");
      scriptBuilder.append("            {");
      scriptBuilder.append("               activePhotoData[2] = getPhotoDisplayName(photographerInformationElement);");
      scriptBuilder.append("               if (activePhotoData[2] !== null)");
      scriptBuilder.append("               {");
      scriptBuilder.append("                  var photoURL = getStandalonePhotoURL();");
      scriptBuilder.append("                  activePhotoData[3] = getPhotoThumbnailURLIDs(photoURL);");
      scriptBuilder.append("                  if (activePhotoData[3] !== null)");
      scriptBuilder.append("                  {");
      scriptBuilder.append("                     activePhotoData[4] = getPhotoTags(rootElement);");
      scriptBuilder.append("                     if (activePhotoData[4] !== null)");
      scriptBuilder.append("                        return activePhotoData;");
      scriptBuilder.append("                  }");
      scriptBuilder.append("               }");
      scriptBuilder.append("            }");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoID()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var pathname = window.location.pathname;");
      scriptBuilder.append("   var lastSlashIndex = pathname.lastIndexOf('/');");
      scriptBuilder.append("   var lastHyphenIndex = pathname.lastIndexOf('-');");

      scriptBuilder.append("   var photoIDStartIndex = Math.max(lastSlashIndex, lastHyphenIndex);");
      scriptBuilder.append("   if (photoIDStartIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoIDStartIndex ++;");
      scriptBuilder.append("      var photoID = pathname.substring(photoIDStartIndex);");

      scriptBuilder.append("      if (isIntegerString(photoID))");
      scriptBuilder.append("         return photoID;");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function isIntegerString(argument)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var character;");

      scriptBuilder.append("   for (var charIndex = 0; charIndex < argument.length; charIndex ++)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      character = argument.charAt(charIndex);");
      scriptBuilder.append("      if ((character < '0') || (character > '9'))");
      scriptBuilder.append("         return false;");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return true;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotographerInformationElement(rootElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var informationRootElement = getFirstElementByClassNames(['image-title-bar', 'image-title-bar-inner', 'img-owner'], rootElement);");
      scriptBuilder.append("   if ((informationRootElement !== null) && (informationRootElement.tagName === 'DIV'))");
      scriptBuilder.append("      return informationRootElement;");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotographerID(photographerInformationElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var ownerLinkElement = getFirstElementByClassNames(['owner-link'], photographerInformationElement);");
      scriptBuilder.append("   if ((ownerLinkElement !== null) && (ownerLinkElement.tagName === 'A'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photographerIDURL = trimURLArgument(ownerLinkElement.href);");
      scriptBuilder.append("      var photographerIDStartIndex = photographerIDURL.lastIndexOf('/');");
      scriptBuilder.append("      if (photographerIDStartIndex !== -1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         photographerIDStartIndex ++;");
      scriptBuilder.append("         return photographerIDURL.substring(photographerIDStartIndex);");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoDisplayName(photographerInformationElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTitleElement = getFirstElementByClassNames(['image-title'], photographerInformationElement);");
      scriptBuilder.append("   var title;");

      scriptBuilder.append("   if ((photoTitleElement !== null) && (photoTitleElement.tagName === 'H1'))");
      scriptBuilder.append("      title = textContent(photoTitleElement).trim();");
      scriptBuilder.append("   else");
      scriptBuilder.append("   {");
      scriptBuilder.append("      title = '';");
      scriptBuilder.append("   }");

      scriptBuilder.append("   var ownerLinkElement = getFirstElementByClassNames(['owner-link'], photographerInformationElement);");
      scriptBuilder.append("   if ((ownerLinkElement !== null) && (ownerLinkElement.tagName === 'A'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photographerName = textContent(ownerLinkElement).trim();");
      scriptBuilder.append("      if (photographerName.length > 0)");
      scriptBuilder.append("         return [title, photographerName];");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getStandalonePhotoURL()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoElement = document.getElementById('theImage');");
      scriptBuilder.append("   if ((photoElement !== null) && (photoElement.tagName === 'IMG'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      return trimURLArgument(photoElement.src);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoThumbnailURLIDs(photoURL)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var stringStartIndex = photoURL.indexOf('://lh');");
      scriptBuilder.append("   if (stringStartIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      stringStartIndex += 5;");

      scriptBuilder.append("      var serverID = null;");

      scriptBuilder.append("      var stringEndIndex = photoURL.indexOf('.googleusercontent.com/', stringStartIndex);");
      scriptBuilder.append("      if (stringEndIndex !== -1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         serverID = photoURL.substring(stringStartIndex, stringEndIndex);");
      scriptBuilder.append("         stringStartIndex = stringEndIndex + 23;");
      scriptBuilder.append("      }");
      scriptBuilder.append("      else");
      scriptBuilder.append("      {");
      scriptBuilder.append("         stringEndIndex = photoURL.indexOf('.ggpht.com/', stringStartIndex);");
      scriptBuilder.append("         if (stringEndIndex !== -1)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            serverID = photoURL.substring(stringStartIndex, stringEndIndex);");
      scriptBuilder.append("            stringStartIndex = stringEndIndex + 11;");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");

      scriptBuilder.append("      if (serverID !== null)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         stringEndIndex = photoURL.lastIndexOf('=');");

      scriptBuilder.append("         if ((stringEndIndex !== -1) && (photoURL.indexOf('/', stringStartIndex) === -1))");
      scriptBuilder.append("            return [serverID, photoURL.substring(stringStartIndex, stringEndIndex)];");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoTags(rootElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTags = null;");

      scriptBuilder.append("   var photoTagsParentElement = getFirstElementByClassNames(['main-content', 'image-attributes', 'tags-list', 'img-tags-list'], rootElement);");
      scriptBuilder.append("   if ((photoTagsParentElement !== null) && (photoTagsParentElement.tagName === 'UL'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoTags = [];");

      scriptBuilder.append("      var photoTagElements = getElementsByClassNames(['tag'], photoTagsParentElement);");
      scriptBuilder.append("      var photoTag;");

      scriptBuilder.append("      for (var tagIndex = 0; tagIndex < photoTagElements.length; tagIndex ++)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         photoTag = textContent(photoTagElements[tagIndex]);");
      scriptBuilder.append("         if (photoTag.length > 0)");
      scriptBuilder.append("            photoTags[photoTags.length] = photoTag;");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return photoTags;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getOverlaidPhotoRootElement()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var overlaidPhotoElement = document.getElementById('image-1-details');");
      scriptBuilder.append("   if ((overlaidPhotoElement !== null) && (overlaidPhotoElement.tagName === 'DIV'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var overlaidPhotoElementParent = overlaidPhotoElement.parentNode;");

      scriptBuilder.append("      if ((overlaidPhotoElementParent !== null) && (overlaidPhotoElementParent.nodeType === 1) && (overlaidPhotoElementParent.tagName === 'DIV') &&");
      scriptBuilder.append("          hasClassName(overlaidPhotoElementParent, 'image-detail-overlay') && (overlaidPhotoElementParent.style.display === 'block'))");
      scriptBuilder.append("         return overlaidPhotoElementParent;");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getOverlaidActivePhotoData(overlaidPhotoElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var activePhotoData = [null, null, null, null, null];");

      scriptBuilder.append("   activePhotoData[0] = getPhotoID();");
      scriptBuilder.append("   if (activePhotoData[0] !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photographerInformationElement = getPhotographerInformationElement(overlaidPhotoElement);");
      scriptBuilder.append("      if (photographerInformationElement !== null)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         activePhotoData[1] = getPhotographerID(photographerInformationElement);");
      scriptBuilder.append("         if (activePhotoData[1] !== null)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            activePhotoData[2] = getPhotoDisplayName(photographerInformationElement);");
      scriptBuilder.append("            if (activePhotoData[2] !== null)");
      scriptBuilder.append("            {");
      scriptBuilder.append("               var photoURL = getOverlaidPhotoURL();");
      scriptBuilder.append("               activePhotoData[3] = getPhotoThumbnailURLIDs(photoURL);");
      scriptBuilder.append("               if (activePhotoData[3] !== null)");
      scriptBuilder.append("               {");
      scriptBuilder.append("                  activePhotoData[4] = getPhotoTags(overlaidPhotoElement);");
      scriptBuilder.append("                  if (activePhotoData[4] !== null)");
      scriptBuilder.append("                     return activePhotoData;");
      scriptBuilder.append("               }");
      scriptBuilder.append("            }");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getOverlaidPhotoURL()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoElement = document.getElementById('DETAIL_IMAGE');");
      scriptBuilder.append("   if ((photoElement !== null) && (photoElement.tagName === 'IMG'))");
      scriptBuilder.append("      return trimURLArgument(photoElement.src);");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function main()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var result = [true, null];");

      scriptBuilder.append("   try");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (hostCheck('pixoto.com'))");
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


   private PixotoJavaScriptString()
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