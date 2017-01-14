/* Memos:
 * - I've moved these constants here from the main FeedbactoryClient class since their names would otherwise not be obfuscated, and variables such as
 *   isDevelopmentProfile would be best kept hidden.
 *
 * - The boolean switches declared here are by design not available as command-line options.
 *
 * - The execution profile booleans (development/test/production) are implemented in this simple yet unfortunately clumsy way to allow the compiler to remove
 *   development and test environment code and constants from compiled code where possible, eg. application server and email server addresses. See ClientNetworkConstants
 *   for an example. Unfortunately the compiler will not optimise on static final Enums. static int works but is not very informative when used from other classes,
 *   eg. FeedbactoryClientConstants.executionProfile == 1 (??).
 *
 *     - ProGuard obfuscation & shrinking unfortunately doesn't seem to be able to help with removing the final Enums either.
 */

package com.feedbactory.client.core;


import java.util.logging.Level;


final public class FeedbactoryClientConstants
{
   static final public String DisplayableVersionID = "2016.05.01.1";

   // A version timestamp, where values lesser than the server's minimum compatible client version will trigger the client to prompt for an update.
   static final public long VersionID = 1462063315361L;

   /* Execution profile switches, one and only one must be true. Do not change to an Enum. See the notes above for an explanation.
    *
    * Check these flags for things such as choosing dev or production server addresses, dumping error stack traces,
    * allowing right-click context menu to appear within the browser, etc.
    */
   static final public boolean IsDevelopmentProfile = true;
   static final public boolean IsTestProfile = false;
   static final public boolean IsProductionProfile = false;

   static final public boolean EnforceSingleInstance = false || IsTestProfile || IsProductionProfile;

   /* Check this flag for things such as whether to disable connection timeouts while stepping through a debug run,
    * as well as allow the Feedbactory pad window to be pushed to the background.
    * There is enough of an important distinction between the development & debug states to keep the flags separate;
    * almost always this should be set to false even during development, and it may sometimes be useful in the test environment.
    */
   static final public boolean IsDebugMode = false && IsDevelopmentProfile;

   static final public Level LogLevel = IsDevelopmentProfile ? Level.ALL : Level.INFO;


   private FeedbactoryClientConstants()
   {
   }
}