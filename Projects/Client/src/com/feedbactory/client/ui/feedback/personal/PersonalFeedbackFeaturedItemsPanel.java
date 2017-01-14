/* Memos:
 * - This panel can apply to any PersonalFeedbackCriteriaType, and different panels can be shown for different broad criteria types, eg. photography.
 *   Maybe this is where an "Unpin photography/etc feedback" button or link needs to be placed eventually.
 */

package com.feedbactory.client.ui.feedback.personal;


import com.feedbactory.client.core.feedback.personal.PersonalFeedbackFeaturedItemsSampleResult;
import com.feedbactory.client.core.feedback.personal.PersonalFeedbackUtilities;
import com.feedbactory.client.core.network.NetworkRequestStatus;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.component.ImageLoader;
import com.feedbactory.client.ui.component.ImageLoader.ImageLoadRequester;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.client.ui.component.SelfLabelledTextField;
import com.feedbactory.client.ui.component.ShadowedComponent;
import com.feedbactory.client.ui.component.ShadowedToolTipButton;
import com.feedbactory.client.ui.component.SmileyProgressBar;
import com.feedbactory.client.ui.feedback.FeedbackPanel;
import com.feedbactory.client.ui.feedback.FeedbackResources;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.feedback.personal.CriteriaFeedbackFeaturedItemsFilter;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackBasicSummary;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackFeaturedPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsiteSet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JToolTip;
import javax.swing.KeyStroke;
import javax.swing.LayoutStyle;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;


final class PersonalFeedbackFeaturedItemsPanel
{
   static final private String SelectedWebsitesPreferencesKey = "FeaturedItemsSelectedWebsites";

   static final private Dimension FeaturedItemsTableDimensions = new Dimension(710, 400);
   static final private int FeaturedItemsTableRowHeight = 50;

   final private PersonalFeedbackUIManager uiManager;

   final private PersonalFeedbackCriteriaType criteriaType;

   final private NimbusBorderPanel delegatePanel;

   final private JToggleButton hotButton = new JToggleButton();
   final private JToggleButton newButton = new JToggleButton();
   private JToggleButton activeTableTabSelectionButton = hotButton;

   final private SelfLabelledTextField tagsFilterTextField = new SelfLabelledTextField();
   final private ShadowedToolTipButton showItemsFromButton = new ShadowedToolTipButton();
   final private ShadowedToolTipButton refreshButton = new ShadowedToolTipButton();

   final private RoundedPanel tablePanel = new RoundedPanel();

   final private JScrollPane featuredFeedbackTableScrollPane = new JScrollPane();
   final private FeaturedFeedbackItemsTable featuredFeedbackTable;
   final private PersonalFeedbackFeaturedItemsTableModel featuredFeedbackTableModel;

   final private JButton viewButton = new JButton();
   final private JButton closeButton = new JButton();

   private boolean isLoadingHotItems;
   private boolean isLoadingNewItems;

   private String activeTagsFilterString = "";
   private Set<String> activeTagsFilter = Collections.emptySet();
   private Set<PersonalFeedbackWebsite> selectedWebsites = new PersonalFeedbackWebsiteSet();

   final private List<PersonalFeedbackFeaturedPerson> hotItems = new ArrayList<PersonalFeedbackFeaturedPerson>();
   final private List<PersonalFeedbackFeaturedPerson> newItems = new ArrayList<PersonalFeedbackFeaturedPerson>();
   private PersonalFeedbackFeaturedPerson lastRetrievedHotItem = new PersonalFeedbackFeaturedPerson(FeedbactoryConstants.NoTime);
   private PersonalFeedbackFeaturedPerson lastRetrievedNewItem = new PersonalFeedbackFeaturedPerson(FeedbactoryConstants.NoTime);


   PersonalFeedbackFeaturedItemsPanel(final PersonalFeedbackUIManager uiManager, final PersonalFeedbackCriteriaType criteriaType,
                                      final ImageLoader imageLoader)
   {
      this.uiManager = uiManager;
      this.criteriaType = criteriaType;

      delegatePanel = createDelegatePanel(criteriaType);
      featuredFeedbackTableModel = new PersonalFeedbackFeaturedItemsTableModel(hotItems, imageLoader);
      featuredFeedbackTable = new FeaturedFeedbackItemsTable(featuredFeedbackTableModel, imageLoader);

      initialise();
   }


   private NimbusBorderPanel createDelegatePanel(final PersonalFeedbackCriteriaType criteriaType)
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Recent " + criteriaType.displayName + " Feedback");
      builder.setRadialGradientPaint(UIConstants.LighterPanelGradient);
      return new NimbusBorderPanel(builder);
   }


   private void initialise()
   {
      restoreSelectedWebsitesFromPreferences();

      initialiseTableControls();
      initialiseDelegatePanel();
   }


   private void initialiseTableControls()
   {
      initialiseFeedbackTypeSelectionButtons();
      initialiseTagsFilterTextField();
      initialiseShowItemsFromButton();
      initialiseRefreshButton();

      initialiseTablePanel();
   }


   private void initialiseTablePanel()
   {
      initialiseTable();
      initialiseTablePanelLayout();
   }


   private void initialiseFeedbackTypeSelectionButtons()
   {
      hotButton.setFont(UIConstants.RegularFont);
      hotButton.setText("Hot");
      hotButton.setFocusable(false);

      newButton.setFont(UIConstants.RegularFont);
      newButton.setText("New");
      newButton.setFocusable(false);

      hotButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleHotButtonActionPerformed();
         }
      });

      newButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleNewButtonActionPerformed();
         }
      });

      final ButtonGroup buttonGroup = new ButtonGroup();
      buttonGroup.add(hotButton);
      buttonGroup.add(newButton);
      buttonGroup.setSelected(hotButton.getModel(), true);
   }


   private void initialiseTagsFilterTextField()
   {
      tagsFilterTextField.setFont(UIConstants.RegularFont);
      tagsFilterTextField.setLabel("Keywords");

      tagsFilterTextField.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleTagsFilterTextFieldActionPerformed();
         }
      });

      final InputMap inputMap = tagsFilterTextField.getInputMap(JComponent.WHEN_FOCUSED);
      final ActionMap actionMap = tagsFilterTextField.getActionMap();

      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelComponent");

      actionMap.put("cancelComponent", new AbstractAction()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleTagsFilterTextFieldActionCancelled();
         }
      });
   }


   private void initialiseShowItemsFromButton()
   {
      showItemsFromButton.setFont(UIConstants.RegularFont);
      showItemsFromButton.setText("Show items from...");

      showItemsFromButton.setToolTipText("Select the websites for which\nfeedback items are displayed.");
      showItemsFromButton.setToolTipDismissDelayMilliseconds(5000);
      showItemsFromButton.setToolTipOffset(new Point(-150, 30));

      showItemsFromButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleShowItemsFromButtonActionPerformed();
         }
      });
   }


   private void initialiseRefreshButton()
   {
      try
      {
         final BufferedImage buttonImage = ImageIO.read(FeedbackPanel.class.getResourceAsStream(FeedbackResources.RefreshIconPath));
         refreshButton.setIcon(new ImageIcon(UIUtilities.getScaledImage(buttonImage, UIConstants.PreferredIconWidth, UIConstants.PreferredIconHeight,
                                                                        RenderingHints.VALUE_INTERPOLATION_BICUBIC)));
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }

      refreshButton.setToolTipText("Refresh the display of feedback items.");
      refreshButton.setToolTipOffset(new Point(-210, 30));

      refreshButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleRefreshButtonActionPerformed();
         }
      });
   }


   private void initialiseTable()
   {
      featuredFeedbackTable.setGridColor(UIConstants.ListCellBorderColour);
      featuredFeedbackTable.setShowGrid(true);

      featuredFeedbackTable.setFillsViewportHeight(true);
      featuredFeedbackTable.setRowHeight(FeaturedItemsTableRowHeight);

      featuredFeedbackTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      featuredFeedbackTable.setDefaultRenderer(Object.class, new PersonalFeedbackGeneralCellRenderer<Object>());

      final JTableHeader tableHeader = featuredFeedbackTable.getTableHeader();
      tableHeader.setFont(UIConstants.RegularFont);
      tableHeader.setReorderingAllowed(false);

      final TableColumn thumbnailColumn = featuredFeedbackTable.getColumnModel().getColumn(PersonalFeedbackFeaturedItemsTableModel.ThumbnailColumnIndex);
      thumbnailColumn.setCellRenderer(new ThumbnailCellRenderer());

      /* Refer to JTable.doLayout() for an explanation of the column resizing algorithms.
       * Enforce a fixed width for the thumbnail column...
       */
      thumbnailColumn.setResizable(false);
      thumbnailColumn.setMinWidth(50);
      thumbnailColumn.setMaxWidth(50);

      // ... and use percentages to distribute the remaining column widths.
      final int remainingColumnWidths = FeaturedItemsTableDimensions.width - 50;

      final TableColumn itemNameColumn = featuredFeedbackTable.getColumnModel().getColumn(PersonalFeedbackFeaturedItemsTableModel.DisplayNameColumnIndex);
      itemNameColumn.setPreferredWidth((int) (0.47f * remainingColumnWidths));

      final TableColumn websiteColumn = featuredFeedbackTable.getColumnModel().getColumn(PersonalFeedbackFeaturedItemsTableModel.WebsiteColumnIndex);
      websiteColumn.setPreferredWidth((int) (0.14f * remainingColumnWidths));

      final TableColumn feedbackSummaryColumn = featuredFeedbackTable.getColumnModel().getColumn(PersonalFeedbackFeaturedItemsTableModel.FeedbackSummaryColumnIndex);
      feedbackSummaryColumn.setPreferredWidth((int) (0.08f * remainingColumnWidths));

      final TableColumn numberOfRatingsColumn = featuredFeedbackTable.getColumnModel().getColumn(PersonalFeedbackFeaturedItemsTableModel.NumberOfRatingsColumnIndex);
      numberOfRatingsColumn.setCellRenderer(new NumberOfRatingsCellRenderer());
      numberOfRatingsColumn.setPreferredWidth((int) (0.12f * remainingColumnWidths));

      final TableColumn firstRatedColumn = featuredFeedbackTable.getColumnModel().getColumn(PersonalFeedbackFeaturedItemsTableModel.FirstRatedColumnIndex);
      firstRatedColumn.setCellRenderer(new FirstRatedTimeCellRenderer());
      firstRatedColumn.setPreferredWidth((int) (0.19f * remainingColumnWidths));

      featuredFeedbackTable.getSelectionModel().addListSelectionListener(new ListSelectionListener()
      {
         @Override
         final public void valueChanged(final ListSelectionEvent listSelectionEvent)
         {
            handleFeedbackSubmissionsTableSelectionChanged();
         }
      });

      featuredFeedbackTable.addMouseListener(new MouseAdapter()
      {
         @Override
         final public void mouseClicked(final MouseEvent mouseEvent)
         {
            if (mouseEvent.getClickCount() == 2)
               handleFeedbackSubmissionsTableMouseDoubleClicked();
         }
      });

      featuredFeedbackTableScrollPane.setViewportView(featuredFeedbackTable);
      featuredFeedbackTableScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      featuredFeedbackTableScrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener()
      {
         @Override
         final public void adjustmentValueChanged(final AdjustmentEvent adjustmentEvent)
         {
            handleScrollPaneAdjustmentValueChanged();
         }
      });
   }


   private void initialiseTablePanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(tablePanel);
      tablePanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(featuredFeedbackTableScrollPane, GroupLayout.PREFERRED_SIZE, FeaturedItemsTableDimensions.width, GroupLayout.PREFERRED_SIZE)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(featuredFeedbackTableScrollPane, GroupLayout.PREFERRED_SIZE, FeaturedItemsTableDimensions.height, GroupLayout.PREFERRED_SIZE)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );
   }


   private void initialiseDelegatePanel()
   {
      initialiseDelegatePanelButtons();
      initialiseDelegateLayout();
   }


   private void initialiseDelegatePanelButtons()
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
   }


   private void initialiseDelegateLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getContentPane());
      delegatePanel.getContentPane().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(panelLayout.createParallelGroup()
            .addGroup(panelLayout.createSequentialGroup()
               .addComponent(hotButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addPreferredGap(ComponentPlacement.UNRELATED)
               .addComponent(newButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addPreferredGap(ComponentPlacement.UNRELATED, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
               .addComponent(tagsFilterTextField, GroupLayout.PREFERRED_SIZE, 120, GroupLayout.PREFERRED_SIZE)
               .addPreferredGap(ComponentPlacement.UNRELATED)
               .addComponent(showItemsFromButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addPreferredGap(ComponentPlacement.UNRELATED)
               .addComponent(refreshButton, GroupLayout.PREFERRED_SIZE, UIConstants.PreferredIconButtonWidth, GroupLayout.PREFERRED_SIZE)
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
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(hotButton)
            .addComponent(newButton)
            .addComponent(tagsFilterTextField)
            .addComponent(showItemsFromButton)
            .addComponent(refreshButton)
         )
         .addPreferredGap(ComponentPlacement.RELATED)
         .addComponent(tablePanel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(viewButton)
            .addComponent(closeButton)
         )
         .addContainerGap()
      );

      panelLayout.linkSize(SwingConstants.VERTICAL, hotButton, newButton, tagsFilterTextField, showItemsFromButton, refreshButton);
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class FeaturedFeedbackItemsTable extends JTable implements ImageLoadRequester
   {
      static final private int MaximumItemThumbnailDimensions = 100;
      static final private int ToolTipLeftDisplayXThreshold = 150;

      final private ImageLoader imageLoader;

      final private SmileyProgressBar loadingProgressBar = new SmileyProgressBar();

      final private ThumbnailToolTip thumbnailToolTip = new ThumbnailToolTip();
      private PersonalFeedbackFeaturedPerson toolTipFeaturedPerson;
      private Point toolTipLocation;


      private FeaturedFeedbackItemsTable(final TableModel tableModel, final ImageLoader imageLoader)
      {
         super(tableModel);

         this.imageLoader = imageLoader;

         initialise();
      }


      private void initialise()
      {
         initialiseLoadingProgressBar();
         initialiseMouseMotionListener();
      }


      private void initialiseLoadingProgressBar()
      {
         loadingProgressBar.setBackground(UIConstants.ProgressBarShadingColour);
         loadingProgressBar.setSize(UIConstants.ProgressBarWidth, UIConstants.ProgressBarHeight);
         loadingProgressBar.setLocation(0, 0);
         loadingProgressBar.setVisible(false);

         add(loadingProgressBar);
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


      @Override
      final public Dimension getPreferredSize()
      {
         if (isLoadingFeedbackItems())
         {
            final Dimension tablePreferredSize = super.getPreferredSize();
            tablePreferredSize.height += (FeaturedItemsTableRowHeight * 3);
            return tablePreferredSize;
         }
         else
            return super.getPreferredSize();
      }


      private void handleSetLoadingFeedbackItems(final boolean isLoading)
      {
         if (isLoading)
         {
            final TableModel tableModel = getModel();
            final int loadingProgressBarXPosition = ((FeaturedItemsTableDimensions.width - loadingProgressBar.getWidth()) / 2);
            final int loadingProgressBarYPosition = ((tableModel.getRowCount() + 1) * FeaturedItemsTableRowHeight);
            loadingProgressBar.setLocation(loadingProgressBarXPosition, loadingProgressBarYPosition);

            loadingProgressBar.setVisible(true);
            loadingProgressBar.setIndeterminate(true);
         }
         else
         {
            loadingProgressBar.setVisible(false);
            loadingProgressBar.setIndeterminate(false);
         }
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
            // Strictly speaking not needed for an unsorted table, but necessary if that changes...
            final int mouseOverRowIndex = convertRowIndexToModel(rowAtPoint);

            final PersonalFeedbackFeaturedItemsTableModel tableModel = (PersonalFeedbackFeaturedItemsTableModel) getModel();
            final PersonalFeedbackFeaturedPerson featuredPerson = tableModel.getFeaturedItem(mouseOverRowIndex);

            if (featuredPerson != toolTipFeaturedPerson)
            {
               // While the tooltip image is loading or even just switching, do not display a tooltip image for a previous item.
               clearToolTip();

               final String photoURL = featuredPerson.personProfile.getThumbnailImageURL();
               if (photoURL != null)
               {
                  toolTipFeaturedPerson = featuredPerson;

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
         if ((toolTipFeaturedPerson != null) && toolTipFeaturedPerson.personProfile.getThumbnailImageURL().equals(imageURL))
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
         if (toolTipFeaturedPerson != null)
         {
            toolTipFeaturedPerson = null;
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


      /****************************************************************************
       *
       ***************************************************************************/


      private boolean isLoadingFeedbackItems()
      {
         return loadingProgressBar.isVisible();
      }


      private void setLoadingFeedbackItems(final boolean isLoading)
      {
         handleSetLoadingFeedbackItems(isLoading);
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


   static final private class ThumbnailCellRenderer extends PersonalFeedbackGeneralCellRenderer<ImageIcon>
   {
      @Override
      final protected void updateValue(final ImageIcon icon)
      {
         setIcon(icon);
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class FirstRatedTimeCellRenderer extends PersonalFeedbackGeneralCellRenderer<Long>
   {
      @Override
      final protected void updateValue(final Long dateAndTime)
      {
         final Calendar displayDateTimeCalendar = Calendar.getInstance();
         displayDateTimeCalendar.setTimeInMillis(dateAndTime);

         final Calendar todayCalendar = Calendar.getInstance();
         final Calendar yesterdayCalendar = Calendar.getInstance();
         yesterdayCalendar.add(Calendar.DAY_OF_YEAR, -1);

         final int displayDateTimeYear = displayDateTimeCalendar.get(Calendar.YEAR);
         final int displayDateTimeDay = displayDateTimeCalendar.get(Calendar.DAY_OF_YEAR);

         final Formatter formatter = new Formatter();

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


   static final private class NumberOfRatingsCellRenderer extends PersonalFeedbackGeneralCellRenderer<Integer>
   {
      @Override
      final protected void updateValue(final Integer numberOfRatings)
      {
         final NumberFormat numberFormat = NumberFormat.getIntegerInstance();
         setValue(numberFormat.format(numberOfRatings.intValue()));
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void handleHotButtonActionPerformed()
   {
      if (! isShowingHotFeedback())
      {
         activeTableTabSelectionButton = hotButton;
         featuredFeedbackTableModel.setFeaturedFeedbackItems(hotItems);
         featuredFeedbackTable.setLoadingFeedbackItems(isLoadingHotItems);

         checkRefreshFeedbackFromUpdatedTags();
      }
   }


   private void handleNewButtonActionPerformed()
   {
      if (! isShowingNewFeedback())
      {
         activeTableTabSelectionButton = newButton;
         featuredFeedbackTableModel.setFeaturedFeedbackItems(newItems);
         featuredFeedbackTable.setLoadingFeedbackItems(isLoadingNewItems);

         checkRefreshFeedbackFromUpdatedTags();
      }
   }


   private void handleTagsFilterTextFieldActionPerformed()
   {
      checkRefreshFeedbackFromUpdatedTags();
   }


   private void handleTagsFilterTextFieldActionCancelled()
   {
      resetTagsFilterTextField();
   }


   private void handleShowItemsFromButtonActionPerformed()
   {
      if ((! isLoadingHotItems) && (! isLoadingNewItems))
      {
         final PersonalFeedbackFeaturedItemsFilterPanel filterPanel = new PersonalFeedbackFeaturedItemsFilterPanel(this, criteriaType, selectedWebsites);
         uiManager.showFormSubcomponent(filterPanel.getDelegate());
      }
   }


   private void handleRefreshButtonActionPerformed()
   {
      if ((! isLoadingHotItems) && (! isLoadingNewItems))
      {
         refreshSelectedTagsFilter();
         clearAllFeaturedFeedback();
         checkFetchActiveFeaturedFeedbackItems();
      }
   }


   private void handleFeedbackSubmissionsTableSelectionChanged()
   {
      viewButton.setEnabled(! featuredFeedbackTable.getSelectionModel().isSelectionEmpty());
   }


   private void handleFeedbackSubmissionsTableMouseDoubleClicked()
   {
      if (! featuredFeedbackTable.getSelectionModel().isSelectionEmpty())
         viewSelectedFeedbackItem();
   }


   private void handleScrollPaneAdjustmentValueChanged()
   {
      final BoundedRangeModel model = featuredFeedbackTableScrollPane.getVerticalScrollBar().getModel();

      if ((model.getExtent() + model.getValue()) == model.getMaximum())
      {
         if (isShowingHotFeedback())
            fetchNextHotFeedbackSample();
         else if (isShowingNewFeedback())
            fetchNextNewFeedbackSample();
      }
   }


   private void handleViewButtonActionPerformed()
   {
      viewSelectedFeedbackItem();
   }


   private void handleCloseButtonActionPerformed()
   {
      uiManager.closeFeaturedFeedbackItemsPanel(this);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private boolean isShowingHotFeedback()
   {
      return (activeTableTabSelectionButton == hotButton);
   }


   private boolean isShowingNewFeedback()
   {
      return (activeTableTabSelectionButton == newButton);
   }


   private void checkRefreshFeedbackFromUpdatedTags()
   {
      if ((! isLoadingHotItems) && (! isLoadingNewItems))
      {
         // Has the tags filter changed?
         if (refreshSelectedTagsFilter())
            clearAllFeaturedFeedback();

         checkFetchActiveFeaturedFeedbackItems();
      }
   }


   private boolean refreshSelectedTagsFilter()
   {
      final boolean wasValidTagsFilter = isValidTagsFilter();
      final Set<String> previousTagsFilter = activeTagsFilter;

      activeTagsFilterString = tagsFilterTextField.getText().trim();
      activeTagsFilter = PersonalFeedbackUtilities.getProcessedTags(activeTagsFilterString);

      return ((wasValidTagsFilter != isValidTagsFilter()) || (! previousTagsFilter.equals(activeTagsFilter)));
   }


   private boolean isValidTagsFilter()
   {
      /* Prevent erroneous tag searches such as those containing invalid words (in this case the activeTagsFilterString grabbed from the
       * filter text field will be non-empty while the activeTagsFilter will be empty), or where there are too many tags specified.
       * A blank tag filter is acceptable.
       */
      return activeTagsFilterString.isEmpty() ||
             ((activeTagsFilter.size() > 0) && (activeTagsFilter.size() <= PersonalFeedbackConstants.MaximumPersonProfileSearchableTags));
   }


   private void resetTagsFilterTextField()
   {
      tagsFilterTextField.setText(activeTagsFilterString);
   }


   private boolean hasRetrievedFirstHotFeedbackItemsSample()
   {
      return (lastRetrievedHotItem.sortValue != FeedbactoryConstants.NoTime);
   }


   private boolean hasRetrievedFirstNewFeedbackItemsSample()
   {
      return (lastRetrievedNewItem.sortValue != FeedbactoryConstants.NoTime);
   }


   private void fetchNextHotFeedbackSample()
   {
      if ((! isLoadingHotItems) && (lastRetrievedHotItem.sortValue != FeedbactoryConstants.EndOfDataLong))
      {
         isLoadingHotItems = true;

         if (isShowingHotFeedback())
            featuredFeedbackTable.setLoadingFeedbackItems(true);

         final PersonalFeedbackPerson lastRetrievedItemID = (lastRetrievedHotItem.personProfile != null) ? lastRetrievedHotItem.personProfile.person : null;
         final CriteriaFeedbackFeaturedItemsFilter filter = new CriteriaFeedbackFeaturedItemsFilter(criteriaType, selectedWebsites, activeTagsFilter,
                                                                                                    lastRetrievedHotItem.sortValue, lastRetrievedItemID);
         uiManager.requestGetHotFeedbackItemsSample(filter);
      }
   }


   private void fetchNextNewFeedbackSample()
   {
      if ((! isLoadingNewItems) && (lastRetrievedNewItem.sortValue != FeedbactoryConstants.EndOfDataLong))
      {
         isLoadingNewItems = true;

         if (isShowingNewFeedback())
            featuredFeedbackTable.setLoadingFeedbackItems(true);

         final PersonalFeedbackPerson lastRetrievedItemID = (lastRetrievedNewItem.personProfile != null) ? lastRetrievedNewItem.personProfile.person : null;
         final CriteriaFeedbackFeaturedItemsFilter filter = new CriteriaFeedbackFeaturedItemsFilter(criteriaType, selectedWebsites, activeTagsFilter,
                                                                                                    lastRetrievedNewItem.sortValue, lastRetrievedItemID);
         uiManager.requestGetNewFeedbackItemsSample(filter);
      }
   }


   private void clearAllFeaturedFeedback()
   {
      clearHotItems();
      clearNewItems();
   }


   private void clearHotItems()
   {
      final long lastRetrievedItemMarker = isValidTagsFilter() ? FeedbactoryConstants.NoTime : FeedbactoryConstants.EndOfDataLong;

      hotItems.clear();
      lastRetrievedHotItem = new PersonalFeedbackFeaturedPerson(lastRetrievedItemMarker);

      if (isShowingHotFeedback())
         featuredFeedbackTableModel.reportFeaturedFeedbackItemsUpdated();
   }


   private void clearNewItems()
   {
      final long lastRetrievedItemMarker = isValidTagsFilter() ? FeedbactoryConstants.NoTime : FeedbactoryConstants.EndOfDataLong;

      newItems.clear();
      lastRetrievedNewItem = new PersonalFeedbackFeaturedPerson(lastRetrievedItemMarker);

      if (isShowingNewFeedback())
         featuredFeedbackTableModel.reportFeaturedFeedbackItemsUpdated();
   }


   private void saveSelectedWebsitesToPreferences()
   {
      final Preferences preferences = Preferences.userNodeForPackage(getClass());

      final String criteriaTypeSelectedWebsitesPreferencesKey = SelectedWebsitesPreferencesKey + Short.toString(criteriaType.value);
      preferences.putInt(criteriaTypeSelectedWebsitesPreferencesKey, PersonalFeedbackWebsiteSet.getElementsBitArray(selectedWebsites));
   }


   private void restoreSelectedWebsitesFromPreferences()
   {
      final Preferences preferences = Preferences.userNodeForPackage(getClass());

      final String criteriaTypeSelectedWebsitesPreferencesKey = SelectedWebsitesPreferencesKey + Short.toString(criteriaType.value);

      // Default of all websites selected (0 bitset for the PersonalFeedbackWebsiteSet) if there's an error.
      final int criteriaTypeSelectedWebsitesPreferencesValue = preferences.getInt(criteriaTypeSelectedWebsitesPreferencesKey, 0);
      selectedWebsites = new PersonalFeedbackWebsiteSet(criteriaTypeSelectedWebsitesPreferencesValue);
   }


   private void viewSelectedFeedbackItem()
   {
      final int modelIndex = featuredFeedbackTable.convertRowIndexToModel(featuredFeedbackTable.getSelectedRow());
      uiManager.showItemInBrowser(featuredFeedbackTableModel.getFeaturedItem(modelIndex).personProfile);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleCheckFetchActiveFeaturedFeedbackItemsSample()
   {
      if (isShowingHotFeedback())
      {
         if (! hasRetrievedFirstHotFeedbackItemsSample())
            fetchNextHotFeedbackSample();
      }
      else if (isShowingNewFeedback())
      {
         if (! hasRetrievedFirstNewFeedbackItemsSample())
            fetchNextNewFeedbackSample();
      }
   }


   private void handleApplyWebsiteFilter(final PersonalFeedbackFeaturedItemsFilterPanel filterPanel, final Set<PersonalFeedbackWebsite> selectedWebsites)
   {
      uiManager.dismissFormSubcomponent(filterPanel.getDelegate());

      final boolean selectedWebsitesChanged = (! this.selectedWebsites.equals(selectedWebsites));

      /* The user may have also edited the tags textfield without applying that new filter before opening
       * the websites selection form. Intuitively the user would probably expect changes to both filters
       * to be applied when the new request is generated after they close the website selection form.
       */
      final boolean selectedTagsFilterChanged = refreshSelectedTagsFilter();

      if (selectedWebsitesChanged)
      {
         this.selectedWebsites = selectedWebsites;
         saveSelectedWebsitesToPreferences();
      }

      if (selectedWebsitesChanged || selectedTagsFilterChanged)
      {
         clearAllFeaturedFeedback();
         checkFetchActiveFeaturedFeedbackItems();
      }
   }


   private void handleReportHotItemsSampleLoaded(final PersonalFeedbackFeaturedItemsSampleResult loadedHotItemsResult)
   {
      if (loadedHotItemsResult.requestStatus == NetworkRequestStatus.OK)
      {
         lastRetrievedHotItem = updateFeaturedItems(loadedHotItemsResult.featuredItemsSample, hotItems, true);
         updateFeaturedItems(loadedHotItemsResult.featuredItemsSample, newItems, false);
      }

      if (isShowingHotFeedback())
      {
         featuredFeedbackTable.setLoadingFeedbackItems(false);
         final int preUpdateSelectedRow = featuredFeedbackTable.getSelectedRow();
         featuredFeedbackTableModel.setFeaturedFeedbackItems(hotItems);
         if (preUpdateSelectedRow != -1)
            featuredFeedbackTable.getSelectionModel().setSelectionInterval(preUpdateSelectedRow, preUpdateSelectedRow);
      }

      isLoadingHotItems = false;
   }


   private void handleReportNewItemsSampleLoaded(final PersonalFeedbackFeaturedItemsSampleResult loadedNewItemsResult)
   {
      if (loadedNewItemsResult.requestStatus == NetworkRequestStatus.OK)
      {
         lastRetrievedNewItem = updateFeaturedItems(loadedNewItemsResult.featuredItemsSample, newItems, true);
         updateFeaturedItems(loadedNewItemsResult.featuredItemsSample, hotItems, false);
      }

      if (isShowingNewFeedback())
      {
         featuredFeedbackTable.setLoadingFeedbackItems(false);
         final int preUpdateSelectedRow = featuredFeedbackTable.getSelectedRow();
         featuredFeedbackTableModel.setFeaturedFeedbackItems(newItems);
         if (preUpdateSelectedRow != -1)
            featuredFeedbackTable.getSelectionModel().setSelectionInterval(preUpdateSelectedRow, preUpdateSelectedRow);
      }

      isLoadingNewItems = false;
   }


   private PersonalFeedbackFeaturedPerson updateFeaturedItems(final List<PersonalFeedbackFeaturedPerson> sourceList, final List<PersonalFeedbackFeaturedPerson> targetList,
                                                              final boolean appendAbsentItems)
   {
      final ListIterator<PersonalFeedbackFeaturedPerson> sourceListIterator = sourceList.listIterator();
      PersonalFeedbackFeaturedPerson sourceListItem = null;

      outer:
      while (sourceListIterator.hasNext())
      {
         sourceListItem = sourceListIterator.next();
         if (sourceListItem.sortValue == FeedbactoryConstants.EndOfDataLong)
            break;

         final ListIterator<PersonalFeedbackFeaturedPerson> targetListIterator = targetList.listIterator();
         PersonalFeedbackFeaturedPerson targetListItem;

         while (targetListIterator.hasNext())
         {
            targetListItem = targetListIterator.next();
            if (targetListItem.personProfile.person.equals(sourceListItem.personProfile.person))
            {
               /* The item has been found amongst the list of previously retrieved items. Update its details, then continue the next iteration of
                * the outer loop to avoid also appending the item to the target list.
                */
               targetListIterator.set(sourceListItem);
               continue outer;
            }
         }

         if (appendAbsentItems)
            targetListIterator.add(sourceListItem);
      }

      return sourceListItem;
   }


   private void handleReportItemProfileDetailsUpdated(final PersonalFeedbackPersonProfile itemProfile)
   {
      updateItemProfile(hotItems, itemProfile, isShowingHotFeedback());
      updateItemProfile(newItems, itemProfile, isShowingNewFeedback());
   }


   private void updateItemProfile(final List<PersonalFeedbackFeaturedPerson> featuredItemList, final PersonalFeedbackPersonProfile itemProfile,
                                  final boolean isItemListActive)
   {
      final ListIterator<PersonalFeedbackFeaturedPerson> featuredItemListIterator = featuredItemList.listIterator();
      PersonalFeedbackFeaturedPerson featuredItem;
      int rowIndex = 0;

      while (featuredItemListIterator.hasNext())
      {
         featuredItem = featuredItemListIterator.next();
         if (featuredItem.personProfile.person.equals(itemProfile.person))
         {
            featuredItemListIterator.set(new PersonalFeedbackFeaturedPerson(itemProfile, featuredItem.feedbackSummary, featuredItem.creationTime, featuredItem.sortValue));
            if (isItemListActive)
               featuredFeedbackTableModel.reportFeaturedFeedbackItemUpdated(rowIndex);

            break;
         }

         rowIndex ++;
      }
   }


   private void handleReportItemBasicFeedbackSummaryUpdated(final PersonalFeedbackPerson item, final PersonalFeedbackBasicSummary basicFeedbackSummary)
   {
      updateBasicFeedbackSummary(hotItems, item, basicFeedbackSummary, isShowingHotFeedback());
      updateBasicFeedbackSummary(newItems, item, basicFeedbackSummary, isShowingNewFeedback());
   }


   private void updateBasicFeedbackSummary(final List<PersonalFeedbackFeaturedPerson> featuredItemList, final PersonalFeedbackPerson targetItem,
                                           final PersonalFeedbackBasicSummary basicFeedbackSummary, final boolean isItemListActive)
   {
      final ListIterator<PersonalFeedbackFeaturedPerson> featuredItemListIterator = featuredItemList.listIterator();
      PersonalFeedbackFeaturedPerson featuredItem;
      int rowIndex = 0;

      while (featuredItemListIterator.hasNext())
      {
         featuredItem = featuredItemListIterator.next();
         if (featuredItem.personProfile.person.equals(targetItem))
         {
            if (basicFeedbackSummary.numberOfRatings > 0)
            {
               featuredItemListIterator.set(new PersonalFeedbackFeaturedPerson(featuredItem.personProfile, basicFeedbackSummary, featuredItem.creationTime, featuredItem.sortValue));
               if (isItemListActive)
                  featuredFeedbackTableModel.reportFeaturedFeedbackItemUpdated(rowIndex);
            }
            else
            {
               /* It's possible for all feedback submissions for an item to have been removed, in which case it should also be removed from
                * the featured items lists.
                */
               featuredItemListIterator.remove();
               if (isItemListActive)
                  featuredFeedbackTableModel.reportFeaturedFeedbackItemRemoved(rowIndex);
            }

            break;
         }

         rowIndex ++;
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }


   final void checkFetchActiveFeaturedFeedbackItems()
   {
      handleCheckFetchActiveFeaturedFeedbackItemsSample();
   }


   final void applyWebsiteFilter(final PersonalFeedbackFeaturedItemsFilterPanel filterPanel, final Set<PersonalFeedbackWebsite> selectedWebsites)
   {
      handleApplyWebsiteFilter(filterPanel, selectedWebsites);
   }


   final void cancelWebsiteFilter(final PersonalFeedbackFeaturedItemsFilterPanel filterPanel)
   {
      uiManager.dismissFormSubcomponent(filterPanel.getDelegate());
   }


   final void reportHotItemsSampleLoaded(final PersonalFeedbackFeaturedItemsSampleResult loadedHotItemsResult)
   {
      handleReportHotItemsSampleLoaded(loadedHotItemsResult);
   }


   final void reportNewItemsSampleLoaded(final PersonalFeedbackFeaturedItemsSampleResult loadedNewItemsResult)
   {
      handleReportNewItemsSampleLoaded(loadedNewItemsResult);
   }


   final void reportItemProfileDetailsUpdated(final PersonalFeedbackPersonProfile itemProfile)
   {
      handleReportItemProfileDetailsUpdated(itemProfile);
   }


   final void reportItemBasicFeedbackSummaryUpdated(final PersonalFeedbackPerson item, final PersonalFeedbackBasicSummary basicFeedbackSummary)
   {
      handleReportItemBasicFeedbackSummaryUpdated(item, basicFeedbackSummary);
   }
}