
package com.feedbactory.client.core;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


abstract public class ClientUtilities
{
   static final private long AwaitTerminationTimeoutSeconds = 10L;


   private ClientUtilities()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private void handleShutdownAndAwaitTermination(final ExecutorService executorService, final String shutdownContext)
   {
      executorService.shutdown();

      try
      {
         if (! executorService.awaitTermination(AwaitTerminationTimeoutSeconds, TimeUnit.SECONDS))
         {
            final String message = "Executor's remaining tasks are taking a long time to complete. Shutdown called from thread: {0}, calling context: {1}.";
            final Object[] parameters = new String[] {Thread.currentThread().getName(), shutdownContext};
            Logger.getLogger(ClientUtilities.class.getName()).log(Level.WARNING, message, parameters);

            executorService.shutdownNow();

            if (! executorService.awaitTermination(AwaitTerminationTimeoutSeconds, TimeUnit.SECONDS))
               Logger.getLogger(ClientUtilities.class.getName()).warning("Unable to cancel tasks on executor service.");
         }
      }
      catch (final InterruptedException interruptedException)
      {
         /* If the calling thread attempting to shutdown the executor service and wait for its completion also happens to be one of the executor's tasks,
          * it will be interrupted by the call sequence of shutdownNow() followed by the 2nd call to awaitTermination().
          * If this happens, the interrupt flag needs to be restored here.
          * 
          * The ExecutorService JavaDocs suggest making a 2nd call to shutdownNow() in this instance, but I'm not sure about the effects of this
          * since the first call would have completed - the interruption/bailout for this thread occurs during the 2nd call to awaitTermination().
          */
         Thread.currentThread().interrupt();
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public void shutdownAndAwaitTermination(final ExecutorService executor, final String shutdownContext)
   {
      handleShutdownAndAwaitTermination(executor, shutdownContext);
   }
}