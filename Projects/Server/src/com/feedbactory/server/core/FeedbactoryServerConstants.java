
package com.feedbactory.server.core;


import java.nio.file.Path;
import java.nio.file.Paths;


abstract public class FeedbactoryServerConstants
{
   static final public int ServerConcurrency = 30;

   static final public Path ConfigurationPath = Paths.get("Configuration");
   static final public Path BaseDataPath = Paths.get("Data");
   static final public Path LogPath = Paths.get("Log");
   static final public String DataFileExtension = ".feedbactory";


   /****************************************************************************
    *
    ***************************************************************************/


   private FeedbactoryServerConstants()
   {
   }
}