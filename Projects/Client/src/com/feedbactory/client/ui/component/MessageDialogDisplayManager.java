/* Memos:
 * - I was previously using a HierarchyListener and the HierarchyEvent.SHOWING_CHANGED signal to detect when the manager component was hidden, and then purge any
 *   temporary dialogs/other components. However as much as I like this approach because the logic is self contained here, it's fraught with danger because the
 *   event will also be fired when for example a SwingNimbusFrame is animated during a resize; the frame temporary hides its main content and switches to a blank panel
 *   during the animation. If for example a caller wanted to display a large-ish temporary component - one that required a resize of the frame - using this manager,
 *   the resize op would immediately trigger the HierarchyEvent which would in turn quietly dismiss the component. So, a solution is to let the caller/owner decide
 *   when to clear out the temporary components from the display manager. It's not so elegant but it does the job.
 *
 * - Another reason to let the caller decide is if the parent UI simply wishes to be able to dismiss the temporary items at any time, eg. when switching between menu items
 *   and panels.
 *
 * - The dismissLockingComponent() and dismissMessageDialog() code paths will dismiss components and fire an event to any dismissal listeners, but will not fire any
 *   action event on the component itself. It's important to distinguish between this code path and the code paths of dismissTemporaryComponents() and
 *   dismissTemporaryLockingComponentNode(), which are geared towards automatically firing the cancel action events on dismissal, if the component being dismissed is a
 *   message dialog. The latter are appropriate when a temporary component is being shown and the owner requires a callback when it's dismissed to notify it that the state
 *   has changed. The cancel action events on these temporary components may be fired when the display is hidden or the user presses Escape to cancel an operation.
 *
 * - Related to the above point, it's up to the caller to ensure that they are set up to receive any callbacks for locking components that they place. For example a caller
 *   should not flag a preset OK message dialog as 'temporary' if it's not acceptable for it to receive no notification when the dialog is dismissed. Notifications will
 *   only be sent for cancellable dialogs: OKCancel, and YesNoCancel. For the OK dialog example, the caller could alternatively set up a dismissal notification listener
 *   and maintain its own reference to the dialog so that it knows when it's been dismissed.
 *
 * - The buttons on transparent-controlled dialogs will be repainted at full opacity when only the button is repainted, eg. on mouse-over.
 *   This glitch is rarely apparent on the fading message dialogs since the parent dialog (and therefore child buttons) are repainted so rapidly.
 *   See the MessageDialog class for a fairly easy (but a little clunky) workaround if this proves to be problematic.
 */

package com.feedbactory.client.ui.component;


import com.feedbactory.client.core.ClientUtilities;
import com.feedbactory.client.ui.component.MessageDialog.ActionListener;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionConfiguration;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionSelection;
import com.feedbactory.client.ui.component.MessageDialogDisplayManager.ComponentDisplayPriority;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.*;
import javax.swing.*;


final public class MessageDialogDisplayManager
{
   final private LockableComponent delegateComponent;

   final private Queue<ComponentNode> lockingComponents = new PriorityQueue<ComponentNode>();
   private JComponent activeLockingComponent;

   final private List<DisplayListener> displayListeners = new ArrayList<DisplayListener>();

   final private TimedMessageDialogNodeFaderTask timedMessageDialogNodeFaderTask = new TimedMessageDialogNodeFaderTask();


   public MessageDialogDisplayManager(final JComponent unlockedComponent)
   {
      this(unlockedComponent, 0);
   }


   public MessageDialogDisplayManager(final JComponent unlockedComponent, final int minimumMarginSize)
   {
      delegateComponent = new LockableComponent(unlockedComponent);

      initialise(minimumMarginSize);
   }


   private void initialise(final int minimumMarginSize)
   {
      initialiseLockedComponent(minimumMarginSize);
   }


   private void initialiseLockedComponent(final int minimumMarginSize)
   {
      initialiseLockedComponentLayout(minimumMarginSize);
      initialiseLockedComponentCancelKeyBinding();
   }


   private void initialiseLockedComponentLayout(final int minimumMarginSize)
   {
      activeLockingComponent = new JPanel(null);

      final GroupLayout componentLayout = new GroupLayout(delegateComponent.getLockedComponent());
      delegateComponent.getLockedComponent().setLayout(componentLayout);

      /* Set up our locked panel so that the component will be displayed in the centre, at its preferred width and height.
       * If not for the PREFERRED_SIZE constraint on the component's maximum width & height, the component would be stretched
       * to compete with the margin gaps so as to fill in the unused space.
       */
      componentLayout.setHorizontalGroup(componentLayout.createSequentialGroup()
         .addGap(minimumMarginSize, minimumMarginSize, Integer.MAX_VALUE)
         .addComponent(activeLockingComponent, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(minimumMarginSize, minimumMarginSize, Integer.MAX_VALUE)
      );

      componentLayout.setVerticalGroup(componentLayout.createSequentialGroup()
         .addGap(minimumMarginSize, minimumMarginSize, Integer.MAX_VALUE)
         .addComponent(activeLockingComponent, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(minimumMarginSize, minimumMarginSize, Integer.MAX_VALUE)
      );
   }


   private void initialiseLockedComponentCancelKeyBinding()
   {
      final InputMap inputMap = delegateComponent.getLockedComponent().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      final ActionMap actionMap = delegateComponent.getLockedComponent().getActionMap();

      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelLockingComponent");

      actionMap.put("cancelLockingComponent", new AbstractAction()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleCancelLockingComponentKeyPressed();
         }
      });
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   /* The ApplicationHighPriorityDialog has been assigned a value that will ensure that components displayed at that priority level will always be
    * visible, even if for example a crash occurs while the display manager is still displaying a busy component.
    */
   static public enum ComponentDisplayPriority
   {
      FormComponent((byte) 20),
      FormSubcomponent((byte) 40),
      FormRegularDialog((byte) 60),
      FormHighPriorityDialog((byte) 80),
      ApplicationRegularDialog((byte) 100),
      ApplicationBusyComponent((byte) 120),
      ApplicationHighPriorityDialog(Byte.MAX_VALUE);

      final private byte value;


      private ComponentDisplayPriority(final byte value)
      {
         this.value = value;
      }


      /****************************************************************************
       *
       ***************************************************************************/


      final public boolean isPriorityOrHigher(final ComponentDisplayPriority otherDisplayPriority)
      {
         return (value >= otherDisplayPriority.value);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   public interface DisplayListener
   {
      public void displayLocked();
      public void displayUnlocked();
      public void newLockingComponentDisplayed(final JComponent component, final ComponentDisplayPriority displayPriority);
      public void lockingComponentDismissed(final JComponent component, final ComponentDisplayPriority displayPriority, final boolean wasBeingDisplayed);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static private class ComponentNode implements Comparable<ComponentNode>
   {
      final protected JComponent component;
      final protected ComponentDisplayPriority displayPriority;
      final protected boolean isTemporary;


      private ComponentNode(final JComponent component, final ComponentDisplayPriority displayPriority, final boolean isTemporary)
      {
         validate(component, displayPriority);

         this.component = component;
         this.displayPriority = displayPriority;
         this.isTemporary = isTemporary;
      }


      private void validate(final JComponent component, final ComponentDisplayPriority displayPriority)
      {
         if (component == null)
            throw new IllegalArgumentException("Display component cannot be null.");
         else if (displayPriority == null)
            throw new IllegalArgumentException("Display priority cannot be null.");
      }


      @Override
      final public int compareTo(final ComponentNode otherComponentNode)
      {
         if (displayPriority.value < otherComponentNode.displayPriority.value)
            return 1;
         else if (displayPriority.value > otherComponentNode.displayPriority.value)
            return -1;
         else
            return 0;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private class MessageDialogNode extends ComponentNode implements ActionListener
   {
      final protected MessageDialog messageDialog;
      final protected int defaultFocusIndex;


      private MessageDialogNode(final MessageDialog messageDialog, final ComponentDisplayPriority displayPriority, final boolean isTemporary,
                                final int defaultFocusIndex)
      {
         super(messageDialog.getDelegate(), displayPriority, isTemporary);

         validate(messageDialog, defaultFocusIndex);

         this.messageDialog = messageDialog;
         this.defaultFocusIndex = defaultFocusIndex;

         initialise();
      }


      private void validate(final MessageDialog messageDialog, final int defaultFocusIndex)
      {
         if (messageDialog == null)
            throw new IllegalArgumentException("Message dialog cannot be null.");
         else if (defaultFocusIndex != -1)
         {
            final JComponent[] inputComponents = messageDialog.getInputComponents();

            if ((defaultFocusIndex < 0) || (defaultFocusIndex > inputComponents.length))
               throw new IllegalArgumentException("Invalid default focus index: " + defaultFocusIndex);
         }
      }


      private void initialise()
      {
         messageDialog.addActionListener(this);
      }


      @Override
      final public void actionPerformed(final MessageDialog messageDialog, final PresetOptionSelection optionSelection, final int optionSelectionIndex)
      {
         messageDialog.removeActionListener(this);
         dismissLockingComponentNode(this);
      }


      /****************************************************************************
       *
       ***************************************************************************/


      protected AbstractButton getDefaultFocusButton()
      {
         if (defaultFocusIndex != -1)
            return ((AbstractButton) messageDialog.getInputComponents()[defaultFocusIndex]);
         else
            return null;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private class TimedMessageDialogNode extends MessageDialogNode
   {
      final private long displayDurationMilliseconds;


      private TimedMessageDialogNode(final MessageDialog messageDialog, final ComponentDisplayPriority displayPriority, final boolean isTemporary,
                                     final int defaultFocusIndex, final long displayDurationMilliseconds)
      {
         super(messageDialog, displayPriority, isTemporary, defaultFocusIndex);

         validate(displayDurationMilliseconds);

         this.displayDurationMilliseconds = displayDurationMilliseconds;
      }


      private void validate(final long displayDurationMilliseconds)
      {
         if (displayDurationMilliseconds < 0)
            throw new IllegalArgumentException("Invalid message dialog display duration: " + displayDurationMilliseconds);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class TimedMessageDialogNodeFaderTask implements Runnable, TransparencyController
   {
      static final long TargetFramesPerSecond = 30L;
      static final float OpacityShiftPerFrame = 0.06f;

      final private ScheduledThreadPoolExecutor executor;

      private TimedMessageDialogNode activeMessageDialog;
      private float opacity = 1f;
      private Future<?> timedDismissalTask;


      private TimedMessageDialogNodeFaderTask()
      {
         executor = new ScheduledThreadPoolExecutor(1);
         executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
      }


      @Override
      final public float getTransparency()
      {
         return opacity;
      }


      @Override
      final public void run()
      {
         SwingUtilities.invokeLater(new Runnable()
         {
            @Override
            final public void run()
            {
               // The component may have already been dismissed via the user since the task was scheduled - check this first.
               if ((! executor.isShutdown()) && (activeMessageDialog != null) && getLockingComponentNode(activeMessageDialog.messageDialog.getDelegate()) != null)
               {
                  opacity -= OpacityShiftPerFrame;

                  if (opacity > 0f)
                     activeMessageDialog.messageDialog.getDelegate().repaint();
                  else
                  {
                     opacity = 0f;
                     finish();
                  }
              }
            };
         });
      }


      private void finish()
      {
         final TimedMessageDialogNode closingActiveMessageDialog = activeMessageDialog;

         cancelFadeTask();

         final AbstractButton defaultFocusButton = closingActiveMessageDialog.getDefaultFocusButton();
         if (defaultFocusButton != null)
            defaultFocusButton.doClick();
         else
            dismissLockingComponentNode(closingActiveMessageDialog);
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private void startFadeTask(final TimedMessageDialogNode activeMessageDialog)
      {
         if (! executor.isShutdown())
         {
            this.activeMessageDialog = activeMessageDialog;
            opacity = 1f;

            if (timedDismissalTask == null)
               timedDismissalTask = executor.scheduleAtFixedRate(this, activeMessageDialog.displayDurationMilliseconds, 1000L / TargetFramesPerSecond, TimeUnit.MILLISECONDS);
         }
      }


      private void cancelFadeTask()
      {
         if (timedDismissalTask != null)
         {
            timedDismissalTask.cancel(false);
            timedDismissalTask = null;
            activeMessageDialog = null;
         }
      }


      private void shutdown()
      {
         ClientUtilities.shutdownAndAwaitTermination(executor, "MessageDialogDisplayManager.TimedMessageDialogNodeFaderTask");
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleCancelLockingComponentKeyPressed()
   {
      final ComponentNode visibleComponentNode = lockingComponents.peek();

      if ((visibleComponentNode != null) && visibleComponentNode.isTemporary)
         dismissTemporaryLockingComponentNode(visibleComponentNode);
   }


   private void notifyListenersOfLockedState()
   {
      for (final DisplayListener listener : displayListeners)
         listener.displayLocked();
   }


   private void notifyListenersOfUnlockedState()
   {
      for (final DisplayListener listener : displayListeners)
         listener.displayUnlocked();
   }


   private void notifyListenersOfNewLockingComponentDisplayed(final JComponent component, final ComponentDisplayPriority displayPriority)
   {
      for (final DisplayListener listener : displayListeners)
         listener.newLockingComponentDisplayed(component, displayPriority);
   }


   private void notifyListenersOfLockingComponentDismissed(final ComponentNode componentNode, final boolean wasBeingDisplayed)
   {
      for (final DisplayListener listener : displayListeners)
         listener.lockingComponentDismissed(componentNode.component, componentNode.displayPriority, wasBeingDisplayed);
   }


   private ComponentNode getLockingComponentNode(final JComponent component)
   {
      for (final ComponentNode node : lockingComponents)
      {
         if (node.component == component)
            return node;
      }

      return null;
   }


   // Returns true if the component will be immediately shown at the top of the display stack, false otherwise.
   private boolean addLockingComponentNode(final ComponentNode componentNode)
   {
      if (lockingComponents.contains(componentNode))
         throw new IllegalStateException("Component already exists in display manager hierarchy.");

      lockingComponents.add(componentNode);

      if (lockingComponents.peek() == componentNode)
      {
         showAddedLockingComponent(componentNode.component);
         processNewLockingComponentNode(componentNode);

         return true;
      }
      else
         return false;
   }


   private void showAddedLockingComponent(final JComponent componentToShow)
   {
      setActiveLockingComponent(componentToShow);

      if (! delegateComponent.isLocked())
      {
         delegateComponent.setLocked(true);
         notifyListenersOfLockedState();
      }
      else
      {
         /* In order to recognise the updated preferred size of the locked panel when a newer, possibly larger locked component is added,
          * the root component needs to be marked as needing to be laid out again.
          * To test this, try removing this call when starting a superseded Feedbactory client - the resulting display message displayed over the top
          * of the already locked (busy) pad will leak into the right side of the window.
          */
         delegateComponent.getRootComponent().invalidate();
      }
   }


   private void setActiveLockingComponent(final JComponent componentToActivate)
   {
      final GroupLayout lockedPanelLayout = (GroupLayout) delegateComponent.getLockedComponent().getLayout();
      lockedPanelLayout.replace(activeLockingComponent, componentToActivate);

      activeLockingComponent = componentToActivate;
   }


   private void processNewLockingComponentNode(final ComponentNode componentNode)
   {
      notifyListenersOfNewLockingComponentDisplayed(componentNode.component, componentNode.displayPriority);

      if (componentNode instanceof MessageDialogNode)
      {
         final MessageDialogNode messageDialogNode = (MessageDialogNode) componentNode;

         if (messageDialogNode.defaultFocusIndex != -1)
         {
            final JComponent[] inputComponents = messageDialogNode.messageDialog.getInputComponents();
            inputComponents[messageDialogNode.defaultFocusIndex].requestFocusInWindow();
         }

         if (messageDialogNode instanceof TimedMessageDialogNode)
            timedMessageDialogNodeFaderTask.startFadeTask((TimedMessageDialogNode) messageDialogNode);
      }
   }


   private void dismissLockingComponentNode(final ComponentNode componentNode)
   {
      if (lockingComponents.peek() == componentNode)
      {
         if (componentNode instanceof TimedMessageDialogNode)
            timedMessageDialogNodeFaderTask.cancelFadeTask();

         lockingComponents.poll();

         if (lockingComponents.isEmpty())
         {
            showUnlockedDisplay();
            notifyListenersOfLockingComponentDismissed(componentNode, true);
            notifyListenersOfUnlockedState();
         }
         else
         {
            final ComponentNode newVisibleComponentNode = lockingComponents.peek();
            setActiveLockingComponent(newVisibleComponentNode.component);
            notifyListenersOfLockingComponentDismissed(componentNode, true);
            processNewLockingComponentNode(newVisibleComponentNode);
         }
      }
      else if (lockingComponents.remove(componentNode))
         notifyListenersOfLockingComponentDismissed(componentNode, false);
   }


   private void showUnlockedDisplay()
   {
      setActiveLockingComponent(new JPanel(null));
      delegateComponent.setLocked(false);
   }


   private void dismissTemporaryLockingComponentNode(final ComponentNode componentNode)
   {
      if (componentNode instanceof MessageDialogNode)
      {
         final MessageDialogNode messageDialogNode = (MessageDialogNode) componentNode;

         final PresetOptionConfiguration optionConfiguration = messageDialogNode.messageDialog.getPresetOptionConfiguration();

         if ((optionConfiguration == PresetOptionConfiguration.OKCancel) ||
             (optionConfiguration == PresetOptionConfiguration.YesNoCancel))
         {
            final int cancelButtonIndex = optionConfiguration.toButtonIndex(PresetOptionSelection.Cancel);
            final AbstractButton cancelButton = (AbstractButton) messageDialogNode.messageDialog.getInputComponents()[cancelButtonIndex];
            cancelButton.doClick();
         }
         else
            dismissLockingComponentNode(componentNode);
      }
      else
         dismissLockingComponentNode(componentNode);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private boolean handleShowLockingComponent(final JComponent lockedComponent, final ComponentDisplayPriority displayPriority, final boolean isTemporary)
   {
      return addLockingComponentNode(new ComponentNode(lockedComponent, displayPriority, isTemporary));
   }


   // Returns true if the component was present, false otherwise.
   private boolean handleDismissLockingComponent(final JComponent component)
   {
      final ComponentNode componentNode = getLockingComponentNode(component);
      if (componentNode != null)
      {
         dismissLockingComponentNode(componentNode);
         return true;
      }
      else
         return false;
   }


   private boolean handleShowMessageDialog(final MessageDialog messageDialog, final ComponentDisplayPriority displayPriority, final boolean isTemporary,
                                           final PresetOptionSelection defaultAction)
   {
      return handleShowMessageDialog(messageDialog, displayPriority, isTemporary, (defaultAction != null) ? messageDialog.getPresetOptionConfiguration().toButtonIndex(defaultAction) : -1);
   }


   private boolean handleShowMessageDialog(final MessageDialog messageDialog, final ComponentDisplayPriority displayPriority, final boolean isTemporary,
                                           final int defaultFocusIndex)
   {
      return addLockingComponentNode(new MessageDialogNode(messageDialog, displayPriority, isTemporary, defaultFocusIndex));
   }


   private boolean handleShowMessageDialog(final MessageDialog.Builder messageDialogBuilder, final ComponentDisplayPriority displayPriority, final boolean isTemporary,
                                           final int defaultFocusIndex, final long displayDurationMilliseconds)
   {
      messageDialogBuilder.setTransparencyController(timedMessageDialogNodeFaderTask);
      final MessageDialog timedMessageDialog = new MessageDialog(messageDialogBuilder);
      return addLockingComponentNode(new TimedMessageDialogNode(timedMessageDialog, displayPriority, isTemporary, defaultFocusIndex, displayDurationMilliseconds));
   }


   private void handleDismissTemporaryComponents()
   {
      // See the header memos for why this operation is triggered by the caller and not automated by a HierarchyEvent.

      /* Create a separate list of the temporary locking components and iterate over that to prevent a concurrent modification exception
       * on lockingComponents if the fired actions on the message dialogs ultimately result in them being removed from the display,
       *  ie. removed from the lockedComponents collection.
       */
      final List<ComponentNode> temporaryLockedComponents = new ArrayList<ComponentNode>(lockingComponents.size());
      for (final ComponentNode node : lockingComponents)
      {
         if (node.isTemporary)
            temporaryLockedComponents.add(node);
      }

      for (final ComponentNode node : temporaryLockedComponents)
         dismissTemporaryLockingComponentNode(node);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public void addDisplayListener(final DisplayListener displayListener)
   {
      displayListeners.add(displayListener);
   }


   final public void removeDisplayListener(final DisplayListener displayListener)
   {
      displayListeners.remove(displayListener);
   }


   final public JComponent getRootComponent()
   {
      return delegateComponent.getRootComponent();
   }


   final public JComponent getUnlockedComponent()
   {
      return delegateComponent.getUnlockedComponent();
   }


   final public boolean isLocked()
   {
      return delegateComponent.isLocked();
   }


   final public boolean showLockingComponent(final JComponent lockedComponent, final ComponentDisplayPriority displayPriority, final boolean isTemporary)
   {
      return handleShowLockingComponent(lockedComponent, displayPriority, isTemporary);
   }


   final public boolean dismissLockingComponent(final JComponent lockedComponent)
   {
      return handleDismissLockingComponent(lockedComponent);
   }


   final public boolean showMessageDialog(final MessageDialog messageDialog, final ComponentDisplayPriority displayPriority)
   {
      return handleShowMessageDialog(messageDialog, displayPriority, false, -1);
   }


   final public boolean showMessageDialog(final MessageDialog messageDialog, final ComponentDisplayPriority displayPriority, final boolean isTemporary,
                                          final PresetOptionSelection defaultAction)
   {
      return handleShowMessageDialog(messageDialog, displayPriority, isTemporary, defaultAction);
   }


   final public boolean showMessageDialog(final MessageDialog messageDialog, final ComponentDisplayPriority displayPriority, final boolean isTemporary,
                                          final int defaultFocusIndex)
   {
      return handleShowMessageDialog(messageDialog, displayPriority, isTemporary, defaultFocusIndex);
   }


   final public boolean showTimedMessageDialog(final MessageDialog.Builder messageDialogBuilder, final ComponentDisplayPriority displayPriority, final boolean isTemporary,
                                               final int defaultFocusIndex, final long displayDurationMilliseconds)
   {
      return handleShowMessageDialog(messageDialogBuilder, displayPriority, isTemporary, defaultFocusIndex, displayDurationMilliseconds);
   }


   final public boolean dismissMessageDialog(final MessageDialog messageDialog)
   {
      return dismissLockingComponent(messageDialog.getDelegate());
   }


   final public void dismissTemporaryComponents()
   {
      handleDismissTemporaryComponents();
   }


   final public void shutdown()
   {
      timedMessageDialogNodeFaderTask.shutdown();
   }
}