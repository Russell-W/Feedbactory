
package com.feedbactory.client.ui.help;


import com.feedbactory.client.ui.browser.BrowserService;
import com.feedbactory.client.ui.browser.BrowserUIManagerService;
import com.feedbactory.client.ui.pad.FeedbactoryPadUIView;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.*;


final public class HelpUIManager
{
   final private FeedbactoryPadUIView feedbactoryPad;

   final private BrowserUIManagerService browserManagerService;

   private JComponent activeHelpComponent;


   public HelpUIManager(final FeedbactoryPadUIView feedbactoryPad, final BrowserUIManagerService browserManagerService)
   {
      this.feedbactoryPad = feedbactoryPad;
      this.browserManagerService = browserManagerService;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void attachCancelKeyBinding(final JComponent component)
   {
      final InputMap inputMap = component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      final ActionMap actionMap = component.getActionMap();

      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelPanel");

      actionMap.put("cancelPanel", new AbstractAction()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            dismissHelpPanel();
         }
      });
   }


   private void handleSwapToHelpIntroductionPanel()
   {
      final HelpIntroductionPanel helpIntroductionPanel = new HelpIntroductionPanel(this);
      attachCancelKeyBinding(helpIntroductionPanel.getDelegate());
      feedbactoryPad.showFormComponent(helpIntroductionPanel.getDelegate(), false);
      feedbactoryPad.dismissLockingComponent(activeHelpComponent);
      activeHelpComponent = helpIntroductionPanel.getDelegate();
   }


   private void handleSwapToFeedbackHelpPanel()
   {
      final FeedbackHelpPanel feedbackHelpPanel = new FeedbackHelpPanel(this);
      attachCancelKeyBinding(feedbackHelpPanel.getDelegate());
      feedbactoryPad.showFormComponent(feedbackHelpPanel.getDelegate(), false);
      feedbactoryPad.dismissLockingComponent(activeHelpComponent);
      activeHelpComponent = feedbackHelpPanel.getDelegate();
   }


   private void handleOpenURL(final String url)
   {
      final BrowserService newBrowserService = browserManagerService.newBrowserService();
      newBrowserService.openURL(url);
      browserManagerService.setActiveBrowserService(newBrowserService);
   }


   private void handleDismissHelpPanel()
   {
      if (activeHelpComponent != null)
      {
         feedbactoryPad.dismissLockingComponent(activeHelpComponent);
         activeHelpComponent = null;
      }
   }


   private void handleShowHelpPanel()
   {
      if (activeHelpComponent == null)
      {
         final HelpIntroductionPanel helpIntroductionPanel = new HelpIntroductionPanel(this);
         activeHelpComponent = helpIntroductionPanel.getDelegate();
         attachCancelKeyBinding(activeHelpComponent);
         feedbactoryPad.showFormComponent(activeHelpComponent, false);
      }
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final void swapToHelpIntroductionPanel()
   {
      handleSwapToHelpIntroductionPanel();
   }


   final void swapToFeedbackHelpPanel()
   {
      handleSwapToFeedbackHelpPanel();
   }


   final void openURL(final String url)
   {
      handleOpenURL(url);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public void showHelpPanel()
   {
      handleShowHelpPanel();
   }


   final public void dismissHelpPanel()
   {
      handleDismissHelpPanel();
   }
}