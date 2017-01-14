
package com.feedbactory.client.ui.component.graftable;


import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.Set;

import java.awt.*;
import javax.swing.JSeparator;


final public class GraftableSeparator extends GraftableSwingComponent<JSeparator>
{
   final private SeparatorDelegate delegate = new SeparatorDelegate();


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class SeparatorDelegate extends JSeparator
   {
      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         super.paintComponent(graphics);
      }


      @Override
      final public void repaint()
      {
         GraftableSeparator.this.repaint();
      }


      @Override
      final public void repaint(final long milliseconds)
      {
         GraftableSeparator.this.repaint();
      }


      @Override
      final public void repaint(final int x, final int y, final int width, final int height)
      {
         GraftableSeparator.this.repaint();
      }


      @Override
      final public void repaint(final Rectangle repaintRectangle)
      {
         GraftableSeparator.this.repaint();
      }


      @Override
      final public void repaint(final long milliseconds, final int x, final int y, final int width, final int height)
      {
         GraftableSeparator.this.repaint();
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   @Override
   final public JSeparator getDelegateComponent()
   {
      return delegate;
   }


   @Override
   final public Set<GraftableComponentEventType> getEventsOfInterest()
   {
      return Collections.emptySet();
   }


   @Override
   final protected void paintComponent(final Graphics2D graphics2D)
   {
      delegate.paintComponent(graphics2D);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void setOrientation(final int orientation)
   {
      delegate.setOrientation(orientation);
   }
}