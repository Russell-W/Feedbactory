/* Memos:
 * - Code taken almost verbatim from the server's SMSAlertManager class.
 * - This handler allows one message to be sent per time period, eg. 1 day.
 * - This limitation is valuable because:
 *   - An SMS alert system should not be used in the same way as a regular high volume log handler; only severe alerts should be processed.
 *   - If the SMS sending itself fails, a further error will be pushed into the logging system, which might then make the roundtrip back to the SMS alert system.
 *     In this case, the check of lastSMSSendTime should put a stop to any further SMS attempts (and failures).
 */

package com.feedbactory.recentfeedbackupdater.core.log;


import com.feedbactory.shared.FeedbactoryConstants;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.net.ssl.HttpsURLConnection;


final public class SMSAlertHandler extends Handler
{
   static final int SMSMaximumLength = 160;

   static final private String FeedbactorySMSAlertRecipientNumber = "<insert SMS alert phone number>";

   // This is not a regular volume logging mechanism. If an SMS is being sent it means something on the server requires attention ASAP.
   static final private long MinimumTimeBetweenAlertsMilliseconds = TimeUnit.DAYS.toMillis(1);

   static final private String TokenRequestURL = "https://api.telstra.com/v1/oauth/token";
   static final private byte[] GetAuthorisationTokenRequestData = initialiseGetAuthorisationTokenData();
   static final private String SendSMSRequestURL = "https://api.telstra.com/v1/sms/messages";

   volatile private long lastSMSSendTime = FeedbactoryConstants.NoTime;


   static private byte[] initialiseGetAuthorisationTokenData()
   {
      return "client_id=<insert client id>&client_secret=<insert client secret>&grant_type=client_credentials&scope=SMS".getBytes(StandardCharsets.ISO_8859_1);
   }


   public SMSAlertHandler()
   {
      initialise();
   }


   private void initialise()
   {
      setFormatter(new SimpleFormatter());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handlePublish(final LogRecord record)
   {
      if (isLoggable(record))
      {
         if ((lastSMSSendTime == FeedbactoryConstants.NoTime) || (System.currentTimeMillis()- lastSMSSendTime) >= MinimumTimeBetweenAlertsMilliseconds)
         {
            // Set the send time here so that if the SMS send fails there cannot be repeated invocations.
            lastSMSSendTime = System.currentTimeMillis();
            processSendSystemAlertSMS(generateLogRecordSMSMessage(record));
         }
         // else don't log a message as the SMS alert handler (and this method) may be triggered repeatedly.
      }
   }


   private String generateLogRecordSMSMessage(final LogRecord record)
   {
      final StringBuilder messageBuilder = new StringBuilder(SMSMaximumLength);
      messageBuilder.append("Recent feedback updater:\\n");
      messageBuilder.append(record.getLevel());
      messageBuilder.append("\\n");

      messageBuilder.append(record.getSourceClassName());
      messageBuilder.append("\\n");

      if (record.getMessage() != null)
         messageBuilder.append(getFormatter().formatMessage(record));

      if (messageBuilder.length() > SMSMaximumLength)
         messageBuilder.setLength(SMSMaximumLength);

      return messageBuilder.toString();
   }


   private void processSendSystemAlertSMS(final String alertMessage)
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
               if (sendSMSResponse.responseCode != 202)
               {
                  final String message = "Send SMS request failed. Response code: {0}, response message: {1}";
                  Logger.getLogger(getClass().getName()).log(Level.SEVERE, message, new Object[] {oAuthResponse.responseCode, oAuthResponse.responseMessage});
               }
            }
            else
            {
               final String message = "Could not parse SMS OAuth token from JSON response: {0}";
               Logger.getLogger(getClass().getName()).log(Level.SEVERE, message, oAuthResponse.responseData);
            }
         }
         else
         {
            final String message = "SMS alert OAuth token request failed. Response code: {0}, response message: {1}";
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, message, new Object[] {oAuthResponse.responseCode, oAuthResponse.responseMessage});
         }
      }
      catch (final Exception anyException)
      {
         final String message = "SMS alert failed.";
         Logger.getLogger(getClass().getName()).log(Level.SEVERE, message, anyException);
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


   @Override
   final public void publish(final LogRecord record)
   {
      handlePublish(record);
   }


   @Override
   final public void flush()
   {
      // NOP
   }


   @Override
   final public void close() throws SecurityException
   {
      // NOP
   }


   final public void resetLastSMSSendTime()
   {
      lastSMSSendTime = FeedbactoryConstants.NoTime;
   }
}