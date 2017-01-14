
package com.feedbactory.client.core.network;


import com.feedbactory.shared.network.BasicOperationStatus;


final public class ProcessedRequestBasicResponse extends ProcessedRequestResult<BasicOperationStatus>
{
   static final public ProcessedRequestBasicResponse Success = new ProcessedRequestBasicResponse(NetworkRequestStatus.OK, BasicOperationStatus.OK);
   static final public ProcessedRequestBasicResponse Failed = new ProcessedRequestBasicResponse(NetworkRequestStatus.OK, BasicOperationStatus.Failed);
   static final public ProcessedRequestBasicResponse FailedTimeout = new ProcessedRequestBasicResponse(NetworkRequestStatus.FailedTimeout);
   static final public ProcessedRequestBasicResponse FailedNetworkOther = new ProcessedRequestBasicResponse(NetworkRequestStatus.FailedNetworkOther);
   static final public ProcessedRequestBasicResponse Consumed = new ProcessedRequestBasicResponse(NetworkRequestStatus.Consumed);


   public ProcessedRequestBasicResponse(final NetworkRequestStatus networkStatus)
   {
      super(networkStatus, null);
   }


   public ProcessedRequestBasicResponse(final NetworkRequestStatus networkStatus, final BasicOperationStatus operationStatus)
   {
      super(networkStatus, operationStatus);
   }
}