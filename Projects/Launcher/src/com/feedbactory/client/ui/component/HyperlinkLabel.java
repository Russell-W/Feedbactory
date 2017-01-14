
package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIConstants;
import java.awt.Cursor;
import javax.swing.JLabel;


final public class HyperlinkLabel extends JLabel
{
   private String hyperlinkURL;


   public HyperlinkLabel()
   {
      initialise();
   }


   public HyperlinkLabel(final String text, final String hyperlink)
   {
      initialise();
      setHyperlink(text, hyperlink);
   }


   private void initialise()
   {
      setFont(UIConstants.RegularFont);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleSetHyperlink(final String text, final String hyperlink)
   {
      this.hyperlinkURL = hyperlink;
      setText("<html><a href='" + hyperlink + "' style='color:#000040'>" + text + "</a></html>");
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public String getHyperlink()
   {
      return hyperlinkURL;
   }


   final public void setHyperlink(final String text, final String hyperlink)
   {
      handleSetHyperlink(text, hyperlink);
   }
}