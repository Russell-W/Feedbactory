
package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIConstants;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;


final public class MessageDialog
{
   final private SwingNimbusFrame delegateFrame = new SwingNimbusFrame();

   final private JComponent borderPanel = new JPanel(null);
   final private JComponent contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private PresetOptionConfiguration presetOptionConfiguration;
   final private JComponent[] inputComponents;

   final private List<ActionListener> actionListeners = new ArrayList<ActionListener>();


   public MessageDialog(final Builder builder)
   {
      /* Validate against copied fields to protect against client mutation during construction.
       * Arrays are cloned for a deep copy, and in doing so null arrays will produce an exception even before validation.
       */
      final Builder builderCopy = new Builder(builder);

      validate(builderCopy);

      presetOptionConfiguration = builderCopy.presetOptionConfiguration;

      if (builderCopy.inputComponents.length > 0)
         inputComponents = builderCopy.inputComponents;
      else if (builderCopy.buttonOptions.length > 0)
         inputComponents = new JButton[builderCopy.buttonOptions.length];
      else
         inputComponents = new JButton[presetOptionConfiguration.options.length];

      initialise(builderCopy);
   }


   private void validate(final Builder builder)
   {
      if (builder.messageType == null)
         throw new IllegalArgumentException("Dialog message type cannot be null.");
      else if (builder.presetOptionConfiguration == null)
         throw new IllegalArgumentException("Dialog options cannot be null.");
   }


   private void initialise(final Builder builder)
   {
      final JLabel iconLabel = initialiseIconLabel(builder.messageType);
      final JLabel[] messageLabels = initialiseMessageLabels(builder.messages);

      if (builder.inputComponents.length == 0)
      {
         if (builder.buttonOptions.length == 0)
            initialisePresetOptionButtons();
         else
            initialiseUserSpecifiedOptionButtons(builder.buttonOptions);
      }

      initialiseContentPanelLayout(iconLabel, messageLabels);

      initialiseBorderPanel();

      initialiseDelegateFrame(builder.borderTitle);
   }


   private JLabel initialiseIconLabel(final MessageType messageType)
   {
      switch (messageType)
      {
         case Plain:
            return null;

         case Information:
            return new JLabel(UIConstants.InformationIconMedium);

         case Question:
            return new JLabel(UIConstants.QuestionIconMedium);

         case Warning:
            return new JLabel(UIConstants.WarningIconMedium);

         case Error:
            return new JLabel(UIConstants.ErrorIconMedium);

         default:
            return null;
      }
   }


   private JLabel[] initialiseMessageLabels(final String[] messages)
   {
      final JLabel[] messageLabels = new JLabel[messages.length];

      for (int messageIndex = 0; messageIndex < messages.length; messageIndex ++)
      {
         messageLabels[messageIndex] = new JLabel();

         messageLabels[messageIndex].setFont(UIConstants.RegularFont);
         messageLabels[messageIndex].setText(messages[messageIndex]);
      }

      return messageLabels;
   }


   private void initialisePresetOptionButtons()
   {
      for (int optionSelectionIndex = 0; optionSelectionIndex < presetOptionConfiguration.options.length; optionSelectionIndex ++)
         initialiseOptionButton(optionSelectionIndex, presetOptionConfiguration.options[optionSelectionIndex], presetOptionConfiguration.options[optionSelectionIndex].displayLabel);
   }


   private void initialiseUserSpecifiedOptionButtons(final String[] options)
   {
      for (int optionSelectionIndex = 0; optionSelectionIndex < options.length; optionSelectionIndex ++)
         initialiseOptionButton(optionSelectionIndex, null, options[optionSelectionIndex]);
   }


   private void initialiseOptionButton(final int optionSelectionIndex, final PresetOptionSelection optionSelection, final String optionSelectionLabel)
   {
      final JButton button = new JButton();
      
      button.setFont(UIConstants.RegularFont);
      button.setText(optionSelectionLabel);

      button.addActionListener(new java.awt.event.ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            /* Iterate over a copy of the collection to prevent possible concurrent modification exceptions where firing the action may
             * result in callbacks to add or remove listeners.
             */
            for (final ActionListener actionListener : new ArrayList<ActionListener>(actionListeners))
               actionListener.actionPerformed(MessageDialog.this, optionSelection, optionSelectionIndex);
         }
      });

      inputComponents[optionSelectionIndex] = button;
   }


   private void initialiseContentPanelLayout(final JLabel iconLabel, final JLabel[] messageLabels)
   {
      final GroupLayout panelLayout = new GroupLayout(contentPanel);
      contentPanel.setLayout(panelLayout);

      final ParallelGroup outerHorizontalParallelGroup = panelLayout.createParallelGroup(Alignment.CENTER);
      final SequentialGroup outerVerticalSequentialGroup = panelLayout.createSequentialGroup();

      outerVerticalSequentialGroup.addGap(UIConstants.MediumContainerGapSize);

      initialiseMessageLabelLayout(panelLayout, outerHorizontalParallelGroup, outerVerticalSequentialGroup, iconLabel, messageLabels);

      // If there are no message labels (ie. buttons only), don't add a vertical gap.
      final boolean requiresGapAfterLabels = ((iconLabel != null) || (messageLabels.length > 0));
      initialiseInputComponents(panelLayout, outerHorizontalParallelGroup, outerVerticalSequentialGroup, requiresGapAfterLabels);

      outerVerticalSequentialGroup.addGap(UIConstants.MediumContainerGapSize);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addGap(UIConstants.MediumContainerGapSize)
         .addGroup(outerHorizontalParallelGroup)
         .addGap(UIConstants.MediumContainerGapSize)
      );

      panelLayout.setVerticalGroup(outerVerticalSequentialGroup);
   }


   private void initialiseMessageLabelLayout(final GroupLayout panelLayout, final ParallelGroup parentHorizontalParallelGroup, final SequentialGroup parentVerticalSequentialGroup,
                                             final JLabel iconLabel, final JLabel[] messageLabels)
   {
      final ParallelGroup horizontalGroup = panelLayout.createParallelGroup();
      ParallelGroup horizontalLabelGroup = horizontalGroup;
      final SequentialGroup verticalGroup = panelLayout.createSequentialGroup();

      if (iconLabel != null)
      {
         if (messageLabels.length > 0)
         {
            horizontalLabelGroup = panelLayout.createParallelGroup();
            horizontalLabelGroup.addComponent(messageLabels[0]);

            horizontalGroup.addGroup(panelLayout.createSequentialGroup()
               .addComponent(iconLabel)
               .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
               .addGroup(horizontalLabelGroup)
            );

            verticalGroup.addGroup(panelLayout.createParallelGroup(Alignment.TRAILING)
               .addComponent(iconLabel)
               .addComponent(messageLabels[0])
            );
         }
         else
         {
            horizontalLabelGroup.addComponent(iconLabel);
            verticalGroup.addComponent(iconLabel);
         }
      }
      else if (messageLabels.length > 0)
      {
         horizontalLabelGroup.addComponent(messageLabels[0]);
         verticalGroup.addComponent(messageLabels[0]);
      }

      for (int messageLabelIndex = 1; messageLabelIndex < messageLabels.length; messageLabelIndex ++)
      {
         horizontalLabelGroup.addComponent(messageLabels[messageLabelIndex]);

         verticalGroup.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED);
         verticalGroup.addComponent(messageLabels[messageLabelIndex]);
      }

      parentHorizontalParallelGroup.addGroup(horizontalGroup);
      parentVerticalSequentialGroup.addGroup(verticalGroup);
   }


   private void initialiseInputComponents(final GroupLayout panelLayout, final ParallelGroup parentHorizontalParallelGroup, final SequentialGroup parentVerticalSequentialGroup,
                                          final boolean requiresGapAfterContentComponents)
   {
      if (inputComponents.length > 0)
      {
         final SequentialGroup inputComponentHorizontalGroup = panelLayout.createSequentialGroup();
         final ParallelGroup inputComponentVerticalGroup = panelLayout.createParallelGroup();

         for (int inputComponentIndex = 0; inputComponentIndex < inputComponents.length; inputComponentIndex ++)
         {
            if (inputComponents[inputComponentIndex] instanceof AbstractButton)
               inputComponentHorizontalGroup.addComponent(inputComponents[inputComponentIndex], UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE);
            else
               inputComponentHorizontalGroup.addComponent(inputComponents[inputComponentIndex], GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);

            // Adding a component gap after the final button will mess up the container gap on the right side.
            if (inputComponentIndex != (inputComponents.length - 1))
               inputComponentHorizontalGroup.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED);

            inputComponentVerticalGroup.addComponent(inputComponents[inputComponentIndex]);
         }

         parentHorizontalParallelGroup.addGroup(inputComponentHorizontalGroup);

         // Cater for the case of buttons only - no top gap.
         if (requiresGapAfterContentComponents)
            parentVerticalSequentialGroup.addGap(UIConstants.MediumContainerGapSize);

         parentVerticalSequentialGroup.addGroup(inputComponentVerticalGroup);
      }
   }


   private void initialiseBorderPanel()
   {
      borderPanel.setBackground(UIConstants.LighterPanelColour);

      final GroupLayout panelLayout = new GroupLayout(borderPanel);
      borderPanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(contentPanel)
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(contentPanel)
         .addContainerGap()
      );
   }


   private void initialiseDelegateFrame(final String frameTitle)
   {
      delegateFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      delegateFrame.setWindowDecorationStyle(JRootPane.NONE);
      delegateFrame.setResizable(false);
      delegateFrame.setFrameTitle(frameTitle, true);

      try
      {
         final BufferedImage frameIconImage = ImageIO.read(getClass().getResourceAsStream(UIConstants.ApplicationIconLargePath));
         delegateFrame.setFrameIcon(frameIconImage, true);
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }

      delegateFrame.setContent(borderPanel);
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final public class Builder
   {
      private String borderTitle;
      private MessageType messageType = MessageType.Plain;
      private String[] messages = new String[0];
      private PresetOptionConfiguration presetOptionConfiguration = PresetOptionConfiguration.None;
      private String[] buttonOptions = new String[0];
      private JComponent[] inputComponents = new JComponent[0];


      public Builder()
      {
      }


      public Builder(final MessageType messageType, final String[] messages)
      {
         this.messageType = messageType;
         this.messages = messages;
      }


      public Builder(final MessageType messageType, final String[] messages, final PresetOptionConfiguration presetOptionConfiguration)
      {
         this.messageType = messageType;
         this.messages = messages;
         this.presetOptionConfiguration = presetOptionConfiguration;
      }


      public Builder(final MessageType messageType, final String[] messages, final String[] options)
      {
         this.messageType = messageType;
         this.messages = messages;
         this.buttonOptions = options;
      }


      Builder(final Builder builderToCopy)
      {
         this.borderTitle = builderToCopy.borderTitle;
         this.messageType = builderToCopy.messageType;
         this.messages = builderToCopy.messages.clone();
         this.presetOptionConfiguration = builderToCopy.presetOptionConfiguration;
         this.buttonOptions = builderToCopy.buttonOptions.clone();
         this.inputComponents = builderToCopy.inputComponents.clone();
      }


      final public void setBorderTitle(final String borderTitle)
      {
         this.borderTitle = borderTitle;
      }


      final public void setMessageType(final MessageType messageType)
      {
         this.messageType = messageType;
      }


      final public void setMessages(final String[] messages)
      {
         this.messages = messages;
      }


      final public void setPresetOptionConfiguration(final PresetOptionConfiguration presetOptionConfiguration)
      {
         this.presetOptionConfiguration = presetOptionConfiguration;
      }


      final public void setButtonOptions(final String[] options)
      {
         this.buttonOptions = options;
      }


      final public void setInputComponents(final JComponent inputComponents[])
      {
         this.inputComponents = inputComponents;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static public enum MessageType
   {
      Plain,
      Information,
      Question,
      Error,
      Warning;
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static public enum PresetOptionConfiguration
   {
      None(new PresetOptionSelection[] {}),
      OK(new PresetOptionSelection[] {PresetOptionSelection.OK})
      {
         @Override
         final public int toButtonIndex(final PresetOptionSelection presetOptionSelection)
         {
            if (presetOptionSelection == PresetOptionSelection.OK)
               return 0;
            else
               throw new IllegalArgumentException();
         }
      },

      OKCancel(new PresetOptionSelection[] {PresetOptionSelection.OK, PresetOptionSelection.Cancel})
      {
         @Override
         final public int toButtonIndex(final PresetOptionSelection presetOptionSelection)
         {
            switch (presetOptionSelection)
            {
               case OK:
                  return 0;
               case Cancel:
                  return 1;
               default:
                  throw new IllegalArgumentException();
            }
         }
      },

      YesNo(new PresetOptionSelection[] {PresetOptionSelection.Yes, PresetOptionSelection.No})
      {
         @Override
         final public int toButtonIndex(final PresetOptionSelection presetOptionSelection)
         {
            switch (presetOptionSelection)
            {
               case Yes:
                  return 0;
               case No:
                  return 1;
               default:
                  throw new IllegalArgumentException();
            }
         }
      },

      YesNoCancel(new PresetOptionSelection[] {PresetOptionSelection.Yes, PresetOptionSelection.No, PresetOptionSelection.Cancel})
      {
         @Override
         final public int toButtonIndex(final PresetOptionSelection presetOptionSelection)
         {
            switch (presetOptionSelection)
            {
               case Yes:
                  return 0;
               case No:
                  return 1;
               case Cancel:
                  return 2;
               default:
                  throw new IllegalArgumentException();
            }
         }
      };

      final private PresetOptionSelection[] options;


      private PresetOptionConfiguration(final PresetOptionSelection[] options)
      {
         this.options = options;
      }


      public int toButtonIndex(final PresetOptionSelection presetOptionSelection)
      {
         throw new IllegalArgumentException();
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static public enum PresetOptionSelection
   {
      OK("OK"),
      Cancel("Cancel"),
      Yes("Yes"),
      No("No");

      final private String displayLabel;


      private PresetOptionSelection(final String displayLabel)
      {
         this.displayLabel = displayLabel;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static public interface ActionListener
   {
      public void actionPerformed(final MessageDialog messageDialog, final PresetOptionSelection optionSelection, final int optionSelectionIndex);
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final JComponent[] getInputComponents()
   {
      // No protective cloning for a package-private call.
      return inputComponents;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public SwingNimbusFrame getDelegate()
   {
      return delegateFrame;
   }


   final public PresetOptionConfiguration getPresetOptionConfiguration()
   {
      return presetOptionConfiguration;
   }


   final public void addActionListener(final ActionListener actionListener)
   {
      actionListeners.add(actionListener);
   }


   final public void removeActionListener(final ActionListener actionListener)
   {
      actionListeners.remove(actionListener);
   }


   final public void dispose()
   {
      delegateFrame.dispose();
   }
}