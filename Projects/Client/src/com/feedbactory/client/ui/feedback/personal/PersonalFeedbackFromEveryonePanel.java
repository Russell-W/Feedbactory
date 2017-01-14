
package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackDetailedSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteria;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaDistribution;
import com.feedbactory.client.core.network.DataAvailabilityStatus;
import javax.swing.RowSorter.SortKey;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SortOrder;
import javax.swing.table.TableRowSorter;
import java.util.Comparator;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import javax.swing.ToolTipManager;
import java.awt.event.MouseMotionAdapter;
import com.feedbactory.client.ui.component.LockableComponent;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.*;
import com.feedbactory.shared.feedback.personal.*;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolTip;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;


final class PersonalFeedbackFromEveryonePanel
{
   static final private int FeedbackDistributionTableWidth = 150;
   static final private int CriteriaFeedbackTableWidth = 340;
   static final private String SuppressedLowAverageCriteriaRatingLabel;

   static
   {
      final NumberFormat numberFormat = NumberFormat.getNumberInstance();
      numberFormat.setMinimumFractionDigits(1);
      SuppressedLowAverageCriteriaRatingLabel = "Less than " + numberFormat.format(PersonalFeedbackCriteriaDistribution.MinimumVisibleAverageCriteriaRating / 10f);
   }

   final private PersonalFeedbackDetailedPanel parentPanel;

   final private LockableComponent delegatePanel = new LockableComponent(new JPanel(null));
   private JComponent activeLockedComponent;

   final private JTable feedbackDistributionTable = new JTable(new PersonalFeedbackDistributionTableModel());
   final private JScrollPane feedbackDistributionTableScrollPane = new JScrollPane(feedbackDistributionTable);

   final private JTable criteriaFeedbackDistributionTable = new CriteriaFeedbackDistributionTable(new PersonalFeedbackCriteriaDistributionTableModel());
   final private JScrollPane criteriaFeedbackDistributionTableScrollPane = new JScrollPane(criteriaFeedbackDistributionTable);

   private PersonalFeedbackPersonProfile personProfile;
   private DataAvailabilityStatus detailedFeedbackFromEveryoneAvailabilityStatus = DataAvailabilityStatus.NotAvailable;
   private PersonalFeedbackDetailedSummary detailedFeedbackFromEveryone = PersonalFeedbackDetailedSummary.EmptyFeedbackSummary;


   PersonalFeedbackFromEveryonePanel(final PersonalFeedbackDetailedPanel parentPanel)
   {
      this.parentPanel = parentPanel;

      initialise();
   }


   private void initialise()
   {
      initialiseEnabledFeedbackPanel();

      initialiseLockedFeedbackPanel();

      initialiseDelegatePanel();
   }


   private void initialiseEnabledFeedbackPanel()
   {
      delegatePanel.getUnlockedComponent().setOpaque(false);

      initialiseOverallFeedbackDistributionTable();

      initialiseCriteriaFeedbackDistributionTable();

      initialisePanelLayout();
   }


   private void initialiseOverallFeedbackDistributionTable()
   {
      feedbackDistributionTable.setGridColor(UIConstants.ListCellBorderColour);
      feedbackDistributionTable.setShowGrid(true);

      // See the layout note below for the rationale behind overriding the table's default scrollable viewport size.
      feedbackDistributionTable.setPreferredScrollableViewportSize(new Dimension(0, 0));
      feedbackDistributionTable.setFillsViewportHeight(true);
      feedbackDistributionTable.setRowHeight(30);

      feedbackDistributionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      feedbackDistributionTable.setDefaultRenderer(Object.class, new PersonalFeedbackGeneralCellRenderer<Object>());

      final JTableHeader tableHeader = feedbackDistributionTable.getTableHeader();
      tableHeader.setFont(UIConstants.RegularFont);
      tableHeader.setReorderingAllowed(false);
      tableHeader.setResizingAllowed(false);

      final TableColumn criteriaColumn = feedbackDistributionTable.getColumnModel().getColumn(PersonalFeedbackDistributionTableModel.RatingColumnIndex);
      criteriaColumn.setPreferredWidth((int) (0.45f * FeedbackDistributionTableWidth));

      final TableColumn averageRatingColumn = feedbackDistributionTable.getColumnModel().getColumn(PersonalFeedbackDistributionTableModel.DistributionPercentageColumnIndex);
      averageRatingColumn.setPreferredWidth((int) (0.55f * FeedbackDistributionTableWidth));

      feedbackDistributionTableScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
   }


   private void initialiseCriteriaFeedbackDistributionTable()
   {
      criteriaFeedbackDistributionTable.setGridColor(UIConstants.ListCellBorderColour);
      criteriaFeedbackDistributionTable.setShowGrid(true);

      // See the layout note below for the rationale behind overriding the table's default scrollable viewport size.
      criteriaFeedbackDistributionTable.setPreferredScrollableViewportSize(new Dimension(0, 0));
      criteriaFeedbackDistributionTable.setFillsViewportHeight(true);
      criteriaFeedbackDistributionTable.setRowHeight(30);

      criteriaFeedbackDistributionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      criteriaFeedbackDistributionTable.setDefaultRenderer(Object.class, new PersonalFeedbackGeneralCellRenderer<Object>());

      final PersonalFeedbackCriteriaDistributionTableModel tableModel = (PersonalFeedbackCriteriaDistributionTableModel) criteriaFeedbackDistributionTable.getModel();
      final TableRowSorter<?> tableRowSorter = new TableRowSorter<PersonalFeedbackCriteriaDistributionTableModel>(tableModel);
      tableRowSorter.setComparator(PersonalFeedbackCriteriaDistributionTableModel.AverageRatingColumnIndex, new AverageCriteriaRatingTableRowSorter());
      List<SortKey> sortKeys = new ArrayList<SortKey>(1);
      sortKeys.add(new SortKey(PersonalFeedbackCriteriaDistributionTableModel.CriteriaColumnIndex, SortOrder.ASCENDING));
      tableRowSorter.setSortKeys(sortKeys);
      criteriaFeedbackDistributionTable.setRowSorter(tableRowSorter);

      final JTableHeader tableHeader = criteriaFeedbackDistributionTable.getTableHeader();
      tableHeader.setFont(UIConstants.RegularFont);
      tableHeader.setReorderingAllowed(false);
      tableHeader.setResizingAllowed(false);

      final TableColumn criteriaColumn = criteriaFeedbackDistributionTable.getColumnModel().getColumn(PersonalFeedbackCriteriaDistributionTableModel.CriteriaColumnIndex);
      criteriaColumn.setPreferredWidth((int) (0.45f * CriteriaFeedbackTableWidth));

      final TableColumn averageRatingColumn = criteriaFeedbackDistributionTable.getColumnModel().getColumn(PersonalFeedbackCriteriaDistributionTableModel.AverageRatingColumnIndex);
      averageRatingColumn.setCellRenderer(new CriteriaAverageRatingCellRenderer());
      averageRatingColumn.setPreferredWidth((int) (0.275f * CriteriaFeedbackTableWidth));

      final TableColumn numberOfRatingsColumn = criteriaFeedbackDistributionTable.getColumnModel().getColumn(PersonalFeedbackCriteriaDistributionTableModel.RatingCountColumnIndex);
      numberOfRatingsColumn.setCellRenderer(new NumberOfRatingsCellRenderer());
      numberOfRatingsColumn.setPreferredWidth((int) (0.275f * CriteriaFeedbackTableWidth));

      criteriaFeedbackDistributionTableScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
   }


   private void initialisePanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getUnlockedComponent());
      delegatePanel.getUnlockedComponent().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(feedbackDistributionTableScrollPane, GroupLayout.PREFERRED_SIZE, FeedbackDistributionTableWidth, GroupLayout.PREFERRED_SIZE)
         .addPreferredGap(ComponentPlacement.UNRELATED)
         .addComponent(criteriaFeedbackDistributionTableScrollPane, GroupLayout.PREFERRED_SIZE, CriteriaFeedbackTableWidth, GroupLayout.PREFERRED_SIZE)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      /* The default preferred scrollable size for JTables is arbitrarily set to 450, 400.
       * Normally it would be possible to override this with sensible preferred values based on the column widths and the cell row height, but the overall height
       * of this panel is dictated by the parent panel in PersonalFeedbackDetailedPanel, for the sake of having the height of this panel and the
       * PersonalFeedbackFromUserPanel be exactly the same. At least the width of the table can be controlled by the horizontal group above, where
       * it's set to an exact amount - that part is simple.
       *
       * But there's a potential problem. If the table's default preferred height of 400 is greater than the room allowed it by the parent panel (and it is), then
       * the table will try to vertically squeeze out other components wherever it can, eg. the container gaps. By the same token it's no good to set a rigid smaller
       * preferred height on the table. The goal is to allow the table to fill out as much as it can vertically, while not eating into the preferred sizes of
       * any other components or gaps. The trick to it is to set rigid vertical container gaps on the GroupLayout, while allowing complete flexibility for the
       * table to grow within the available space - give it constraints of minimum 0, preferred 0, maximum Integer.MAX_VALUE.
       */
      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, UIConstants.MediumContainerGapSize)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(feedbackDistributionTableScrollPane, 0, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
            .addComponent(criteriaFeedbackDistributionTableScrollPane, 0, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, UIConstants.MediumContainerGapSize)
      );
   }


   private void initialiseLockedFeedbackPanel()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getLockedComponent());
      delegatePanel.getLockedComponent().setLayout(panelLayout);

      activeLockedComponent = new JPanel(null);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addGap(0, 0, Integer.MAX_VALUE)
         .addComponent(activeLockedComponent, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(0, 0, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addGap(0, 0, Integer.MAX_VALUE)
         .addComponent(activeLockedComponent, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
         .addGap(0, 0, Integer.MAX_VALUE)
      );
   }


   private void initialiseDelegatePanel()
   {
      delegatePanel.getRootComponent().setOpaque(false);
   }


   /****************************************************************************
    *
    * Start feedback by criteria table
    *
    ***************************************************************************/


   final private class CriteriaFeedbackDistributionTable extends JTable
   {
      static final private int ToolTipLeftDisplayXThreshold = 155;

      static final private int FeedbackTableToolTipInitialDelayMilliseconds = 300;
      static final private int FeedbackTableToolTipDismissDelayMilliseconds = 15000;

      private int originalToolTipInitialDelayMilliseconds;
      private int originalToolTipDismissDelayMilliseconds;

      private PersonalFeedbackCriteria toolTipCriteria;
      private PersonalFeedbackCriteriaDistribution toolTipCriteriaRatingDistribution;
      private Point toolTipLocation;


      private CriteriaFeedbackDistributionTable(final TableModel tableModel)
      {
         super(tableModel);

         initialise();
      }


      private void initialise()
      {
         initialiseListeners();
      }


      private void initialiseListeners()
      {
         addMouseListener(new MouseAdapter()
         {
            @Override
            final public void mouseEntered(final MouseEvent mouseEvent)
            {
               originalToolTipInitialDelayMilliseconds = ToolTipManager.sharedInstance().getInitialDelay();
               originalToolTipDismissDelayMilliseconds = ToolTipManager.sharedInstance().getDismissDelay();

               ToolTipManager.sharedInstance().setInitialDelay(FeedbackTableToolTipInitialDelayMilliseconds);
               ToolTipManager.sharedInstance().setDismissDelay(FeedbackTableToolTipDismissDelayMilliseconds);
            }


            @Override
            final public void mouseExited(final MouseEvent mouseEvent)
            {
               ToolTipManager.sharedInstance().setInitialDelay(originalToolTipInitialDelayMilliseconds);
               ToolTipManager.sharedInstance().setDismissDelay(originalToolTipDismissDelayMilliseconds);
            }
         });

         addMouseMotionListener(new MouseMotionAdapter()
         {
            @Override
            final public void mouseMoved(final MouseEvent mouseEvent)
            {
               handleMouseMoved(mouseEvent);
            }
         });
      }


      /****************************************************************************
       * 
       ***************************************************************************/


      private void handleMouseMoved(final MouseEvent mouseEvent)
      {
         final Point mouseLocation = mouseEvent.getPoint();

         final int rowAtPoint = rowAtPoint(mouseLocation);

         if (rowAtPoint != -1)
         {
            final int mouseOverRowIndex = convertRowIndexToModel(rowAtPoint);
            final PersonalFeedbackCriteriaDistributionTableModel tableModel = (PersonalFeedbackCriteriaDistributionTableModel) getModel();
            final PersonalFeedbackCriteria criteria = tableModel.getCriteria(mouseOverRowIndex);
            final PersonalFeedbackCriteriaDistribution criteriaFeedbackDistribution = tableModel.getFeedbackDistribution(mouseOverRowIndex);

            if (criteriaFeedbackDistribution != toolTipCriteriaRatingDistribution)
            {
               if (criteriaFeedbackDistribution.numberOfRatings > 0)
               {
                  if (mouseEvent.getX() < ToolTipLeftDisplayXThreshold)
                     mouseLocation.translate(35, -180);
                  else
                     mouseLocation.translate(-180, -180);

                  activateToolTip(criteria, criteriaFeedbackDistribution, mouseLocation);
               }
               else
                  clearToolTip();
            }
         }
         else
            clearToolTip();
      }


      private void activateToolTip(final PersonalFeedbackCriteria criteria, final PersonalFeedbackCriteriaDistribution criteriaFeedbackDistribution, final Point location)
      {
         toolTipCriteria = criteria;
         toolTipCriteriaRatingDistribution = criteriaFeedbackDistribution;
         toolTipLocation = location;

         setToolTipText("");
      }


      private void clearToolTip()
      {
         if (toolTipCriteria != null)
         {
            toolTipCriteria = null;
            toolTipCriteriaRatingDistribution = null;
            toolTipLocation = null;

            setToolTipText(null);
         }
      }


      @Override
      final public JToolTip createToolTip()
      {
         final PersonalFeedbackCriteriaType selectedCriteriaType = (PersonalFeedbackCriteriaType) personProfile.person.getCriteriaType();
         final PersonalFeedbackRatingDistributionToolTip toolTip = new PersonalFeedbackRatingDistributionToolTip(selectedCriteriaType.attributes.getSubmissionScaleProfile(),
                                                                                                                 toolTipCriteria.getDisplayName(),
                                                                                                                 toolTipCriteriaRatingDistribution);
         return toolTip.getDelegateToolTip();
      }


      @Override
      final public Point getToolTipLocation(final MouseEvent mouseEvent)
      {
         return toolTipLocation;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class NumberOfRatingsCellRenderer extends PersonalFeedbackGeneralCellRenderer<Integer>
   {
      @Override
      final protected void updateValue(final Integer numberOfRatings)
      {
         setValue(NumberFormat.getIntegerInstance().format(numberOfRatings.intValue()));
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class CriteriaAverageRatingCellRenderer extends PersonalFeedbackGeneralCellRenderer<PersonalFeedbackCriteriaDistribution>
   {
      @Override
      final protected void updateValue(final PersonalFeedbackCriteriaDistribution criteriaFeedbackDistribution)
      {
         if (criteriaFeedbackDistribution.numberOfRatings > 0)
         {
            final NumberFormat numberFormat = NumberFormat.getNumberInstance();
            numberFormat.setMinimumFractionDigits(1);

            if (criteriaFeedbackDistribution.averageFeedbackRating != PersonalFeedbackCriteriaDistribution.SuppressedLowAverageRating)
               setValue(numberFormat.format(criteriaFeedbackDistribution.averageFeedbackRating / 10f));
            else
               setValue("Less than " + numberFormat.format(PersonalFeedbackCriteriaDistribution.MinimumVisibleAverageCriteriaRating / 10f));
         }
         else
            setValue("-");
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class AverageCriteriaRatingTableRowSorter implements Comparator<PersonalFeedbackCriteriaDistribution>
   {
      @Override
      final public int compare(final PersonalFeedbackCriteriaDistribution criteriaRatingDistribution, final PersonalFeedbackCriteriaDistribution otherCriteriaRatingDistribution)
      {
         if (criteriaRatingDistribution.numberOfRatings == 0)
            return (otherCriteriaRatingDistribution.numberOfRatings == 0) ? 0 : -1;
         else if (otherCriteriaRatingDistribution.numberOfRatings == 0)
            return 1;
         else if (criteriaRatingDistribution.averageFeedbackRating == PersonalFeedbackCriteriaDistribution.SuppressedLowAverageRating)
            return (otherCriteriaRatingDistribution.averageFeedbackRating == PersonalFeedbackCriteriaDistribution.SuppressedLowAverageRating) ? 0 : -1;
         else if (criteriaRatingDistribution.averageFeedbackRating < otherCriteriaRatingDistribution.averageFeedbackRating)
            return -1;
         else if (criteriaRatingDistribution.averageFeedbackRating > otherCriteriaRatingDistribution.averageFeedbackRating)
            return 1;
         else
            return 0;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class PersonalFeedbackRatingDistributionToolTip
   {
      final private ShadowedToolTip delegateToolTip = new ShadowedToolTip();

      final private JLabel headingLabel = new JLabel();
      final private JLabel ratingLabel = new JLabel();
      final private JLabel resultLabel = new JLabel();
      final private JLabel numberOfRatingsLabel = new JLabel();


      private PersonalFeedbackRatingDistributionToolTip(final PersonalFeedbackSubmissionScaleProfile submissionScaleProfile,
                                                        final String criteriaName, final PersonalFeedbackCriteriaDistribution feedbackDistribution)
      {
         initialise(submissionScaleProfile, criteriaName, feedbackDistribution);
      }


      private void initialise(final PersonalFeedbackSubmissionScaleProfile submissionScaleProfile, final String criteriaName,
                              final PersonalFeedbackCriteriaDistribution feedbackDistribution)
      {
         final JLabel[] criteriaFeedbackDistributionKeyLabels;
         final JLabel[] criteriaFeedbackDistributionValueLabels;

         if (feedbackDistribution.averageFeedbackRating != PersonalFeedbackCriteriaDistribution.SuppressedLowAverageRating)
         {
            // Allocate space for every submission scale label, except for "Pass" (no rating).
            criteriaFeedbackDistributionKeyLabels = new JLabel[submissionScaleProfile.getKeyValues().size() - 1];
            criteriaFeedbackDistributionValueLabels = new JLabel[submissionScaleProfile.getKeyValues().size() - 1];
         }
         else
         {
            criteriaFeedbackDistributionKeyLabels = new JLabel[] {};
            criteriaFeedbackDistributionValueLabels = new JLabel[] {};
         }

         initialiseContent(submissionScaleProfile, criteriaName, feedbackDistribution,
                           criteriaFeedbackDistributionKeyLabels, criteriaFeedbackDistributionValueLabels);

         initialiseLayout(criteriaFeedbackDistributionKeyLabels, criteriaFeedbackDistributionValueLabels);
      }


      private void initialiseContent(final PersonalFeedbackSubmissionScaleProfile submissionScaleProfile, final String criteriaName,
                                     final PersonalFeedbackCriteriaDistribution feedbackDistribution,
                                     final JLabel[] criteriaFeedbackDistributionKeyLabels, final JLabel[] criteriaFeedbackDistributionValueLabels)
      {
         headingLabel.setFont(UIConstants.RegularBoldFont);
         headingLabel.setText(criteriaName);

         ratingLabel.setFont(UIConstants.RegularFont);
         ratingLabel.setText("Rating (0-10):");

         resultLabel.setFont(UIConstants.RegularBoldFont);

         if (feedbackDistribution.averageFeedbackRating != PersonalFeedbackCriteriaDistribution.SuppressedLowAverageRating)
         {
            final NumberFormat numberFormat = NumberFormat.getNumberInstance();
            numberFormat.setMinimumFractionDigits(1);
            resultLabel.setText(numberFormat.format(feedbackDistribution.averageFeedbackRating / 10f));
         }
         else
            resultLabel.setText(SuppressedLowAverageCriteriaRatingLabel);

         numberOfRatingsLabel.setFont(UIConstants.RegularFont);

         final StringBuilder labelStringBuilder = new StringBuilder();
         labelStringBuilder.append(NumberFormat.getIntegerInstance().format(feedbackDistribution.numberOfRatings));
         labelStringBuilder.append(" rating");
         if (feedbackDistribution.numberOfRatings != 1)
            labelStringBuilder.append('s');

         numberOfRatingsLabel.setText(labelStringBuilder.toString());

         if (feedbackDistribution.averageFeedbackRating != PersonalFeedbackCriteriaDistribution.SuppressedLowAverageRating)
         {
            int submissionScaleIndex = 0;
            JLabel criteriaKeyLabel;
            JLabel criteriaValueLabel;
            Byte feedbackDistributionPercentage;

            for (final PersonalFeedbackSubmissionScaleKeyValue submissionScale : submissionScaleProfile.getKeyValues())
            {
               if (submissionScale.value == PersonalFeedbackSubmission.NoRatingValue)
                  continue;

               criteriaKeyLabel = new JLabel();
               criteriaKeyLabel.setFont(UIConstants.RegularFont);
               criteriaKeyLabel.setText(submissionScale.displayName + ':');

               criteriaValueLabel = new JLabel();
               criteriaValueLabel.setFont(UIConstants.RegularFont);
               // The explicit boxing of byte -> Byte isn't necessary but I prefer it to hidden auto-boxing.
               feedbackDistributionPercentage = feedbackDistribution.feedbackDistributionPercentages.get(submissionScale);

               if (feedbackDistributionPercentage != null)
                  criteriaValueLabel.setText(feedbackDistributionPercentage.toString() + '%');
               else
                  criteriaValueLabel.setText("0%");

               criteriaFeedbackDistributionKeyLabels[submissionScaleIndex] = criteriaKeyLabel;
               criteriaFeedbackDistributionValueLabels[submissionScaleIndex] = criteriaValueLabel;

               submissionScaleIndex ++;
            }
         }
      }


      private void initialiseLayout(final JLabel[] submissionScaleKeyLabels, final JLabel[] submissionScaleValueLabels)
      {
         final GroupLayout toolTipLayout = new GroupLayout(delegateToolTip.getContentPane());
         delegateToolTip.getContentPane().setLayout(toolTipLayout);

         final ParallelGroup criteriaLabelHorizontalGroup = toolTipLayout.createParallelGroup();
         final ParallelGroup criteriaHorizontalGroup = toolTipLayout.createParallelGroup();
         final SequentialGroup criteriaVerticalGroup = toolTipLayout.createSequentialGroup();

         criteriaVerticalGroup.addGap(10)
            .addComponent(headingLabel)
            .addPreferredGap(ComponentPlacement.UNRELATED)
            .addGroup(toolTipLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
               .addComponent(ratingLabel)
               .addComponent(resultLabel)
            )
            .addPreferredGap(ComponentPlacement.UNRELATED)
            .addComponent(numberOfRatingsLabel)
            .addPreferredGap(ComponentPlacement.UNRELATED);

         // The array will be empty if the overall result has been suppressed (SuppressedLowAverageRating).
         for (int submissionScaleIndex = 0; submissionScaleIndex < submissionScaleKeyLabels.length; submissionScaleIndex ++)
         {
            final JLabel submissionScaleKeyLabel = submissionScaleKeyLabels[submissionScaleIndex];
            final JLabel submissionScaleValueLabel = submissionScaleValueLabels[submissionScaleIndex];

            criteriaLabelHorizontalGroup.addComponent(submissionScaleKeyLabel);
            criteriaHorizontalGroup.addComponent(submissionScaleValueLabel);

            criteriaVerticalGroup.addGroup(toolTipLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
               .addComponent(submissionScaleKeyLabel)
               .addComponent(submissionScaleValueLabel)
            );

            if (submissionScaleIndex < (submissionScaleKeyLabels.length - 1))
               criteriaVerticalGroup.addPreferredGap(ComponentPlacement.RELATED);
         }

         criteriaVerticalGroup.addGap(10);

         toolTipLayout.setHorizontalGroup(toolTipLayout.createSequentialGroup()
            .addGap(15)
            .addGroup(toolTipLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
               .addComponent(headingLabel)
               .addGroup(toolTipLayout.createSequentialGroup()
                  .addComponent(ratingLabel)
                  .addPreferredGap(ComponentPlacement.UNRELATED)
                  .addComponent(resultLabel)
               )
               .addComponent(numberOfRatingsLabel)
               .addGroup(toolTipLayout.createSequentialGroup()
                  .addGroup(criteriaLabelHorizontalGroup)
                  .addPreferredGap(ComponentPlacement.UNRELATED)
                  .addGroup(criteriaHorizontalGroup)
               )
            )
            .addGap(15)
         );

         toolTipLayout.setVerticalGroup(criteriaVerticalGroup);
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private JToolTip getDelegateToolTip()
      {
         return delegateToolTip;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void refreshCriteriaFeedbackTable()
   {
      final PersonalFeedbackDistributionTableModel feedbackDistributionTableModel = (PersonalFeedbackDistributionTableModel) feedbackDistributionTable.getModel();
      feedbackDistributionTableModel.setFeedbackDistributionPercentages(detailedFeedbackFromEveryone.getRatingDistributionPercentages());

      final PersonalFeedbackCriteriaType criteriaType = personProfile.person.getCriteriaType();
      final Map<? extends PersonalFeedbackCriteria, PersonalFeedbackCriteriaDistribution> criteriaFeedback = generateCriteriaFeedbackForCriteriaType(criteriaType.attributes);
      final PersonalFeedbackCriteriaDistributionTableModel tableModel = (PersonalFeedbackCriteriaDistributionTableModel) criteriaFeedbackDistributionTable.getModel();
      tableModel.setFeedbackCriteria(criteriaFeedback);
   }


   @SuppressWarnings("unchecked")
   private <E extends Enum<E> & PersonalFeedbackCriteria> Map<? extends PersonalFeedbackCriteria, PersonalFeedbackCriteriaDistribution> generateCriteriaFeedbackForCriteriaType(final PersonalFeedbackCriteriaAttributes<E> criteriaAttributes)
   {
      final Map<E, PersonalFeedbackCriteriaDistribution> criteriaTypeFeedback = new EnumMap<E, PersonalFeedbackCriteriaDistribution>(criteriaAttributes.getCriteriaClass());

      for (final E criteria : criteriaAttributes.getCriteriaSet())
         criteriaTypeFeedback.put(criteria, PersonalFeedbackCriteriaDistribution.NoCriteriaRatings);

      final Map<E, PersonalFeedbackCriteriaDistribution> detailedFeedbackSummary = (Map<E, PersonalFeedbackCriteriaDistribution>) detailedFeedbackFromEveryone.criteriaFeedback;
      if (detailedFeedbackSummary != null)
         criteriaTypeFeedback.putAll(detailedFeedbackSummary);

      return criteriaTypeFeedback;
   }


   private void updateLockedPanelForChangedStatus()
   {
      if (isErrorRetrievingDetailedFeedbackFromEveryone())
         showLockedComponent(getFeedbackRetrievalErrorDialog().getDelegate());
      else
         hideLockedPanel();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void showLockedComponent(final JComponent lockedComponentToActivate)
   {
      setActiveLockedComponent(lockedComponentToActivate);
      delegatePanel.setLocked(true);
   }


   private void setActiveLockedComponent(final JComponent lockedComponentToActivate)
   {
      final GroupLayout lockedPanelLayout = (GroupLayout) delegatePanel.getLockedComponent().getLayout();
      lockedPanelLayout.replace(activeLockedComponent, lockedComponentToActivate);

      activeLockedComponent = lockedComponentToActivate;
   }


   private void hideLockedPanel()
   {
      delegatePanel.setLocked(false);
      setActiveLockedComponent(new JPanel(null));
   }


   private MessageDialog getFeedbackRetrievalErrorDialog()
   {
      final JButton retryButton = new JButton();
      retryButton.setFont(UIConstants.RegularFont);
      retryButton.setText("Retry");
      retryButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            parentPanel.requestFetchDetailedFeedbackSummary();
         }
      });

      final String[] message = new String[] {"An error occurred while trying to load the feedback."};
      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Error, message);
      builder.setInputComponents(new JComponent[] {retryButton});

      return new MessageDialog(builder);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private boolean isErrorRetrievingDetailedFeedbackFromEveryone()
   {
      return (detailedFeedbackFromEveryoneAvailabilityStatus == DataAvailabilityStatus.Failed);
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handlePanelUpdateForChangedFeedback()
   {
      refreshCriteriaFeedbackTable();
      updateLockedPanelForChangedStatus();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final JComponent getDelegatePanel()
   {
      return delegatePanel.getRootComponent();
   }


   final void showUserEntry(final PersonalFeedbackPersonProfile personProfile, final DataAvailabilityStatus detailedFeedbackFromEveryoneAvailabilityStatus,
                            final PersonalFeedbackDetailedSummary detailedFeedbackFromEveryone)
   {
      this.personProfile = personProfile;
      this.detailedFeedbackFromEveryoneAvailabilityStatus = detailedFeedbackFromEveryoneAvailabilityStatus;
      this.detailedFeedbackFromEveryone = detailedFeedbackFromEveryone;

      handlePanelUpdateForChangedFeedback();
   }


   final void profileDetailsUpdated(final PersonalFeedbackPersonProfile updatedPersonProfile)
   {
      this.personProfile = updatedPersonProfile;
   }


   final void detailedFeedbackSummaryUpdated(final DataAvailabilityStatus detailedFeedbackFromEveryoneAvailabilityStatus,
                                             final PersonalFeedbackDetailedSummary detailedFeedbackFromEveryone)
   {
      this.detailedFeedbackFromEveryoneAvailabilityStatus = detailedFeedbackFromEveryoneAvailabilityStatus;
      this.detailedFeedbackFromEveryone = detailedFeedbackFromEveryone;

      handlePanelUpdateForChangedFeedback();
   }
}