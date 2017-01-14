
package com.feedbactory.client.launch.core;


enum ConfigurationCheckResult
{
   NoUpdateRequired,
   UpdateRequired,
   SupersededJavaVersion,
   UnsupportedPlatform,
   HeadlessModeNotSupported,
   ConfigurationError;
}