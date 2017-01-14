
package com.feedbactory.client.ui.console;


import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.DefaultFocusTraversalPolicy;
import java.awt.FocusTraversalPolicy;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.GroupLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.LayoutStyle.ComponentPlacement;


final class BrowserConsole
{
   final private BrowserConsoleUIManager uiManager;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private JComponent contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);
   final private JSplitPane contentPanelSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
   final private JTextArea consoleOutputTextArea = new JTextArea();
   final private JScrollPane consoleOutputScrollPane = new JScrollPane(consoleOutputTextArea);
   final private JTextArea consoleInputTextArea = new JTextArea();
   final private JScrollPane consoleInputScrollPane = new JScrollPane(consoleInputTextArea);

   final private JButton executeButton = new JButton();
   final private JButton clearButton = new JButton();
   final private JButton closeButton = new JButton();


   BrowserConsole(final BrowserConsoleUIManager uiManager)
   {
      this.uiManager = uiManager;

      initialise();
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Browser Console");
      builder.setRadialGradientPaint(UIConstants.LighterPanelGradient);
      return new NimbusBorderPanel(builder);
   }


   private void initialise()
   {
      initialiseContentPanel();
      initialiseDelegatePanel();
   }


   private void initialiseContentPanel()
   {
      contentPanel.setBackground(UIConstants.ContentPanelColour);

      initialiseSplitPane();
      initialiseContentPanelLayout();
   }


   private void initialiseSplitPane()
   {
      initialiseConsoleOutputTextArea();
      initialiseConsoleInputTextArea();

      contentPanelSplitPane.setResizeWeight(0.5d);
      contentPanelSplitPane.setTopComponent(consoleOutputScrollPane);
      contentPanelSplitPane.setBottomComponent(consoleInputScrollPane);

      final FocusTraversalPolicy focusTraversalPolicy = new DefaultFocusTraversalPolicy()
      {
         @Override
         final public Component getFirstComponent(final Container aContainer)
         {
            return consoleInputTextArea;
         }
      };

      contentPanelSplitPane.setFocusTraversalPolicy(focusTraversalPolicy);
      contentPanelSplitPane.setFocusTraversalPolicyProvider(true);
   }


   private void initialiseConsoleOutputTextArea()
   {
      consoleOutputTextArea.setFont(Font.decode(Font.MONOSPACED));
      consoleOutputTextArea.setEditable(false);
   }


   private void initialiseConsoleInputTextArea()
   {
      consoleInputTextArea.setFont(Font.decode(Font.MONOSPACED));

      final InputMap inputMap = consoleInputTextArea.getInputMap(JComponent.WHEN_FOCUSED);
      final ActionMap actionMap = consoleInputTextArea.getActionMap();

      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_MASK, true), "executeCommand");

      actionMap.put("executeCommand", new AbstractAction()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleConsoleInputTextAreaExecuteActionPerformed();
         }
      });
   }


   private void initialiseContentPanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(contentPanel);
      contentPanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(contentPanelSplitPane, GroupLayout.PREFERRED_SIZE, 800, GroupLayout.PREFERRED_SIZE)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(contentPanelSplitPane, GroupLayout.PREFERRED_SIZE, 600, GroupLayout.PREFERRED_SIZE)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );
   }


   private void initialiseDelegatePanel()
   {
      initialiseControlButtons();
      initialiseDelegatePanelLayout();
   }


   private void initialiseControlButtons()
   {
      executeButton.setFont(UIConstants.RegularFont);
      executeButton.setText("Execute");
      executeButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleExecuteButtonActionPerformed();
         }
      });

      clearButton.setFont(UIConstants.RegularFont);
      clearButton.setText("Clear");
      clearButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleClearButtonActionPerformed();
         }
      });

      closeButton.setFont(UIConstants.RegularFont);
      closeButton.setText("Close");
      closeButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleCloseButtonActionPerformed();
         }
      });
   }


   private void initialiseDelegatePanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getContentPane());
      delegatePanel.getContentPane().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(contentPanel)
            .addGroup(panelLayout.createSequentialGroup()
               .addGap(0, 0, Integer.MAX_VALUE)
               .addComponent(executeButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addPreferredGap(ComponentPlacement.UNRELATED)
               .addComponent(clearButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addPreferredGap(ComponentPlacement.UNRELATED)
               .addComponent(closeButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addGap(0, 0, Integer.MAX_VALUE)
            )
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(contentPanel)
         .addPreferredGap(ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(executeButton)
            .addComponent(clearButton)
            .addComponent(closeButton)
         )
         .addContainerGap()
      );
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleConsoleInputTextAreaExecuteActionPerformed()
   {
      executeConsoleCommand();
   }


   private void handleExecuteButtonActionPerformed()
   {
      executeConsoleCommand();
   }


   private void handleClearButtonActionPerformed()
   {
      clearConsoleOutput();
   }


   private void handleCloseButtonActionPerformed()
   {
      dismissBrowserConsole();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void executeConsoleCommand()
   {
      try
      {
         final String commandToEvaluate = BrowserConsoleJavaScriptString.library + consoleInputTextArea.getText();
         final Object result = uiManager.evaluateJavaScriptInActiveBrowserWindow(commandToEvaluate);

         writeJavaScriptValue(result, 0, -1);
      }
      catch (final Exception anyException)
      {
         consoleOutputTextArea.append(anyException.getMessage());
         consoleOutputTextArea.append("\n");
      }
   }


   private void writeJavaScriptValue(final Object object, final int indendationLevel, final int arrayIndex)
   {
      if (indendationLevel > 0)
      {
         final char[] indendationArray = new char[indendationLevel * 3];
         Arrays.fill(indendationArray, ' ');
         consoleOutputTextArea.append(new String(indendationArray));

         /* Prevent the possibility of infinite recursion in the case where a nested object refers back to its own structure;
          * Bail out after a maximum number of recursive calls/indentations.
          */
         if (indendationLevel > 5)
         {
            consoleOutputTextArea.append("...\n");
            return;
         }

         if (arrayIndex != -1)
         {
            consoleOutputTextArea.append("[");
            consoleOutputTextArea.append(Integer.toString(arrayIndex));
            consoleOutputTextArea.append("]");
         }
      }

      if (object instanceof Object[])
      {
         consoleOutputTextArea.append("[Array]:\n");
         final Object[] arrayObject = (Object[]) object;

         for (int arrayObjectIndex = 0; arrayObjectIndex < arrayObject.length; arrayObjectIndex ++)
            writeJavaScriptValue(arrayObject[arrayObjectIndex], indendationLevel + 1, arrayObjectIndex);
      }
      else if (object != null)
      {
         if (object instanceof String)
            consoleOutputTextArea.append("[String]: \"");
         else if (object instanceof Boolean)
            consoleOutputTextArea.append("[Boolean]: ");
         else if (object instanceof Double)
            consoleOutputTextArea.append("[Number]: ");
         else
            consoleOutputTextArea.append("[Unknown]: ");

         consoleOutputTextArea.append(object.toString());

         if (object instanceof String)
            consoleOutputTextArea.append("\"");

         consoleOutputTextArea.append("\n");
      }
      else
      {
         consoleOutputTextArea.append("[Null, undefined, or unmappable object]");
         consoleOutputTextArea.append("\n");
      }
   }


   private void clearConsoleOutput()
   {
      consoleOutputTextArea.setText("");
   }


   private void dismissBrowserConsole()
   {
      uiManager.dismissBrowserConsole();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}