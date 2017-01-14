
package com.feedbactory.client.ui.browser.feedback.personal.js;


import com.feedbactory.client.ui.browser.feedback.js.SharedJavaScriptString;

abstract public class YouPicJavaScriptString
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

      scriptBuilder.append("   var lightboxPhotoDivElement = getLightboxPhotoDivElement();");
      scriptBuilder.append("   if (lightboxPhotoDivElement !== null)");
      scriptBuilder.append("      activePhotoData = getLightboxPhotoData(lightboxPhotoDivElement);");
      scriptBuilder.append("   else");
      scriptBuilder.append("      activePhotoData = getRegularPhotoData();");

      scriptBuilder.append("   if ((activePhotoData[0] !== null) && (activePhotoData[1] !== null) && (activePhotoData[2] !== null) && (activePhotoData[3] !== null) && (activePhotoData[4] !== null))");
      scriptBuilder.append("      return activePhotoData;");
      scriptBuilder.append("   else");
      scriptBuilder.append("      return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxPhotoData(lightboxPhotoDivElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var activePhotoData = [null, null, null, null, null];");

      scriptBuilder.append("   activePhotoData[0] = getLightboxPhotoID(lightboxPhotoDivElement);");
      scriptBuilder.append("   if (activePhotoData[0] !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photoURL = getLightboxPhotoURL(lightboxPhotoDivElement);");
      scriptBuilder.append("      if (photoURL !== null)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         activePhotoData[1] = getPhotographerID(photoURL);");
      scriptBuilder.append("         if (activePhotoData[1] !== null)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            var lightboxAsideElement = getLightboxAsideElement();");
      scriptBuilder.append("            if (lightboxAsideElement !== null)");
      scriptBuilder.append("            {");
      scriptBuilder.append("               activePhotoData[2] = getLightboxPhotoDisplayName(lightboxAsideElement);");
      scriptBuilder.append("               if (activePhotoData[2] !== null)");
      scriptBuilder.append("               {");
      scriptBuilder.append("                  activePhotoData[3] = getThumbnailID(photoURL, activePhotoData[1]);");
      scriptBuilder.append("                  if (activePhotoData[3] !== null)");
      scriptBuilder.append("                     activePhotoData[4] = getPhotoTags(lightboxAsideElement, ['imdl-aside-info', 'imdl-aside-story']);");
      scriptBuilder.append("               }");
      scriptBuilder.append("            }");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return activePhotoData;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxPhotoDivElement()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoDivElement = getFirstElementByClassNames(['imdl', 'imdl-image-box', 'imdl-img', 'imdl-box', 'imdl-fullscreen']);");
      scriptBuilder.append("   if ((photoDivElement !== null) && (photoDivElement.tagName === 'DIV'))");
      scriptBuilder.append("      return photoDivElement;");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxPhotoID(photoDivElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoLinkElements = photoDivElement.getElementsByTagName('a');");
      scriptBuilder.append("   if (photoLinkElements.length === 1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photoLink = trimURLArgument(photoLinkElements[0].href.trim());");

      scriptBuilder.append("      var photoIDStartIndex = photoLink.indexOf('/image/');");
      scriptBuilder.append("      if (photoIDStartIndex !== -1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         photoIDStartIndex += 7;");

      scriptBuilder.append("         var lastSlashIndex = photoLink.indexOf('/', photoIDStartIndex);");
      scriptBuilder.append("         if (lastSlashIndex === -1)");
      scriptBuilder.append("            return photoLink.substring(photoIDStartIndex);");
      scriptBuilder.append("         else if (lastSlashIndex === (photoLink.length - 1))");
      scriptBuilder.append("            return photoLink.substring(photoIDStartIndex, lastSlashIndex);");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxPhotoURL(photoDivElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoThumbnailElements = photoDivElement.getElementsByTagName('img');");
      scriptBuilder.append("   if (photoThumbnailElements.length === 1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      return trimURLArgument(photoThumbnailElements[0].src.trim());");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotographerID(photoThumbnailURL)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photographerIDStartIndex = photoThumbnailURL.lastIndexOf('/');");
      scriptBuilder.append("   if (photographerIDStartIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photographerIDStartIndex ++;");
      scriptBuilder.append("      var photographerIDEndIndex = photoThumbnailURL.indexOf('_', photographerIDStartIndex);");
      scriptBuilder.append("      if (photographerIDEndIndex !== -1)");
      scriptBuilder.append("         return photoThumbnailURL.substring(photographerIDStartIndex, photographerIDEndIndex);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxAsideElement()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoAsideElement = getFirstElementByClassNames(['imdl', 'imdl-aside']);");
      scriptBuilder.append("   if ((photoAsideElement !== null) && (photoAsideElement.tagName === 'ASIDE'))");
      scriptBuilder.append("      return photoAsideElement;");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxPhotoDisplayName(photoAsideElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTitleElement = getFirstElementByClassNames(['imdl-aside-info', 'imdl-aside-ttl'], photoAsideElement);");
      scriptBuilder.append("   if ((photoTitleElement !== null) && (photoTitleElement.tagName === 'H4'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photoTitle = textContent(photoTitleElement).trim();");
      scriptBuilder.append("      if (photoTitle === 'Untitled')");
      scriptBuilder.append("         photoTitle = '';");

      scriptBuilder.append("      var photographerNameDivElement = getFirstElementByClassNames(['imdl-aside-box', 'imdl-aside-name'], photoAsideElement);");
      scriptBuilder.append("      if ((photographerNameDivElement !== null) && (photographerNameDivElement.tagName === 'DIV'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var photographerNameLinkElements = photographerNameDivElement.getElementsByTagName('a');");
      scriptBuilder.append("         if (photographerNameLinkElements.length === 1)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            var photographerName = textContent(photographerNameLinkElements[0]).trim();");
      scriptBuilder.append("            return [photoTitle, photographerName];");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getThumbnailID(photoThumbnailURL, photographerID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var thumbnailIDStartIndex = photoThumbnailURL.lastIndexOf('/' + photographerID + '_');");
      scriptBuilder.append("   if (thumbnailIDStartIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      thumbnailIDStartIndex += photographerID.length + 2;");
      scriptBuilder.append("      return photoThumbnailURL.substring(thumbnailIDStartIndex);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoTags(startElement, targetClassNames)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTags = null;");

      scriptBuilder.append("   var photoTagsParentElement = getFirstElementByClassNames(targetClassNames, startElement);");
      scriptBuilder.append("   if ((photoTagsParentElement !== null) && (photoTagsParentElement.tagName === 'P'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoTags = [];");

      scriptBuilder.append("      var photoTagElements = photoTagsParentElement.getElementsByTagName('a');");
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


      scriptBuilder.append("function getRegularPhotoData()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var activePhotoData = [null, null, null, null, null];");

      scriptBuilder.append("   activePhotoData[0] = getRegularPhotoID();");

      scriptBuilder.append("   if ((activePhotoData[0] !== null) && (activePhotoData[0] === getStateObjectPhotoID()))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photoURL = getRegularPhotoURL();");
      scriptBuilder.append("      if (photoURL !== null)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         activePhotoData[1] = getPhotographerID(photoURL);");
      scriptBuilder.append("         if (activePhotoData[1] !== null)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            activePhotoData[2] = getRegularPhotoDisplayName();");
      scriptBuilder.append("            if (activePhotoData[2] !== null)");
      scriptBuilder.append("            {");
      scriptBuilder.append("               activePhotoData[3] = getThumbnailID(photoURL, activePhotoData[1]);");
      scriptBuilder.append("               if (activePhotoData[3] !== null)");
      scriptBuilder.append("                  activePhotoData[4] = getPhotoTags(document, ['imgp-info', 'imgp-info-aside', 'imgp-aside-box', 'imgp-desc']);");
      scriptBuilder.append("            }");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return activePhotoData;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getRegularPhotoID()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var pathname = window.location.pathname;");
      scriptBuilder.append("   if (pathname.indexOf('/image/') === 0)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var slashIndex = pathname.indexOf('/', 7);");
      scriptBuilder.append("      if (slashIndex === -1)");
      scriptBuilder.append("         return pathname.substring(7);");
      scriptBuilder.append("      else");
      scriptBuilder.append("         return pathname.substring(7, slashIndex);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getStateObjectPhotoID()");
      scriptBuilder.append("{");
      scriptBuilder.append("   if (objectExists(window, 'State', 'image', 'image_id'))");
      scriptBuilder.append("      return stringify(window.State.image.image_id);");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getRegularPhotoURL()");
      scriptBuilder.append("{");
      scriptBuilder.append("   if (objectExists(window.State.image, 'image_urls', 'small'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      return trimURLArgument(window.State.image.image_urls.small.trim());");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getRegularPhotoDisplayName()");
      scriptBuilder.append("{");
      scriptBuilder.append("   if (objectExists(window.State.image, 'image_name'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photoTitle = window.State.image.image_name.trim();");

      scriptBuilder.append("      if (photoTitle === 'Untitled')");
      scriptBuilder.append("         photoTitle = '';");

      scriptBuilder.append("      if (objectExists(window.State.image, 'user', 'display_name'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var photographerName = window.State.image.user.display_name.trim();");
      scriptBuilder.append("         return [photoTitle, photographerName];");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function main()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var result = [true, null];");

      scriptBuilder.append("   try");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (hostCheck('youpic.com'))");
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


   private YouPicJavaScriptString()
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