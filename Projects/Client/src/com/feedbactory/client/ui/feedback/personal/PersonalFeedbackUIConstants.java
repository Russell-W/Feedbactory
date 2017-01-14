
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.client.ui.UIConstants;
import java.awt.Color;


abstract class PersonalFeedbackUIConstants
{
   static final Color VeryColour = new Color(0, 255, 0, 30);
   static final Color ConsiderablyColour = new Color(200, 255, 0, 30);
   static final Color ModeratelyColour = new Color(255, 200, 0, 30);
   static final Color NotVeryColour = new Color(255, 100, 0, 30);
   static final Color NotAtAllColour = new Color(255, 0, 0, 30);
   static final Color NoRatingColour = new Color(0, 0, 0, 0);


   private PersonalFeedbackUIConstants()
   {
   }


   // Not used in the existing LAF, but used to be in Napkin. And may be useful sometime again..?
   static public Color calculateFeedbackRatingColor(final byte feedbackGrade)
   {
      final float scaleValue;

      if ((feedbackGrade >= 0) && (feedbackGrade < 25))
      {
         scaleValue = (float) feedbackGrade / 25;
         return getScaledColour(PersonalFeedbackUIConstants.NotAtAllColour, PersonalFeedbackUIConstants.NotVeryColour, scaleValue);
      }
      else if ((feedbackGrade >= 25) && (feedbackGrade < 50))
      {
         scaleValue = (float) (feedbackGrade - 25) / 25;
         return getScaledColour(PersonalFeedbackUIConstants.NotVeryColour, PersonalFeedbackUIConstants.ModeratelyColour, scaleValue);
      }
      else if ((feedbackGrade >= 50) && (feedbackGrade < 75))
      {
         scaleValue = (float) (feedbackGrade - 50) / 25;
         return getScaledColour(PersonalFeedbackUIConstants.ModeratelyColour, PersonalFeedbackUIConstants.ConsiderablyColour, scaleValue);
      }
      else if ((feedbackGrade >= 75) && (feedbackGrade <= 100))
      {
         scaleValue = (float) (feedbackGrade - 75) / 25;
         return getScaledColour(PersonalFeedbackUIConstants.ConsiderablyColour, PersonalFeedbackUIConstants.VeryColour, scaleValue);
      }
      else
         return UIConstants.ClearColour;
   }


   static public Color getScaledColour(final Color colourOne, final Color colourTwo, final float scaleValue)
   {
      final int scaledRedComponent = getScaledColourComponent(colourOne.getRed(), colourTwo.getRed(), scaleValue);
      final int scaledGreenComponent = getScaledColourComponent(colourOne.getGreen(), colourTwo.getGreen(), scaleValue);
      final int scaledBlueComponent = getScaledColourComponent(colourOne.getBlue(), colourTwo.getBlue(), scaleValue);
      final int scaledAlphaComponent = getScaledColourComponent(colourOne.getAlpha(), colourTwo.getAlpha(), scaleValue);

      return new Color(scaledRedComponent, scaledGreenComponent, scaledBlueComponent, scaledAlphaComponent);
   }


   static public int getScaledColourComponent(final int colourComponentOne, final int colourComponentTwo, final float scaleValue)
   {
      return colourComponentOne + (int) ((colourComponentTwo - colourComponentOne) * scaleValue);
   }
}