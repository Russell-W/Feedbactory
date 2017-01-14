/* Enhancements:
 * - Assertion checks for Sync'd calls between Swing & SWT. Aside from the initial SWT -> Swing event calls, when should they never happen?
 *    - Keeping in mind that a sync'd chain such as SWT -> Swing -> SWT (or vise versa) will cause deadlock.
 * 
 * 
 * Memos:
 *
 * - Trying to set a custom font on the CTabFolder, at any stage (eg. after initialisation, and UI launch) will cause a lockup for some reason.
 *   - Trying to set a custom font on the status bar label results in nothing being displayed at all.
 *   - Setting a font on the top level shell (hoping that child components will inherit the attribute) won't result in a lockup, but it has no effect.
 *   - The Roboto font does work when applied to our custom SWTNimbusFrame, but it somehow doesn't look the same as the Roboto rendered in Swing. So,
 *        it may be best to just leave the frame title painted using the default system font, which after all looks like it has to be used with the
 *        browser tab folder and the status bar (this we could change with a custom painted Widget, I suppose...).
 *
 * - Caution must be taken when shutting down the graftable component framework. The method calls within the framework follow this pattern:
 * 
 * SWT components ----- async calls -----> Swing components ----- async and/or sync calls for repaints or setting of cursor, etc -----> back to SWT components
 * 
 * (Note the async calls from SWT to Swing, always. Deadlock would occur if we did otherwise. Regardless of our async rule, deadlocks may also occur if we're not careful with
 * synchronized blocks that may be held (for eg.) by an SWT thread, during which a call is branched to Swing which also tries to acquire a lock on the sync object.)
 *
 * Due to the asynchronous communication, we usually can't be sure from the SWT end that there aren't more repaint requests on their way. This comes to a head at the time of
 * shutdown, when we stop pumping out SWT events and dispose of the display elements. How can we safely stop the flow of requests coming back from the Swing components,
 * and avoid the need for messy exception handling/checking of volatile shutdown flags, etc?
 * 
 * A strategy that seems to work is a two-phase shutdown, where each phase occurs synchronously on each SWT and Swing event dispatch thread. During the pre-shutdown
 * phase we can 'disconnect' the coupling from the SWT components to their Swing peers, so that the flow of events stops. Once the flow of SWT events to the Swing
 * components has stopped, the Swing components will generally stop responding to inputs by requesting repaints or other methods on the SWT peers. For this approach to work
 * we also need to stop any Swing animation tasks which have scheduled repaints being pushed back to SWT. It's important to note that the disconnect at this stage is only
 * one way. Even as we are shutting down the flow of new events from SWT components, those components will likely be still receiving the requests from Swing. This is fine,
 * because although we have muted the events, the components themselves are not yet disposed. Otherwise we would have to deal with a bunch of SWT.ERROR_WIDGET_DISPOSED
 * exceptions which is primarily what we're trying to avoid.
 * 
 * So the shutdown looks something like:
 * 
 * Pre-shutdown Phase:
 * 
 * 1a) Synchronous pre-shutdown of Swing elements.
 *    - Stop Swing animation tasks which may be pumping repaint requests.
 *    - Clean up focus, eg. if a component still has focus, it needs to relinquish it (which will trigger other events, eg. pop-ups to close, repaints to happen..).
 *    - (Optional) Disconnect Swing graftable components within nested Swing components (eg. panel). Note that this is disconnecting the flow of events from the
 *         Swing parents to the Swing child components, but not the other way around; there may still be Swing paint requests in transit to parent/peers up until (but
 *         not during) phase 2a).
 *
 * 1b) Synchronous pre-shutdown of SWT elements.
 *    - Disconnect listeners on SWT components that are pumping asynchronous events to Swing top level components.
 *    - Stop SWT animation tasks (eg. water panel) which may be pumping repaint requests.
 *    - Remove all browser listeners.
 * 
 * Shutdown Phase:
 * 
 * 2a) Synchronous shutdown of Swing elements.
 *    - Actual disposal of Swing components. No operations taken here should result in events being fired back to the SWT peers in response.
 * 
 * 2b) Synchronous shutdown of SWT elements.
 *    - Actual disposal of SWT components.
 * 
 * 
 * The synchronous execution of each phase (in contrast to the async execution of many of the events fired off throughout the normal operation of the graftable framework)
 * is crucial to the success of the shutdown. Success being a clean shutdown, with no lingering requests that are going to be fired on disposed widgets. If we simply fired
 * off each phase as an invokeLater() or asyncExec() request, it would be possible for 1a) to still be running as 2b) is disposing of the SWT components - not good.
 * 
 * Our scheme relies on Swing to always execute its invokeLater() and invokeNow() tasks in the order in which they are submitted. So, even if a task is submitted to
 * invokeLater(), it will always be scheduled to run before a task submitted to invokeNow() on the following line. In the same way we are also relying on SWT to be
 * deterministic in its scheduling of submitted events via asyncExec() and syncExec().
 *
 * Consider the final UI events fired from SWT to Swing, just before the initial trigger to shutdown is fired off. These tasks must have been submitted to Swing's invokeLater()
 * before 1a) (in the case of the user initiating shutdown via Swing EDT) or at the very least before 1b) (in the case of the user initiating shutdown via SWT EDT). Either
 * way, the UI event must have been submitted to the Swing dispatcher, and have been executed, before 2a) can have commenced. Keeping in mind our note above about the
 * invokeLater() & invokeNow() tasks always being executed in the sequential order of their submission. The Swing UI task may in turn request a repaint or cursor change,
 * requests which are submitted back to the SWT peer asynchronously or synchronously. Again it's the timing of the task submission that is crucial. So any repaint requests
 * or other requests which result in direct calls on the SWT components will be submitted (either to SWT's syncExec or asyncExec) and executed before 2b), which is when the
 * SWT components are finally disposed. Applying the same logic as with Swing's EDT, the synchronous SWT call at 2b) will first flush pending SWT tasks before it runs. So, not
 * only will the task submissions have been flushed from Swing and submitted to SWT, but the SWT tasks themselves will all have been completed by the time that 2b) actually
 * executes and starts disposing of the widgets.
 * 
 * The logic is tricky to follow and a bit delicate. It essentially relies on the fact that the graftable framework does all of its work via chained SWT and Swing
 * EDT tasks. It would seem all too easy for a rogue animation thread, for example, which is unaffected by the shutdown procedure, to be firing off repaint
 * requests to SWT components even after the pre-shutdown phase has completed. A prime candidate is the ShadedButton, which has an alert animation
 * effect scheduled to run off its own independent thread. This thread fires off requests to Swing's repaint(), which asynchronously calls the component's
 * paintComponent() method on the Swing EDT, which (in the case of the GraftableShadedButton) will ultimately result in an asychronous call to SWT's canvas redraw
 * method. The vulnerable point is in the animation's independent worker thread. Before requesting any repaints, the thread needs a safe way of checking that a shutdown
 * has not been scheduled since its previous drawing frame, hence the locking and shutdown notification scheme used in classes like ShadedButton - if there is a shutdown
 * in progress, the task which would normally schedule a repaint must bail out without pushing any more events to SWT. If there is no shutdown scheduled, the repaint()
 * call will be executed (with the lock still being held) and will result in a call to Swing's scheduler, from which point the task is safely placed in the EDT's queue as
 * described above, and from there we know that as before the repaint on the SWT peer must be carried out before 2b) can begin.
 * 
 * Prior to the two-phase shutdown strategy I employed a scheme using a volatile isShuttingDown thread, with exception handlers scattered about the code that made
 * direct calls to the SWT components. The logic was something along the lines of "if we're shutting down and the exception is of type widget disposed, then just
 * ignore it". This worked fine but also had the drawbacks of a) additional exception handling code where it's only needed for shutdown, and b) keeping the exceptions
 * hidden from the client objects, which have a right to know if something's wrong.
 * 
 * There's also another approach which I experimented with, involving Semaphores, and each task from Swing to SWT requesting a ticket. At the time of shutdown,
 * our shutdown thread requests all tickets, which ensures that no tasks are still pending. This scheme would work fine and reasonably cleanly if not for the fact that
 * the graftable framework allows components to submit arbitrary tasks (ie. not just the setPeerCursor or setPeerToolTip ones), which would put the onus on the
 * Swing peers to release the semaphore tickets once their tasks are done. All this in the name of a clean shutdown - not good.
 *
 * - Another hazard to be aware of, which sort of ties in with the shutdown scheme described above but really applies throughout the entire app: we need to be very careful
 * about using synchronized blocks where there is a possibility of (for eg.) a Swing thread pushing a synchronous request back to SWT while holding a lock; and the SWT
 * task unfortunately tries to acquire a lock on the same object: deadlock. This is a good illustration of the Java advice to avoid calling 'foreign' methods while a lock
 * is being held.
 */


package com.feedbactory.client.ui.browser;


import com.feedbactory.client.core.ConfigurationManager;
import com.feedbactory.client.core.network.ClientNetworkConstants;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.UIManager;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.component.SWTFrame;
import com.feedbactory.client.ui.component.SWTNimbusFrame;
import com.feedbactory.client.ui.component.WateryPanel;
import com.feedbactory.client.ui.component.graftable.GraftableComponentSWTPeer;
import com.feedbactory.client.ui.component.graftable.GraftableComponentSwingFramework;
import com.feedbactory.client.ui.feedback.FeedbackUIManager;
import com.feedbactory.client.ui.pad.FeedbactoryPadUIView;
import com.feedbactory.client.ui.useraccount.AccountUIManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Pattern;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;


final public class FeedbactoryBrowserWindow
{
   /* To store the Feedbactory start web page locally or not?
    * On the one hand it would be nice even though the resources (images, css, etc) would still need to be loaded from elsewhere anyway. It would mean that the Feedbactory
    * welcome page would not be accessible from anywhere except from within the application.
    * Internet Explorer handles this fine when using setText(html, trusted == false) on the browser instance, however calling setText(html, true or false) on WebKit
    * seems to put it into an untrusted state where it won't load the CSS. Strangely, opening a new browser tab then seems to inherit the untrusted state even when
    * trying to load the welcome page (and the regular Feedbactory page) directly off the internet instead of using setText - maybe because the shared CSS resources have
    * already been marked as untrusted by WebKit at a global level..? setURL(local file) seems to work fine for both browsers, although it would mean that I would have
    * to bundle the bare bones web page with the downloaded app payload, either outside the jar or within which would then have to be extracted as a jar resource.
    *
    * The arguments against bundling a local start web page are:
    * - I would need to implement some extra logic if loading it via setText() for when the user refreshes the page, otherwise a refresh sets the page to about:blank.
    * - I would not be able to display important notices eg. via Bootstrap modals on the page, except via Feedbactory's broadcast system on the admin console.
    *   - This alone might be worth abandoning the local page option, as an important notices system would be so handy.
    *   - I guess the local page could always use import to check for notices from the web server.
    * - If I include ads on the page I'm not sure whether users loading it locally would invalidate them.
    */
   static final String FeedbactoryWelcomePage = ClientNetworkConstants.FeedbactoryWebServer + "/recentFeedback/photography/index.html";

   static final private Point MinimumWindowSize = new Point(1150, 700);
   static final private float BrowserInitialDisplayProportions = 0.95f;

   static final private String IsBrowserMaximisedPreferencesKey = "IsBrowserMaximised";
   static final private String BrowserBoundsPreferencesKey = "BrowserBounds";

   static final private RGB PanelStartGradientColour = new RGB(214, 217, 223);
   static final private RGB PanelEndGradientColour = new RGB(188, 191, 195);

   static final private int StatusBarHeight = 18;

   final private UIManager uiManager;

   final private Display swtDisplay;

   // Initialised on SWT thread, must be visible to Swing thread.
   final private SWTFrame applicationFrame;

   // Initialised on SWT thread, must be visible to Swing thread during initialisation and shutdown.
   final private GraftableComponentSWTPeer controlPanelSWTPeer;
   private WateryPanel controlPanelWateryPanel;

   private Color controlPanelGradientStartColour;
   private Color controlPanelGradientEndColour;
   private Pattern controlPanelGradientPattern;

   // Initialised on SWT thread, must be visible to Swing thread during initialisation and shutdown.
   final private GraftableComponentSWTPeer urlEntryComboBoxPopupMenuSWTPeer;
   private WateryPanel urlEntryComboBoxPopupMenuWateryPanel;

   final private GraftableComponentSwingFramework graftableComponentFramework = new GraftableComponentSwingFramework();
   final private BrowserControlPanel controlPanel;

   private Font tabFolderFont;

   private BrowserStatusBar statusBar;

   // Initialised on SWT thread, must be visible to Swing thread during initialisation.
   final private BrowserEngineManager browserEngineManager;


   public FeedbactoryBrowserWindow(final AccountSessionManager userAccountManager, final UIManager uiManager, final Display swtDisplay,
                                   final AccountUIManager userAccountUIManager, final FeedbactoryPadUIView feedbactoryPadClientUIView)
   {
      assert SwingUtilities.isEventDispatchThread();

      this.uiManager = uiManager;
      this.swtDisplay = swtDisplay;

      if (ConfigurationManager.isRunningWindows)
         preInitialiseSWTNimbusFrame();

      try
      {
         applicationFrame = initialiseApplicationFrame();

         controlPanelSWTPeer = initialiseBrowserControlPanelSWTPeer();

         urlEntryComboBoxPopupMenuSWTPeer = initialiseURLEntryComboBoxSWTPeer();

         browserEngineManager = initialiseBrowserEngineManager();

         initialiseStatusBar();

         controlPanel = new BrowserControlPanel(userAccountManager, this, browserEngineManager,
                                                graftableComponentFramework, controlPanelSWTPeer, urlEntryComboBoxPopupMenuSWTPeer,
                                                userAccountUIManager, feedbactoryPadClientUIView);

         initialiseApplicationFrameBounds();
      }
      catch (final Exception anyException)
      {
         try
         {
            /* We may have been midway through SWT and Swing resource allocations at the time of the exception. Since our parent won't have
            * a constructed object reference for this instance on which to dispose of those resources, we should make an attempt of it ourself.
            */
            preDispose();
            dispose();
         }
         catch (final Exception shutdownException)
         {
            // Log it, but don't rethrow it - we will be rethrowing the cause of the original exception instead.
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error during Feedbactory browser shutdown.", shutdownException);
         }

         if (anyException instanceof RuntimeException)
            throw (RuntimeException) anyException;
         else
            throw new RuntimeException(anyException);
      }
   }


   private void preInitialiseSWTNimbusFrame()
   {
      /* Nimbus Frame requires (synchronous) preloading of Nimbus images on the Swing EDT.
       * An alternative to using this method would be for the SWTNimbusFrame to do its own lazy loading of the Swing images during object creation.
       * However, since the Swing call would still have to be synchronous - keeping in mind that this browser object is created via a Swing thread, so we need to wary of
       * deadlock - it would mean that either the SWTNimbusFrame NOT be constructed via the SWT thread (lots of extra hassle for the caller when there are other SWT children
       * of the frame to instantiate), or we would have to forgo constructing this browser UI object on the Swing thread, and propagate the extra complexity to the parent.
       */
      SWTNimbusFrame.preInitialiseNimbus();
   }


   private SWTFrame initialiseApplicationFrame()
   {
      final AtomicReference<SWTFrame> applicationFrameReference = new AtomicReference<SWTFrame>();

      swtDisplay.syncExec(new Runnable()
      {
         @Override
         final public void run()
         {
            final SWTFrame frame = ConfigurationManager.isRunningWindows ? new SWTNimbusFrame(swtDisplay) : new SWTFrame(swtDisplay);

            frame.setMinimumSize(MinimumWindowSize.x, MinimumWindowSize.y);

            frame.setFrameIcon(new Image(swtDisplay, getClass().getResourceAsStream(UIConstants.ApplicationIconLargePath)));

            // Previously this method allowed the setting of a font by name, however the default OS fonts appear to render better than a custom font.
            frame.setFrameTitle(UIConstants.ApplicationTitle);

            frame.getFrameRootComponent().setLayout(new FormLayout());

            applicationFrameReference.set(frame);
         }
      });

      return applicationFrameReference.get();
   }


   private void initialiseSWTUIFont()
   {
      /* I'm going to leave this code here, in case it's worth revisiting.
       * The aim was to make the fonts in the browser window consistent, by loading the Roboto font into SWT. Unfortunately there's a very roundabout way for
       * SWT to load fonts when using jars - a temporary file has to be created so that Display.loadFont(file path) can work.
       *
       * I got this to work but aside from the kludge of the SWT font load, there are some other drawbacks:
       * - Since the font family name must be used when instantiating a new SWT font, Roboto-bold clashes with Roboto-regular. Seems I can only use one?
       *   And the font of the bolded 'New' tab when using Roboto-regular definitely isn't right, looks too chunky.
       * - There's too much of a jump in the Roboto font sizes as interpreted by SWT. Font size 8 is too small (and produces a weird shrunken down 'F'),
       *   and font size 9 is too large.
       * - It seems that the temporary file must stay around for the duration of the app for it to be available.
       *
       * I think that at least on Windows, the default tab folder font looks better anyway, and doesn't seem to clash much with the Roboto used in the URL entry.
       */
      BufferedInputStream existingFontFileInputStream = null;
      BufferedOutputStream temporaryFontFileOutputStream = null;

      try
      {
         final File temporaryFontFile = File.createTempFile("TitleBarFont", "ttf");
         // The font file has to be in place for the duration of the app for it to work.
         temporaryFontFile.deleteOnExit();

         existingFontFileInputStream = new BufferedInputStream(getClass().getResourceAsStream(UIConstants.UIRegularFontLocation));
         temporaryFontFileOutputStream = new BufferedOutputStream(new FileOutputStream(temporaryFontFile));

         int nextByte;

         while ((nextByte = existingFontFileInputStream.read()) != -1)
            temporaryFontFileOutputStream.write(nextByte);

         existingFontFileInputStream.close();
         temporaryFontFileOutputStream.close();

         swtDisplay.loadFont(temporaryFontFile.getAbsolutePath());
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }
      finally
      {
         try
         {
            if (existingFontFileInputStream != null)
            {
               existingFontFileInputStream.close();

               if (temporaryFontFileOutputStream != null)
                  temporaryFontFileOutputStream.close();
            }
         }
         catch (final IOException ioException)
         {
            throw new RuntimeException(ioException);
         }
      }
   }


   private GraftableComponentSWTPeer initialiseBrowserControlPanelSWTPeer()
   {
      final AtomicReference<GraftableComponentSWTPeer> controlPanelPeerReference = new AtomicReference<GraftableComponentSWTPeer>();

      swtDisplay.syncExec(new Runnable()
      {
         @Override
         final public void run()
         {
            /* The control panel canvas is created with the NO_FOCUS flag because it will otherwise always grab the focus even when the user clicks on a
             * non-focusable child component within the canvas. There seems to be no clean way to reject focus within SWT. So, I'm creating a canvas which does
             * not accept focus by default, but which can forcibly request it on certain events, eg. user clicking on a child control.
             *
             * The DOUBLE_BUFFERED flag prevents flickering, which is especially apparent on text fields.
             */
            final Canvas controlPanelCanvas = new Canvas(applicationFrame.getFrameRootComponent(), SWT.DOUBLE_BUFFERED | SWT.NO_FOCUS);

            final FormData controlPanelLayoutFormData = new FormData(SWT.DEFAULT, BrowserControlPanel.ControlPanelHeight);
            controlPanelLayoutFormData.left = new FormAttachment(0);
            controlPanelLayoutFormData.right = new FormAttachment(100);
            controlPanelLayoutFormData.top = new FormAttachment(0);
            controlPanelCanvas.setLayoutData(controlPanelLayoutFormData);

            /* The SWT gradient paint has to be put in place before the (graftable component) watery panel, otherwise the painting will be out of order.
             * This is a bit of a pain in the arse since it means that the implementation has to sit here rather than within the control panel class.
             * If the paint order assumption proves to be unsafe, we could maybe shift the gradient paint to Swing, although this would have to be done within the graftable panel,
             * which is custom overridden within the GraftablePanel class (and does no painting of itself at the moment, only of its child components).
             * See ShadedComponent for another possibility.
             */
            controlPanelGradientStartColour = new Color(controlPanelCanvas.getDisplay(), PanelStartGradientColour);
            controlPanelGradientEndColour = new Color(controlPanelCanvas.getDisplay(), PanelEndGradientColour);

            controlPanelCanvas.addControlListener(new ControlAdapter()
            {
               @Override
               final public void controlResized(final ControlEvent controlEvent)
               {
                  handleControlPanelResized(controlEvent);
               }
            });

            controlPanelCanvas.addPaintListener(new PaintListener()
            {
               @Override
               final public void paintControl(final PaintEvent paintEvent)
               {
                  handleControlPanelRepaint(paintEvent);
               }
            });

            controlPanelWateryPanel = new WateryPanel(controlPanelCanvas, 5, 12, 15);
            controlPanelWateryPanel.setWaterEffectEnabled(true);

            controlPanelPeerReference.set(new GraftableComponentSWTPeer(controlPanelCanvas, controlPanelWateryPanel));
         }
      });

      return controlPanelPeerReference.get();
   }


   private GraftableComponentSWTPeer initialiseURLEntryComboBoxSWTPeer()
   {
      final AtomicReference<GraftableComponentSWTPeer> urlEntryPeerReference = new AtomicReference<GraftableComponentSWTPeer>();

      swtDisplay.syncExec(new Runnable()
      {
         @Override
         final public void run()
         {
            final Composite urlEntryComboBoxComposite;

            if (! ConfigurationManager.isRunningMacOSX)
            {
               urlEntryComboBoxComposite = new Canvas(applicationFrame.getFrameRootComponent(), SWT.NO_FOCUS | SWT.DOUBLE_BUFFERED);
               urlEntryComboBoxComposite.setVisible(false);

               /* The component Z order for SWT follows the order that the components are created & attached to the parent, ie. first created component has top priority.
                * Since the URL entry combo popup is initialised after the control panel Canvas, it needs to be manually shifted above it in the Z order.
                * Everything created & added to the parent after this point including the browser should be painted behind (ie. before) the popup,
                * although on Mac OS X there are currently some issues with this: websites with repainted content will draw over the top of the URL popup,
                * and the (static?) front page of Flickr will even draw the entire page over the top of the popup.
                */
               urlEntryComboBoxComposite.moveAbove(null);

               final FormData urlComboBoxPopupMenuLayoutFormData = new FormData(SWT.DEFAULT, SWT.DEFAULT);
               urlComboBoxPopupMenuLayoutFormData.left = new FormAttachment(0, BrowserControlPanel.URLEntryComboBoxPopupOffset.x);
               urlComboBoxPopupMenuLayoutFormData.top = new FormAttachment(0, BrowserControlPanel.URLEntryComboBoxPopupOffset.y);
               urlEntryComboBoxComposite.setLayoutData(urlComboBoxPopupMenuLayoutFormData);
            }
            else
            {
               /* There is a z-order draw problem on Mac OS X that causes some web pages to be drawn over the top of the combo box popup.
                * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=479097 . 
                * To get around this glitch I've switched to using a subshell to display the combo box popup. It seems to work nicely!
                * It also means that the code to deal with the other Mac OS X url combo popup glitch (see below) can be put on ice for now.
                * The downside is that extra code is needed to make sure the combo box popup is kept in display sync with the parent shell.
                * 
                * NO_FOCUS is not listed in the docs as a style for Shell, but it works - prevents the combo box from losing the focus
                * and automatically closing when the user clicks on the combo's popup.
                */
               urlEntryComboBoxComposite = new Shell(applicationFrame.getShell(), SWT.NO_TRIM | SWT.NO_FOCUS);
               
               applicationFrame.addControlListener(new ControlListener()
               {
                  @Override
                  final public void controlMoved(final ControlEvent controlEvent)
                  {
                     handleBrowserWindowBoundsChanged(urlEntryComboBoxComposite);
                  }


                  @Override
                  final public void controlResized(final ControlEvent controlEvent)
                  {
                     handleBrowserWindowBoundsChanged(urlEntryComboBoxComposite);
                  }
               });

               /* Keep this code snippet, as it was used to fix a glitch before the combo popup on Mac OS X was changed to a shell.
                * 
                * Mac OS X/Cocoa display (confirmed not a problem with WebKit on Windows or Linux) also has problems with the URL entry popup
                * ghosting its image onto the browser area as the user enters a URL and the popup resizes as the history narrows.
                * This listener provides a redraw kick to the active browser window whenever the URL entry popup Canvas resizes.
                */
//               urlEntryComboBoxComposite.addControlListener(new ControlAdapter()
//               {
//                  @Override
//                  final public void controlResized(final ControlEvent controlEvent)
//                  {
//                     handleURLEntryComboBoxPopupResized();
//                  }
//               });
            }

            urlEntryComboBoxPopupMenuWateryPanel = new WateryPanel(urlEntryComboBoxComposite, 7, 13, 10);

            urlEntryPeerReference.set(new GraftableComponentSWTPeer(urlEntryComboBoxComposite, urlEntryComboBoxPopupMenuWateryPanel));
         }
      });

      return urlEntryPeerReference.get();
   }


   private BrowserEngineManager initialiseBrowserEngineManager()
   {
      final AtomicReference<BrowserEngineManager> browserEngineManagerReference = new AtomicReference<BrowserEngineManager>();

      swtDisplay.syncExec(new Runnable()
      {
         @Override
         final public void run()
         {
            final CTabFolder browserEngineTabFolder = initialiseBrowserEngineTabFolder();

            browserEngineManagerReference.set(new BrowserEngineManager(new FeedbactoryBrowserLocationController(), browserEngineTabFolder));
         }
      });

      return browserEngineManagerReference.get();
   }


   private CTabFolder initialiseBrowserEngineTabFolder()
   {
      final CTabFolder tabFolder = new CTabFolder(applicationFrame.getFrameRootComponent(), SWT.BORDER);

      tabFolderFont = initialiseNewBrowserTabFont(tabFolder);
      tabFolder.setFont(tabFolderFont);

      tabFolder.setTabHeight(16);

      final FormData layoutFormData = new FormData(SWT.DEFAULT, SWT.DEFAULT);
      layoutFormData.left = new FormAttachment(0);
      layoutFormData.right = new FormAttachment(100);
      layoutFormData.top = new FormAttachment(controlPanelSWTPeer.getPeer());
      layoutFormData.bottom = new FormAttachment(100, -StatusBarHeight);
      tabFolder.setLayoutData(layoutFormData);

      return tabFolder;
   }


   private Font initialiseNewBrowserTabFont(final CTabFolder tabFolder)
   {
      final FontData[] newFontData = tabFolder.getFont().getFontData();

      final int fontSize = ConfigurationManager.isRunningMacOSX ? 11 : 8;
      for (int fontDataIndex = 0; fontDataIndex < newFontData.length; fontDataIndex ++)
         newFontData[fontDataIndex] = new FontData(newFontData[fontDataIndex].getName(), fontSize, SWT.NORMAL);

      return new Font(tabFolder.getDisplay(), newFontData);
   }


   private void initialiseStatusBar()
   {
      swtDisplay.syncExec(new Runnable()
      {
         @Override
         final public void run()
         {
            statusBar = new BrowserStatusBar(applicationFrame.getFrameRootComponent(), browserEngineManager, PanelStartGradientColour, PanelEndGradientColour);

            final FormData layoutFormData = new FormData(SWT.DEFAULT, StatusBarHeight);
            layoutFormData.left = new FormAttachment(0);
            layoutFormData.right = new FormAttachment(100);
            layoutFormData.bottom = new FormAttachment(100);

            statusBar.getDelegate().setLayoutData(layoutFormData);
         }
      });
   }


   private void initialiseApplicationFrameBounds()
   {
      swtDisplay.syncExec(new Runnable()
      {
         @Override
         final public void run()
         {
            restoreSavedApplicationFrameBounds();
         }
      });
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void saveApplicationFrameBounds()
   {
      final Preferences preferences = Preferences.userNodeForPackage(getClass());

      final Rectangle browserBounds;

      if (applicationFrame.isMaximised())
      {
         preferences.putBoolean(IsBrowserMaximisedPreferencesKey, true);
         browserBounds = applicationFrame.getRestoredBounds();
      }
      else
      {
         preferences.putBoolean(IsBrowserMaximisedPreferencesKey, false);
         browserBounds = applicationFrame.getBounds();
      }

      preferences.put(BrowserBoundsPreferencesKey, UIUtilities.getPreferencesBoundsString(browserBounds));
   }


   private void restoreSavedApplicationFrameBounds()
   {
      final Preferences preferences = Preferences.userNodeForPackage(getClass());

      final Rectangle browserBounds = getSavedApplicationFrameBounds(preferences);

      applicationFrame.setRestoredBounds(browserBounds);

      if (preferences.getBoolean(IsBrowserMaximisedPreferencesKey, false))
         applicationFrame.setMaximised(true);
   }


   private org.eclipse.swt.graphics.Rectangle getSavedApplicationFrameBounds(final Preferences preferences)
   {
      final String savedApplicationFrameBoundsString = preferences.get(BrowserBoundsPreferencesKey, null);

      if (savedApplicationFrameBoundsString != null)
      {
         try
         {
            final Rectangle savedApplicationFrameBounds = UIUtilities.parsePreferencesBoundsString(savedApplicationFrameBoundsString);
            correctApplicationFrameBoundsForScreen(savedApplicationFrameBounds);
            return savedApplicationFrameBounds;
         }
         catch (final Exception anyException)
         {
            return getDefaultApplicationFrameBounds();
         }
      }
      else
         return getDefaultApplicationFrameBounds();
   }


   private Rectangle getDefaultApplicationFrameBounds()
   {
      final Rectangle screenBounds = applicationFrame.getFrameRootComponent().getMonitor().getClientArea();

      final int frameWidth = Math.max((int) (screenBounds.width * BrowserInitialDisplayProportions), MinimumWindowSize.x);
      final int frameHeight = Math.max((int) (screenBounds.height * BrowserInitialDisplayProportions), MinimumWindowSize.y);

      return new Rectangle(screenBounds.x + ((screenBounds.width - frameWidth) / 2),
                           screenBounds.y + ((screenBounds.height - frameHeight) / 2),
                           frameWidth, frameHeight);
   }


   private void correctApplicationFrameBoundsForScreen(final Rectangle bounds)
   {
      final Rectangle screenBounds = applicationFrame.getFrameRootComponent().getMonitor().getClientArea();

      if (bounds.width < MinimumWindowSize.x)
         bounds.width = MinimumWindowSize.x;
      else if ((bounds.width > screenBounds.width) && (screenBounds.width >= MinimumWindowSize.x))
         bounds.width = screenBounds.width;

      if (bounds.height < MinimumWindowSize.y)
         bounds.height = MinimumWindowSize.y;
      else if ((bounds.height > screenBounds.height) && (screenBounds.height >= MinimumWindowSize.y))
         bounds.height = screenBounds.height;

      if (bounds.x < screenBounds.x)
         bounds.x = screenBounds.x;
      else if ((bounds.x + bounds.width) > (screenBounds.x + screenBounds.width))
      {
         if (screenBounds.width >= MinimumWindowSize.x)
            bounds.x = screenBounds.x + screenBounds.width - bounds.width;
         else
            bounds.x = screenBounds.x;
      }

      if (bounds.y < screenBounds.y)
         bounds.y = screenBounds.y;
      else if ((bounds.y + bounds.height) > (screenBounds.y + screenBounds.height))
      {
         if (screenBounds.height >= MinimumWindowSize.y)
            bounds.y = screenBounds.y + screenBounds.height - bounds.height;
         else
            bounds.y = screenBounds.y;
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleControlPanelResized(final ControlEvent controlEvent)
   {
      disposeGradientPaintPattern();

      final Canvas controlPanelCanvas = (Canvas) controlEvent.widget;
      final org.eclipse.swt.graphics.Point controlPanelSize = controlPanelCanvas.getSize();
      controlPanelGradientPattern = new Pattern(controlEvent.display, 0f, 0f, 0f, controlPanelSize.y, controlPanelGradientStartColour, controlPanelGradientEndColour);
   }


   private void handleControlPanelRepaint(final PaintEvent paintEvent)
   {
      if (controlPanelGradientPattern != null)
      {
         paintEvent.gc.setBackgroundPattern(controlPanelGradientPattern);
         paintEvent.gc.fillRectangle(paintEvent.x, paintEvent.y, paintEvent.width, paintEvent.height);
      }
   }


   private void handleBrowserWindowBoundsChanged(final Composite urlEntryComboBoxComposite)
   {
      final Rectangle shellContentsArea = applicationFrame.getShell().getClientArea();
      final Rectangle trim = applicationFrame.getShell().computeTrim(0, 0, shellContentsArea.width, shellContentsArea.height);
      final Point parentLocation = applicationFrame.getLocation();
      final int xOffset = parentLocation.x - trim.x + BrowserControlPanel.URLEntryComboBoxPopupOffset.x;
      final int yOffset = parentLocation.y - trim.y + BrowserControlPanel.URLEntryComboBoxPopupOffset.y;
      urlEntryComboBoxComposite.setLocation(xOffset, yOffset);
   }


   /* Keep this code snippet, as it was used to fix a Mac OS X glitch which may reappear - see the
    * url combo box initialisation for details.
    */
//   private void handleURLEntryComboBoxPopupResized()
//   {
//      if (browserEngineManager.getActiveBrowserService() != null)
//         ((BrowserEngine) browserEngineManager.getActiveBrowserService()).getBrowserControl().redraw();
//   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSetWaterEffectEnabled(final boolean isWaterEffectEnabled)
   {
      swtDisplay.asyncExec(new Runnable()
      {
         @Override
         final public void run()
         {
            controlPanelWateryPanel.setWaterEffectEnabled(isWaterEffectEnabled);
         }
      });
   }


   private Point handleGetLocation()
   {
      if (Display.getCurrent() == swtDisplay)
         return applicationFrame.getLocation();
      else
      {
         final AtomicReference<Point> location = new AtomicReference<Point>();

         swtDisplay.syncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               location.set(applicationFrame.getLocation());
            }
         });

         return location.get();
      }
   }


   private void handleSetVisible(final boolean isVisible)
   {
      if (Display.getCurrent() == swtDisplay)
         applicationFrame.setVisible(isVisible);
      else
      {
         swtDisplay.asyncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               applicationFrame.setVisible(isVisible);
            }
         });
      }
   }


   private void handleShowWelcomePage()
   {
      if (Display.getCurrent() == swtDisplay)
         handleShowWelcomePageEDT();
      else
      {
         swtDisplay.asyncExec(new Runnable()
         {
            @Override
            final public void run()
            {
               handleShowWelcomePageEDT();
            }
         });
      }
   }


   private void handleShowWelcomePageEDT()
   {
      final BrowserService newBrowserService;

      try
      {
         newBrowserService = browserEngineManager.newBrowserService();
      }
      catch (final SWTError swtError)
      {
         uiManager.reportNoCompatibleBrowser(swtError);
         return;
      }

      newBrowserService.openURL(FeedbactoryWelcomePage);
      browserEngineManager.setActiveBrowserService(newBrowserService);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handlePreDispose()
   {
      if (SwingUtilities.isEventDispatchThread())
         preDisposeSwingElements();
      else
      {
         try
         {
            SwingUtilities.invokeAndWait(new Runnable()
            {
               @Override
               final public void run()
               {
                  preDisposeSwingElements();
               }
            });
         }
         catch (final Exception anyException)
         {
            throw new RuntimeException(anyException);
         }
      }

      preDisposeSWTElements();
   }


   private void preDisposeSwingElements()
   {
      if (controlPanel != null)
         controlPanel.preDispose(controlPanelSWTPeer, urlEntryComboBoxPopupMenuSWTPeer);
   }


   private void preDisposeSWTElements()
   {
      swtDisplay.syncExec(new Runnable()
      {
         @Override
         final public void run()
         {
            if (browserEngineManager != null)
               browserEngineManager.preDispose();

            if (controlPanelWateryPanel != null)
               controlPanelWateryPanel.preDispose();

            if (urlEntryComboBoxPopupMenuWateryPanel != null)
               urlEntryComboBoxPopupMenuWateryPanel.preDispose();
         }
      });
   }


   private void handleDispose()
   {
      if (SwingUtilities.isEventDispatchThread())
         disposeOfSwingElements();
      else
      {
         try
         {
            SwingUtilities.invokeAndWait(new Runnable()
            {
               @Override
               final public void run()
               {
                  disposeOfSwingElements();
               }
            });
         }
         catch (final Exception anyException)
         {
            throw new RuntimeException(anyException);
         }
      }

      disposeOfSWTElements();
   }


   private void disposeOfSwingElements()
   {
      if (controlPanel != null)
         controlPanel.dispose();

      if (graftableComponentFramework != null)
         graftableComponentFramework.dispose();
   }


   private void disposeOfSWTElements()
   {
      /* Children of the shell structure, including tab folder and browsers, will automatically be disposed when the shell frame is taken care of.
       * Also note that the SWT dispose() method will not fire off the window closed event.
       * The display itself will be disposed after this final task has been processed, since it will bail out of its executeAndDispatch loop once
       * the keepSWTDispatchThreadAlive has been changed.
       */
      swtDisplay.syncExec(new Runnable()
      {
         @Override
         final public void run()
         {
            saveApplicationFrameBounds();

            applicationFrame.dispose();

            // Only after disposing of the frame can we safely dispose of the various resources used for painting: colours, fonts, etc.
            disposeGradientPaint();

            if (browserEngineManager != null)
               browserEngineManager.dispose();

            if (tabFolderFont != null)
               tabFolderFont.dispose();

            if (statusBar != null)
               statusBar.dispose();
         }
      });
   }


   private void disposeGradientPaint()
   {
      disposeGradientPaintPattern();

      if (controlPanelGradientStartColour != null)
         controlPanelGradientStartColour.dispose();

      if (controlPanelGradientEndColour != null)
         controlPanelGradientEndColour.dispose();
   }


   private void disposeGradientPaintPattern()
   {
      if (controlPanelGradientPattern != null)
         controlPanelGradientPattern.dispose();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final void requestURLEntryFocus()
   {
      /* Transfer the focus to the URL entry combo box.
       * Since it's set up as the graftable panel's default focus control, transferring the focus to the panel should do the trick.
       */
      controlPanelSWTPeer.requestPeerFocus();
   }


   final void setWaterEffectEnabled(final boolean isWaterEffectEnabled)
   {
      handleSetWaterEffectEnabled(isWaterEffectEnabled);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public void postInitialise(final FeedbackUIManager feedbackUI)
   {
      controlPanel.postInitialise(feedbackUI);
   }


   final public SWTFrame getDelegate()
   {
      return applicationFrame;
   }


   final public BrowserUIManagerService getBrowserUIManagerService()
   {
      return browserEngineManager;
   }


   final public Point getLocation()
   {
      return handleGetLocation();
   }


   final public void setVisible(final boolean isVisible)
   {
      handleSetVisible(isVisible);
   }


   final public void showWelcomePage()
   {
      handleShowWelcomePage();
   }


   /* THREADING: Synchronous Swing and/or SWT calls ahead. To prevent deadlock, this method must not be invoked from either the Swing or SWT EDT threads, or any
    * threads which have tied them up.
    */
   final public void preDispose()
   {
      handlePreDispose();
   }


   /* THREADING: Synchronous Swing and/or SWT calls ahead. To prevent deadlock, this method must not be invoked from either the Swing or SWT EDT threads, or any
    * threads which have tied them up.
    */
   final public void dispose()
   {
      handleDispose();
   }
}