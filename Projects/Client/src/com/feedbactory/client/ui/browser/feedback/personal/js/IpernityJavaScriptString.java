
package com.feedbactory.client.ui.browser.feedback.personal.js;


import com.feedbactory.client.ui.browser.feedback.js.SharedJavaScriptString;

abstract public class IpernityJavaScriptString
{
   static private final String library;

   static
   {
      final StringBuilder scriptBuilder = new StringBuilder(SharedJavaScriptString.library.length() + 5000);

      scriptBuilder.append("return (function()");
      scriptBuilder.append("{");

      scriptBuilder.append(SharedJavaScriptString.library);

      /* Start paste area.
       */
      scriptBuilder.append("function getActivePhotoData()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var activePhotoData = [null, null, null, null];");

      scriptBuilder.append("   if (isLightboxShowing())");
      scriptBuilder.append("      activePhotoData = getLightboxPhotoData();");
      scriptBuilder.append("   else if (isRegularPhotoViewShowing())");
      scriptBuilder.append("      activePhotoData = getRegularViewPhotoData();");

      scriptBuilder.append("   if ((activePhotoData[0] !== null) && (activePhotoData[1] !== null) && (activePhotoData[2] !== null) && (activePhotoData[3] !== null))");
      scriptBuilder.append("      return activePhotoData;");
      scriptBuilder.append("   else");
      scriptBuilder.append("      return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function isRegularPhotoViewShowing()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return objectExists(window, 'doc_id');");
      scriptBuilder.append("}");


      scriptBuilder.append("function isLightboxShowing()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return (objectExists(window, 'lightbox-instance') && window['lightbox-instance'].is_shown);");
      scriptBuilder.append("}");


      scriptBuilder.append("function isPhoto(documentObject)");
      scriptBuilder.append("{");
      scriptBuilder.append("   return (documentObject.type === '1');");
      scriptBuilder.append("}");


      scriptBuilder.append("function isPhotoPublic(documentObject)");
      scriptBuilder.append("{");
      scriptBuilder.append("   return (documentObject.share === '31');");
      scriptBuilder.append("}");


      scriptBuilder.append("function decodeHTMLText(text)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var tempDivElement = document.createElement('div');");
      scriptBuilder.append("   tempDivElement.innerHTML = text;");
      scriptBuilder.append("   return textContent(tempDivElement);");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxPhotoData()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var activePhotoData = [null, null, null, null];");

      scriptBuilder.append("   var lightboxPhotoObject = getLightboxPhotoObject();");
      scriptBuilder.append("   if ((lightboxPhotoObject !== null) && isPhoto(lightboxPhotoObject) && isPhotoPublic(lightboxPhotoObject))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      activePhotoData[0] = getLightboxPhotoID(lightboxPhotoObject);");
      scriptBuilder.append("      if (activePhotoData[0] !== null)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         activePhotoData[1] = getLightboxPhotographerID(lightboxPhotoObject);");
      scriptBuilder.append("         if (activePhotoData[1] !== null)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            activePhotoData[2] = getLightboxPhotoDisplayName(lightboxPhotoObject, activePhotoData[1]);");
      scriptBuilder.append("            if (activePhotoData[2] !== null)");
      scriptBuilder.append("               activePhotoData[3] = getLightboxThumbnailURLElements(lightboxPhotoObject);");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return activePhotoData;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxPhotoObject()");
      scriptBuilder.append("{");
      scriptBuilder.append("   if (objectExists(window['lightbox-instance'], 'current_doc'))");
      scriptBuilder.append("      return window['lightbox-instance'].current_doc;");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxPhotoID(lightboxPhotoObject)");
      scriptBuilder.append("{");
      scriptBuilder.append("   return stringify(lightboxPhotoObject.doc_id).trim();");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxPhotographerID(lightboxPhotoObject)");
      scriptBuilder.append("{");
      scriptBuilder.append("   return stringify(lightboxPhotoObject.user_id).trim();");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxPhotoDisplayName(lightboxPhotoObject, photographerID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var title = decodeHTMLText(lightboxPhotoObject.title.trim());");

      scriptBuilder.append("   if (objectExists(window['lightbox-instance'], 'users', photographerID))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photographerName = decodeHTMLText(window['lightbox-instance'].users[photographerID].title.trim());");
      scriptBuilder.append("      return [title, photographerName];");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getLightboxThumbnailURLElements(lightboxPhotoObject)");
      scriptBuilder.append("{");
      scriptBuilder.append("   if (objectExists(lightboxPhotoObject, 'thumbs', '240'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var thumbnailObject = lightboxPhotoObject.thumbs['240'];");

      scriptBuilder.append("      var pathID = stringify(thumbnailObject.path).trim();");
      scriptBuilder.append("      if ((pathID.charAt(0) === '/') && (pathID.charAt(pathID.length - 1) === '/'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         pathID = pathID.substring(1, pathID.length - 1);");
      scriptBuilder.append("         var secretID = stringify(thumbnailObject.secret).trim();");

      scriptBuilder.append("         if (thumbnailObject.url.indexOf('//cdn.ipernity.com/') !== -1)");
      scriptBuilder.append("            return ['', pathID, secretID];");
      scriptBuilder.append("         else");
      scriptBuilder.append("         {");
      scriptBuilder.append("            var farmID = stringify(thumbnailObject.farm).trim();");
      scriptBuilder.append("            return [farmID, pathID, secretID];");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getRegularViewPhotoData()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var activePhotoData = [null, null, null, null];");

      scriptBuilder.append("   activePhotoData[0] = getPhotoID();");
      scriptBuilder.append("   if (activePhotoData[0] !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photoObject = getDocumentObject(activePhotoData[0]);");
      scriptBuilder.append("      if ((photoObject !== null) && isPhoto(photoObject) && isPhotoPublic(photoObject))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         activePhotoData[1] = getPhotographerID(photoObject);");
      scriptBuilder.append("         if (activePhotoData[1] !== null)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            activePhotoData[2] = getPhotoDisplayName(photoObject, activePhotoData[1]);");
      scriptBuilder.append("            if (activePhotoData[2] !== null)");
      scriptBuilder.append("            {");
      scriptBuilder.append("               var photoURL = getPhotoURL();");
      scriptBuilder.append("               if (photoURL !== null)");
      scriptBuilder.append("                  activePhotoData[3] = getThumbnailURLElements(photoURL, activePhotoData[0]);");
      scriptBuilder.append("            }");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return activePhotoData;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoID()");
      scriptBuilder.append("{");
      scriptBuilder.append("   return stringify(window.doc_id).trim();");
      scriptBuilder.append("}");


      scriptBuilder.append("function getDocumentObject(photoID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   if (objectExists(window, 'Data', 'doc', photoID))");
      scriptBuilder.append("      return window.Data.doc[photoID];");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotographerID(documentObject)");
      scriptBuilder.append("{");
      scriptBuilder.append("   return stringify(documentObject.user_id).trim();");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoDisplayName(documentObject, photographerID)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var title = decodeHTMLText(documentObject.title.trim());");

      scriptBuilder.append("   if (objectExists(window.Data, 'user', photographerID))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var photographerName = decodeHTMLText(window.Data.user[photographerID].title.trim());");
      scriptBuilder.append("      return [title, photographerName];");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoURL()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoElement = document.getElementById('doc_img');");
      scriptBuilder.append("   if ((photoElement !== null) && (photoElement.tagName === 'IMG'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      return photoElement.getAttribute('data-lowres').trim();");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getThumbnailURLElements(rawPhotoURL, photoID)");
      scriptBuilder.append("{");

      scriptBuilder.append("   var photoURL = trimURLArgument(rawPhotoURL);");
      scriptBuilder.append("   if (photoURL.endsWith('.jpg'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var stringStartIndex = photoURL.indexOf('//cdn.ipernity.com/');");
      scriptBuilder.append("      if (stringStartIndex !== -1)");
      scriptBuilder.append("         return getCDNFormatThumbnailURLElements(photoURL, photoID, stringStartIndex + 19);");
      scriptBuilder.append("      else");
      scriptBuilder.append("      {");
      scriptBuilder.append("         stringStartIndex = photoURL.indexOf('//u');");
      scriptBuilder.append("         if (stringStartIndex !== -1)");
      scriptBuilder.append("            return getFarmIDFormatThumbnailURLElements(photoURL, photoID, stringStartIndex + 3);");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getCDNFormatThumbnailURLElements(photoURL, photoID, pathIDStartIndex)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var stringEndIndex = photoURL.indexOf('/' + photoID + '.', pathIDStartIndex);");

      scriptBuilder.append("   if (stringEndIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var pathID = photoURL.substring(pathIDStartIndex, stringEndIndex);");
      scriptBuilder.append("      var stringStartIndex = stringEndIndex + photoID.length + 2;");
      scriptBuilder.append("      stringEndIndex = photoURL.indexOf('.', stringStartIndex);");
      scriptBuilder.append("      if (stringEndIndex !== -1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var secretID = photoURL.substring(stringStartIndex, stringEndIndex);");
      scriptBuilder.append("         return ['', pathID, secretID];");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getFarmIDFormatThumbnailURLElements(photoURL, photoID, farmIDStartIndex)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var stringEndIndex = photoURL.indexOf('.ipernity.com/', farmIDStartIndex);");

      scriptBuilder.append("   if (stringEndIndex !== -1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var farmID = photoURL.substring(farmIDStartIndex, stringEndIndex);");

      scriptBuilder.append("      var stringStartIndex = stringEndIndex + 14;");
      scriptBuilder.append("      stringEndIndex = photoURL.indexOf('/' + photoID + '.', stringStartIndex);");

      scriptBuilder.append("      if (stringEndIndex !== -1)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var pathID = photoURL.substring(stringStartIndex, stringEndIndex);");
      scriptBuilder.append("         stringStartIndex = stringEndIndex + photoID.length + 2;");
      scriptBuilder.append("         stringEndIndex = photoURL.indexOf('.', stringStartIndex);");
      scriptBuilder.append("         if (stringEndIndex !== -1)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            var secretID = photoURL.substring(stringStartIndex, stringEndIndex);");
      scriptBuilder.append("            return [farmID, pathID, secretID];");
      scriptBuilder.append("         }");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function main()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var result = [true, null];");

      scriptBuilder.append("   try");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (hostCheck('ipernity.com'))");
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


   private IpernityJavaScriptString()
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