
package com.feedbactory.client.ui.useraccount;


import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.LayoutStyle;
import javax.swing.SwingConstants;


final class ChangeSendEmailAlertsPanel
{
   final private AccountDetailsPanel parentPanel;
   final private boolean existingSendEmailAlerts;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private RoundedPanel contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private JCheckBox sendEmailAlertsCheckBox = new JCheckBox();

   final private JButton applyUpdateSendEmailAlerts = new JButton();
   final private JButton cancelButton = new JButton();


   ChangeSendEmailAlertsPanel(final AccountDetailsPanel parentPanel, final boolean existingSendEmailAlerts)
   {
      this.parentPanel = parentPanel;
      this.existingSendEmailAlerts = existingSendEmailAlerts;

      initialise();
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Email Alerts");
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

      initialiseSendAnnouncementsConfirmationControls();
      initialiseContentPanelLayout();
   }


   private void initialiseSendAnnouncementsConfirmationControls()
   {
      final ItemListener sendAnnouncementsItemListener = new ItemListener()
      {
         @Override
         final public void itemStateChanged(final ItemEvent itemEvent)
         {
            handleNewSendAnnouncementsFieldUpdated();
         }
      };

      final ActionListener updateSendAnnouncementsActionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleApplyUpdateSendAnnouncementsActionPerformed();
         }
      };

      sendEmailAlertsCheckBox.setFont(UIConstants.RegularFont);
      sendEmailAlertsCheckBox.setText("Send important announcements:");
      sendEmailAlertsCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
      sendEmailAlertsCheckBox.setSelected(existingSendEmailAlerts);
      sendEmailAlertsCheckBox.addItemListener(sendAnnouncementsItemListener);

      applyUpdateSendEmailAlerts.setFont(UIConstants.RegularFont);
      applyUpdateSendEmailAlerts.setText("Apply");
      applyUpdateSendEmailAlerts.setEnabled(false);
      applyUpdateSendEmailAlerts.addActionListener(updateSendAnnouncementsActionListener);

      cancelButton.setFont(UIConstants.RegularFont);
      cancelButton.setText("Cancel");
      cancelButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleCancelButtonActionPerformed();
         }
      });
   }


   private void initialiseContentPanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(contentPanel);
      contentPanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(sendEmailAlertsCheckBox)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(sendEmailAlertsCheckBox)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );
   }


   private void initialiseDelegatePanel()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getContentPane());
      delegatePanel.getContentPane().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(contentPanel)
            .addGroup(panelLayout.createSequentialGroup()
               .addComponent(applyUpdateSendEmailAlerts, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
               .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
               .addComponent(cancelButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
            )
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(contentPanel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(applyUpdateSendEmailAlerts)
            .addComponent(cancelButton)
         )
         .addContainerGap()
      );
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleNewSendAnnouncementsFieldUpdated()
   {
      applyUpdateSendEmailAlerts.setEnabled(hasSendAnnouncementsChanged());
   }


   private boolean hasSendAnnouncementsChanged()
   {
      return (sendEmailAlertsCheckBox.isSelected() != existingSendEmailAlerts);
   }


   private void handleApplyUpdateSendAnnouncementsActionPerformed()
   {
      parentPanel.dismissActiveAccountSubcomponent();
      parentPanel.updateSendEmailAlerts(sendEmailAlertsCheckBox.isSelected());
   }


   private void handleCancelButtonActionPerformed()
   {
      parentPanel.dismissActiveAccountSubcomponent();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}