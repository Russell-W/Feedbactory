/* Memos:
 * - Class to prevent overly frequent calls to System.currentTimeMillis(), which can apparently be expensive. For most purposes I don't need a fine grained level of
 *   accuracy for the current time.
 *
 * - If later down the track I find that it's a needless optimisation, I can simply ditch the thread and delegate the getCurrentTimeMilliseconds() call to
 *   System.currentTimeMillis().
 *
 * - The updating thread is a daemon thread so that the VM can shut down without explicitly shutting down this task.
 */

package com.feedbactory.server.core;


import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;


abstract public class TimeCache
{
   static final private long RefreshPeriodMilliseconds = 1000;

   volatile static private long currentTimeMillis = System.currentTimeMillis();
 

   static
   {
      new TimeRefreshTask().start();
   }


   private TimeCache()
   {
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final private class TimeRefreshTask implements ThreadFactory, Runnable
   {
      final private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, this);


      @Override
      final public Thread newThread(final Runnable runnable)
      {
         final Thread newThread = new Thread(runnable, "Time cache update task");
         newThread.setDaemon(true);

         return newThread;
      }


      @Override
      final public void run()
      {
         refreshTime();
      }


      /****************************************************************************
       * 
       ***************************************************************************/


      private void start()
      {
         executor.scheduleAtFixedRate(this, RefreshPeriodMilliseconds, RefreshPeriodMilliseconds, TimeUnit.MILLISECONDS);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private void refreshTime()
   {
      currentTimeMillis = System.currentTimeMillis();
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static public long getCurrentTimeMilliseconds()
   {
      return currentTimeMillis;
   }
}