/* Memos:
 * - The og:title does not always exactly match the title displayed within the photo's web page.
 *   I came across a case of a photo having an extra space -within- the photo title (ie. not side padding): http://www.1x.com/photo/656736
 *   So, the title displayed within the photo (what the user actually sees) is more reliable. Also for this reason the photo title cannot
 *   be 'sanity checked' against the og:title meta tag. I was previously doing this to also grab the photographer's name, since it appears
 *   within the same meta tag.
 *
 * - 1x.com thumbnail photos are not shown in JRE 6 due to an SSL bug related to the Diffie Hellman key exchange handling.
 */


function getActivePhotoData()
{
   /* 0: photo ID
    * 1: photographer ID
    * 2: display name
    * 3: photo thumbnail ID
    */
   var activePhotoData = [null, null, null, null];

   activePhotoData[0] = getPhotoID();
   if (activePhotoData[0] !== null)
   {
      activePhotoData[1] = getPhotographerID(activePhotoData[0]);
      if (activePhotoData[1] !== null)
      {
         activePhotoData[2] = getPhotoDisplayName();
         if (activePhotoData[2] !== null)
            activePhotoData[3] = getPhotoThumbnailID(activePhotoData[0]);
      }
   }

   if ((activePhotoData[0] !== null) && (activePhotoData[1] !== null) && (activePhotoData[2] !== null) && (activePhotoData[3] !== null))
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


function getPhotographerID(photoID)
{
   var photographerElement = document.getElementById('loadimg_userid_' + photoID);
   if ((photographerElement !== null) && (photographerElement.tagName === 'INPUT'))
      return photographerElement.value;

   return null;
}


function getPhotoDisplayName()
{
   var photoTitleElement = document.getElementById('phototitle');
   if ((photoTitleElement !== null) && (photoTitleElement.tagName === 'SPAN'))
   {
      var photoTitle = textContent(photoTitleElement).trim();

      var slideshowNameElement = document.getElementById('slideshow_name');
      if ((slideshowNameElement !== null) && (slideshowNameElement.tagName === 'DIV'))
      {
         var photographerName = textContent(slideshowNameElement);
         var photographerNameStartIndex = photographerName.indexOf('by ');

         if (photographerNameStartIndex !== -1)
         {
            photographerNameStartIndex += 3;
            photographerName = photographerName.substring(photographerNameStartIndex).trim();
            return [photoTitle, photographerName];
         }
      }
   }

   return null;
}


function getPhotoThumbnailID(photoID)
{
   var photographerElement = document.getElementById('loadimg_src_ld_' + photoID);
   if ((photographerElement !== null) && (photographerElement.tagName === 'INPUT'))
   {
      var lowDefinitionPhotoURL = photographerElement.value;
      var startIndex = lowDefinitionPhotoURL.lastIndexOf('/');
      if (startIndex !== -1)
      {
         startIndex ++;
         var endIndex = lowDefinitionPhotoURL.indexOf('-ld.jpg', startIndex);
         if (endIndex !== -1)
            return lowDefinitionPhotoURL.substring(startIndex, endIndex);
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
      if (hostCheck('1x.com'))
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