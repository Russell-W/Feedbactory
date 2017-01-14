
package com.feedbactory.client.core.network;


import com.feedbactory.client.core.FeedbactoryClientConstants;


abstract public class ClientNetworkConstants
{
   static final public int MaximumRequestSizeBytes = 8192;

   static final public String FeedbactoryApplicationServer;
   static final public String FeedbactoryWebServer;


   static
   {
      // Compiler optimisation will remove the unused code paths from the compiled class file.
      if (FeedbactoryClientConstants.IsDevelopmentProfile)
      {
         FeedbactoryApplicationServer = "127.0.0.1";
         FeedbactoryWebServer = "http://127.0.0.1/feedbactory";
      }
      else if (FeedbactoryClientConstants.IsTestProfile)
      {
         FeedbactoryApplicationServer = "127.0.0.1";
         FeedbactoryWebServer = "http://127.0.0.1/feedbactory";
      }
      else if (FeedbactoryClientConstants.IsProductionProfile)
      {
         FeedbactoryApplicationServer = "127.0.0.1";
         FeedbactoryWebServer = "http://127.0.0.1/feedbactory";
      }
      else
         throw new AssertionError("Unknown or misconfigured execution profile.");
   }


   private ClientNetworkConstants()
   {
   }
}