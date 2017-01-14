/* Memos:
 * - Refer to The Drawer folder for superseded Flickr implementations, as well as many more comments regarding why some methods were used (or not used) to
 *   rip the page data.
 *
 * - The current version uses v3, which mostly gathers data directly from pre-written JavaScript variables.
 *
 * - If I decided to opt out of using a user ID entirely, it would still be possible for everything to work but it might be living a little dangerously.
 *   An older but still supported Flickr URL format uses only the photo ID: http://www.flickr.com/photo.gne?id=<photo id> , however this format isn't
 *   officially referenced anywhere at least not recently, and support for it may be dropped. In which case it would become impossible to direct users
 *   to a photo's page with only the photo ID.
 *
 * - For now I'm allowing public and moderate content to be recognised by Feedbactory.
 *
 * - Photo URL information: http://www.flickr.com/services/api/misc.urls.html
 *
 * - v3 hooks into the new Flickr format's appContext variable to gather information about the currently displayed photo, however this variable
 *   was often not initialised at the time of the page load completion event. Using the newer method of delayed checks using JavaScript's
 *   setTimeout, however, this is no longer a problem.
 *
 * - (v3) Once fully loaded, the user ID is available from the photo owner's photostream section (class="photostream-context-view", nested class="context-view").
 *   However after much testing this section is often too slow/late to load, meaning it's unavailable at the time that the code injection has been triggered
 *   by the page load completion event.
 *
 * - For the new Flickr page layout (v3), these two GreaseMonkey scripts find the photo ID by iterating over the ROUTES collection and applying
 *   the regexp to the window.location.pathname until there's a match, eg. ROUTES[i].regexp.exec(window.location.pathname):
 *
 *   - https://userscripts.org/scripts/review/168642
 *
 *   - https://userscripts.org/scripts/review/111902
 *
 *   I'm not convinced of the stability of the method however, especially since they are grabbing the photo ID from the 5th array element of the
 *   return value, whereas now the photo ID is being stored in the 3rd array element. I think that would have broken those scripts.
 *   Could Flickr have changed this to thwart the page grabbers?
 *
 * - Tags/keywords are a bit problematic in the lightbox view. They are not added to the appContext.modelRegistries['photo-tags-models'] collection
 *   when the user navigates left/right from the lightbox view. They do however become available once the user has toggled between the views, and they
 *   are also always available in the regular photo view. I had planned to then instead grab the photo tags from the meta name="keywords" header tag (*)
 *   since they seem to always be updated on major browsers as the user navigates from either view, however many of these tags are unusable because they
 *   have removed spacing and hyphens from keywords eg. travel photography -> travelphotography. So I'm going with the slightly less functional but safer
 *   option of mainly supporting the regular view and only recognising the lightbox view after the photo has already been seen in regular view. I would
 *   really like a solid workaround for this.
 *   - (*) The appContent.modelRegistries['photo-head-meta-models'] collection contains the same unsuitable tags populated in the meta tags, so it can
 *     also be ruled out as an option.
 */


function v3IsAppContextVariableAvailable()
{
   return objectExists(window, 'appContext');
}


function v3GetViewParentElement(parentClassName)
{
   var regularViewParentElement = getFirstElementByClassNames(parentClassName);
   if ((regularViewParentElement !== null) && (regularViewParentElement.tagName === 'DIV'))
      return regularViewParentElement;

   return null;
}


function v3PhotoCheck(photoID)
{
   // The raw JavaScript mediaType object may be 'undefined', but getValue() converts this to a meaningful value.
   return (appContext.modelRegistries['photo-models'].getValue(photoID, 'mediaType') === 'photo');
}


function v3PrivacyCheck(photoID)
{
   /* The isPublic property for a photo is set to undefined until shortly after the user has browsed the photo.
    * I originally thought that maybe this was instead because the property just didn't exist for older photos,
    * but that's not the case. An initially 'undefined' isPublic can be later initialised to 'false', so
    * this function needs to err on the side of caution and return false if the property type is anything but a boolean.
    */
   var isPublic = appContext.modelRegistries['photo-privacy-models'].getValue(photoID, 'isPublic');
   if ((typeof isPublic) === 'boolean')
      return isPublic;
   else
      return false;
}


function v3ExplicitContentCheck(photoID)
{
   var safetyLevel = appContext.modelRegistries['photo-models'].getValue(photoID, 'safetyLevel');
   // Allow public (0) or moderate (1) photos, not private (2).
   return ((safetyLevel === 0) || (safetyLevel === 1));
}


function v3GetActivePhotoData(viewParentElement, photoIDClassNames)
{
   var itemData = [null, null, null, null, null];

   itemData[0] = stringify(v3GetPhotoID(viewParentElement, photoIDClassNames));

   if (itemData[0] !== null)
   {
      var photoID = itemData[0];
      var photoModels = appContext.modelRegistries['photo-models'];

      if (v3PhotoCheck(photoID) && v3PrivacyCheck(photoID) && v3ExplicitContentCheck(photoID))
      {
         itemData[1] = photoModels.getValue(photoID, 'owner').id;
         itemData[2] = v3GetPhotoDisplayName(photoModels, photoID, itemData[1]);

         var rawThumbnailURL = v3GetThumbnailURL(photoID);
         if (rawThumbnailURL !== null)
         {
            itemData[3] = v3GetThumbnailURLElements(rawThumbnailURL);
            itemData[4] = v3GetPhotoTags(photoID);
         }
      }
   }

   return itemData;
}


function v3GetPhotoID(viewParentElement, photoIDClassNames)
{
   var mainPhotoElement = getFirstElementByClassNames(photoIDClassNames, viewParentElement);
   if ((mainPhotoElement !== null) && (mainPhotoElement.tagName === 'IMG'))
   {
      var mainPhotoURL = mainPhotoElement.src;
      var stringStartIndex = mainPhotoURL.lastIndexOf('/');
      if (stringStartIndex !== -1)
      {
         stringStartIndex ++;
         var stringEndIndex = mainPhotoURL.indexOf('_', stringStartIndex);
         if (stringEndIndex !== -1)
            return mainPhotoURL.substring(stringStartIndex, stringEndIndex);
      }
   }

   return null;
}


function v3GetPhotoDisplayName(photoModels, photoID, ownerID)
{
   /* If the title is null on the raw JavaScript object, getValue() will hopefully automatically convert it to an empty string,
    * but check for null just in case. If it is null or even undefined (as is possible with the privacy check), a null
    * display name should be returned to indicate that something went awry.
    */
   var title = photoModels.getValue(photoID, 'title');
   if ((typeof title) === 'string')
   {
      title = title.trim();

      /* Grabbing the realname first, then opting for the username as a fallback ensures that the data returned by this function
       * matches that returned by the old Flickr page parser. If both are defined, the old Flickr page will display both (Feedbactory
       * opts for the 'photo-name-line-1' element, which corresponds to the realname if it's defined, otherwise the username).
       * The new Flickr page will only display one name but seems to enforce the same logic as the old page's 'photo-name-line-1' element,
       * prioritising the realname and showing username if that's blank.
       */
      var userDisplayName = appContext.modelRegistries['person-models'].getValue(ownerID, 'realname');
      if ((typeof userDisplayName === 'undefined') || (userDisplayName === null) || (userDisplayName === ''))
         userDisplayName = appContext.modelRegistries['person-models'].getValue(ownerID, 'username');

      if ((typeof userDisplayName) === 'string')
      {
         userDisplayName = userDisplayName.trim();
         return [title, userDisplayName];
      }
   }

   return null;
}


function v3GetThumbnailURL(photoID)
{
   /* I'm grabbing the thumbnail URL from the meta tag rather than the photoModels.getValue(photoID, 'sizes').q.url
    * attribute to hopefully avoid the possibility of encountering an Akamai (cached?) link injected into the latter.
    * I think the meta tag will tend to have the more canonical links. See the top comments for details.
    * To ensure that the thumbnail URL looks legit (ie. not a URL to a different photo), a simple sanity check is
    * performed on the URL using the photo ID acquired from elsewhere.
    */
   var metaElements = document.head.getElementsByTagName('meta');
   for (var elementIndex = 0; elementIndex < metaElements.length; elementIndex ++)
   {
      var element = metaElements[elementIndex];
      if (element.getAttribute('property') === 'og:image')
      {
         // Run a sanity check on the thumbnail URL - it should contain the matching photo ID that was grabbed from elsewhere.
         var checkPhotoIDIndex = element.content.lastIndexOf('/');
         if ((checkPhotoIDIndex !== -1) && (checkPhotoIDIndex === element.content.lastIndexOf('/' + photoID, checkPhotoIDIndex)))
            return element.content;
      }
   }

   return null;
}


function v3GetThumbnailURLElements(thumbnailURL)
{
   /* Fetch the farmID, serverID, and secretID from the thumbnail URL.
    * Place them into a formatted string, separated by a null character.
    * These IDs can later be combined to reconstruct the thumbnail URL.
    *
    * The typical format is: http://farm<farmID>.staticflickr.com/<serverID>/<photoID>_<secretID>_q.jpg,
    * but it may also be truncated to just //farm<etc>.
    */
   var stringStartIndex = thumbnailURL.indexOf('//farm');
   var stringEndIndex;
   var farmID;

   if (stringStartIndex !== -1)
   {
      stringStartIndex += 6;
      stringEndIndex = thumbnailURL.indexOf('.staticflickr.com/', stringStartIndex);
      if (stringEndIndex === -1)
         return null;

      farmID = thumbnailURL.substring(stringStartIndex, stringEndIndex);
      stringStartIndex = stringEndIndex + 18;
   }
   else
   {
      /* Cater for alternate URLs of the form https://cxxx.staticflickr.com/<farm ID>/<server ID>/<the rest as usual>.
       * These are sometimes used in the older page format - confirmed that for one photo this thumbnail URL form was used
       * when loading in IE 9 mode (old page format), whereas the same photo page in IE10 (new page format) used the
       * standard farmxx.staticflickr.com thumbnail format.
       * The use of https seems to have no bearing on the use of the alternate format.
       *
       * Also there's an initial 'sanity' check here to see that the URL actually looks something like a thumbnail URL, before proceeding.
       */
      stringStartIndex = thumbnailURL.indexOf('.staticflickr.com/');
      if (stringStartIndex === -1)
         return null;

      stringStartIndex = thumbnailURL.indexOf('/', stringStartIndex);
      stringStartIndex ++;
      stringEndIndex = thumbnailURL.indexOf('/', stringStartIndex);
      if (stringStartIndex >= stringEndIndex)
         return null;

      farmID = thumbnailURL.substring(stringStartIndex, stringEndIndex);

      stringStartIndex = stringEndIndex + 1;
   }

   stringEndIndex = thumbnailURL.indexOf('/', stringStartIndex);

   if (stringEndIndex !== -1)
   {
      var serverID = thumbnailURL.substring(stringStartIndex, stringEndIndex);

      stringStartIndex = thumbnailURL.indexOf('_', stringEndIndex);
      if (stringStartIndex !== -1)
      {
         stringStartIndex ++;
         stringEndIndex = thumbnailURL.indexOf('_', stringStartIndex);

         if (stringEndIndex !== -1)
            return [farmID, serverID, thumbnailURL.substring(stringStartIndex, stringEndIndex)];
      }
   }

   return null;
}


function v3GetPhotoTags(photoID)
{
   var photoTagModels;

   /* The photo-tags-models collection is often partially initialised for some time until after the
    * first Flickr image has loaded. Returning a null for the photo tags will bail out of
    * the process for this attempt, rather than returning [] which would indicate no photo tags.
    */
   if (objectExists(appContext, 'modelRegistries', 'photo-tags-models'))
   {
      photoTagModels = appContext.modelRegistries['photo-tags-models'].getValue(photoID, 'tags');
      if ((typeof photoTagModels) === 'object')
         photoTagModels = photoTagModels.getList();
      else
         return null;
   }
   else
      return null;

   var photoTags = [];

   var tagModels = appContext.modelRegistries['tag-models'];
   var photoTagID;
   var photoTag;

   for (var tagIndex = 0; tagIndex < photoTagModels.length; tagIndex ++)
   {
      photoTagID = photoTagModels[tagIndex].id;

      /* Don't lowercase here, let Java do it since it must override the Locale used to produce the correct result.
       * There's also no need to trim the string since a Java process will parse within each photoTag looking
       * for keywords.
       * Also I'm taking the tagRaw rather than the tagValue, since tagValue has been lowercased (and may have been done
       * so for the current locale which again may differ to the server), also spaces and hyphens between terms have been
       * stripped out of tagValue.
       */
      photoTag = tagModels.getValue(photoTagID, 'tagRaw');
      if (photoTag.length > 0)
         photoTags[photoTags.length] = photoTag;
   }

   return photoTags;
}


/****************************************************************************
 *
 ***************************************************************************/


function getActivePhotoData()
{
   var itemData = null;
   if (v3IsAppContextVariableAvailable())
   {
      var viewParentElement = v3GetViewParentElement(['photo-well-scrappy-view']);
      if (viewParentElement !== null)
         itemData = v3GetActivePhotoData(viewParentElement, ['photo-well-media-scrappy-view', 'low-res-photo']);
      else
      {
         viewParentElement = v3GetViewParentElement(['photo-page-lightbox-scrappy-view']);
         if (viewParentElement !== null)
            itemData = v3GetActivePhotoData(viewParentElement, ['photo-well-media-view', 'low-res-photo']);
      }
   }

   if ((itemData !== null) && (itemData[0] !== null) && (itemData[1] !== null) && (itemData[2] !== null) && (itemData[3] !== null) && (itemData[4] !== null))
      return itemData;

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
      if (hostCheck('flickr.com'))
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