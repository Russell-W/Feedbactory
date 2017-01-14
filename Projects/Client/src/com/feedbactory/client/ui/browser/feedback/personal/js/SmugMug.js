/* Memos:
 * - There's a bit of work involved to handle SmugMug because of the number of different possibilities for an individual photo to be shown:
 *
 *   1. Legacy SmugMug gallery view (not supported - see the Photo Notes doc).
 *   2. Legacy SmugMug lightbox (fullscreen photo) view (not supported - see the Photo Notes doc).
 *   3. New SmugMug gallery view.
 *   4. New SmugMug lightbox (fullscreen photo) view.
 *
 * URL: Null for new smugmug, for legacy: <albumID_albumkey>, <imagekey>
 * - This in a nutshell is why legacy isn't supported - it would allow for server fragmentation if/when legacy albums migrate to the newer SmugMug themes.
 *
 * - SmugMug photo galleries made 'anonymous' by changing the settings to hide photographer info ('Hide Owner') from the gallery name and hyperlinks will not
 *   work in Feedbactory, as the photographer ID cannot be picked up. Confirmed with a test account that the user account ID appears nowhere on the page.
 *   The URL to the gallery page changes to www.smugmug.com/gallery/<someID>. Incidentally though if the user makes their gallery anonymous, the previous links
 *   still seem to work, which is good.
 * 
 * - If loading a lightbox image directly (ie. URL appended with a '/A'), the gallery behind will not be initialised until the lightbox is closed. Useful to know
 *   if I was considering trying to link the lightbox photo ID to the gallery underneath to try to gather the photo data.
 * 
 * - Picking up the photographer's username from either a main gallery or lightbox link will often fail due to a) the links attached to it point to a domain
 *   such as barrysphotography.com rather than barrysphotography.smugmug.com, or b) the links attached to it are prefaced with 'photos.smugmug.com' rather
 *   than 'barrysphotography.smugmug.com'. These links can often be found on keyword galleries, eg. www.smugmug.com/keyword/space . Technically I could pick
 *   up the photos from case a) and even grab a reference to the link at the arbitrary domain so that the users can open it in a new browser window, but from
 *   that point Feedbactory won't be able to recognise the photo since it won't be triggered for that domain.
 *
 * - It seems the <meta property="og:title"> cannot be relied upon to produce the correct photo title - see the Photo Notes for more details including examples.
 *
 * - The <meta property="og:image"> appears to have the canonical image URL. Images on featured galleries can have different links on the IMG tag
 *   (see Photo Notes.txt), which could lead to fragmentation on the server if users are arriving at the same images via different galleries.
 */


function getPhotoIDFromPhotoLink(photoLink)
{
   var photoIDStartIndex = photoLink.lastIndexOf('/i-');
   if (photoIDStartIndex !== -1)
   {
      photoIDStartIndex += 3;

      // Don't get thrown off by a trailing slash.
      var trailingSlashIndex = photoLink.indexOf('/', photoIDStartIndex);
      return (trailingSlashIndex === -1) ? photoLink.substring(photoIDStartIndex) : photoLink.substring(photoIDStartIndex, trailingSlashIndex);
   }

   return null;
}


function getPhotoUserIDFromPhotoLink(photoLink)
{
   var domainStartIndex = photoLink.indexOf('://');
   if (domainStartIndex !== -1)
   {
      domainStartIndex += 3;
      var smugMugSuffixIndex = photoLink.indexOf('.smugmug.com/', domainStartIndex);
      if (smugMugSuffixIndex !== -1)
      {
         /* Perform some basic sanity checks on the user ID.
          * Need to weed out unlikely but incorrect forms such as 'www.barrysphotos.smugmug.com',
          * likewise 'www.barrysphotos.com/gallery.smugmug.com/etc'.
          * Finally, a user ID of 'photos' is not a genuine user ID since it's used as a general prefix by SmugMug to
          * link to many photos within its 'keyword' galleries, eg. photos.smugmug.com/etc.
          */
         var firstSlashIndex = photoLink.indexOf('/', domainStartIndex);
         var firstDotIndex = photoLink.indexOf('.', domainStartIndex);
         if ((firstSlashIndex > smugMugSuffixIndex) && (firstDotIndex === smugMugSuffixIndex))
         {
            var userID = photoLink.substring(domainStartIndex, smugMugSuffixIndex);
            if (userID !== 'photos')
               return userID;
         }
      }
   }

   return null;
}


function getPhotoUserIDFromMetaTag(photoID)
{
   /* The meta tag link may also suffer from the problem of having an embedded non-genuine username as described in the getPhotoIDFromLightboxElement()
    * function, but unlike the lightbox link it requires no parsing to get to the embedded URL.
    * The non-genuine username issue, if present, will be picked up by the getPhotoUserIDFromPhotoLink() function.
    */
   var metaElements = document.head.getElementsByTagName('meta');
   for (var elementIndex = 0; elementIndex < metaElements.length; elementIndex ++)
   {
      var element = metaElements[elementIndex];
      if (element.getAttribute('property') === 'og:url')
      {
         /* Run a sanity check on the URL - it should contain the matching photo ID that was grabbed from elsewhere,
          * at the expected position within the link.
          */
         if (element.content.endsWith('/i-' + photoID))
            return getPhotoUserIDFromPhotoLink(element.content);
         else
            break;
      }
   }

   return null;
}


function getPhotoTitle(galleryRootElement, infoClassNames)
{
   var photoTitle = null;

   var infoRootElement = getFirstElementByClassNames(infoClassNames, galleryRootElement);
   if ((infoRootElement !== null) && (infoRootElement.tagName === 'DIV'))
   {
      /* If the sm-xxxx-info element has been found, default the photo title to 'Untitled' (empty string) since it's possible that the
       * child elements will not exist at all if the photo hasn't been given a title. This will occur in the lightbox view.
       * The slight danger of this approach is that the child element class names may have changed between versions, leading to Feedbactory
       * erroneously labeling a photo as 'Untitled' when the title is in fact located within different tags.
       */
      photoTitle = '';
      var photoName = null;
      var photoDescription = null;

      var childNodes = infoRootElement.childNodes;
      var childNode;

      for (var childIndex = 0; childIndex < childNodes.length; childIndex ++)
      {
         childNode = childNodes[childIndex];

         /* Don't enforce the tagName, it changes between SmugMug default gallery style and lightbox view, at least for the title.
          * The 'data-name' attribute is used here because the elements are without class names in the regular SmugMug default gallery style.
          */
         if (childNode.nodeType === 1)
         {
            if (childNode.getAttribute('data-name') === 'Title')
               photoName = textContent(childNode).trim();
            else if (childNode.getAttribute('data-name') === 'CaptionRaw')
               photoDescription = textContent(childNode).trim();
         }

         // Allow a non-empty photo description to override an empty photo title.
         if ((photoName !== null) && (photoName !== ''))
            photoTitle = photoName;
         else if (photoDescription !== null)
            photoTitle = photoDescription;
      }
   }

   return photoTitle;
}


function getPhotoThumbnailElementsFromMetaTag(photoID)
{
   /* The meta tag link may also suffer from the problem of having an embedded non-genuine username as described in the getPhotoIDFromLightboxElement()
    * function, but unlike the lightbox link it requires no parsing to get to the embedded URL.
    * The non-genuine username issue, if present, will be picked up by the getPhotoUserIDFromPhotoLink() function.
    */
   var metaElements = document.head.getElementsByTagName('meta');
   for (var elementIndex = 0; elementIndex < metaElements.length; elementIndex ++)
   {
      var element = metaElements[elementIndex];
      if (element.getAttribute('property') === 'og:image')
         return getPhotoThumbnailElementsFromImageSource(element.content, photoID);
   }

   return null;
}


function getPhotoThumbnailElementsFromImageSource(imageSource, photoID)
{
   if (imageSource.indexOf('?') !== -1)
      return null;

   var albumFolderStartIndex = imageSource.indexOf('.smugmug.com/');
   if (albumFolderStartIndex !== -1)
   {
      albumFolderStartIndex += 13;

      var filenameStartIndex = imageSource.lastIndexOf('/');
      if (filenameStartIndex !== -1)
      {
         filenameStartIndex ++;
         var suffixStartIndex = imageSource.lastIndexOf('.');

         if (suffixStartIndex > filenameStartIndex)
         {
            suffixStartIndex ++;
            var filenameSuffix = imageSource.substring(suffixStartIndex);

            // Check: photo link ends with /filename-<something non-blank>.<some image format>.
            suffixStartIndex = imageSource.lastIndexOf('-', suffixStartIndex - 3);
            if (suffixStartIndex > filenameStartIndex)
            {
               var filename = imageSource.substring(filenameStartIndex, suffixStartIndex);
               var albumFolderEndIndex = nthLastIndexOf(imageSource, '/', 3, filenameStartIndex - 2);

               /* Check against photoID within the link:
                * The link should look like /<album folder1>/<album folder2>/<etc>/i-<photoID>/<something>/<something>/<filename>.
                */
               if ((albumFolderEndIndex !== -1) && (imageSource.indexOf('/i-' + photoID, albumFolderEndIndex) === albumFolderEndIndex))
               {
                  var albumFolder = imageSource.substring(albumFolderStartIndex, albumFolderEndIndex);
                  return [albumFolder, filename, filenameSuffix];
               }
            }
         }
      }
   }

   return null;
}


function nthLastIndexOf(sourceString, targetCharacter, n, startIndex)
{
   // Assumes that startIndex (from where to move backwards through the string) if supplied is never 0, which would cause problems here.
   startIndex = startIndex || (sourceString.length - 1);

   var matchNumber = 0;

   for (var characterIndex = startIndex; characterIndex >= 0; characterIndex --)
   {
      if (sourceString.charAt(characterIndex) === targetCharacter)
      {
         matchNumber ++;

         if (matchNumber === n)
            return characterIndex;
      }
   }

   return -1;
}


function getPhotoTags(rootElement, photoTagClassIdentifiers)
{
   /* At this point it's already established that the photo's sm-xxxx-info element is present and where it should be.
    * However when the photo has no tags, the sm-lightbox-keywords element will not be present in the lightbox view whereas the corresponding
    * sm-tile-keywords will be present for the regular gallery view. To ensure that the lightbox photo isn't rejected by Feedbactory when this element
    * isn't found, the photo tags should default to [] rather than null. This goes a little against the usual defensive examination approach by Feedbactory
    * in bailing out if a photo element isn't found (maybe not yet loaded!) or has changed since the last known version.
    */
   var photoTags = [];
   var parentTagElement = getFirstElementByClassNames(photoTagClassIdentifiers, rootElement);

   if ((parentTagElement !== null) && (parentTagElement.tagName === 'P'))
   {
      var childElement;

      for (var childIndex = 0; childIndex < parentTagElement.childNodes.length; childIndex ++)
      {
         childElement = parentTagElement.childNodes[childIndex];
         if ((childElement !== null) && (childElement.nodeType === 1) && (childElement.tagName === 'A') && hasClassName(childElement, 'sm-muted'))
         {
            /* Don't lowercase here, let Java do it since it must override the Locale used to produce the correct result.
             * There's also no need to trim the string since a Java process will parse within each photoTag looking
             * for keywords.
             */
            photoTags[photoTags.length] = textContent(childElement);
         }
      }
   }

   return photoTags;
}


/****************************************************************************
 *
 ***************************************************************************/


function getLightboxElement()
{
   // When the lightbox is hidden, the outer lightbox class is changed from sm-lightbox-focused to sm-lightbox-hidden.
   var lightboxElement = getFirstElementByClassNames(['sm-lightbox-focused']);
   if ((lightboxElement !== null) && (lightboxElement.tagName === 'DIV'))
      return lightboxElement;

   return null;
}


function getPhotoDataFromLightboxPage(lightboxRootElement)
{
   /* itemData[0]: Photo ID,
    * itemData[1]: UserID,
    * itemData[2]: Photo title or caption if none,
    * itemData[3]: Photo URL elements: <URL path>, <filename without the -S.jpg or -M.jpg>
    * itemData[4]: Photo tags.
    */
   var photoData = [null, null, null, null, null];

   var lightboxImageElement = getFirstElementByClassNames(['sm-lightbox-image'], lightboxRootElement);
   if ((lightboxImageElement !== null) && (lightboxImageElement.tagName === 'IMG'))
   {
      var encodedLightboxImageLink;

      /* The lightbox image often uses a copy protection style where the src is a dummy gif while the real link is set to the style.backgroundImage.
       * In this instance the link always (?) seems to be enclosed in a function: url('link').
       *
       * The other reason for only grabbing the photoID and nothing else is that depending on the page context, the lightbox link may or may not
       * contain the photographer's username, eg. for SmugMug aggregration by keyword galleries the lightbox link will be something like
       * photos.smugmug.com/etc rather than barrysphotography.smugmug.com/etc.
       */
      if (lightboxImageElement.style.backgroundImage.trim() !== '')
         encodedLightboxImageLink = lightboxImageElement.style.backgroundImage;
      else
         encodedLightboxImageLink = lightboxImageElement.src;

      photoData[0] = getLightboxPhotoID(encodedLightboxImageLink);
      if (photoData[0] !== null)
      {
         photoData[1] = getPhotoUserIDFromPhotoLink(encodedLightboxImageLink);

         // getPhotoUserIDFromPhotoLink() will fail if the image link is of the form 'photos.smugmug.com', so try grabbing the user ID from the meta tag.
         if (photoData[1] === null)
            photoData[1] = getPhotoUserIDFromMetaTag(photoData[0]);

         if (photoData[1] !== null)
         {
            photoData[2] = getPhotoTitle(lightboxRootElement, ['sm-lightbox-info']);
            if (photoData[2] !== null)
            {
               /* The og:image meta tag appears to have the canonical image URL. Images on featured galleries can have different links on the IMG tag
                * (see Photo Notes.txt), which could lead to fragmentation on the server if users are browsing the same images via different galleries.
                */
               photoData[3] = getPhotoThumbnailElementsFromMetaTag(photoData[0]);
               if (photoData[3] !== null)
                  photoData[4] = getPhotoTags(lightboxRootElement, ['sm-lightbox-info', 'sm-lightbox-keywords']);
            }
         }
      }
   }

   return photoData;
}


function getLightboxPhotoID(encodedLightboxImageLink)
{
   var photoIDStartIndex = nthLastIndexOf(encodedLightboxImageLink, '/', 4);
   if ((photoIDStartIndex !== -1) && (encodedLightboxImageLink.indexOf('/i-', photoIDStartIndex) === photoIDStartIndex))
   {
      photoIDStartIndex += 3;
      var photoIDEndIndex = encodedLightboxImageLink.indexOf('/', photoIDStartIndex);

      if (photoIDEndIndex !== -1)
         return encodedLightboxImageLink.substring(photoIDStartIndex, photoIDEndIndex);
   }

   return null;
}


/****************************************************************************
 *
 ***************************************************************************/


function getGalleryElement()
{
   var galleryRootElement = document.getElementById('sm-gallery');
   if ((galleryRootElement !== null) && (galleryRootElement.tagName === 'DIV'))
      return galleryRootElement;

   return null;
}


function isSmugMugStyleGallery(galleryRootElement)
{
   return hasClassName(galleryRootElement, 'sm-gallery-smugmug');
}


function getActiveSmugMugPhotoData(galleryRootElement)
{
   /* itemData[0]: Photo ID,
    * itemData[1]: UserID,
    * itemData[2]: Photo title or caption if none,
    * itemData[3]: Photo URL elements: <URL path>, <filename without the -S.jpg or -M.jpg>
    * itemData[4]: Photo tags.
    */
   var photoData = [null, null, null, null, null];

   var smugMugGalleryElement = getFirstElementByClassNames(['sm-gallery-image-container'], galleryRootElement);
   if ((smugMugGalleryElement !== null) && (smugMugGalleryElement.tagName === 'DIV'))
   {
      var activeGalleryPhotoLinkElement = getFirstElementByClassNames(['sm-tile-content'], smugMugGalleryElement);
      if ((activeGalleryPhotoLinkElement !== null) && (activeGalleryPhotoLinkElement.tagName === 'A'))
      {
         photoData[0] = getPhotoIDFromPhotoLink(activeGalleryPhotoLinkElement.href);

         if (photoData[0] !== null)
         {
            photoData[1] = getPhotoUserIDFromPhotoLink(activeGalleryPhotoLinkElement.href);

            // getPhotoUserIDFromPhotoLink() will fail if the image link is of the form 'photos.smugmug.com', so try grabbing the user ID from the meta tag.
            if (photoData[1] === null)
               photoData[1] = getPhotoUserIDFromMetaTag(photoData[0]);

            if (photoData[1] !== null)
            {
               photoData[2] = getPhotoTitle(smugMugGalleryElement, ['sm-tile-info']);
               if (photoData[2] !== null)
               {
                  photoData[3] = getPhotoThumbnailElementsFromMetaTag(photoData[0]);
                  if (photoData[3] !== null)
                     photoData[4] = getPhotoTags(smugMugGalleryElement, ['sm-tile-info', 'sm-tile-keywords']);
               }
            }
         }
      }
   }

   return photoData;
}


/****************************************************************************
 *
 ***************************************************************************/


function getActivePhotoData()
{
   var photoData = [null, null, null, null, null];

   /* First look for a focused lightbox image - if there is one present, it will be superimposed on top
    * of the gallery, making it the current image. It's possible for the lightbox to be showing a different image than the
    * currently active gallery image (if any), if the user is navigating via the lightbox.
    */
   var lightboxRootElement = getLightboxElement();
   var galleryRootElement = getGalleryElement();
   var isSmugMugStyleGalleryElement = (galleryRootElement !== null) && isSmugMugStyleGallery(galleryRootElement);

   if (lightboxRootElement !== null)
      photoData = getPhotoDataFromLightboxPage(lightboxRootElement);
   else if ((galleryRootElement !== null) && isSmugMugStyleGalleryElement)
   {
      /* As mentioned above, the gallery's current featured image (SmugMug gallery style only) is overridden by any focused lightbox image.
       * Only attempt to set the active photo data to the active gallery photo if there isn't a visible lightbox image, and of course
       * if the gallery type is the SmugMug gallery, which has a featured photo.
       */
      photoData = getActiveSmugMugPhotoData(galleryRootElement);
   }

   if ((photoData[0] !== null) && (photoData[1] !== null) && (photoData[2] !== null) && (photoData[3] !== null) && (photoData[4] !== null))
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
      if (hostCheck('smugmug.com'))
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