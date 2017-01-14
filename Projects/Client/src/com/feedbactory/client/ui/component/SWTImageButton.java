// LIMITATIONS:
//    - No checking of dimensions of button images - if they don't fit, they're cropped. No border padding or centre alignment is provided for smaller images.
//    - Button shape must be rectangular.
//    - Only responds to primary mouse button clicks.
//    - No graceful handling of exceptions on action event.

package com.feedbactory.client.ui.component;


import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;


final public class SWTImageButton extends Canvas
{
   private ImageData enabledImage;
   private ImageData mouseOverImage;
   private ImageData pressedImage;
   private ImageData backgroundImage;

   private boolean isForegroundPainted;
   private boolean isMouseOver;
   private boolean isPressed;


   public SWTImageButton(final Composite parent)
   {
      super(parent, SWT.NONE);

      initialise();
   }


   private void initialise()
   {
      // NOTE: Setting the size manually doesn't appear to work, have to use the layout..?

      addMouseTrackListener(new MouseTrackAdapter()
      {
         @Override
         final public void mouseEnter(final MouseEvent mouseEvent)
         {
            isMouseOver = true;
            redraw();
         }


         @Override
         final public void mouseExit(final MouseEvent mouseEvent)
         {
            isMouseOver = false;
            redraw();
         }
      });

      addMouseListener(new MouseAdapter()
      {
         @Override
         final public void mouseDown(final MouseEvent mouseEvent)
         {
            if (mouseEvent.button == 1)
            {
               isPressed = true;
               redraw();
            }
         }


         @Override
         final public void mouseUp(final MouseEvent mouseEvent)
         {
            if (mouseEvent.button == 1)
            {
               isPressed = false;
               redraw();

               if (getClientArea().contains(mouseEvent.x, mouseEvent.y))
               {
                  final Event selectionEvent = new Event();
                  for (Listener listener : getListeners(SWT.Selection))
                     listener.handleEvent(selectionEvent);
               }
            }
         }
      });

      addPaintListener(new PaintListener()
      {
         @Override
         final public void paintControl(final PaintEvent paintEvent)
         {
            ImageData imageDataToDraw = null;
            if (isPressed && (pressedImage != null))
               imageDataToDraw = pressedImage;
            else if (isMouseOver && (mouseOverImage != null))
               imageDataToDraw = mouseOverImage;
            else if (isForegroundPainted && (enabledImage != null))
               imageDataToDraw = enabledImage;
            else if ((! isForegroundPainted) && (backgroundImage != null))
               imageDataToDraw = backgroundImage;

            if (imageDataToDraw != null)
            {
               final Image imageToDraw = new Image(paintEvent.display, imageDataToDraw);
               paintEvent.gc.drawImage(imageToDraw, 0, 0);
               imageToDraw.dispose();
            }
         }
      });
   }


   final public void setEnabledImage(final ImageData enabledImage)
   {
      this.enabledImage = enabledImage;
   }


   final public void setMouseOverImage(final ImageData mouseOverImage)
   {
      this.mouseOverImage = mouseOverImage;
   }


   final public void setPressedImage(final ImageData pressedImage)
   {
      this.pressedImage = pressedImage;
   }


   final public void setUnfocusedImage(final ImageData unfocusedImage)
   {
      this.backgroundImage = unfocusedImage;
   }


   final public void setForegroundPainted(final boolean isForegroundPainted)
   {
      this.isForegroundPainted = isForegroundPainted;
   }
}