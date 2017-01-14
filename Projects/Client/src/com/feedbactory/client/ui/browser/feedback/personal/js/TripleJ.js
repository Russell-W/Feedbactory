

function getActiveItemData()
{
   var itemData = [null, null, null];

   itemData[0] = getItemID();
   if (itemData[0] !== null)
   {
      itemData[1] = getDisplayName();
      if (itemData[1] !== null)
      {
         /* Thumbnail photo URL is optional, none is supplied for quite a few of the presenters.
          * For those, the JJJ primary profile photo (using the user ID from the page URL) can be used as a fallback.
          */
         itemData[2] = getPhotoURL();

         return itemData;
      }
   }

   return null;
}


function getItemID()
{
   if (window.location.pathname.endsWith('.htm'))
   {
      var slashLastPosition = window.location.pathname.lastIndexOf('/');
      if (slashLastPosition !== -1)
         return window.location.pathname.substring(slashLastPosition + 1, window.location.pathname.length - 4);
   }

   return null;
}


function getDisplayName()
{
   var midholdElement = document.getElementById('midhold');
   if ((midholdElement !== null) && (midholdElement.tagName === 'DIV'))
   {
      var pictureElement = getFirstElementByClassNames(['picture'], midholdElement);
      if ((pictureElement !== null) && (pictureElement.tagName === 'DIV'))
      {
         var childNodes = pictureElement.childNodes;

         if ((childNodes.length === 1) && (childNodes[0].nodeType === 1) && (childNodes[0].tagName === 'IMG'))
            return childNodes[0].alt.trim();
      }
   }

   return null;
}


function getPhotoURL()
{
   var photoSidebarElement = document.getElementById('people-col-left-b');
   if ((photoSidebarElement !== null) && (photoSidebarElement.tagName === 'DIV'))
   {
      var thumbsElement = getFirstElementByClassNames(['thumbs'], photoSidebarElement);
      if ((thumbsElement !== null) && (thumbsElement.tagName === 'DIV'))
      {
         var imageElements = thumbsElement.getElementsByTagName('img');
         if (imageElements.length > 0)
         {
            var thumbnailURL = imageElements[0].src.trim();
            if (thumbnailURL.endsWith('.jpg'))
            {
               var imagePrefixIndex = thumbnailURL.indexOf('img/extras/');
               if (imagePrefixIndex !== -1)
               {
                  imagePrefixIndex += 11;
                  /* Possible that the extracted URL actually contains subfolders between the 'img/extras/' and the final '.jpg',
                   * I guess this should be fine as long as the URL generator tacks everything onto the end as-is.
                   */
                  return thumbnailURL.substring(imagePrefixIndex, thumbnailURL.length - 4);
               }
            }
         }
      }
   }

   return null;
}


function main()
{
   var result = [true, null];

   try
   {
      if (hostCheck('abc.net.au') && (window.location.pathname.indexOf('/triplej/people/') === 0))
      {
         if (document.readyState === 'complete')
            result[1] = getActiveItemData();
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