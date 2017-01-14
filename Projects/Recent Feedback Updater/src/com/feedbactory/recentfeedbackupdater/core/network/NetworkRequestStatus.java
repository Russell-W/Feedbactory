
package com.feedbactory.recentfeedbackupdater.core.network;


public enum NetworkRequestStatus
{
   OK,
   ServerNotAvailable,
   IPBlocked,
   SupersededClientVersion,
   BadRequest,
   FailedTimeout,
   FailedNetworkOther;
}