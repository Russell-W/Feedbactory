
package com.feedbactory.server.core.log;


import com.feedbactory.server.core.TimeCache;
import com.feedbactory.shared.FeedbactoryConstants;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;


final class SMSAlertManager
{
   static final int SMSMaximumLength = 160;

   static final private String FeedbactorySMSAlertRecipientNumber = "<insert SMS alert phone number>";

   // This is not a regular volume logging mechanism. If an SMS is being sent it means something on the server requires attention ASAP.
   static final private long MinimumTimeBetweenAlertsMilliseconds = TimeUnit.DAYS.toMillis(1);

   static final private String TokenRequestURL = "https://api.telstra.com/v1/oauth/token";
   static final private byte[] GetAuthorisationTokenRequestData = initialiseGetAuthorisationTokenData();
   static final private String SendSMSRequestURL = "https://api.telstra.com/v1/sms/messages";

   volatile private long smsLastSentTime = FeedbactoryConstants.NoTime;


   static private byte[] initialiseGetAuthorisationTokenData()
   {
      return "client_id=<insert client id>&client_secret=<insert client secret>&grant_type=client_credentials&scope=SMS".getBytes(StandardCharsets.ISO_8859_1);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSendSystemAlertSMS(final String alertMessage, final boolean setSMSSendTime)
   {
      if ((smsLastSentTime == FeedbactoryConstants.NoTime) || (TimeCache.getCurrentTimeMilliseconds() - smsLastSentTime) >= MinimumTimeBetweenAlertsMilliseconds)
         processSendSystemAlertSMS(alertMessage, setSMSSendTime);
      else
      {
         final String message = "Cannot send another SMS until the wait period has expired since the last SMS.";
         FeedbactoryLogger.reportSMSFailed(new SystemEvent(SystemLogLevel.Debug, getClass(), message), alertMessage);
      }
   }


   private void processSendSystemAlertSMS(final String alertMessage, final boolean setSMSSendTime)
   {
      try
      {
         final SMSHTTPResponse oAuthResponse = sendOAuthTokenRequest();
         if (oAuthResponse.responseCode == 200)
         {
            final String oAuthToken = getSMSRequestToken(oAuthResponse.responseData);
            if (oAuthToken != null)
            {
               final SMSHTTPResponse sendSMSResponse = sendSMSRequest(oAuthToken, alertMessage);
               if (sendSMSResponse.responseCode == 202)
               {
                  if (setSMSSendTime)
                     smsLastSentTime = TimeCache.getCurrentTimeMilliseconds();
               }
               else
               {
                  final String message = "Send SMS request failed. Response code: " + oAuthResponse.responseCode + ", response message: " + oAuthResponse.responseMessage;
                  final SystemEvent systemEvent = new SystemEvent(SystemLogLevel.ApplicationError, getClass(), message);
                  FeedbactoryLogger.reportSMSFailed(systemEvent, alertMessage);
               }
            }
            else
            {
               final String message = "Could not parse SMS OAuth token from JSON response: " + oAuthResponse.responseData;
               FeedbactoryLogger.reportSMSFailed(new SystemEvent(SystemLogLevel.ApplicationError, getClass(), message), alertMessage);
            }
         }
         else
         {
            final String message = "SMS alert OAuth token request failed. Response code: " + oAuthResponse.responseCode + ", response message: " + oAuthResponse.responseMessage;
            final SystemEvent systemEvent = new SystemEvent(SystemLogLevel.ApplicationError, getClass(), message);
            FeedbactoryLogger.reportSMSFailed(systemEvent, alertMessage);
         }
      }
      catch (final Exception anyException)
      {
         final String message = "SMS alert failed.";
         FeedbactoryLogger.reportSMSFailed(new SystemEvent(SystemLogLevel.ApplicationError, getClass(), message, anyException), alertMessage);
      }
   }


   private SMSHTTPResponse sendOAuthTokenRequest() throws IOException
   {
      URL url = new URL(TokenRequestURL);
      final HttpsURLConnection httpsConnection = (HttpsURLConnection) url.openConnection();
      setBaseRequestProperties(httpsConnection);
      httpsConnection.setRequestMethod("POST");
      httpsConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      httpsConnection.setRequestProperty("Content-Length", Integer.toString(GetAuthorisationTokenRequestData.length));

      httpsConnection.setDoOutput(true);
      httpsConnection.getOutputStream().write(GetAuthorisationTokenRequestData);
      httpsConnection.getOutputStream().flush();
      httpsConnection.getOutputStream().close();

      return readHttpResponse(httpsConnection, 200);
   }


   private void setBaseRequestProperties(final HttpsURLConnection httpsConnection) throws IOException
   {
      httpsConnection.setRequestProperty("Accept", "application/json");
      httpsConnection.setRequestProperty("Accept-Language", "en-AU");
      httpsConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; Win64; x64; Trident/6.0)");
      httpsConnection.setRequestProperty("Host", "api.telstra.com");
      httpsConnection.setRequestProperty("DNT", "1");
   }


   static SMSHTTPResponse readHttpResponse(final HttpURLConnection httpConnection, final int successCode) throws IOException
   {
      if (httpConnection.getResponseCode() == successCode)
      {
         try
         (
            final InputStream socketInput = httpConnection.getInputStream();
         )
         {
            final byte[] responseReadBufferArray = new byte[200];
            int bytesRead;

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(200);

            while ((bytesRead = socketInput.read(responseReadBufferArray)) != -1)
               byteArrayOutputStream.write(responseReadBufferArray, 0, bytesRead);

            byteArrayOutputStream.close();

            final String responseData = byteArrayOutputStream.toString(StandardCharsets.ISO_8859_1.name());
            return new SMSHTTPResponse(successCode, httpConnection.getResponseMessage(), responseData);
         }
         finally
         {
            httpConnection.disconnect();
         }
      }
      else
         return new SMSHTTPResponse(httpConnection.getResponseCode(), httpConnection.getResponseMessage(), null);
   }


   private String getSMSRequestToken(final String tokenJSONResponse)
   {
      /* Sample format: { "access_token": "ARrGOKZILyOiLesBGaxZ3m0XBuOY", "expires_in": "3599" }
       * I could start up Java's Rhino JavaScript engine here to evaluate the JSON response, but that seems extremely heavy handed; a basic text parse should suffice.
       * The tokenJSONResponse should be case sensitive including the keys, so lowercasing it to search for the 'access_token' substring should be unnecessary.
       */
      final int accessTokenHeaderStartIndex = tokenJSONResponse.indexOf("access_token");
      if (accessTokenHeaderStartIndex != -1)
      {
         int quoteIndex = tokenJSONResponse.indexOf('"', accessTokenHeaderStartIndex + 12);
         if (quoteIndex != -1)
         {
            quoteIndex ++;
            quoteIndex = tokenJSONResponse.indexOf('"', quoteIndex);
            if (quoteIndex != -1)
            {
               // The 2nd double quote after 'access_token' should mark the start of the actual token.
               final int accessTokenStartIndex = quoteIndex + 1;
               final int accessTokenEndIndex = tokenJSONResponse.indexOf('"', accessTokenStartIndex);
               if (accessTokenEndIndex != -1)
                  return tokenJSONResponse.substring(accessTokenStartIndex, accessTokenEndIndex).trim();
            }
         }
      }

      return null;
   }


   private SMSHTTPResponse sendSMSRequest(final String authorisationToken, final String alertMessage) throws IOException
   {
      final byte[] smsRequestJSON = generateSMSRequestJSON(alertMessage);

      URL url = new URL(SendSMSRequestURL);
      final HttpsURLConnection httpsConnection = (HttpsURLConnection) url.openConnection();
      setBaseRequestProperties(httpsConnection);
      httpsConnection.setRequestMethod("GET");
      httpsConnection.setRequestProperty("Content-Type", "application/json");
      httpsConnection.setRequestProperty("Content-Length", Integer.toString(smsRequestJSON.length));
      httpsConnection.setRequestProperty("Authorization", "Bearer " + authorisationToken);

      httpsConnection.setDoOutput(true);
      httpsConnection.getOutputStream().write(smsRequestJSON);
      httpsConnection.getOutputStream().flush();
      httpsConnection.getOutputStream().close();

      return readHttpResponse(httpsConnection, 202);
   }


   private byte[] generateSMSRequestJSON(final String message)
   {
      final String sanitisedMessage = message.replace("\"", "'");

      final StringBuilder stringBuilder = new StringBuilder(200);
      stringBuilder.append("{\"to\": \"");
      stringBuilder.append(FeedbactorySMSAlertRecipientNumber);
      stringBuilder.append("\", \"body\": \"");
      stringBuilder.append(sanitisedMessage);
      stringBuilder.append("\"}");

      // ISO_8859_1 encoding ensures that there will always be 1 byte used per message character.
      return stringBuilder.toString().getBytes(StandardCharsets.ISO_8859_1);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final long getSMSLastSentTime()
   {
      return smsLastSentTime;
   }


   final void resetSMSLastSentTime()
   {
      smsLastSentTime = FeedbactoryConstants.NoTime;
   }


   final void sendSystemAlertSMS(final String alertMessage, final boolean setSMSSendTime)
   {
      handleSendSystemAlertSMS(alertMessage, setSMSSendTime);
   }
}