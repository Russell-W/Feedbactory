
package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIConstants;
import java.awt.Point;
import java.awt.event.*;
import javax.swing.JButton;
import javax.swing.JToolTip;
import javax.swing.text.*;


final public class ShadowedToolTipButton extends JButton
{
   private StyledDocument toolTipStyledDocument;

   private int toolTipDismissDelayMilliseconds;

   private boolean isToolTipShowing;
   private Point toolTipLocation;
   private Point toolTipRequestedOffset;


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public JToolTip createToolTip()
   {
      final ShadowedTextToolTip toolTip = new ShadowedTextToolTip(toolTipStyledDocument);

      if (toolTipDismissDelayMilliseconds > 0)
         toolTip.setDismissDelay(toolTipDismissDelayMilliseconds);

      toolTip.addHierarchyListener(new HierarchyListener()
      {
         @Override
         final public void hierarchyChanged(final HierarchyEvent hierarchyEvent)
         {
            if ((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0)
               handleToolTipShowingStatusChanged(((JToolTip) hierarchyEvent.getSource()));
         }
      });

      return toolTip;
   }


   private void handleToolTipShowingStatusChanged(final JToolTip toolTip)
   {
      isToolTipShowing = toolTip.isShowing();
   }


   @Override
   final public Point getToolTipLocation(final MouseEvent mouseEvent)
   {
      if (toolTipRequestedOffset == null)
         return super.getToolTipLocation(mouseEvent);

      if (! isToolTipShowing)
         toolTipLocation = new Point(mouseEvent.getX() + toolTipRequestedOffset.x, mouseEvent.getY() + toolTipRequestedOffset.y);

      return new Point(toolTipLocation);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSetToolTipText(final String toolTipText)
   {
      this.toolTipStyledDocument = new DefaultStyledDocument();

      final MutableAttributeSet paragraphAttributeSet = new SimpleAttributeSet();
      StyleConstants.setLineSpacing(paragraphAttributeSet, UIConstants.RegularLineSpacing);
      toolTipStyledDocument.setParagraphAttributes(0, 0, paragraphAttributeSet, false);

      final MutableAttributeSet characterAttributeSet = new SimpleAttributeSet();
      StyleConstants.setFontFamily(characterAttributeSet, UIConstants.RegularFont.getFamily());
      StyleConstants.setFontSize(characterAttributeSet, UIConstants.RegularFont.getSize());

      try
      {
         toolTipStyledDocument.insertString(0, toolTipText, characterAttributeSet);
      }
      catch (final BadLocationException badLocationException)
      {
         // Propagate the exception to Swing's default uncaught exception handler.
         throw new RuntimeException(badLocationException);
      }

      super.setToolTipText(toolTipText);
   }


   private void handleSetToolTipText(final StyledDocument styledToolTipText)
   {
      // No way to do a deep copy of the styled document, to protect against mutation by client..?
      this.toolTipStyledDocument = styledToolTipText;

      try
      {
         super.setToolTipText(styledToolTipText.getText(0, styledToolTipText.getLength()));
      }
      catch (final BadLocationException badLocationException)
      {
         throw new RuntimeException(badLocationException);
      }
   }


   private void handleSetToolTipOffset(final Point toolTipRequestedOffset)
   {
      if (toolTipDismissDelayMilliseconds < 0)
         throw new IllegalArgumentException("Invalid tool tip dismiss delay: " + toolTipDismissDelayMilliseconds + " milliseconds.");

      this.toolTipRequestedOffset = new Point(toolTipRequestedOffset);
   }


   private void handleSetToolTipDismissDelay(final int toolTipDismissDelayMilliseconds)
   {
      if (toolTipDismissDelayMilliseconds < 0)
         throw new IllegalArgumentException("Invalid tool tip dismiss delay: " + toolTipDismissDelayMilliseconds + " milliseconds.");

      this.toolTipDismissDelayMilliseconds = toolTipDismissDelayMilliseconds;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   @Override
   final public void setToolTipText(final String text)
   {
      handleSetToolTipText(text);
   }


   final public void setToolTipText(final StyledDocument styledDocument)
   {
      handleSetToolTipText(styledDocument);
   }


   final public void setToolTipOffset(final Point toolTipRequestedOffset)
   {
      handleSetToolTipOffset(toolTipRequestedOffset);
   }


   final public void setToolTipDismissDelayMilliseconds(final int toolTipDismissDelayMilliseconds)
   {
      handleSetToolTipDismissDelay(toolTipDismissDelayMilliseconds);
   }
}