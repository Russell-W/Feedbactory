/* Memos:
 * - This class provides a mechanism to limit a queue of concurrent requests, cancelling the oldest pending request when a new request overflows the queue.
 *
 * - Using a separate queue to the Executor's provided queue, as the docs mention that it's ill-advised for the caller to perform any operations directly on
 *   the Executor's queue; once created, the Executor owns it.
 */

package com.feedbactory.client.ui.component;


import com.feedbactory.client.core.ClientUtilities;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


final public class RegulatedQueueExecutorService<T>
{
   final private ThreadPoolExecutor executor;
   final private List<RegulatedTask> queuedRequests = new LinkedList<RegulatedTask>();
   final private int regulatedQueueSize;


   public RegulatedQueueExecutorService(final int corePoolSize, final long timeoutMilliseconds, final int regulatedQueueSize)
   {
      if (corePoolSize > regulatedQueueSize)
         throw new IllegalArgumentException("Thread pool size cannot exceed task queue size.");

      executor = new ThreadPoolExecutor(corePoolSize, corePoolSize, timeoutMilliseconds, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
      if (timeoutMilliseconds > 0L)
         executor.allowCoreThreadTimeOut(true);

      this.regulatedQueueSize = regulatedQueueSize;
   }



   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class RegulatedTask extends FutureTask<T>
   {
      final private RegulatedQueueExecutorServiceConsumer<T> consumer;


      private RegulatedTask(final Callable<T> task, final RegulatedQueueExecutorServiceConsumer<T> consumer)
      {
         super(task);
         this.consumer = consumer;
      }


      @Override
      final protected void done()
      {
         if (! isCancelled())
            requestCompleted(this);
         else
            requestCancelled(this);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static public interface RegulatedQueueExecutorServiceConsumer<T>
   {
      public void requestCompleted(final T result, final Throwable exception);
      public void requestCancelled();
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void requestCompleted(final RegulatedTask regulatedTask)
   {
      synchronized (queuedRequests)
      {
         queuedRequests.remove(regulatedTask);
      }

      try
      {
         final T result = regulatedTask.get();
         regulatedTask.consumer.requestCompleted(result, null);
      }
      catch (final InterruptedException interruptedException)
      {
         regulatedTask.consumer.requestCompleted(null, interruptedException);
      }
      catch (final ExecutionException executionException)
      {
         regulatedTask.consumer.requestCompleted(null, executionException.getCause());
      }
   }


   private void requestCancelled(final RegulatedTask regulatedTask)
   {
      synchronized (queuedRequests)
      {
         queuedRequests.remove(regulatedTask);
      }

      regulatedTask.consumer.requestCancelled();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleExecute(final Callable<T> callable, final RegulatedQueueExecutorServiceConsumer<T> consumer)
   {
      synchronized (queuedRequests)
      {
         final RegulatedTask regulatedTask = new RegulatedTask(callable, consumer);

         /* Need to be aware that when execute() is called with a wrapped Runnable in a FutureTask as it is here, any uncaught exceptions
          * during the run() method will not be automatically reported to the default uncaught exception handler. This is contrary to
          * the behaviour of execute() on a standalone Runnable. Exceptions will instead be attached to the FutureTask and thrown at
          * the time of calling get() to retrieve the result.
          *
          * If the service has been shutdown, a RejectedExecutionException will be thrown; let the caller decide whether to first check for shutdown
          * before calling this method and if it not, how to handle such exceptions.
          */
         executor.execute(regulatedTask);

         queuedRequests.add(regulatedTask);

         if (queuedRequests.size() > regulatedQueueSize)
         {
            // Cancel the oldest request and bail out.
            final Iterator<RegulatedTask> activeTaskIterator = queuedRequests.iterator();
            RegulatedTask queuedRegulatedTask;

            do
            {
               queuedRegulatedTask = activeTaskIterator.next();

               /* Depending on the sequencing of the callbacks of finishing requests to requestCompleted(), there's a slim possibility
                * that all of the requests are either still in progress and cannot be cancelled, or have finished
                * but will not yet have been removed from the queued requests. In either case the cancel() attempt below will
                * return false and the iterator will move onto the next request. This could lead to the possibility of the RegulatedTask
                * that has just added being cancelled! So, perform an extra check to ensure that this doesn't happen.
                */
               if ((queuedRegulatedTask != regulatedTask) && queuedRegulatedTask.cancel(false))
               {
                  /* Successful cancellation of the request will result in the RegulatedTask's done() method to be called,
                   * and the request to be removed from the queued requests collection. This method needs to bail out without
                   * checking/using the iterator to avoid a ConcurrentModificationException.
                   */
                  break;
               }
            }
            while (activeTaskIterator.hasNext());
         }
      }
   }


   private void handleShutdown(final boolean cancelQueuedTasks)
   {
      if (cancelQueuedTasks)
      {
         final List<RegulatedTask> queuedRequestsCopy;

         synchronized (queuedRequests)
         {
            // Dump the active and queued tasks into a new list to avoid a ConcurrentModificationException when cancelling.
            queuedRequestsCopy = new ArrayList<RegulatedTask>(queuedRequests);
         }

         // Cancel the tasks that have not yet started; already executing tasks will continue.
         for (final RegulatedTask task : queuedRequestsCopy)
            task.cancel(false);
      }

      ClientUtilities.shutdownAndAwaitTermination(executor, "RegulatedQueueExecutorService.RegulatedTask");
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void execute(final Callable<T> callable, final RegulatedQueueExecutorServiceConsumer<T> consumer)
   {
      handleExecute(callable, consumer);
   }


   final public boolean isShutdown()
   {
      return executor.isShutdown();
   }


   final public void shutdown(final boolean cancelQueuedTasks)
   {
      handleShutdown(cancelQueuedTasks);
   }
}