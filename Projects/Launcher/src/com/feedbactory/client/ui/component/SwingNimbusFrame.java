/* Memos:
 *
 * - The sliding menu components are added to the JFrame's LayeredPane at a display priority higher than all other components. This ensures that there are no weird
 *   effects such as the menus being hidden beneath a combo box pop-up, or a tooltip. It also means no drawing clashes with overlapping child components on
 *   the content panel - previously the sliding menus (although not the invoking buttons at the menu top) were added directly to the content panel rather than directly
 *   to the SwingNimbusFrame, meaning that they were competing with regular components for paint priority; according to the GroupLayout manager used, they were
 *   sharing the same display position within the panel, at the same paint priority. Overriding JPanel's isOptimizedDrawingEnabled() to false
 *   became necessary to obtain a correct draw, when for example a mouse-over occurred on a combo-box while an 'overlapping' menu was still open. Without overriding
 *   the flag to false, the combo-box would be temporarily drawn over the top of the sliding menu. Setting the component Z order didn't appear to help.
 */


package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.launch.core.ConfigurationManager;
import com.feedbactory.client.ui.UIConstants;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.swing.*;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;


final public class SwingNimbusFrame
{
   static final private int MaximumShadowTransparency = 130;
   static final private int ActiveFrameShadowLength = 9;
   static final private int InactiveFrameShadowLength = 4;

   static
   {
      /* PopupFactory in JRE7 introduces logic in one of its getPopupType() methods that checks to see whether the popup's parent window is (not) opaque, translucent, or shaped,
       * presumably because popups previously assumed safe to be spawned as lightweights might overflow the parent window bounds if one of the former cases is true.
       * In order to display correctly, all popups that overflow the parent window bounds must be heavyweights. However, for this frame (which is not opaque), this extra
       * paranoid checking has ramifications for our popup tooltips which we'd like to decorate with drop shadows. Without our ugly hack of overriding the popup factory and
       * temporarily setting the window opacity to true at the right time, the drop shadow would be drawn onto a heavyweight popup (having an opaque background).
       */
      if (! System.getProperty("java.version").startsWith("1.6"))
         PopupFactory.setSharedInstance(new JRE7TranslucentPopupsFactoryHack());
   }

   final private DelegateFrame delegateFrame = new DelegateFrame();

   final private NimbusBorderPanel borderPanel;

   final private JComponent shadowPanel;
   final private int frameShadowLength;

   /* Listener required to horizontally reposition the right menu components when the borderPanel is resized.
    * If/when the menu components change, the listener needs to be removed from the borderPanel.
    * I decided to add this here rather than to the borderPanel since the menu components are added to the outer JFrame's layered pane,
    * therefore ownership is more at that level rather than at the nested border panel level.
    * Note that this is referring to the menu components that appear atop other components when the menus are invoked, and not the
    * menu invocation components themselves, which are directly added to the borderPanel's title bar.
    */
   private ComponentAdapter rightMenuComponentsResizeListener;


   public SwingNimbusFrame()
   {
      borderPanel = new NimbusBorderPanel(this);

      if (TranslucencyUtilities.isTranslucencySupported())
      {
         shadowPanel = new RoundedShadowComponent(delegateFrame, borderPanel, ActiveFrameShadowLength, InactiveFrameShadowLength, MaximumShadowTransparency);
         frameShadowLength = Math.max(ActiveFrameShadowLength, InactiveFrameShadowLength);
      }
      else
      {
         shadowPanel = null;
         frameShadowLength = 0;
      }

      initialise();
   }


   private void initialise()
   {
      initialiseDelegateFrame();
   }


   private void initialiseDelegateFrame()
   {
      delegateFrame.setUndecorated(true);

      // If we can't set the frame's translucency the corners will be squared off rather than rounded.
      if (TranslucencyUtilities.isTranslucencySupported())
      {
         try
         {
            TranslucencyUtilities.setWindowOpaque(delegateFrame, false);

            // This call is required on Mac OS X, otherwise the grey background behind the shadow is still drawn.
            delegateFrame.getRootPane().setBackground(UIConstants.ClearColour);
         }
         catch (final Exception anyException)
         {
            throw new RuntimeException(anyException);
         }
      }

      initialiseDelegateFrameContentPane();
   }


   private void initialiseDelegateFrameContentPane()
   {
      delegateFrame.setContentPane((shadowPanel != null) ? shadowPanel : borderPanel);
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final private class DelegateFrame extends JFrame
   {
      private Rectangle restoredFrameBounds;

      /* We need to maintain our own interpretation of whether or not the frame is maximised. We can't really maximise the underlying delegate frame, otherwise
       * the custom window decorations would extend outside of the window bounds.
       */
      private boolean isMaximised;

      static private boolean isJRE7TranslucentPopupsHackEnabled;


      static private void setJRE7TranslucentPopupsHackEnabled(final boolean isJRE7TranslucentPopupsHackEnabled)
      {
         DelegateFrame.isJRE7TranslucentPopupsHackEnabled = isJRE7TranslucentPopupsHackEnabled;
      }


      private boolean isMaximised()
      {
         return isMaximised;
      }


      private void setRestored()
      {
         setBounds(restoredFrameBounds);
         isMaximised = false;
      }


      private void setMaximisedAt(final Rectangle bounds)
      {
         restoredFrameBounds = getBounds();
         setBounds(bounds);
         isMaximised = true;
      }


      private Rectangle getRestoredBounds()
      {
         return restoredFrameBounds;
      }


      private void setRestoredBounds(final Rectangle bounds)
      {
         if (! isMaximised)
            setBounds(bounds);
         else
         {
            restoredFrameBounds.x = bounds.x;
            restoredFrameBounds.y = bounds.y;
            restoredFrameBounds.width = bounds.width;
            restoredFrameBounds.height = bounds.height;
         }
      }


      private void setRestoredSize(final Dimension size)
      {
         if (! isMaximised)
            setSize(size);
         else
         {
            restoredFrameBounds.width = size.width;
            restoredFrameBounds.height = size.height;
         }
      }


      final public void setRestoredLocation(final int x, final int y)
      {
         if (! isMaximised)
            setLocation(x, y);
         else
         {
            restoredFrameBounds.x = x;
            restoredFrameBounds.y = y;
         }
      }


      @Override
      final public int getExtendedState()
      {
         final int realExtendedState = super.getExtendedState();

         if (realExtendedState == Frame.ICONIFIED)
            return Frame.ICONIFIED;
         else if (isMaximised)
            return Frame.MAXIMIZED_BOTH;
         else
            return Frame.NORMAL;
      }


      @Override
      final public boolean isOpaque()
      {
         return isJRE7TranslucentPopupsHackEnabled;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class NimbusBorderPanel extends JPanel
   {
      static final private int DefaultMarginSize = 6;

      static final private Color ActiveOuterBorderColour1 = new Color(43, 46, 51);
      static final private Color ActiveOuterBorderColour2 = new Color(204, 207, 213);
      static final private Color ActiveOuterBorderColour3 = new Color(171, 176, 186);
      static final private Color ActiveOuterBorderColour4 = new Color(149, 158, 167);
      static final private Color ActiveOuterBorderColour5 = new Color(108, 114, 128);
      static final private Color ActiveGradientStartColour = new Color(227, 229, 232);
      static final private Color ActiveGradientEndColour = ActiveOuterBorderColour3;

      static final private Color InactiveOuterBorderColour1 = new Color(136, 141, 151);
      static final private Color InactiveOuterBorderColour2 = new Color(211, 214, 219);
      static final private Color InactiveOuterBorderColour3 = new Color(192, 196, 203);
      static final private Color InactiveOuterBorderColour4 = new Color(203, 207, 213);
      static final private Color InactiveOuterBorderColour5 = new Color(158, 163, 173);
      static final private Color InactiveGradientStartColour = new Color(170, 174, 183);
      static final private Color InactiveGradientEndColour = InactiveOuterBorderColour3;

      static final private int ResizeBorderThresholdSize = DefaultMarginSize + 2;

      static final private Dimension ControlButtonDimensions = new Dimension(18, 15);
      static final private int ControlButtonTopClearance = 4;
      static final private int ControlButtonBottomClearance = 7;
      static final private int ControlButtonSpacing = 3;

      static private boolean AreControlButtonsInitialised;
      static final private BufferedImage[] MinimiseButtonImages = new BufferedImage[ControlButtonImageDataIndex.values().length];
      static final private BufferedImage[] MaximiseButtonImages = new BufferedImage[ControlButtonImageDataIndex.values().length];
      static final private BufferedImage[] RestoreButtonImages = new BufferedImage[ControlButtonImageDataIndex.values().length];
      static final private BufferedImage[] CloseButtonImages = new BufferedImage[ControlButtonImageDataIndex.values().length];

      static final private Dimension IconSize = new Dimension(16, 16);
      static final private int IconDrawYOffset = 5;

      static final private Font TitleFont = UIConstants.RegularFont;
      static final private Point TitleDrawOffset = new Point(8, 18);

      static final private int MenuInset = 8;
      static final private int InterMenuGap = 5;

      static private enum ControlButtonImageDataIndex
      {
         Enabled((byte) 0),
         MouseOver((byte) 1),
         Pressed((byte) 2),
         Inactive((byte) 3);

         final private byte imageIndex;

         private ControlButtonImageDataIndex(final byte imageIndex)
         {
            this.imageIndex = imageIndex;
         }
      }

      static private enum CursorDirection
      {
         None((byte) 0),
         North((byte) 1),
         East((byte) 2),
         South((byte) 4),
         West((byte) 8);

         final private byte flag;

         private CursorDirection(final byte index)
         {
            this.flag = index;
         }
      };

      static final private int[] DirectionalCursors;

      static
      {
         DirectionalCursors = new int[13];
         DirectionalCursors[CursorDirection.None.flag] = Cursor.DEFAULT_CURSOR;
         DirectionalCursors[CursorDirection.North.flag] = Cursor.N_RESIZE_CURSOR;
         DirectionalCursors[CursorDirection.North.flag | CursorDirection.East.flag] = Cursor.NE_RESIZE_CURSOR;
         DirectionalCursors[CursorDirection.East.flag] = Cursor.E_RESIZE_CURSOR;
         DirectionalCursors[CursorDirection.South.flag | CursorDirection.East.flag] = Cursor.SE_RESIZE_CURSOR;
         DirectionalCursors[CursorDirection.South.flag] = Cursor.S_RESIZE_CURSOR;
         DirectionalCursors[CursorDirection.South.flag | CursorDirection.West.flag] = Cursor.SW_RESIZE_CURSOR;
         DirectionalCursors[CursorDirection.West.flag] = Cursor.W_RESIZE_CURSOR;
         DirectionalCursors[CursorDirection.North.flag | CursorDirection.West.flag] = Cursor.NW_RESIZE_CURSOR;
      };

      /* The explicit outer class reference (being a static class) is a bit annoying, but having a static class also means that we can enclose all of the static constant
       * objects within this inner class.
       */
      final private SwingNimbusFrame parent;

      private int frameTitleBarHeight;

      private Component content = new JPanel(null);

      private String frameTitle;
      private Image frameIcon;

      private JButton minimiseButton = new JButton();
      private JButton maximiseButton = new JButton();
      private JButton restoreButton = new JButton();
      private JButton closeButton = new JButton();

      private boolean isPaintingActiveTitleButtons;

      private int windowDecorationStyle = JRootPane.FRAME;

      private byte resizeDirection;
      private boolean isResizing;
      private Point lastDragPoint;
      private Cursor originalCursor;


      static private void lazyInitialiseFrameShellTitleBarControlButtonImages()
      {
         try
         {
            // Nimbus LAF changes packages between JDK6 and JDK7, hence we can't rely on using an instanceof NimbusLookAndFeel test.
            if (! UIManager.getLookAndFeel().getName().equals("Nimbus"))
               throw new IllegalStateException("Nimbus LAF must be set before creation of the first SwingNimbusFrame.");

            final String minimiseButtonPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.iconifyButton\"[Enabled].backgroundPainter";
            final String minimiseButtonMouseoverPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.iconifyButton\"[MouseOver].backgroundPainter";
            final String minimiseButtonPressedPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.iconifyButton\"[Pressed].backgroundPainter";
            final String minimiseButtonNotFocusedPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.iconifyButton\"[Enabled+WindowNotFocused].backgroundPainter";

            final String maximiseButtonPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.maximizeButton\"[Enabled].backgroundPainter";
            final String maximiseButtonMouseoverPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.maximizeButton\"[MouseOver].backgroundPainter";
            final String maximiseButtonPressedPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.maximizeButton\"[Pressed].backgroundPainter";
            final String maximiseButtonNotFocusedPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.maximizeButton\"[Enabled+WindowNotFocused].backgroundPainter";

            final String restoreButtonPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.maximizeButton\"[Enabled+WindowMaximized].backgroundPainter";
            final String restoreButtonMouseoverPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.maximizeButton\"[MouseOver+WindowMaximized].backgroundPainter";
            final String restoreButtonPressedPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.maximizeButton\"[Pressed+WindowMaximized].backgroundPainter";
            final String restoreButtonNotFocusedPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.maximizeButton\"[Enabled+WindowMaximized+WindowNotFocused].backgroundPainter";

            final String closeButtonPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.closeButton\"[Enabled].backgroundPainter";
            final String closeButtonMouseoverPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.closeButton\"[MouseOver].backgroundPainter";
            final String closeButtonPressedPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.closeButton\"[Pressed].backgroundPainter";
            final String closeButtonNotFocusedPainterKey = "InternalFrame:InternalFrameTitlePane:\"InternalFrameTitlePane.closeButton\"[Enabled+WindowNotFocused].backgroundPainter";

            initialiseFrameShellTitleBarControlButtonImage(MinimiseButtonImages, minimiseButtonPainterKey, minimiseButtonMouseoverPainterKey, minimiseButtonPressedPainterKey, minimiseButtonNotFocusedPainterKey);
            initialiseFrameShellTitleBarControlButtonImage(MaximiseButtonImages, maximiseButtonPainterKey, maximiseButtonMouseoverPainterKey, maximiseButtonPressedPainterKey, maximiseButtonNotFocusedPainterKey);
            initialiseFrameShellTitleBarControlButtonImage(RestoreButtonImages, restoreButtonPainterKey, restoreButtonMouseoverPainterKey, restoreButtonPressedPainterKey, restoreButtonNotFocusedPainterKey);
            initialiseFrameShellTitleBarControlButtonImage(CloseButtonImages, closeButtonPainterKey, closeButtonMouseoverPainterKey, closeButtonPressedPainterKey, closeButtonNotFocusedPainterKey);

            AreControlButtonsInitialised = true;
         }
         catch (final Exception anyException)
         {
            throw new RuntimeException(anyException);
         }
      }


      static private void initialiseFrameShellTitleBarControlButtonImage(final BufferedImage[] imageButtons, final String enabledPainterKey, final String mouseOverPainterKey,
                                                                         final String pressedPainterKey, final String inactivePainterKey) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException
      {
         imageButtons[ControlButtonImageDataIndex.Enabled.imageIndex] = createButtonImageFromNimbusPainter(enabledPainterKey);
         imageButtons[ControlButtonImageDataIndex.MouseOver.imageIndex] = createButtonImageFromNimbusPainter(mouseOverPainterKey);
         imageButtons[ControlButtonImageDataIndex.Pressed.imageIndex] = createButtonImageFromNimbusPainter(pressedPainterKey);
         imageButtons[ControlButtonImageDataIndex.Inactive.imageIndex] = createButtonImageFromNimbusPainter(inactivePainterKey);
      }


      static private BufferedImage createButtonImageFromNimbusPainter(final String painterKey) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException
      {
         final Object painter = UIManager.getLookAndFeelDefaults().get(painterKey);
         final Class<?> painterClass = painter.getClass();
         final Method painterMethod = painterClass.getMethod("paint", Graphics2D.class, JComponent.class, Integer.TYPE, Integer.TYPE);

         final BufferedImage swingButtonImage = UIUtilities.createCompatibleImage(ControlButtonDimensions.width, ControlButtonDimensions.height, Transparency.TRANSLUCENT);
         final Graphics2D graphics2D = swingButtonImage.createGraphics();
         painterMethod.invoke(painter, graphics2D, null, ControlButtonDimensions.width, ControlButtonDimensions.height);
         graphics2D.dispose();

         return swingButtonImage;
      }



      private NimbusBorderPanel(final SwingNimbusFrame parent)
      {
         this.parent = parent;

         if (! AreControlButtonsInitialised)
            lazyInitialiseFrameShellTitleBarControlButtonImages();
         
         initialise();
      }


      private void initialise()
      {
         initialiseTitleBarControlButtons();

         initialiseBorderPanelLayout(new JComponent[] {}, new JComponent[] {}, new JComponent[] {}, new JComponent[] {});

         initialiseBorderPanelListeners();

         initialiseParentFrameListeners();
      }


      private void initialiseTitleBarControlButtons()
      {
         minimiseButton = initialiseTitleBarControlButton(MinimiseButtonImages);
         maximiseButton = initialiseTitleBarControlButton(MaximiseButtonImages);
         restoreButton = initialiseTitleBarControlButton(RestoreButtonImages);
         closeButton = initialiseTitleBarControlButton(CloseButtonImages);

         minimiseButton.addActionListener(new ActionListener()
         {
            @Override
            final public void actionPerformed(final ActionEvent actionEvent)
            {
               parent.setIconified();
            }
         });

         maximiseButton.addActionListener(new ActionListener()
         {
            @Override
            final public void actionPerformed(final ActionEvent actionEvent)
            {
               parent.handleToggleMaximised();
            }
         });

         restoreButton.addActionListener(new ActionListener()
         {
            @Override
            final public void actionPerformed(final ActionEvent actionEvent)
            {
               parent.handleToggleMaximised();
            }
         });

         closeButton.addActionListener(new ActionListener()
         {
            @Override
            final public void actionPerformed(final ActionEvent actionEvent)
            {
               parent.handleWindowCloseButtonPressed();
            }
         });
      }


      private JButton initialiseTitleBarControlButton(final BufferedImage[] buttonImages)
      {
         final JButton button = new JButton();

         button.setBorder(null);
         button.setFocusable(false);

         // Since the frame will initially not be active, the initial state of the buttons should also be set to paint them for the inactive frame.
         button.setIcon(new ImageIcon(buttonImages[ControlButtonImageDataIndex.Inactive.imageIndex]));
         button.setRolloverIcon(new ImageIcon(buttonImages[ControlButtonImageDataIndex.MouseOver.imageIndex]));
         button.setPressedIcon(new ImageIcon(buttonImages[ControlButtonImageDataIndex.Pressed.imageIndex]));
         button.setDisabledIcon(new ImageIcon(buttonImages[ControlButtonImageDataIndex.Enabled.imageIndex]));

         return button;
      }


      private void initialiseBorderPanelLayout(final JComponent[] leftMenuInvocationComponents, final JComponent[] leftMenuComponents,
                                               final JComponent[] rightMenuInvocationComponents, final JComponent[] rightMenuComponents)
      {
         final Dimension[] leftMenuInvocationComponentsPreferredSizes = getPreferredSizes(leftMenuInvocationComponents);
         final Dimension[] leftMenuComponentsPreferredSizes = getPreferredSizes(leftMenuComponents);
         final Dimension[] rightMenuInvocationComponentsPreferredSizes = getPreferredSizes(rightMenuInvocationComponents);
         final Dimension[] rightMenuComponentsPreferredSizes = getPreferredSizes(rightMenuComponents);

         final int tallestTitleBarComponentHeight = getTallestComponentHeight(leftMenuInvocationComponentsPreferredSizes, rightMenuInvocationComponentsPreferredSizes);

         // If the frame has custom controls inserted, it needs to allow room for the tallest of them plus an additional gap between them and the top control buttons.
         if (tallestTitleBarComponentHeight > 0)
            frameTitleBarHeight = ControlButtonTopClearance + ControlButtonDimensions.height + (2 * ControlButtonBottomClearance) + tallestTitleBarComponentHeight;
         else
            frameTitleBarHeight = ControlButtonTopClearance + ControlButtonDimensions.height + ControlButtonBottomClearance;

         final GroupLayout panelLayout = new GroupLayout(this);
         setLayout(panelLayout);

         final SequentialGroup titleBarComponentsHorizontalGroup = panelLayout.createSequentialGroup();
         titleBarComponentsHorizontalGroup.addGap(MenuInset);

         int interMenuGap;

         for (int componentIndex = 0; componentIndex < leftMenuInvocationComponents.length; componentIndex ++)
         {
            titleBarComponentsHorizontalGroup.addComponent(leftMenuInvocationComponents[componentIndex], GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);

            if (componentIndex < (leftMenuInvocationComponents.length - 1))
            {
               // Add a gap so that the left edge of the next menu invocation component will line up with the left edge of its corresponding child menu component.
               if (leftMenuComponentsPreferredSizes[componentIndex].width > leftMenuInvocationComponentsPreferredSizes[componentIndex].width)
                  interMenuGap = (leftMenuComponentsPreferredSizes[componentIndex].width + InterMenuGap) - leftMenuInvocationComponentsPreferredSizes[componentIndex].width;
               else
                  interMenuGap = InterMenuGap;

               titleBarComponentsHorizontalGroup.addGap(interMenuGap);
            }
         }

         titleBarComponentsHorizontalGroup.addGap(GroupLayout.PREFERRED_SIZE, InterMenuGap, Integer.MAX_VALUE);

         for (int componentIndex = 0; componentIndex < rightMenuInvocationComponents.length; componentIndex ++)
         {
            titleBarComponentsHorizontalGroup.addComponent(rightMenuInvocationComponents[componentIndex], GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);

            if (rightMenuComponentsPreferredSizes[componentIndex].width > rightMenuInvocationComponentsPreferredSizes[componentIndex].width)
               interMenuGap = (rightMenuComponentsPreferredSizes[componentIndex].width) - rightMenuInvocationComponentsPreferredSizes[componentIndex].width;
            else
               interMenuGap = 0;

            /* Don't add an inter-menu gap (or a menu inset gap) after the rightmost menu component.
             * This is really only because I'm using shadowed menu components, and the shadow already provides a nice visual gap at the right.
             */
            if (componentIndex < (rightMenuInvocationComponents.length - 1))
               interMenuGap += InterMenuGap;

            titleBarComponentsHorizontalGroup.addGap(interMenuGap);
         }

         final SequentialGroup titleBarComponentsVerticalGroup = panelLayout.createSequentialGroup();

         if (tallestTitleBarComponentHeight > 0)
         {
            final ParallelGroup parallelGroup = panelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE);
            for (final JComponent component : leftMenuInvocationComponents)
               parallelGroup.addComponent(component, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);

            for (final JComponent component : rightMenuInvocationComponents)
               parallelGroup.addComponent(component, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);

            titleBarComponentsVerticalGroup.addGroup(parallelGroup);
            titleBarComponentsVerticalGroup.addGap(ControlButtonBottomClearance);
         }

         final SequentialGroup controlButtonGroup = panelLayout.createSequentialGroup();

         if (ConfigurationManager.isRunningWindows)
         {
            controlButtonGroup.addGap(0, 0, Integer.MAX_VALUE)
               .addComponent(minimiseButton)
               .addGap(ControlButtonSpacing)
               .addComponent(maximiseButton)
               .addGap(ControlButtonSpacing)
               .addComponent(closeButton);
         }
         else
         {
            controlButtonGroup.addComponent(closeButton)
               .addGap(ControlButtonSpacing)
               .addComponent(minimiseButton)
               .addGap(ControlButtonSpacing)
               .addComponent(maximiseButton)
               .addGap(0, 0, Integer.MAX_VALUE);
         }

         panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
            .addGap(DefaultMarginSize)
            .addGroup(panelLayout.createParallelGroup()
               .addGroup(controlButtonGroup)
               .addGroup(titleBarComponentsHorizontalGroup)
               .addComponent(content, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
            )
            .addGap(DefaultMarginSize)
         );

         panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
            .addGap(ControlButtonTopClearance)
            .addGroup(panelLayout.createParallelGroup()
               .addComponent(minimiseButton)
               .addComponent(maximiseButton)
               .addComponent(closeButton)
            )
            .addGap(ControlButtonBottomClearance)
            .addGroup(titleBarComponentsVerticalGroup)
            /* This gap was previously used when the constructor used to allow custom title bar heights (specified as an int, rather than derived from controls and
             * standard gap sizes as it is now):
             *
             * .addGap(frameTitleBarHeight - (ControlButtonTopClearance + ControlButtonDimensions.height))
             */
            .addComponent(content, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
            .addGap(DefaultMarginSize)
         );

         // Reserve space for the title bar even when there are no visible buttons, ie. window decoration style is JRootPane.NONE.
         panelLayout.setHonorsVisibility(minimiseButton, false);
         panelLayout.setHonorsVisibility(maximiseButton, false);
         panelLayout.setHonorsVisibility(closeButton, false);
      }


      private Dimension[] getPreferredSizes(final JComponent[] components)
      {
         final Dimension[] preferredSizes = new Dimension[components.length];

         for (int componentIndex = 0; componentIndex < components.length; componentIndex ++)
            preferredSizes[componentIndex] = components[componentIndex].getPreferredSize();

         return preferredSizes;
      }


      private int getTallestComponentHeight(final Dimension[] leftMenuInvocationComponentsPreferredSizes, final Dimension[] rightMenuInvocationComponentsPreferredSizes)
      {
         int tallestComponentHeight = 0;
         for (final Dimension dimension : leftMenuInvocationComponentsPreferredSizes)
         {
            if (dimension.height > tallestComponentHeight)
               tallestComponentHeight = dimension.height;
         }

         for (final Dimension dimension : rightMenuInvocationComponentsPreferredSizes)
         {
            if (dimension.height > tallestComponentHeight)
               tallestComponentHeight = dimension.height;
         }

         return tallestComponentHeight;
      }


      private void initialiseBorderPanelListeners()
      {
         addMouseMotionListener(new MouseMotionListener()
         {
            @Override
            final public void mouseDragged(final MouseEvent mouseEvent)
            {
               handleFrameMouseDraggedEvent(mouseEvent);
            }


            @Override
            final public void mouseMoved(final MouseEvent mouseEvent)
            {
               handleFrameMouseMovedEvent(mouseEvent);
            }
         });

         addMouseListener(new MouseAdapter()
         {
            @Override
            final public void mouseClicked(final MouseEvent mouseEvent)
            {
               handleFrameMouseClickedEvent(mouseEvent);
            }


            @Override
            final public void mousePressed(final MouseEvent mouseEvent)
            {
               handleFrameMousePressedEvent(mouseEvent);
            }


            @Override
            final public void mouseReleased(final MouseEvent mouseEvent)
            {
               handleFrameMouseReleasedEvent(mouseEvent);
            }


            @Override
            final public void mouseExited(final MouseEvent mouseEvent)
            {
               handleFrameMouseExitedEvent();
            }
         });
      }


      private void initialiseParentFrameListeners()
      {
         parent.addWindowFocusListener(new WindowFocusListener()
         {
            @Override
            final public void windowGainedFocus(final WindowEvent windowEvent)
            {
               setBorderActive(true);
            }

            @Override
            final public void windowLostFocus(final WindowEvent windowEvent)
            {
               setBorderActive(false);
            }
         });
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private void handleFrameMouseDraggedEvent(final MouseEvent mouseEvent)
      {
         if ((! parent.isMaximised()) && (lastDragPoint != null) && ((mouseEvent.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0))
         {
            int dragDeltaX = mouseEvent.getXOnScreen() - lastDragPoint.x;
            int dragDeltaY = mouseEvent.getYOnScreen() - lastDragPoint.y;

            lastDragPoint = mouseEvent.getLocationOnScreen();

            final Rectangle frameBounds = parent.getDecoratedBounds();

            if (resizeDirection == CursorDirection.None.flag)
            {
               int yLocation = frameBounds.y + dragDeltaY;
               if (dragDeltaY < 0)
               {
                  // Ensure that the top of the frame can't move beyond the screen bounds.
                  final Rectangle screenBounds = parent.getMaximumWindowBounds();
                  if (yLocation < screenBounds.y)
                     yLocation = screenBounds.y;
               }

               parent.setLocation(frameBounds.x + dragDeltaX, yLocation);
            }
            else
            {
               final Dimension frameMinimumSize = parent.getMinimumSize();

               if ((resizeDirection & CursorDirection.West.flag) != 0)
               {
                  if (dragDeltaX > 0)
                  {
                     final int maximumDeltaX = frameBounds.width - frameMinimumSize.width;
                     if (dragDeltaX > maximumDeltaX)
                        dragDeltaX = maximumDeltaX;
                  }

                  frameBounds.x += dragDeltaX;
                  frameBounds.width -= dragDeltaX;
               }
               else if ((resizeDirection & CursorDirection.East.flag) != 0)
               {
                  if (dragDeltaX < 0)
                  {
                     final int minimumDragDeltaX = frameMinimumSize.width - frameBounds.width;
                     if (dragDeltaX < minimumDragDeltaX)
                        dragDeltaX = minimumDragDeltaX;
                  }

                  frameBounds.width += dragDeltaX;
               }

               if ((resizeDirection & CursorDirection.North.flag) != 0)
               {
                  if (dragDeltaY > 0)
                  {
                     final int maximumDeltaY = frameBounds.height - frameMinimumSize.height;
                     if (dragDeltaY > maximumDeltaY)
                        dragDeltaY = maximumDeltaY;
                  }
                  else if (dragDeltaY < 0)
                  {
                     // Ensure that the top of the frame can't be dragged beyond the screen bounds.
                     final Rectangle screenBounds = parent.getMaximumWindowBounds();
                     if (dragDeltaY < (screenBounds.y - frameBounds.y))
                        dragDeltaY = (screenBounds.y - frameBounds.y);
                  }

                  frameBounds.y += dragDeltaY;
                  frameBounds.height -= dragDeltaY;
               }
               else if ((resizeDirection & CursorDirection.South.flag) != 0)
               {
                  if (dragDeltaY < 0)
                  {
                     final int minimumDragDeltaY = frameMinimumSize.height - frameBounds.height;
                     if (dragDeltaY < minimumDragDeltaY)
                        dragDeltaY = minimumDragDeltaY;
                  }

                  frameBounds.height += dragDeltaY;
               }

               parent.setDecoratedBounds(frameBounds);
            }
         }
      }


      private void handleFrameMouseMovedEvent(final MouseEvent mouseEvent)
      {
         if (parent.isMaximised() || (! parent.isResizable()))
            return;

         int previousResizeDirection = resizeDirection;

         resizeDirection = 0;

         if (mouseEvent.getX() < ResizeBorderThresholdSize)
            resizeDirection = CursorDirection.West.flag;
         else if (mouseEvent.getX() > (getWidth() - ResizeBorderThresholdSize))
            resizeDirection = CursorDirection.East.flag;

         if (mouseEvent.getY() < ResizeBorderThresholdSize)
            resizeDirection |= CursorDirection.North.flag;
         else if (mouseEvent.getY() > (getHeight() - ResizeBorderThresholdSize))
            resizeDirection |= CursorDirection.South.flag;

         if (resizeDirection != previousResizeDirection)
         {
            if (previousResizeDirection == 0)
               originalCursor = parent.getCursor();

            parent.setCursor(Cursor.getPredefinedCursor(DirectionalCursors[resizeDirection]));
         }
      }


      private void handleFrameMouseClickedEvent(final MouseEvent mouseEvent)
      {
         if (parent.isResizable() && (mouseEvent.getClickCount() == 2) && (mouseEvent.getButton() == MouseEvent.BUTTON1))
            parent.handleToggleMaximised();
      }


      private void handleFrameMousePressedEvent(final MouseEvent mouseEvent)
      {
         if (mouseEvent.getButton() == MouseEvent.BUTTON1)
         {
            lastDragPoint = mouseEvent.getLocationOnScreen();

            if (resizeDirection != CursorDirection.None.flag)
               isResizing = true;
         }
      }


      private void handleFrameMouseReleasedEvent(final MouseEvent mouseEvent)
      {
         if (isResizing && (mouseEvent.getButton() == MouseEvent.BUTTON1))
         {
            resizeDirection = CursorDirection.None.flag;
            parent.setCursor(originalCursor);
            isResizing = false;
         }
      }


      private void handleFrameMouseExitedEvent()
      {
         if (! isResizing)
         {
            resizeDirection = 0;
            parent.setCursor(originalCursor);
         }
      }


      /****************************************************************************
       *
       ***************************************************************************/


      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         final Graphics2D graphics2D = (Graphics2D) graphics.create();

         paintBorder(graphics2D);

         paintTitleElements(graphics2D);

         graphics2D.dispose();
      }


      private void paintBorder(final Graphics2D graphics2D)
      {
         graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

         if (isPaintingActiveTitleButtons)
         {
            graphics2D.setColor(ActiveOuterBorderColour1);
            graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            graphics2D.setColor(ActiveOuterBorderColour2);
            graphics2D.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 9, 9);
            graphics2D.setColor(ActiveOuterBorderColour3);
            graphics2D.drawRect(2, frameTitleBarHeight - 4, getWidth() - 5, getHeight() - frameTitleBarHeight + 1);
            graphics2D.drawRect(3, frameTitleBarHeight - 3, getWidth() - 7, getHeight() - frameTitleBarHeight - 1);
            graphics2D.setColor(ActiveOuterBorderColour4);
            graphics2D.drawRect(4, frameTitleBarHeight - 2, getWidth() - 9, getHeight() - 3 - frameTitleBarHeight);
            graphics2D.setColor(ActiveOuterBorderColour5);
            graphics2D.drawRect(5, frameTitleBarHeight - 1, getWidth() - 11, getHeight() - 5 - frameTitleBarHeight);

            final GradientPaint activeFrameTitleBarGradientPaint = new GradientPaint(0, 0, ActiveGradientStartColour, 0, frameTitleBarHeight - 6, ActiveGradientEndColour);
            graphics2D.setPaint(activeFrameTitleBarGradientPaint);
            graphics2D.fillRect(2, 2, getWidth() - 4, frameTitleBarHeight - 6);
         }
         else
         {
            graphics2D.setColor(InactiveOuterBorderColour1);
            graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 11, 11);
            graphics2D.setColor(InactiveOuterBorderColour2);
            graphics2D.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 10, 10);
            graphics2D.setColor(InactiveOuterBorderColour3);
            graphics2D.drawRect(2, frameTitleBarHeight - 4, getWidth() - 5, getHeight() - frameTitleBarHeight + 1);
            graphics2D.drawRect(3, frameTitleBarHeight - 3, getWidth() - 7, getHeight() - frameTitleBarHeight - 1);
            graphics2D.setColor(InactiveOuterBorderColour4);
            graphics2D.drawRect(4, frameTitleBarHeight - 2, getWidth() - 9, getHeight() - frameTitleBarHeight - 3);
            graphics2D.setColor(InactiveOuterBorderColour5);
            graphics2D.drawRect(5, frameTitleBarHeight - 1, getWidth() - 11, getHeight() - frameTitleBarHeight - 5);

            final GradientPaint InactiveFrameTitleBarGradientPaint = new GradientPaint(0, 0, InactiveGradientStartColour, 0, frameTitleBarHeight - 6, InactiveGradientEndColour);
            graphics2D.setPaint(InactiveFrameTitleBarGradientPaint);
            graphics2D.fillRect(2, 2, getWidth() - 4, frameTitleBarHeight - 6);
         }
      }


      private void paintTitleElements(final Graphics2D graphics2D)
      {
         /* This is a bit hackish, since the title & icon could overlap the control buttons.
          * A more correct solution would take the control button dimensions (including OS-specific left or right position) into account.
          */
         if (frameTitle != null)
         {
            graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics2D.setFont(TitleFont);
            graphics2D.setColor(Color.BLACK);

            final FontMetrics fontMetrics = graphics2D.getFontMetrics();

            if (frameIcon != null)
            {
               final int titleElementsWidth = IconSize.width + TitleDrawOffset.x + fontMetrics.stringWidth(frameTitle);
               final int drawStartX = (getWidth() - titleElementsWidth) / 2;
               graphics2D.drawImage(frameIcon, drawStartX, IconDrawYOffset, null);
               graphics2D.drawString(frameTitle, drawStartX + IconSize.width + TitleDrawOffset.x, TitleDrawOffset.y);
            }
            else
            {
               final int titleElementsWidth = fontMetrics.stringWidth(frameTitle);
               final int drawStartX = (getWidth() - titleElementsWidth) / 2;
               graphics2D.drawString(frameTitle, drawStartX, TitleDrawOffset.y);
            }
         }
         else if (frameIcon != null)
         {
            final int titleElementsWidth = IconSize.width;
            final int drawStartX = (getWidth() - titleElementsWidth) / 2;
            graphics2D.drawImage(frameIcon, drawStartX, IconDrawYOffset, null);
         }
      }


      /****************************************************************************
       * 
       ***************************************************************************/


      private void setFrameIcon(final BufferedImage frameIcon)
      {
         if ((frameIcon.getWidth() != IconSize.width) || (frameIcon.getHeight() != IconSize.height))
            this.frameIcon = UIUtilities.getScaledImage(frameIcon, IconSize.width, IconSize.height, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
         else
            this.frameIcon = frameIcon;
      }


      private void setFrameTitle(final String frameTitle)
      {
         this.frameTitle = frameTitle;
      }


      final public void setWindowDecorationStyle(final int windowDecorationStyle)
      {
         if (windowDecorationStyle == JRootPane.NONE)
         {
            minimiseButton.setVisible(false);
            maximiseButton.setVisible(false);
            restoreButton.setVisible(false);
            closeButton.setVisible(false);
            repaint();
         }
         else if (windowDecorationStyle == JRootPane.PLAIN_DIALOG)
         {
            minimiseButton.setVisible(false);
            maximiseButton.setVisible(false);
            restoreButton.setVisible(false);
            closeButton.setVisible(true);
            repaint();
         }
         else if (windowDecorationStyle == JRootPane.FRAME)
         {
            minimiseButton.setVisible(true);
            maximiseButton.setVisible(parent.isResizable());
            restoreButton.setVisible(parent.isResizable());
            closeButton.setVisible(true);
            repaint();
         }
         else
            throw new IllegalArgumentException("Invalid window decoration style: " + windowDecorationStyle);

         this.windowDecorationStyle = windowDecorationStyle;
      }


      private void setMenuInvocationComponents(final JComponent[] leftMenuInvocationComponents, final JComponent[] leftMenuComponents,
                                               final JComponent[] rightMenuInvocationComponents, final JComponent[] rightMenuComponents)
      {
         initialiseBorderPanelLayout(leftMenuInvocationComponents, leftMenuComponents, rightMenuInvocationComponents, rightMenuComponents);
      }


      private void setResizable(final boolean isResizable)
      {
         if (windowDecorationStyle == JRootPane.FRAME)
         {
            maximiseButton.setVisible(isResizable);
            restoreButton.setVisible(isResizable);

            repaint();
         }
      }


      private void setMaximised(final boolean isMaximised)
      {
         if (isMaximised)
            ((GroupLayout) getLayout()).replace(maximiseButton, restoreButton);
         else
            ((GroupLayout) getLayout()).replace(restoreButton, maximiseButton);
      }


      private void setBorderActive(final boolean isActive)
      {
         if (this.isPaintingActiveTitleButtons != isActive)
         {
            this.isPaintingActiveTitleButtons = isActive;

            swapButtonIcons(minimiseButton);
            swapButtonIcons(maximiseButton);
            swapButtonIcons(restoreButton);
            swapButtonIcons(closeButton);

            parent.repaint();
         }
      }


      private void swapButtonIcons(final JButton button)
      {
         final Icon icon = button.getIcon();
         button.setIcon(button.getDisabledIcon());
         button.setDisabledIcon(icon);
      }


      private void setContent(final Component component)
      {
         ((GroupLayout) getLayout()).replace(content, component);
         content = component;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static final private class JRE7TranslucentPopupsFactoryHack extends PopupFactory
   {
      @Override
      final public Popup getPopup(final Component owner, final Component contents, final int x, final int y) throws IllegalArgumentException
      {
         DelegateFrame.setJRE7TranslucentPopupsHackEnabled(true);

         final Popup popup = super.getPopup(owner, contents, x, y);

         DelegateFrame.setJRE7TranslucentPopupsHackEnabled(false);

         return popup;
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   private int getAdjustedIncomingDimension(final int dimension)
   {
      return (dimension + frameShadowLength);
   }


   private int getAdjustedOutgoingDimension(final int dimension)
   {
      return (dimension - frameShadowLength);
   }


   private Dimension getAdjustedIncomingSize(final Dimension size)
   {
      return new Dimension(size.width + frameShadowLength, size.height + frameShadowLength);
   }


   private Rectangle getAdjustedIncomingBounds(final Rectangle bounds)
   {
      return new Rectangle(bounds.x, bounds.y, bounds.width + frameShadowLength, bounds.height + frameShadowLength);
   }


   private void adjustForOutgoingFrameBounds(final Rectangle bounds)
   {
      bounds.width -= frameShadowLength;
      bounds.height -= frameShadowLength;
   }


   private Rectangle getDecoratedBounds()
   {
      return delegateFrame.getBounds();
   }


   private void setDecoratedBounds(final Rectangle bounds)
   {
      delegateFrame.setBounds(bounds);
   }


   private Rectangle getMaximumWindowBounds()
   {
      final Rectangle screenBounds = delegateFrame.getGraphicsConfiguration().getBounds();
      final Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(delegateFrame.getGraphicsConfiguration());

      // Remember, screen bounds may have negative origins in multi-monitor configurations.
      screenBounds.x += screenInsets.left;
      screenBounds.y += screenInsets.top;
      screenBounds.width -= (screenInsets.left + screenInsets.right);
      screenBounds.height -= (screenInsets.top + screenInsets.bottom);

      return screenBounds;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void setLocation(final int x, final int y)
   {
      delegateFrame.setLocation(x, y);
   }


   private void repaint()
   {
      if (shadowPanel != null)
         shadowPanel.repaint();
      else
         borderPanel.repaint();
   }


   private void handleWindowCloseButtonPressed()
   {
      if (delegateFrame.getDefaultCloseOperation() == WindowConstants.DISPOSE_ON_CLOSE)
         dispose();
      else if (delegateFrame.getDefaultCloseOperation() == WindowConstants.HIDE_ON_CLOSE)
      {
         delegateFrame.setVisible(false);

         // We need to synthesise the window closing event ourselves, to emulate the usual behaviour.
         // In Swing this event is fired when a close button is pressed on a HIDE_ON_CLOSE frame.
         fireWindowClosingEvent();
      }
      else if (delegateFrame.getDefaultCloseOperation() == WindowConstants.DO_NOTHING_ON_CLOSE)
      {
         // We need to synthesise the window closing event ourselves, to emulate the usual behaviour.
         // In Swing this event is fired when a close button is pressed on a DO_NOTHING_ON_CLOSE frame.
         fireWindowClosingEvent();
      }
   }


   private void fireWindowClosingEvent()
   {
      for (final WindowListener windowListener : delegateFrame.getWindowListeners())
         windowListener.windowClosing(new WindowEvent(delegateFrame, WindowEvent.WINDOW_CLOSING));
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleSetFrameIcon(final BufferedImage frameIcon, final boolean showInFrameTitleBar)
   {
      delegateFrame.setIconImage(frameIcon);

      if (showInFrameTitleBar)
         borderPanel.setFrameIcon(frameIcon);
   }


   private void handleSetFrameTitle(final String frameTitle, final boolean showInFrameTitleBar)
   {
      // Useful for when users have configured their OS to display both taskbar icon and title info. Otherwise it looks a bit weird without it.
      delegateFrame.setTitle(frameTitle);

      if (showInFrameTitleBar)
         borderPanel.setFrameTitle(frameTitle);
   }


   private void handleSetMenuComponents(final JComponent[] leftMenuInvocationComponents, final JComponent[] leftMenuComponents,
                                        final JComponent[] rightMenuInvocationComponents, final JComponent[] rightMenuComponents)
   {
      if ((leftMenuInvocationComponents.length != leftMenuComponents.length) ||
          (rightMenuInvocationComponents.length != rightMenuComponents.length))
         throw new IllegalArgumentException("Mismatched menu component argument lengths.");

      /* All of the menu components are passed to the border panel, but only the invocation components are added to it.
       * The child menu components are needed however to calculate spacing between the menu invocation components on the title bar.
       */
      borderPanel.setMenuInvocationComponents(leftMenuInvocationComponents, leftMenuComponents, rightMenuInvocationComponents, rightMenuComponents);

      /* Add the child menu components to the JFrame's LayeredPane at a high-ish priority, so that the menu will always be painted on top
       * of items such as pop-ups and tooltips. See the note at the top of this class.
       * Because the child menu components are added in this way, and the default LayeredPane doesn't make use of a layout manager (nor should it be forced to,
       * at least not when other content will be added to it outside of this class), the sizing and placement of the menu components needs to be handled
       * manually, without the help of a GroupLayout or other manager. It's possible to place them within a non-opaque parent panel and then add -that- directly to
       * the JFrame's LayeredPane, however there is the drawback that the parent panel (although translucent) will cover everything and interfere with the cursor
       * states of panels painted below it, eg. text fields. Therefore the best approach is to size and place the child menu components individually - the onus is
       * on them to ensure that they are hidden when not active so that they can't interfere with cursor state.
       *
       * Note that the spacing of the child menu components needs to be handled carefully so that they line up at the left with their corresponding
       * invocation components on the frame title bar.
       */
      final Integer layeredPanePriority = new Integer(350);

      if (leftMenuComponents.length > 0)
      {
         int menuLocation = NimbusBorderPanel.DefaultMarginSize + UIConstants.ComponentShadowLength;

         for (final JComponent menuComponent : leftMenuComponents)
         {
            delegateFrame.getLayeredPane().add(menuComponent, layeredPanePriority);

            menuComponent.setSize(menuComponent.getPreferredSize());
            menuComponent.setLocation(menuLocation, borderPanel.frameTitleBarHeight);
            menuLocation += (menuComponent.getWidth() + NimbusBorderPanel.InterMenuGap);
         }
      }

      // Remove any previous resize listener, if present.
      if (rightMenuComponentsResizeListener != null)
      {
         borderPanel.removeComponentListener(rightMenuComponentsResizeListener);
         rightMenuComponentsResizeListener = null;
      }

      if (rightMenuComponents.length > 0)
      {
         for (final JComponent menuComponent : rightMenuComponents)
         {
            delegateFrame.getLayeredPane().add(menuComponent, layeredPanePriority);

            menuComponent.setSize(menuComponent.getPreferredSize());
         }

         rightMenuComponentsResizeListener = new ComponentAdapter()
         {
            @Override
            final public void componentResized(final ComponentEvent componentEvent)
            {
               repositionRightMenuComponents(rightMenuComponents);
            }
         };

         borderPanel.addComponentListener(rightMenuComponentsResizeListener);
      }
   }


   private void repositionRightMenuComponents(final JComponent[] rightMenuComponents)
   {
      /* The border panel has been resized and the rightmost menu components need to be repositioned horizontally.
       * Since these menu components have been added directly to the JFrame's layered pane at a custom level,
       * the repositioning needs to be handled manually. Starting from the rightmost menu component, the
       * initial horizontal position will be the width of the border panel - the border panel's margin size - the menu component's width.
       * This logic assumes that there is no shadowing on the left side of the border panel, which would also need to be taken into account.
       */
      int componentIndex = rightMenuComponents.length - 1;
      int menuLocation = borderPanel.getWidth() - NimbusBorderPanel.DefaultMarginSize;

      do
      {
         menuLocation -= rightMenuComponents[componentIndex].getWidth();
         rightMenuComponents[componentIndex].setLocation(menuLocation, borderPanel.frameTitleBarHeight);

         // Add some spacing between the child menu components so that they line up with their invocation components on the title bar.
         menuLocation -= NimbusBorderPanel.InterMenuGap;

         componentIndex --;
      }
      while (componentIndex >= 0);
   }


   private void handleSetSize(final int newWidth, final int newHeight)
   {
      if (isMaximised())
         throw new IllegalStateException("Cannot resize maximised frame.");

      delegateFrame.setSize(getAdjustedIncomingDimension(newWidth), getAdjustedIncomingDimension(newHeight));
   }


   private Rectangle handleGetBounds()
   {
      final Rectangle frameBounds = delegateFrame.getBounds();
      adjustForOutgoingFrameBounds(frameBounds);
      return frameBounds;
   }


   private Rectangle handleGetRestoredBounds()
   {
      final Rectangle frameBounds = delegateFrame.getRestoredBounds();
      adjustForOutgoingFrameBounds(frameBounds);
      return frameBounds;
   }


   // The frame's minimum size is ignored, ie. too bad if the minimum size is actually set larger than the maximum window size.
   private void handleSetMaximised(final boolean setMaximised)
   {
      if (setMaximised == delegateFrame.isMaximised())
         return;

      if (setMaximised)
      {
         final Rectangle maximumWindowBounds = getAdjustedIncomingBounds(getMaximumWindowBounds());

         // HACK: Ensure a one pixel gap to allow the bottom taskbar to appear on Windows (on the most common config, at least).
         maximumWindowBounds.height --;

         delegateFrame.setMaximisedAt(maximumWindowBounds);
         borderPanel.setMaximised(true);
      }
      else
      {
         delegateFrame.setRestored();
         borderPanel.setMaximised(false);
      }
   }


   private void handleToggleMaximised()
   {
      handleSetMaximised(! delegateFrame.isMaximised());
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public JFrame getDelegate()
   {
      return delegateFrame;
   }


   final public GraphicsConfiguration getGraphicsConfiguration()
   {
      return delegateFrame.getGraphicsConfiguration();
   }


   final public void setFrameIcon(final BufferedImage frameIcon, final boolean showInFrameTitleBar)
   {
      handleSetFrameIcon(frameIcon, showInFrameTitleBar);
   }


   final public void setFrameTitle(final String frameTitle, final boolean showInFrameTitleBar)
   {
      handleSetFrameTitle(frameTitle, showInFrameTitleBar);
   }


   final public void setWindowDecorationStyle(final int windowDecorationStyle)
   {
      borderPanel.setWindowDecorationStyle(windowDecorationStyle);
   }


   final public void setMenuComponents(final JComponent[] leftMenuInvocationComponents, final JComponent[] leftMenuComponents,
                                       final JComponent[] rightMenuInvocationComponents, final JComponent[] rightInvocationComponents)
   {
      handleSetMenuComponents(leftMenuInvocationComponents, leftMenuComponents, rightMenuInvocationComponents, rightInvocationComponents);
   }


   final public Component getContent()
   {
      return borderPanel.content;
   }


   final public void setContent(final Component component)
   {
      borderPanel.setContent(component);
   }


   final public void setDefaultCloseOperation(final int defaultCloseOperation)
   {
      if (defaultCloseOperation == WindowConstants.EXIT_ON_CLOSE)
         throw new IllegalArgumentException("Default close operation EXIT_ON_CLOSE is not supported.");

      delegateFrame.setDefaultCloseOperation(defaultCloseOperation);
   }


   final public boolean isResizable()
   {
      return delegateFrame.isResizable();
   }


   final public void setResizable(final boolean isResizable)
   {
      delegateFrame.setResizable(isResizable);
      borderPanel.setResizable(isResizable);
   }


   final public void setRestoredSize(final Dimension size)
   {
      delegateFrame.setRestoredSize(getAdjustedIncomingSize(size));
   }


   final public Dimension getMinimumSize()
   {
      return delegateFrame.getMinimumSize();
   }


   final public void setMinimumSize(final Dimension minimumSize)
   {
      delegateFrame.setMinimumSize(getAdjustedIncomingSize(minimumSize));
   }


   final public int getWidth()
   {
      /* The width of the frame as the caller sees it is the width of the frame without the shadow (if any), ie. the width of the border panel.
       * So, I should be able to just return borderPanel.getWidth(). However for some reason this call results in a huge performance penalty. When used in
       * conjunction with the frame resize animator, it slows the resizes to a crawl. It turns out that it's a lot more efficient to derive the frame width
       * manually rather than make the direct call to the component itself.
       */
      return getAdjustedOutgoingDimension(delegateFrame.getWidth());
//      return borderPanel.getWidth();
   }


   final public int getHeight()
   {
      // See the note on getWidth().
      return getAdjustedOutgoingDimension(delegateFrame.getHeight());
//      return borderPanel.getHeight();
   }


   final public Dimension getSize()
   {
      return new Dimension(getWidth(), getHeight());
   }


   final public void setSize(final int width, final int height)
   {
      handleSetSize(width, height);
   }


   final public Rectangle getBounds()
   {
      // Unlike getSize() this can't of course return the bounds of the inner border panel, since its location will be relative to the container rather than the screen.
      return handleGetBounds();
   }


   final public Rectangle getRestoredBounds()
   {
      return handleGetRestoredBounds();
   }


   final public void setRestoredBounds(final Rectangle bounds)
   {
      delegateFrame.setRestoredBounds(getAdjustedIncomingBounds(bounds));
   }


   final public boolean isMaximised()
   {
      return delegateFrame.isMaximised();
   }


   final public void setMaximised(final boolean setMaximised)
   {
      handleSetMaximised(setMaximised);
   }


   final public Point getLocation()
   {
      return delegateFrame.getLocation();
   }


   final public void setRestoredLocation(final int x, final int y)
   {
      delegateFrame.setRestoredLocation(x, y);
   }


   private void setIconified()
   {
      delegateFrame.setState(Frame.ICONIFIED);
   }


   final public Cursor getCursor()
   {
      return delegateFrame.getCursor();
   }


   final public void setCursor(final Cursor cursor)
   {
      delegateFrame.setCursor(cursor);
   }


   final public void setWindowOpacity(final float windowOpacity) throws IllegalAccessException, InvocationTargetException
   {
      TranslucencyUtilities.setWindowOpacity(delegateFrame, windowOpacity);
   }


   final public void pack()
   {
      delegateFrame.pack();
   }


   final public boolean isDisplayable()
   {
      return delegateFrame.isDisplayable();
   }


   final public int getExtendedState()
   {
      return delegateFrame.getExtendedState();
   }


   final public void setExtendedState(final int extendedState)
   {
      delegateFrame.setExtendedState(extendedState);
   }


   final public boolean isVisible()
   {
      return delegateFrame.isVisible();
   }


   final public void setVisible(final boolean visible)
   {
      delegateFrame.setVisible(visible);
   }


   final public boolean isActive()
   {
      return delegateFrame.isActive();
   }


   final public void toBack()
   {
      delegateFrame.toBack();
   }


   final public void toFront()
   {
      delegateFrame.toFront();
   }


   final public void setAlwaysOnTop(final boolean isAlwaysOnTop)
   {
      delegateFrame.setAlwaysOnTop(isAlwaysOnTop);
   }


   final public void addHierarchyListener(final HierarchyListener hierarchyListener)
   {
      delegateFrame.addHierarchyListener(hierarchyListener);
   }


   final public void removeHierarchyListener(final HierarchyListener hierarchyListener)
   {
      delegateFrame.removeHierarchyListener(hierarchyListener);
   }


   final public void addComponentListener(final ComponentListener componentListener)
   {
      delegateFrame.addComponentListener(componentListener);
   }


   final public void removeComponentListener(final ComponentListener componentListener)
   {
      delegateFrame.removeComponentListener(componentListener);
   }


   final public void addWindowListener(final WindowListener windowListener)
   {
      delegateFrame.addWindowListener(windowListener);
   }


   final public void removeWindowListener(final WindowListener windowListener)
   {
      delegateFrame.removeWindowListener(windowListener);
   }


   final public void addWindowFocusListener(final WindowFocusListener windowFocusListener)
   {
      delegateFrame.addWindowFocusListener(windowFocusListener);
   }


   final public void removeWindowFocusListener(final WindowFocusListener windowFocusListener)
   {
      delegateFrame.removeWindowFocusListener(windowFocusListener);
   }


   final public void addMouseListener(final MouseListener mouseListener)
   {
      borderPanel.addMouseListener(mouseListener);
   }


   final public void removeMouseListener(final MouseListener mouseListener)
   {
      borderPanel.removeMouseListener(mouseListener);
   }


   final public void dispose()
   {
      delegateFrame.dispose();
   }
}