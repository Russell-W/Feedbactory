
package com.feedbactory.client.ui.console;


import com.feedbactory.client.ui.browser.BrowserUIManagerService;
import com.feedbactory.client.ui.pad.FeedbactoryPadUIView;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;


final public class BrowserConsoleUIManager
{
   final private FeedbactoryPadUIView feedbactoryPad;

   final private BrowserUIManagerService browserManagerService;

   private BrowserConsole activeBrowserConsole;


   public BrowserConsoleUIManager(final FeedbactoryPadUIView feedbactoryPad, final BrowserUIManagerService browserManagerService)
   {
      this.feedbactoryPad = feedbactoryPad;
      this.browserManagerService = browserManagerService;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleShowBrowserConsole()
   {
      if (activeBrowserConsole == null)
      {
         activeBrowserConsole = new BrowserConsole(this);
         attachCancelKeyBinding();
      }

      feedbactoryPad.showFormComponent(activeBrowserConsole.getDelegate(), false);
   }


   private void attachCancelKeyBinding()
   {
      final InputMap inputMap = activeBrowserConsole.getDelegate().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      final ActionMap actionMap = activeBrowserConsole.getDelegate().getActionMap();

      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelPanel");

      actionMap.put("cancelPanel", new AbstractAction()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            dismissBrowserConsole();
         }
      });
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final Object evaluateJavaScriptInActiveBrowserWindow(final String javaScript)
   {
      return browserManagerService.getActiveBrowserService().evaluateJavascript(javaScript);
   }


   final void dismissBrowserConsole()
   {
      feedbactoryPad.dismissLockingComponent(activeBrowserConsole.getDelegate());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void showBrowserConsole()
   {
      handleShowBrowserConsole();
   }
}