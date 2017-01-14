/* Memos:
 * - Window translucency and custom shaping methods were added to Java in the JDK 6u10 release as private com.sun.awt.AWTUtilities methods, and then merged into the core JDK
 *   as of JDK 7.
 *
 * - This class attempts to provide the functionality via reflection into whichever of the two libraries (if any) exist in the current JVM.
 *
 * - Before invoking any of the window update methods, callers must first invoke the corresponding boolean methods to test whether the functionality is supported.
 */

package com.feedbactory.client.ui.component;


import java.awt.Color;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Shape;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


abstract public class TranslucencyUtilities
{
   static final private Color OpaqueColor = new Color(0, 0, 0, 255);
   static final private Color NonOpaqueColor = new Color(0, 0, 0, 0);

   static final private boolean isCoreTranslucencyImplemented;

   static final private boolean isWindowOpacityControlSupported;
   static final private boolean isPerPixelTranslucencySupported;
   static final private boolean isWindowShapingSupported;

   static final private Method setWindowOpacityMethod;
   static final private Method setWindowOpaqueMethod;
   static final private Method setWindowShapeMethod;

   static
   {
      boolean isCoreTranslucencyImplementedInitialiser = false;

      boolean isWindowOpacityControlSupportedInitialiser = false;
      boolean isPerPixelTranslucencySupportedInitialiser = false;
      boolean isWindowShapingSupportedInitialiser = false;

      Method setWindowOpacityMethodInitialiser = null;
      Method setWindowOpaqueMethodInitialiser = null;
      Method setWindowShapeMethodInitialiser = null;

      try
      {
         final Class<?> coreWindowTranslucencyClass = Class.forName("java.awt.GraphicsDevice$WindowTranslucency");

         final Object[] translucencyConstants = coreWindowTranslucencyClass.getEnumConstants();
         final Object perPixelTransparent = translucencyConstants[0];
         final Object translucent = translucencyConstants[1];
         final Object perPixelTranslucent = translucencyConstants[2];

         final Method isTranslucencySupportedMethod = GraphicsDevice.class.getMethod("isWindowTranslucencySupported", coreWindowTranslucencyClass);
         final Method isTranslucencyCapableMethod = GraphicsConfiguration.class.getMethod("isTranslucencyCapable");
         setWindowOpacityMethodInitialiser = Window.class.getMethod("setOpacity", float.class);
         setWindowShapeMethodInitialiser = Window.class.getMethod("setShape", Shape.class);

         isCoreTranslucencyImplementedInitialiser = true;

         final GraphicsDevice graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

         isWindowOpacityControlSupportedInitialiser = (Boolean) isTranslucencySupportedMethod.invoke(graphicsDevice, translucent);
         isPerPixelTranslucencySupportedInitialiser = (Boolean) isTranslucencyCapableMethod.invoke(graphicsDevice.getDefaultConfiguration()) &&
                                                      ((Boolean) isTranslucencySupportedMethod.invoke(graphicsDevice, perPixelTranslucent));
         isWindowShapingSupportedInitialiser = (Boolean) isTranslucencySupportedMethod.invoke(graphicsDevice, perPixelTransparent);
      }
      catch (final Exception anyException)
      {
         /* If the exception occurred before the isCoreTranslucencyImplementedInitialiser was set to true, the core translucency library isn't present in the current
          * version of Java - let's try the non-official translucency methods introduced in earlier versions.
          *
          * Otherwise all of the core translucency methods were detected but something else went wrong after the isCoreTranslucencyImplementedInitialiser
          * flag was set to true, and there is no point in trying to detect and invoke the older non-core translucency library.
          */
      }

      isCoreTranslucencyImplemented = isCoreTranslucencyImplementedInitialiser;

      if (! isCoreTranslucencyImplemented)
      {
         try
         {
            final Class<?> AWTUtilitiesClass = Class.forName("com.sun.awt.AWTUtilities");
            final Class<?> translucencyClass = Class.forName("com.sun.awt.AWTUtilities$Translucency");

            final Object[] translucencyConstants = translucencyClass.getEnumConstants();
            final Object perPixelTransparent = translucencyConstants[0];
            final Object translucent = translucencyConstants[1];
            final Object perPixelTranslucent = translucencyConstants[2];

            final Method isTranslucencySupportedMethod = AWTUtilitiesClass.getMethod("isTranslucencySupported", translucencyClass);
            final Method isTranslucencyCapableMethod = AWTUtilitiesClass.getMethod("isTranslucencyCapable", GraphicsConfiguration.class);
            setWindowOpacityMethodInitialiser = AWTUtilitiesClass.getMethod("setWindowOpacity", Window.class, float.class);
            setWindowOpaqueMethodInitialiser = AWTUtilitiesClass.getMethod("setWindowOpaque", Window.class, boolean.class);
            setWindowShapeMethodInitialiser = AWTUtilitiesClass.getMethod("setWindowShape", Window.class, Shape.class);

            final GraphicsDevice graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            isWindowOpacityControlSupportedInitialiser = (Boolean) isTranslucencySupportedMethod.invoke(graphicsDevice, translucent);
            isPerPixelTranslucencySupportedInitialiser = (Boolean) isTranslucencyCapableMethod.invoke(null, graphicsDevice.getDefaultConfiguration()) &&
                                                         ((Boolean) isTranslucencySupportedMethod.invoke(graphicsDevice, perPixelTranslucent));
            isWindowShapingSupportedInitialiser = (Boolean) isTranslucencySupportedMethod.invoke(graphicsDevice, perPixelTransparent);
         }
         catch (final Exception anyException)
         {
            // Translucency not available.
         }
      }

      isWindowOpacityControlSupported = isWindowOpacityControlSupportedInitialiser;
      isPerPixelTranslucencySupported = isPerPixelTranslucencySupportedInitialiser;
      isWindowShapingSupported = isWindowShapingSupportedInitialiser;

      setWindowOpacityMethod = setWindowOpacityMethodInitialiser;
      setWindowOpaqueMethod = setWindowOpaqueMethodInitialiser;
      setWindowShapeMethod = setWindowShapeMethodInitialiser;
   }


   private TranslucencyUtilities()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private static void invokeTranslucencyMethod(final Method method, final Object targetObject, final Object... arguments) throws IllegalAccessException, InvocationTargetException
   {
      method.invoke(targetObject, arguments);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public boolean isWindowOpacityControlSupported()
   {
      return isWindowOpacityControlSupported;
   }


   static public boolean isTranslucencySupported()
   {
      return isPerPixelTranslucencySupported;
   }


   static public boolean isWindowShapingSupported()
   {
      return isWindowShapingSupported;
   }


   static public void setWindowOpacity(final Window window, final float opacity) throws IllegalAccessException, InvocationTargetException
   {
      if (isCoreTranslucencyImplemented)
         invokeTranslucencyMethod(setWindowOpacityMethod, window, opacity);
      else
         invokeTranslucencyMethod(setWindowOpacityMethod, null, window, opacity);
   }


   static public void setWindowOpaque(final Window window, final boolean opaque) throws IllegalAccessException, InvocationTargetException
   {
      if (isCoreTranslucencyImplemented)
         window.setBackground(opaque ? OpaqueColor : NonOpaqueColor);
      else
         invokeTranslucencyMethod(setWindowOpaqueMethod, null, window, opaque);
   }


   static public void setWindowShape(final Window window, final Shape shape) throws IllegalAccessException, InvocationTargetException
   {
      if (isCoreTranslucencyImplemented)
         invokeTranslucencyMethod(setWindowShapeMethod, window, shape);
      else
         invokeTranslucencyMethod(setWindowShapeMethod, null, window, shape);
   }
}