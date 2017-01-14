package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.ShadedComponent.RadialGradientPaintProfile;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.ToolTipManager;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;


public class ShadowedToolTip extends JToolTip
{
   final private RoundedShadowComponent borderPanel;
   final private JComponent contentPane = new RoundedPanel(new RadialGradientPaintProfile(0f, 0f, 1f, 1f,
                                                           new Color[] {UIConstants.TextToolTipInnerColour, UIConstants.TextToolTipOuterColour},
                                                           new float[] {0f, 1f}));

   private int originalDismissDelayMilliseconds;
   private int dismissDelayMilliseconds;


   public ShadowedToolTip()
   {
      this(UIConstants.ComponentShadowLength, UIConstants.ComponentMaximumShadowTransparency);
   }


   public ShadowedToolTip(final int shadowLength, final int maximumShadowTransparency)
   {
      borderPanel = new RoundedShadowComponent(contentPane, shadowLength, maximumShadowTransparency);

      initialise();
   }


   private void initialise()
   {
      contentPane.setBackground(UIConstants.TextToolTipOuterColour);

      setOpaque(false);

      setBorder(BorderFactory.createEmptyBorder());

      setLayout(new BorderLayout());
      add(borderPanel, BorderLayout.CENTER);

      addAncestorListener(new AncestorListener()
      {
         @Override
         final public void ancestorAdded(final AncestorEvent event)
         {
            if (dismissDelayMilliseconds > 0)
            {
               originalDismissDelayMilliseconds = ToolTipManager.sharedInstance().getDismissDelay();
               ToolTipManager.sharedInstance().setDismissDelay(dismissDelayMilliseconds);
            }
         }


         @Override
         final public void ancestorRemoved(final AncestorEvent event)
         {
            if (originalDismissDelayMilliseconds > 0)
               ToolTipManager.sharedInstance().setDismissDelay(originalDismissDelayMilliseconds);
         }


         @Override
         final public void ancestorMoved(final AncestorEvent event)
         {
         }
      });
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public Dimension getPreferredSize()
   {
      return borderPanel.getPreferredSize();
   }


   @Override
   final protected void paintComponent(final Graphics graphics)
   {
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public JComponent getContentPane()
   {
      return contentPane;
   }


   final public void setDismissDelay(final int dismissDelayMilliseconds)
   {
      this.dismissDelayMilliseconds = dismissDelayMilliseconds;
   }
}