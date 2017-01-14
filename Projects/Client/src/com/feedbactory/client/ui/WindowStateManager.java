
package com.feedbactory.client.ui;


import com.feedbactory.client.core.ClientUtilities;
import com.feedbactory.client.ui.component.SWTFrame;
import com.feedbactory.client.ui.component.SwingNimbusFrame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;


final class WindowStateManager
{
   /* The number of milliseconds time lag between the user switching between Feedbactory windows; if this time is exceeded, it's an indication
    * that the user has switched to a non-Feedbactory window, therefore the remaining Feedbactory windows should then be hidden.
    *
    * Be careful of setting this to smaller values - on slower machines it can actually prevent the user from displaying the Feedbactory pad window,
    * repeatedly hiding it if it's too slow to appear when the user clicks on the browser toolbar.
    */
   static final private long WindowSwitchDelayMilliseconds = 500;

   final private UIManager uiManager;

   final private SWTFrame browserWindow;
   private WindowState browserWindowState;

   final private List<WindowStateMonitor> applicationWindows = new ArrayList<WindowStateMonitor>();

   final private WindowDeactivationCheckTask windowDeactivationTask = new WindowDeactivationCheckTask();


   WindowStateManager(final UIManager uiManager, final SWTFrame browserWindow)
   {
      this.uiManager = uiManager;
      this.browserWindow = browserWindow;

      initialise();
   }


   private void initialise()
   {
      synchronized (windowDeactivationTask)
      {
         browserWindowState = WindowState.Inactive;
      }

      browserWindow.getFrameRootComponent().getDisplay().syncExec(new Runnable()
      {
         @Override
         final public void run()
         {
            browserWindow.addShellListener(new ShellListener()
            {
               @Override
               final public void shellActivated(final ShellEvent shellEvent)
               {
                  handleBrowserShellActivated();
               }


               @Override
               final public void shellDeactivated(final ShellEvent shellEvent)
               {
                  handleBrowserShellDeactivated();
               }


               @Override
               final public void shellDeiconified(final ShellEvent shellEvent)
               {
                  handleBrowserShellDeiconified();
               }


               @Override
               final public void shellIconified(final ShellEvent shellEvent)
               {
                  handleBrowserShellIconified();
               }


               @Override
               final public void shellClosed(final ShellEvent shellEvent)
               {
                  handleBrowserShellClosed(shellEvent);
               }
            });
         }
      });
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private enum WindowState
   {
      Inactive,
      Active,
      Iconified;
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class WindowStateMonitor extends ComponentAdapter implements WindowFocusListener
   {
      private WindowState state;


      private WindowStateMonitor()
      {
         initialise();
      }


      private void initialise()
      {
         synchronized (windowDeactivationTask)
         {
            state = WindowState.Inactive;
         }
      }


      @Override
      final public void windowGainedFocus(final WindowEvent windowEvent)
      {
         handleApplicationWindowGainedFocus(this);
      }


      @Override
      final public void windowLostFocus(final WindowEvent windowEvent)
      {
         handleApplicationWindowLostFocus(this);
      }


      @Override
      final public void componentShown(final ComponentEvent componentEvent)
      {
         handleApplicationWindowShown(this);
      }


      @Override
      final public void componentHidden(final ComponentEvent componentEvent)
      {
         handleApplicationWindowHidden(this);
      }
   }


   /****************************************************************************
    *
    * 
    * 
    ***************************************************************************/


   final private class WindowDeactivationCheckTask implements Runnable
   {
      final private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
      private ScheduledFuture<?> executorTask;


      private WindowDeactivationCheckTask()
      {
         initialise();
      }


      private void initialise()
      {
         executor.setKeepAliveTime(10, TimeUnit.SECONDS);
         executor.allowCoreThreadTimeOut(true);
      }


      @Override
      final public void run()
      {
         Thread.currentThread().setName("WindowStateManager.WindowDeactivationCheckTask");

         // This is run as a separate task, without default exception handling - add some if this does anything more adventurous.
         if (! isApplicationWindowActive())
            uiManager.hideApplicationChildWindows();
      }


      /****************************************************************************
       *
       ***************************************************************************/


      synchronized private void windowDeactivated()
      {
         if ((! executor.isShutdown()) && (browserWindowState != WindowState.Iconified))
         {
            cancelTask();

            executorTask = executor.schedule(this, WindowSwitchDelayMilliseconds, TimeUnit.MILLISECONDS);
         }
      }


      synchronized private void cancelTask()
      {
         if (executorTask != null)
         {
            executorTask.cancel(false);
            executorTask = null;
         }
      }


      private void shutdown()
      {
         /* As per the docs, invoking shutdown on an executor will still allow previously submitted tasks to run.
          * Cancelling the long-delayed window deactivation check task prevents unnecessarily waiting for it to run if it has already been submitted.
          */
         cancelTask();

         /* The run() task doesn't synchronously invoke Swing or SWT tasks, nor is this shutdown() method invoked on
          * the Swing or SWT threads, so there's no chance of deadlock.
          */
         ClientUtilities.shutdownAndAwaitTermination(executor, "WindowStateManager.WindowDeactivationCheckTask");
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private void handleBrowserShellActivated()
   {
      synchronized (windowDeactivationTask)
      {
         browserWindowState = WindowState.Active;
      }
   }


   private void handleBrowserShellDeactivated()
   {
      synchronized (windowDeactivationTask)
      {
         browserWindowState = WindowState.Inactive;
         windowDeactivationTask.windowDeactivated();
      }
   }


   private void handleBrowserShellDeiconified()
   {
      synchronized (windowDeactivationTask)
      {
         browserWindowState = WindowState.Inactive;
      }
   }


   private void handleBrowserShellIconified()
   {
      synchronized (windowDeactivationTask)
      {
         browserWindowState = WindowState.Iconified;
      }

      uiManager.hideApplicationChildWindows();
   }


   private void handleBrowserShellClosed(final ShellEvent shellEvent)
   {
      synchronized (windowDeactivationTask)
      {
         shellEvent.doit = false;
      }

      uiManager.shutdownRequestedByUser();
   }


   private void handleApplicationWindowGainedFocus(final WindowStateMonitor windowStateMonitor)
   {
      synchronized (windowDeactivationTask)
      {
         windowStateMonitor.state = WindowState.Active;
      }
   }


   private void handleApplicationWindowLostFocus(final WindowStateMonitor windowStateMonitor)
   {
      synchronized (windowDeactivationTask)
      {
         windowStateMonitor.state = WindowState.Inactive;
         windowDeactivationTask.windowDeactivated();
      }
   }


   private void handleApplicationWindowShown(final WindowStateMonitor windowStateMonitor)
   {
      synchronized (windowDeactivationTask)
      {
         windowStateMonitor.state = WindowState.Active;
      }
   }


   private void handleApplicationWindowHidden(final WindowStateMonitor windowStateMonitor)
   {
      synchronized (windowDeactivationTask)
      {
         windowStateMonitor.state = WindowState.Inactive;
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleManageWindow(final SwingNimbusFrame window)
   {
      final WindowStateMonitor windowStateMonitor;

      synchronized (windowDeactivationTask)
      {
         windowStateMonitor = new WindowStateMonitor();
         windowStateMonitor.state = window.isActive() ? WindowState.Active : WindowState.Inactive;
      }

      window.addWindowFocusListener(windowStateMonitor);
      window.addComponentListener(windowStateMonitor);

      applicationWindows.add(windowStateMonitor);
   }


   private boolean handleIsApplicationWindowActive()
   {
      synchronized (windowDeactivationTask)
      {
         if (browserWindowState == WindowState.Active)
            return true;

         for (final WindowStateMonitor stateMonitor : applicationWindows)
         {
            if (stateMonitor.state == WindowState.Active)
               return true;
         }

         return false;
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void manageWindow(final SwingNimbusFrame window)
   {
      handleManageWindow(window);
   }


   final boolean isApplicationWindowActive()
   {
      return handleIsApplicationWindowActive();
   }


   final void shutdown()
   {
      windowDeactivationTask.shutdown();
   }
}