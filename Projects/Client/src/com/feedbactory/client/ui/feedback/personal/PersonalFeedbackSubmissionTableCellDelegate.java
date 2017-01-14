
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleKeyValue;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackSubmissionScaleProfile;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;


final class PersonalFeedbackSubmissionTableCellDelegate
{
   final private JPanel rendererComponent = new JPanel(null);

   final private Map<PersonalFeedbackSubmissionScaleKeyValue, JRadioButton> submissionScaleButtons = new HashMap<PersonalFeedbackSubmissionScaleKeyValue, JRadioButton>();

   private PersonalFeedbackSubmissionScaleKeyValue selection;

   final private List<PersonalFeedbackSubmissionSelectionChangeListener> selectionChangeListeners = new ArrayList<PersonalFeedbackSubmissionSelectionChangeListener>(1);


   PersonalFeedbackSubmissionTableCellDelegate()
   {
      initialise();
   }


   private void initialise()
   {
      initialiseRendererComponent();
   }


   private void initialiseRendererComponent()
   {
      final GroupLayout panelLayout = new GroupLayout(rendererComponent);
      rendererComponent.setLayout(panelLayout);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSwitchToSubmissionScaleProfile(final PersonalFeedbackSubmissionScaleProfile submissionScaleProfile)
   {
      rendererComponent.removeAll();

      final PersonalFeedbackSubmissionScaleRenderer submissionScaleProfileRenderer = PersonalFeedbackSubmissionScaleRenderer.getRendererFor(submissionScaleProfile);
      final int[] horizontalControlGaps = submissionScaleProfileRenderer.getSubmissionScaleControlGaps();
      final GroupLayout panelLayout = (GroupLayout) rendererComponent.getLayout();
      final SequentialGroup horizontalGroup = panelLayout.createSequentialGroup();
      final ParallelGroup verticalGroup = panelLayout.createParallelGroup();
      final ButtonGroup buttonGroup = new ButtonGroup();
      JRadioButton submissionScaleButton;
      int submissionScaleGapIndex = 0;

      horizontalGroup.addGap(horizontalControlGaps[submissionScaleGapIndex ++]);

      for (final PersonalFeedbackSubmissionScaleKeyValue submissionScaleKeyValue : submissionScaleProfile.getKeyValues())
      {
         submissionScaleButton = new JRadioButton();
         buttonGroup.add(submissionScaleButton);

         submissionScaleButton.addActionListener(new ActionListener()
         {
            @Override
            final public void actionPerformed(final ActionEvent actionEvent)
            {
               handleSubmissionScaleButtonActionPerformed(submissionScaleKeyValue);
            }
         });

         horizontalGroup.addComponent(submissionScaleButton);
         if (submissionScaleGapIndex < submissionScaleProfile.getKeyValues().size())
            horizontalGroup.addGap(horizontalControlGaps[submissionScaleGapIndex]);

         verticalGroup.addComponent(submissionScaleButton);

         submissionScaleButtons.put(submissionScaleKeyValue, submissionScaleButton);

         submissionScaleGapIndex ++;
      }

      horizontalGroup.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE);

      panelLayout.setHorizontalGroup(horizontalGroup);
      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
         .addGroup(verticalGroup)
         .addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
      );
   }


   private void handleSubmissionScaleButtonActionPerformed(final PersonalFeedbackSubmissionScaleKeyValue submissionScaleKeyValue)
   {
      selection = submissionScaleKeyValue;
      notifyItemListenersOfSelectionChange();
   }


   private void notifyItemListenersOfSelectionChange()
   {
      for (final PersonalFeedbackSubmissionSelectionChangeListener selectionChangeListener : selectionChangeListeners)
         selectionChangeListener.selectionChanged(selection);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void switchToSubmissionScaleProfile(final PersonalFeedbackSubmissionScaleProfile submissionScaleProfile)
   {
      handleSwitchToSubmissionScaleProfile(submissionScaleProfile);
   }


   final JPanel getRendererComponent()
   {
      return rendererComponent;
   }


   final PersonalFeedbackSubmissionScaleKeyValue getValue()
   {
      return selection;
   }


   final void setValue(final PersonalFeedbackSubmissionScaleKeyValue submissionScaleKeyValue)
   {
      selection = submissionScaleKeyValue;
      submissionScaleButtons.get(submissionScaleKeyValue).setSelected(true);
   }


   final void setPaintProperties(final boolean isSelected, final int paintRowIndex)
   {
      if (isSelected)
         rendererComponent.setBackground(UIConstants.ListCellSelectionHighlightColour);
      else
         rendererComponent.setBackground(((paintRowIndex % 2) == 0) ? UIConstants.ListCellRegularColor : UIConstants.ListCellStripeColour);
   }


   final void addSelectionChangeListener(final PersonalFeedbackSubmissionSelectionChangeListener selectionChangeListener)
   {
      selectionChangeListeners.add(selectionChangeListener);
   }


   final void removeSelectionChangeListener(final PersonalFeedbackSubmissionSelectionChangeListener selectionChangeListener)
   {
      selectionChangeListeners.remove(selectionChangeListener);
   }
}