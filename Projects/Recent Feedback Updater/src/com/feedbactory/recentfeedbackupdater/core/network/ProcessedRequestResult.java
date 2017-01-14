/* Memos:
 * - The Feedbactory server's network, IP status, and header statuses have been amalgamated into the one response class here.
 */

package com.feedbactory.recentfeedbackupdater.core.network;


final public class ProcessedRequestResult<V extends Object>
{
   static final private ProcessedRequestResult<Void> OK = new ProcessedRequestResult<>(NetworkRequestStatus.OK);
   static final private ProcessedRequestResult<Void> ServerNotAvailable = new ProcessedRequestResult<>(NetworkRequestStatus.ServerNotAvailable);
   static final private ProcessedRequestResult<Void> IPBlocked = new ProcessedRequestResult<>(NetworkRequestStatus.IPBlocked);
   static final private ProcessedRequestResult<Void> SupersededClientVersion = new ProcessedRequestResult<>(NetworkRequestStatus.SupersededClientVersion);
   static final private ProcessedRequestResult<Void> BadRequest = new ProcessedRequestResult<>(NetworkRequestStatus.BadRequest);
   static final private ProcessedRequestResult<Void> FailedTimeout = new ProcessedRequestResult<>(NetworkRequestStatus.FailedTimeout);
   static final private ProcessedRequestResult<Void> FailedNetworkOther = new ProcessedRequestResult<>(NetworkRequestStatus.FailedNetworkOther);

   final public NetworkRequestStatus requestStatus;
   final public V data;


   public ProcessedRequestResult(final NetworkRequestStatus requestStatus)
   {
      this.requestStatus = requestStatus;
      this.data = null;
   }


   public ProcessedRequestResult(final NetworkRequestStatus requestStatus, final V data)
   {
      this.requestStatus = requestStatus;
      this.data = data;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   /* The static instances are stateless - no data attachment - so the type parameter doesn't apply.
    * Therefore it's safe to cast to any payload type.
    */
   @SuppressWarnings("unchecked")
   static public <E> ProcessedRequestResult<E> resultForNetworkRequestStatus(final NetworkRequestStatus requestStatus)
   {
      switch (requestStatus)
      {
         case OK:
            return (ProcessedRequestResult<E>) OK;
         case ServerNotAvailable:
            return (ProcessedRequestResult<E>) ServerNotAvailable;
         case IPBlocked:
            return (ProcessedRequestResult<E>) IPBlocked;
         case SupersededClientVersion:
            return (ProcessedRequestResult<E>) SupersededClientVersion;
         case BadRequest:
            return (ProcessedRequestResult<E>) BadRequest;
         case FailedTimeout:
            return (ProcessedRequestResult<E>) FailedTimeout;
         case FailedNetworkOther:
            return (ProcessedRequestResult<E>) FailedNetworkOther;
         default:
            throw new AssertionError("Unhandled network request status for processed request result: " + requestStatus);
      }
   }
}