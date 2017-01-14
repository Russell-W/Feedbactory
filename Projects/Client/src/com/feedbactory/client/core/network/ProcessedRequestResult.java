
package com.feedbactory.client.core.network;


public class ProcessedRequestResult<V extends Object>
{
   static final private ProcessedRequestResult<Void> OK = new ProcessedRequestResult<Void>(NetworkRequestStatus.OK);
   static final private ProcessedRequestResult<Void> Consumed = new ProcessedRequestResult<Void>(NetworkRequestStatus.Consumed);
   static final private ProcessedRequestResult<Void> FailedTimeout = new ProcessedRequestResult<Void>(NetworkRequestStatus.FailedTimeout);
   static final private ProcessedRequestResult<Void> FailedNetworkOther = new ProcessedRequestResult<Void>(NetworkRequestStatus.FailedNetworkOther);

   final public NetworkRequestStatus requestStatus;
   final public V data;


   public ProcessedRequestResult(final NetworkRequestStatus requestStatus)
   {
      this.requestStatus = requestStatus;
      data = null;
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
         case Consumed:
            return (ProcessedRequestResult<E>) Consumed;
         case FailedTimeout:
            return (ProcessedRequestResult<E>) FailedTimeout;
         case FailedNetworkOther:
            return (ProcessedRequestResult<E>) FailedNetworkOther;
         default:
            throw new AssertionError("Unhandled network request status for processed request result: " + requestStatus);
      }
   }
}