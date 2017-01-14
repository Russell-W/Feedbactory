/* Memos:
 * - Sometimes there is an embedded iframe element (id='photoframe') containing the main photo and associated data elements.
 *   This seems to be present when the user clicks a thumbnail to open a photo within the same browser tab. When a thumbnail
 *   is clicked to open in a new browser tab, the embedded iframe isn't there.
 *
 * - Photo without a title: http://www.viewbug.com/photo/31080501, also http://www.viewbug.com/photo/28237811
 *   - Photos without an explicit title are automatically given the title of 'Photo' by ViewBug in almost all occurrences on the page,
 *     with the exception of the alt tag and the document title.
 *
 * - Photo having a different title in the alt tag: http://www.viewbug.com/contests/lighthouses-photo-contest/4820514
 */


function getActivePhotoData()
{
   /* 0: photo ID
    * 1: photographer ID
    * 2: display name
    * 3: thumbnail ID
    * 4: tags
    */
   var activePhotoData = [null, null, null, null, null];

   var rootDocument = getIFrameMainPhotoDocumentElement();
   if (rootDocument === null)
      rootDocument = document;

   activePhotoData[0] = getPhotoID(rootDocument);
   if (activePhotoData[0] !== null)
   {
      var photographerDetailsElement = getPhotographerDetailsElement(rootDocument);
      if (photographerDetailsElement !== null)
      {
         activePhotoData[1] = getPhotographerID(photographerDetailsElement);
         if (activePhotoData[1] !== null)
         {
            activePhotoData[2] = getPhotoDisplayName(rootDocument, photographerDetailsElement);
            if (activePhotoData[2] !== null)
            {
               activePhotoData[3] = getPhotoThumbnailID(rootDocument, activePhotoData[0]);
               if (activePhotoData[3] !== null)
               {
                  activePhotoData[4] = getPhotoTags(rootDocument);
                  if (activePhotoData[4] !== null)
                     return activePhotoData;
               }
            }
         }
      }
   }

   return null;
}


function getIFrameMainPhotoDocumentElement()
{
   var iframeElement = document.getElementById('photoframe');
   if ((iframeElement !== null) && (iframeElement.tagName === 'IFRAME'))
      return iframeElement.contentDocument;

   return null;
}


function getPhotoID(rootDocument)
{
   var photoWrapperElement = rootDocument.getElementById('photo-wrapper');
   if ((photoWrapperElement !== null) && (photoWrapperElement.tagName === 'DIV'))
   {
      var protectPhotoElement = getFirstElementByClassNames(['protect-photo'], photoWrapperElement);
      if ((protectPhotoElement !== null) && (protectPhotoElement.tagName === 'DIV'))
      {
         /* Grabbing the photo ID from the protect-photo element rather than the image URL (where it's embedded) allows a double check of the ID to be performed later
          * when obtaining the thumbnail URL elements.
          */
         var photoID = protectPhotoElement.getAttribute('media_id');
         if (photoID !== null)
            return photoID.trim();
      }
   }

   return null;
}


function getPhotographerDetailsElement(rootDocument)
{
   var photographerDetailsElement = getFirstElementByClassNames(['main_content', 'topphoto', 'profile', 'details'], rootDocument);
   if ((photographerDetailsElement !== null) && (photographerDetailsElement.tagName === 'DIV'))
      return photographerDetailsElement;

   return null;
}


function getPhotographerID(photographerDetailsElement)
{
   var photographerLinkElements = photographerDetailsElement.getElementsByTagName('a');
   if (photographerLinkElements.length === 1)
   {
      var userID = photographerLinkElements[0].getAttribute('user_id');
      if (userID !== null)
         return userID.trim();
   }

   return null;
}


function getPhotoDisplayName(rootDocument, photographerDetailsElement)
{
   var photoElement = rootDocument.getElementById('main_image');
   if ((photoElement !== null) && (photoElement.tagName === 'IMG'))
   {
      var title = photoElement.alt.trim();
      var photographerElements = photographerDetailsElement.getElementsByTagName('H6');
      if (photographerElements.length === 1)
      {
         var photographerName = textContent(photographerElements[0]).trim();
         if (photographerName.length > 0)
            return [title, photographerName];
      }
   }

   return null;
}


function getPhotoThumbnailID(rootDocument, photoID)
{
   var photoElement = rootDocument.getElementById('main_image');
   if ((photoElement !== null) && (photoElement.tagName === 'IMG'))
   {
      // No URL argument is currently used, but if a dummy one were to be added it might fool the parser here if it's not removed first.
      var rawPhotoURL = trimURLArgument(photoElement.src);

      // In theory the IMG URL used could be relative, starting with /media/mediafiles/, so don't check for the protocol and domain prefix.
      var thumbnailIDStartIndex = rawPhotoURL.indexOf('/media/mediafiles/');
      if (thumbnailIDStartIndex !== -1)
      {
         thumbnailIDStartIndex += 18;
         var thumbnailIDEndIndex = rawPhotoURL.lastIndexOf('/' + photoID + '_');
         if (thumbnailIDEndIndex !== -1)
            return rawPhotoURL.substring(thumbnailIDStartIndex, thumbnailIDEndIndex);
      }
   }

   return null;
}


function getPhotoTags(rootDocument)
{
   var photoTags = null;

   var photoInfoElement = rootDocument.getElementById('photo-info');
   if ((photoInfoElement !== null) && (photoInfoElement.tagName === 'DIV'))
   {
      photoTags = [];

      // Element will not be present if the photo has no tags.
      var photoTagsParentElement = getFirstElementByClassNames(['tags'], photoInfoElement);
      if ((photoTagsParentElement !== null) && (photoTagsParentElement.tagName === 'DIV'))
      {
         var photoTagElements = photoTagsParentElement.getElementsByTagName('a');
         var photoTag;

         for (var tagIndex = 0; tagIndex < photoTagElements.length; tagIndex ++)
         {
            /* Return a raw array of tag strings from the page to Java. Let the Java parsing of the tags handle:
             * - The trimming of the string.
             * - The globally consistent (ie. non-locale-specific) lowercasing of the string.
             * - The handling of non-alphanumerical characters.
             * - Tokenising each tag string.
             * - Merging duplicate tags.
             * - Enforcing a maximum number of tags.
             */
            photoTag = textContent(photoTagElements[tagIndex]);
            if (photoTag.length > 0)
               photoTags[photoTags.length] = photoTag;
         }
      }
   }

   return photoTags;
}


/****************************************************************************
 *
 ***************************************************************************/


function main()
{
   var result = [true, null];

   try
   {
      if (hostCheck('viewbug.com'))
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