/* Memos:
 * - An assumption at present is that the number of applicable websites for every criteria type wouldn't stretch beyond the bounds of the screen.
 *   If this proves not to be the case, the controls may need to be displayed in a table within a scrollpane.
 *
 * - This panel is displayed as a temporary component on the display manager, meaning that when the user presses Escape it is automatically dismissed without
 *   firing any actions or cleanup methods here. If this need should change, ie. some cleanup does need to be performed, I need to be aware of this.
 *
 * - If empty, the supplied filterWebsites parameter indicates that no filter applies, ie. all websites for the criteria type are selected.
 *   Internally to this class though, the selected websites must be present in the selectedWebsites set as this makes it much easier to track the
 *   selections (switching on or off) as the user updates the check boxes.
 */

package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsiteSet;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.LayoutStyle;
import javax.swing.LayoutStyle.ComponentPlacement;


final class PersonalFeedbackFeaturedItemsFilterPanel
{
   final private PersonalFeedbackFeaturedItemsPanel featuredItemsPanel;

   final private NimbusBorderPanel delegatePanel;

   final private RoundedPanel innerPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private JLabel selectAllLabel = new JLabel();
   final private JCheckBox selectAllCheckBox = new JCheckBox();

   final private Set<PersonalFeedbackWebsite> applicableWebsites;
   final private Set<PersonalFeedbackWebsite> selectedWebsites;

   final private JButton applyButton = new JButton();
   final private JButton cancelButton = new JButton();


   PersonalFeedbackFeaturedItemsFilterPanel(final PersonalFeedbackFeaturedItemsPanel featuredItemsPanel, final PersonalFeedbackCriteriaType criteriaType,
                                            final Set<PersonalFeedbackWebsite> selectedWebsites)
   {
      delegatePanel = createDelegatePanel(criteriaType);

      this.featuredItemsPanel = featuredItemsPanel;
      applicableWebsites = PersonalFeedbackWebsite.getWebsites(criteriaType);
      this.selectedWebsites = new PersonalFeedbackWebsiteSet(selectedWebsites);

      initialise();
   }


   private NimbusBorderPanel createDelegatePanel(final PersonalFeedbackCriteriaType criteriaType)
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder(criteriaType.displayName + " Websites");
      builder.setRadialGradientPaint(UIConstants.LighterPanelGradient);
      return new NimbusBorderPanel(builder);
   }


   private void initialise()
   {
      initialiseSelectedWebsites();
      final List<WebsiteControls> websiteCheckBoxes = initialiseWebsiteCheckboxes();
      initialiseSelectAllCheckBox(websiteCheckBoxes);
      initialiseInnerPanel(websiteCheckBoxes);
      initialiseDelegatePanel();
   }


   private void initialiseSelectedWebsites()
   {
      if (selectedWebsites.isEmpty())
         selectedWebsites.addAll(applicableWebsites);
   }


   private List<WebsiteControls> initialiseWebsiteCheckboxes()
   {
      final List<WebsiteControls> websiteCheckBoxes = new LinkedList<WebsiteControls>();
      JLabel websiteLabel;
      WebsiteControls websiteControls;

      for (final PersonalFeedbackWebsite website : applicableWebsites)
      {
         websiteLabel = new JLabel();
         websiteLabel.setFont(UIConstants.RegularFont);
         websiteLabel.setText(website.getName() + ":");

         final JCheckBox websiteCheckBox = new JCheckBox();
         if (selectedWebsites.contains(website))
            websiteCheckBox.setSelected(true);

         websiteControls = new WebsiteControls(website, websiteLabel, websiteCheckBox);
         initialiseWebsiteCheckboxListener(websiteControls);

         websiteCheckBoxes.add(new WebsiteControls(website, websiteLabel, websiteCheckBox));
      }

      Collections.sort(websiteCheckBoxes, new WebsiteControlsComparator());

      return websiteCheckBoxes;
   }


   private void initialiseWebsiteCheckboxListener(final WebsiteControls websiteControls)
   {
      websiteControls.websiteCheckBox.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleWebsiteCheckBoxActionPerformed(websiteControls);
         }
      });
   }


   private void initialiseSelectAllCheckBox(final List<WebsiteControls> websiteControls)
   {
      selectAllLabel.setFont(UIConstants.RegularFont);
      selectAllLabel.setText("Select all:");

      selectAllCheckBox.setFont(UIConstants.RegularFont);

      if (selectedWebsites.size() == applicableWebsites.size())
         selectAllCheckBox.setSelected(true);

      selectAllCheckBox.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleSelectDeselectAllCheckBoxActionPerformed(websiteControls);
         }
      });
   }


   private void initialiseInnerPanel(List<WebsiteControls> websiteControls)
   {
      final GroupLayout panelLayout = new GroupLayout(innerPanel);
      innerPanel.setLayout(panelLayout);

      final ParallelGroup websiteLabelsHorizontalGroup = panelLayout.createParallelGroup();
      final ParallelGroup websiteCheckBoxesHorizontalGroup = panelLayout.createParallelGroup();

      final SequentialGroup websiteControlsVerticalGroup = panelLayout.createSequentialGroup();
      websiteControlsVerticalGroup.addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE);

      int controlIndex = 0;
      for (final WebsiteControls controls : websiteControls)
      {
         websiteLabelsHorizontalGroup.addComponent(controls.websiteLabel);
         websiteCheckBoxesHorizontalGroup.addComponent(controls.websiteCheckBox);

         websiteControlsVerticalGroup.addGroup(panelLayout.createParallelGroup(Alignment.CENTER)
            .addComponent(controls.websiteLabel)
            .addComponent(controls.websiteCheckBox)
         );

         if (controlIndex != (websiteControls.size() - 1))
            websiteControlsVerticalGroup.addPreferredGap(ComponentPlacement.UNRELATED);

         controlIndex ++;
      }

      websiteControlsVerticalGroup.addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addGroup(websiteLabelsHorizontalGroup)
         .addPreferredGap(ComponentPlacement.UNRELATED)
         .addGroup(websiteCheckBoxesHorizontalGroup)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(websiteControlsVerticalGroup);
   }


   private void initialiseDelegatePanel()
   {
      initialiseDelegatePanelFooterControls();
      initialiseDelegatePanelLayout();
   }


   private void initialiseDelegatePanelFooterControls()
   {
      applyButton.setFont(UIConstants.RegularFont);
      applyButton.setText("Apply");
      applyButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleApplyButtonActionPerformed();
         }
      });

      cancelButton.setFont(UIConstants.RegularFont);
      cancelButton.setText("Cancel");
      cancelButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleCancelButtonActionPerformed();
         }
      });
   }


   private void initialiseDelegatePanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getContentPane());
      delegatePanel.getContentPane().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(panelLayout.createParallelGroup()
            .addGroup(panelLayout.createSequentialGroup()
               .addGap(5)
               .addComponent(selectAllLabel)
               .addPreferredGap(ComponentPlacement.UNRELATED)
               .addComponent(selectAllCheckBox)
            )
            .addComponent(innerPanel)
            .addGroup(panelLayout.createSequentialGroup()
               .addGap(0, 0, Integer.MAX_VALUE)
               .addComponent(applyButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
               .addComponent(cancelButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addGap(0, 0, Integer.MAX_VALUE)
            )
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addGap(10)
         .addGroup(panelLayout.createParallelGroup(Alignment.CENTER)
            .addComponent(selectAllLabel)
            .addComponent(selectAllCheckBox)
         )
         .addPreferredGap(ComponentPlacement.UNRELATED)
         .addComponent(innerPanel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(applyButton)
            .addComponent(cancelButton)
         )
         .addContainerGap()
      );
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class WebsiteControls
   {
      final private PersonalFeedbackWebsite website;
      final private JLabel websiteLabel;
      final private JCheckBox websiteCheckBox;


      private WebsiteControls(final PersonalFeedbackWebsite website, final JLabel websiteLabel, final JCheckBox websiteCheckBox)
      {
         this.website = website;
         this.websiteLabel = websiteLabel;
         this.websiteCheckBox = websiteCheckBox;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class WebsiteControlsComparator implements Comparator<WebsiteControls>
   {
      @Override
      final public int compare(final WebsiteControls websiteControlsOne, final WebsiteControls websiteControlsTwo)
      {
         return websiteControlsOne.websiteLabel.getText().compareToIgnoreCase(websiteControlsTwo.websiteLabel.getText());
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void handleWebsiteCheckBoxActionPerformed(final WebsiteControls websiteControls)
   {
      if (websiteControls.websiteCheckBox.isSelected())
         selectedWebsites.add(websiteControls.website);
      else
         selectedWebsites.remove(websiteControls.website);

      if (selectedWebsites.isEmpty())
      {
         selectAllCheckBox.setSelected(false);
         applyButton.setEnabled(false);
      }
      else
      {
         if (selectedWebsites.size() == applicableWebsites.size())
            selectAllCheckBox.setSelected(true);

         applyButton.setEnabled(true);
      }
   }


   private void handleSelectDeselectAllCheckBoxActionPerformed(final List<WebsiteControls> websiteControls)
   {
      if (selectAllCheckBox.isSelected())
      {
         for (final WebsiteControls control : websiteControls)
         {
            control.websiteCheckBox.setSelected(true);
            selectedWebsites.add(control.website);
         }

         applyButton.setEnabled(true);
      }
      else
      {
         for (final WebsiteControls control : websiteControls)
            control.websiteCheckBox.setSelected(false);

         selectedWebsites.clear();
         applyButton.setEnabled(false);
      }
   }


   private void handleApplyButtonActionPerformed()
   {
      // If all websites have been selected by the user (the expected case), use an empty set to indicate this.
      if (selectedWebsites.size() == applicableWebsites.size())
         featuredItemsPanel.applyWebsiteFilter(this, PersonalFeedbackWebsiteSet.EmptySet);
      else
         featuredItemsPanel.applyWebsiteFilter(this, selectedWebsites);
   }


   private void handleCancelButtonActionPerformed()
   {
      featuredItemsPanel.cancelWebsiteFilter(this);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}