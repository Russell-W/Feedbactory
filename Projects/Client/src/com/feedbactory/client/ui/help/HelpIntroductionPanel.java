
package com.feedbactory.client.ui.help;


import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.HyperlinkLabel;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.service.FiveHundredPX;
import com.feedbactory.shared.feedback.personal.service.Flickr;
import com.feedbactory.shared.feedback.personal.service.Ipernity;
import com.feedbactory.shared.feedback.personal.service.OneX;
import com.feedbactory.shared.feedback.personal.service.PhotoShelter;
import com.feedbactory.shared.feedback.personal.service.Pixoto;
import com.feedbactory.shared.feedback.personal.service.SeventyTwoDPI;
import com.feedbactory.shared.feedback.personal.service.SmugMug;
import com.feedbactory.shared.feedback.personal.service.ViewBug;
import com.feedbactory.shared.feedback.personal.service.YouPic;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.LayoutStyle;


final class HelpIntroductionPanel
{
   final private HelpUIManager parentPanel;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private JComponent contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private JLabel welcomeLabel = new JLabel();

   final private JLabel textLabelP1L1 = new JLabel();
   final private JLabel textLabelP1L2 = new JLabel();
   final private JLabel textLabelP1L3 = new JLabel();
   final private JLabel textLabelP2L1 = new JLabel();
   final private JLabel textLabelP2L2 = new JLabel();
   final private JLabel textLabelP3L1 = new JLabel();
   final private JLabel textLabelP3L2 = new JLabel();
   final private JLabel textLabelP3L3 = new JLabel();

   final private List<HyperlinkLabel> hyperlinkLabels = new ArrayList<HyperlinkLabel>();

   final private JButton feedbackHelpButton = new JButton();
   final private JButton closeButton = new JButton();


   HelpIntroductionPanel(final HelpUIManager parentPanel)
   {
      this.parentPanel = parentPanel;

      initialise();
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("");
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

      initialiseTextLabels();
      initialiseHyperlinkLabels();

      initialiseContentLayout();
   }


   private void initialiseTextLabels()
   {
      /* Feedbactory is an innovative new service that allows you to
       * view and provide anonymous feedback for products, services,
       * and other items as you browse the web.
       *
       * During this early release period, Feedbactory ratings are
       * limited to some popular photography sharing websites:
       *
       * Support for more feedback categories and websites will be
       * enabled via automatic updates. In the meantime, thank you
       * for helping us launch this exciting new platform!
       */
      textLabelP1L1.setFont(UIConstants.RegularFont);
      textLabelP1L1.setText("Feedbactory is an innovative new service that allows you to");
      textLabelP1L2.setFont(UIConstants.RegularFont);
      textLabelP1L2.setText("view and provide anonymous feedback for products, services,");
      textLabelP1L3.setFont(UIConstants.RegularFont);
      textLabelP1L3.setText("and other items as you browse the web.");

      textLabelP2L1.setFont(UIConstants.RegularFont);
      textLabelP2L1.setText("During this early release period, Feedbactory ratings are");
      textLabelP2L2.setFont(UIConstants.RegularFont);
      textLabelP2L2.setText("limited to some popular photography sharing websites:");

      textLabelP3L1.setFont(UIConstants.RegularFont);
      textLabelP3L1.setText("Support for more feedback categories and websites will be");
      textLabelP3L2.setFont(UIConstants.RegularFont);
      textLabelP3L2.setText("enabled via automatic updates. In the meantime, thank you");
      textLabelP3L3.setFont(UIConstants.RegularFont);
      textLabelP3L3.setText("for helping us launch this exciting new platform!");
   }


   private void initialiseHyperlinkLabels()
   {
      hyperlinkLabels.add(createHyperlinkLabel(Flickr.instance));
      hyperlinkLabels.add(createHyperlinkLabel(SmugMug.instance));
      hyperlinkLabels.add(createHyperlinkLabel(FiveHundredPX.instance));
      hyperlinkLabels.add(createHyperlinkLabel(PhotoShelter.instance));
      hyperlinkLabels.add(createHyperlinkLabel(OneX.instance));
      hyperlinkLabels.add(createHyperlinkLabel(SeventyTwoDPI.instance));
      hyperlinkLabels.add(createHyperlinkLabel(YouPic.instance));
      hyperlinkLabels.add(createHyperlinkLabel(Ipernity.instance));
      hyperlinkLabels.add(createHyperlinkLabel(ViewBug.instance));
      hyperlinkLabels.add(createHyperlinkLabel(Pixoto.instance));

      Collections.shuffle(hyperlinkLabels);
   }


   private HyperlinkLabel createHyperlinkLabel(final PersonalFeedbackWebsite website)
   {
      final HyperlinkLabel hyperlinkLabel = new HyperlinkLabel();

      hyperlinkLabel.setFont(UIConstants.RegularFont);
      hyperlinkLabel.setHyperlink(website.getName(), website.getItemBrowseURL());

      hyperlinkLabel.addMouseListener(new MouseAdapter()
      {
         @Override
         final public void mouseClicked(final MouseEvent mouseEvent)
         {
            handleLinkClicked(hyperlinkLabel);
         }
      });

      return hyperlinkLabel;
   }


   private void initialiseContentLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(contentPanel);
      contentPanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createParallelGroup()
         .addGroup(panelLayout.createSequentialGroup()
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
            .addGroup(panelLayout.createParallelGroup()
               .addComponent(textLabelP1L1)
               .addComponent(textLabelP1L2)
               .addComponent(textLabelP1L3)
               .addComponent(textLabelP2L1)
               .addComponent(textLabelP2L2)
               .addComponent(textLabelP3L1)
               .addComponent(textLabelP3L2)
               .addComponent(textLabelP3L3)
            )
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         )
         .addGroup(panelLayout.createSequentialGroup()
            .addContainerGap(30, 30)
            .addComponent(hyperlinkLabels.get(0), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(hyperlinkLabels.get(1), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(hyperlinkLabels.get(2), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(hyperlinkLabels.get(3), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(hyperlinkLabels.get(4), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         )
         .addGroup(panelLayout.createSequentialGroup()
            .addContainerGap(30, 30)
            .addComponent(hyperlinkLabels.get(5), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(hyperlinkLabels.get(6), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(hyperlinkLabels.get(7), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(hyperlinkLabels.get(8), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(hyperlinkLabels.get(9), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         )
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(textLabelP1L1)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP1L2)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP1L3)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(textLabelP2L1)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP2L2)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 20, 20)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(hyperlinkLabels.get(0))
            .addComponent(hyperlinkLabels.get(1))
            .addComponent(hyperlinkLabels.get(2))
            .addComponent(hyperlinkLabels.get(3))
            .addComponent(hyperlinkLabels.get(4))
         )
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(hyperlinkLabels.get(5))
            .addComponent(hyperlinkLabels.get(6))
            .addComponent(hyperlinkLabels.get(7))
            .addComponent(hyperlinkLabels.get(8))
            .addComponent(hyperlinkLabels.get(9))
         )
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 20, 20)
         .addComponent(textLabelP3L1)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP3L2)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP3L3)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );
   }


   private void initialiseDelegatePanel()
   {
      welcomeLabel.setFont(UIConstants.LargeFont);
      welcomeLabel.setText("Welcome");

      initialiseFeedbackHelpButton();
      initialiseCloseButton();

      initialiseDelegatePanelLayout();
   }


   private void initialiseFeedbackHelpButton()
   {
      feedbackHelpButton.setFont(UIConstants.RegularFont);
      feedbackHelpButton.setText("How do I view and submit feedback?");

      feedbackHelpButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleFeedbackHelpButtonActionPerformed();
         }
      });
   }


   private void initialiseCloseButton()
   {
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
            .addGroup(panelLayout.createSequentialGroup()
               .addGap(UIConstants.MediumContainerGapSize)
               .addComponent(welcomeLabel)
            )
            .addComponent(contentPanel)
            .addGroup(panelLayout.createSequentialGroup()
               .addGap(0, 0, Integer.MAX_VALUE)
               .addComponent(feedbackHelpButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
               .addComponent(closeButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addGap(0, 0, Integer.MAX_VALUE)
            )
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(welcomeLabel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(contentPanel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(feedbackHelpButton)
            .addComponent(closeButton)
         )
         .addContainerGap()
      );
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleLinkClicked(final HyperlinkLabel hyperlinkLabel)
   {
      parentPanel.openURL(hyperlinkLabel.getHyperlink());
   }


   private void handleFeedbackHelpButtonActionPerformed()
   {
      parentPanel.swapToFeedbackHelpPanel();
   }


   private void handleCloseButtonActionPerformed()
   {
      parentPanel.dismissHelpPanel();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}