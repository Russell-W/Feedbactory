
package com.feedbactory.client.ui.browser.feedback.personal.js;


import com.feedbactory.client.ui.browser.feedback.js.SharedJavaScriptString;

abstract public class SmugMugJavaScriptString
{
   static final private String library;

   static
   {
      final StringBuilder scriptBuilder = new StringBuilder(SharedJavaScriptString.library.length() + 10000);

      scriptBuilder.append("return (function()");
      scriptBuilder.append("{");

      scriptBuilder.append(SharedJavaScriptString.library);

      /* Start paste area.
       */
      scriptBuilder.append("function getPhotoIDFromPhotoLink(photoLink)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoIDStartIndex = photoLink.lastIndexOf('/i-');");
      scriptBuilder.append("   if (photoIDStartIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoIDStartIndex += 3;");

      scriptBuilder.append("      var trailingSlashIndex = photoLink.indexOf('/', photoIDStartIndex);");
      scriptBuilder.append("      return (trailingSlashIndex === -1) ? photoLink.substring(photoIDStartIndex) : photoLink.substring(photoIDStartIndex, trailingSlashIndex);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoUserIDFromPhotoLink(photoLink)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var domainStartIndex = photoLink.indexOf('://');");
      scriptBuilder.append("   if (domainStartIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      domainStartIndex += 3;");
      scriptBuilder.append("      var smugMugSuffixIndex = photoLink.indexOf('.smugmug.com/', domainStartIndex);");
      scriptBuilder.append("      if (smugMugSuffixIndex !== -1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var firstSlashIndex = photoLink.indexOf('/', domainStartIndex);");
      scriptBuilder.append("         var firstDotIndex = photoLink.indexOf('.', domainStartIndex);");
      scriptBuilder.append("         if ((firstSlashIndex > smugMugSuffixIndex) && (firstDotIndex === smugMugSuffixIndex))");
      scriptBuilder.append("         {");
      scriptBuilder.append("            var userID = photoLink.substring(domainStartIndex, smugMugSuffixIndex);");
      scriptBuilder.append("            if (userID !== 'photos')");
      scriptBuilder.append("               return userID;");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoUserIDFromMetaTag(photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var metaElements = document.head.getElementsByTagName('meta');");
      scriptBuilder.append("   for (var elementIndex = 0; elementIndex < metaElements.length; elementIndex ++)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var element = metaElements[elementIndex];");
      scriptBuilder.append("      if (element.getAttribute('property') === 'og:url')");
      scriptBuilder.append("      {");
      scriptBuilder.append("         if (element.content.endsWith('/i-' + photoID))");
      scriptBuilder.append("            return getPhotoUserIDFromPhotoLink(element.content);");
      scriptBuilder.append("         else");
      scriptBuilder.append("            break;");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoTitle(galleryRootElement, infoClassNames)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTitle = null;");

      scriptBuilder.append("   var infoRootElement = getFirstElementByClassNames(infoClassNames, galleryRootElement);");
      scriptBuilder.append("   if ((infoRootElement !== null) && (infoRootElement.tagName === 'DIV'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoTitle = '';");
      scriptBuilder.append("      var photoName = null;");
      scriptBuilder.append("      var photoDescription = null;");

      scriptBuilder.append("      var childNodes = infoRootElement.childNodes;");
      scriptBuilder.append("      var childNode;");

      scriptBuilder.append("      for (var childIndex = 0; childIndex < childNodes.length; childIndex ++)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         childNode = childNodes[childIndex];");

      scriptBuilder.append("         if (childNode.nodeType === 1)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            if (childNode.getAttribute('data-name') === 'Title')");
      scriptBuilder.append("               photoName = textContent(childNode).trim();");
      scriptBuilder.append("            else if (childNode.getAttribute('data-name') === 'CaptionRaw')");
      scriptBuilder.append("               photoDescription = textContent(childNode).trim();");
      scriptBuilder.append("         }");

      scriptBuilder.append("         if ((photoName !== null) && (photoName !== ''))");
      scriptBuilder.append("            photoTitle = photoName;");
      scriptBuilder.append("         else if (photoDescription !== null)");
      scriptBuilder.append("            photoTitle = photoDescription;");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return photoTitle;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoThumbnailElementsFromMetaTag(photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var metaElements = document.head.getElementsByTagName('meta');");
      scriptBuilder.append("   for (var elementIndex = 0; elementIndex < metaElements.length; elementIndex ++)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var element = metaElements[elementIndex];");
      scriptBuilder.append("      if (element.getAttribute('property') === 'og:image')");
      scriptBuilder.append("         return getPhotoThumbnailElementsFromImageSource(element.content, photoID);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoThumbnailElementsFromImageSource(imageSource, photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   if (imageSource.indexOf('?') !== -1)");
      scriptBuilder.append("      return null;");

      scriptBuilder.append("   var albumFolderStartIndex = imageSource.indexOf('.smugmug.com/');");
      scriptBuilder.append("   if (albumFolderStartIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      albumFolderStartIndex += 13;");

      scriptBuilder.append("      var filenameStartIndex = imageSource.lastIndexOf('/');");
      scriptBuilder.append("      if (filenameStartIndex !== -1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         filenameStartIndex ++;");
      scriptBuilder.append("         var suffixStartIndex = imageSource.lastIndexOf('.');");

      scriptBuilder.append("         if (suffixStartIndex > filenameStartIndex)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            suffixStartIndex ++;");
      scriptBuilder.append("            var filenameSuffix = imageSource.substring(suffixStartIndex);");

      scriptBuilder.append("            suffixStartIndex = imageSource.lastIndexOf('-', suffixStartIndex - 3);");
      scriptBuilder.append("            if (suffixStartIndex > filenameStartIndex)");
      scriptBuilder.append("            {");
      scriptBuilder.append("               var filename = imageSource.substring(filenameStartIndex, suffixStartIndex);");
      scriptBuilder.append("               var albumFolderEndIndex = nthLastIndexOf(imageSource, '/', 3, filenameStartIndex - 2);");

      scriptBuilder.append("               if ((albumFolderEndIndex !== -1) && (imageSource.indexOf('/i-' + photoID, albumFolderEndIndex) === albumFolderEndIndex))");
      scriptBuilder.append("               {");
      scriptBuilder.append("                  var albumFolder = imageSource.substring(albumFolderStartIndex, albumFolderEndIndex);");
      scriptBuilder.append("                  return [albumFolder, filename, filenameSuffix];");
      scriptBuilder.append("               }");
      scriptBuilder.append("            }");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function nthLastIndexOf(sourceString, targetCharacter, n, startIndex)");
      scriptBuilder.append("{");
      scriptBuilder.append("   startIndex = startIndex || (sourceString.length - 1);");

      scriptBuilder.append("   var matchNumber = 0;");

      scriptBuilder.append("   for (var characterIndex = startIndex; characterIndex >= 0; characterIndex --)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (sourceString.charAt(characterIndex) === targetCharacter)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         matchNumber ++;");

      scriptBuilder.append("         if (matchNumber === n)");
      scriptBuilder.append("            return characterIndex;");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return -1;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoTags(rootElement, photoTagClassIdentifiers)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTags = [];");
      scriptBuilder.append("   var parentTagElement = getFirstElementByClassNames(photoTagClassIdentifiers, rootElement);");

      scriptBuilder.append("   if ((parentTagElement !== null) && (parentTagElement.tagName === 'P'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var childElement;");

      scriptBuilder.append("      for (var childIndex = 0; childIndex < parentTagElement.childNodes.length; childIndex ++)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         childElement = parentTagElement.childNodes[childIndex];");
      scriptBuilder.append("         if ((childElement !== null) && (childElement.nodeType === 1) && (childElement.tagName === 'A') && hasClassName(childElement, 'sm-muted'))");
      scriptBuilder.append("         {");
      scriptBuilder.append("            photoTags[photoTags.length] = textContent(childElement);");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return photoTags;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxElement()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var lightboxElement = getFirstElementByClassNames(['sm-lightbox-focused']);");
      scriptBuilder.append("   if ((lightboxElement !== null) && (lightboxElement.tagName === 'DIV'))");
      scriptBuilder.append("      return lightboxElement;");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoDataFromLightboxPage(lightboxRootElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoData = [null, null, null, null, null];");

      scriptBuilder.append("   var lightboxImageElement = getFirstElementByClassNames(['sm-lightbox-image'], lightboxRootElement);");
      scriptBuilder.append("   if ((lightboxImageElement !== null) && (lightboxImageElement.tagName === 'IMG'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var encodedLightboxImageLink;");

      scriptBuilder.append("      if (lightboxImageElement.style.backgroundImage.trim() !== '')");
      scriptBuilder.append("         encodedLightboxImageLink = lightboxImageElement.style.backgroundImage;");
      scriptBuilder.append("      else");
      scriptBuilder.append("         encodedLightboxImageLink = lightboxImageElement.src;");

      scriptBuilder.append("      photoData[0] = getLightboxPhotoID(encodedLightboxImageLink);");
      scriptBuilder.append("      if (photoData[0] !== null)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         photoData[1] = getPhotoUserIDFromPhotoLink(encodedLightboxImageLink);");

      scriptBuilder.append("         if (photoData[1] === null)");
      scriptBuilder.append("            photoData[1] = getPhotoUserIDFromMetaTag(photoData[0]);");

      scriptBuilder.append("         if (photoData[1] !== null)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            photoData[2] = getPhotoTitle(lightboxRootElement, ['sm-lightbox-info']);");
      scriptBuilder.append("            if (photoData[2] !== null)");
      scriptBuilder.append("            {");
      scriptBuilder.append("               photoData[3] = getPhotoThumbnailElementsFromMetaTag(photoData[0]);");
      scriptBuilder.append("               if (photoData[3] !== null)");
      scriptBuilder.append("                  photoData[4] = getPhotoTags(lightboxRootElement, ['sm-lightbox-info', 'sm-lightbox-keywords']);");
      scriptBuilder.append("            }");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return photoData;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxPhotoID(encodedLightboxImageLink)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoIDStartIndex = nthLastIndexOf(encodedLightboxImageLink, '/', 4);");
      scriptBuilder.append("   if ((photoIDStartIndex !== -1) && (encodedLightboxImageLink.indexOf('/i-', photoIDStartIndex) === photoIDStartIndex))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoIDStartIndex += 3;");
      scriptBuilder.append("      var photoIDEndIndex = encodedLightboxImageLink.indexOf('/', photoIDStartIndex);");

      scriptBuilder.append("      if (photoIDEndIndex !== -1)");
      scriptBuilder.append("         return encodedLightboxImageLink.substring(photoIDStartIndex, photoIDEndIndex);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getGalleryElement()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var galleryRootElement = document.getElementById('sm-gallery');");
      scriptBuilder.append("   if ((galleryRootElement !== null) && (galleryRootElement.tagName === 'DIV'))");
      scriptBuilder.append("      return galleryRootElement;");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function isSmugMugStyleGallery(galleryRootElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   return hasClassName(galleryRootElement, 'sm-gallery-smugmug');");
      scriptBuilder.append("}");


      scriptBuilder.append("function getActiveSmugMugPhotoData(galleryRootElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoData = [null, null, null, null, null];");

      scriptBuilder.append("   var smugMugGalleryElement = getFirstElementByClassNames(['sm-gallery-image-container'], galleryRootElement);");
      scriptBuilder.append("   if ((smugMugGalleryElement !== null) && (smugMugGalleryElement.tagName === 'DIV'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var activeGalleryPhotoLinkElement = getFirstElementByClassNames(['sm-tile-content'], smugMugGalleryElement);");
      scriptBuilder.append("      if ((activeGalleryPhotoLinkElement !== null) && (activeGalleryPhotoLinkElement.tagName === 'A'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         photoData[0] = getPhotoIDFromPhotoLink(activeGalleryPhotoLinkElement.href);");

      scriptBuilder.append("         if (photoData[0] !== null)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            photoData[1] = getPhotoUserIDFromPhotoLink(activeGalleryPhotoLinkElement.href);");

      scriptBuilder.append("            if (photoData[1] === null)");
      scriptBuilder.append("               photoData[1] = getPhotoUserIDFromMetaTag(photoData[0]);");

      scriptBuilder.append("            if (photoData[1] !== null)");
      scriptBuilder.append("            {");
      scriptBuilder.append("               photoData[2] = getPhotoTitle(smugMugGalleryElement, ['sm-tile-info']);");
      scriptBuilder.append("               if (photoData[2] !== null)");
      scriptBuilder.append("               {");
      scriptBuilder.append("                  photoData[3] = getPhotoThumbnailElementsFromMetaTag(photoData[0]);");
      scriptBuilder.append("                  if (photoData[3] !== null)");
      scriptBuilder.append("                     photoData[4] = getPhotoTags(smugMugGalleryElement, ['sm-tile-info', 'sm-tile-keywords']);");
      scriptBuilder.append("               }");
      scriptBuilder.append("            }");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return photoData;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getActivePhotoData()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoData = [null, null, null, null, null];");

      scriptBuilder.append("   var lightboxRootElement = getLightboxElement();");
      scriptBuilder.append("   var galleryRootElement = getGalleryElement();");
      scriptBuilder.append("   var isSmugMugStyleGalleryElement = (galleryRootElement !== null) && isSmugMugStyleGallery(galleryRootElement);");

      scriptBuilder.append("   if (lightboxRootElement !== null)");
      scriptBuilder.append("      photoData = getPhotoDataFromLightboxPage(lightboxRootElement);");
      scriptBuilder.append("   else if ((galleryRootElement !== null) && isSmugMugStyleGalleryElement)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoData = getActiveSmugMugPhotoData(galleryRootElement);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   if ((photoData[0] !== null) && (photoData[1] !== null) && (photoData[2] !== null) && (photoData[3] !== null) && (photoData[4] !== null))");
      scriptBuilder.append("      return photoData;");
      scriptBuilder.append("   else");
      scriptBuilder.append("      return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function main()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var result = [true, null];");

      scriptBuilder.append("   try");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (hostCheck('smugmug.com'))");
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


   private SmugMugJavaScriptString()
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