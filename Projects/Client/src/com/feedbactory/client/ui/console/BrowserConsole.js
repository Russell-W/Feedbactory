

function examine(object)
{
   if (typeof object !== 'object')
      return object;

   var showResult = new Array();

   var showResultNode;
   var keyIndex = 0;
   var keyValue;

   for (var key in object)
   {
      keyValue = object[key];

      showResultNode = new Array(3);
      showResultNode[0] = key;
      showResultNode[1] = typeof keyValue;

      /* If the keyValue is of type boolean, string, or number, it will be successfully converted to a Java type and the value will be displayable;
       * no work needs to be done here. If the keyValue type is undefined, null, function, or some unknown type of object, it will be converted to
       * a null in Java, but the typeof value in the 2nd array element will be enough information for the user.
       * The only work case that explicitly needs to be handled is if the keyValue is of type object and is non-null, then it needs to be made
       * clear to the user that it's a nested object and not null (typeof null === 'object' in JavaScript).
       */
      if ((typeof keyValue === 'object') && (keyValue !== null))
         showResultNode[2] = '[object]';
      else
         showResultNode[2] = keyValue;

      showResult[keyIndex] = showResultNode;

      keyIndex ++;
   }

   return showResult;
}