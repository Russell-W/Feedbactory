

function getActivePhotoData()
{
   /* 0: photo ID
    * 1: photographer ID
    * 2: display name
    */
   var activePhotoData = [null, null, null];

   activePhotoData[0] = getPhotoID();
   if (activePhotoData[0] !== null)
   {
      var headerElement = document.getElementsByTagName('head')[0];
      var metaTags = headerElement.getElementsByTagName('meta');

      if (checkMetaTagPhotoID(metaTags, activePhotoData[0]))
      {
         activePhotoData[1] = getPhotographerID();
         if (activePhotoData[1] !== null)
            activePhotoData[2] = getPhotoDisplayName();
      }
   }

   if ((activePhotoData[0] !== null) && (activePhotoData[1] !== null) && (activePhotoData[2] !== null))
      return activePhotoData;
   else
      return null;
}


function getPhotoID()
{
   var pathname = window.location.pathname;

   if (pathname.indexOf('/photo/') === 0)
   {
      var photoIDStartIndex = 7;
      var photoIDEndIndex = pathname.indexOf('/', photoIDStartIndex);
      if (photoIDEndIndex === -1)
         return pathname.substring(photoIDStartIndex);
      else
         return pathname.substring(photoIDStartIndex, photoIDEndIndex);
   }

   return null;
}


function checkMetaTagPhotoID(metaTags, photoID)
{
   var metaTagElement;

   for (var tagIndex = 0; tagIndex < metaTags.length; tagIndex ++)
   {
      metaTagElement = metaTags[tagIndex];
      if ((metaTagElement.getAttribute('property') === 'og:url') &&
          (metaTagElement.content.endsWith('/' + photoID) || (metaTagElement.content.indexOf('/' + photoID + '/') !== -1)))
         return true;
   }

   return false;
}


function getPhotographerID()
{
   var nameLinkElement = getFirstElementByClassNames(['namelink']);
   if ((nameLinkElement !== null) && (nameLinkElement.tagName === 'A'))
   {
      var photographerIDStartIndex = nameLinkElement.href.lastIndexOf('gallery/');
      if (photographerIDStartIndex !== -1)
      {
         photographerIDStartIndex += 8;
         var photographerID = nameLinkElement.href.substring(photographerIDStartIndex);
         if (photographerID.endsWith('/'))
            photographerID = photographerID.substring(0, photographerID.length - 1);

         return photographerID;
      }
   }

   return null;
}


function getPhotoDisplayName()
{
   var photoTitleElement = getFirstElementByClassNames(['phototitle']);
   if ((photoTitleElement !== null) && (photoTitleElement.tagName === 'TD'))
   {
      var rawDisplayName = textContent(photoTitleElement);

      var photographerNameElement = getFirstElementByClassNames(['medtxt'], photoTitleElement);
      if ((photographerNameElement !== null) && (photographerNameElement.tagName === 'SPAN'))
      {
         var photographerName = textContent(photographerNameElement);
         var photographerNameStartIndex = rawDisplayName.indexOf(photographerName);
         if (photographerNameStartIndex !== -1)
         {
            var photoTitle = rawDisplayName.substring(0, photographerNameStartIndex).trim();
            if (photoTitle.indexOf('"') === 0)
               photoTitle = photoTitle.substring(1);

            if (photoTitle.endsWith('"'))
               photoTitle = photoTitle.substring(0, photoTitle.length - 1);

            // Confirmed some cases where the title within the quotes has whitespace at the end - the server will consider these erroneous if not trimmed.
            photoTitle = photoTitle.trim();

            // Special case for 72dpi, where untitled photos have an explicit value of 'Untitled'.
            if (photoTitle === 'Untitled')
               photoTitle = '';

            var copyrightIndex = photographerName.indexOf('© ');
            if (copyrightIndex !== -1)
            {
               photographerName = photographerName.substring(copyrightIndex + 2).trim();
               return [photoTitle, photographerName];
            }
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
      if (hostCheck('72dpi.com'))
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