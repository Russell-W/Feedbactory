
package com.feedbactory.client.ui.browser.feedback.personal.js;

import com.feedbactory.client.ui.browser.feedback.js.SharedJavaScriptString;


abstract public class FlickrJavaScriptString
{
   static final private String library;

   static
   {
      final StringBuilder scriptBuilder = new StringBuilder(SharedJavaScriptString.library.length() + 10000);

      scriptBuilder.append("return (function()");
      scriptBuilder.append("{");

      scriptBuilder.append(SharedJavaScriptString.library);

      /* Start paste area.
       */
      scriptBuilder.append("function v3IsAppContextVariableAvailable()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return objectExists(window, 'appContext');");
      scriptBuilder.append("}");


      scriptBuilder.append("function v3GetViewParentElement(parentClassName)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var regularViewParentElement = getFirstElementByClassNames(parentClassName);");
      scriptBuilder.append("   if ((regularViewParentElement !== null) && (regularViewParentElement.tagName === 'DIV'))");
      scriptBuilder.append("      return regularViewParentElement;");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function v3PhotoCheck(photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   return (appContext.modelRegistries['photo-models'].getValue(photoID, 'mediaType') === 'photo');");
      scriptBuilder.append("}");


      scriptBuilder.append("function v3PrivacyCheck(photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var isPublic = appContext.modelRegistries['photo-privacy-models'].getValue(photoID, 'isPublic');");
      scriptBuilder.append("   if ((typeof isPublic) === 'boolean')");
      scriptBuilder.append("      return isPublic;");
      scriptBuilder.append("   else");
      scriptBuilder.append("      return false;");
      scriptBuilder.append("}");


      scriptBuilder.append("function v3ExplicitContentCheck(photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var safetyLevel = appContext.modelRegistries['photo-models'].getValue(photoID, 'safetyLevel');");
      scriptBuilder.append("   return ((safetyLevel === 0) || (safetyLevel === 1));");
      scriptBuilder.append("}");


      scriptBuilder.append("function v3GetActivePhotoData(viewParentElement, photoIDClassNames)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var itemData = [null, null, null, null, null];");

      scriptBuilder.append("   itemData[0] = stringify(v3GetPhotoID(viewParentElement, photoIDClassNames));");

      scriptBuilder.append("   if (itemData[0] !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photoID = itemData[0];");
      scriptBuilder.append("      var photoModels = appContext.modelRegistries['photo-models'];");

      scriptBuilder.append("      if (v3PhotoCheck(photoID) && v3PrivacyCheck(photoID) && v3ExplicitContentCheck(photoID))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         itemData[1] = photoModels.getValue(photoID, 'owner').id;");
      scriptBuilder.append("         itemData[2] = v3GetPhotoDisplayName(photoModels, photoID, itemData[1]);");

      scriptBuilder.append("         var rawThumbnailURL = v3GetThumbnailURL(photoID);");
      scriptBuilder.append("         if (rawThumbnailURL !== null)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            itemData[3] = v3GetThumbnailURLElements(rawThumbnailURL);");
      scriptBuilder.append("            itemData[4] = v3GetPhotoTags(photoID);");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return itemData;");
      scriptBuilder.append("}");


      scriptBuilder.append("function v3GetPhotoID(viewParentElement, photoIDClassNames)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var mainPhotoElement = getFirstElementByClassNames(photoIDClassNames, viewParentElement);");
      scriptBuilder.append("   if ((mainPhotoElement !== null) && (mainPhotoElement.tagName === 'IMG'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var mainPhotoURL = mainPhotoElement.src;");
      scriptBuilder.append("      var stringStartIndex = mainPhotoURL.lastIndexOf('/');");
      scriptBuilder.append("      if (stringStartIndex !== -1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         stringStartIndex ++;");
      scriptBuilder.append("         var stringEndIndex = mainPhotoURL.indexOf('_', stringStartIndex);");
      scriptBuilder.append("         if (stringEndIndex !== -1)");
      scriptBuilder.append("            return mainPhotoURL.substring(stringStartIndex, stringEndIndex);");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function v3GetPhotoDisplayName(photoModels, photoID, ownerID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var title = photoModels.getValue(photoID, 'title');");
      scriptBuilder.append("   if ((typeof title) === 'string')");
      scriptBuilder.append("   {");
      scriptBuilder.append("      title = title.trim();");

      scriptBuilder.append("      var userDisplayName = appContext.modelRegistries['person-models'].getValue(ownerID, 'realname');");
      scriptBuilder.append("      if ((typeof userDisplayName === 'undefined') || (userDisplayName === null) || (userDisplayName === ''))");
      scriptBuilder.append("         userDisplayName = appContext.modelRegistries['person-models'].getValue(ownerID, 'username');");

      scriptBuilder.append("      if ((typeof userDisplayName) === 'string')");
      scriptBuilder.append("      {");
      scriptBuilder.append("         userDisplayName = userDisplayName.trim();");
      scriptBuilder.append("         return [title, userDisplayName];");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function v3GetThumbnailURL(photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var metaElements = document.head.getElementsByTagName('meta');");
      scriptBuilder.append("   for (var elementIndex = 0; elementIndex < metaElements.length; elementIndex ++)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var element = metaElements[elementIndex];");
      scriptBuilder.append("      if (element.getAttribute('property') === 'og:image')");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var checkPhotoIDIndex = element.content.lastIndexOf('/');");
      scriptBuilder.append("         if ((checkPhotoIDIndex !== -1) && (checkPhotoIDIndex === element.content.lastIndexOf('/' + photoID, checkPhotoIDIndex)))");
      scriptBuilder.append("            return element.content;");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function v3GetThumbnailURLElements(thumbnailURL)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var stringStartIndex = thumbnailURL.indexOf('//farm');");
      scriptBuilder.append("   var stringEndIndex;");
      scriptBuilder.append("   var farmID;");

      scriptBuilder.append("   if (stringStartIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      stringStartIndex += 6;");
      scriptBuilder.append("      stringEndIndex = thumbnailURL.indexOf('.staticflickr.com/', stringStartIndex);");
      scriptBuilder.append("      if (stringEndIndex === -1)");
      scriptBuilder.append("         return null;");

      scriptBuilder.append("      farmID = thumbnailURL.substring(stringStartIndex, stringEndIndex);");
      scriptBuilder.append("      stringStartIndex = stringEndIndex + 18;");
      scriptBuilder.append("   }");
      scriptBuilder.append("   else");
      scriptBuilder.append("   {");
      scriptBuilder.append("      stringStartIndex = thumbnailURL.indexOf('.staticflickr.com/');");
      scriptBuilder.append("      if (stringStartIndex === -1)");
      scriptBuilder.append("         return null;");

      scriptBuilder.append("      stringStartIndex = thumbnailURL.indexOf('/', stringStartIndex);");
      scriptBuilder.append("      stringStartIndex ++;");
      scriptBuilder.append("      stringEndIndex = thumbnailURL.indexOf('/', stringStartIndex);");
      scriptBuilder.append("      if (stringStartIndex >= stringEndIndex)");
      scriptBuilder.append("         return null;");

      scriptBuilder.append("      farmID = thumbnailURL.substring(stringStartIndex, stringEndIndex);");

      scriptBuilder.append("      stringStartIndex = stringEndIndex + 1;");
      scriptBuilder.append("   }");

      scriptBuilder.append("   stringEndIndex = thumbnailURL.indexOf('/', stringStartIndex);");

      scriptBuilder.append("   if (stringEndIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var serverID = thumbnailURL.substring(stringStartIndex, stringEndIndex);");

      scriptBuilder.append("      stringStartIndex = thumbnailURL.indexOf('_', stringEndIndex);");
      scriptBuilder.append("      if (stringStartIndex !== -1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         stringStartIndex ++;");
      scriptBuilder.append("         stringEndIndex = thumbnailURL.indexOf('_', stringStartIndex);");

      scriptBuilder.append("         if (stringEndIndex !== -1)");
      scriptBuilder.append("            return [farmID, serverID, thumbnailURL.substring(stringStartIndex, stringEndIndex)];");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function v3GetPhotoTags(photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTagModels;");

      scriptBuilder.append("   if (objectExists(appContext, 'modelRegistries', 'photo-tags-models'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoTagModels = appContext.modelRegistries['photo-tags-models'].getValue(photoID, 'tags');");
      scriptBuilder.append("      if ((typeof photoTagModels) === 'object')");
      scriptBuilder.append("         photoTagModels = photoTagModels.getList();");
      scriptBuilder.append("      else");
      scriptBuilder.append("         return null;");
      scriptBuilder.append("   }");
      scriptBuilder.append("   else");
      scriptBuilder.append("      return null;");

      scriptBuilder.append("   var photoTags = [];");

      scriptBuilder.append("   var tagModels = appContext.modelRegistries['tag-models'];");
      scriptBuilder.append("   var photoTagID;");
      scriptBuilder.append("   var photoTag;");

      scriptBuilder.append("   for (var tagIndex = 0; tagIndex < photoTagModels.length; tagIndex ++)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoTagID = photoTagModels[tagIndex].id;");

      scriptBuilder.append("      photoTag = tagModels.getValue(photoTagID, 'tagRaw');");
      scriptBuilder.append("      if (photoTag.length > 0)");
      scriptBuilder.append("         photoTags[photoTags.length] = photoTag;");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return photoTags;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getActivePhotoData()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var itemData = null;");
      scriptBuilder.append("   if (v3IsAppContextVariableAvailable())");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var viewParentElement = v3GetViewParentElement(['photo-well-scrappy-view']);");
      scriptBuilder.append("      if (viewParentElement !== null)");
      scriptBuilder.append("         itemData = v3GetActivePhotoData(viewParentElement, ['photo-well-media-scrappy-view', 'low-res-photo']);");
      scriptBuilder.append("      else");
      scriptBuilder.append("      {");
      scriptBuilder.append("         viewParentElement = v3GetViewParentElement(['photo-page-lightbox-scrappy-view']);");
      scriptBuilder.append("         if (viewParentElement !== null)");
      scriptBuilder.append("            itemData = v3GetActivePhotoData(viewParentElement, ['photo-well-media-view', 'low-res-photo']);");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   if ((itemData !== null) && (itemData[0] !== null) && (itemData[1] !== null) && (itemData[2] !== null) && (itemData[3] !== null) && (itemData[4] !== null))");
      scriptBuilder.append("      return itemData;");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function main()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var result = [true, null];");

      scriptBuilder.append("   try");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (hostCheck('flickr.com'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         if (document.readyState === 'complete')");
      scriptBuilder.append("            result[1] = getActivePhotoData();");
      scriptBuilder.append("         else");
      scriptBuilder.append("            result[0] = false;");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");
      scriptBuilder.append("   catch (exception)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (debug)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         if (objectExists(exception, 'stack'))");
      scriptBuilder.append("            consoleError(exception.stack.toString());");
      scriptBuilder.append("         else");
      scriptBuilder.append("            consoleError(exception.toString());");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return result;");
      scriptBuilder.append("}");
      /* End paste area.
       */

      scriptBuilder.append("return main();");

      scriptBuilder.append("})();");

      library = scriptBuilder.toString();
   }


   private FlickrJavaScriptString()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public String getLiveExecutor()
   {
      return library;
   }
}