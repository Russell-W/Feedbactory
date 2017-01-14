package com.feedbactory.client.ui.pad;


import com.feedbactory.client.ui.component.ImageLoader;
import com.feedbactory.client.ui.component.MessageDialog;
import javax.swing.JComponent;


public interface FeedbactoryPadUIView
{
   public ImageLoader getImageLoader();

   public void requestRepack();

   public boolean isVisible();
   public void setVisible(final boolean isVisible);

   public void setOpacity(final byte opacity);

   public boolean isFeedbackPanelShowing();
   public void showAccountPanel();
   public void showSettingsPanel();
   public void showHelpPanel();

   public void showFormComponent(final JComponent component, final boolean isTemporary);
   public void showFormSubcomponent(final JComponent component, final boolean isTemporary);
   public void dismissLockingComponent(final JComponent component);
   public void showMessageDialog(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection defaultAction, final boolean actionOnPadHidden);
   public void showHighPriorityMessageDialog(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection defaultAction, final boolean actionOnPadHidden);
   public void showTimedMessageDialog(final MessageDialog.Builder messageDialogBuilder, final boolean actionOnPadHidden, final int defaultActionIndex, final long displayDurationMilliseconds);

   public boolean isFeedbackPadBusy();
   public void setBusy(final boolean isSubmissionActive);

   public void cancelDiscardUnsavedFeedback();
   public void confirmDiscardUnsavedFeedback();
}