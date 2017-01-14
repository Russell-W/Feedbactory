
package com.feedbactory.recentfeedbackupdater.core.log;


final class SMSHTTPResponse
{
   final int responseCode;
   final String responseMessage;
   final String responseData;


   SMSHTTPResponse(final int responseCode, final String responseMessage, final String responseData)
   {
      this.responseCode = responseCode;
      this.responseMessage = responseMessage;
      this.responseData = responseData;
   }
}