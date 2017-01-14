/* Memos:
 * - A photo URL where the start of photo ID indicator is a hash character: http://www.pixoto.com/images-photography/all/all/leading#6318092280922112
 *
 * - http://www.pixoto.com/images-photography/buildings-and-architecture/other-interior/5433013388181504
 */


function getActivePhotoData()
{
   if (isStandalonePhotoPage())
   {
      var rootElement = getStandalonePhotoRootElement();
      if (rootElement !== null)
         return getStandaloneActivePhotoData(rootElement);
   }
   else
   {
      var overlaidPhotoElement = getOverlaidPhotoRootElement();
      if (overlaidPhotoElement !== null)
         return getOverlaidActivePhotoData(overlaidPhotoElement);
   }

   return null;
}


/****************************************************************************
 *
 ***************************************************************************/


function isStandalonePhotoPage()
{
   return hasClassName(document.body, 'image-detail-pg');
}


function getStandalonePhotoRootElement()
{
   var rootElement = document.getElementById('main');
   if ((rootElement !== null) && (rootElement.tagName === 'DIV'))
      return rootElement;

   return null;
}


function getStandaloneActivePhotoData(rootElement)
{
   /* 0: photo ID
    * 1: photographer ID
    * 2: display name
    * 3: thumbnail ID
    * 4: tags
    */
   var activePhotoData = [null, null, null, null, null];

   activePhotoData[0] = getPhotoID();
   if (activePhotoData[0] !== null)
   {
      var containerElement = document.getElementById('container');
      if ((containerElement !== null) && (containerElement.tagName === 'DIV'))
      {
         var photographerInformationElement = getPhotographerInformationElement(containerElement);
         if (photographerInformationElement !== null)
         {
            activePhotoData[1] = getPhotographerID(photographerInformationElement);
            if (activePhotoData[1] !== null)
            {
               activePhotoData[2] = getPhotoDisplayName(photographerInformationElement);
               if (activePhotoData[2] !== null)
               {
                  var photoURL = getStandalonePhotoURL();
                  activePhotoData[3] = getPhotoThumbnailURLIDs(photoURL);
                  if (activePhotoData[3] !== null)
                  {
                     activePhotoData[4] = getPhotoTags(rootElement);
                     if (activePhotoData[4] !== null)
                        return activePhotoData;
                  }
               }
            }
         }
      }
   }

   return null;
}


function getPhotoID()
{
   var pathname = window.location.pathname;
   var lastSlashIndex = pathname.lastIndexOf('/');
   var lastHyphenIndex = pathname.lastIndexOf('-');

   var photoIDStartIndex = Math.max(lastSlashIndex, lastHyphenIndex);
   if (photoIDStartIndex !== -1)
   {
      photoIDStartIndex ++;
      var photoID = pathname.substring(photoIDStartIndex);

      // Sanity check - the photo ID marked by the last hyphen until the end of the URL should be an integer.
      if (isIntegerString(photoID))
         return photoID;
   }

   return null;
}


// parseInt('43548blah') returns 43548, and cannot be relied upon for validation.
function isIntegerString(argument)
{
   var character;

   for (var charIndex = 0; charIndex < argument.length; charIndex ++)
   {
      character = argument.charAt(charIndex);
      if ((character < '0') || (character > '9'))
         return false;
   }

   return true;
}


function getPhotographerInformationElement(rootElement)
{
   var informationRootElement = getFirstElementByClassNames(['image-title-bar', 'image-title-bar-inner', 'img-owner'], rootElement);
   if ((informationRootElement !== null) && (informationRootElement.tagName === 'DIV'))
      return informationRootElement;

   return null;
}


function getPhotographerID(photographerInformationElement)
{
   var ownerLinkElement = getFirstElementByClassNames(['owner-link'], photographerInformationElement);
   if ((ownerLinkElement !== null) && (ownerLinkElement.tagName === 'A'))
   {
      var photographerIDURL = trimURLArgument(ownerLinkElement.href);
      var photographerIDStartIndex = photographerIDURL.lastIndexOf('/');
      if (photographerIDStartIndex !== -1)
      {
         photographerIDStartIndex ++;
         return photographerIDURL.substring(photographerIDStartIndex);
      }
   }

   return null;
}


function getPhotoDisplayName(photographerInformationElement)
{
   var photoTitleElement = getFirstElementByClassNames(['image-title'], photographerInformationElement);
   var title;

   if ((photoTitleElement !== null) && (photoTitleElement.tagName === 'H1'))
      title = textContent(photoTitleElement).trim();
   else
   {
      // In standalone view, untitled photos have no H1 element present.
      title = '';
   }

   var ownerLinkElement = getFirstElementByClassNames(['owner-link'], photographerInformationElement);
   if ((ownerLinkElement !== null) && (ownerLinkElement.tagName === 'A'))
   {
      var photographerName = textContent(ownerLinkElement).trim();
      if (photographerName.length > 0)
         return [title, photographerName];
   }

   return null;
}


function getStandalonePhotoURL()
{
   var photoElement = document.getElementById('theImage');
   if ((photoElement !== null) && (photoElement.tagName === 'IMG'))
   {
      /* The thumbnail parsing does not deal with the very end of the URL string, therefore no whitespace trimming is required.
       * However the parsing does look for a '=' which in this case is not part of a trailing URL parameter (after '?'), so
       * be sure to trim off all URL parameters before further processing.
       */
      return trimURLArgument(photoElement.src);
   }

   return null;
}


function getPhotoThumbnailURLIDs(photoURL)
{
   var stringStartIndex = photoURL.indexOf('://lh');
   if (stringStartIndex !== -1)
   {
      stringStartIndex += 5;

      var serverID = null;

      /* As of mid 2015 it seems this has only recently switched over from ggpht to googleusercontent.
       * Confirmed that older photos will still use ggpht as part of their direct URL.
       */
      var stringEndIndex = photoURL.indexOf('.googleusercontent.com/', stringStartIndex);
      if (stringEndIndex !== -1)
      {
         serverID = photoURL.substring(stringStartIndex, stringEndIndex);
         stringStartIndex = stringEndIndex + 23;
      }
      else
      {
         stringEndIndex = photoURL.indexOf('.ggpht.com/', stringStartIndex);
         if (stringEndIndex !== -1)
         {
            serverID = photoURL.substring(stringStartIndex, stringEndIndex);
            stringStartIndex = stringEndIndex + 11;
         }
      }

      if (serverID !== null)
      {
         stringEndIndex = photoURL.lastIndexOf('=');

         // Perform a simple sanity check that there are no slashes embedded within the URL's ID.
         if ((stringEndIndex !== -1) && (photoURL.indexOf('/', stringStartIndex) === -1))
            return [serverID, photoURL.substring(stringStartIndex, stringEndIndex)];
      }
   }

   return null;
}


function getPhotoTags(rootElement)
{
   var photoTags = null;

   // Element will be present event when there are no tags defined.
   var photoTagsParentElement = getFirstElementByClassNames(['main-content', 'image-attributes', 'tags-list', 'img-tags-list'], rootElement);
   if ((photoTagsParentElement !== null) && (photoTagsParentElement.tagName === 'UL'))
   {
      photoTags = [];

      var photoTagElements = getElementsByClassNames(['tag'], photoTagsParentElement);
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

   return photoTags;
}


/****************************************************************************
 *
 ***************************************************************************/


function getOverlaidPhotoRootElement()
{
   var overlaidPhotoElement = document.getElementById('image-1-details');
   if ((overlaidPhotoElement !== null) && (overlaidPhotoElement.tagName === 'DIV'))
   {
      var overlaidPhotoElementParent = overlaidPhotoElement.parentNode;

      // The key thing here is that the style.display is set to 'block', and not 'none' (in which case it's temporarily hidden and should not be acknowledged).
      if ((overlaidPhotoElementParent !== null) && (overlaidPhotoElementParent.nodeType === 1) && (overlaidPhotoElementParent.tagName === 'DIV') &&
          hasClassName(overlaidPhotoElementParent, 'image-detail-overlay') && (overlaidPhotoElementParent.style.display === 'block'))
         return overlaidPhotoElementParent;
   }

   return null;
}


function getOverlaidActivePhotoData(overlaidPhotoElement)
{
   var activePhotoData = [null, null, null, null, null];

   activePhotoData[0] = getPhotoID();
   if (activePhotoData[0] !== null)
   {
      var photographerInformationElement = getPhotographerInformationElement(overlaidPhotoElement);
      if (photographerInformationElement !== null)
      {
         activePhotoData[1] = getPhotographerID(photographerInformationElement);
         if (activePhotoData[1] !== null)
         {
            activePhotoData[2] = getPhotoDisplayName(photographerInformationElement);
            if (activePhotoData[2] !== null)
            {
               var photoURL = getOverlaidPhotoURL();
               activePhotoData[3] = getPhotoThumbnailURLIDs(photoURL);
               if (activePhotoData[3] !== null)
               {
                  activePhotoData[4] = getPhotoTags(overlaidPhotoElement);
                  if (activePhotoData[4] !== null)
                     return activePhotoData;
               }
            }
         }
      }
   }

   return null;
}


function getOverlaidPhotoURL()
{
   var photoElement = document.getElementById('DETAIL_IMAGE');
   if ((photoElement !== null) && (photoElement.tagName === 'IMG'))
      return trimURLArgument(photoElement.src);

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
      if (hostCheck('pixoto.com'))
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