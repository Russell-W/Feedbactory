
package com.feedbactory.client.ui.component.graftable;


import com.feedbactory.client.ui.component.BouncingTextLabel;
import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.Control;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.Set;

import java.awt.*;
import java.util.EnumSet;
import javax.swing.SwingUtilities;


final public class GraftableBouncingTextLabel extends GraftableSwingComponent<BouncingTextLabel>
{
   static final private Set<GraftableComponentEventType> EventsOfInterest = Collections.unmodifiableSet(EnumSet.of(Control));

   final private BouncingTextLabelDelegate delegate = new BouncingTextLabelDelegate();


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class BouncingTextLabelDelegate extends BouncingTextLabel
   {
      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         super.paintComponent(graphics);
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
         /* Making this check here because it's known that the JumpingTextLabel will call repaint() on a non-Swing thread;
          * the forwarded call to the GraftableSwingComponent's repaint() must however be performed on the Swing thread.
          */
         if (SwingUtilities.isEventDispatchThread())
            GraftableBouncingTextLabel.this.repaint();
         else
            SwingUtilities.invokeLater(new Runnable()
            {
               @Override
               final public void run()
               {
                  GraftableBouncingTextLabel.this.repaint();
               }
            });
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   @Override
   final public BouncingTextLabel getDelegateComponent()
   {
      return delegate;
   }


   @Override
   final public Set<GraftableComponentEventType> getEventsOfInterest()
   {
      return EventsOfInterest;
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


   final public void setFont(final Font font)
   {
      delegate.setFont(font);
   }


   final public String getText()
   {
      return delegate.getText();
   }


   final public void setText(final String text)
   {
      delegate.setText(text);
   }


   final public void showAnimatedText(final String text)
   {
      delegate.showAnimatedText(text);
   }
}