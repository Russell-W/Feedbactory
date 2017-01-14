/* Memos:
 * - As with the execution profile booleans in the client, the corresponding booleans have been moved from the main Launcher class to here to allow them to
 *   be obfuscated.
 *
 * - The execution profile booleans (development/test/production) are implemented in this simple yet unfortunately clumsy way to allow the compiler to remove
 *   development and test environment code and constants from compiled code where possible, eg. application server and email server addresses. See ClientNetworkConstants
 *   for an example. Unfortunately the compiler will not optimise on static final Enums. static int works but is not very informative when used from other classes,
 *   eg. FeedbactoryClientConstants.executionProfile == 1 (??).
 */

package com.feedbactory.client.launch.core;


final public class LauncherConstants
{
   static final public String DisplayableVersionID = "2015.11.26.1";
   static final public long VersionID = 1448514681502L;

   /* Execution profile switches, one and only one must be true. Do not change to an Enum. See the notes above for an explanation.
    *
    * Check these flags for things such as choosing dev or production server addresses, and dumping error stack traces, etc.
    */
   static final public boolean IsDevelopmentProfile = true;
   static final public boolean IsTestProfile = false;
   static final public boolean IsProductionProfile = false;

   /* Check this flag for things such as whether to disable connection timeouts or display the output of running the client process
    * while stepping through a debug run.
    * There is enough of an important distinction between the development & debug states to keep the flags separate;
    * almost always this should be set to false even during development, and it may sometimes be useful in the test environment.
    */
   static final public boolean IsDebugMode = false && IsDevelopmentProfile;


   private LauncherConstants()
   {
   }
}