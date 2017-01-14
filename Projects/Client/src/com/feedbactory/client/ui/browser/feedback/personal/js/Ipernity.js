/* Memos:
 * Example of a photo title containing a single quotation mark, which would appear encoded if not for using the decodeHTMLText function:
 *   http://www.ipernity.com/doc/photosfogline/19821595
 *
 * Example of an untitled photo: http://www.ipernity.com/doc/ramcol57/34381255
 *
 * - Keywords not supported, since they appear to be unavailable when the lightbox is visible.
 *
 * - I uploaded a png, the main photo and thumbnail is converted to jpg, so it looks like the '.jpg' extension can always be assumed.
 */


function getActivePhotoData()
{
   /* 0: photo ID
    * 1: photographer ID
    * 2: display name
    * 3: thumbnail elements
    */
   var activePhotoData = [null, null, null, null];

   if (isLightboxShowing())
      activePhotoData = getLightboxPhotoData();
   else if (isRegularPhotoViewShowing())
      activePhotoData = getRegularViewPhotoData();

   if ((activePhotoData[0] !== null) && (activePhotoData[1] !== null) && (activePhotoData[2] !== null) && (activePhotoData[3] !== null))
      return activePhotoData;
   else
      return null;
}


function isRegularPhotoViewShowing()
{
   return objectExists(window, 'doc_id');
}


function isLightboxShowing()
{
   return (objectExists(window, 'lightbox-instance') && window['lightbox-instance'].is_shown);
}


function isPhoto(documentObject)
{
   return (documentObject.type === '1');
}


function isPhotoPublic(documentObject)
{
   // Bitset permissions (albeit as a string!), 31 is all bits set indicating that the photo may be shared with everyone.
   return (documentObject.share === '31');
}


function decodeHTMLText(text)
{
   // Ugh, what an ugly hack. Google search indicates that without jQuery it's the method recommended by the majority of developers.
   var tempDivElement = document.createElement('div');
   tempDivElement.innerHTML = text;
   return textContent(tempDivElement);
}


/****************************************************************************
 *
 ***************************************************************************/


function getLightboxPhotoData()
{
   var activePhotoData = [null, null, null, null];

   var lightboxPhotoObject = getLightboxPhotoObject();
   if ((lightboxPhotoObject !== null) && isPhoto(lightboxPhotoObject) && isPhotoPublic(lightboxPhotoObject))
   {
      activePhotoData[0] = getLightboxPhotoID(lightboxPhotoObject);
      if (activePhotoData[0] !== null)
      {
         activePhotoData[1] = getLightboxPhotographerID(lightboxPhotoObject);
         if (activePhotoData[1] !== null)
         {
            activePhotoData[2] = getLightboxPhotoDisplayName(lightboxPhotoObject, activePhotoData[1]);
            if (activePhotoData[2] !== null)
               activePhotoData[3] = getLightboxThumbnailURLElements(lightboxPhotoObject);
         }
      }
   }

   return activePhotoData;
}


function getLightboxPhotoObject()
{
   if (objectExists(window['lightbox-instance'], 'current_doc'))
      return window['lightbox-instance'].current_doc;

   return null;
}


function getLightboxPhotoID(lightboxPhotoObject)
{
   return stringify(lightboxPhotoObject.doc_id).trim();
}


function getLightboxPhotographerID(lightboxPhotoObject)
{
   return stringify(lightboxPhotoObject.user_id).trim();
}


function getLightboxPhotoDisplayName(lightboxPhotoObject, photographerID)
{
   // Decoding is required to avoid HTML escaped characters which may be embedded in ipernity strings, eg. &amp; for &, &#039; for ', etc.
   var title = decodeHTMLText(lightboxPhotoObject.title.trim());

   if (objectExists(window['lightbox-instance'], 'users', photographerID))
   {
      var photographerName = decodeHTMLText(window['lightbox-instance'].users[photographerID].title.trim());
      return [title, photographerName];
   }

   return null;
}


function getLightboxThumbnailURLElements(lightboxPhotoObject)
{
   if (objectExists(lightboxPhotoObject, 'thumbs', '240'))
   {
      var thumbnailObject = lightboxPhotoObject.thumbs['240'];

      var pathID = stringify(thumbnailObject.path).trim();
      if ((pathID.charAt(0) === '/') && (pathID.charAt(pathID.length - 1) === '/'))
      {
         pathID = pathID.substring(1, pathID.length - 1);
         var secretID = stringify(thumbnailObject.secret).trim();

         // See getThumbnailURLElements() for the note regarding the alternate thumbnail formats.
         if (thumbnailObject.url.indexOf('//cdn.ipernity.com/') !== -1)
            return ['', pathID, secretID];
         else
         {
            var farmID = stringify(thumbnailObject.farm).trim();
            return [farmID, pathID, secretID];
         }
      }
   }

   return null;
}


/****************************************************************************
 *
 ***************************************************************************/


function getRegularViewPhotoData()
{
   var activePhotoData = [null, null, null, null];

   activePhotoData[0] = getPhotoID();
   if (activePhotoData[0] !== null)
   {
      var photoObject = getDocumentObject(activePhotoData[0]);
      if ((photoObject !== null) && isPhoto(photoObject) && isPhotoPublic(photoObject))
      {
         activePhotoData[1] = getPhotographerID(photoObject);
         if (activePhotoData[1] !== null)
         {
            activePhotoData[2] = getPhotoDisplayName(photoObject, activePhotoData[1]);
            if (activePhotoData[2] !== null)
            {
               var photoURL = getPhotoURL();
               if (photoURL !== null)
                  activePhotoData[3] = getThumbnailURLElements(photoURL, activePhotoData[0]);
            }
         }
      }
   }

   return activePhotoData;
}


function getPhotoID()
{
   return stringify(window.doc_id).trim();
}


function getDocumentObject(photoID)
{
   if (objectExists(window, 'Data', 'doc', photoID))
      return window.Data.doc[photoID];

   return null;
}


function getPhotographerID(documentObject)
{
   return stringify(documentObject.user_id).trim();
}


function getPhotoDisplayName(documentObject, photographerID)
{
   var title = decodeHTMLText(documentObject.title.trim());

   if (objectExists(window.Data, 'user', photographerID))
   {
      var photographerName = decodeHTMLText(window.Data.user[photographerID].title.trim());
      return [title, photographerName];
   }

   return null;
}


function getPhotoURL()
{
   var photoElement = document.getElementById('doc_img');
   if ((photoElement !== null) && (photoElement.tagName === 'IMG'))
   {
      // The URL elements attached to the img src attribute can be different (and rejected for thumbnail size) if it is being displayed at a higher res, eg. 1024.
      return photoElement.getAttribute('data-lowres').trim();
   }

   return null;
}


function getThumbnailURLElements(rawPhotoURL, photoID)
{
   /* Fetch the farmID, pathID, and secretID from the photo URL.
    * Place them into a formatted string, separated by a null character.
    * These IDs can later be combined to reconstruct the thumbnail URL.
    *
    * Since late 2014 Ipernity have (mostly?) switched to a CDN and changed their thumbnail URL format from:
    *   http://u<farmID>.ipernity.com/<pathID>/<photoID>.<secretID>.<resolution>.jpg?<url arguments>
    * to:
    *   http://cdn.ipernity.com/<pathID>/<photoID>.<secretID>.<resolution>.jpg?<url arguments>
    *   
    * Since some photos still appear to use the older format exclusively (applying the newer CDN format to them will produce a dead image link),
    * so both most must be supported for now. In practical terms this means the urlElements string has either two or three elements,
    * where the older format also includes the farm ID.
    */

   var photoURL = trimURLArgument(rawPhotoURL);
   if (photoURL.endsWith('.jpg'))
   {
      var stringStartIndex = photoURL.indexOf('//cdn.ipernity.com/');
      if (stringStartIndex !== -1)
         return getCDNFormatThumbnailURLElements(photoURL, photoID, stringStartIndex + 19);
      else
      {
         stringStartIndex = photoURL.indexOf('//u');
         if (stringStartIndex !== -1)
            return getFarmIDFormatThumbnailURLElements(photoURL, photoID, stringStartIndex + 3);
      }
   }

   return null;
}


function getCDNFormatThumbnailURLElements(photoURL, photoID, pathIDStartIndex)
{
   var stringEndIndex = photoURL.indexOf('/' + photoID + '.', pathIDStartIndex);

   if (stringEndIndex !== -1)
   {
      var pathID = photoURL.substring(pathIDStartIndex, stringEndIndex);
      var stringStartIndex = stringEndIndex + photoID.length + 2;
      stringEndIndex = photoURL.indexOf('.', stringStartIndex);
      if (stringEndIndex !== -1)
      {
         var secretID = photoURL.substring(stringStartIndex, stringEndIndex);
         return ['', pathID, secretID];
      }
   }

   return null;
}


function getFarmIDFormatThumbnailURLElements(photoURL, photoID, farmIDStartIndex)
{
   var stringEndIndex = photoURL.indexOf('.ipernity.com/', farmIDStartIndex);

   if (stringEndIndex !== -1)
   {
      var farmID = photoURL.substring(farmIDStartIndex, stringEndIndex);

      var stringStartIndex = stringEndIndex + 14;
      stringEndIndex = photoURL.indexOf('/' + photoID + '.', stringStartIndex);

      if (stringEndIndex !== -1)
      {
         var pathID = photoURL.substring(stringStartIndex, stringEndIndex);
         stringStartIndex = stringEndIndex + photoID.length + 2;
         stringEndIndex = photoURL.indexOf('.', stringStartIndex);
         if (stringEndIndex !== -1)
         {
            var secretID = photoURL.substring(stringStartIndex, stringEndIndex);
            return [farmID, pathID, secretID];
         }
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
      if (hostCheck('ipernity.com'))
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