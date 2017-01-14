
package com.feedbactory.client.ui.browser.feedback.personal.js;


import com.feedbactory.client.ui.browser.feedback.js.SharedJavaScriptString;


abstract public class FiveHundredPXJavaScriptString
{
   static private final String library;

   static
   {
      final StringBuilder scriptBuilder = new StringBuilder(SharedJavaScriptString.library.length() + 10000);

      scriptBuilder.append("return (function()");
      scriptBuilder.append("{");

      scriptBuilder.append(SharedJavaScriptString.library);

      /* Start paste area.
       */
      scriptBuilder.append("function getBrowserURLPhotoID()");
      scriptBuilder.append("{");
      scriptBuilder.append("   if (window.location.pathname.indexOf('/photo/') !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photoIDStartIndex = 7;");
      scriptBuilder.append("      var photoIDEndIndex = window.location.pathname.indexOf('/', photoIDStartIndex);");
      scriptBuilder.append("      if (photoIDEndIndex !== -1)");
      scriptBuilder.append("         return window.location.pathname.substring(photoIDStartIndex, photoIDEndIndex);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getActivePhotoObject()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoModelsRoot = null;");
      scriptBuilder.append("   if (document.getElementById('pxLightbox-1') !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (objectExists(window, 'App', 'content', 'currentView', 'body', 'collection'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         photoModelsRoot = window.App.content.currentView.body.collection;");
      scriptBuilder.append("      }");
      scriptBuilder.append("      else if (objectExists(window, 'App', 'controller', 'layout', 'bodyRegion', 'currentView', 'collection'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         photoModelsRoot = window.App.controller.layout.bodyRegion.currentView.collection;");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");
      scriptBuilder.append("   else if (objectExists(window, 'App', 'content', 'currentView', 'navigationContext'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photoModelsRoot = window.App.content.currentView.navigationContext;");
      scriptBuilder.append("   }");

      scriptBuilder.append("   if (photoModelsRoot !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var activePhotoModelIndex = photoModelsRoot.selectedPhotoOffset();");
      scriptBuilder.append("      if (activePhotoModelIndex !== -1)");
      scriptBuilder.append("         return photoModelsRoot.photoAt(activePhotoModelIndex);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoDataFromObject(photoObject)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var itemData = [null, null, null, null, null];");

      scriptBuilder.append("   itemData[0] = stringify(photoObject.id);");
      scriptBuilder.append("   if (itemData[0] !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      itemData[1] = stringify(photoObject.get('user_id'));");
      scriptBuilder.append("      itemData[2] = getPhotoDisplayName(photoObject);");
      scriptBuilder.append("      itemData[3] = getPhotoURLElements(photoObject, itemData[0]);");
      scriptBuilder.append("      itemData[4] = photoObject.get('tags');");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return itemData;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoDisplayName(photoObject)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoTitle = photoObject.get('name');");
      scriptBuilder.append("   if (photoTitle !== null)");
      scriptBuilder.append("      photoTitle = photoTitle.trim();");
      scriptBuilder.append("   else");
      scriptBuilder.append("      photoTitle = '';");

      scriptBuilder.append("   var photographerName = photoObject.getUser().get('fullname');");
      scriptBuilder.append("   if (photographerName !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      photographerName = photographerName.trim();");
      scriptBuilder.append("      return [photoTitle, photographerName];");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoURLElements(photoObject, photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var thumbnailPhotoURL = photoObject.getImagePath({size: 2});");
      scriptBuilder.append("   if (thumbnailPhotoURL !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var thumbnailURLKey = getImageKey(thumbnailPhotoURL, photoID);");
      scriptBuilder.append("      if (thumbnailURLKey !== null)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var largeImageURL = photoObject.getImagePath({size: 4});");
      scriptBuilder.append("         if (largeImageURL !== null)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            var largeImageURLKey = getImageKey(largeImageURL, photoID);");
      scriptBuilder.append("            if (largeImageURLKey !== null)");
      scriptBuilder.append("               return [thumbnailURLKey, largeImageURLKey];");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getImageKey(fullPhotoURL, photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   fullPhotoURL = trimURLArgument(fullPhotoURL);");
      scriptBuilder.append("   if (fullPhotoURL.endsWith('/'))");
      scriptBuilder.append("      fullPhotoURL = fullPhotoURL.substring(0, fullPhotoURL.length - 1);");

      scriptBuilder.append("   var photoIDStartIndex = fullPhotoURL.indexOf('/' + photoID + '/');");
      scriptBuilder.append("   if (photoIDStartIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photoDimensionsStartIndex = (photoIDStartIndex + photoID.length + 2);");
      scriptBuilder.append("      var photoURLIDStartIndex = fullPhotoURL.indexOf('/', photoDimensionsStartIndex);");
      scriptBuilder.append("      if (photoURLIDStartIndex !== -1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         photoURLIDStartIndex ++;");
      scriptBuilder.append("         if (photoURLIDStartIndex < fullPhotoURL.length)");
      scriptBuilder.append("            return fullPhotoURL.substring(photoURLIDStartIndex);");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getActivePhotoData()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var browserURLPhotoID = getBrowserURLPhotoID();");
      scriptBuilder.append("   if (browserURLPhotoID !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var activePhotoObject = getActivePhotoObject();");
      scriptBuilder.append("      if ((activePhotoObject !== null) && (stringify(activePhotoObject.id) === browserURLPhotoID) && activePhotoObject.isPublic() && (! activePhotoObject.get('nsfw')))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var activePhotoData = getPhotoDataFromObject(activePhotoObject);");
      scriptBuilder.append("         if ((activePhotoData[0] !== null) && (activePhotoData[1] !== null) && (activePhotoData[2] !== null) &&");
      scriptBuilder.append("             (activePhotoData[3] !== null) && (activePhotoData[4] !== null))");
      scriptBuilder.append("            return activePhotoData;");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function main()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var result = [true, null];");

      scriptBuilder.append("   try");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (hostCheck('500px.com'))");
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


   private FiveHundredPXJavaScriptString()
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