
package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.ShadedComponent.RadialGradientPaintProfile;
import java.awt.*;
import javax.swing.GroupLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;


final public class NimbusBorderPanel
{
   static final private int BorderWidth = 6;

   static final private Color BorderPanelColor1 = new Color(43, 46, 51);
   static final private Color BorderPanelColor2 = new Color(204, 207, 213);
   static final private Color BorderPanelColor3 = new Color(171, 176, 186);
   static final private Color BorderPanelColor4 = new Color(149, 158, 167);
   static final private Color BorderPanelColor5 = new Color(108, 114, 128);
   static final private Color BorderPanelGradientStartColour = new Color(227, 229, 232);
   static final private Color BorderPanelGradientEndColour = BorderPanelColor3;

   static final private int BorderTitleTopClearance = 5;
   static final private int BorderTitleBottomClearance = 5;
   static final private int BorderTitleLeftClearance = 5;

   final private RoundedShadowComponent delegatePanel;

   final private JPanel borderPanel = new BorderPanel();
   final private String borderTitle;

   final private JComponent contentPane;


   public NimbusBorderPanel(final Builder builder)
   {
      /* We don't do any builder cloning and validation here (to protect against object mutation by the client during construction), since we rely on the
       * child objects to perform the necessary validation, if any. None of the builder data if mutated would put this parent object into a jeopardised state.
       * If for example the hasRadialGradientPaint flag and values were to be mutated after the check below, it may change the panel type but wouldn't result
       * in us creating an illegal parent object, so long as the eventual values passed in for the ShadedPanel constructor were valid.
       */

      delegatePanel = new RoundedShadowComponent(borderPanel, builder.shadowLength, builder.maximumShadowTransparency);

      borderTitle = builder.borderTitle;

      contentPane = initialiseContentPane(builder);

      initialise((builder.roundedBorderPanelColour != null) ? initialiseTopLevelComponent(builder.roundedBorderPanelColour) : contentPane);
   }


   private JComponent initialiseContentPane(final Builder builder)
   {
      if (builder.roundedBorderPanelColour != null)
      {
         if (builder.radialGradient != null)
         {
            final RoundedPanel roundedPanel = new RoundedPanel(builder.radialGradient);
            roundedPanel.setBackground(UIConstants.ContentPanelColour);
            return roundedPanel;
         }
         else
            return new RoundedPanel();
      }
      else
      {
         if (builder.radialGradient != null)
            return new ShadedComponent(builder.radialGradient);
         else
            return new JPanel(null);
      }
   }


   private JComponent initialiseTopLevelComponent(final Color roundedBorderPanelColour)
   {
      final JPanel panel = new JPanel(null);

      panel.setBackground(roundedBorderPanelColour);

      final GroupLayout panelLayout = new GroupLayout(panel);
      panel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(contentPane)
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(contentPane)
         .addContainerGap()
      );

      return panel;
   }


   private void initialise(final JComponent topLevelChildComponent)
   {
      initialiseLayout(topLevelChildComponent);
   }


   private void initialiseLayout(final JComponent topLevelChildComponent)
   {
      final GroupLayout panelLayout = new GroupLayout(borderPanel);
      borderPanel.setLayout(panelLayout);

      final int titleBarHeight = (borderTitle != null) ? (borderPanel.getFontMetrics(UIConstants.RegularFont).getHeight() + (BorderTitleTopClearance + BorderTitleBottomClearance)) : BorderWidth;

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addGap(BorderWidth)
         .addComponent(topLevelChildComponent)
         .addGap(BorderWidth)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addGap(titleBarHeight)
         .addComponent(topLevelChildComponent)
         .addGap(BorderWidth)
      );
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class BorderPanel extends JPanel
   {
      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         final Graphics2D graphics2D = (Graphics2D) graphics.create();

         graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

         graphics2D.setColor(BorderPanelColor1);
         graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
         graphics2D.setColor(BorderPanelColor2);
         graphics2D.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 9, 9);
         graphics2D.setColor(BorderPanelColor3);

         if (borderTitle != null)
         {
            graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics2D.setFont(UIConstants.RegularFont);

            final FontMetrics titleBarFontMetrics = graphics2D.getFontMetrics();
            final int titleBarHeight = titleBarFontMetrics.getHeight() + (BorderTitleTopClearance + BorderTitleBottomClearance);

            graphics2D.drawRect(2, titleBarHeight - 4, getWidth() - 5, getHeight() - titleBarHeight + 1);
            graphics2D.drawRect(3, titleBarHeight - 3, getWidth() - 7, getHeight() - titleBarHeight - 1);
            graphics2D.setColor(BorderPanelColor4);
            graphics2D.drawRect(4, titleBarHeight - 2, getWidth() - 9, getHeight() - titleBarHeight - 3);
            graphics2D.setColor(BorderPanelColor5);
            graphics2D.drawRect(5, titleBarHeight - 1, getWidth() - 11, getHeight() - titleBarHeight - 5);

            final GradientPaint titleBarGradientPaint = new GradientPaint(0, 0, BorderPanelGradientStartColour, 0, titleBarHeight - 6, BorderPanelGradientEndColour);
            graphics2D.setPaint(titleBarGradientPaint);
            graphics2D.fillRect(2, 2, getWidth() - 4, titleBarHeight - 6);

            graphics2D.setColor(Color.black);
            graphics2D.drawString(borderTitle, BorderWidth + BorderTitleLeftClearance, titleBarHeight - BorderTitleBottomClearance - titleBarFontMetrics.getMaxDescent());
         }
         else
         {
            graphics2D.drawRect(2, 2, getWidth() - 5, getHeight() - 5);
            graphics2D.drawRect(3, 3, getWidth() - 7, getHeight() - 7);
            graphics2D.setColor(BorderPanelColor4);
            graphics2D.drawRect(4, 4, getWidth() - 9, getHeight() - 9);
            graphics2D.setColor(BorderPanelColor5);
            graphics2D.drawRect(5, 5, getWidth() - 11, getHeight() - 11);
         }

         graphics2D.dispose();
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final public class Builder
   {
      private String borderTitle;

      private Color roundedBorderPanelColour;

      private RadialGradientPaintProfile radialGradient;

      private int shadowLength = UIConstants.ComponentShadowLength;
      private int maximumShadowTransparency = UIConstants.ComponentMaximumShadowTransparency;


      public Builder()
      {
      }


      public Builder(final String borderTitle)
      {
         this.borderTitle = borderTitle;
      }


      final public void setBorderTitle(String borderTitle)
      {
         this.borderTitle = borderTitle;
      }


      final public void setRoundedBorderPanelColour(final Color nestedContentPaneColour)
      {
         this.roundedBorderPanelColour = nestedContentPaneColour;
      }


      final public void setRadialGradientPaint(final RadialGradientPaintProfile radialGradient)
      {
         this.radialGradient = radialGradient;
      }


      final public void setBorderShadow(final int shadowLength, final int maximumShadowTransparency)
      {
         this.shadowLength = shadowLength;
         this.maximumShadowTransparency = maximumShadowTransparency;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final public JComponent getDelegate()
   {
      return delegatePanel;
   }


   final public JComponent getContentPane()
   {
      return contentPane;
   }
}