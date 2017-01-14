
package com.feedbactory.client.ui.browser.feedback.js;


import com.feedbactory.client.core.FeedbactoryClientConstants;


abstract public class SharedJavaScriptString
{
   static final public String library;


   static
   {
      final StringBuilder scriptBuilder = new StringBuilder(10000);

      scriptBuilder.append("var debug = ");
      scriptBuilder.append(FeedbactoryClientConstants.IsDevelopmentProfile);
      scriptBuilder.append(';');

      /* Start paste area.
       */
      scriptBuilder.append("if (! String.prototype.trim)");
      scriptBuilder.append("{");
      scriptBuilder.append("   String.prototype.trim = function()");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var whitespace = ' \\n\\r\\t\\f\\x0b\\xa0\\u2000\\u2001\\u2002\\u2003\\u2004\\u2005\\u2006\\u2007\\u2008\\u2009\\u200a\\u200b\\u2028\\u2029\\u3000';");

      scriptBuilder.append("      for (var startIndex = 0; startIndex < this.length; startIndex ++)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         if (whitespace.indexOf(this.charAt(startIndex)) === -1)");
      scriptBuilder.append("            break;");
      scriptBuilder.append("      }");

      scriptBuilder.append("      for (var endIndex = (this.length - 1); endIndex > startIndex; endIndex --)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         if (whitespace.indexOf(this.charAt(endIndex)) === -1)");
      scriptBuilder.append("            break;");
      scriptBuilder.append("      }");

      scriptBuilder.append("      return (startIndex <= endIndex) ? this.substring(startIndex, (endIndex + 1)) : '';");
      scriptBuilder.append("   };");
      scriptBuilder.append("}");


      scriptBuilder.append("if (! String.prototype.endsWith)");
      scriptBuilder.append("{");
      scriptBuilder.append("   String.prototype.endsWith = function(suffix)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (this.length >= suffix.length)");
      scriptBuilder.append("         return (this.indexOf(suffix, this.length - suffix.length) !== -1);");
      scriptBuilder.append("      else");
      scriptBuilder.append("         return false;");
      scriptBuilder.append("   };");
      scriptBuilder.append("}");


      scriptBuilder.append("function trimURLArgument(url)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var argumentStartIndex = url.lastIndexOf('?');");
      scriptBuilder.append("   if (argumentStartIndex !== -1)");
      scriptBuilder.append("      url = url.substring(0, argumentStartIndex);");

      scriptBuilder.append("   return url;");
      scriptBuilder.append("}");


      scriptBuilder.append("function stringify(argument)");
      scriptBuilder.append("{");
      scriptBuilder.append("   if ((typeof argument) === 'number')");
      scriptBuilder.append("      return argument.toString();");
      scriptBuilder.append("   else");
      scriptBuilder.append("      return argument;");
      scriptBuilder.append("}");


      scriptBuilder.append("function objectExists(rootObject)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var object = rootObject;");

      scriptBuilder.append("   for (var argumentIndex = 1; argumentIndex < arguments.length; argumentIndex ++)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      if (! Object.prototype.hasOwnProperty.call(object, arguments[argumentIndex]))");
      scriptBuilder.append("         return false;");

      scriptBuilder.append("      object = object[arguments[argumentIndex]];");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return true;");
      scriptBuilder.append("}");


      scriptBuilder.append("function hasClassName(element, className)");
      scriptBuilder.append("{");
      scriptBuilder.append("   if (element.className === className)");
      scriptBuilder.append("      return true;");
      scriptBuilder.append("   else");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var classNameElements = element.className.split(' ');");
      scriptBuilder.append("      for (var index = 0; index < classNameElements.length; index ++)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         if (classNameElements[index] === className)");
      scriptBuilder.append("            return true;");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return false;");
      scriptBuilder.append("}");


      scriptBuilder.append("function textContent(element)");
      scriptBuilder.append("{");
      scriptBuilder.append("   if (element.nodeType === 3)");
      scriptBuilder.append("      return element.nodeValue;");
      scriptBuilder.append("   else if (element.nodeType === 1)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var elementTextContent = '';");
      scriptBuilder.append("      var childNodes = element.childNodes;");

      scriptBuilder.append("      for (var childIndex = 0; childIndex < childNodes.length; childIndex ++)");
      scriptBuilder.append("         elementTextContent += textContent(childNodes[childIndex]);");

      scriptBuilder.append("      return elementTextContent;");
      scriptBuilder.append("   }");
      scriptBuilder.append("   else");
      scriptBuilder.append("      return '';");
      scriptBuilder.append("}");


      scriptBuilder.append("function getFirstElementByClassNames(classNames, startElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   startElement = (startElement || document);");

      scriptBuilder.append("   if ('getElementsByClassName' in startElement)");
      scriptBuilder.append("      return nativeGetFirstElementByClassNames(classNames, startElement, 0);");
      scriptBuilder.append("   else if ('$' in window)");
      scriptBuilder.append("      return jQueryGetFirstElementByClassName(classNames, startElement);");
      scriptBuilder.append("   else if ('YUI' in window)");
      scriptBuilder.append("      return yuiGetFirstElementByClassName(classNames, startElement);");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function nativeGetFirstElementByClassNames(classNames, startElement, classNamesIndex)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var elements = startElement.getElementsByClassName(classNames[classNamesIndex]);");
      scriptBuilder.append("   if (elements.length > 0)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var element = elements[0];");
      scriptBuilder.append("      if (classNamesIndex === (classNames.length - 1))");
      scriptBuilder.append("         return element;");
      scriptBuilder.append("      else");
      scriptBuilder.append("         return nativeGetFirstElementByClassNames(classNames, element, classNamesIndex + 1);");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function jQueryGetFirstElementByClassName(classNames, startElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var selectorString = '';");
      scriptBuilder.append("   for (var classNamesIndex = 0; classNamesIndex < classNames.length; classNamesIndex ++)");
      scriptBuilder.append("      selectorString += '.' + classNames[classNamesIndex] + ':first ';");

      scriptBuilder.append("   var selectorResult = $(startElement).find(selectorString);");
      scriptBuilder.append("   if (selectorResult.length >= 1)");
      scriptBuilder.append("      return selectorResult[0];");
      scriptBuilder.append("   else");
      scriptBuilder.append("      return null;");
      scriptBuilder.append("}");


      scriptBuilder.append("function yuiGetFirstElementByClassName(classNames, startElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var selectorString = '';");
      scriptBuilder.append("   for (var classNamesIndex = 0; classNamesIndex < classNames.length; classNamesIndex ++)");
      scriptBuilder.append("      selectorString += '.' + classNames[classNamesIndex] + ' ';");

      scriptBuilder.append("   var element = null;");
      scriptBuilder.append("   YUI().use('node', function (Y)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var startElementResult = Y.one(startElement);");
      scriptBuilder.append("      if (startElementResult !== null)");
      scriptBuilder.append("      {");
      scriptBuilder.append("         var selectorResult = startElementResult.one(selectorString);");
      scriptBuilder.append("         if (selectorResult !== null)");
      scriptBuilder.append("            element = selectorResult.getDOMNode();");
      scriptBuilder.append("      }");
      scriptBuilder.append("   });");

      scriptBuilder.append("   return element;");
      scriptBuilder.append("}");


      scriptBuilder.append("function getElementsByClassNames(classNames, startElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   startElement = (startElement || document);");

      scriptBuilder.append("   if ('getElementsByClassName' in startElement)");
      scriptBuilder.append("      return nativeGetElementsByClassNames(classNames, startElement, 0, []);");
      scriptBuilder.append("   else if ('$' in window)");
      scriptBuilder.append("      return jQueryGetElementsByClassName(classNames, startElement);");
      scriptBuilder.append("   else if ('YUI' in window)");
      scriptBuilder.append("      return yuiGetElementsByClassName(classNames, startElement);");

      scriptBuilder.append("   return [];");
      scriptBuilder.append("}");


      scriptBuilder.append("function nativeGetElementsByClassNames(classNames, startElement, classNamesIndex, matches)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var elements = startElement.getElementsByClassName(classNames[classNamesIndex]);");
      scriptBuilder.append("   for (var elementIndex = 0; elementIndex < elements.length; elementIndex ++)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var element = elements[elementIndex];");
      scriptBuilder.append("      if (classNamesIndex === (classNames.length - 1))");
      scriptBuilder.append("         matches[matches.length] = element;");
      scriptBuilder.append("      else");
      scriptBuilder.append("      {");
      scriptBuilder.append("         nativeGetElementsByClassNames(classNames, element, classNamesIndex + 1, matches);");
      scriptBuilder.append("      }");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return matches;");
      scriptBuilder.append("}");


      scriptBuilder.append("function jQueryGetElementsByClassName(classNames, startElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var selectorString = '';");
      scriptBuilder.append("   for (var classNamesIndex = 0; classNamesIndex < classNames.length; classNamesIndex ++)");
      scriptBuilder.append("      selectorString += '.' + classNames[classNamesIndex] + ' ';");

      scriptBuilder.append("   return $(startElement).find(selectorString).toArray();");
      scriptBuilder.append("}");


      scriptBuilder.append("function yuiGetElementsByClassName(classNames, startElement)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var selectorString = '';");
      scriptBuilder.append("   for (var classNamesIndex = 0; classNamesIndex < classNames.length; classNamesIndex ++)");
      scriptBuilder.append("      selectorString += '.' + classNames[classNamesIndex] + ' ';");

      scriptBuilder.append("   var matchingElements = [];");

      scriptBuilder.append("   YUI().use('node', function (Y)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      var startElementResult = Y.one(startElement);");
      scriptBuilder.append("      if (startElementResult !== null)");
      scriptBuilder.append("         matchingElements = startElementResult.all(selectorString).getDOMNodes();");
      scriptBuilder.append("   });");

      scriptBuilder.append("   return matchingElements;");
      scriptBuilder.append("}");


      scriptBuilder.append("function addEventListener(element, eventType, listener)");
      scriptBuilder.append("{");
      scriptBuilder.append("   if (element.addEventListener)");
      scriptBuilder.append("      element.addEventListener(eventType, listener);");
      scriptBuilder.append("   else");
      scriptBuilder.append("      element.attachEvent('on' + eventType, listener);");
      scriptBuilder.append("}");


      scriptBuilder.append("function hostCheck(hostNameSuffix)");
      scriptBuilder.append("{");
      scriptBuilder.append("   var dotPrependedSuffix = '.' + hostNameSuffix;");

      scriptBuilder.append("   try");
      scriptBuilder.append("   {");
      scriptBuilder.append("      return ((window.location.hostname === hostNameSuffix) || window.location.hostname.endsWith(dotPrependedSuffix)) &&");
      scriptBuilder.append("             ((window.location.protocol === 'http:') || (window.location.protocol === 'https:'));");
      scriptBuilder.append("   }");
      scriptBuilder.append("   catch (exception)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      return false;");
      scriptBuilder.append("   }");
      scriptBuilder.append("}");

      library = scriptBuilder.toString();
   }


   private SharedJavaScriptString()
   {
   }
}