/* Memos:
 * - The menu employs a cached image of the sliding component for efficiency during the animation. This separate component is hidden at other times.
 *   I could null it out for GC when the menu is inactive, but for now the image is initialised once and kept.
 *
 * - Should I let action listeners register on the menu, ie. create the ActionListeners here, and then have the clients register one 'master' ActionListener on the
 *   menu, where the source of an ActionEvent is set to the event button? But, the menu components are JComponents, not JButtons, or ShadedButtons...
 */

package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.core.ClientUtilities;
import com.feedbactory.client.ui.component.ShadedButton.ActionListener;
import com.feedbactory.client.ui.component.ShadedComponent.RadialGradientPaintProfile;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import javax.swing.*;


final public class SlidingMenuBar
{
   static final private int DefaultComponentShadowLength = 7;
   static final private int DefaultComponentMaximumShadowTransparency = 130;

   static final private int MenuExtensionDelayMilliseconds = 150;
   static final private int MenuRetractionDelayMilliseconds = 100;

   final private JPanel delegatePanel = new JPanel(null);

   final private NimbusBorderPanel slidingPanel;
   final private CachePaintedSlidingPanel slidingPanelCachedImage = new CachePaintedSlidingPanel();

   // The invocation component is the component which doesn't sit within the menu but generally sits above it and can invoke the menu via mouse movement or clicking.
   private JComponent menuInvocationComponent;
   private MouseAdapter menuInvocationComponentMouseListener;

   private JComponent[] menuItems;
   private MouseListener menuItemsMouseListener;
   private ActionListener menuItemsActionListener;

   private MenuAnimationTask menuAnimationTask;

   final private ScheduledThreadPoolExecutor slideDelayExecutorService = new ScheduledThreadPoolExecutor(1);
   private ScheduledFuture<?> slideDelayTask;

   private boolean isEnabled = true;


   public SlidingMenuBar(final RadialGradientPaintProfile gradientPaint)
   {
      this(DefaultComponentShadowLength, DefaultComponentMaximumShadowTransparency, gradientPaint);
   }


   public SlidingMenuBar(final int shadowLength, final int maximumShadowTransparency, final RadialGradientPaintProfile gradientPaint)
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder();
      builder.setBorderShadow(shadowLength, maximumShadowTransparency);
      builder.setRadialGradientPaint(gradientPaint);

      slidingPanel = new NimbusBorderPanel(builder);

      initialise();
   }


   private void initialise()
   {
      initialiseSlidingPanel();

      initialiseDelegatePanel();

      initialiseSlideDelayTimer();
   }


   private void initialiseSlidingPanel()
   {
      slidingPanel.getDelegate().addMouseListener(new MouseAdapter()
      {
         @Override
         final public void mouseEntered(final MouseEvent mouseEvent)
         {
            handleMouseEntered();
         }


         @Override
         final public void mouseExited(final MouseEvent mouseEvent)
         {
            handleMouseExited(mouseEvent.getX(), mouseEvent.getY());
         }
      });
   }


   private void initialiseDelegatePanel()
   {
      delegatePanel.setOpaque(false);
      delegatePanel.add(slidingPanel.getDelegate());
      delegatePanel.add(slidingPanelCachedImage);
   }


   private void initialiseSlideDelayTimer()
   {
      slideDelayExecutorService.setKeepAliveTime(MenuExtensionDelayMilliseconds, TimeUnit.MILLISECONDS);
      slideDelayExecutorService.allowCoreThreadTimeOut(true);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class CachePaintedSlidingPanel extends JComponent
   {
      private BufferedImage cachedImage;


      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         graphics.drawImage(cachedImage, 0, 0, null);
      }


      private void setImage(final BufferedImage cachedImage)
      {
         this.cachedImage = cachedImage;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private enum AnimationState
   {
      Retracted,
      Retracting,
      Extending,
      ExtendingBounceUp,
      Extended,
      Shutdown;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class MenuAnimationTask implements Runnable
   {
      static final private int MaximumExtendedPosition = -10;
      static final private int MaximumBounceHeight = 11;
      static final private int AnimationFrameDelayMilliseconds = 6;
      static final private int StartVelocity = 3;
      static final private float AccelerationPerFrame = 0.7f;

      private ScheduledExecutorService executorService;
      private AnimationState state = AnimationState.Retracted;

      private float velocity;
      private int bounceHeight;


      private void startTask()
      {
         if (! isExecutorServiceInitialised())
         {
            executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.scheduleAtFixedRate(this, 0, AnimationFrameDelayMilliseconds, TimeUnit.MILLISECONDS);
         }
      }


      private void finishTask()
      {
         executorService.shutdown();
         executorService = null;
      }


      @Override
      final public void run()
      {
         Thread.currentThread().setName("SlidingMenuBar.MenuAnimationTask");

         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               if ((! isShutdown()) && isExecutorServiceInitialised())
                  updateState();
            }
         });
      }


      private void updateState()
      {
         final int menuVerticalPosition = slidingPanelCachedImage.getY();

         switch (state)
         {
            case Extended:

               slidingPanel.getDelegate().setVisible(true);
               slidingPanelCachedImage.setVisible(false);
               slidingPanelCachedImage.setLocation(0, MaximumExtendedPosition);

               finishTask();

               break;

            case Retracted:

               /* When fully retracted, hide the parent panel from Swing's processing so that it won't interfere with the cursor types of overlapping panels
                * showing beneath, eg. text fields. A 'visible' but non-opaque panel will prevent cursor changes.
                */
               delegatePanel.setVisible(false);
               slidingPanelCachedImage.setLocation(0, -slidingPanelCachedImage.getHeight());

               finishTask();

               break;

            case Extending:

               if (menuVerticalPosition < MaximumExtendedPosition)
               {
                  slidingPanelCachedImage.setLocation(0, Math.min((int) (menuVerticalPosition + velocity), MaximumExtendedPosition));
                  velocity += AccelerationPerFrame;
               }
               else if (bounceHeight > 0)
                  state = AnimationState.ExtendingBounceUp;
               else
                  state = AnimationState.Extended;

               break;

            case ExtendingBounceUp:

               if (menuVerticalPosition > (MaximumExtendedPosition - bounceHeight))
                  slidingPanelCachedImage.setLocation(0, menuVerticalPosition - 1);
               else
               {
                  bounceHeight >>= 2;
                  state = AnimationState.Extending;
               }

               break;

            case Retracting:

               if (menuVerticalPosition > (-slidingPanelCachedImage.getHeight()))
                  slidingPanelCachedImage.setLocation(0, menuVerticalPosition - 6);
               else
                  state = AnimationState.Retracted;

               break;
         }
      }


      /****************************************************************************
       * 
       ***************************************************************************/


      private boolean isExecutorServiceInitialised()
      {
         return (executorService != null);
      }


      private boolean isExtending()
      {
         return (state == AnimationState.Extended) || (state == AnimationState.Extending) || (state == AnimationState.ExtendingBounceUp);
      }


      private boolean isRetracting()
      {
         return (state == AnimationState.Retracted) || (state == AnimationState.Retracting);
      }


      private boolean isShutdown()
      {
         return (state == AnimationState.Shutdown);
      }


      private void extendMenuBar()
      {
         assert SwingUtilities.isEventDispatchThread();

         if ((! isShutdown()) && (! isExtending()))
         {
            state = AnimationState.Extending;
            velocity = StartVelocity;
            bounceHeight = MaximumBounceHeight;

            // See the note above, regarding the parent panel visibility when the menu has been fully retracted.
            delegatePanel.setVisible(true);

            startTask();
         }
      }


      private void retractMenuBar()
      {
         assert SwingUtilities.isEventDispatchThread();

         if ((! isShutdown()) && (! isRetracting()))
         {
            state = AnimationState.Retracting;
            bounceHeight = MaximumBounceHeight;

            slidingPanelCachedImage.setVisible(true);
            slidingPanel.getDelegate().setVisible(false);

            startTask();
         }
      }


      private void shutdown()
      {
         assert SwingUtilities.isEventDispatchThread();

         state = AnimationState.Shutdown;

         if (isExecutorServiceInitialised())
         {
            /* Ensure that even if all Swing tasks haven't completed by the time this method returns,
             * there is at least the guarantee that any remaining calls to Swing's invokeLater have been made.
             */
            ClientUtilities.shutdownAndAwaitTermination(executorService, "SlidingMenuBar.MenuAnimationTask");
         }
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private void handleSetMenuComponents(final JComponent menuInvocationComponent, final JComponent[] components)
   {
      if (menuInvocationComponent == null)
         throw new IllegalArgumentException("Menu invocation component cannot be null.");
      else if ((components == null) || (components.length == 0))
         throw new IllegalArgumentException("Menu components cannot be empty.");

      initialiseRootComponent(menuInvocationComponent);

      initialiseSlidingPanel(components);

      initialiseSlidingPanelCachedImage();

      initialiseDelegatePanelSize();

      initialiseMenuTask();
   }


   private void initialiseRootComponent(final JComponent menuInvocationComponent)
   {
      if (this.menuInvocationComponent == menuInvocationComponent)
         return;

      // Remove the listeners from the old menu invocation component first.
      if (isEnabled && (this.menuInvocationComponent != null))
         removeInvocationComponentListeners();

      this.menuInvocationComponent = menuInvocationComponent;

      menuInvocationComponentMouseListener = new MouseAdapter()
      {
         @Override
         final public void mouseClicked(final MouseEvent mouseEvent)
         {
            handleMouseClickedInMenuInvocationComponent();
         }


         @Override
         final public void mouseEntered(final MouseEvent mouseEvent)
         {
            handleMouseEntered();
         }


         @Override
         final public void mouseExited(final MouseEvent mouseEvent)
         {
            handleMouseExitedMenuInvocationComponent();
         }


         @Override
         final public void mouseMoved(final MouseEvent mouseEvent)
         {
            handleMouseMovedInMenuInvocationComponent();
         }
      };

      if (isEnabled)
         addInvocationComponentListeners();
   }


   private void addInvocationComponentListeners()
   {
      menuInvocationComponent.addMouseListener(menuInvocationComponentMouseListener);
      menuInvocationComponent.addMouseMotionListener(menuInvocationComponentMouseListener);
   }


   private void removeInvocationComponentListeners()
   {
      menuInvocationComponent.removeMouseListener(menuInvocationComponentMouseListener);
      menuInvocationComponent.removeMouseMotionListener(menuInvocationComponentMouseListener);
   }


   private void initialiseSlidingPanel(final JComponent[] newMenuItems)
   {
      initialiseContentPane(newMenuItems);

      final Dimension preferredSize = slidingPanel.getDelegate().getPreferredSize();

      slidingPanel.getDelegate().setSize(preferredSize);
      slidingPanel.getDelegate().setLocation(0, MenuAnimationTask.MaximumExtendedPosition);
      slidingPanel.getDelegate().setVisible(false);

      initialiseMenuItems(newMenuItems);

      // The nested layout needs to be initialised before the cached image can be generated.
      initialiseSlidingPanelLayout(slidingPanel.getDelegate());
   }


   private void initialiseContentPane(final JComponent[] newMenuItems)
   {
      final GroupLayout panelLayout = new GroupLayout(slidingPanel.getContentPane());
      slidingPanel.getContentPane().setLayout(panelLayout);

      final GroupLayout.ParallelGroup buttonHorizontalGroup = panelLayout.createParallelGroup();
      final GroupLayout.SequentialGroup buttonVerticalGroup = panelLayout.createSequentialGroup();

      for (int componentIndex = 0; componentIndex < newMenuItems.length; componentIndex ++)
      {
         buttonHorizontalGroup.addComponent(newMenuItems[componentIndex], GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE);

         buttonVerticalGroup.addComponent(newMenuItems[componentIndex], GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE);

         if (componentIndex != (newMenuItems.length - 1))
            buttonVerticalGroup.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED);
      }

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(buttonHorizontalGroup)
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(buttonVerticalGroup)
         .addContainerGap()
      );
   }


   private void initialiseMenuItems(final JComponent[] newMenuItems)
   {
      if (menuItems != null)
         removeExistingMenuItemMouseListeners();

      menuItems = newMenuItems;

      addMenuItemsMouseListeners();
   }


   private void removeExistingMenuItemMouseListeners()
   {
      for (final JComponent component : menuItems)
      {
         component.removeMouseListener(menuItemsMouseListener);

         if (component instanceof ShadedButton)
            ((ShadedButton) component).removeActionListener(menuItemsActionListener);
      }
   }


   private void addMenuItemsMouseListeners()
   {
      menuItemsMouseListener = new MouseAdapter()
      {
         @Override
         final public void mouseEntered(final MouseEvent mouseEvent)
         {
            handleMouseEntered();
         }


         @Override
         final public void mouseExited(final MouseEvent mouseEvent)
         {
            handleMouseExitedNestedMenuItem(mouseEvent);
         }
      };

      // When a nested menu button is pressed, retract the menu as the user would expect.
      menuItemsActionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ShadedButton.ActionEvent actionEvent)
         {
            setExtended(false);
         }
      };

      /* When there are nested menu components, it's possible for the mouse pointer to move out of the container fast enough so that no mouse events are
       * generated within the parent slidingPanel. In this instance, the menu would annoyingly remain open even if the pointer has left the panel.
       * So, we also add a listener to each nested component to catch their mouse exited event and cater for this possibility.
       */
      for (final JComponent component : menuItems)
      {
         component.addMouseListener(menuItemsMouseListener);

         if (component instanceof ShadedButton)
            ((ShadedButton) component).addActionListener(menuItemsActionListener);
      }
   }


   private void initialiseSlidingPanelLayout(final Container component)
   {
      if (component != null)
      {
         // Resize and lay out the immediate children components.
         component.doLayout();

         for (final Component childComponent : component.getComponents())
         {
            if (childComponent instanceof Container)
               initialiseSlidingPanelLayout((Container) childComponent);
         }
      }
   }


   private void initialiseSlidingPanelCachedImage()
   {
      slidingPanelCachedImage.setSize(slidingPanel.getDelegate().getWidth(), slidingPanel.getDelegate().getHeight());
      slidingPanelCachedImage.setLocation(0, -slidingPanelCachedImage.getHeight());

      final BufferedImage bufferedImage = UIUtilities.createCompatibleImage(slidingPanelCachedImage.getWidth(), slidingPanelCachedImage.getHeight(), Transparency.TRANSLUCENT);
      final Graphics2D graphics2D = bufferedImage.createGraphics();
      slidingPanel.getDelegate().paint(graphics2D);
      graphics2D.dispose();

      slidingPanelCachedImage.setImage(bufferedImage);
   }


   private void initialiseDelegatePanelSize()
   {
      final Dimension slidingPanelSize = slidingPanel.getDelegate().getSize();

      delegatePanel.setPreferredSize(slidingPanelSize);
      delegatePanel.setMaximumSize(slidingPanelSize);
      delegatePanel.setMinimumSize(slidingPanelSize);
   }


   private void initialiseMenuTask()
   {
      menuAnimationTask = new MenuAnimationTask();
   }


   private void cancelMenuDelayTask()
   {
      if (slideDelayTask != null)
      {
         slideDelayTask.cancel(false);
         slideDelayTask = null;
      }
   }


   private void handleMouseClickedInMenuInvocationComponent()
   {
      setExtended(true);
   }


   private void handleMouseMovedInMenuInvocationComponent()
   {
      if (! menuAnimationTask.isExtending())
         handleMouseEntered();
   }


   private void handleMouseEntered()
   {
      scheduleMenuExtension();
   }


   private void handleMouseExitedMenuInvocationComponent()
   {
      scheduleMenuRetraction();
   }


   private void handleMouseExitedNestedMenuItem(final MouseEvent mouseEvent)
   {
      final Component nestedSourceComponent = (Component) mouseEvent.getSource();

      final int mouseX = nestedSourceComponent.getX() + mouseEvent.getX();
      final int mouseY = nestedSourceComponent.getY() + mouseEvent.getY();

      handleMouseExited(mouseX, mouseY);
   }


   private void handleMouseExited(final int mouseX, final int mouseY)
   {
      if (! slidingPanel.getDelegate().getVisibleRect().contains(mouseX, mouseY))
         scheduleMenuRetraction();
   }


   private void scheduleMenuExtension()
   {
      cancelMenuDelayTask();

      slideDelayTask = slideDelayExecutorService.schedule(new Runnable()
      {
         @Override
         final public void run()
         {
            handleMouseHoverInsideMenuRegion();
         }
      }, MenuExtensionDelayMilliseconds, TimeUnit.MILLISECONDS);
   }


   private void scheduleMenuRetraction()
   {
      cancelMenuDelayTask();

      slideDelayTask = slideDelayExecutorService.schedule(new Runnable()
      {
         @Override
         final public void run()
         {
            handleMouseHoverOutsideMenuRegion();
         }
      }, MenuRetractionDelayMilliseconds, TimeUnit.MILLISECONDS);
   }


   private void handleMouseHoverInsideMenuRegion()
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            extendMenuBar();
         }
      });
   }


   private void handleMouseHoverOutsideMenuRegion()
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            retractMenuBar();
         }
      });
   }


   private void extendMenuBar()
   {
      menuAnimationTask.extendMenuBar();
   }


   private void retractMenuBar()
   {
      menuAnimationTask.retractMenuBar();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSetExtended(final boolean isExtended)
   {
      /* Lingering mouse hover tasks may be fired after this call, and may place the menu in the wrong state.
       * Note that there's still a slim possibility of the hover task being fired, and running immediately
       * after this method has finished.
       */
      cancelMenuDelayTask();

      if (isExtended)
         extendMenuBar();
      else
         retractMenuBar();
   }


   private void handleSetEnabled(final boolean isEnabled)
   {
      if (isEnabled != this.isEnabled)
      {
         this.isEnabled = isEnabled;

         if (menuInvocationComponent != null)
         {
            if (isEnabled)
            {
               menuInvocationComponent.setEnabled(true);
               addInvocationComponentListeners();
            }
            else
            {
               menuInvocationComponent.setEnabled(false);
               removeInvocationComponentListeners();
            }
         }
      }
   }


   private void handleDispose()
   {
      /* Prevent the generation of further mouse hover events, which will invoke the menu slide executor that is about
       * to be shutdown.
       */
      if (isEnabled && (menuInvocationComponent != null))
         removeInvocationComponentListeners();

      if (menuItems != null)
         removeExistingMenuItemMouseListeners();

      if (menuAnimationTask != null)
         menuAnimationTask.shutdown();

      cancelMenuDelayTask();
      ClientUtilities.shutdownAndAwaitTermination(slideDelayExecutorService, "SlidingMenuBar.SlideDelayTask");
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public JComponent getDelegate()
   {
      return delegatePanel;
   }


   final public JComponent getSlidingComponent()
   {
      return slidingPanel.getDelegate();
   }


   final public void setMenuComponents(final JComponent menuInvocationComponent, final JComponent[] components)
   {
      handleSetMenuComponents(menuInvocationComponent, components.clone());
   }


   final public void setExtended(final boolean isExtended)
   {
      handleSetExtended(isExtended);
   }


   final public void setEnabled(final boolean isEnabled)
   {
      handleSetEnabled(isEnabled);
   }


   final public void dispose()
   {
      handleDispose();
   }
}