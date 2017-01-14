
package com.feedbactory.client.ui.browser.feedback.personal.js;


import com.feedbactory.client.ui.browser.feedback.js.SharedJavaScriptString;

abstract public class PhotoShelterJavaScriptString
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
      scriptBuilder.append("function isPhotoFilename(photoTitle)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var lowercasedPhotoTitle = photoTitle.toLowerCase();");

      scriptBuilder.append("   return (lowercasedPhotoTitle.endsWith('.jpg') || lowercasedPhotoTitle.endsWith('.tif') || lowercasedPhotoTitle.endsWith('.tiff') ||");
      scriptBuilder.append("           lowercasedPhotoTitle.endsWith('.raw') || lowercasedPhotoTitle.endsWith('.psd') || lowercasedPhotoTitle.endsWith('.dng') ||");
      scriptBuilder.append("           lowercasedPhotoTitle.endsWith('.pdf'));");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoIDFromImageLink(imageLink, photoIDPrefix)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoIDStartIndex = imageLink.indexOf(photoIDPrefix);");
      scriptBuilder.append("   if (photoIDStartIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoIDStartIndex += photoIDPrefix.length;");
      scriptBuilder.append("      var photoIDEndIndex = imageLink.indexOf('/', photoIDStartIndex);");
      scriptBuilder.append("      if (photoIDEndIndex !== -1)");
      scriptBuilder.append("         return imageLink.substring(photoIDStartIndex, photoIDEndIndex);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getNonFilenamePhotoTitle(parentElement, headingTagName, descriptionTagName)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoName = null;");
      scriptBuilder.append("   var photoDescription = null;");

      scriptBuilder.append("   var childNodes = parentElement.childNodes;");
      scriptBuilder.append("   var childNode;");

      scriptBuilder.append("   for (var childIndex = 0; childIndex < childNodes.length; childIndex ++)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      childNode = childNodes[childIndex];");
      scriptBuilder.append("      if (childNode.nodeType === 1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         if (childNode.tagName === headingTagName)");
      scriptBuilder.append("            photoName = textContent(childNode).trim();");
      scriptBuilder.append("         else if (childNode.tagName === descriptionTagName)");
      scriptBuilder.append("            photoDescription = textContent(childNode).trim();");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   if ((photoName !== null) && (! isPhotoFilename(photoName)))");
      scriptBuilder.append("      return photoName;");
      scriptBuilder.append("   else if ((photoDescription !== null) && (! isPhotoFilename(photoDescription)))");
      scriptBuilder.append("      return photoDescription;");
      scriptBuilder.append("   else");
      scriptBuilder.append("   {");
      scriptBuilder.append("      return '';");
      scriptBuilder.append("   }");
      scriptBuilder.append("}");


      scriptBuilder.append("function isCustomEnvVariableAvailable()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return objectExists(window, 'C2_CFG', 'customEnv');");
      scriptBuilder.append("}");


      scriptBuilder.append("function getThemedGalleryPhotoData()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var galleryParentElement = null;");
      scriptBuilder.append("   var isPhotoVisibleFunction = null;");
      scriptBuilder.append("   var getPhotoTitleFunction = getThemedGalleryPhotoTitle;");

      scriptBuilder.append("   if (isMarqueeTheme() || isPromenadeTheme() || isShuffleTheme() || isElementTheme() || isEastTheme() || isDowntownTheme() || isPivotTheme())");
      scriptBuilder.append("   {");
      scriptBuilder.append("      galleryParentElement = getFirstElementByClassNames(['gallerySingleImage']);");
      scriptBuilder.append("      if ((galleryParentElement !== null) && (galleryParentElement.tagName === 'SECTION'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         if (isMarqueeTheme())");
      scriptBuilder.append("            isPhotoVisibleFunction = isMarqueePhotoVisible;");
      scriptBuilder.append("         else if (isPromenadeTheme())");
      scriptBuilder.append("            isPhotoVisibleFunction = isPromenadePhotoVisible;");
      scriptBuilder.append("         else if (isShuffleTheme())");
      scriptBuilder.append("            isPhotoVisibleFunction = isShufflePhotoVisible;");
      scriptBuilder.append("         else if (isElementTheme())");
      scriptBuilder.append("            isPhotoVisibleFunction = isElementPhotoVisible;");
      scriptBuilder.append("         else if (isEastTheme())");
      scriptBuilder.append("            isPhotoVisibleFunction = isEastPhotoVisible;");
      scriptBuilder.append("         else if (isDowntownTheme())");
      scriptBuilder.append("            isPhotoVisibleFunction = isDowntownPhotoVisible;");
      scriptBuilder.append("         else if (isPivotTheme())");
      scriptBuilder.append("         {");
      scriptBuilder.append("            isPhotoVisibleFunction = isPivotPhotoVisible;");
      scriptBuilder.append("            getPhotoTitleFunction = getPivotPhotoTitle;");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");
      scriptBuilder.append("   else if (isSonnetTheme())");
      scriptBuilder.append("   {");
      scriptBuilder.append("      galleryParentElement = getFirstElementByClassNames(['slideShow']);");
      scriptBuilder.append("      if ((galleryParentElement !== null) && (galleryParentElement.tagName === 'DIV'))");
      scriptBuilder.append("         isPhotoVisibleFunction = isSonnetPhotoVisible;");
      scriptBuilder.append("   }");

      scriptBuilder.append("   if ((galleryParentElement !== null) && (isPhotoVisibleFunction !== null))");
      scriptBuilder.append("      return getThemedGalleryPhotoDataDelegate(galleryParentElement, isPhotoVisibleFunction, getPhotoTitleFunction);");
      scriptBuilder.append("   else");
      scriptBuilder.append("      return [null, null, null];");
      scriptBuilder.append("}");


      scriptBuilder.append("function isMarqueeTheme()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return (window.C2_CFG.customEnv.theme_name === 'Marquee');");
      scriptBuilder.append("}");


      scriptBuilder.append("function isMarqueePhotoVisible(gallerySingleImageElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var stackTopElement = document.getElementById('mode-index');");
      scriptBuilder.append("   if ((stackTopElement !== null) && (stackTopElement.tagName === 'DIV') && hasClassName(stackTopElement, 'collapse'))");
      scriptBuilder.append("      return isElementPhotoVisible(gallerySingleImageElement);");

      scriptBuilder.append("   return false;");
      scriptBuilder.append("}");


      scriptBuilder.append("function isPromenadeTheme()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return (window.C2_CFG.customEnv.theme_name === 'Promenade');");
      scriptBuilder.append("}");


      scriptBuilder.append("function isPromenadePhotoVisible()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var imageStageElement = getFirstElementByClassNames(['stack-top', 'gallerySingleImage', 'ImageStage']);");
      scriptBuilder.append("   return ((imageStageElement !== null) && (imageStageElement.tagName === 'DIV') && (! hasClassName(imageStageElement, 'hidden')));");
      scriptBuilder.append("}");


      scriptBuilder.append("function isShuffleTheme()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return (window.C2_CFG.customEnv.theme_name === 'Shuffle');");
      scriptBuilder.append("}");


      scriptBuilder.append("function isShufflePhotoVisible()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var stackTopElement = document.getElementById('mode-index');");
      scriptBuilder.append("   return ((stackTopElement !== null) && (stackTopElement.tagName === 'DIV') && hasClassName(stackTopElement, 'stack'));");
      scriptBuilder.append("}");


      scriptBuilder.append("function isElementTheme()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return (window.C2_CFG.customEnv.theme_name === 'Element');");
      scriptBuilder.append("}");


      scriptBuilder.append("function isElementPhotoVisible(gallerySingleImageElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var galleryViewerElement = getFirstElementByClassNames(['ThumbViewer'], gallerySingleImageElement);");
      scriptBuilder.append("   return ((galleryViewerElement !== null) && (! hasClassName(galleryViewerElement, 'on')));");
      scriptBuilder.append("}");


      scriptBuilder.append("function isEastTheme()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return (window.C2_CFG.customEnv.theme_name === 'East');");
      scriptBuilder.append("}");


      scriptBuilder.append("function isEastPhotoVisible()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return isShufflePhotoVisible();");
      scriptBuilder.append("}");


      scriptBuilder.append("function isDowntownTheme()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return (window.C2_CFG.customEnv.theme_name === 'Downtown');");
      scriptBuilder.append("}");


      scriptBuilder.append("function isDowntownPhotoVisible(gallerySingleImageElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var galleryViewerElement = getFirstElementByClassNames(['ThumbFocus'], gallerySingleImageElement);");
      scriptBuilder.append("   return ((galleryViewerElement !== null) && (! hasClassName(galleryViewerElement, 'on')));");
      scriptBuilder.append("}");


      scriptBuilder.append("function isPivotTheme()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return (window.C2_CFG.customEnv.theme_name === 'Pivot');");
      scriptBuilder.append("}");


      scriptBuilder.append("function isPivotPhotoVisible()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return isShufflePhotoVisible();");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPivotPhotoTitle(photoID, galleryParentElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var imageElements = getElementsByClassNames(['stack-top', 'GalleryViewer', 'img']);");
      scriptBuilder.append("   var imageElement;");

      scriptBuilder.append("   for (var elementIndex = 0; elementIndex < imageElements.length; elementIndex ++)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      imageElement = imageElements[elementIndex];");
      scriptBuilder.append("      if (imageElement.getAttribute('data-index') === photoID)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var metaElement = getFirstElementByClassNames(['meta'], imageElement);");
      scriptBuilder.append("         if ((metaElement !== null) && (metaElement.tagName === 'DIV'))");
      scriptBuilder.append("         {");
      scriptBuilder.append("            return getNonFilenamePhotoTitle(metaElement, 'H2', 'P');");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function isSonnetTheme()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return (window.C2_CFG.customEnv.theme_name === 'Sonnet');");
      scriptBuilder.append("}");


      scriptBuilder.append("function isSonnetPhotoVisible(slideShowElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   return hasClassName(slideShowElement, 'on');");
      scriptBuilder.append("}");


      scriptBuilder.append("function getThemedGalleryPhotoDataDelegate(galleryParentElement, isPhotoVisibleFunction, getPhotoTitleFunction)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoData = [null, null, null];");

      scriptBuilder.append("   if (isPhotoVisibleFunction(galleryParentElement))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoData[0] = getThemedGalleryPhotoID(galleryParentElement);");
      scriptBuilder.append("      if (photoData[0] !== null)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         photoData[1] = getThemedGalleryPhotographerID();");
      scriptBuilder.append("         photoData[2] = getPhotoTitleFunction(photoData[0], galleryParentElement);");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return photoData;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getThemedGalleryPhotoID(galleryParentElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var imageElement = getFirstElementByClassNames(['ImageStage', 'current'], galleryParentElement);");
      scriptBuilder.append("   if ((imageElement !== null) && (imageElement.tagName === 'DIV'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var backgroundPhotoLink = imageElement.style.backgroundImage;");
      scriptBuilder.append("      return getPhotoIDFromImageLink(backgroundPhotoLink, '/img-get2/');");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getThemedGalleryPhotographerID()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return window.C2_CFG.customEnv.label.trim();");
      scriptBuilder.append("}");


      scriptBuilder.append("function getThemedGalleryPhotoTitle(photoID, galleryParentElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTitleDivElement = getFirstElementByClassNames(['MetaViewer', 'content'], galleryParentElement);");
      scriptBuilder.append("   if ((photoTitleDivElement !== null) && (photoTitleDivElement.tagName === 'DIV'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      return getNonFilenamePhotoTitle(photoTitleDivElement, 'H1', 'DIV');");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getDefaultGalleryPhotoData(imageElement, photoIDPrefix)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoData = [null, null, null];");

      scriptBuilder.append("   photoData[0] = getPhotoIDFromImageLink(imageElement.src, photoIDPrefix);");
      scriptBuilder.append("   if (photoData[0] !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoData[1] = getLegacyThemePhotographerID();");
      scriptBuilder.append("      if (photoData[1] !== null)");
      scriptBuilder.append("         photoData[2] = getLegacyThemePhotoTitle(imageElement);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return photoData;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLegacyThemeImageElement(targetClassNames)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var imageWidgetElement = getFirstElementByClassNames(targetClassNames);");
      scriptBuilder.append("   if ((imageWidgetElement !== null) && (imageWidgetElement.tagName === 'DIV'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var imageChildElements = imageWidgetElement.getElementsByTagName('img');");

      scriptBuilder.append("      for (var childIndex = 0; childIndex < imageChildElements.length; childIndex ++)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         if (imageChildElements[childIndex].getAttribute('itemprop') === 'contentURL')");
      scriptBuilder.append("            return imageChildElements[childIndex];");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLegacyThemePhotographerID()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var url = window.location.hostname;");

      scriptBuilder.append("   var userIDEndIndex = url.indexOf('.');");
      scriptBuilder.append("   if ((userIDEndIndex !== -1) && (userIDEndIndex === url.indexOf('.photoshelter.com')) && ((userIDEndIndex + 17) === url.length))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var userID = url.substring(0, userIDEndIndex);");
      scriptBuilder.append("      if (userID !== 'www')");
      scriptBuilder.append("         return userID;");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLegacyThemePhotoTitle(imageElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTitle = null;");

      scriptBuilder.append("   var titleSeparatorIndex = document.title.indexOf(' | ');");
      scriptBuilder.append("   if (titleSeparatorIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var rawPhotoTitle = document.title.substring(0, titleSeparatorIndex).trim();");
      scriptBuilder.append("      if ((rawPhotoTitle.length > 0) && (! isPhotoFilename(rawPhotoTitle)))");
      scriptBuilder.append("         photoTitle = rawPhotoTitle;");
      scriptBuilder.append("      else");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var rawPhotoDescription = imageElement.alt.trim();");
      scriptBuilder.append("         if ((rawPhotoDescription.length > 0) && (! isPhotoFilename(rawPhotoDescription)))");
      scriptBuilder.append("            photoTitle = rawPhotoDescription;");
      scriptBuilder.append("         else");
      scriptBuilder.append("            photoTitle = '';");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return photoTitle;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getClassicPortfolioSlideShowElement()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var slideShowElement = getFirstElementByClassNames(['PSPortfolio', 'psport_slider']);");
      scriptBuilder.append("   if ((slideShowElement !== null) && (slideShowElement.tagName === 'DIV'))");
      scriptBuilder.append("      return slideShowElement;");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getClassicPortfolioPhotoData(slideShowElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoData = [null, null, null];");

      scriptBuilder.append("   var activeSlideShowElement = getClassicPortfolioActiveElement(slideShowElement);");
      scriptBuilder.append("   if (activeSlideShowElement !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoData[0] = getClassicPortfolioPhotoID(activeSlideShowElement);");
      scriptBuilder.append("      if (photoData[0] !== null)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         photoData[1] = getLegacyThemePhotographerID();");
      scriptBuilder.append("         if (photoData[1] !== null)");
      scriptBuilder.append("            photoData[2] = getClassicPortfolioPhotoTitle(activeSlideShowElement);");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return photoData;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getClassicPortfolioActiveElement(slideShowElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var targetElementOffset = -slideShowElement.style.pixelLeft;");
      scriptBuilder.append("   var childNodes = slideShowElement.childNodes;");
      scriptBuilder.append("   var childNode;");

      scriptBuilder.append("   for (var childIndex = 0; childIndex < childNodes.length; childIndex ++)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      childNode = childNodes[childIndex];");
      scriptBuilder.append("      if ((childNode.nodeType === 1) && (childNode.tagName === 'DIV') && (childNode.style.pixelLeft === targetElementOffset))");
      scriptBuilder.append("         return childNode;");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getClassicPortfolioPhotoID(activeElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var childNodes = activeElement.childNodes;");
      scriptBuilder.append("   var childNode;");
      scriptBuilder.append("   var photoID;");

      scriptBuilder.append("   for (var childIndex = 0; childIndex < childNodes.length; childIndex ++)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      childNode = childNodes[childIndex];");
      scriptBuilder.append("      if ((childNode.nodeType === 1) && (childNode.tagName === 'IMG'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         photoID = getPhotoIDFromImageLink(childNode.src, '/img-get/');");
      scriptBuilder.append("         if (photoID !== null)");
      scriptBuilder.append("            return photoID;");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getClassicPortfolioPhotoTitle(activeElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTitle = null;");

      scriptBuilder.append("   var photoNameElement = getFirstElementByClassNames(['psport_headline'], activeElement);");
      scriptBuilder.append("   if ((photoNameElement !== null) && (photoNameElement.tagName === 'SPAN'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photoName = textContent(photoNameElement).trim();");
      scriptBuilder.append("      if ((photoName.length > 0) && (! isPhotoFilename(photoName)))");
      scriptBuilder.append("         return photoName;");
      scriptBuilder.append("      else");
      scriptBuilder.append("         photoTitle = '';");
      scriptBuilder.append("   }");

      scriptBuilder.append("   var photoDescriptionElement = getFirstElementByClassNames(['psport_cap'], activeElement);");
      scriptBuilder.append("   if ((photoDescriptionElement !== null) && (photoDescriptionElement.tagName === 'DIV'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photoDescription = textContent(photoDescriptionElement).trim();");
      scriptBuilder.append("      if ((photoDescription.length > 0) && (! isPhotoFilename(photoDescription)))");
      scriptBuilder.append("         photoTitle = photoDescription;");
      scriptBuilder.append("      else");
      scriptBuilder.append("         photoTitle = '';");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return photoTitle;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getEmbeddedSlideShowBaseElement()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var embeddedDivElement = getFirstElementByClassNames(['psEmbed']);");
      scriptBuilder.append("   if ((embeddedDivElement !== null) && (embeddedDivElement.tagName === 'DIV') && (embeddedDivElement.getAttribute('data-ps-embed-type') === 'slideshow'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var childNodes = embeddedDivElement.childNodes;");
      scriptBuilder.append("      var childNode;");

      scriptBuilder.append("      for (var childIndex = 0; childIndex < childNodes.length; childIndex ++)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         childNode = childNodes[childIndex];");
      scriptBuilder.append("         if ((childNode.nodeType === 1) && (childNode.tagName === 'IFRAME'))");
      scriptBuilder.append("         {");
      scriptBuilder.append("            var embeddedDocument = childNode.contentDocument;");
      scriptBuilder.append("            if (embeddedDocument !== null)");
      scriptBuilder.append("            {");
      scriptBuilder.append("               var baseElement = embeddedDocument.getElementById('mode-slideshow');");
      scriptBuilder.append("               if ((baseElement !== null) && (baseElement.tagName === 'DIV'))");
      scriptBuilder.append("                  return baseElement;");
      scriptBuilder.append("            }");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getEmbeddedSlideShowPhotoData(embeddedSlideShowBaseElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoData = [null, null, null];");

      scriptBuilder.append("   photoData[0] = getThemedGalleryPhotoID(embeddedSlideShowBaseElement);");
      scriptBuilder.append("   if (photoData[0] !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoData[1] = getLegacyThemePhotographerID();");

      scriptBuilder.append("      photoData[2] = getThemedGalleryPhotoTitle(null, embeddedSlideShowBaseElement);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return photoData;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getActivePhotoData()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoData = [null, null, null];");

      scriptBuilder.append("   if (isCustomEnvVariableAvailable())");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoData = getThemedGalleryPhotoData();");
      scriptBuilder.append("   }");
      scriptBuilder.append("   else");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var defaultGalleryImageElement = getLegacyThemeImageElement(['imageWrap', 'imageWidget']);");
      scriptBuilder.append("      if (defaultGalleryImageElement !== null)");
      scriptBuilder.append("         photoData = getDefaultGalleryPhotoData(defaultGalleryImageElement, '/img-get2/');");
      scriptBuilder.append("      else");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var classicThemeImageElement = getLegacyThemeImageElement(['imageWidget']);");
      scriptBuilder.append("         if (classicThemeImageElement !== null)");
      scriptBuilder.append("            photoData = getDefaultGalleryPhotoData(classicThemeImageElement, '/img-get/');");
      scriptBuilder.append("         else");
      scriptBuilder.append("         {");
      scriptBuilder.append("            var classicPortfolioSlideShowElement = getClassicPortfolioSlideShowElement();");
      scriptBuilder.append("            if (classicPortfolioSlideShowElement !== null)");
      scriptBuilder.append("               photoData = getClassicPortfolioPhotoData(classicPortfolioSlideShowElement);");
      scriptBuilder.append("            else");
      scriptBuilder.append("            {");
      scriptBuilder.append("               var embeddedSlideShowBaseElement = getEmbeddedSlideShowBaseElement();");
      scriptBuilder.append("               if (embeddedSlideShowBaseElement !== null)");
      scriptBuilder.append("                  photoData = getEmbeddedSlideShowPhotoData(embeddedSlideShowBaseElement);");
      scriptBuilder.append("            }");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   if ((photoData[0] !== null) && (photoData[1] !== null) && (photoData[2] !== null))");
      scriptBuilder.append("      return photoData;");
      scriptBuilder.append("   else");
      scriptBuilder.append("      return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function main()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var result = [true, null];");

      scriptBuilder.append("   try");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (hostCheck('photoshelter.com'))");
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


   private PhotoShelterJavaScriptString()
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