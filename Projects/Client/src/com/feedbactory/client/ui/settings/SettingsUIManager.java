
package com.feedbactory.client.ui.settings;


import com.feedbactory.client.core.ConfigurationManager;
import com.feedbactory.client.ui.component.TranslucencyUtilities;
import com.feedbactory.client.ui.pad.FeedbactoryPadUIView;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;


final public class SettingsUIManager
{
   static final private String IdlePadOpacityPreferencesKey = "IdlePadOpacity";
   static final private String AnimatedPadResizeEnabledPreferencesKey = "AnimatedPadResizeEnabled";
   static final private String FlashingFeedbackAlertEnabledPreferencesKey = "FlashingFeedbackAlertEnabled";
   static final private String FeedbackAlertSoundEnabledPreferencesKey = "FeedbackAlertSoundEnabled";

   static final public byte MaximumIdlePadOpacity = 100;
   static final byte MinimumIdlePadOpacity = 20;
   static final private byte DefaultIdlePadOpacity = 35;

   final private FeedbactoryPadUIView feedbactoryPad;

   private JComponent activeSettingsPanel;

   private byte idlePadOpacity;
   private boolean animatedPadResizeEnabled;
   private FlashingFeedbackAlertOption flashingFeedbackAlertEnabled;
   private boolean feedbackAlertSoundEnabled;

   final private Map<Setting, Set<SettingChangeListener>> settingChangeListeners = new EnumMap<Setting, Set<SettingChangeListener>>(Setting.class);


   public SettingsUIManager(final FeedbactoryPadUIView feedbactoryPadUIView)
   {
      this.feedbactoryPad = feedbactoryPadUIView;

      initialise();
   }


   private void initialise()
   {
      initialiseSettingChangeListeners();

      restoreSettings();
   }


   private void initialiseSettingChangeListeners()
   {
      for (final Setting settingID : Setting.values())
         settingChangeListeners.put(settingID, new HashSet<SettingChangeListener>());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void saveSettings()
   {
      final Preferences preferences = Preferences.userNodeForPackage(getClass());

      preferences.putInt(IdlePadOpacityPreferencesKey, idlePadOpacity);
      preferences.putBoolean(AnimatedPadResizeEnabledPreferencesKey, animatedPadResizeEnabled);
      preferences.putInt(FlashingFeedbackAlertEnabledPreferencesKey, flashingFeedbackAlertEnabled.value);
      preferences.putBoolean(FeedbackAlertSoundEnabledPreferencesKey, feedbackAlertSoundEnabled);
   }


   private void restoreSettings()
   {
      final Preferences preferences = Preferences.userNodeForPackage(getClass());

      if (TranslucencyUtilities.isWindowOpacityControlSupported())
      {
         idlePadOpacity = (byte) preferences.getInt(IdlePadOpacityPreferencesKey, DefaultIdlePadOpacity);
         if ((idlePadOpacity < MinimumIdlePadOpacity) || (idlePadOpacity > MaximumIdlePadOpacity))
            idlePadOpacity = MaximumIdlePadOpacity;
      }
      else
         idlePadOpacity = MaximumIdlePadOpacity;

      if (ConfigurationManager.isRunningMacOSX)
         animatedPadResizeEnabled = false;
      else
         animatedPadResizeEnabled = preferences.getBoolean(AnimatedPadResizeEnabledPreferencesKey, true);

      final byte flashingFeedbackAlertValue = (byte) preferences.getInt(FlashingFeedbackAlertEnabledPreferencesKey, FlashingFeedbackAlertOption.ShowForItemsHavingFeedback.value);
      flashingFeedbackAlertEnabled = FlashingFeedbackAlertOption.fromValue(flashingFeedbackAlertValue);

      feedbackAlertSoundEnabled = preferences.getBoolean(FeedbackAlertSoundEnabledPreferencesKey, false);
   }


   private void notifySettingChange(final Setting setting)
   {
      for (final SettingChangeListener settingChangeListener : settingChangeListeners.get(setting))
         settingChangeListener.settingChanged(setting);
   }


   private void attachCancelKeyBinding()
   {
      final InputMap inputMap = activeSettingsPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      final ActionMap actionMap = activeSettingsPanel.getActionMap();

      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelPanel");

      actionMap.put("cancelPanel", new AbstractAction()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            dismissSettingsPanel();
         }
      });
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleAddSettingChangeListener(final Setting setting, final SettingChangeListener settingChangeListener)
   {
      if (settingChangeListener == null)
         throw new IllegalArgumentException("Setting change listener cannot be null.");

      settingChangeListeners.get(setting).add(settingChangeListener);
   }


   private void handleSetIdlePadOpacity(final byte idlePadOpacity)
   {
      if ((idlePadOpacity < MinimumIdlePadOpacity) || (idlePadOpacity > MaximumIdlePadOpacity))
         throw new IllegalArgumentException("Invalid pad transparency value: " + idlePadOpacity);
      else if (this.idlePadOpacity != idlePadOpacity)
      {
         this.idlePadOpacity = idlePadOpacity;
         notifySettingChange(Setting.IdlePadOpacity);
      }
   }


   private void handleSetAnimatedPadResizeEnabled(final boolean animatedPadResizeEnabled)
   {
      if (this.animatedPadResizeEnabled != animatedPadResizeEnabled)
      {
         this.animatedPadResizeEnabled = animatedPadResizeEnabled;
         notifySettingChange(Setting.AnimatePadResizeEnabled);
      }
   }


   private void handleSetFlashingFeedbackAlertEnabled(final FlashingFeedbackAlertOption flashingFeedbackAlertEnabled)
   {
      if (this.flashingFeedbackAlertEnabled != flashingFeedbackAlertEnabled)
      {
         this.flashingFeedbackAlertEnabled = flashingFeedbackAlertEnabled;
         notifySettingChange(Setting.FlashingFeedbackAlertEnabled);
      }
   }


   private void handleSetFeedbackAlertSoundEnabled(final boolean feedbackAlertSoundEnabled)
   {
      if (this.feedbackAlertSoundEnabled != feedbackAlertSoundEnabled)
      {
         this.feedbackAlertSoundEnabled = feedbackAlertSoundEnabled;
         notifySettingChange(Setting.FeedbackAlertSoundEnabled);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleShowSettingsPanel()
   {
      if (activeSettingsPanel == null)
      {
         activeSettingsPanel = new SettingsPanel(this).getDelegate();
         attachCancelKeyBinding();
         feedbactoryPad.showFormComponent(activeSettingsPanel, false);
      }
   }


   private void handleDismissSettingsPanel()
   {
      if (activeSettingsPanel != null)
      {
         feedbactoryPad.dismissLockingComponent(activeSettingsPanel);

         if (TranslucencyUtilities.isWindowOpacityControlSupported())
            restorePadOpacity();

         activeSettingsPanel = null;
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void previewPadOpacity(final byte padOpacity)
   {
      feedbactoryPad.setOpacity(padOpacity);
   }


   final void restorePadOpacity()
   {
      feedbactoryPad.setOpacity(MaximumIdlePadOpacity);
   }


   final void setIdlePadOpacity(final byte idlePadOpacity)
   {
      handleSetIdlePadOpacity(idlePadOpacity);
   }


   final void setAnimatedPadResizeEnabled(final boolean animatedPadResizeEnabled)
   {
      handleSetAnimatedPadResizeEnabled(animatedPadResizeEnabled);
   }


   final void setFlashingFeedbackAlertEnabled(final FlashingFeedbackAlertOption flashingFeedbackAlertEnabled)
   {
      handleSetFlashingFeedbackAlertEnabled(flashingFeedbackAlertEnabled);
   }


   final void setFeedbackAlertSoundEnabled(final boolean feedbackAlertSoundEnabled)
   {
      handleSetFeedbackAlertSoundEnabled(feedbackAlertSoundEnabled);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void addSettingChangeListener(final Setting setting, final SettingChangeListener settingChangeListener)
   {
      handleAddSettingChangeListener(setting, settingChangeListener);
   }


   final public void removeSettingChangeListener(final Setting setting, final SettingChangeListener settingChangeListener)
   {
      settingChangeListeners.get(setting).remove(settingChangeListener);
   }


   final public boolean isIdlePadTranslucencyEnabled()
   {
      return (idlePadOpacity != MaximumIdlePadOpacity);
   }


   final public byte getIdlePadOpacity()
   {
      return idlePadOpacity;
   }


   final public boolean isAnimatedPadResizeEnabled()
   {
      return animatedPadResizeEnabled;
   }


   final public FlashingFeedbackAlertOption isFlashingFeedbackAlertEnabled()
   {
      return flashingFeedbackAlertEnabled;
   }


   final public boolean isFeedbackAlertSoundEnabled()
   {
      return feedbackAlertSoundEnabled;
   }


   final public void showSettingsPanel()
   {
      handleShowSettingsPanel();
   }


   final public void dismissSettingsPanel()
   {
      handleDismissSettingsPanel();
   }


   final public void shutdown()
   {
      saveSettings();
   }
}