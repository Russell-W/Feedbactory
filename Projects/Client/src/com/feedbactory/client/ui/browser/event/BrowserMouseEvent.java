/* Memos:
 * - Models click events only.
 * - Does not model accompanying key state masks, eg. Shift, Alt, Ctrl.
 */

package com.feedbactory.client.ui.browser.event;


import com.feedbactory.client.ui.browser.BrowserService;


final public class BrowserMouseEvent
{
   final public BrowserService browserService;
   final public int button;
   final public int componentX;
   final public int componentY;


   public BrowserMouseEvent(final BrowserService browserService, final int button, final int componentX, final int componentY)
   {
      this.browserService = browserService;
      this.button = button;
      this.componentX = componentX;
      this.componentY = componentY;
   }
}