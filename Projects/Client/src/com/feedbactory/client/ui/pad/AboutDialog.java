
package com.feedbactory.client.ui.pad;


import com.feedbactory.client.core.FeedbactoryClientConstants;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.client.ui.feedback.FeedbackPanel;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.LayoutStyle;


final class AboutDialog
{
   final private FeedbactoryPad parent;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private JComponent contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private JLabel iconLabel = new JLabel();

   final private JLabel versionLabel = new JLabel();
   final private JButton versionCopyButton = new JButton();

   final private JLabel textLabelP1L1 = new JLabel();
   final private JLabel textLabelP2L1 = new JLabel();
   final private JLabel textLabelP2L2 = new JLabel();
   final private JLabel textLabelP2L3 = new JLabel();
   final private JLabel textLabelP2L4 = new JLabel();
   final private JLabel textLabelP2L5 = new JLabel();
   final private JLabel textLabelP2L6 = new JLabel();
   final private JLabel textLabelP2L7 = new JLabel();

   final private JButton okButton = new JButton();


   AboutDialog(final FeedbactoryPad parent)
   {
      this.parent = parent;

      initialise();
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("About Feedbactory");
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

      initialiseIconLabel();
      initialiseVersionInformation();
      initialiseTextLabels();

      initialiseContentPanelLayout();
   }


   private void initialiseIconLabel()
   {
      try
      {
         final BufferedImage buttonImage = ImageIO.read(FeedbackPanel.class.getResourceAsStream(UIConstants.ApplicationIconLargePath));
         iconLabel.setIcon(new ImageIcon(UIUtilities.getScaledImage(buttonImage, 64, 64, RenderingHints.VALUE_INTERPOLATION_BICUBIC)));
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }
   }


   private void initialiseVersionInformation()
   {
      versionLabel.setFont(UIConstants.RegularFont);
      versionLabel.setText("Feedbactory version: " + FeedbactoryClientConstants.DisplayableVersionID);

      versionCopyButton.setFont(UIConstants.RegularFont);
      versionCopyButton.setText("Copy");
      versionCopyButton.setFocusable(false);

      versionCopyButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            versionCopyButtonActionPerformed();
         }
      });
   }


   private void initialiseTextLabels()
   {
      textLabelP1L1.setFont(UIConstants.RegularFont);
      textLabelP1L1.setText("Many thanks:");

      textLabelP2L1.setFont(UIConstants.RegularFont);
      textLabelP2L1.setText("Java libraries: Java SE, SWT, JAI, JavaMail");
      textLabelP2L2.setFont(UIConstants.RegularFont);
      textLabelP2L2.setText("Tools: NetBeans, GIMP, ProGuard, Inno Setup, Launch4j");
      textLabelP2L3.setFont(UIConstants.RegularFont);
      textLabelP2L3.setText("Browser support: Microsoft Open Technologies, Mozilla, WebKit");
      textLabelP2L4.setFont(UIConstants.RegularFont);
      textLabelP2L4.setText("Logo design: Daria Angelova");
      textLabelP2L5.setFont(UIConstants.RegularFont);
      textLabelP2L5.setText("Font design: Christian Robertson, Google Fonts");
      textLabelP2L6.setFont(UIConstants.RegularFont);
      textLabelP2L6.setText("Screenshot photography: Roland Shainidze");
      textLabelP2L7.setFont(UIConstants.RegularFont);
      textLabelP2L7.setText("Testing assistance: You");
   }


   private void initialiseContentPanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(contentPanel);
      contentPanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createParallelGroup()
         .addGroup(panelLayout.createSequentialGroup()
            .addGap(0, 0, Integer.MAX_VALUE)
            .addComponent(iconLabel)
            .addGap(0, 0, Integer.MAX_VALUE)
         )
         .addGroup(panelLayout.createSequentialGroup()
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
            .addGroup(panelLayout.createParallelGroup()
               .addGroup(panelLayout.createSequentialGroup()
                  .addComponent(versionLabel)
                  .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(versionCopyButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               )
               .addComponent(textLabelP1L1)
               .addComponent(textLabelP2L1)
               .addComponent(textLabelP2L2)
               .addComponent(textLabelP2L3)
               .addComponent(textLabelP2L4)
               .addComponent(textLabelP2L5)
               .addComponent(textLabelP2L6)
               .addComponent(textLabelP2L7)
            )
            .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         )
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(iconLabel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(versionLabel)
            .addComponent(versionCopyButton)
         )
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(textLabelP1L1)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(textLabelP2L1)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP2L2)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP2L3)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP2L4)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP2L5)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP2L6)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(textLabelP2L7)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );
   }


   private void initialiseDelegatePanel()
   {
      initialiseOKButton();
      initialiseDelegatePanelLayout();
   }


   private void initialiseOKButton()
   {
      okButton.setFont(UIConstants.RegularFont);
      okButton.setText("OK");

      okButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleOKButtonActionPerformed();
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
               .addComponent(okButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addGap(0, 0, Integer.MAX_VALUE)
            )
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(contentPanel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addComponent(okButton)
         .addContainerGap()
      );
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void versionCopyButtonActionPerformed()
   {
      final StringSelection stringSelection = new StringSelection(FeedbactoryClientConstants.DisplayableVersionID);
      final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      clipboard.setContents(stringSelection, null);
   }


   private void handleOKButtonActionPerformed()
   {
      parent.dismissLockingComponent(getDelegate());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}