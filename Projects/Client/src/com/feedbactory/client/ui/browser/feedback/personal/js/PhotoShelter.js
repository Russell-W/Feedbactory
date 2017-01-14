/* Memos:
 * - Keywords are currently not doable with PhotoShelter, since they aren't available via all theme views. Instead, searches will have to rely upon
 *   the auto-keywords as generated by the title.
 *
 * - As with some other photography websites, PhotoShelter has gone through an underlying platform transformation. All of their web pages can be split into
 *   two main categories: those using the newer Beam framework/platform, and those using the legacy/'classic' themes. The Beam platform pages can be
 *   further split into the handful of themes for this that PhotoShelter currently provides: Marquee, Promenade, Shuffle, Element, Sonnet, East, Downtown,
 *   Pivot, and Horizon. All of these themes perform at least some internal page navigation without generating page load events within Mozilla (and sometimes IE),
 *   so the external click listener events installed from the Java end are especially necessary here.
 *
 * - The Horizon theme is not supported as it is basically a fluid horizontal carousel view without a definite 'active' photo. Kind of like SmugMug's Journal theme.
 *   
 * - A default photo gallery and individual photo view is provided by PhotoShelter both as a fallback option for older browsers (eg IE <= 8) as well
 *   as a standard view when the user selects 'More Info' on many of the Beam-themed pages. This view is fairly similar to the legacy/classic page format,
 *   so that some of the photo data ripping functions are shared. The fallback gallery & photo view is also often referred to within photographer's pages
 *   as their 'archive' of galleries.
 *
 * - The newer PhotoShelter themes are supported in IE9+; earlier versions of IE will be directed straight to the archive section of a photographer's
 *   website. This means that within the handling of the themes I can assume native browser support of most tags, eg. <nav>, <section>, <article>, etc.
 *
 * - For the sake of avoiding item fragmentation on the server, it's important that the 'default'/archive handler returns the same data, including
 *   photo title etc, as the data ripped from the themed pages. It's possible for the same photo to be browsed using either view, so the data has to match.
 *
 * - Element requires refresh in IE after first loading and also after clicking on a gallery name. I can't do anything about that.
 *   - Promenade appears to suffer from the same hitch on initial load, is fine after a refresh on the initial load.
 *
 * - Both Element and Sonnet seem to have an optional autoplay slideshow feature, not sure whether other themes have this or whether all examples that
 *   I could find have just disabled it.
 *
 * - Don't get the Element photo title/description from the meta tags. For this photo for eg, they are blank:
 *   http://peterhewitt.photoshelter.com/#!/portfolio/G0000GefHr5LMuDk/I0000hw2f9n12ghg
 *
 * - [Classic] The slideshows on older themed pages incorporate an embedded iframe element which uses PhotoShelter's newer Beam framework.
 *   The data within the embedded frame matches very closely to that used within the Element theme. The same functions are used to rip the page data,
 *   so be careful when updating the Element theme functions.
 * 
 * - [Classic] Internet Explorer 10 can't seem to stop the slideshows, also the images shown are out of step with what's being loaded behind the scenes.
 *   - I think this might have something to do with the fact that the slideshows are shown in Flash, if it's enabled. The UI is certainly different.
 *     Meanwhile maybe the data is also loaded in a regular view behind the Flash component..?
 *
 * - [Classic] The slideshow photos on some classic websites cannot be picked up because the embedded iframe is pointing to a different domain,
 *   eg. where the photographer has their own .dom domain (where the iframe is sourced from) in conjunction with a PhotoShelter account.
 *   - Example: solas.photoshelter.com, also other instances on PhotoShelter's own page listing sample websites: http://www.photoshelter.com/website-examples/
 *
 * - Problem page: http://andrewstrain.photoshelter.com/#!/index/I0000yAVi4Shw3CQ
 */


function isPhotoFilename(photoTitle)
{
   var lowercasedPhotoTitle = photoTitle.toLowerCase();

   // File types taken from PhotoShelter's list of supported image types.
   return (lowercasedPhotoTitle.endsWith('.jpg') || lowercasedPhotoTitle.endsWith('.tif') || lowercasedPhotoTitle.endsWith('.tiff') ||
           lowercasedPhotoTitle.endsWith('.raw') || lowercasedPhotoTitle.endsWith('.psd') || lowercasedPhotoTitle.endsWith('.dng') ||
           lowercasedPhotoTitle.endsWith('.pdf'));
}


function getPhotoIDFromImageLink(imageLink, photoIDPrefix)
{
   var photoIDStartIndex = imageLink.indexOf(photoIDPrefix);
   if (photoIDStartIndex !== -1)
   {
      photoIDStartIndex += photoIDPrefix.length;
      var photoIDEndIndex = imageLink.indexOf('/', photoIDStartIndex);
      if (photoIDEndIndex !== -1)
         return imageLink.substring(photoIDStartIndex, photoIDEndIndex);
   }

   return null;
}


function getNonFilenamePhotoTitle(parentElement, headingTagName, descriptionTagName)
{
   var photoName = null;
   var photoDescription = null;

   var childNodes = parentElement.childNodes;
   var childNode;

   /* Unfortunately the photo heading and description tags are without class names.
    * I'm relying on them being the only tags of their kind beneath the parent tag.
    */
   for (var childIndex = 0; childIndex < childNodes.length; childIndex ++)
   {
      childNode = childNodes[childIndex];
      if (childNode.nodeType === 1)
      {
         if (childNode.tagName === headingTagName)
            photoName = textContent(childNode).trim();
         else if (childNode.tagName === descriptionTagName)
            photoDescription = textContent(childNode).trim();
      }
   }

   if ((photoName !== null) && (! isPhotoFilename(photoName)))
      return photoName;
   else if ((photoDescription !== null) && (! isPhotoFilename(photoDescription)))
      return photoDescription;
   else
   {
      /* This would seem to go against the usual defensive Feedbactory idiom of returning null if in doubt of the data,
       * however it is known at the point of calling this function that the parentElement exists and is the root of the
       * photo name and description. If neither exists (which is known to happen), it can fairly safely be assumed that the photo
       * is untitled rather than an invalid photo. The risk being run here is that the headingTagName and descriptionTagName
       * may change to different tags, in which case this function would erroneously report that a photo is untitled rather
       * than bail out with a null (causing the photo as a whole to be rejected by Feedbactory).
       */
      return '';
   }
}


/****************************************************************************
 *
 ***************************************************************************/


function isCustomEnvVariableAvailable()
{
   return objectExists(window, 'C2_CFG', 'customEnv');
}


function getThemedGalleryPhotoData()
{
   var galleryParentElement = null;
   var isPhotoVisibleFunction = null;
   var getPhotoTitleFunction = getThemedGalleryPhotoTitle;

   if (isMarqueeTheme() || isPromenadeTheme() || isShuffleTheme() || isElementTheme() || isEastTheme() || isDowntownTheme() || isPivotTheme())
   {
      galleryParentElement = getFirstElementByClassNames(['gallerySingleImage']);
      if ((galleryParentElement !== null) && (galleryParentElement.tagName === 'SECTION'))
      {
         if (isMarqueeTheme())
            isPhotoVisibleFunction = isMarqueePhotoVisible;
         else if (isPromenadeTheme())
            isPhotoVisibleFunction = isPromenadePhotoVisible;
         else if (isShuffleTheme())
            isPhotoVisibleFunction = isShufflePhotoVisible;
         else if (isElementTheme())
            isPhotoVisibleFunction = isElementPhotoVisible;
         else if (isEastTheme())
            isPhotoVisibleFunction = isEastPhotoVisible;
         else if (isDowntownTheme())
            isPhotoVisibleFunction = isDowntownPhotoVisible;
         else if (isPivotTheme())
         {
            isPhotoVisibleFunction = isPivotPhotoVisible;
            getPhotoTitleFunction = getPivotPhotoTitle;
         }
      }
   }
   else if (isSonnetTheme())
   {
      galleryParentElement = getFirstElementByClassNames(['slideShow']);
      if ((galleryParentElement !== null) && (galleryParentElement.tagName === 'DIV'))
         isPhotoVisibleFunction = isSonnetPhotoVisible;
   }

   if ((galleryParentElement !== null) && (isPhotoVisibleFunction !== null))
      return getThemedGalleryPhotoDataDelegate(galleryParentElement, isPhotoVisibleFunction, getPhotoTitleFunction);
   else
      return [null, null, null];
}


function isMarqueeTheme()
{
   return (window.C2_CFG.customEnv.theme_name === 'Marquee');
}


function isMarqueePhotoVisible(gallerySingleImageElement)
{
   // If this function returns false, the active photo is being obscured by either the gallery grid or the top-level gallery selector screen.
   var stackTopElement = document.getElementById('mode-index');
   if ((stackTopElement !== null) && (stackTopElement.tagName === 'DIV') && hasClassName(stackTopElement, 'collapse'))
      return isElementPhotoVisible(gallerySingleImageElement);

   return false;
}


function isPromenadeTheme()
{
   return (window.C2_CFG.customEnv.theme_name === 'Promenade');
}


function isPromenadePhotoVisible()
{
   /* When the top level page is showing, the gallerySingleImage element is shifted to a 'mode stack' parent element rather than the 'stack-top' element.
    * When the gallery grid view is showing, the gallerySingleImage element has been shifted to the 'stack-top' parent element, however it will
    * have a 'hidden' class attribute.
    */
   var imageStageElement = getFirstElementByClassNames(['stack-top', 'gallerySingleImage', 'ImageStage']);
   return ((imageStageElement !== null) && (imageStageElement.tagName === 'DIV') && (! hasClassName(imageStageElement, 'hidden')));
}


function isShuffleTheme()
{
   return (window.C2_CFG.customEnv.theme_name === 'Shuffle');
}


function isShufflePhotoVisible()
{
   var stackTopElement = document.getElementById('mode-index');
   return ((stackTopElement !== null) && (stackTopElement.tagName === 'DIV') && hasClassName(stackTopElement, 'stack'));
}


function isElementTheme()
{
   return (window.C2_CFG.customEnv.theme_name === 'Element');
}


function isElementPhotoVisible(gallerySingleImageElement)
{
   /* Applies to both Marquee and Element style galleries.
    * When switching back & forth between the gallery and individual featured images, the ThumbViewer is simply hidden.
    * When the ThumbViewer is active, it will have an extra 'on' class attribute, during which time no photo should be considered
    * as being active.
    */
   var galleryViewerElement = getFirstElementByClassNames(['ThumbViewer'], gallerySingleImageElement);
   return ((galleryViewerElement !== null) && (! hasClassName(galleryViewerElement, 'on')));
}


function isEastTheme()
{
   return (window.C2_CFG.customEnv.theme_name === 'East');
}


function isEastPhotoVisible()
{
   return isShufflePhotoVisible();
}


function isDowntownTheme()
{
   return (window.C2_CFG.customEnv.theme_name === 'Downtown');
}


function isDowntownPhotoVisible(gallerySingleImageElement)
{
   var galleryViewerElement = getFirstElementByClassNames(['ThumbFocus'], gallerySingleImageElement);
   return ((galleryViewerElement !== null) && (! hasClassName(galleryViewerElement, 'on')));
}


function isPivotTheme()
{
   return (window.C2_CFG.customEnv.theme_name === 'Pivot');
}


function isPivotPhotoVisible()
{
   return isShufflePhotoVisible();
}


/* Leaving the unused 2nd parameter here as a reminder that this function signature is the same used as getThemedGalleryPhotoTitle,
 * being assigned to a callable function variable (getPhotoTitleFunction) in the getThemedGalleryPhotoData() function.
 */
function getPivotPhotoTitle(photoID, galleryParentElement)
{
   var imageElements = getElementsByClassNames(['stack-top', 'GalleryViewer', 'img']);
   var imageElement;

   for (var elementIndex = 0; elementIndex < imageElements.length; elementIndex ++)
   {
      imageElement = imageElements[elementIndex];
      if (imageElement.getAttribute('data-index') === photoID)
      {
         var metaElement = getFirstElementByClassNames(['meta'], imageElement);
         if ((metaElement !== null) && (metaElement.tagName === 'DIV'))
         {
            // Unlike other Beam themes, Pivot uses h2 and p tags for the photo title and description, respectively.
            return getNonFilenamePhotoTitle(metaElement, 'H2', 'P');
         }
      }
   }

   return null;
}


function isSonnetTheme()
{
   return (window.C2_CFG.customEnv.theme_name === 'Sonnet');
}


function isSonnetPhotoVisible(slideShowElement)
{
   return hasClassName(slideShowElement, 'on');
}


function getThemedGalleryPhotoDataDelegate(galleryParentElement, isPhotoVisibleFunction, getPhotoTitleFunction)
{
   var photoData = [null, null, null];

   if (isPhotoVisibleFunction(galleryParentElement))
   {
      photoData[0] = getThemedGalleryPhotoID(galleryParentElement);
      if (photoData[0] !== null)
      {
         photoData[1] = getThemedGalleryPhotographerID();
         photoData[2] = getPhotoTitleFunction(photoData[0], galleryParentElement);
      }
   }

   return photoData;
}


function getThemedGalleryPhotoID(galleryParentElement)
{
   var imageElement = getFirstElementByClassNames(['ImageStage', 'current'], galleryParentElement);
   if ((imageElement !== null) && (imageElement.tagName === 'DIV'))
   {
      var backgroundPhotoLink = imageElement.style.backgroundImage;
      return getPhotoIDFromImageLink(backgroundPhotoLink, '/img-get2/');
   }

   return null;
}


function getThemedGalleryPhotographerID()
{
   return window.C2_CFG.customEnv.label.trim();
}


/* The photo ID parameter is not used for this function, but the function signature must be the same as for getPivotPhotoTitle which
 * does use the parameter. One of thse functions is assigned to a callable function variable (getPhotoTitleFunction) in the getThemedGalleryPhotoData() function.
 */
function getThemedGalleryPhotoTitle(photoID, galleryParentElement)
{
   var photoTitleDivElement = getFirstElementByClassNames(['MetaViewer', 'content'], galleryParentElement);
   if ((photoTitleDivElement !== null) && (photoTitleDivElement.tagName === 'DIV'))
   {
      // All of the Beam themes except for Pivot appear to use h1 and div tags for photo title and description, respectively.
      return getNonFilenamePhotoTitle(photoTitleDivElement, 'H1', 'DIV');
   }

   return null;
}


/****************************************************************************
 *
 ***************************************************************************/


function getDefaultGalleryPhotoData(imageElement, photoIDPrefix)
{
   var photoData = [null, null, null];

   photoData[0] = getPhotoIDFromImageLink(imageElement.src, photoIDPrefix);
   if (photoData[0] !== null)
   {
      photoData[1] = getLegacyThemePhotographerID();
      if (photoData[1] !== null)
         photoData[2] = getLegacyThemePhotoTitle(imageElement);
   }

   return photoData;
}


function getLegacyThemeImageElement(targetClassNames)
{
   var imageWidgetElement = getFirstElementByClassNames(targetClassNames);
   if ((imageWidgetElement !== null) && (imageWidgetElement.tagName === 'DIV'))
   {
      var imageChildElements = imageWidgetElement.getElementsByTagName('img');

      for (var childIndex = 0; childIndex < imageChildElements.length; childIndex ++)
      {
         if (imageChildElements[childIndex].getAttribute('itemprop') === 'contentURL')
            return imageChildElements[childIndex];
      }
   }

   return null;
}


function getLegacyThemePhotographerID()
{
   var url = window.location.hostname;

   /* Run a couple of sanity checks: the first '.' in the hostname should be the start of the substring '.photoshelter.com',
    * which should also end the hostname string.
    */
   var userIDEndIndex = url.indexOf('.');
   if ((userIDEndIndex !== -1) && (userIDEndIndex === url.indexOf('.photoshelter.com')) && ((userIDEndIndex + 17) === url.length))
   {
      var userID = url.substring(0, userIDEndIndex);
      if (userID !== 'www')
         return userID;
   }

   return null;
}


function getLegacyThemePhotoTitle(imageElement)
{
   /* The titles (h1, name, description) provided in the class='imageBoxSub' section will often not match up with the title provided for
    * the photo in other gallery views, eg. marquee. The document title on the other hand always appears to be identical, at least the
    * prefix does - it must be parsed to grab the string before the ' | ' marker. This obviously opens up the possibility of a mishandled
    * title when the title string itself contains a ' | ', but it would be extremely rare. Also, sometimes a title contains multiple ' | '
    * separators between the title, gallery name, and the photographer's portfolio name, so using lastIndexOf() is not a good option.
    *
    * The image alt attribute here corresponds to the description shown in other gallery views, which is the fallback if the title
    * resembles an image filename string. If both resemble a filename string, an empty string is returned.
    */
   var photoTitle = null;

   var titleSeparatorIndex = document.title.indexOf(' | ');
   if (titleSeparatorIndex !== -1)
   {
      var rawPhotoTitle = document.title.substring(0, titleSeparatorIndex).trim();
      if ((rawPhotoTitle.length > 0) && (! isPhotoFilename(rawPhotoTitle)))
         photoTitle = rawPhotoTitle;
      else
      {
         var rawPhotoDescription = imageElement.alt.trim();
         if ((rawPhotoDescription.length > 0) && (! isPhotoFilename(rawPhotoDescription)))
            photoTitle = rawPhotoDescription;
         else
            photoTitle = '';
      }
   }

   return photoTitle;
}


/****************************************************************************
 *
 ***************************************************************************/


function getClassicPortfolioSlideShowElement()
{
   var slideShowElement = getFirstElementByClassNames(['PSPortfolio', 'psport_slider']);
   if ((slideShowElement !== null) && (slideShowElement.tagName === 'DIV'))
      return slideShowElement;

   return null;
}


function getClassicPortfolioPhotoData(slideShowElement)
{
   var photoData = [null, null, null];

   var activeSlideShowElement = getClassicPortfolioActiveElement(slideShowElement);
   if (activeSlideShowElement !== null)
   {
      photoData[0] = getClassicPortfolioPhotoID(activeSlideShowElement);
      if (photoData[0] !== null)
      {
         photoData[1] = getLegacyThemePhotographerID();
         if (photoData[1] !== null)
            photoData[2] = getClassicPortfolioPhotoTitle(activeSlideShowElement);
      }
   }

   return photoData;
}


function getClassicPortfolioActiveElement(slideShowElement)
{
   var targetElementOffset = -slideShowElement.style.pixelLeft;
   var childNodes = slideShowElement.childNodes;
   var childNode;

   for (var childIndex = 0; childIndex < childNodes.length; childIndex ++)
   {
      childNode = childNodes[childIndex];
      if ((childNode.nodeType === 1) && (childNode.tagName === 'DIV') && (childNode.style.pixelLeft === targetElementOffset))
         return childNode;
   }

   return null;
}


function getClassicPortfolioPhotoID(activeElement)
{
   var childNodes = activeElement.childNodes;
   var childNode;
   var photoID;

   for (var childIndex = 0; childIndex < childNodes.length; childIndex ++)
   {
      childNode = childNodes[childIndex];
      if ((childNode.nodeType === 1) && (childNode.tagName === 'IMG'))
      {
         photoID = getPhotoIDFromImageLink(childNode.src, '/img-get/');
         if (photoID !== null)
            return photoID;
      }
   }

   return null;
}


function getClassicPortfolioPhotoTitle(activeElement)
{
   var photoTitle = null;

   var photoNameElement = getFirstElementByClassNames(['psport_headline'], activeElement);
   if ((photoNameElement !== null) && (photoNameElement.tagName === 'SPAN'))
   {
      var photoName = textContent(photoNameElement).trim();
      if ((photoName.length > 0) && (! isPhotoFilename(photoName)))
         return photoName;
      else
         photoTitle = '';
   }

   var photoDescriptionElement = getFirstElementByClassNames(['psport_cap'], activeElement);
   if ((photoDescriptionElement !== null) && (photoDescriptionElement.tagName === 'DIV'))
   {
      var photoDescription = textContent(photoDescriptionElement).trim();
      if ((photoDescription.length > 0) && (! isPhotoFilename(photoDescription)))
         photoTitle = photoDescription;
      else
         photoTitle = '';
   }

   return photoTitle;
}


/****************************************************************************
 *
 ***************************************************************************/


function getEmbeddedSlideShowBaseElement()
{
   var embeddedDivElement = getFirstElementByClassNames(['psEmbed']);
   if ((embeddedDivElement !== null) && (embeddedDivElement.tagName === 'DIV') && (embeddedDivElement.getAttribute('data-ps-embed-type') === 'slideshow'))
   {
      var childNodes = embeddedDivElement.childNodes;
      var childNode;

      for (var childIndex = 0; childIndex < childNodes.length; childIndex ++)
      {
         childNode = childNodes[childIndex];
         if ((childNode.nodeType === 1) && (childNode.tagName === 'IFRAME'))
         {
            /* If the iFrame is sourced from a different domain than the parent window, some browsers will return null (WebKit),
             * others will throw an exception. Either way there's no way around it, but it should be handled gracefully by
             * the scheduled photo check exception handler.
             */
            var embeddedDocument = childNode.contentDocument;
            if (embeddedDocument !== null)
            {
               var baseElement = embeddedDocument.getElementById('mode-slideshow');
               if ((baseElement !== null) && (baseElement.tagName === 'DIV'))
                  return baseElement;
            }
         }
      }
   }

   return null;
}


function getEmbeddedSlideShowPhotoData(embeddedSlideShowBaseElement)
{
   var photoData = [null, null, null];

   photoData[0] = getThemedGalleryPhotoID(embeddedSlideShowBaseElement);
   if (photoData[0] !== null)
   {
      photoData[1] = getLegacyThemePhotographerID();

      // The first parameter is unused for a call from this code branch.
      photoData[2] = getThemedGalleryPhotoTitle(null, embeddedSlideShowBaseElement);
   }

   return photoData;
}


/****************************************************************************
 *
 ***************************************************************************/


function getActivePhotoData()
{
   var photoData = [null, null, null];

   if (isCustomEnvVariableAvailable())
   {
      // New, Beam platform themed photo pages.
      photoData = getThemedGalleryPhotoData();
   }
   else
   {
      /* 'Standard' or default view of the more fancy Beam themed photos above (still uses Beam under the hood).
       * The user is often directed here when they select 'More Info' next to a photo.
       * This view is also used as a fallback for older browsers, eg. IE <= 8.
       * It's also the view opened when the user selects a photo to open standalone from within Feedbactory, hence 'default'.
       */
      var defaultGalleryImageElement = getLegacyThemeImageElement(['imageWrap', 'imageWidget']);
      if (defaultGalleryImageElement !== null)
         photoData = getDefaultGalleryPhotoData(defaultGalleryImageElement, '/img-get2/');
      else
      {
         // Equivalent to the above stock standard view of individual photos, but for the pre-Beam 'classic' themes.
         var classicThemeImageElement = getLegacyThemeImageElement(['imageWidget']);
         if (classicThemeImageElement !== null)
            photoData = getDefaultGalleryPhotoData(classicThemeImageElement, '/img-get/');
         else
         {
            /* Horizontal slider view shown for classic themes when a user selects the portfolio menu option.
             * Not available for all classic pages.
             */
            var classicPortfolioSlideShowElement = getClassicPortfolioSlideShowElement();
            if (classicPortfolioSlideShowElement !== null)
               photoData = getClassicPortfolioPhotoData(classicPortfolioSlideShowElement);
            else
            {
               /* Slideshow presented in classic themed photo pages, albeit powered by Beam within
                * a separate iframe. Due to the separate iframe, there's the possibility of encountering
                * permission errors if the iframe is sourced from a different domain, eg. if the photographer
                * is linking back to their .com domain.
                */
               var embeddedSlideShowBaseElement = getEmbeddedSlideShowBaseElement();
               if (embeddedSlideShowBaseElement !== null)
                  photoData = getEmbeddedSlideShowPhotoData(embeddedSlideShowBaseElement);
            }
         }
      }
   }

   if ((photoData[0] !== null) && (photoData[1] !== null) && (photoData[2] !== null))
      return photoData;
   else
      return null;
}


/****************************************************************************
 *
 ***************************************************************************/


function main()
{
   var result = [true, null];

   try
   {
      if (hostCheck('photoshelter.com'))
      {
         if (document.readyState === 'complete')
            result[1] = getActivePhotoData();
         else
            result[0] = false;
      }
   }
   catch (exception)
   {
      if (debug)
      {
         if (objectExists(exception, 'stack'))
            consoleError(exception.stack.toString());
         else
            consoleError(exception.toString());
      }
   }

   return result;
}