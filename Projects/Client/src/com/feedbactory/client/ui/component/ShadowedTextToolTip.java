
package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIConstants;
import java.awt.*;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import javax.swing.*;
import javax.swing.text.*;


final public class ShadowedTextToolTip extends ShadowedToolTip
{
   final private JTextPane textPane;


   public ShadowedTextToolTip(final String text)
   {
      this(text, UIConstants.ComponentShadowLength, UIConstants.ComponentMaximumShadowTransparency);
   }


   public ShadowedTextToolTip(final String text, final int shadowLength, final int maximumShadowTransparency)
   {
      super(shadowLength, maximumShadowTransparency);

      textPane = new JTextPane();

      initialiseForDefaultText(text);
   }


   public ShadowedTextToolTip(final StyledDocument styledDocument)
   {
      this(styledDocument, UIConstants.ComponentShadowLength, UIConstants.ComponentMaximumShadowTransparency);
   }


   public ShadowedTextToolTip(final StyledDocument styledDocument, final int shadowLength, final int maximumShadowTransparency)
   {
      super(shadowLength, maximumShadowTransparency);

      textPane = new JTextPane(styledDocument);

      initialiseForStyledDocument();
   }


   private void initialiseForDefaultText(final String text)
   {
      initialiseTextPane();

      final MutableAttributeSet paragraphAttributeSet = new SimpleAttributeSet();
      StyleConstants.setLineSpacing(paragraphAttributeSet, UIConstants.RegularLineSpacing);
      textPane.setParagraphAttributes(paragraphAttributeSet, false);

      final MutableAttributeSet characterAttributeSet = new SimpleAttributeSet();
      StyleConstants.setFontFamily(characterAttributeSet, UIConstants.RegularFont.getFamily());
      StyleConstants.setFontSize(characterAttributeSet, UIConstants.RegularFont.getSize());

      try
      {
         textPane.getStyledDocument().insertString(0, text, characterAttributeSet);
      }
      catch (final BadLocationException badLocationException)
      {
         throw new RuntimeException(badLocationException);
      }

      initialiseContentPane();
   }


   private void initialiseForStyledDocument()
   {
      initialiseTextPane();

      initialiseContentPane();
   }


   private void initialiseTextPane()
   {
      textPane.setEditable(false);

      /* Unlike most other components the text pane will still paint its background when it is setOpaque(false), so we need to apply the clear colour trick.
       * There's still a legit reason to set the pane to non-opaque though: this acts as a signal for the -parent- underneath that it will need to be repainted
       * since it believes that textPane doesn't fully paint its bounds. For the majority of cases this is unnecessary however if there happens to be a
       * blinking cursor underneath the tooltip, it will actually bleed through if we only set the background colour to clear without the call to setOpaque(false) 
       * (because normally the paint manager would see that the nested text pane is opaque - assuming solid painting colours - and think that there's no need to
       * even bother with repainting the parent panel - in this case a RoundedPanel).
       */
      textPane.setOpaque(false);
      textPane.setBackground(UIConstants.ClearColour);

      /* HACK: Removing mouse listeners on the text pane to prevent the user from being able to select text on the tooltip, which looks tacky.
       * I tried using disableEvents(AWTEvent.MouseXXX | AWTEvent.TextXXX) on an overridden delegate text pane, without any success. For the reason behind this,
       * look at the source for Component.eventTypeEnabled() - the event will be processed if the mask is enabled OR a listener for that event is present.
       * setEnabled(false) works, however has the drawback that all text is greyed out no matter how we try to apply the styles on the StyledDocument.
       */
      final MouseListener[] mouseListeners = textPane.getMouseListeners();
      for (final MouseListener mouseListener : mouseListeners)
         textPane.removeMouseListener(mouseListener);

      final MouseMotionListener[] mouseMotionListeners = textPane.getMouseMotionListeners();
      for (final MouseMotionListener mouseMotionListener : mouseMotionListeners)
         textPane.removeMouseMotionListener(mouseMotionListener);
   }


   private void initialiseContentPane()
   {
      final JComponent contentPane = getContentPane();
      contentPane.setLayout(new BorderLayout());
      contentPane.add(textPane, BorderLayout.CENTER);
   }
}