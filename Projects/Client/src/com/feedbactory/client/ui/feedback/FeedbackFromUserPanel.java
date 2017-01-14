
package com.feedbactory.client.ui.feedback;


import com.feedbactory.client.core.feedback.ItemProfileFeedbackSummary;
import com.feedbactory.client.ui.component.ImageLoader;
import com.feedbactory.client.ui.component.ImageLoader.ImageLoadRequester;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.client.ui.component.SelfLabelledTextField;
import com.feedbactory.client.ui.component.ShadowedComponent;
import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Formatter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolTip;
import javax.swing.KeyStroke;
import javax.swing.LayoutStyle;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter.SortKey;
import javax.swing.ScrollPaneConstants;
import javax.swing.SortOrder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;


final class FeedbackFromUserPanel
{
   static final private Dimension FeedbackSubmissionsTableSize = new Dimension(710, 400);

   final private FeedbackUIManager uiManager;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private SelfLabelledTextField filterTextField = new SelfLabelledTextField();

   final private JComponent tablePanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private JScrollPane feedbackSubmissionsTableScrollPane = new JScrollPane();
   final private JTable feedbackSubmissionsTable;
   final private FeedbackFromUserTableModel feedbackSubmissionsTableModel;

   final private JButton viewButton = new JButton();
   final private JButton closeButton = new JButton();

   final private List<ItemProfileFeedbackSummary> userFeedbackSubmissions;
   private List<ItemProfileFeedbackSummary> filteredUserFeedbackSubmissions;


   FeedbackFromUserPanel(final FeedbackUIManager uiManager, final ImageLoader imageLoader, final List<ItemProfileFeedbackSummary> userFeedbackSubmissions)
   {
      this.uiManager = uiManager;

      feedbackSubmissionsTableModel = new FeedbackFromUserTableModel(imageLoader);
      feedbackSubmissionsTable = new FeedbackSubmissionsTable(feedbackSubmissionsTableModel, imageLoader);

      this.userFeedbackSubmissions = userFeedbackSubmissions;

      initialise();
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Feedback From Me");
      builder.setRadialGradientPaint(UIConstants.LighterPanelGradient);
      return new NimbusBorderPanel(builder);
   }


   private void initialise()
   {
      initialiseTagsFilterTextField();
      initialiseTablePanel();
      initialiseDelegatePanel();
   }


   private void initialiseTagsFilterTextField()
   {
      resetFilteredUserFeedbackSubmissions();

      filterTextField.setFont(UIConstants.RegularFont);
      filterTextField.setLabel("Keywords");

      filterTextField.getDocument().addDocumentListener(new DocumentListener()
      {
         @Override
         final public void insertUpdate(final DocumentEvent documentEvent)
         {
            handleFilterTextFieldChanged(true);
         }


         @Override
         final public void removeUpdate(final DocumentEvent documentEvent)
         {
            handleFilterTextFieldChanged(false);
         }


         @Override
         final public void changedUpdate(final DocumentEvent documentEvent)
         {
         }
      });

      final InputMap inputMap = filterTextField.getInputMap(JComponent.WHEN_FOCUSED);
      final ActionMap actionMap = filterTextField.getActionMap();

      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelComponent");

      actionMap.put("cancelComponent", new AbstractAction()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleFilterTextFieldActionCancelled();
         }
      });
   }


   private void initialiseTablePanel()
   {
      tablePanel.setBackground(UIConstants.ContentPanelColour);

      initialiseCriteriaFeedbackTable();
      initialiseTablePanelLayout();
   }


   private void initialiseCriteriaFeedbackTable()
   {
      feedbackSubmissionsTableModel.setUserFeedbackSubmissions(userFeedbackSubmissions);

      feedbackSubmissionsTable.setGridColor(UIConstants.ListCellBorderColour);
      feedbackSubmissionsTable.setShowGrid(true);

      feedbackSubmissionsTable.setFillsViewportHeight(true);
      feedbackSubmissionsTable.setRowHeight(50);

      feedbackSubmissionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      feedbackSubmissionsTable.setDefaultRenderer(Object.class, new BasicTableCellRenderer());

      final TableRowSorter<FeedbackFromUserTableModel> tableRowSorter = new TableRowSorter<FeedbackFromUserTableModel>(feedbackSubmissionsTableModel);
      tableRowSorter.setComparator(FeedbackFromUserTableModel.FeedbackSubmissionSummaryColumnIndex, new FeedbackSubmissionSummarySorter());
      tableRowSorter.setComparator(FeedbackFromUserTableModel.FeedbackSummaryColumnIndex, new FeedbackSummarySorter());
      List<SortKey> sortKeys = new ArrayList<SortKey>(1);
      sortKeys.add(new SortKey(FeedbackFromUserTableModel.DisplayNameColumnIndex, SortOrder.ASCENDING));
      tableRowSorter.setSortKeys(sortKeys);
      feedbackSubmissionsTable.setRowSorter(tableRowSorter);

      final JTableHeader tableHeader = feedbackSubmissionsTable.getTableHeader();
      tableHeader.setFont(UIConstants.RegularFont);
      tableHeader.setReorderingAllowed(false);

      final TableColumn thumbnailColumn = feedbackSubmissionsTable.getColumnModel().getColumn(FeedbackFromUserTableModel.ThumbnailColumnIndex);
      thumbnailColumn.setCellRenderer(new ThumbnailCellRenderer());

      // Apply a rigid size for the thumbnail column.
      thumbnailColumn.setResizable(false);
      thumbnailColumn.setMinWidth(50);
      thumbnailColumn.setMaxWidth(50);

      // And use percentages to set the widths of the remaining columns, as a proportion of the remaining overall table width.
      final int remainingTableWidth = FeedbackSubmissionsTableSize.width - 50;

      final TableColumn itemNameColumn = feedbackSubmissionsTable.getColumnModel().getColumn(FeedbackFromUserTableModel.DisplayNameColumnIndex);
      itemNameColumn.setPreferredWidth((int) (0.38f * remainingTableWidth));

      final TableColumn websiteColumn = feedbackSubmissionsTable.getColumnModel().getColumn(FeedbackFromUserTableModel.WebsiteColumnIndex);
      websiteColumn.setPreferredWidth((int) (0.125f * remainingTableWidth));

      final TableColumn feedbackSubmissionSummaryColumn = feedbackSubmissionsTable.getColumnModel().getColumn(FeedbackFromUserTableModel.FeedbackSubmissionSummaryColumnIndex);
      feedbackSubmissionSummaryColumn.setCellRenderer(new FeedbackSubmissionSummaryCellRenderer());
      feedbackSubmissionSummaryColumn.setPreferredWidth((int) (0.125f * remainingTableWidth));

      final TableColumn feedbackSubmissionTimeColumn = feedbackSubmissionsTable.getColumnModel().getColumn(FeedbackFromUserTableModel.FeedbackSubmissionTimeColumnIndex);
      feedbackSubmissionTimeColumn.setCellRenderer(new FeedbackSubmissionTimeCellRenderer());
      feedbackSubmissionTimeColumn.setPreferredWidth((int) (0.19f * remainingTableWidth));

      final TableColumn feedbackSummaryColumn = feedbackSubmissionsTable.getColumnModel().getColumn(FeedbackFromUserTableModel.FeedbackSummaryColumnIndex);
      feedbackSummaryColumn.setCellRenderer(new FeedbackSummaryCellRenderer());
      feedbackSummaryColumn.setPreferredWidth((int) (0.08f * remainingTableWidth));

      final TableColumn numberOfRatingsColumn = feedbackSubmissionsTable.getColumnModel().getColumn(FeedbackFromUserTableModel.NumberOfRatingsColumnIndex);
      numberOfRatingsColumn.setCellRenderer(new NumberOfRatingsCellRenderer());
      numberOfRatingsColumn.setPreferredWidth((int) (0.1f * remainingTableWidth));

      feedbackSubmissionsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener()
      {
         @Override
         final public void valueChanged(final ListSelectionEvent listSelectionEvent)
         {
            handleFeedbackSubmissionsTableSelectionChanged();
         }
      });

      feedbackSubmissionsTable.addMouseListener(new MouseAdapter()
      {
         @Override
         final public void mouseClicked(final MouseEvent mouseEvent)
         {
            if (mouseEvent.getClickCount() == 2)
               handleFeedbackSubmissionsTableMouseDoubleClicked();
         }
      });

      feedbackSubmissionsTableScrollPane.setViewportView(feedbackSubmissionsTable);
      feedbackSubmissionsTableScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
   }


   private void initialiseTablePanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(tablePanel);
      tablePanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(feedbackSubmissionsTableScrollPane, GroupLayout.PREFERRED_SIZE, FeedbackSubmissionsTableSize.width, GroupLayout.PREFERRED_SIZE)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(feedbackSubmissionsTableScrollPane, GroupLayout.PREFERRED_SIZE, FeedbackSubmissionsTableSize.height, GroupLayout.PREFERRED_SIZE)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );
   }


   private void initialiseDelegatePanel()
   {
      viewButton.setFont(UIConstants.RegularFont);
      viewButton.setText("View");
      viewButton.setEnabled(false);
      viewButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleViewButtonActionPerformed();
         }
      });

      closeButton.setFont(UIConstants.RegularFont);
      closeButton.setText("Close");
      closeButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleCloseButtonActionPerformed();
         }
      });

      initialiseDelegatePanelLayout();
   }


   private void initialiseDelegatePanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getContentPane());
      delegatePanel.getContentPane().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.TRAILING)
            .addGroup(panelLayout.createSequentialGroup()
               .addComponent(filterTextField, GroupLayout.PREFERRED_SIZE, 120, GroupLayout.PREFERRED_SIZE)
               .addGap(UIConstants.MediumContainerGapSize)
            )
            .addComponent(tablePanel)
            .addGroup(panelLayout.createSequentialGroup()
               .addGap(0, 0, Integer.MAX_VALUE)
               .addComponent(viewButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
               .addComponent(closeButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addGap(0, 0, Integer.MAX_VALUE)
            )
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(filterTextField)
         .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
         .addComponent(tablePanel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(viewButton)
            .addComponent(closeButton)
         )
         .addContainerGap()
      );
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class FeedbackSubmissionsTable extends JTable implements ImageLoadRequester
   {
      static final private int MaximumItemThumbnailDimensions = 100;
      static final private int ToolTipLeftDisplayXThreshold = 150;

      final private ImageLoader imageLoader;

      final private ThumbnailToolTip thumbnailToolTip = new ThumbnailToolTip();
      private ItemProfileFeedbackSummary toolTipFeedbackSubmission;
      private Point toolTipLocation;


      private FeedbackSubmissionsTable(final FeedbackFromUserTableModel tableModel, final ImageLoader imageLoader)
      {
         super(tableModel);
         this.imageLoader = imageLoader;

         initialise();
      }


      private void initialise()
      {
         initialiseMouseMotionListener();
      }


      private void initialiseMouseMotionListener()
      {
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
            final FeedbackFromUserTableModel tableModel = (FeedbackFromUserTableModel) getModel();
            final ItemProfileFeedbackSummary feedbackSubmission = tableModel.getUserFeedbackSubmission(mouseOverRowIndex);

            if (feedbackSubmission != toolTipFeedbackSubmission)
            {
               // While the tooltip image is loading or even just switching, do not display a tooltip image for a previous item.
               clearToolTip();

               final String photoURL = feedbackSubmission.itemProfile.getThumbnailImageURL();
               if (photoURL != null)
               {
                  toolTipFeedbackSubmission = feedbackSubmission;

                  /* There's a lurking pitfall in Swing here when updating the location, ultimately sometimes leading to a NullPointerException
                   * (within Swing code, specifically initiateToolTip(MouseEvent event)) when an image hasn't already been cached by the
                   * Feedbactory image loader. It seems to hinge on the value of the toolTipLocation - if the toolTipLocation has been set
                   * to non-null for a non-cached image (loadImage returns null below) at the time that the overridden getToolTipLocation() 
                   * is called by Swing, a NPE can often result. The issue can be difficult to replicate usually, but there are a few things
                   * to help it along:
                   * - Change the SoftReferences in the image loader to WeakReferences so that they aren't cached for as long.
                   * - Load a Feedbactory checkpoint with plenty of images, enough to scroll between a couple of pages.
                   * - Ensure that some of the thumbnail images are large images that are scaled down.
                   * - Change rapidly between the tooltip images.
                   * 
                   * It's the checking for null of the thumbnail tooltip's image in getToolTipLocation() that can prevent the NPE here; if there's a cached
                   * image, the property will be set and it's safe to return the updated tool tip location, otherwise it must always return null.
                   * 
                   * I previously implemented an almost identical version of this method that set the tooltip location only if
                   * the image had been cached, ie. loadImage() below returns non-null. This also worked fine, however had the downside that
                   * the mouse/tool tip location was not being retained at the time of image load, and later when reportImageLoaded() is called
                   * and the tooltip is then ready for display. It seems that if getToolTipLocation() returns null, Swing will fall back to
                   * its own decision on where to display the tool tip. However its decisions will often lead to heavyweight tooltips being
                   * displayed slightly outside of the bounds of the window, killing the nice shadow effect.
                   */
                  updateToolTipLocation(mouseEvent);

                  final BufferedImage toolTipImage = imageLoader.loadImage(photoURL, MaximumItemThumbnailDimensions, MaximumItemThumbnailDimensions, this);
                  if (toolTipImage != null)
                     processToolTipImage(photoURL, toolTipImage);
               }
            }
         }
         else
            clearToolTip();
      }


      private void updateToolTipLocation(final MouseEvent mouseEvent)
      {
         if ((getWidth() - mouseEvent.getX()) >= ToolTipLeftDisplayXThreshold)
            toolTipLocation = new Point(mouseEvent.getX() + MaximumItemThumbnailDimensions, mouseEvent.getY() - MaximumItemThumbnailDimensions);
         else
            toolTipLocation = new Point(mouseEvent.getX() - (MaximumItemThumbnailDimensions * 2), mouseEvent.getY() - MaximumItemThumbnailDimensions);
      }


      @Override
      final public void reportImageLoaded(final String imageURL, final BufferedImage image, final Throwable exception)
      {
         // Check to make sure that the user hasn't moved the mouse to a different row or to a point where no tooltip should be displayed.
         if ((toolTipFeedbackSubmission != null) && toolTipFeedbackSubmission.itemProfile.getThumbnailImageURL().equals(imageURL))
         {
            if (image != null)
               processToolTipImage(imageURL, image);
            else
               clearToolTip();
         }
      }


      private void processToolTipImage(final String imageURL, final BufferedImage toolTipImage)
      {
         final BufferedImage thumbnailScaledImage = UIUtilities.getSquareCroppedScaledImage(toolTipImage, MaximumItemThumbnailDimensions,
                                                                                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
         thumbnailToolTip.setImage(thumbnailScaledImage);
         setToolTipText(imageURL);
      }


      private void clearToolTip()
      {
         if (toolTipFeedbackSubmission != null)
         {
            toolTipFeedbackSubmission = null;
            toolTipLocation = null;
            setToolTipText(null);
            thumbnailToolTip.setImage(null);
         }
      }


      @Override
      final public JToolTip createToolTip()
      {
         return thumbnailToolTip;
      }


      @Override
      final public Point getToolTipLocation(final MouseEvent mouseEvent)
      {
         // Refer to the comments in handleMouseMoved() above.
         return (thumbnailToolTip.getImage() != null) ? toolTipLocation : null;
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class ThumbnailToolTip extends JToolTip
   {
      final private JLabel label = new JLabel();
      final private ShadowedComponent borderPanel = new ShadowedComponent(label);


      private ThumbnailToolTip()
      {
         initialise();
      }


      private void initialise()
      {
         setOpaque(false);

         setBorder(BorderFactory.createEmptyBorder());

         label.setBorder(BorderFactory.createLineBorder(Color.black));

         setLayout(new BorderLayout());
         add(borderPanel, BorderLayout.CENTER);
      }


      @Override
      final public Dimension getPreferredSize()
      {
         return borderPanel.getPreferredSize();
      }


      @Override
      final protected void paintComponent(final Graphics graphics)
      {
         // NOP
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private Icon getImage()
      {
         return label.getIcon();
      }


      private void setImage(final BufferedImage toolTipImage)
      {
         label.setIcon((toolTipImage != null) ? new ImageIcon(toolTipImage) : null);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static private class BasicTableCellRenderer extends DefaultTableCellRenderer
   {
      private BasicTableCellRenderer()
      {
         initialise();
      }


      private void initialise()
      {
         // Setting the font on the table itself won't do the job, it has to be done here on the renderer.
         setFont(UIConstants.RegularFont);
      }


      /****************************************************************************
       *
       ***************************************************************************/


      @Override
      final public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column)
      {
         updateValue(value, row);

         if (isSelected)
            setBackground(UIConstants.ListCellSelectionHighlightColour);
         else
            setBackground(((row % 2) == 0) ? UIConstants.ListCellRegularColor : UIConstants.ListCellStripeColour);

         return this;
      }


      void updateValue(final Object value, final int row)
      {
         setValue(value);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class ThumbnailCellRenderer extends BasicTableCellRenderer
   {
      @Override
      final void updateValue(final Object value, final int row)
      {
         final ImageIcon thumbnailImageIcon = (ImageIcon) value;
         setIcon(thumbnailImageIcon);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class FeedbackSubmissionSummaryCellRenderer extends BasicTableCellRenderer
   {
      @Override
      final void updateValue(final Object value, final int row)
      {
         final ItemProfileFeedbackSummary feedbackSummary = (ItemProfileFeedbackSummary) value;
         final FeedbackCategoryDataFormatter formatter = getFeedbackCategoryDataFormatter(feedbackSummary.itemProfile.getFeedbackCategory());
         final String formattedString = formatter.getFeedbackSubmissionSummaryString(feedbackSummary.feedbackSubmissionSummary);
         setValue(formattedString);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class FeedbackSubmissionTimeCellRenderer extends BasicTableCellRenderer
   {
      @Override
      final void updateValue(final Object value, final int row)
      {
         final long dateAndTime = (Long) value;

         final Formatter formatter = new Formatter();

         final Calendar displayDateTimeCalendar = Calendar.getInstance();
         displayDateTimeCalendar.setTimeInMillis(dateAndTime);

         final Calendar todayCalendar = Calendar.getInstance();
         final Calendar yesterdayCalendar = Calendar.getInstance();
         yesterdayCalendar.add(Calendar.DAY_OF_YEAR, -1);

         final int displayDateTimeYear = displayDateTimeCalendar.get(Calendar.YEAR);
         final int displayDateTimeDay = displayDateTimeCalendar.get(Calendar.DAY_OF_YEAR);

         if ((displayDateTimeYear == todayCalendar.get(Calendar.YEAR)) &&
             (displayDateTimeDay == todayCalendar.get(Calendar.DAY_OF_YEAR)))
            formatter.format("Today %tl:%<tM%<tp", dateAndTime);
         else if ((displayDateTimeYear == yesterdayCalendar.get(Calendar.YEAR)) &&
                  (displayDateTimeDay == yesterdayCalendar.get(Calendar.DAY_OF_YEAR)))
            formatter.format("Yesterday %tl:%<tM%<tp", dateAndTime);
         else
            formatter.format("%td/%<tm/%<tY", dateAndTime);

         setValue(formatter.toString());
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class FeedbackSummaryCellRenderer extends BasicTableCellRenderer
   {
      @Override
      final void updateValue(final Object value, final int row)
      {
         final ItemProfileFeedbackSummary feedbackSummary = (ItemProfileFeedbackSummary) value;
         final FeedbackCategoryDataFormatter formatter = getFeedbackCategoryDataFormatter(feedbackSummary.itemProfile.getFeedbackCategory());
         final String formattedString = formatter.getFeedbackSummaryString(feedbackSummary.feedbackSummary);
         setValue(formattedString);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class NumberOfRatingsCellRenderer extends BasicTableCellRenderer
   {
      @Override
      final void updateValue(final Object value, final int row)
      {
         final int numberOfRatings = (Integer) value;
         final NumberFormat numberFormat = NumberFormat.getIntegerInstance();
         setValue(numberFormat.format(numberOfRatings));
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   final private class FeedbackSubmissionSummarySorter implements Comparator<ItemProfileFeedbackSummary>
   {
      @Override
      final public int compare(final ItemProfileFeedbackSummary feedbackSummaryOne, final ItemProfileFeedbackSummary feedbackSummaryTwo)
      {
         final FeedbackCategoryDataFormatter formatterOne = getFeedbackCategoryDataFormatter(feedbackSummaryOne.itemProfile.getFeedbackCategory());
         final FeedbackCategoryDataFormatter formatterTwo = getFeedbackCategoryDataFormatter(feedbackSummaryTwo.itemProfile.getFeedbackCategory());
         final byte sortValueOne = formatterOne.getSortableSubmissionSummary(feedbackSummaryOne.feedbackSubmissionSummary);
         final byte sortValueTwo = formatterTwo.getSortableSubmissionSummary(feedbackSummaryTwo.feedbackSubmissionSummary);

         if (sortValueOne < sortValueTwo)
            return -1;
         else if (sortValueOne > sortValueTwo)
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


   final private class FeedbackSummarySorter implements Comparator<ItemProfileFeedbackSummary>
   {
      @Override
      final public int compare(final ItemProfileFeedbackSummary feedbackSummaryOne, final ItemProfileFeedbackSummary feedbackSummaryTwo)
      {
         final FeedbackCategoryDataFormatter formatterOne = getFeedbackCategoryDataFormatter(feedbackSummaryOne.itemProfile.getFeedbackCategory());
         final FeedbackCategoryDataFormatter formatterTwo = getFeedbackCategoryDataFormatter(feedbackSummaryTwo.itemProfile.getFeedbackCategory());
         final byte sortValueOne = formatterOne.getSortableFeedbackSummary(feedbackSummaryOne.feedbackSummary);
         final byte sortValueTwo = formatterTwo.getSortableFeedbackSummary(feedbackSummaryTwo.feedbackSummary);

         if (sortValueOne < sortValueTwo)
            return -1;
         else if (sortValueOne > sortValueTwo)
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


   private FeedbackCategoryDataFormatter getFeedbackCategoryDataFormatter(final FeedbackCategory feedbackCategory)
   {
      return uiManager.getFeedbackCategoryUIRegistry().getFeedbackCategoryUIHandler(feedbackCategory).getFeedbackDataFormatter();
   }


   private void resetFilteredUserFeedbackSubmissions()
   {
      filteredUserFeedbackSubmissions = new LinkedList<ItemProfileFeedbackSummary>(userFeedbackSubmissions);
   }


   private void handleFilterTextFieldChanged(final boolean isNarrowingSearch)
   {
      if (! isNarrowingSearch)
         resetFilteredUserFeedbackSubmissions();

      final Pattern splitPattern = Pattern.compile("\\s");
      final String[] filterWords = splitPattern.split(filterTextField.getText().toLowerCase());

      final Iterator<ItemProfileFeedbackSummary> filteredUserFeedbackIterator = filteredUserFeedbackSubmissions.iterator();
      FeedbackItemProfile submissionItemProfile;

      while (filteredUserFeedbackIterator.hasNext())
      {
         submissionItemProfile = filteredUserFeedbackIterator.next().itemProfile;
         if (! matchesItemTags(submissionItemProfile, filterWords, splitPattern))
            filteredUserFeedbackIterator.remove();
      }

      feedbackSubmissionsTableModel.setUserFeedbackSubmissions(filteredUserFeedbackSubmissions);
   }


   private boolean matchesItemTags(final FeedbackItemProfile itemProfile, final String[] filterWords, final Pattern splitPattern)
   {
      final String[] displayNameWords = splitPattern.split(itemProfile.getFullName().toLowerCase());

      filterTagLoop:
      for (final String filterWord : filterWords)
      {
         /* At the moment, tags are not loaded from the server for previously submitted items to reduce bandwidth, so this local filtering
          * only works against the display name until the item has been browsed.
          */
         for (final String itemTag : itemProfile.getTags())
         {
            if (itemTag.contains(filterWord))
               continue filterTagLoop;
         }

         for (final String displayNameWord : displayNameWords)
         {
            if (displayNameWord.contains(filterWord))
               continue filterTagLoop;
         }

         return false;
      }

      return true;
   }


   private void handleFilterTextFieldActionCancelled()
   {
      filterTextField.setText("");
      feedbackSubmissionsTableModel.setUserFeedbackSubmissions(userFeedbackSubmissions);
   }


   private void handleFeedbackSubmissionsTableSelectionChanged()
   {
      viewButton.setEnabled(! feedbackSubmissionsTable.getSelectionModel().isSelectionEmpty());
   }


   private void handleFeedbackSubmissionsTableMouseDoubleClicked()
   {
      if (! feedbackSubmissionsTable.getSelectionModel().isSelectionEmpty())
         viewSelectedFeedbackItem();
   }


   private void handleViewButtonActionPerformed()
   {
      viewSelectedFeedbackItem();
   }


   private void viewSelectedFeedbackItem()
   {
      final int modelIndex = feedbackSubmissionsTable.convertRowIndexToModel(feedbackSubmissionsTable.getSelectedRow());
      uiManager.showItemInBrowser(filteredUserFeedbackSubmissions.get(modelIndex).itemProfile);
   }


   private void handleCloseButtonActionPerformed()
   {
      uiManager.dismissFeedbackFromUserPanel();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}