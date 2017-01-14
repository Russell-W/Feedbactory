/* Preconditions:
 * - Nimbus L&F must be active at the creation of the first instance of the frame.
 *
 * Issues:
 * - Minimise doesn't work on Mac OS X.
 * 
 * Enhancements:
 * - Allow non-resizable.
 * - Customisation of frame control buttons, ie. dialog only, disable resize, no buttons...
 */

package com.feedbactory.client.ui.component;


import com.feedbactory.client.ui.UIUtilities;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;


final public class SWTNimbusFrame extends SWTFrame
{
   static final private int FrameTitleBarHeight = 25;
   static final private int FrameDefaultMarginSize = 6;

   static final private int ResizeBorderThresholdSize = FrameDefaultMarginSize + 2;

   static final private RGB ActiveFrameOuterBorderColor1 = new RGB(43, 46, 51);
   static final private RGB ActiveFrameOuterBorderColor2 = new RGB(204, 207, 213);
   static final private RGB ActiveFrameOuterBorderColor3 = new RGB(171, 176, 186);
   static final private RGB ActiveFrameOuterBorderColor4 = new RGB(149, 158, 167);
   static final private RGB ActiveFrameOuterBorderColor5 = new RGB(108, 114, 128);

   static final private RGB ActiveFrameTitleBarStartGradientColor = new RGB(227, 229, 232);
   static final private RGB ActiveFrameTitleBarEndGradientColor = ActiveFrameOuterBorderColor3;

   static final private RGB InactiveFrameOuterBorderColor1 = new RGB(136, 141, 151);
   static final private RGB InactiveFrameOuterBorderColor2 = new RGB(211, 214, 219);
   static final private RGB InactiveFrameOuterBorderColor3 = new RGB(192, 196, 203);
   static final private RGB InactiveFrameOuterBorderColor4 = new RGB(203, 207, 213);
   static final private RGB InactiveFrameOuterBorderColor5 = new RGB(158, 163, 173);

   static final private RGB InactiveFrameTitleBarStartGradientColor = new RGB(170, 174, 183);
   static final private RGB InactiveFrameTitleBarEndGradientColor = InactiveFrameOuterBorderColor3;

   static final private Point FrameControlButtonDimensions = new Point(22, 18);
   static final private int FrameControlButtonTopClearance = 4;
   static final private int FrameControlButtonSpacing = 3;

   /* The Nimbus button images within each array are initialised via the Swing thread after the class has first loaded, but accessed via the
    * SWT thread as each instance is initialised. Hence the need for some visibility between threads.
    * The AreControlButtonsInitialised variable is only accessed via the Swing thread.
    */
   static private boolean AreControlButtonsInitialised;
   static final private AtomicReferenceArray<ImageData> MinimiseButtonImages = new AtomicReferenceArray<ImageData>(ControlButtonImageDataIndex.values().length);
   static final private AtomicReferenceArray<ImageData> MaximiseButtonImages = new AtomicReferenceArray<ImageData>(ControlButtonImageDataIndex.values().length);
   static final private AtomicReferenceArray<ImageData> RestoreButtonImages = new AtomicReferenceArray<ImageData>(ControlButtonImageDataIndex.values().length);
   static final private AtomicReferenceArray<ImageData> CloseButtonImages = new AtomicReferenceArray<ImageData>(ControlButtonImageDataIndex.values().length);

   static final private Point FrameIconSize = new Point(16, 16);
   static final private Point FrameIconDrawOffset = new Point(FrameDefaultMarginSize + 4, 5);

   static final private Point FrameTitleDrawOffset = new Point(FrameIconDrawOffset.x + FrameIconSize.x + 8, 5);

   static final private int[] DirectionalCursors;

   static
   {
      DirectionalCursors = new int[13];
      DirectionalCursors[CursorDirection.None.flag] = SWT.CURSOR_ARROW;
      DirectionalCursors[CursorDirection.North.flag] = SWT.CURSOR_SIZEN;
      DirectionalCursors[CursorDirection.North.flag | CursorDirection.East.flag] = SWT.CURSOR_SIZENE;
      DirectionalCursors[CursorDirection.East.flag] = SWT.CURSOR_SIZEE;
      DirectionalCursors[CursorDirection.South.flag | CursorDirection.East.flag] = SWT.CURSOR_SIZESE;
      DirectionalCursors[CursorDirection.South.flag] = SWT.CURSOR_SIZES;
      DirectionalCursors[CursorDirection.South.flag | CursorDirection.West.flag] = SWT.CURSOR_SIZESW;
      DirectionalCursors[CursorDirection.West.flag] = SWT.CURSOR_SIZEW;
      DirectionalCursors[CursorDirection.North.flag | CursorDirection.West.flag] = SWT.CURSOR_SIZENW;
   };

   private ShellAdapter frameShellActivationListener;

   private Region frameShellRegion;

   private SWTImageButton minimiseButton;
   private SWTImageButton maximiseButton;
   private SWTImageButton restoreButton;
   private SWTImageButton closeButton;
   final private List<Region> controlButtonRegions = new ArrayList<Region>(4);

   final private Composite frameRootComponent;

   private byte resizeDirection;
   private boolean isResizing;
   private Point lastDragPoint;

   private boolean isMaximised;
   private Rectangle restoredFrameBounds = new Rectangle(0, 0, 0, 0);

   private boolean isShellActive;


   public SWTNimbusFrame(final Display display)
   {
      super(display, SWT.NO_TRIM);

      frameRootComponent = new Composite(frameShell, SWT.DOUBLE_BUFFERED);

      initialise();
   }


   private void initialise()
   {
      initialiseFrameShell();
      initialiseFrameRootComponent();
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


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


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


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


   /****************************************************************************
    *
    * Begin Swing thread initialisation section.
    *
    ***************************************************************************/


   static public void preInitialiseNimbus()
   {
      assert SwingUtilities.isEventDispatchThread();

      if (! AreControlButtonsInitialised)
      {
         initialiseFrameShellTitleBarControlButtonImages();
         AreControlButtonsInitialised = true;
      }
   }


   static private void initialiseFrameShellTitleBarControlButtonImages()
   {
      try
      {
         initialiseFrameShellTitleBarControlButtonImagesEDT();
      }
      catch (final Exception exception)
      {
         throw new RuntimeException(exception);
      }
   }


   static private void initialiseFrameShellTitleBarControlButtonImagesEDT() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException
   {
      // Nimbus LAF changes packages between JDK6 and JDK7, hence we can't rely on using an instanceof NimbusLookAndFeel test.
      if (! UIManager.getLookAndFeel().getName().equals("Nimbus"))
         throw new IllegalStateException("Nimbus LAF must be set before creation of the first NimbusFrame.");

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
   }


   static private void initialiseFrameShellTitleBarControlButtonImage(final AtomicReferenceArray<ImageData> imageButtons, final String enabledPainterKey, final String mouseOverPainterKey,
                                                                      final String pressedPainterKey, final String inactivePainterKey) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException
   {
      imageButtons.set(ControlButtonImageDataIndex.Enabled.imageIndex, createButtonImageFromNimbusPainter(enabledPainterKey));
      imageButtons.set(ControlButtonImageDataIndex.MouseOver.imageIndex, createButtonImageFromNimbusPainter(mouseOverPainterKey));
      imageButtons.set(ControlButtonImageDataIndex.Pressed.imageIndex, createButtonImageFromNimbusPainter(pressedPainterKey));
      imageButtons.set(ControlButtonImageDataIndex.Inactive.imageIndex, createButtonImageFromNimbusPainter(inactivePainterKey));
   }


   static private ImageData createButtonImageFromNimbusPainter(final String painterKey) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException
   {
      final Object painter = UIManager.getLookAndFeelDefaults().get(painterKey);
      final Class<?> painterClass = painter.getClass();
      final Method painterMethod = painterClass.getMethod("paint", Graphics2D.class, JComponent.class, Integer.TYPE, Integer.TYPE);

      final BufferedImage swingButtonImage = UIUtilities.createCompatibleImage(FrameControlButtonDimensions.x, FrameControlButtonDimensions.y, Transparency.TRANSLUCENT);
      final Graphics2D graphics2D = swingButtonImage.createGraphics();
      painterMethod.invoke(painter, graphics2D, null, FrameControlButtonDimensions.x, FrameControlButtonDimensions.y);
      graphics2D.dispose();

      return UIUtilities.swingImageToSWTImageData(swingButtonImage);
   }


   /****************************************************************************
    * End Swing thread initialisation section.
    ***************************************************************************/


   private void initialiseFrameShell()
   {
      frameShell.setLayout(new FormLayout());

      initialiseFrameShellControlButtons();
      initialiseFrameShellPaintListener();
      initialiseFrameShellActivationListener();
      initialiseFrameShellResizeAndMoveMouseListeners();
   }


   private void initialiseFrameShellControlButtons()
   {
      minimiseButton = initialiseFrameShellTitleBarControlButton(MinimiseButtonImages);
      maximiseButton = initialiseFrameShellTitleBarControlButton(MaximiseButtonImages);
      restoreButton = initialiseFrameShellTitleBarControlButton(RestoreButtonImages);
      closeButton = initialiseFrameShellTitleBarControlButton(CloseButtonImages);

      restoreButton.setVisible(false);

      FormData layoutFormData = new FormData(FrameControlButtonDimensions.x, FrameControlButtonDimensions.y);
      layoutFormData.right = new FormAttachment(100, -FrameDefaultMarginSize);
      layoutFormData.top = new FormAttachment(0, FrameControlButtonTopClearance);
      closeButton.setLayoutData(layoutFormData);

      layoutFormData = new FormData(FrameControlButtonDimensions.x, FrameControlButtonDimensions.y);
      layoutFormData.right = new FormAttachment(closeButton, -FrameControlButtonSpacing);
      layoutFormData.top = new FormAttachment(0, FrameControlButtonTopClearance);
      restoreButton.setLayoutData(layoutFormData);
      maximiseButton.setLayoutData(layoutFormData);

      layoutFormData = new FormData(FrameControlButtonDimensions.x, FrameControlButtonDimensions.y);
      layoutFormData.right = new FormAttachment(restoreButton, -FrameControlButtonSpacing);
      layoutFormData.top = new FormAttachment(0, FrameControlButtonTopClearance);
      minimiseButton.setLayoutData(layoutFormData);

      initialiseFrameShellTitleBarControlButtonRoundedCorners(minimiseButton);
      initialiseFrameShellTitleBarControlButtonRoundedCorners(maximiseButton);
      initialiseFrameShellTitleBarControlButtonRoundedCorners(restoreButton);
      initialiseFrameShellTitleBarControlButtonRoundedCorners(closeButton);

      minimiseButton.addListener(SWT.Selection, new Listener()
      {
         @Override
         final public void handleEvent(final Event event)
         {
            // Doesn't appear to work on Mac OS X.
            frameShell.setMinimized(true);
         }
      });

      maximiseButton.addListener(SWT.Selection, new Listener()
      {
         @Override
         final public void handleEvent(final Event event)
         {
            toggleMaximised();
         }
      });

      restoreButton.addListener(SWT.Selection, new Listener()
      {
         @Override
         final public void handleEvent(final Event event)
         {
            toggleMaximised();
         }
      });

      closeButton.addListener(SWT.Selection, new Listener()
      {
         @Override
         final public void handleEvent(final Event event)
         {
            frameShell.close();
         }
      });
   }


   private void initialiseFrameShellTitleBarControlButtonRoundedCorners(final SWTImageButton imageButton)
   {
      final Region region = new Region(frameShell.getDisplay());
      region.add(new Rectangle(0, 0, FrameControlButtonDimensions.x, FrameControlButtonDimensions.y));
      region.subtract(0, FrameControlButtonDimensions.y - 1, FrameControlButtonDimensions.x, FrameControlButtonDimensions.y);
      region.subtract(new int[] {0, 0, 2, 0, 0, 2});
      region.subtract(new int[] {FrameControlButtonDimensions.x, 0, FrameControlButtonDimensions.x, 2, FrameControlButtonDimensions.x - 2, 0});
      region.subtract(new int[] {0, FrameControlButtonDimensions.y, 3, FrameControlButtonDimensions.y, 0, FrameControlButtonDimensions.y - 3});
      region.subtract(new int[] {FrameControlButtonDimensions.x, FrameControlButtonDimensions.y, FrameControlButtonDimensions.x, FrameControlButtonDimensions.y - 3, FrameControlButtonDimensions.x - 3, FrameControlButtonDimensions.y});

      imageButton.setRegion(region);

      controlButtonRegions.add(region);
   }


   private SWTImageButton initialiseFrameShellTitleBarControlButton(final AtomicReferenceArray<ImageData> buttonImages)
   {
      final SWTImageButton imageButton = new SWTImageButton(frameShell);

      imageButton.setEnabledImage(buttonImages.get(ControlButtonImageDataIndex.Enabled.imageIndex));
      imageButton.setMouseOverImage(buttonImages.get(ControlButtonImageDataIndex.MouseOver.imageIndex));
      imageButton.setPressedImage(buttonImages.get(ControlButtonImageDataIndex.Pressed.imageIndex));
      imageButton.setUnfocusedImage(buttonImages.get(ControlButtonImageDataIndex.Inactive.imageIndex));

      return imageButton;
   }


   private void initialiseFrameShellPaintListener()
   {
      frameShell.addPaintListener(new PaintListener()
      {
         @Override
         final public void paintControl(final PaintEvent paintEvent)
         {
            handlePaintComponent(paintEvent);
         }
      });
   }


   private void initialiseFrameShellActivationListener()
   {
      frameShellActivationListener = new ShellAdapter()
      {
         @Override
         final public void shellActivated(final ShellEvent shellEvent)
         {
            handleShellActivated();
         }


         @Override
         final public void shellDeactivated(final ShellEvent shellEvent)
         {
            handleShellDeactivated();
         }
      };

      frameShell.addShellListener(frameShellActivationListener);
   }


   private void initialiseFrameShellResizeAndMoveMouseListeners()
   {
      frameShell.addMouseMoveListener(new MouseMoveListener()
      {
         @Override
         final public void mouseMove(final MouseEvent mouseEvent)
         {
            handleMouseMotionEvent(mouseEvent);
         }
      });

      frameShell.addMouseListener(new MouseListener()
      {
         @Override
         final public void mouseDoubleClick(final MouseEvent mouseEvent)
         {
            handleMouseDoubleClick(mouseEvent);
         }


         @Override
         final public void mouseDown(final MouseEvent mouseEvent)
         {
            handleMouseDown(mouseEvent);
         }


         @Override
         final public void mouseUp(final MouseEvent mouseEvent)
         {
            handleMouseUp(mouseEvent);
         }
      });

      frameShell.addMouseTrackListener(new MouseTrackAdapter()
      {
         @Override
         final public void mouseExit(final MouseEvent mouseEvent)
         {
            handleMouseExit();
         }
      });
   }


   private void initialiseFrameRootComponent()
   {
      final FormData layoutFormData = new FormData(SWT.DEFAULT, SWT.DEFAULT);
      layoutFormData.left = new FormAttachment(0, FrameDefaultMarginSize);
      layoutFormData.right = new FormAttachment(100, -FrameDefaultMarginSize);
      layoutFormData.top = new FormAttachment(0, FrameTitleBarHeight);
      layoutFormData.bottom = new FormAttachment(100, -FrameDefaultMarginSize);
      frameRootComponent.setLayoutData(layoutFormData);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleShellActivated()
   {
      isShellActive = true;
      handleUpdateTitleBarButtons(true);
      frameShell.redraw();
   }


   private void handleShellDeactivated()
   {
      isShellActive = false;
      handleUpdateTitleBarButtons(false);
      frameShell.redraw();
   }


   private void handleUpdateTitleBarButtons(final boolean isWindowFocused)
   {
      minimiseButton.setForegroundPainted(isWindowFocused);
      maximiseButton.setForegroundPainted(isWindowFocused);
      restoreButton.setForegroundPainted(isWindowFocused);
      closeButton.setForegroundPainted(isWindowFocused);

      minimiseButton.redraw();
      maximiseButton.redraw();
      restoreButton.redraw();
      closeButton.redraw();
   }


   private void handlePaintComponent(final PaintEvent paintEvent)
   {
      final Display display = frameShell.getDisplay();

      final GC graphics = paintEvent.gc;
      graphics.setAntialias(SWT.ON);

      final Color borderColor1;
      final Color borderColor2;
      final Color borderColor3;
      final Color borderColor4;
      final Color borderColor5;
      final Color startGradientColor;
      final Color endGradientColor;

      final Point shellDimensions = frameShell.getSize();

      if (isShellActive)
      {
         borderColor1 = new Color(display, ActiveFrameOuterBorderColor1);
         borderColor2 = new Color(display, ActiveFrameOuterBorderColor2);
         borderColor3 = new Color(display, ActiveFrameOuterBorderColor3);
         borderColor4 = new Color(display, ActiveFrameOuterBorderColor4);
         borderColor5 = new Color(display, ActiveFrameOuterBorderColor5);
         startGradientColor = new Color(display, ActiveFrameTitleBarStartGradientColor);
         endGradientColor = new Color(display, ActiveFrameTitleBarEndGradientColor);
      }
      else
      {
         borderColor1 = new Color(display, InactiveFrameOuterBorderColor1);
         borderColor2 = new Color(display, InactiveFrameOuterBorderColor2);
         borderColor3 = new Color(display, InactiveFrameOuterBorderColor3);
         borderColor4 = new Color(display, InactiveFrameOuterBorderColor4);
         borderColor5 = new Color(display, InactiveFrameOuterBorderColor5);
         startGradientColor = new Color(display, InactiveFrameTitleBarStartGradientColor);
         endGradientColor = new Color(display, InactiveFrameTitleBarEndGradientColor);
      }

      graphics.setForeground(borderColor1);
      graphics.drawRoundRectangle(0, 0, shellDimensions.x - 1, shellDimensions.y - 1, 11, 11);
      graphics.setForeground(borderColor2);
      graphics.drawRoundRectangle(1, 1, shellDimensions.x - 3, shellDimensions.y - 3, 10, 10);
      graphics.setForeground(borderColor3);
      graphics.drawRectangle(2, FrameTitleBarHeight - 4, shellDimensions.x - 5, shellDimensions.y - FrameTitleBarHeight + 1);
      graphics.drawRectangle(3, FrameTitleBarHeight - 3, shellDimensions.x - 7, shellDimensions.y - FrameTitleBarHeight - 1);
      graphics.setForeground(borderColor4);
      graphics.drawRectangle(4, FrameTitleBarHeight - 2, shellDimensions.x - 9, shellDimensions.y - 3 - FrameTitleBarHeight);
      graphics.setForeground(borderColor5);
      graphics.drawRectangle(5, FrameTitleBarHeight - 1, shellDimensions.x - 11, shellDimensions.y - 5 - FrameTitleBarHeight);
      graphics.setForeground(startGradientColor);
      graphics.setBackground(endGradientColor);
      graphics.fillGradientRectangle(2, 2, shellDimensions.x - 4, FrameTitleBarHeight - 6, true);

      borderColor1.dispose();
      borderColor2.dispose();
      borderColor3.dispose();
      borderColor4.dispose();
      borderColor5.dispose();
      startGradientColor.dispose();
      endGradientColor.dispose();

      if (titleBarIconImage != null)
      {
         graphics.setInterpolation(SWT.HIGH);
         final Rectangle imageBounds = titleBarIconImage.getBounds();
         graphics.drawImage(titleBarIconImage, 0, 0, imageBounds.width, imageBounds.height, FrameIconDrawOffset.x, FrameIconDrawOffset.y, FrameIconSize.x, FrameIconSize.y);
      }

      if (frameShell.getText() != null)
      {
         graphics.setTextAntialias(SWT.ON);
         graphics.setForeground(frameShell.getDisplay().getSystemColor(SWT.COLOR_BLACK));
         graphics.drawString(frameShell.getText(), FrameTitleDrawOffset.x, FrameTitleDrawOffset.y, true);
      }
   }


   private void handleMouseMotionEvent(final MouseEvent mouseEvent)
   {
      if (! isMaximised)
      {
         if ((mouseEvent.stateMask & SWT.BUTTON_MASK) != 0)
            handleMouseDragged(mouseEvent);
         else
            handleMouseMoved(mouseEvent);
      }
   }


   private void handleMouseDragged(final MouseEvent mouseEvent)
   {
      if ((lastDragPoint != null) && ((mouseEvent.stateMask & SWT.BUTTON1) != 0))
      {
         final Rectangle frameBounds = frameShell.getBounds();
         final Point absoluteMousePosition = new Point(frameBounds.x + mouseEvent.x, frameBounds.y + mouseEvent.y);

         int dragDeltaX = absoluteMousePosition.x - lastDragPoint.x;
         int dragDeltaY = absoluteMousePosition.y - lastDragPoint.y;

         lastDragPoint = absoluteMousePosition;

         if (resizeDirection == CursorDirection.None.flag)
         {
            int yLocation = frameBounds.y + dragDeltaY;
            if (dragDeltaY < 0)
            {
               // Ensure that the top of the frame can't move beyond the screen bounds.
               final Rectangle screenBounds = frameShell.getDisplay().getClientArea();
               if (yLocation < screenBounds.y)
                  yLocation = screenBounds.y;
            }

            frameShell.setLocation(frameBounds.x + dragDeltaX, yLocation);
         }
         else
         {
            final Point frameMinimumSize = frameShell.getMinimumSize();

            if ((resizeDirection & CursorDirection.West.flag) != 0)
            {
               if (dragDeltaX > 0)
               {
                  final int maximumDeltaX = frameBounds.width - frameMinimumSize.x;
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
                  final int minimumDragDeltaX = frameMinimumSize.x - frameBounds.width;
                  if (dragDeltaX < minimumDragDeltaX)
                     dragDeltaX = minimumDragDeltaX;
               }

               frameBounds.width += dragDeltaX;
            }

            if ((resizeDirection & CursorDirection.North.flag) != 0)
            {
               if (dragDeltaY > 0)
               {
                  final int maximumDeltaY = frameBounds.height - frameMinimumSize.y;
                  if (dragDeltaY > maximumDeltaY)
                     dragDeltaY = maximumDeltaY;
               }
               else if (dragDeltaY < 0)
               {
                  // Ensure that the top of the frame can't be dragged beyond the screen bounds.
                  final Rectangle screenBounds = frameShell.getDisplay().getClientArea();
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
                  final int minimumDragDeltaY = frameMinimumSize.y - frameBounds.height;
                  if (dragDeltaY < minimumDragDeltaY)
                     dragDeltaY = minimumDragDeltaY;
               }

               frameBounds.height += dragDeltaY;
            }

            setBounds(frameBounds);
         }
      }
   }


   private void handleMouseMoved(final MouseEvent mouseEvent)
   {
      final int previousResizeDirection = resizeDirection;

      final Point frameSize = frameShell.getSize();

      resizeDirection = 0;
      if (mouseEvent.x < ResizeBorderThresholdSize)
         resizeDirection = CursorDirection.West.flag;
      else if (mouseEvent.x > (frameSize.x - ResizeBorderThresholdSize))
         resizeDirection = CursorDirection.East.flag;

      if (mouseEvent.y < ResizeBorderThresholdSize)
         resizeDirection |= CursorDirection.North.flag;
      else if (mouseEvent.y > (frameSize.y - ResizeBorderThresholdSize))
         resizeDirection |= CursorDirection.South.flag;

      if (resizeDirection != previousResizeDirection)
         frameShell.setCursor(frameShell.getDisplay().getSystemCursor(DirectionalCursors[resizeDirection]));
   }


   private void handleMouseDoubleClick(final MouseEvent mouseEvent)
   {
      if (mouseEvent.button == 1)
         toggleMaximised();
   }


   private void handleMouseDown(final MouseEvent mouseEvent)
   {
      if (mouseEvent.button == 1)
      {
         lastDragPoint = frameShell.toDisplay(mouseEvent.x, mouseEvent.y);

         if (resizeDirection != CursorDirection.None.flag)
            isResizing = true;
      }
   }


   private void handleMouseUp(final MouseEvent mouseEvent)
   {
      if ((mouseEvent.button == 1) && isResizing)
      {
         resizeDirection = CursorDirection.None.flag;
         frameShell.setCursor(frameShell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
         isResizing = false;
      }
   }


   private void handleMouseExit()
   {
      if (! isResizing)
      {
         resizeDirection = CursorDirection.None.flag;
         frameShell.setCursor(frameShell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
      }
   }


   private void toggleMaximised()
   {
      handleSetMaximised(! isMaximised);
   }


   private void setBounds(final Rectangle bounds)
   {
      frameShell.setBounds(bounds);
      updateFrameShellRoundedCorners();
   }


   private void updateFrameShellRoundedCorners()
   {
      final Rectangle shellArea = frameShell.getClientArea();

      final Region region = new Region(frameShell.getDisplay());

      region.add(shellArea);
      region.subtract(new int[] {0, 0, 4, 0, 2, 1, 1, 2, 0, 4});
      region.subtract(new int[] {shellArea.width, 0, shellArea.width - 4, 0, shellArea.width - 2, 1, shellArea.width - 1, 3, shellArea.width, 4});
      region.subtract(new int[] {0, shellArea.height, 4, shellArea.height, 4, shellArea.height - 1, 3, shellArea.height - 1, 1, shellArea.height - 3, 1, shellArea.height - 4, 0, shellArea.height - 4});
      region.subtract(new int[] {shellArea.width, shellArea.height, shellArea.width, shellArea.height - 4, shellArea.width - 1, shellArea.height - 4, shellArea.width - 3, shellArea.height - 1, shellArea.width - 4, shellArea.height - 1, shellArea.width - 4, shellArea.height});

      frameShell.setRegion(region);

      if (frameShellRegion != null)
         frameShellRegion.dispose();

      frameShellRegion = region;
   }


   private void setSize(final int width, final int height)
   {
      frameShell.setSize(width, height);
      updateFrameShellRoundedCorners();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleSetRestoredBounds(final Rectangle bounds)
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


   private void handleSetRestoredSize(final int width, final int height)
   {
      if (! isMaximised)
         setSize(width, height);
      else
      {
         restoredFrameBounds.width = width;
         restoredFrameBounds.height = height;
      }
   }


   // The frame's minimum size is ignored, ie. too bad if the minimum size is actually set larger than the maximum window size.
   private void handleSetMaximised(final boolean setMaximised)
   {
      if (this.isMaximised == setMaximised)
         return;

      if (setMaximised)
      {
         restoredFrameBounds = frameShell.getBounds();

         final Rectangle maximumWindowBounds = frameShell.getDisplay().getClientArea();

         // HACK: Make a one pixel gap to allow the bottom taskbar to appear on Windows.
         maximumWindowBounds.height --;

         setBounds(maximumWindowBounds);

         restoreButton.setVisible(true);
         maximiseButton.setVisible(false);

         this.isMaximised = true;
      }
      else
      {
         setBounds(restoredFrameBounds);

         maximiseButton.setVisible(true);
         restoreButton.setVisible(false);

         this.isMaximised = false;
      }
   }


   private void handleSetRestoredLocation(final int x, final int y)
   {
      if (! isMaximised)
         frameShell.setLocation(x, y);
      else
      {
         restoredFrameBounds.x = x;
         restoredFrameBounds.y = y;
      }
   }


   private void handleDispose()
   {
      /* The first item of the shutdown process is to remove the shell listener which exists only to update and redraw the frame margin and control buttons.
       * Testing produced a scenario where this listener is being fired off -during- the call to the frameShell.dispose(); unfortunately the listener then references
       * widges which have been disposed, and the window hangs. So, we need to remove the listener before disposing of the frame.
       * To replicate the scenario, leave the shell listener active here, start the app, open the pad window then click and hold on a dropdown button on the browser window,
       * then drag to the browser close button and release. Boom.
       */
      frameShell.removeShellListener(frameShellActivationListener);

      for (final Region region : controlButtonRegions)
         region.dispose();

      if (frameShellRegion != null)
         frameShellRegion.dispose();

      super.dispose();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public Composite getFrameRootComponent()
   {
      return frameRootComponent;
   }


   @Override
   final public Rectangle getRestoredBounds()
   {
      return new Rectangle(restoredFrameBounds.x, restoredFrameBounds.y, restoredFrameBounds.width, restoredFrameBounds.height);
   }


   @Override
   final public void setRestoredBounds(final Rectangle bounds)
   {
      handleSetRestoredBounds(bounds);
   }


   @Override
   final public void setRestoredSize(final int width, final int height)
   {
      handleSetRestoredSize(width, height);
   }


   @Override
   final public boolean isMaximised()
   {
      return isMaximised;
   }


   @Override
   final public void setMaximised(final boolean setMaximised)
   {
      handleSetMaximised(setMaximised);
   }


   @Override
   final public void setRestoredLocation(final int x, final int y)
   {
      handleSetRestoredLocation(x, y);
   }


   @Override
   final public void dispose()
   {
      handleDispose();
   }
}