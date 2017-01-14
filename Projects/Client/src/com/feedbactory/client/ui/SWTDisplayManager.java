
package com.feedbactory.client.ui;


import com.feedbactory.client.core.FeedbactoryClientConstants;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.swt.widgets.Display;


final public class SWTDisplayManager
{
   final private UIManager uiManager;

   private Display SWTDisplay;

   /* Using a flag to indicate the shutdown time for the SWT read & dispatch thread rather than use applicationFrame.isDisposed() means that
    * I can reasonably cleanly separate the initialisation of the read & dispatch thread from the application frame.
    */
   private boolean keepSWTDispatchThreadAlive = true;


   SWTDisplayManager(final UIManager uiManager)
   {
      this.uiManager = uiManager;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private Display handleCreateDisplay()
   {
      // Without explicitly setting this on Mac OS X, the app name will default to 'SWT'.
      Display.setAppName(UIConstants.ApplicationTitle);

      SWTDisplay = new Display();
      SWTDisplay.setWarnings(FeedbactoryClientConstants.IsDevelopmentProfile);
      return SWTDisplay;
   }


   private void handleExecuteReadAndDispatch()
   {
      Thread.currentThread().setName("SWT Display Thread");

      try
      {
         while (keepSWTDispatchThreadAlive)
         {
            try
            {
               handleSWTReadAndDispatchLoop();
            }
            catch (final Exception anyException)
            {
               Logger.getLogger(getClass().getName()).log(Level.SEVERE, "SWT read & dispatch handler exception.", anyException);
               uiManager.reportUncaughtException(SWTDisplayManager.class, "SWT read & dispatch handler exception.", Thread.currentThread(), anyException);
            }
         }
      }
      finally
      {
         SWTDisplay.dispose();
      }
   }


   private void handleSWTReadAndDispatchLoop()
   {
      // Loop without embedded try/catch exception handling. It may or may not be more efficient.
      while (keepSWTDispatchThreadAlive)
      {
         if (! SWTDisplay.readAndDispatch())
            SWTDisplay.sleep();
      }
   }


   private void handleDispose()
   {
      /* To guarantee thread safety of the SWTDisplay variable, the initialisation of the variable must 'happen-before' the
       * invocation of this method, eg. by a new shutdown thread run by an ExecutorService.
       */
      if ((SWTDisplay != null) && (! SWTDisplay.isDisposed()))
      {
         SWTDisplay.syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               keepSWTDispatchThreadAlive = false;
            }
         });
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final Display createDisplay()
   {
      return handleCreateDisplay();
   }


   final void executeReadAndDispatch()
   {
      handleExecuteReadAndDispatch();
   }


   final void dispose()
   {
      handleDispose();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public Display getDisplay()
   {
      return SWTDisplay;
   }
}