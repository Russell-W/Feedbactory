
package com.feedbactory.client.ui.component.graftable;


import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.JLabel;

import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.*;
import java.awt.*;
import javax.swing.Icon;


final public class GraftableLabel extends GraftableSwingComponent<JLabel>
{
   static final private Set<GraftableComponentEventType> EventsOfInterest = Collections.unmodifiableSet(EnumSet.of(Control));

   final private LabelDelegate delegate = new LabelDelegate();


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class LabelDelegate extends JLabel
   {
      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         super.paintComponent(graphics);
      }


      @Override
      final public void repaint()
      {
         GraftableLabel.this.repaint();
      }


      @Override
      final public void repaint(final long milliseconds)
      {
         GraftableLabel.this.repaint();
      }


      @Override
      final public void repaint(final int x, final int y, final int width, final int height)
      {
         GraftableLabel.this.repaint();
      }


      @Override
      final public void repaint(final Rectangle repaintRectangle)
      {
         GraftableLabel.this.repaint();
      }


      @Override
      final public void repaint(final long milliseconds, final int x, final int y, final int width, final int height)
      {
         GraftableLabel.this.repaint();
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   @Override
   final public JLabel getDelegateComponent()
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


   /****************************************************************************
    *
    ***************************************************************************/


   final public void setIcon(final Icon icon)
   {
      delegate.setIcon(icon);
   }


   final public void setFont(final Font font)
   {
      delegate.setFont(font);
   }


   final public void setHorizontalAlignment(final int horizontalAlignment)
   {
      delegate.setHorizontalAlignment(horizontalAlignment);
   }


   final public void setVerticalAlignment(final int verticalAlignment)
   {
      delegate.setVerticalAlignment(verticalAlignment);
   }


   final public void setText(final String text)
   {
      delegate.setText(text);
   }
}