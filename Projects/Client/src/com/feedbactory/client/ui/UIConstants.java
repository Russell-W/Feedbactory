

package com.feedbactory.client.ui;


import com.feedbactory.client.ui.component.ShadedComponent.RadialGradientPaintProfile;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;


abstract public class UIConstants
{
   static final public String ApplicationTitle = "Feedbactory";

   static final public String ApplicationIconSmallPath = "/media/shared/FeedbactoryIconSmall.png";
   static final public String ApplicationIconLargePath = "/media/shared/FeedbactoryIconLarge.png";

   static final public String UIFontBaseName = "Roboto";
   static final public String UIRegularFontLocation = "/media/shared/Roboto-Regular.ttf";
   static final public String UIBoldFontLocation = "/media/shared/Roboto-Bold.ttf";

   static final public Color ClearColour = new Color(0, 0, 0, 0);

   static final public int ComponentShadowLength = 7;
   static final public int ComponentMaximumShadowTransparency = 130;

   static final public RadialGradientPaintProfile FrameTitleBarButtonMouseOverGradient = new RadialGradientPaintProfile(0f, 0f, 1f, 1f,
                                                                                                                        new Color[] {new Color(204, 207, 213), new Color(171, 176, 186)},
                                                                                                                        new float[] {0f, 1f});

   static final public RadialGradientPaintProfile FrameTitleBarButtonPressedGradient = new RadialGradientPaintProfile(0f, 0f, 1f, 1f,
                                                                                                                      new Color[] {new Color(164, 173, 182), new Color(123, 129, 143)},
                                                                                                                      new float[] {0f, 1f});

   static final public RadialGradientPaintProfile FrameTitleBarButtonAlertGradient = new RadialGradientPaintProfile(0f, 0f, 1f, 1f,
                                                                                                                    new Color[] {new Color(234, 237, 243), new Color(201, 206, 216)},
                                                                                                                    new float[] {0f, 1f});

   static final public RadialGradientPaintProfile ControlPanelButtonMouseOverGradient = new RadialGradientPaintProfile(0f, 0f, 1f, 1f,
                                                                                                                       new Color[] {new Color(214, 217, 223), new Color(188, 191, 195)},
                                                                                                                       new float[] {0f, 1f});

   static final public RadialGradientPaintProfile ControlPanelButtonPressedGradient = new RadialGradientPaintProfile(0f, 0f, 1f, 1f,
                                                                                                                     new Color[] {new Color(174, 183, 192), new Color(150, 153, 157)},
                                                                                                                     new float[] {0f, 1f});

   static final public RadialGradientPaintProfile ControlPanelButtonAlertGradient = new RadialGradientPaintProfile(0f, 0f, 1f, 1f,
                                                                                                                   new Color[] {new Color(234, 237, 243), new Color(218, 221, 225)},
                                                                                                                   new float[] {0f, 1f});

   static final public RadialGradientPaintProfile MenuGradient = new RadialGradientPaintProfile(-0.5f, 0f, 2f, 1f,
                                                                                                new Color[] {new Color(244, 247, 243), new Color(211, 216, 226)},
                                                                                                new float[] {0f, 1f});

   static final public RadialGradientPaintProfile MenuButtonMouseOverGradient = new RadialGradientPaintProfile(0f, 0f, 1f, 1f,
                                                                                                               new Color[] {new Color(234, 237, 233), new Color(201, 206, 216)},
                                                                                                               new float[] {0f, 1f});

   static final public RadialGradientPaintProfile MenuButtonPressedGradient = new RadialGradientPaintProfile(0f, 0f, 1f, 1f,
                                                                                                             new Color[] {new Color(189, 198, 207), new Color(148, 154, 168)},
                                                                                                             new float[] {0f, 1f});

   static final public int MediumContainerGapSize = 10;
   static final public int LargeUnrelatedComponentGapSize = 30;

   static final public Color LighterPanelColour = new Color(214, 217, 223);
   static final public Color ContentPanelColour = new Color(208, 210, 214);

   static final public RadialGradientPaintProfile LighterPanelGradient = new RadialGradientPaintProfile(0f, 0f, 1f, 1f,
                                                                                                        new Color[] {UIUtilities.shiftColourBrightness(LighterPanelColour, 0.1f), UIConstants.LighterPanelColour},
                                                                                                        new float[] {0f, 1f});

   static final public RadialGradientPaintProfile ContentPanelGradient = new RadialGradientPaintProfile(0f, 0f, 1f, 1f,
                                                                                                        new Color[] {UIUtilities.shiftColourBrightness(ContentPanelColour, 0.1f), ContentPanelColour},
                                                                                                        new float[] {0f, 1f});

   static final public Color TextFlashFaderColour = new Color(165, 0, 40);

   static final public Font SmallFont;
   static final public Font RegularFont;
   static final public Font MediumFont;
   static final public Font LargeFont;

   static final public Font RegularBoldFont;
   static final public Font MediumBoldFont;
   static final public Font LargeBoldFont;

   static final public float RegularLineSpacing = 0.3f;

   static final public Color ListCellRegularColor = Color.white;
   static final public Color ListCellStripeColour = new Color(248, 248, 252);
   static final public Color ListCellBorderColour = new Color(232, 240, 247);
   static final public Color ListCellSelectionHighlightColour = new Color(226, 234, 241); //new Color(206, 216, 224);

   static final public int TitleBarButtonCornerRadius = 20;

   static final public int MinimumButtonWidth = 80;

   static final public int PreferredIconWidth = 17;
   static final public int PreferredIconHeight = 17;

   // A little extra button width makes the feedback item 'lock' button look less squished in.
   static final public int PreferredIconButtonWidth = 31;
   static final public int PreferredIconButtonHeight = 29;

   static final public Color TextToolTipInnerColour = new Color(255, 255, 215);
   static final public Color TextToolTipOuterColour = new Color(242, 242, 189);

   static final public Color ProgressBarShadingColour = new Color(180, 180, 180, 130);
   static final public int ProgressBarWidth = 130;
   static final public int ProgressBarHeight = 61;

   static final public Icon InformationIconSmall;
   static final public Icon QuestionIconSmall;
   static final public Icon WarningIconSmall;
   static final public Icon ErrorIconSmall;

   static final public Icon InformationIconMedium;
   static final public Icon QuestionIconMedium;
   static final public Icon WarningIconMedium;
   static final public Icon ErrorIconMedium;

   static final public Icon InformationIconLarge;
   static final public Icon QuestionIconLarge;
   static final public Icon WarningIconLarge;
   static final public Icon ErrorIconLarge;

   static
   {
      try
      {
         final Font basePlainFont = Font.createFont(Font.TRUETYPE_FONT, UIConstants.class.getResourceAsStream(UIRegularFontLocation));
         GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(basePlainFont);

         SmallFont = basePlainFont.deriveFont(Font.PLAIN, 11);
         RegularFont = basePlainFont.deriveFont(Font.PLAIN, 12);
         MediumFont = basePlainFont.deriveFont(Font.PLAIN, 13);
         LargeFont = basePlainFont.deriveFont(Font.PLAIN, 16);

         final Font baseBoldFont = Font.createFont(Font.TRUETYPE_FONT, UIConstants.class.getResourceAsStream(UIBoldFontLocation));
         GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(baseBoldFont);

         RegularBoldFont = baseBoldFont.deriveFont(Font.BOLD, 12);
         MediumBoldFont = baseBoldFont.deriveFont(Font.BOLD, 14);
         LargeBoldFont = baseBoldFont.deriveFont(Font.BOLD, 16);

         final int smallIconDimension = 12;
         final int mediumIconDimension = 20;
         final int largeIconDimension = 30;

         InformationIconSmall = new ImageIcon(getScaledIcon(UIManager.getIcon("OptionPane.informationIcon"), smallIconDimension, smallIconDimension));
         QuestionIconSmall = new ImageIcon(getScaledIcon(UIManager.getIcon("OptionPane.questionIcon"), smallIconDimension, smallIconDimension));
         WarningIconSmall = new ImageIcon(getScaledIcon(UIManager.getIcon("OptionPane.warningIcon"), smallIconDimension, smallIconDimension));
         ErrorIconSmall = new ImageIcon(getScaledIcon(UIManager.getIcon("OptionPane.errorIcon"), smallIconDimension, smallIconDimension));

         InformationIconMedium = new ImageIcon(getScaledIcon(UIManager.getIcon("OptionPane.informationIcon"), mediumIconDimension, mediumIconDimension));
         QuestionIconMedium = new ImageIcon(getScaledIcon(UIManager.getIcon("OptionPane.questionIcon"), mediumIconDimension, mediumIconDimension));
         WarningIconMedium = new ImageIcon(getScaledIcon(UIManager.getIcon("OptionPane.warningIcon"), mediumIconDimension, mediumIconDimension));
         ErrorIconMedium = new ImageIcon(getScaledIcon(UIManager.getIcon("OptionPane.errorIcon"), mediumIconDimension, mediumIconDimension));

         InformationIconLarge = new ImageIcon(getScaledIcon(UIManager.getIcon("OptionPane.informationIcon"), largeIconDimension, largeIconDimension));
         QuestionIconLarge = new ImageIcon(getScaledIcon(UIManager.getIcon("OptionPane.questionIcon"), largeIconDimension, largeIconDimension));
         WarningIconLarge = new ImageIcon(getScaledIcon(UIManager.getIcon("OptionPane.warningIcon"), largeIconDimension, largeIconDimension));
         ErrorIconLarge = new ImageIcon(getScaledIcon(UIManager.getIcon("OptionPane.errorIcon"), largeIconDimension, largeIconDimension));
      }
      catch (final Exception fontException)
      {
         throw new RuntimeException("Could not initialise fonts.", fontException);
      }
   }


   static private Image getScaledIcon(final Icon fullSizeIcon, final int scaledWidth, final int scaledHeight)
   {
      final BufferedImage fullSizeIconImage = UIUtilities.createCompatibleImage(fullSizeIcon.getIconWidth(), fullSizeIcon.getIconHeight(), Transparency.TRANSLUCENT);
      final Graphics2D graphics2D = fullSizeIconImage.createGraphics();
      fullSizeIcon.paintIcon(null, graphics2D, 0, 0);
      graphics2D.dispose();

      return UIUtilities.getProportionalScaledImage(fullSizeIconImage, scaledWidth, scaledHeight, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
   }


   private UIConstants()
   {
   }
}