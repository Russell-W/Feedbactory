
package com.feedbactory.client.ui.console;


final class BrowserConsoleJavaScriptString
{
   static final String library;


   static
   {
      final StringBuilder scriptBuilder = new StringBuilder(1000);

      scriptBuilder.append("function examine(object)");
      scriptBuilder.append("{");
      scriptBuilder.append("   if (typeof object !== 'object')");
      scriptBuilder.append("      return object;");

      scriptBuilder.append("   var showResult = new Array();");

      scriptBuilder.append("   var showResultNode;");
      scriptBuilder.append("   var keyIndex = 0;");
      scriptBuilder.append("   var keyValue;");

      scriptBuilder.append("   for (var key in object)");
      scriptBuilder.append("   {");
      scriptBuilder.append("      keyValue = object[key];");

      scriptBuilder.append("      showResultNode = new Array();");
      scriptBuilder.append("      showResultNode[0] = key;");
      scriptBuilder.append("      showResultNode[1] = typeof keyValue;");

      scriptBuilder.append("      if ((typeof keyValue === 'object') && (keyValue !== null))");
      scriptBuilder.append("         showResultNode[2] = '[object]';");
      scriptBuilder.append("      else");
      scriptBuilder.append("         showResultNode[2] = keyValue;");

      scriptBuilder.append("      showResult[keyIndex] = showResultNode;");

      scriptBuilder.append("      keyIndex ++;");
      scriptBuilder.append("   }");

      scriptBuilder.append("   return showResult;");
      scriptBuilder.append("}");

      library = scriptBuilder.toString();
   }
}