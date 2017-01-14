

package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIUtilities;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import javax.swing.GroupLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;


final public class LockableComponent
{
   final private JComponent rootComponent = new LockableComponentRootPanel(this, null);

   final private JComponent unlockedComponent;
   final private JComponent lockedComponent = new LockedComponentDelegate();

   private boolean isLocked;
   private BufferedImage fadedUnlockedPanelImage;

   final private Set<LockableComponentListener> lockableComponentListeners = new HashSet<LockableComponentListener>();


   public LockableComponent(final JComponent unlockedComponent)
   {
      if (unlockedComponent == null)
         throw new IllegalArgumentException();

      this.unlockedComponent = unlockedComponent;

      initialise();
   }


   private void initialise()
   {
      initialiseRootComponent();
   }


   private void initialiseRootComponent()
   {
      initialiseRootComponentLayout();
      initialiseRootComponentListeners();
   }


   private void initialiseRootComponentLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(rootComponent);
      rootComponent.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addComponent(unlockedComponent)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addComponent(unlockedComponent)
      );
   }


   private void initialiseRootComponentListeners()
   {
      rootComponent.addComponentListener(new ComponentAdapter()
      {
         @Override
         final public void componentResized(final ComponentEvent componentEvent)
         {
            handleComponentResized();
         }
      });
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   /* This class is used purely as a marker when recursively laying out locked components
    * before taking a snapshot. See the note in layoutComponentHierarchy().
    */
   static final private class LockableComponentRootPanel extends JPanel
   {
      final private LockableComponent lockableComponent;


      private LockableComponentRootPanel(final LockableComponent lockableComponent, final LayoutManager layoutManager)
      {
         super(layoutManager);

         this.lockableComponent = lockableComponent;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   final private class LockedComponentDelegate extends JComponent
   {
      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         /* The size check on the unlocked panel image against this locked component ensures that all repaints are deferred until the
          * root component resizings have settled and the unlocked panel image to be drawn is of the correct size. The repainting effect of differently
          * sized images in the interim can otherwise be a bit jarring.
          */
         if ((fadedUnlockedPanelImage != null) && (fadedUnlockedPanelImage.getWidth() == getWidth()) && (fadedUnlockedPanelImage.getHeight() == getHeight()))
            graphics.drawImage(fadedUnlockedPanelImage, 0, 0, null);
      }


      @Override
      final public Dimension getMinimumSize()
      {
         return getDimensionUnion(super.getMinimumSize(), unlockedComponent.getMinimumSize());
      }


      @Override
      final public Dimension getPreferredSize()
      {
         return getDimensionUnion(super.getPreferredSize(), unlockedComponent.getPreferredSize());
      }


      @Override
      final public Dimension getMaximumSize()
      {
         return getDimensionUnion(super.getMaximumSize(), unlockedComponent.getMaximumSize());
      }


      private Dimension getDimensionUnion(final Dimension dimensionOne, final Dimension dimensionTwo)
      {
         if (dimensionOne == null)
            return dimensionTwo;
         else if (dimensionTwo == null)
            return dimensionOne;
         else
         {
            final int width = (dimensionOne.width >= dimensionTwo.width) ? dimensionOne.width : dimensionTwo.width;
            final int height = (dimensionOne.height >= dimensionTwo.height) ? dimensionOne.height : dimensionTwo.height;

            return new Dimension(width, height);
         }
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static public interface LockableComponentListener
   {
      public void componentLockedStateChanged(final LockableComponent component);
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private void handleComponentResized()
   {
      if (isLocked)
      {
         layoutAndTakeSnapshot();

         // Manually nudge Swing to perform a repaint, which often isn't performed a final time after an animated frame resizing, for example.
         lockedComponent.repaint();
      }
   }


   private void layoutAndTakeSnapshot()
   {
      final Dimension lockedComponentSize = lockedComponent.getSize();

      if ((lockedComponentSize.width != 0) && (lockedComponentSize.height != 0))
      {
         unlockedComponent.setSize(lockedComponentSize);

         /* We need to manually force the layout on the component hierarchy, since the component is sitting outside of the visible hierarchy.
          * Note that this still won't produce a perfect result, especially in the case of tables where a cell renderer is used.
          */
         layoutComponentHierarchy(unlockedComponent);

         handleTakeFadedUnlockedPanelSnapshot();
      }
   }


   private void layoutComponentHierarchy(final Container component)
   {
      if (component != null)
      {
         // Resize and lay out the immediate children components.
         component.doLayout();

         for (final Component childComponent : component.getComponents())
         {
            if (childComponent instanceof LockableComponentRootPanel)
            {
               final LockableComponent lockableComponent = ((LockableComponentRootPanel) childComponent).lockableComponent;

               /* If the nested component is also a LockableComponent, and is locked, its unlocked component will of course be
                * sitting outside of the layout hierarchy and won't be accounted for either in laying out or the final top level snapshot.
                * To remedy this, the nested LockableComponent needs to undergo exactly the same treatment as is being given to
                * the higher level LockableComponent, ie. sync the size of the unlocked component with the locked component and then
                * manually lay out the unlocked component hierarchy.
                * If the LockableComponent is not locked, its unlocked component is within the layout hierarchy and still needs to
                * be processed (laid out) recursively.
                */
               if (lockableComponent.isLocked())
                  lockableComponent.layoutAndTakeSnapshot();
               else
                  layoutComponentHierarchy(lockableComponent.unlockedComponent);
            }
            else if (childComponent instanceof Container)
               layoutComponentHierarchy((Container) childComponent);
         }
      }
   }


   private void handleSetLockedState(final boolean isLocked)
   {
      if (isLocked == this.isLocked)
         return;

      this.isLocked = isLocked;

      if (isLocked)
         handleSwitchToLockedComponent();
      else
      {
         handleSwitchToUnlockedComponent();

         fadedUnlockedPanelImage = null;
      }

      handleNotifyLockableComponentListeners();
   }


   private void handleSwitchToLockedComponent()
   {
      handleTakeFadedUnlockedPanelSnapshot();
      handleSwapComponents(unlockedComponent, lockedComponent);
   }


   private void handleTakeFadedUnlockedPanelSnapshot()
   {
      if ((unlockedComponent.getWidth() > 0) && (unlockedComponent.getHeight() > 0))
      {
         // The input image to the box blur must be of type TYPE_INT_ARGB_PRE.
         fadedUnlockedPanelImage = new BufferedImage(unlockedComponent.getWidth(), unlockedComponent.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
         Graphics2D bufferGraphics = fadedUnlockedPanelImage.createGraphics();
         unlockedComponent.paint(bufferGraphics);
         bufferGraphics.dispose();

         fadedUnlockedPanelImage = UIUtilities.boxBlurImage(fadedUnlockedPanelImage, fadedUnlockedPanelImage, 3, 3);
      }
   }


   private void handleSwitchToUnlockedComponent()
   {
      handleSwapComponents(lockedComponent, unlockedComponent);
   }


   private void handleSwapComponents(final JComponent componentToDeactivate, final JComponent componentToActivate)
   {
      final GroupLayout panelLayout = (GroupLayout) rootComponent.getLayout();
      panelLayout.replace(componentToDeactivate, componentToActivate);
   }


   private void handleNotifyLockableComponentListeners()
   {
      for (final LockableComponentListener listener : lockableComponentListeners)
         listener.componentLockedStateChanged(this);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public JComponent getRootComponent()
   {
      return rootComponent;
   }


   final public JComponent getUnlockedComponent()
   {
      return unlockedComponent;
   }


   final public JComponent getLockedComponent()
   {
      return lockedComponent;
   }


   final public boolean isLocked()
   {
      return isLocked;
   }


   final public void setLocked(final boolean isLocked)
   {
      handleSetLockedState(isLocked);
   }


   final public void addLockableComponentListener(final LockableComponentListener listener)
   {
      lockableComponentListeners.add(listener);
   }


   final public void removeLockableComponentListener(final LockableComponentListener listener)
   {
      lockableComponentListeners.remove(listener);
   }
}