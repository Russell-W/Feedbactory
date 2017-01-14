/* Memos:
 * - The thumbnail photos may end in .jpg, .JPG, .jpeg, maybe other variations; this information must be collected by Feedbactory rather than append
 *   .jpg in every case.
 */


function getActivePhotoData()
{
   /* 0: photo ID
    * 1: photographer ID
    * 2: display name
    * 3: thumbnail ID
    * 4: keywords
    */
   var activePhotoData = [null, null, null, null, null];

   var lightboxPhotoDivElement = getLightboxPhotoDivElement();
   if (lightboxPhotoDivElement !== null)
      activePhotoData = getLightboxPhotoData(lightboxPhotoDivElement);
   else
      activePhotoData = getRegularPhotoData();

   if ((activePhotoData[0] !== null) && (activePhotoData[1] !== null) && (activePhotoData[2] !== null) && (activePhotoData[3] !== null) && (activePhotoData[4] !== null))
      return activePhotoData;
   else
      return null;
}


function getLightboxPhotoData(lightboxPhotoDivElement)
{
   var activePhotoData = [null, null, null, null, null];

   activePhotoData[0] = getLightboxPhotoID(lightboxPhotoDivElement);
   if (activePhotoData[0] !== null)
   {
      var photoURL = getLightboxPhotoURL(lightboxPhotoDivElement);
      if (photoURL !== null)
      {
         activePhotoData[1] = getPhotographerID(photoURL);
         if (activePhotoData[1] !== null)
         {
            var lightboxAsideElement = getLightboxAsideElement();
            if (lightboxAsideElement !== null)
            {
               activePhotoData[2] = getLightboxPhotoDisplayName(lightboxAsideElement);
               if (activePhotoData[2] !== null)
               {
                  activePhotoData[3] = getThumbnailID(photoURL, activePhotoData[1]);
                  if (activePhotoData[3] !== null)
                     activePhotoData[4] = getPhotoTags(lightboxAsideElement, ['imdl-aside-info', 'imdl-aside-story']);
               }
            }
         }
      }
   }

   return activePhotoData;
}


function getLightboxPhotoDivElement()
{
   var photoDivElement = getFirstElementByClassNames(['imdl', 'imdl-image-box', 'imdl-img', 'imdl-box', 'imdl-fullscreen']);
   if ((photoDivElement !== null) && (photoDivElement.tagName === 'DIV'))
      return photoDivElement;

   return null;
}


function getLightboxPhotoID(photoDivElement)
{
   var photoLinkElements = photoDivElement.getElementsByTagName('a');
   if (photoLinkElements.length === 1)
   {
      var photoLink = trimURLArgument(photoLinkElements[0].href.trim());

      var photoIDStartIndex = photoLink.indexOf('/image/');
      if (photoIDStartIndex !== -1)
      {
         photoIDStartIndex += 7;

         var lastSlashIndex = photoLink.indexOf('/', photoIDStartIndex);
         if (lastSlashIndex === -1)
            return photoLink.substring(photoIDStartIndex);
         else if (lastSlashIndex === (photoLink.length - 1))
            return photoLink.substring(photoIDStartIndex, lastSlashIndex);
         // else unknown/invalid format.
      }
   }

   return null;
}


function getLightboxPhotoURL(photoDivElement)
{
   var photoThumbnailElements = photoDivElement.getElementsByTagName('img');
   if (photoThumbnailElements.length === 1)
   {
      // May end in .jpg, .JPG, .jpeg, maybe other variations, eg.png. This information must be preserved.
      return trimURLArgument(photoThumbnailElements[0].src.trim());
   }

   return null;
}


function getPhotographerID(photoThumbnailURL)
{
   var photographerIDStartIndex = photoThumbnailURL.lastIndexOf('/');
   if (photographerIDStartIndex !== -1)
   {
      photographerIDStartIndex ++;
      var photographerIDEndIndex = photoThumbnailURL.indexOf('_', photographerIDStartIndex);
      if (photographerIDEndIndex !== -1)
         return photoThumbnailURL.substring(photographerIDStartIndex, photographerIDEndIndex);
   }

   return null;
}


function getLightboxAsideElement()
{
   var photoAsideElement = getFirstElementByClassNames(['imdl', 'imdl-aside']);
   if ((photoAsideElement !== null) && (photoAsideElement.tagName === 'ASIDE'))
      return photoAsideElement;

   return null;
}


function getLightboxPhotoDisplayName(photoAsideElement)
{
   var photoTitleElement = getFirstElementByClassNames(['imdl-aside-info', 'imdl-aside-ttl'], photoAsideElement);
   if ((photoTitleElement !== null) && (photoTitleElement.tagName === 'H4'))
   {
      var photoTitle = textContent(photoTitleElement).trim();
      if (photoTitle === 'Untitled')
         photoTitle = '';

      var photographerNameDivElement = getFirstElementByClassNames(['imdl-aside-box', 'imdl-aside-name'], photoAsideElement);
      if ((photographerNameDivElement !== null) && (photographerNameDivElement.tagName === 'DIV'))
      {
         var photographerNameLinkElements = photographerNameDivElement.getElementsByTagName('a');
         if (photographerNameLinkElements.length === 1)
         {
            var photographerName = textContent(photographerNameLinkElements[0]).trim();
            return [photoTitle, photographerName];
         }
      }
   }

   return null;
}


function getThumbnailID(photoThumbnailURL, photographerID)
{
   var thumbnailIDStartIndex = photoThumbnailURL.lastIndexOf('/' + photographerID + '_');
   if (thumbnailIDStartIndex !== -1)
   {
      thumbnailIDStartIndex += photographerID.length + 2;
      // Don't discard the image filename suffix, since it can vary between photos, including the case.
      return photoThumbnailURL.substring(thumbnailIDStartIndex);
   }

   return null;
}


function getPhotoTags(startElement, targetClassNames)
{
   var photoTags = null;

   var photoTagsParentElement = getFirstElementByClassNames(targetClassNames, startElement);
   if ((photoTagsParentElement !== null) && (photoTagsParentElement.tagName === 'P'))
   {
      photoTags = [];

      var photoTagElements = photoTagsParentElement.getElementsByTagName('a');
      var photoTag;

      for (var tagIndex = 0; tagIndex < photoTagElements.length; tagIndex ++)
      {
         /* Let the Java parsing of the tags handle:
          * - The trimming of the string.
          * - The lowercasing of the string.
          * - The handling of non-alphanumerical characters.
          */
         photoTag = textContent(photoTagElements[tagIndex]);
         if (photoTag.length > 0)
            photoTags[photoTags.length] = photoTag;
      }
   }

   return photoTags;
}


function getRegularPhotoData()
{
   var activePhotoData = [null, null, null, null, null];

   activePhotoData[0] = getRegularPhotoID();

   // Perform a sanity check on the state object's photo ID to ensure that the correct data is being ripped.
   if ((activePhotoData[0] !== null) && (activePhotoData[0] === getStateObjectPhotoID()))
   {
      var photoURL = getRegularPhotoURL();
      if (photoURL !== null)
      {
         activePhotoData[1] = getPhotographerID(photoURL);
         if (activePhotoData[1] !== null)
         {
            activePhotoData[2] = getRegularPhotoDisplayName();
            if (activePhotoData[2] !== null)
            {
               activePhotoData[3] = getThumbnailID(photoURL, activePhotoData[1]);
               if (activePhotoData[3] !== null)
                  activePhotoData[4] = getPhotoTags(document, ['imgp-info', 'imgp-info-aside', 'imgp-aside-box', 'imgp-desc']);
            }
         }
      }
   }

   return activePhotoData;
}


function getRegularPhotoID()
{
   var pathname = window.location.pathname;
   if (pathname.indexOf('/image/') === 0)
   {
      var slashIndex = pathname.indexOf('/', 7);
      if (slashIndex === -1)
         return pathname.substring(7);
      else
         return pathname.substring(7, slashIndex);
   }

   return null;
}


function getStateObjectPhotoID()
{
   /* This object is unfortunately not easily (or reliably?) available from the lightbox view, although it can be found
    * by digging through the React object, eg. React.__internals.Mount._instancesByReactRootID['.0'].state.image (yuck).
    * Maybe it will always be available via that reference, maybe not...
    */
   if (objectExists(window, 'State', 'image', 'image_id'))
      return stringify(window.State.image.image_id);

   return null;
}


function getRegularPhotoURL()
{
   if (objectExists(window.State.image, 'image_urls', 'small'))
   {
      // May end in .jpg, .JPG, .jpeg, maybe other variations, eg.png. This information must be preserved.
      return trimURLArgument(window.State.image.image_urls.small.trim());
   }

   return null;
}


function getRegularPhotoDisplayName()
{
   if (objectExists(window.State.image, 'image_name'))
   {
      var photoTitle = window.State.image.image_name.trim();

      /* Unlike the lightbox photo title ripped from the DOM, the JavaScript object will be equal to the raw title, ie. not converted to 'Untitled'
       * if the title is actually blank. However if the user has actually set the title to 'Untitled', it would presumably be non-blank here.
       * To ensure that the title ripped here is given the same treatment as the lightbox title ripped from the DOM (preventing fragmentation on the server),
       * 'Untitled' must be converted to the blank string to be safe.
       */
      if (photoTitle === 'Untitled')
         photoTitle = '';

      if (objectExists(window.State.image, 'user', 'display_name'))
      {
         var photographerName = window.State.image.user.display_name.trim();
         return [photoTitle, photographerName];
      }
   }

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
      if (hostCheck('youpic.com'))
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