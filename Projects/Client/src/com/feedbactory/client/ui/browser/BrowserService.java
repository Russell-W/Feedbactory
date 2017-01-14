/* Memos:
 * - The KeyListener was a late addition to the API, when it became clear that a listener must be registered on each browser instance to allow the Feedbactory pad hotkey (Alt)
 *   to work. Unfortunately it breaks the abstraction of the API a little since it's directly tied to SWT. I don't see too much point in creating a wrapper for its listener
 *   and events, since ultimately the key code and modifier mask should be comparable to key constants, whether in SWT or Swing.
 */

package com.feedbactory.client.ui.browser;


import com.feedbactory.client.ui.browser.event.BrowserDisposeListener;
import com.feedbactory.client.ui.browser.event.BrowserLoadProgressListener;
import com.feedbactory.client.ui.browser.event.BrowserLocationListener;
import com.feedbactory.client.ui.browser.event.BrowserMouseListener;
import com.feedbactory.client.ui.browser.event.BrowserStatusTextListener;
import com.feedbactory.client.ui.browser.event.BrowserTitleListener;
import org.eclipse.swt.events.KeyListener;


public interface BrowserService
{
   public String getPendingURL();
   public String getURL();
   public String getTitle();
   public boolean isLoadingPage();

   public void openURL(final String URL);
   public String getHTML();
   public void setHTML(final String HTML, final boolean isTrusted);
   public void executeJavascript(final String javascript);
   public Object evaluateJavascript(final String javascript);
   public void registerFunction(final String functionName, final BrowserServiceFunction function);
   public void deregisterFunction(final BrowserServiceFunction function);

   public boolean isBackEnabled();
   public void back();
   public boolean isForwardEnabled();
   public void forward();
   public void refresh();
   public void stop();

   public void requestFocus();

   public void addLocationListener(final BrowserLocationListener locationListener);
   public void removeLocationListener(final BrowserLocationListener locationListener);

   public void addLoadProgressListener(final BrowserLoadProgressListener pageLoadProgressListener);
   public void removeLoadProgressListener(final BrowserLoadProgressListener pageLoadProgressListener);

   public void addStatusTextEventListener(final BrowserStatusTextListener statusTextEventListener);
   public void removeStatusEventListener(final BrowserStatusTextListener statusTextEventListener);

   public void addTitleEventListener(final BrowserTitleListener titleEventListener);
   public void removeTitleEventListener(final BrowserTitleListener titleEventListener);

   public void addKeyListener(final KeyListener keyListener);
   public void removeKeyListener(final KeyListener keyListener);

   public void addMouseListener(final BrowserMouseListener mouseListener);
   public void removeMouseListener(final BrowserMouseListener mouseListener);

   public void addDisposeEventListener(final BrowserDisposeListener disposalEventListener);
   public void removeDisposeEventListener(final BrowserDisposeListener disposalEventListener);

   public boolean isClosed();
   public void close();
}