
package com.feedbactory.client.ui.help;


import com.feedbactory.client.core.ConfigurationManager;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.component.BouncingTextLabel;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.client.ui.component.ShadedButton;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.*;


final class FeedbackHelpPanel
{
   final private HelpUIManager parentPanel;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private JComponent contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private JLabel feedbackLabel = new JLabel();

   final private JLabel textLabelP1L1 = new JLabel();
   final private JLabel textLabelP1L2 = new JLabel();
   final private JLabel textLabelP2L1 = new JLabel();
   final private JLabel textLabelP2L2 = new JLabel();
   final private JLabel textLabelP3L1 = new JLabel();
   final private JLabel textLabelP3L2 = new JLabel();
   final private JLabel textLabelP3L3 = new JLabel();
   final private JLabel textLabelP3L4 = new JLabel();
   final private JLabel textLabelP4L1 = new JLabel();
   final private JLabel textLabelP4L2 = new JLabel();
   final private JLabel textLabelP5L1 = new JLabel();
   final private JLabel textLabelP5L2 = new JLabel();

   final private ShadedButton feedbackAlertButton = new ShadedButton(UIConstants.TitleBarButtonCornerRadius);
   final private JSeparator verticalSeparator = new JSeparator();
   final private BouncingTextLabel feedbackItemDisplayLabel = new BouncingTextLabel();

   final private JButton backButton = new JButton();
   final private JButton closeButton = new JButton();


   FeedbackHelpPanel(final HelpUIManager parentPanel)
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
      initialiseExampleToolbarControls();

      initialiseContentPanelLayout();
   }


   /* When Feedbactory recognises a web page for a product,
    * service, or other item, it will appear on the toolbar:
    * 
    * If the item has been rated, the Feedback button will also
    * flash momentarily.
    * 
    * Click the button or press Alt to display this window and view
    * the feedback for items browsed during the session.
    * Recent and popular feedback submitted by others can also
    * be viewed via the Feedback menu.
    *
    * You need to sign up for an account before you can submit
    * feedback of your own. Signing up is quick, easy, and free.
    *
    * All feedback contributions are anonymous, and no private or
    * secure browsing information is captured.
    */
   private void initialiseTextLabels()
   {
      textLabelP1L1.setFont(UIConstants.RegularFont);
      textLabelP1L1.setText("When Feedbactory recognises a web page for a product,");
      textLabelP1L2.setFont(UIConstants.RegularFont);
      textLabelP1L2.setText("service, or other item, it will appear on the toolbar:");

      textLabelP2L1.setFont(UIConstants.RegularFont);
      textLabelP2L1.setText("If the item has been rated, the Feedback button will also");
      textLabelP2L2.setFont(UIConstants.RegularFont);
      textLabelP2L2.setText("flash momentarily.");

      textLabelP3L1.setFont(UIConstants.RegularFont);
      textLabelP3L2.setFont(UIConstants.RegularFont);
      if (ConfigurationManager.isRunningMacOSX)
      {
         textLabelP3L1.setText("Click the button or press Option to display this window and");
         textLabelP3L2.setText("view the feedback for items browsed during the session.");
      }
      else
      {
         textLabelP3L1.setText("Click the button or press Alt to display this window and view");
         textLabelP3L2.setText("the feedback for items browsed during the session.");
      }

      textLabelP3L3.setFont(UIConstants.RegularFont);
      textLabelP3L3.setText("Recent and popular ratings submitted by others can also be");
      textLabelP3L4.setFont(UIConstants.RegularFont);
      textLabelP3L4.setText("explored via the Feedback menu.");

      textLabelP4L1.setFont(UIConstants.RegularFont);
      textLabelP4L1.setText("You need to sign up for an account before you can submit");
      textLabelP4L2.setFont(UIConstants.RegularFont);
      textLabelP4L2.setText("feedback of your own. Signing up is quick, easy, and free.");

      textLabelP5L1.setFont(UIConstants.RegularBoldFont);
      textLabelP5L1.setText("All feedback contributions are anonymous, and no private or");
      textLabelP5L2.setFont(UIConstants.RegularBoldFont);
      textLabelP5L2.setText("secure browsing information is processed or collected.");
   }


   private void initialiseExampleToolbarControls()
   {
      initialiseAlertButton();

      verticalSeparator.setOrientation(SwingConstants.VERTICAL);

      feedbackItemDisplayLabel.setFont(UIConstants.SmallFont);
   }


   private void initialiseAlertButton()
   {
      feedbackAlertButton.setFont(UIConstants.SmallFont);
      feedbackAlertButton.setText("Feedback");
      feedbackAlertButton.setFocusable(false);

      feedbackAlertButton.setMouseOverRadialGradientPaint(UIConstants.ControlPanelButtonMouseOverGradient);
      feedbackAlertButton.setPressedRadialGradientPaint(UIConstants.ControlPanelButtonPressedGradient);
      feedbackAlertButton.setAlertRadialGradientPaint(UIConstants.ControlPanelButtonAlertGradient);

      try
      {
         final BufferedImage buttonImage = ImageIO.read(getClass().getResourceAsStream(UIConstants.ApplicationIconSmallPath));
         feedbackAlertButton.setImage(UIUtilities.getScaledImage(buttonImage, 19, 19, RenderingHints.VALUE_INTERPOLATION_BICUBIC));
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }
   }


   private void initialiseContentPanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(contentPanel);
      contentPanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createParallelGroup()
         .addGroup(panelLayout.createSequentialGroup()
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
            .addGroup(panelLayout.createParallelGroup()
               .addComponent(textLabelP1L1)
               .addComponent(textLabelP1L2)
               .addComponent(textLabelP2L1)
               .addComponent(textLabelP2L2)
               .addComponent(textLabelP3L1)
               .addComponent(textLabelP3L2)
               .addComponent(textLabelP3L3)
               .addComponent(textLabelP3L4)
               .addComponent(textLabelP4L1)
               .addComponent(textLabelP4L2)
               .addComponent(textLabelP5L1)
               .addComponent(textLabelP5L2)
            )
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         )
         .addGroup(panelLayout.createSequentialGroup()
            .addContainerGap(50, 50)
            .addComponent(feedbackAlertButton, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addGap(20)
            .addComponent(verticalSeparator)
            .addGap(10)
            .addComponent(feedbackItemDisplayLabel, GroupLayout.PREFERRED_SIZE, 160, GroupLayout.PREFERRED_SIZE)
            .addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
         )
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(textLabelP1L1)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP1L2)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 20, 20)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(feedbackAlertButton)
            .addComponent(verticalSeparator)
            .addComponent(feedbackItemDisplayLabel, GroupLayout.PREFERRED_SIZE, 15, GroupLayout.PREFERRED_SIZE)
         )
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 20, 20)
         .addComponent(textLabelP2L1)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP2L2)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 20, 20)
         .addComponent(textLabelP3L1)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP3L2)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP3L3)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP3L4)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(textLabelP4L1)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP4L2)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(textLabelP5L1)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP5L2)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );
   }


   private void initialiseDelegatePanel()
   {
      feedbackLabel.setFont(UIConstants.LargeFont);
      feedbackLabel.setText("Feedback");

      initialiseBackButton();
      initialiseCloseButton();

      initialiseDelegatePanelLayout();

      initialiseDelegatePanelListener();
   }


   private void initialiseBackButton()
   {
      backButton.setFont(UIConstants.RegularFont);
      backButton.setText("Back");

      backButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleBackButtonActionPerformed();
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
               .addComponent(feedbackLabel)
            )
            .addComponent(contentPanel)
            .addGroup(panelLayout.createSequentialGroup()
               .addGap(0, 0, Integer.MAX_VALUE)
               .addComponent(backButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
               .addComponent(closeButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addGap(0, 0, Integer.MAX_VALUE)
            )
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(feedbackLabel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(contentPanel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(backButton)
            .addComponent(closeButton)
         )
         .addContainerGap()
      );
   }


   private void initialiseDelegatePanelListener()
   {
      delegatePanel.getDelegate().addHierarchyListener(new HierarchyListener()
      {
         @Override
         final public void hierarchyChanged(final HierarchyEvent hierarchyEvent)
         {
            if (((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) && delegatePanel.getDelegate().isShowing())
               handlePanelShown();
         }
      });
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handlePanelShown()
   {
      feedbackAlertButton.showButtonAlert();
      feedbackItemDisplayLabel.showAnimatedText("Odyssey Backpackers");
   }


   private void handleBackButtonActionPerformed()
   {
      parentPanel.swapToHelpIntroductionPanel();
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