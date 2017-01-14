
package com.feedbactory.client.ui.browser.feedback.personal.js;


import com.feedbactory.client.ui.browser.feedback.js.SharedJavaScriptString;

abstract public class TripleJJavaScriptString
{
   static final private String library;

   static
   {
      final StringBuilder scriptBuilder = new StringBuilder(SharedJavaScriptString.library.length() + 5000);

      scriptBuilder.append("return (function()");
      scriptBuilder.append("{");

      scriptBuilder.append(SharedJavaScriptString.library);

      /* Start paste area.
       */
      scriptBuilder.append("function getActiveItemData()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var itemData = [null, null, null];");

      scriptBuilder.append("   itemData[0] = getItemID();");
      scriptBuilder.append("   if (itemData[0] !== null)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      itemData[1] = getDisplayName();");
      scriptBuilder.append("      if (itemData[1] !== null)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         itemData[2] = getPhotoURL();");

      scriptBuilder.append("         return itemData;");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getItemID()");
      scriptBuilder.append("{");
      scriptBuilder.append("   if (window.location.pathname.endsWith('.htm'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var slashLastPosition = window.location.pathname.lastIndexOf('/');");
      scriptBuilder.append("      if (slashLastPosition !== -1)");
      scriptBuilder.append("         return window.location.pathname.substring(slashLastPosition + 1, window.location.pathname.length - 4);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getDisplayName()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var midholdElement = document.getElementById('midhold');");
      scriptBuilder.append("   if ((midholdElement !== null) && (midholdElement.tagName === 'DIV'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var pictureElement = getFirstElementByClassNames(['picture'], midholdElement);");
      scriptBuilder.append("      if ((pictureElement !== null) && (pictureElement.tagName === 'DIV'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var childNodes = pictureElement.childNodes;");

      scriptBuilder.append("         if ((childNodes.length === 1) && (childNodes[0].nodeType === 1) && (childNodes[0].tagName === 'IMG'))");
      scriptBuilder.append("            return childNodes[0].alt.trim();");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getPhotoURL()");
      scriptBuilder.append("{");
      scriptBuilder.append("   var photoSidebarElement = document.getElementById('people-col-left-b');");
      scriptBuilder.append("   if ((photoSidebarElement !== null) && (photoSidebarElement.tagName === 'DIV'))");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var thumbsElement = getFirstElementByClassNames(['thumbs'], photoSidebarElement);");
      scriptBuilder.append("      if ((thumbsElement !== null) && (thumbsElement.tagName === 'DIV'))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var imageElements = thumbsElement.getElementsByTagName('img');");
      scriptBuilder.append("         if (imageElements.length > 0)");
      scriptBuilder.append("         {");
      scriptBuilder.append("            var thumbnailURL = imageElements[0].src.trim();");
      scriptBuilder.append("            if (thumbnailURL.endsWith('.jpg'))");
      scriptBuilder.append("            {");
      scriptBuilder.append("               var imagePrefixIndex = thumbnailURL.indexOf('img/extras/');");
      scriptBuilder.append("               if (imagePrefixIndex !== -1)");
      scriptBuilder.append("               {");
      scriptBuilder.append("                  imagePrefixIndex += 11;");
      scriptBuilder.append("                  return thumbnailURL.substring(imagePrefixIndex, thumbnailURL.length - 4);");
      scriptBuilder.append("               }");
      scriptBuilder.append("            }");
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
      scriptBuilder.append("      if (hostCheck('abc.net.au') && (window.location.pathname.indexOf('/triplej/people/') === 0))");
      scriptBuilder.append("      {");
      scriptBuilder.append("         if (document.readyState === 'complete')");
      scriptBuilder.append("            result[1] = getActiveItemData();");
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


   private TripleJJavaScriptString()
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