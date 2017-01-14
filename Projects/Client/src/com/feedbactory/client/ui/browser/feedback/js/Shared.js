

// Trim() is only available from IE9 onwards, so slot in an implementation of it here if necessary.
if (! String.prototype.trim)
{
   String.prototype.trim = function()
   {
      var whitespace = ' \n\r\t\f\x0b\xa0\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u200b\u2028\u2029\u3000';

      for (var startIndex = 0; startIndex < this.length; startIndex ++)
      {
         if (whitespace.indexOf(this.charAt(startIndex)) === -1)
            break;
      }

      for (var endIndex = (this.length - 1); endIndex > startIndex; endIndex --)
      {
         if (whitespace.indexOf(this.charAt(endIndex)) === -1)
            break;
      }

      return (startIndex <= endIndex) ? this.substring(startIndex, (endIndex + 1)) : '';
   };
}


if (! String.prototype.endsWith)
{
   String.prototype.endsWith = function(suffix)
   {
      if (this.length >= suffix.length)
         return (this.indexOf(suffix, this.length - suffix.length) !== -1);
      else
         return false;
   };
}


function trimURLArgument(url)
{
   var argumentStartIndex = url.lastIndexOf('?');
   if (argumentStartIndex !== -1)
      url = url.substring(0, argumentStartIndex);

   return url;
}


/* Coerce JavaScript numbers to be strings, otherwise they will be returned incorrectly to Java, eg:
 * 139404290 -> 1.3940429E8.
 * Note that 'new String(argument)' returns an 'object' type value that won't automatically be converted to a String when returned from JavaScript to Java,
 * whereas argument.toString() returns a 'string' type value that is converted properly.
 */
function stringify(argument)
{
   if ((typeof argument) === 'number')
      return argument.toString();
   else
      return argument;
}


// Usage: feedbactoryObjectExists(window, 'objectRoot', 'nestedProperty', etc.)
function objectExists(rootObject)
{
   var object = rootObject;

   for (var argumentIndex = 1; argumentIndex < arguments.length; argumentIndex ++)
   {
      /* Need to be careful here, some bare-bones objects don't inherit the hasOwnProperty function from Object,
       * eg. objects created using Object.create(null). Use the root Object funtion instead.
       */
      if (! Object.prototype.hasOwnProperty.call(object, arguments[argumentIndex]))
         return false;

      object = object[arguments[argumentIndex]];
   }

   return true;
}


function hasClassName(element, className)
{
   if (element.className === className)
      return true;
   else
   {
      var classNameElements = element.className.split(' ');
      for (var index = 0; index < classNameElements.length; index ++)
      {
         if (classNameElements[index] === className)
            return true;
      }
   }

   return false;
}


/* Custom alternative function to both element.textContent and the IE-specific element.innerText, which return subtly different results.
 * Relying on those functions would be a problem from the perspective of ripping identical item profile content across different browsers
 * (and versions) and minimising/eliminating item fragmentation on the server.
 */
function textContent(element)
{
   if (element.nodeType === 3)
      return element.nodeValue;
   else if (element.nodeType === 1)
   {
      var elementTextContent = '';
      var childNodes = element.childNodes;

      for (var childIndex = 0; childIndex < childNodes.length; childIndex ++)
         elementTextContent += textContent(childNodes[childIndex]);

      return elementTextContent;
   }
   else
      return '';
}


function getFirstElementByClassNames(classNames, startElement)
{
   startElement = (startElement || document);

   if ('getElementsByClassName' in startElement)
      return nativeGetFirstElementByClassNames(classNames, startElement, 0);
   else if ('$' in window)
      return jQueryGetFirstElementByClassName(classNames, startElement);
   else if ('YUI' in window)
      return yuiGetFirstElementByClassName(classNames, startElement);

   return null;
}


function nativeGetFirstElementByClassNames(classNames, startElement, classNamesIndex)
{
   var elements = startElement.getElementsByClassName(classNames[classNamesIndex]);
   if (elements.length > 0)
   {
      var element = elements[0];
      if (classNamesIndex === (classNames.length - 1))
         return element;
      else
         return nativeGetFirstElementByClassNames(classNames, element, classNamesIndex + 1);
   }

   return null;
}


function jQueryGetFirstElementByClassName(classNames, startElement)
{
   var selectorString = '';
   for (var classNamesIndex = 0; classNamesIndex < classNames.length; classNamesIndex ++)
      selectorString += '.' + classNames[classNamesIndex] + ':first ';

   var selectorResult = $(startElement).find(selectorString);
   if (selectorResult.length >= 1)
      return selectorResult[0];
   else
      return null;
}


function yuiGetFirstElementByClassName(classNames, startElement)
{
   var selectorString = '';
   for (var classNamesIndex = 0; classNamesIndex < classNames.length; classNamesIndex ++)
      selectorString += '.' + classNames[classNamesIndex] + ' ';

   /* Experimentation with executing JavaScript on browser events has shown that sometimes although the YUI object is defined,
    * it may not yet be fully initialised. This can produce some unusual side effects such as the nested function below not being
    * executed at all, even if the call to use() doesn't produce any error (!). I originally thought that this problem may have
    * been caused by some sort of concurrency issue between SWT and the browser layer, eg. the nested function being executed
    * at the same time as the outer, but this would presumably break everything. The issue is that the function just isn't being invoked.
    * 
    * With this in mind, any code injection that utilises third party functions must be used defensively; if the element variable below is not
    * initialised to null, it will be undefined if the nested function never executes. Thankfully this method of fetching matching elements
    * is only used in older versions of IE: <= v7.
    *
    * Confirmed that this is an issue on Flickr pages.
    */
   var element = null;
   YUI().use('node', function (Y)
   {
      var startElementResult = Y.one(startElement);
      if (startElementResult !== null)
      {
         var selectorResult = startElementResult.one(selectorString);
         if (selectorResult !== null)
            element = selectorResult.getDOMNode();
      }
   });

   return element;
}


function getElementsByClassNames(classNames, startElement)
{
   startElement = (startElement || document);

   if ('getElementsByClassName' in startElement)
      return nativeGetElementsByClassNames(classNames, startElement, 0, []);
   else if ('$' in window)
      return jQueryGetElementsByClassName(classNames, startElement);
   else if ('YUI' in window)
      return yuiGetElementsByClassName(classNames, startElement);

   return [];
}


function nativeGetElementsByClassNames(classNames, startElement, classNamesIndex, matches)
{
   var elements = startElement.getElementsByClassName(classNames[classNamesIndex]);
   for (var elementIndex = 0; elementIndex < elements.length; elementIndex ++)
   {
      var element = elements[elementIndex];
      if (classNamesIndex === (classNames.length - 1))
         matches[matches.length] = element;
      else
      {
         // No return here - the processing must continue through to the end of each loop.
         nativeGetElementsByClassNames(classNames, element, classNamesIndex + 1, matches);
      }
   }

   return matches;
}


function jQueryGetElementsByClassName(classNames, startElement)
{
   var selectorString = '';
   for (var classNamesIndex = 0; classNamesIndex < classNames.length; classNamesIndex ++)
      selectorString += '.' + classNames[classNamesIndex] + ' ';

   return $(startElement).find(selectorString).toArray();
}


function yuiGetElementsByClassName(classNames, startElement)
{
   var selectorString = '';
   for (var classNamesIndex = 0; classNamesIndex < classNames.length; classNamesIndex ++)
      selectorString += '.' + classNames[classNamesIndex] + ' ';

   // See the warning comment in yuiGetFirstElementByClassName - the same applies here.
   var matchingElements = [];

   YUI().use('node', function (Y)
   {
      var startElementResult = Y.one(startElement);
      if (startElementResult !== null)
         matchingElements = startElementResult.all(selectorString).getDOMNodes();
   });

   return matchingElements;
}


function addEventListener(element, eventType, listener)
{
   if (element.addEventListener)
      element.addEventListener(eventType, listener);
   else
      element.attachEvent('on' + eventType, listener);
}


function hostCheck(hostNameSuffix)
{
   var dotPrependedSuffix = '.' + hostNameSuffix;

   try
   {
      /* This code may fail with a permission denied error if the window's top level has a different domain to that of the window executing this code,
       * ie. Same Origin Policy. This happens with IE 10 and Flickr, when viewing a photo page and then clicking the add to favourites 'star', which pops up
       * a new window for https://login.yahoo.com. The pop-up to sign in to Yahoo is created via a redirect from the Flickr domain, eg.
       * http://www.flickr.com/signin?src=fave®=1&popup=1&redir=/photo_grease_postlogin.gne?d=http%3A%2F%2Fwww.flickr.com%2Fphotos%2F... etc, hence why this
       * code is executed: the domain is still Flickr. Strangely, Safari doesn't have the same behaviour - this code is not executed for the new pop-up
       * for https://login.yahoo.com page, which is probably how it should be.
       */
      return ((window.location.hostname === hostNameSuffix) || window.location.hostname.endsWith(dotPrependedSuffix)) &&
             ((window.location.protocol === 'http:') || (window.location.protocol === 'https:'));
   }
   catch (exception)
   {
      return false;
   }
}