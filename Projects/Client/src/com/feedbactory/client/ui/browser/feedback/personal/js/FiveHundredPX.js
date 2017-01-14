/* Memos:
 * - 500px uses jQuery so there's no unsupported browser feature issues, eg. $('.photo_header') can replace
 *   element.getElementsByClassName('photo_header'), which is not supported in earlier versions of IE (<= 8).
 *   Look up CSS selectors for more information about the search syntax.
 *
 * - 500px changed the format of its website around October 2015, and consequently the active photo data is located under a different
 *   JavaScript element than previous:
 *
 *   - Formerly PxApp.router.view.model
 *   - Now:
 *     - Lightbox view: App.content.currentView.photoGrid.collection
 *     - Regular and zoomed view: App.content.currentView.navigationContext
 *     - Zoomed view is not available on older browsers such as IE 10, but on newer browsers it can be shown by clicking on the regular view image.
 */

function getBrowserURLPhotoID()
{
   if (window.location.pathname.indexOf('/photo/') !== -1)
   {
      var photoIDStartIndex = 7;
      var photoIDEndIndex = window.location.pathname.indexOf('/', photoIDStartIndex);
      if (photoIDEndIndex !== -1)
         return window.location.pathname.substring(photoIDStartIndex, photoIDEndIndex);
   }

   return null;
}


function getActivePhotoObject()
{
   var photoModelsRoot = null;
   if (document.getElementById('pxLightbox-1') !== null)
   {
      if (objectExists(window, 'App', 'content', 'currentView', 'body', 'collection'))
      {
         // Lightbox view of a gallery featuring photos from different users, eg. the popular gallery.
         photoModelsRoot = window.App.content.currentView.body.collection;
      }
      else if (objectExists(window, 'App', 'controller', 'layout', 'bodyRegion', 'currentView', 'collection'))
      {
         // Lightbox view of a gallery for a single user.
         photoModelsRoot = window.App.controller.layout.bodyRegion.currentView.collection;
      }
   }
   else if (objectExists(window, 'App', 'content', 'currentView', 'navigationContext'))
   {
      // Regular view (eg. opened in separate tab) of photos.
      photoModelsRoot = window.App.content.currentView.navigationContext;
   }

   if (photoModelsRoot !== null)
   {
      var activePhotoModelIndex = photoModelsRoot.selectedPhotoOffset();
      if (activePhotoModelIndex !== -1)
         return photoModelsRoot.photoAt(activePhotoModelIndex);
   }

   return null;
}


function getPhotoDataFromObject(photoObject)
{
   /* 0: itemID
    * 1: userID
    * 2: title
    * 3: photoURLIDs
    * 4: tags
    *
    * Previously used more reader friendly declaration, ie:
    *
    * var itemData = {itemID:null, title:null, photoURLIDs:null, userID:null, tags:null};
    *
    * However that type of array cannot be passed back to Java for debugging purposes.
    */
   var itemData = [null, null, null, null, null];

   itemData[0] = stringify(photoObject.id);
   if (itemData[0] !== null)
   {
      itemData[1] = stringify(photoObject.get('user_id'));
      itemData[2] = getPhotoDisplayName(photoObject);
      itemData[3] = getPhotoURLElements(photoObject, itemData[0]);
      itemData[4] = photoObject.get('tags');
   }

   return itemData;
}


function getPhotoDisplayName(photoObject)
{
   var photoTitle = photoObject.get('name');
   if (photoTitle !== null)
      photoTitle = photoTitle.trim();
   else
      photoTitle = '';

   var photographerName = photoObject.getUser().get('fullname');
   if (photographerName !== null)
   {
      photographerName = photographerName.trim();
      return [photoTitle, photographerName];
   }

   return null;
}


function getPhotoURLElements(photoObject, photoID)
{
   /* Currently the 500px image handling is unique in that Feedbactory is having to grab two different image 'keys' which are needed
    * as part of the URL to load the images; one key is for a small thumbnail, and one for a larger size version.
    * 
    * The photo's thumbnail image (target size ID is 2) has a size of 140 x 140.
    * (Previously Feedbactory was using target size ID 20 (height 300, proportional width), however as of April 2016 this format no longer seems to be available.
    * Unfortunately this means breakage for existing photos in the Feedbactory database.)
    * The longer dimension of the image's larger resolution (target size ID is 4) is 900, if the user has uploaded an image of at least that size.
    */
   var thumbnailPhotoURL = photoObject.getImagePath({size: 2});
   if (thumbnailPhotoURL !== null)
   {
      var thumbnailURLKey = getImageKey(thumbnailPhotoURL, photoID);
      if (thumbnailURLKey !== null)
      {
         var largeImageURL = photoObject.getImagePath({size: 4});
         if (largeImageURL !== null)
         {
            var largeImageURLKey = getImageKey(largeImageURL, photoID);
            if (largeImageURLKey !== null)
               return [thumbnailURLKey, largeImageURLKey];
         }
      }
   }

   return null;
}


function getImageKey(fullPhotoURL, photoID)
{
   // Currently the thumbnails often have a url argument that needs to be trimmed.
   fullPhotoURL = trimURLArgument(fullPhotoURL);
   if (fullPhotoURL.endsWith('/'))
      fullPhotoURL = fullPhotoURL.substring(0, fullPhotoURL.length - 1);

   var photoIDStartIndex = fullPhotoURL.indexOf('/' + photoID + '/');
   if (photoIDStartIndex !== -1)
   {
      var photoDimensionsStartIndex = (photoIDStartIndex + photoID.length + 2);
      var photoURLIDStartIndex = fullPhotoURL.indexOf('/', photoDimensionsStartIndex);
      if (photoURLIDStartIndex !== -1)
      {
         photoURLIDStartIndex ++;
         if (photoURLIDStartIndex < fullPhotoURL.length)
            return fullPhotoURL.substring(photoURLIDStartIndex);
      }
   }

   return null;
}


/****************************************************************************
 *
 ***************************************************************************/


function getActivePhotoData()
{
   // App.content.currentView.photoGrid.collection.models[0].photo.getImagePath({size: 20});
   var browserURLPhotoID = getBrowserURLPhotoID();
   if (browserURLPhotoID !== null)
   {
      var activePhotoObject = getActivePhotoObject();
      if ((activePhotoObject !== null) && (stringify(activePhotoObject.id) === browserURLPhotoID) && activePhotoObject.isPublic() && (! activePhotoObject.get('nsfw')))
      {
         var activePhotoData = getPhotoDataFromObject(activePhotoObject);
         if ((activePhotoData[0] !== null) && (activePhotoData[1] !== null) && (activePhotoData[2] !== null) &&
             (activePhotoData[3] !== null) && (activePhotoData[4] !== null))
            return activePhotoData;
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
      if (hostCheck('500px.com'))
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