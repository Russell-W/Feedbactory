

package com.feedbactory.client.ui.component.graftable;


import com.feedbactory.client.ui.component.SmileyProgressBar;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.SwingUtilities;

import static com.feedbactory.client.ui.component.graftable.GraftableComponentEventType.*;
import java.awt.Color;


final public class GraftableSmileyProgressBar extends GraftableSwingComponent<SmileyProgressBar>
{
   static final private Set<GraftableComponentEventType> EventsOfInterest = Collections.unmodifiableSet(EnumSet.of(Control));

   final private SmileyProgressBar delegate;


   public GraftableSmileyProgressBar()
   {
      delegate = new SmileyProgressBarDelegate((byte) 50);
   }


   public GraftableSmileyProgressBar(final byte smileyValue)
   {
      delegate = new SmileyProgressBarDelegate(smileyValue);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class SmileyProgressBarDelegate extends SmileyProgressBar
   {
      private SmileyProgressBarDelegate(final byte value)
      {
         super(value);
      }


      /****************************************************************************
       *
       ***************************************************************************/


      /* Overridden paint methods fired off when the progress bar appearance changes either
       * due to a value change, or during animation when it's in an indeterminate state.
       * We forward these to our graftable's repaint() routine, which in turn will
       * call the graftable's paintComponent().
       */
      @Override
      final public void repaint()
      {
         repaintGraftablePeer();
      }


      @Override
      final public void repaint(final long milliseconds)
      {
         repaintGraftablePeer();
      }


      @Override
      final public void repaint(final int x, final int y, final int width, final int height)
      {
         repaintGraftablePeer();
      }


      @Override
      final public void repaint(final Rectangle repaintRectangle)
      {
         repaintGraftablePeer();
      }


      @Override
      final public void repaint(final long milliseconds, final int x, final int y, final int width, final int height)
      {
         repaintGraftablePeer();
      }


      private void repaintGraftablePeer()
      {
         if (SwingUtilities.isEventDispatchThread())
            GraftableSmileyProgressBar.this.repaint();
         else
            SwingUtilities.invokeLater(new Runnable()
            {
               @Override
               final public void run()
               {
                  GraftableSmileyProgressBar.this.repaint();
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
   final public SmileyProgressBar getDelegateComponent()
   {
      return delegate;
   }


   @Override
   final public Set<GraftableComponentEventType> getEventsOfInterest()
   {
      return EventsOfInterest;
   }


   @Override
   final public void paintComponent(final Graphics2D graphics2D)
   {
      delegate.paintComponent(graphics2D);
   }


   @Override
   final public void preDispose()
   {
      // Ensure that the progress bar is no longer animating.
      delegate.setIndeterminate(false);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public void setBackground(final Color backgroundColour)
   {
      delegate.setBackground(backgroundColour);
   }


   final public void setMaximum(final int maximum)
   {
      delegate.setMaximum(maximum);
   }


   final public void setValue(final int value)
   {
      delegate.setValue(value);
   }


   final public boolean isIndeterminate()
   {
      return delegate.isIndeterminate();
   }


   final public void setIndeterminate(final boolean isIndeterminate)
   {
      delegate.setIndeterminate(isIndeterminate);
   }
}