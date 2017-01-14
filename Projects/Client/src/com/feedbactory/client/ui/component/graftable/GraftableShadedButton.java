
package com.feedbactory.client.ui.component.graftable;


import com.feedbactory.client.ui.component.ShadedButton;
import com.feedbactory.client.ui.component.ShadedComponent;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.SwingUtilities;

import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.*;
import java.awt.*;


final public class GraftableShadedButton extends GraftableSwingComponent<ShadedButton>
{
   static final private Set<GraftableComponentEventType> EventsOfInterest = Collections.unmodifiableSet(EnumSet.of(Control, MouseClick, MouseTrack));

   final private ShadedButtonDelegate delegate;

   private String toolTipText;


   public GraftableShadedButton()
   {
      this(ShadedButton.DefaultButtonCornerRadius);
   }


   public GraftableShadedButton(final int cornerRadius)
   {
      delegate = new ShadedButtonDelegate(cornerRadius);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class ShadedButtonDelegate extends ShadedButton
   {
      private ShadedButtonDelegate(final int cornerRadius)
      {
         super(cornerRadius);
      }


      @Override
      final public boolean isShowing()
      {
         return true;
      }


      @Override
      final public void repaint()
      {
         repaintComponent();
      }


      @Override
      final public void repaint(final long milliseconds)
      {
         repaintComponent();
      }


      @Override
      final public void repaint(final int x, final int y, final int width, final int height)
      {
         repaintComponent();
      }


      @Override
      final public void repaint(final Rectangle repaintRectangle)
      {
         repaintComponent();
      }


      @Override
      final public void repaint(final long milliseconds, final int x, final int y, final int width, final int height)
      {
         repaintComponent();
      }


      private void repaintComponent()
      {
         /* Making this check here because it's known that the ShadedButton will call repaint() on a non-Swing thread;
          * the forwarded call to the GraftableSwingComponent's repaint() must however be performed on the Swing thread.
          */
         if (SwingUtilities.isEventDispatchThread())
            GraftableShadedButton.this.repaint();
         else
            SwingUtilities.invokeLater(new Runnable()
            {
               @Override
               final public void run()
               {
                  GraftableShadedButton.this.repaint();
               }
            });
      }


      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         super.paintComponent(graphics);
      }
   }


   /****************************************************************************
    *
    *
    * 
    ***************************************************************************/


   @Override
   final public ShadedButton getDelegateComponent()
   {
      return delegate;
   }


   @Override
   final protected BufferedImage createOutputImage(final int width, final int height)
   {
      /* Using alpha premultiplied images will sometimes result in darkened edges of button icons when the icon is drawn directly onto a cleared background
       * rather than onto an existing radial gradient. The effect is very subtle but more noticeable with the Feedbactory logo, where there is some colour.
       * For some reason the requirement here is reversed from that of the box blur images in UIUtilities - alpha premultiplied images are required there
       * in order to -prevent- the effect...! See UIUtilities.boxBlurImage() for more details.
       */
      return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
   }


   @Override
   final public Set<GraftableComponentEventType> getEventsOfInterest()
   {
      return EventsOfInterest;
   }


   @Override
   final public void receiveMouseEnteredEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      super.receiveMouseEnteredEventFromSWTPeer(mouseEvent);

      if (SwingUtilities.isEventDispatchThread())
         setToolTipTextOnPeer(toolTipText);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               setToolTipTextOnPeer(toolTipText);
            }
         });
   }


   private void setToolTipTextOnPeer(final String buttonToolTip)
   {
      if (toolTipText != null)
         graftableComponentPeer.setPeerToolTipText(buttonToolTip);
   }


   @Override
   final public void receiveMouseExitedEventFromSWTPeer(final org.eclipse.swt.events.MouseEvent mouseEvent)
   {
      super.receiveMouseExitedEventFromSWTPeer(mouseEvent);

      if (SwingUtilities.isEventDispatchThread())
         setToolTipTextOnPeer(null);
      else
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               setToolTipTextOnPeer(null);
            }
         });
   }


   @Override
   final protected void paintComponent(final Graphics2D graphics2D)
   {
      delegate.paintComponent(graphics2D);
   }


   @Override
   final public void preDispose()
   {
      delegate.dispose();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void setImage(final BufferedImage buttonImage)
   {
      delegate.setImage(buttonImage);
   }


   final public void setFont(final Font font)
   {
      delegate.setFont(font);
   }


   final public void setText(final String buttonText)
   {
      delegate.setText(buttonText);
   }


   final public void setMouseOverRadialGradientPaint(final ShadedComponent.RadialGradientPaintProfile mouseOverGradient)
   {
      delegate.setMouseOverRadialGradientPaint(mouseOverGradient);
   }


   final public void setPressedRadialGradientPaint(final ShadedComponent.RadialGradientPaintProfile pressedGradient)
   {
      delegate.setPressedRadialGradientPaint(pressedGradient);
   }


   final public void setAlertRadialGradientPaint(final ShadedComponent.RadialGradientPaintProfile alertGradient)
   {
      delegate.setAlertRadialGradientPaint(alertGradient);
   }


   final public void setEnabled(final boolean isEnabled)
   {
      delegate.setEnabled(isEnabled);
   }


   final public void setToolTipText(final String toolTipText)
   {
      this.toolTipText = toolTipText;
   }


   final public void showButtonAlert()
   {
      delegate.showButtonAlert();
   }


   final public void cancelButtonAlert()
   {
      delegate.cancelButtonAlert();
   }


   final public void addActionListener(final ShadedButton.ActionListener actionListener)
   {
      delegate.addActionListener(actionListener);
   }


   final public void removeActionListener(final ShadedButton.ActionListener actionListener)
   {
      delegate.removeActionListener(actionListener);
   }
}