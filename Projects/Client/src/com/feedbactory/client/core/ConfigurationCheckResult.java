
package com.feedbactory.client.core;


public enum ConfigurationCheckResult
{
   OK,
   LauncherUpdateRequired,
   InstanceActive,
   HeadlessModeNotSupported,
   SupersededJavaVersion,
   ConfigurationError;
}