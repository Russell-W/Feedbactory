
package com.feedbactory.client.ui.settings;


import com.feedbactory.client.core.ConfigurationManager;
import com.feedbactory.client.ui.component.TranslucencyUtilities;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.client.ui.component.StripedListComboBox;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.LayoutStyle;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;


public class SettingsPanel
{
   final private SettingsUIManager parent;

   final private NimbusBorderPanel delegatePanel;

   final private RoundedPanel contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private JLabel idleFeedbactoryWindowTransparencyLabel = new JLabel();
   final private JSlider idleFeedbactoryWindowTransparencySlider = new JSlider();

   final private JLabel animatedWindowResizeEnabledLabel = new JLabel();
   final private JCheckBox animatedWindowResizeEnabledCheckBox = new JCheckBox();

   final private JLabel flashingFeedbackAlertEnabledLabel = new JLabel();
   final private JComboBox flashingFeedbackAlertEnabledComboBox = new StripedListComboBox();

   final private JLabel feedbackAlertSoundEnabledLabel = new JLabel();
   final private JCheckBox feedbackAlertSoundEnabledCheckBox = new JCheckBox();

   final private JButton okButton = new JButton();
   final private JButton cancelButton = new JButton();


   SettingsPanel(final SettingsUIManager parent)
   {
      this.parent = parent;

      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Feedbactory Settings");
      builder.setRadialGradientPaint(UIConstants.LighterPanelGradient);

      delegatePanel = new NimbusBorderPanel(builder);

      initialise();
   }


   private void initialise()
   {
      initialiseContentPanel();
      initialiseDelegatePanel();
   }


   private void initialiseContentPanel()
   {
      initialiseIdleFeedbactoryWindowTransparencyControls();
      initialiseAnimatedWindowResizeControls();
      initialiseFlashingFeedbackAlertControls();
      initialiseFeedbackAlertSoundControls();

      initialiseContentPanelLayout();
   }


   private void initialiseIdleFeedbactoryWindowTransparencyControls()
   {
      idleFeedbactoryWindowTransparencyLabel.setFont(UIConstants.RegularFont);
      idleFeedbactoryWindowTransparencyLabel.setText("Idle window transparency:");

      idleFeedbactoryWindowTransparencySlider.setMaximum(opacityToTranslucency(SettingsUIManager.MinimumIdlePadOpacity));
      idleFeedbactoryWindowTransparencySlider.setValue(opacityToTranslucency(parent.getIdlePadOpacity()));

      if (TranslucencyUtilities.isWindowOpacityControlSupported())
      {
         idleFeedbactoryWindowTransparencySlider.addChangeListener(new ChangeListener()
         {
            @Override
            final public void stateChanged(final ChangeEvent changeEvent)
            {
               handleIdleFeedbactoryWindowTransparencySliderChanged();
            }
         });

         idleFeedbactoryWindowTransparencySlider.addMouseListener(new MouseAdapter()
         {
            @Override
            final public void mousePressed(final MouseEvent mouseEvent)
            {
               handleIdleFeedbactoryWindowTransparencySliderMousePressed();
            }


            @Override
            final public void mouseReleased(final MouseEvent mouseEvent)
            {
               handleIdleFeedbactoryWindowTransparencySliderMouseReleased();
            }
         });

         idleFeedbactoryWindowTransparencySlider.addFocusListener(new FocusAdapter()
         {
            @Override
            final public void focusLost(final FocusEvent focusEvent)
            {
               handleIdleFeedbactoryWindowTransparencySliderFocusLost();
            }
         });
      }
      else
         idleFeedbactoryWindowTransparencySlider.setEnabled(false);
   }


   private void initialiseAnimatedWindowResizeControls()
   {
      animatedWindowResizeEnabledLabel.setFont(UIConstants.RegularFont);
      animatedWindowResizeEnabledLabel.setText("Animate window resize:");

      animatedWindowResizeEnabledCheckBox.setSelected(parent.isAnimatedPadResizeEnabled());
   }


   private void initialiseFlashingFeedbackAlertControls()
   {
      flashingFeedbackAlertEnabledLabel.setFont(UIConstants.RegularFont);
      flashingFeedbackAlertEnabledLabel.setText("Show feedback alert:");

      flashingFeedbackAlertEnabledComboBox.setFont(UIConstants.RegularFont);
      for (final FlashingFeedbackAlertOption flashingFeedbackAlertOption : FlashingFeedbackAlertOption.values())
         flashingFeedbackAlertEnabledComboBox.addItem(flashingFeedbackAlertOption);

      flashingFeedbackAlertEnabledComboBox.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleFlashingFeedbackAlertEnabledComboBoxActionPerformed();
         }
      });

      // This will fire the action event, which will initialise the enabled state of the sound alert controls.
      flashingFeedbackAlertEnabledComboBox.setSelectedItem(parent.isFlashingFeedbackAlertEnabled());
   }


   private void initialiseFeedbackAlertSoundControls()
   {
      feedbackAlertSoundEnabledLabel.setFont(UIConstants.RegularFont);
      feedbackAlertSoundEnabledLabel.setText("Feedback alert sound:");

      feedbackAlertSoundEnabledCheckBox.setSelected(parent.isFeedbackAlertSoundEnabled());
   }


   private void initialiseContentPanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(contentPanel);
      contentPanel.setLayout(panelLayout);

      final ParallelGroup horizontalLabelsGroup = panelLayout.createParallelGroup();
      final ParallelGroup horizontalControlsGroup = panelLayout.createParallelGroup();
      final SequentialGroup verticalGroup = panelLayout.createSequentialGroup();

      horizontalLabelsGroup.addComponent(idleFeedbactoryWindowTransparencyLabel);
      horizontalControlsGroup.addComponent(idleFeedbactoryWindowTransparencySlider, 0, 0, Integer.MAX_VALUE);

      verticalGroup.addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(idleFeedbactoryWindowTransparencyLabel)
            .addComponent(idleFeedbactoryWindowTransparencySlider)
         )
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED);

      if (! ConfigurationManager.isRunningMacOSX)
      {
         horizontalLabelsGroup.addComponent(animatedWindowResizeEnabledLabel);
         horizontalControlsGroup.addComponent(animatedWindowResizeEnabledCheckBox);
         verticalGroup.addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
               .addComponent(animatedWindowResizeEnabledLabel)
               .addComponent(animatedWindowResizeEnabledCheckBox)
            )
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED);
      }

      horizontalLabelsGroup.addComponent(flashingFeedbackAlertEnabledLabel);
      horizontalControlsGroup.addComponent(flashingFeedbackAlertEnabledComboBox);

      verticalGroup.addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(flashingFeedbackAlertEnabledLabel)
            .addComponent(flashingFeedbackAlertEnabledComboBox)
         )
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(feedbackAlertSoundEnabledLabel)
            .addComponent(feedbackAlertSoundEnabledCheckBox)
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE);

      horizontalLabelsGroup.addComponent(feedbackAlertSoundEnabledLabel);
      horizontalControlsGroup.addComponent(feedbackAlertSoundEnabledCheckBox);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addGroup(horizontalLabelsGroup)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(horizontalControlsGroup)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(verticalGroup);
   }


   private void initialiseDelegatePanel()
   {
      initialiseDelegatePanelControls();
      initialiseDelegatePanelLayout();
   }


   private void initialiseDelegatePanelControls()
   {
      okButton.setFont(UIConstants.RegularFont);
      okButton.setText("OK");

      cancelButton.setFont(UIConstants.RegularFont);
      cancelButton.setText("Cancel");

      okButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleOKButtonActionPerformed();
         }
      });

      cancelButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleCancelButtonActionPerformed();
         }
      });
   }


   private void initialiseDelegatePanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getContentPane());
      delegatePanel.getContentPane().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(contentPanel)
            .addGroup(panelLayout.createSequentialGroup()
               .addComponent(okButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
               .addComponent(cancelButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
            )
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(contentPanel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(okButton)
            .addComponent(cancelButton)
          )
         .addContainerGap()
      );
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleIdleFeedbactoryWindowTransparencySliderChanged()
   {
      previewPadOpacity();
   }


   private void handleIdleFeedbactoryWindowTransparencySliderMousePressed()
   {
      previewPadOpacity();
   }


   private void handleIdleFeedbactoryWindowTransparencySliderMouseReleased()
   {
      restorePadOpacity();
   }


   private void handleIdleFeedbactoryWindowTransparencySliderFocusLost()
   {
      restorePadOpacity();
   }


   private void handleFlashingFeedbackAlertEnabledComboBoxActionPerformed()
   {
      refreshFeedbackAlertSoundControls();
   }


   private void handleOKButtonActionPerformed()
   {
      parent.setIdlePadOpacity(translucencyToOpacity((byte) idleFeedbactoryWindowTransparencySlider.getValue()));
      parent.setAnimatedPadResizeEnabled(animatedWindowResizeEnabledCheckBox.isSelected());
      parent.setFlashingFeedbackAlertEnabled((FlashingFeedbackAlertOption) flashingFeedbackAlertEnabledComboBox.getSelectedItem());
      parent.setFeedbackAlertSoundEnabled(feedbackAlertSoundEnabledCheckBox.isSelected());

      parent.dismissSettingsPanel();
   }


   private void handleCancelButtonActionPerformed()
   {
      parent.dismissSettingsPanel();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void refreshFeedbackAlertSoundControls()
   {
      final FlashingFeedbackAlertOption selectedFlashingFeedbackAlertOption = (FlashingFeedbackAlertOption) flashingFeedbackAlertEnabledComboBox.getSelectedItem();
      final boolean areControlsEnabled = (selectedFlashingFeedbackAlertOption == FlashingFeedbackAlertOption.ShowForAnyRecognisedItems) ||
                                         (selectedFlashingFeedbackAlertOption == FlashingFeedbackAlertOption.ShowForItemsHavingFeedback);

      feedbackAlertSoundEnabledLabel.setEnabled(areControlsEnabled);
      feedbackAlertSoundEnabledCheckBox.setEnabled(areControlsEnabled);
   }


   private byte opacityToTranslucency(final byte translucency)
   {
      return (byte) (100 - translucency);
   }


   private byte translucencyToOpacity(final byte opacity)
   {
      return opacityToTranslucency(opacity);
   }


   private void previewPadOpacity()
   {
      parent.previewPadOpacity(translucencyToOpacity((byte) idleFeedbactoryWindowTransparencySlider.getValue()));
   }


   private void restorePadOpacity()
   {
      parent.restorePadOpacity();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}
