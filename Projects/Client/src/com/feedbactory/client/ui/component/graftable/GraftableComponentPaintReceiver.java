package com.feedbactory.client.ui.component.graftable;


import java.awt.image.BufferedImage;


public interface GraftableComponentPaintReceiver
{
   public void regionPainted(final BufferedImage paintedImage, final int destinationX, final int destinationY, final boolean deferRedraw);
   public void regionPainted(final BufferedImage paintedImage, final int inputImageStartX, final int inputImageStartY,
                                final int regionWidth, final int regionHeight, final int destinationX, final int destinationY, final boolean deferRedraw);
   public void transferRegion(final int sourceX, final int sourceY, final int regionWidth, final int regionHeight, final int destinationX, final int destinationY, final boolean deferRedraw);
   public void regionCleared(final int regionX, final int regionY, final int regionWidth, final int regionHeight, final boolean deferRedraw);
   public void redrawRegion(final int regionX, final int regionY, final int regionWidth, final int regionHeight);
}