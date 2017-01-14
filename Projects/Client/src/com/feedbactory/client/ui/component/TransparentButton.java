
package com.feedbactory.client.ui.component;


import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JButton;


final public class TransparentButton extends JButton
{
   final private TransparencyController transparencyController;


   public TransparentButton(final TransparencyController transparencyController)
   {
      validate(transparencyController);

      this.transparencyController = transparencyController;
   }


   private void validate(final TransparencyController transparencyController)
   {
      if (transparencyController == null)
         throw new IllegalArgumentException("Transparency controller cannot be null.");
   }


   @Override
   final public void paint(final Graphics graphics)
   {
      /* I could detect whether the alpha transparency has already been assigned from the TransparencyController via
       * the button's parent component, but the overhead of simply creating & reassigning a duplicate AlphaComposite object is trivial.
       */
      final Graphics2D graphics2D = (Graphics2D) graphics;
      final Composite existingComposite = graphics2D.getComposite();
      graphics2D.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, transparencyController.getTransparency()));
      super.paint(graphics2D);
      graphics2D.setComposite(existingComposite);
   }
}