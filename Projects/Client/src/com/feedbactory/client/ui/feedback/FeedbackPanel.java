/* Memos:
 * - This is a fairly hefty class but a lot of the material is related to the operation of the feedback item dropdown box, and the logic is more bulky than tricky.
 *   There is functionality for the dropdown list rendering, the item image tooltip, the lock button and its own tooltip, the tooltip for displaying newly
 *   browsed item notifications, and also some plumbing to allow cancelling/confirming selections when there is changed feedback.
 *
 * - The handleApplyFilter method will not correctly handle the first keystroke of 'replaced' text strings in the feedback item combo box, ie. when the user types
 *   over an existing selection. This is due to replacement events being fired as two consecutive events (removeUpdate, insertUpdate) without an intervening keystroke,
 *   and the isEditingURLFilter flag is reset after the first event. The outcome isn't too bad though, more of a delay in filtering the items once the user types
 *   the 2nd character.
 *
 * - Here is where the FeedbackPanelUIComponents are used, where each FeedbackCategoryUIManager defines its own UI component for that purpose. When the user
 *   selects a feedback item from the dropdown box, its category type is used to determine which FeedbackPanelUIComponent must be used to render the feedback
 *   for the item. State need not be preserved by the FeedbackPanelUIComponents as the user switches between different items (and possibly categories), hence
 *   it's fine if the category UI manager creates a new component from scratch each time. In fact it will help with memory consumption, allowing unused
 *   components to be GC'd.
 */

package com.feedbactory.client.ui.feedback;


import com.feedbactory.client.core.ConfigurationManager;
import com.feedbactory.client.core.useraccount.FeedbactoryUserAccount;
import com.feedbactory.client.core.useraccount.AccountEventListener;
import com.feedbactory.client.core.useraccount.AccountSessionManager;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.UIUtilities;
import com.feedbactory.client.ui.component.ImageLoader;
import com.feedbactory.client.ui.component.ImageLoader.ImageLoadRequester;
import com.feedbactory.client.ui.component.*;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionSelection;
import com.feedbactory.client.ui.pad.FeedbactoryPadUIView;
import com.feedbactory.client.ui.pad.PadResources;
import com.feedbactory.shared.feedback.FeedbackCategory;
import com.feedbactory.shared.feedback.FeedbackItem;
import com.feedbactory.shared.feedback.FeedbackItemProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;


final public class FeedbackPanel
{
   static final private String LockFeedbackItemPreferencesKey = "LockFeedbackItem";

   final private FeedbackUIManager feedbackUIManager;

   final private FeedbactoryPadUIView feedbactoryPad;

   private FeedbackPanelUIComponent activeFeedbackComponentUI;
   private JComponent activeFeedbackComponent;

   final private JPanel delegatePanel = new JPanel(null);

   final private FeedbackItemComboBox feedbackItemComboBox;
   final private FeedbackItemComboBoxEditor feedbackItemComboBoxEditor = new FeedbackItemComboBoxEditor();
   private boolean isEditingFeedbackItemFilter;

   // This variable allows an undo selection facility for the combo box, in the case of unsaved feedback.
   private FeedbackItemProfile selectedUndoItemProfile;

   final private ShadowedToolTipButton refreshItemFeedbackButton = new ShadowedToolTipButton();

   final private JToggleButton lockFeedbackItemButton = new ItemSelectionLockToggleButton();

   private UnsavedFeedbackCheckState unsavedFeedbackCheckState = UnsavedFeedbackCheckState.Normal;

   private FeedbactoryUserAccount signedInUserAccount;


   public FeedbackPanel(final AccountSessionManager userAccountManager,
                        final FeedbackUIManager feedbackUIManager,
                        final FeedbactoryPadUIView feedbactoryPad)
   {
      this.feedbackUIManager = feedbackUIManager;
      this.feedbactoryPad = feedbactoryPad;

      feedbackItemComboBox = new FeedbackItemComboBox(new FeedbackPanelComboBoxModel(), feedbactoryPad.getImageLoader());

      initialise(userAccountManager);
   }


   private void initialise(final AccountSessionManager userAccountManager)
   {
      initialiseFeedbackPanel();

      userAccountManager.addUserAccountEventUIListener(new AccountEventListener()
      {
         @Override
         final public void signedInToUserAccount(final FeedbactoryUserAccount userAccount)
         {
            handleSignedInToUserAccount(userAccount);
         }


         @Override
         final public void userAccountDetailsUpdated(final FeedbactoryUserAccount userAccount)
         {
            handleUserAccountDetailsUpdated(userAccount);
         }


         @Override
         final public void signedOutOfUserAccount(final FeedbactoryUserAccount userAccount)
         {
            handleSignedOutOfUserAccount(userAccount);
         }
      });

      restorePreferences();
   }


   private void initialiseFeedbackPanel()
   {
      final JComponent feedbackItemSelectionPanel = initialiseFeedbackItemSelectionPanel();

      activeFeedbackComponent = createDefaultFeedbackPanel();

      final GroupLayout panelLayout = new GroupLayout(delegatePanel);
      delegatePanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
         .addGroup(panelLayout.createSequentialGroup()
            .addContainerGap()
            .addComponent(feedbackItemSelectionPanel)
            .addContainerGap()
         )
         .addComponent(activeFeedbackComponent)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(feedbackItemSelectionPanel)
         .addPreferredGap(ComponentPlacement.RELATED)
         .addComponent(activeFeedbackComponent)
      );
   }


   private JComponent initialiseFeedbackItemSelectionPanel()
   {
      final JComponent feedbackItemSelectionPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

      feedbackItemSelectionPanel.setBackground(UIConstants.ContentPanelColour);

      initialiseFeedbackItemComboBox();

      initialiseRefreshItemFeedbackButton();

      lockFeedbackItemButton.setEnabled(false);

      initialiseFeedbackItemSelectionPanelLayout(feedbackItemSelectionPanel);

      initialiseFeedbackItemSelectionPanelListeners(feedbackItemSelectionPanel);

      feedbackItemSelectionPanel.setFocusable(false);

      return feedbackItemSelectionPanel;
   }


   private void initialiseFeedbackItemComboBox()
   {
      feedbackItemComboBox.setEditor(feedbackItemComboBoxEditor);
      feedbackItemComboBox.setEditable(true);
      feedbackItemComboBox.setRenderer(new FeedbackPanelComboBoxRenderer(feedbackItemComboBox, feedbactoryPad.getImageLoader()));

      feedbackItemComboBox.setFont(UIConstants.MediumFont);
      feedbackItemComboBox.setMaximumRowCount(6);
      feedbackItemComboBox.setEnabled(false);

      final JTextComponent feedbackItemComboBoxEditorComponent = ((JTextComponent) feedbackItemComboBoxEditor.getEditorComponent());

      feedbackItemComboBoxEditorComponent.addKeyListener(new KeyAdapter()
      {
         @Override
         final public void keyPressed(final KeyEvent keyEvent)
         {
            handleFeedbackItemsComboBoxKeyPressed(keyEvent);
         }
      });

      feedbackItemComboBoxEditorComponent.getDocument().addDocumentListener(new DocumentListener()
      {
         @Override
         final public void insertUpdate(final DocumentEvent documentEvent)
         {
            handleApplyFilter(true);
         }


         @Override
         final public void removeUpdate(final DocumentEvent documentEvent)
         {
            handleApplyFilter(false);
         }


         @Override
         final public void changedUpdate(final DocumentEvent documentEvent)
         {
         }
      });

      feedbackItemComboBoxEditorComponent.addFocusListener(new FocusAdapter()
      {
         @Override
         final public void focusLost(final FocusEvent focusEvent)
         {
            handleFeedbackItemFocusLost();
         }
      });

      feedbackItemComboBox.addPopupMenuListener(new PopupMenuListener()
      {
         @Override
         final public void popupMenuWillBecomeVisible(final PopupMenuEvent popupMenuEvent)
         {
            handleFeedbackItemComboBoxPopUpOpened();
         }


         @Override
         final public void popupMenuWillBecomeInvisible(final PopupMenuEvent popupMenuEvent)
         {
            handleFeedbackItemComboBoxPopUpClosed();
         }


         @Override
         final public void popupMenuCanceled(final PopupMenuEvent popupMenuEvent)
         {
         }
      });

      feedbackItemComboBox.addItemListener(new ItemListener()
      {
         @Override
         final public void itemStateChanged(final ItemEvent itemEvent)
         {
            handleFeedbackItemSelectionChanged(itemEvent);
         }
      });
   }


   private void initialiseRefreshItemFeedbackButton()
   {
      refreshItemFeedbackButton.setEnabled(false);

      try
      {
         final BufferedImage buttonImage = ImageIO.read(FeedbackPanel.class.getResourceAsStream(FeedbackResources.RefreshIconPath));
         refreshItemFeedbackButton.setIcon(new ImageIcon(UIUtilities.getScaledImage(buttonImage, UIConstants.PreferredIconWidth, UIConstants.PreferredIconHeight,
                                                                                    RenderingHints.VALUE_INTERPOLATION_BICUBIC)));
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }

      refreshItemFeedbackButton.setToolTipText("Refresh the feedback.");
      refreshItemFeedbackButton.setToolTipOffset(new Point(-100, 30));

      refreshItemFeedbackButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleRefreshItemFeedbackButtonActionPerformed();
         }
      });
   }


   private void initialiseFeedbackItemSelectionPanelLayout(final JComponent feedbackItemSelectionPanel)
   {
      final int minimumFeedbackItemComboBoxWidth = 240;

      final GroupLayout panelLayout = new GroupLayout(feedbackItemSelectionPanel);
      feedbackItemSelectionPanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, UIConstants.MediumContainerGapSize)
         .addComponent(feedbackItemComboBox, minimumFeedbackItemComboBoxWidth, minimumFeedbackItemComboBoxWidth, Integer.MAX_VALUE)
         .addPreferredGap(ComponentPlacement.RELATED)
         .addComponent(refreshItemFeedbackButton, GroupLayout.PREFERRED_SIZE, UIConstants.PreferredIconButtonWidth, GroupLayout.PREFERRED_SIZE)
         .addPreferredGap(ComponentPlacement.RELATED)
         .addComponent(lockFeedbackItemButton, GroupLayout.PREFERRED_SIZE, UIConstants.PreferredIconButtonWidth, GroupLayout.PREFERRED_SIZE)
         .addContainerGap(UIConstants.MediumContainerGapSize, UIConstants.MediumContainerGapSize)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, UIConstants.MediumContainerGapSize)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(feedbackItemComboBox)
            .addComponent(refreshItemFeedbackButton, GroupLayout.PREFERRED_SIZE, UIConstants.PreferredIconButtonHeight, GroupLayout.PREFERRED_SIZE)
            .addComponent(lockFeedbackItemButton, GroupLayout.PREFERRED_SIZE, UIConstants.PreferredIconButtonHeight, GroupLayout.PREFERRED_SIZE)
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, UIConstants.MediumContainerGapSize)
      );

      panelLayout.linkSize(SwingConstants.VERTICAL, feedbackItemComboBox, refreshItemFeedbackButton, lockFeedbackItemButton);
   }


   private void initialiseFeedbackItemSelectionPanelListeners(final JComponent feedbackItemSelectionPanel)
   {
      feedbackItemSelectionPanel.addHierarchyListener(new HierarchyListener()
      {
         @Override
         final public void hierarchyChanged(final HierarchyEvent hierarchyEvent)
         {
            if (((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) && feedbackItemSelectionPanel.isShowing())
               handlePadShown();
         }
      });
   }


   /****************************************************************************
    * 
    * Start feedback item selection combobox
    * 
    ***************************************************************************/


   static final private class FeedbackItemComboBox extends JComboBox implements ImageLoadRequester
   {
      static final private int MaximumItemThumbnailDimensions = 100;

      /* This value is used to ensure that the current item's thumbnail picture is placed within the bounds of the panel, to prevent a heavyweight
       * popup from being spawned and the tooltip shadow from looking a bit rubbish. The value is the minimum horizontal distance that the user's mouse pointer
       * can get to the end of the combo box before the photo tooltip must be placed to the left of the mouse pointer rather than the right.
       */
      static final private int ThumbnailMouseXRepositionThreshold = 65;

      final private FeedbackPanelComboBoxModel model;

      final private ThumbnailToolTip thumbnailToolTip = new ThumbnailToolTip();

      final private ImageLoader imageLoader;

      /* isMouseWithinList flag prevents the thumbnail tooltip from appearing if the user uses the arrow keys (for eg.) instead of the mouse to select different items.
       * It's possible to display the thumbnail in this case but I'd have to make sure that its display location is tied to the list cell bounds
       * (list.getUI().getCellBounds()) rather than the mouse screen location, otherwise the tooltip might be displayed anywhere on the screen (and as a heavyweight
       * window, losing the shadow effect).
       */
      private boolean isMouseWithinList;
      private FeedbackItemProfile mouseoverThumbnailProfile;

      final private List<FeedbackItemProfile> addedItems = new LinkedList<FeedbackItemProfile>();
      private String pendingToolTipText;
      private boolean isAddedItemsNotificationToolTipShowing;


      private FeedbackItemComboBox(final FeedbackPanelComboBoxModel feedbackItemComboBoxModel, final ImageLoader imageLoader)
      {
         super(feedbackItemComboBoxModel);

         this.model = feedbackItemComboBoxModel;

         this.imageLoader = imageLoader;

         initialise();
      }


      private void initialise()
      {
         initialisePopupMenuListeners();
      }


      private void initialisePopupMenuListeners()
      {
         final JPopupMenu popupMenu = (JPopupMenu) getUI().getAccessibleChild(this, 0);
         final JList popupMenuList = (JList) ((ComboPopup) popupMenu).getList();

         /* If we don't deregister the list from the tool tip manager, the mouse events within it (reported to the tool tip manager) will wreak havoc with our scheme of
          * trying to synthesise the tooltip for the parent combo box. We want control of when, what, and where the tooltip is showing.
          */
         ToolTipManager.sharedInstance().unregisterComponent(popupMenuList);

         popupMenu.addPopupMenuListener(new PopupMenuListener()
         {
            @Override
            final public void popupMenuWillBecomeVisible(final PopupMenuEvent popupMenuEvent)
            {
               handlePopupMenuWillBecomeVisible();
            }


            @Override
            final public void popupMenuWillBecomeInvisible(final PopupMenuEvent popupMenuEvent)
            {
               handlePopupMenuWillBecomeInvisible();
            }


            @Override
            final public void popupMenuCanceled(final PopupMenuEvent popupMenuEvent)
            {
            }
         });

         popupMenuList.addMouseListener(new MouseAdapter()
         {
            @Override
            final public void mouseEntered(final MouseEvent mouseEvent)
            {
               handleMouseEntered(popupMenuList);
            }


            @Override
            final public void mouseExited(final MouseEvent mouseEvent)
            {
               handleMouseExited();
            }
         });

         popupMenuList.addListSelectionListener(new ListSelectionListener()
         {
            @Override
            final public void valueChanged(final ListSelectionEvent listSelectionEvent)
            {
               handlePopupListValueChanged(popupMenuList);
            }
         });
      }


      /****************************************************************************
       * 
       ***************************************************************************/


      private void handlePopupMenuWillBecomeVisible()
      {
         // User is about to see the list of new items - no need to notify them of the new items sometime later.
         clearPendingNotifications();
      }


      private void handlePopupMenuWillBecomeInvisible()
      {
         isMouseWithinList = false;
         clearThumbnail();
      }


      private void handleMouseEntered(final JList popupMenuList)
      {
         isMouseWithinList = true;
         updateComboBoxToolTipForSelectedListItem(popupMenuList);
      }


      private void handleMouseExited()
      {
         isMouseWithinList = false;
         clearThumbnail();
      }


      private void handlePopupListValueChanged(final JList popupMenuList)
      {
         if (isPopupVisible() && isMouseWithinList)
            updateComboBoxToolTipForSelectedListItem(popupMenuList);
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private void clearThumbnail()
      {
         mouseoverThumbnailProfile = null;
         setToolTipText(null);
         synthesiseHideComboBoxTooltipEvent();
      }


      private void updateComboBoxToolTipForSelectedListItem(final JList list)
      {
         final int selectedPopupListIndex = list.getSelectedIndex();

         if (selectedPopupListIndex != -1)
         {
            final FeedbackItemProfile itemProfile = model.getElementAt(selectedPopupListIndex);

            final String photoURL = itemProfile.getThumbnailImageURL();
            if (photoURL != null)
            {
               mouseoverThumbnailProfile = itemProfile;

               /* While the tooltip image is loading, don't display a tooltip image for a previous item.
                * For some reason the list tooltip is not susceptible to the null pointer exception issue that
                * affects the table tooltip in FeedbackFromUserPanel.
                */
               setToolTipText(null);

               final BufferedImage thumbnailImage = imageLoader.loadImage(photoURL, MaximumItemThumbnailDimensions, MaximumItemThumbnailDimensions, this);
               if (thumbnailImage != null)
                  processThumbnailImage(thumbnailImage);
            }
            else
               clearThumbnail();
         }
         else
            clearThumbnail();
      }


      @Override
      final public void reportImageLoaded(final String imageURL, final BufferedImage image, final Throwable exception)
      {
         // Check to make sure that the user hasn't moved onto another profile.
         if ((mouseoverThumbnailProfile != null) && mouseoverThumbnailProfile.getThumbnailImageURL().equals(imageURL))
         {
            if (image != null)
               processThumbnailImage(image);
            else
               mouseoverThumbnailProfile = null;
         }
      }


      private void processThumbnailImage(final BufferedImage image)
      {
         final BufferedImage thumbnailScaledImage = UIUtilities.getSquareCroppedScaledImage(image, MaximumItemThumbnailDimensions,
                                                                                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);

         thumbnailToolTip.setImage(thumbnailScaledImage);
         // Setting a different tooltip string will update the location of the tooltip.
         setToolTipText(mouseoverThumbnailProfile.getItem().getItemID().toString());

         /* Why not just use a saved mouse location from a mouse listener to place the tooltip?
          * Well for the combo box list there is no mouse motion listener, only a listener for mouse entry and also a listener to detect when
          * the selected item changes. If the MouseInfo approach proves to be more problematic I can always just replace the
          * list selection listener with a (more expensive) mouse motion listener, and save the mouse pointer location there.
          */
         final Point location = MouseInfo.getPointerInfo().getLocation();
         SwingUtilities.convertPointFromScreen(location, this);

         /* Ensure that the image is being placed within the panel bounds, otherwise a heavyweight tooltip will be spawned and the shadow will look rubbish.
          * Getting this right is more trial and error than an exact science.
          */
         if ((getWidth() - location.x) >= ThumbnailMouseXRepositionThreshold)
            synthesiseShowComboBoxTooltipEvent(location.x + 60, location.y - MaximumItemThumbnailDimensions - 20);
         else
            synthesiseShowComboBoxTooltipEvent(location.x - 150, location.y - MaximumItemThumbnailDimensions - 20);
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private boolean hasNotificationsOtherThan(final FeedbackItemProfile selectedItemProfile)
      {
         return (addedItems.size() > 1) || 
               ((addedItems.size() == 1) && ((selectedItemProfile == null) || (! selectedItemProfile.getItem().equals(addedItems.get(0).getItem()))));
      }


      // Relying on the enclosing class to protect from duplicates.
      private void registerNotificationFor(final FeedbackItemProfile itemProfile)
      {
         addedItems.add(itemProfile);
      }


      private void preparePendingNotifications(final FeedbackItemProfile selectedItemProfile)
      {
         final StringBuilder stringBuilder = new StringBuilder();
         stringBuilder.append("Added:  ");

         if (addedItems.contains(selectedItemProfile))
            stringBuilder.append(UIUtilities.getEllipsisTruncatedString(selectedItemProfile.getFullName(), 50));
         else
            stringBuilder.append(UIUtilities.getEllipsisTruncatedString(addedItems.get(0).getFullName(), 50));

         if (addedItems.size() > 1)
         {
            stringBuilder.append('\n');
            stringBuilder.append("     + ");
            stringBuilder.append(addedItems.size() - 1);
            stringBuilder.append(" more");
         }

         pendingToolTipText = stringBuilder.toString();

         clearPendingNotifications();
      }


      private void clearPendingNotifications()
      {
         addedItems.clear();
      }


      private void installNotificationToolTipListener(final JToolTip itemsAddedNotificationToolTip)
      {
         itemsAddedNotificationToolTip.addHierarchyListener(new HierarchyListener()
         {
            @Override
            final public void hierarchyChanged(final HierarchyEvent hierarchyEvent)
            {
               if (((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) && (! itemsAddedNotificationToolTip.isShowing()))
               {
                  isAddedItemsNotificationToolTipShowing = false;
                  setToolTipText(null);
               }
            }
         });
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private void synthesiseShowComboBoxTooltipEvent(final int mouseXOffset, final int mouseYOffset)
      {
         ToolTipManager.sharedInstance().mouseEntered(new MouseEvent(this, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, mouseXOffset, mouseYOffset, 0, false));
         ToolTipManager.sharedInstance().mouseMoved(new MouseEvent(this, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, mouseXOffset, mouseYOffset, 0, false));
      }


      private void synthesiseHideComboBoxTooltipEvent()
      {
         ToolTipManager.sharedInstance().mouseExited(new MouseEvent(this, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 0, 0, 0, false));
      }


      @Override
      final public JToolTip createToolTip()
      {
         if (isMouseWithinList)
            return thumbnailToolTip;
         else
         {
            final ShadowedTextToolTip itemsAddedNotificationToolTip = new ShadowedTextToolTip(pendingToolTipText);
            installNotificationToolTipListener(itemsAddedNotificationToolTip);

            isAddedItemsNotificationToolTipShowing = true;

            return itemsAddedNotificationToolTip;
         }
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private boolean handleAddIfAbsent(final FeedbackItemProfile itemProfile, final boolean selectItem)
      {
         final boolean wasNewItem = model.addIfAbsent(itemProfile);

         /* The display of pending notifications is different depending on the selected item, so be
          * sure to select the item first if it's been requested.
          */
         if (selectItem)
            model.setSelectedItemByItemID(itemProfile.getItem());

         if (wasNewItem)
         {
            registerNotificationFor(itemProfile);

            if (isShowing())
               flushPendingNotifications();
         }

         return wasNewItem;
      }


      private void handleFlushPendingNotifications()
      {
         if (! isAddedItemsNotificationToolTipShowing)
         {
            final FeedbackItemProfile selectedProfile = getSelectedItem();
            if ((! isMouseWithinList) && hasNotificationsOtherThan(selectedProfile))
            {
               preparePendingNotifications(selectedProfile);
               setToolTipText("");
               synthesiseShowComboBoxTooltipEvent(10, 0);
            }
            else
               clearPendingNotifications();
         }
      }


      /****************************************************************************
       * 
       ***************************************************************************/


      private FeedbackPanelComboBoxModel getFeedbackPanelComboBoxModel()
      {
         return model;
      }


      private JList getPopupList()
      {
         final Object component = getUI().getAccessibleChild(this, 0);
         if (component instanceof ComboPopup)
            return ((ComboPopup) component).getList();

         return null;
      }


      private boolean addIfAbsent(final FeedbackItemProfile itemProfile, final boolean selectItem)
      {
         return handleAddIfAbsent(itemProfile, selectItem);
      }


      @Override
      final public FeedbackItemProfile getSelectedItem()
      {
         return model.getSelectedItem();
      }


      private void setSelectedItemByItemID(final FeedbackItem itemID)
      {
         model.setSelectedItemByItemID(itemID);
      }


      private void flushPendingNotifications()
      {
         handleFlushPendingNotifications();
      }
   }


   /****************************************************************************
    * 
    * End feedback item selection combobox
    * 
    ***************************************************************************/


   static final private class FeedbackItemComboBoxEditor extends BasicComboBoxEditor
   {
      private boolean isApplyingFilter;


      private void setApplyingFilter(final boolean isApplyingFilter)
      {
         this.isApplyingFilter = isApplyingFilter;
      }


      @Override
      final protected JTextField createEditorComponent()
      {
         final JTextField textField = new JTextField();

         // Set name to enable Nimbus styling.
         textField.setName("ComboBox.textField");
         return textField;
      }


      @Override
      final public void setItem(final Object anObject)
      {
         if (! isApplyingFilter)
         {
            editor.setText((anObject != null) ? anObject.toString() : "");
            editor.setCaretPosition(0);
         }
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


      private void setImage(final BufferedImage image)
      {
         label.setIcon(new ImageIcon(image));
      }
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   static final private class ItemSelectionLockToggleButton extends JToggleButton implements HierarchyListener
   {
      final private ImageIcon lockButtonLockedImage = new ImageIcon();
      final private ImageIcon lockButtonUnlockedImage = new ImageIcon();

      private boolean isToolTipShowing;
      private Point toolTipLocation;


      private ItemSelectionLockToggleButton()
      {
         initialise();
      }


      private void initialise()
      {
         initialiseButtonImages();

         final StringBuilder toolTipText = new StringBuilder();
         toolTipText.append("Lock to prevent the automatic selection\n");
         toolTipText.append("of items as you browse.");

         setToolTipText(toolTipText.toString());

         addActionListener(new ActionListener()
         {
            @Override
            final public void actionPerformed(final ActionEvent actionEvent)
            {
               refreshButtonImage();
            }
         });
      }


      private void initialiseButtonImages()
      {
         try
         {
            final BufferedImage lockedImage = ImageIO.read(FeedbackPanel.class.getResourceAsStream(FeedbackResources.LockedIconPath));
            lockButtonLockedImage.setImage(UIUtilities.getScaledImage(lockedImage, UIConstants.PreferredIconWidth,
                                                                      UIConstants.PreferredIconHeight,
                                                                      RenderingHints.VALUE_INTERPOLATION_BICUBIC));
            final BufferedImage unlockedImage = ImageIO.read(FeedbackPanel.class.getResourceAsStream(FeedbackResources.UnlockedIconPath));
            lockButtonUnlockedImage.setImage(UIUtilities.getScaledImage(unlockedImage, UIConstants.PreferredIconWidth,
                                                                        UIConstants.PreferredIconHeight,
                                                                        RenderingHints.VALUE_INTERPOLATION_BICUBIC));
         }
         catch (final IOException ioException)
         {
            throw new RuntimeException(ioException);
         }

         refreshButtonImage();
      }


      /****************************************************************************
       *
       ***************************************************************************/


      private void refreshButtonImage()
      {
         setIcon(isSelected() ? lockButtonLockedImage : lockButtonUnlockedImage);
      }


      @Override
      final public Point getToolTipLocation(final MouseEvent mouseEvent)
      {
         if (! isToolTipShowing)
            toolTipLocation = new Point(mouseEvent.getX() - 225, mouseEvent.getY() + 30);

         return new Point(toolTipLocation);
      }


      @Override
      final public JToolTip createToolTip()
      {
         final ShadowedTextToolTip toolTip = new ShadowedTextToolTip(getToolTipText());
         toolTip.setDismissDelay(10000);
         toolTip.addHierarchyListener(this);

         return toolTip;
      }


      @Override
      final public void hierarchyChanged(final HierarchyEvent hierarchyEvent)
      {
         if ((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0)
            isToolTipShowing = ((JToolTip) hierarchyEvent.getSource()).isShowing();
      }
   }


   /****************************************************************************
    * 
    * 
    * 
    ***************************************************************************/


   static private enum UnsavedFeedbackCheckState
   {
      Normal,
      UnsavedFeedbackOnCheckAtItemChange,
      UnsavedFeedbackOnCheckByPad;
   }


   /****************************************************************************
    *
    *
    *
    ***************************************************************************/


   private void savePreferences()
   {
      final Preferences preferences = Preferences.userNodeForPackage(getClass());
      preferences.putBoolean(LockFeedbackItemPreferencesKey, lockFeedbackItemButton.isSelected());
   }


   private void restorePreferences()
   {
      final Preferences preferences = Preferences.userNodeForPackage(getClass());
      lockFeedbackItemButton.setSelected(preferences.getBoolean(LockFeedbackItemPreferencesKey, false));
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleSignedInToUserAccount(final FeedbactoryUserAccount userAccount)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            signedInUserAccount = userAccount;

            if (activeFeedbackComponentUI != null)
               activeFeedbackComponentUI.signedInToUserAccount(signedInUserAccount);
         }
      });
   }


   private void handleUserAccountDetailsUpdated(final FeedbactoryUserAccount userAccount)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            signedInUserAccount = userAccount;

            if (activeFeedbackComponentUI != null)
               activeFeedbackComponentUI.userAccountDetailsUpdated(userAccount);
         }
      });
   }


   private void handleSignedOutOfUserAccount(final FeedbactoryUserAccount userAccount)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         @Override
         final public void run()
         {
            signedInUserAccount = null;

            if (activeFeedbackComponentUI != null)
               activeFeedbackComponentUI.signedOutOfUserAccount(userAccount);
         }
      });
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void handleFeedbackItemsComboBoxKeyPressed(final KeyEvent keyEvent)
   {
      isEditingFeedbackItemFilter = false;

      final int keyCode = keyEvent.getKeyCode();

      if (keyCode == KeyEvent.VK_UP)
         processURLEntryUpKeyPressed(keyEvent);
      else if (keyCode == KeyEvent.VK_DOWN)
         processURLEntryDownKeyPressed(keyEvent);
      else if (keyCode == KeyEvent.VK_PAGE_UP)
         processURLEntryPageUpKeyPressed(keyEvent);
      else if (keyCode == KeyEvent.VK_PAGE_DOWN)
         processURLEntryPageDownKeyPressed(keyEvent);
      else if (keyCode == KeyEvent.VK_ENTER)
         processURLEntryEnterKeyPressed(keyEvent);
      else if (keyCode == KeyEvent.VK_ESCAPE)
         ; // Let the Escape operation be handled as normal, without consuming the key event.
      else // User is probably editing text.
         isEditingFeedbackItemFilter = true;
   }


   private void processURLEntryUpKeyPressed(final KeyEvent keyEvent)
   {
      if (feedbackItemComboBox.isPopupVisible())
      {
         final JList popUpList = feedbackItemComboBox.getPopupList();

         if (popUpList.getSelectedIndex() > 0)
         {
            popUpList.setSelectedIndex(popUpList.getSelectedIndex() - 1);
            feedbackItemComboBoxEditor.setItem(popUpList.getSelectedValue());
         }
      }
      else
         feedbackItemComboBox.setPopupVisible(true);

      keyEvent.consume();
   }


   private void processURLEntryDownKeyPressed(final KeyEvent keyEvent)
   {
      if (feedbackItemComboBox.isPopupVisible())
      {
         final JList popUpList = feedbackItemComboBox.getPopupList();
         if (popUpList.getSelectedIndex() < (popUpList.getModel().getSize() - 1))
         {
            popUpList.setSelectedIndex(popUpList.getSelectedIndex() + 1);
            feedbackItemComboBoxEditor.setItem(popUpList.getSelectedValue());
         }
      }
      else
         feedbackItemComboBox.setPopupVisible(true);

      keyEvent.consume();
   }


   private void processURLEntryPageUpKeyPressed(final KeyEvent keyEvent)
   {
      if (feedbackItemComboBox.isPopupVisible())
      {
         final JList popUpList = feedbackItemComboBox.getPopupList();
         if (popUpList.getSelectedIndex() > 0)
         {
            int newListIndex = popUpList.getSelectedIndex() - (feedbackItemComboBox.getMaximumRowCount() - 1);
            if (newListIndex < 0)
               newListIndex = 0;

            popUpList.setSelectedIndex(newListIndex);
            feedbackItemComboBoxEditor.setItem(popUpList.getSelectedValue());
         }
      }
      else
         feedbackItemComboBox.setPopupVisible(true);

      keyEvent.consume();
   }


   private void processURLEntryPageDownKeyPressed(final KeyEvent keyEvent)
   {
      if (feedbackItemComboBox.isPopupVisible())
      {
         final JList popUpList = feedbackItemComboBox.getPopupList();
         final int maximumListIndex = (popUpList.getModel().getSize() - 1);

         if (popUpList.getSelectedIndex() < maximumListIndex)
         {
            int newListIndex = popUpList.getSelectedIndex() + (feedbackItemComboBox.getMaximumRowCount() - 1);
            if (newListIndex > maximumListIndex)
               newListIndex = maximumListIndex;

            popUpList.setSelectedIndex(newListIndex);
            feedbackItemComboBoxEditor.setItem(popUpList.getSelectedValue());
         }
      }
      else
         feedbackItemComboBox.setPopupVisible(true);

      keyEvent.consume();
   }


   private void processURLEntryEnterKeyPressed(final KeyEvent keyEvent)
   {
      if (feedbackItemComboBox.isPopupVisible())
      {
         final JList popUpList = feedbackItemComboBox.getPopupList();
         final int selectionIndex = popUpList.getSelectedIndex();
         if (selectionIndex != -1)
         {
            if (selectionIndex != feedbackItemComboBox.getSelectedIndex())
               feedbackItemComboBox.setSelectedIndex(selectionIndex);
            else
               revertFeedbackItemComboBoxToSelection();
         }

         feedbackItemComboBox.setPopupVisible(false);
      }
      else
         revertFeedbackItemComboBoxToSelection();

      keyEvent.consume();
   }


   private void revertFeedbackItemComboBoxToSelection()
   {
      feedbackItemComboBox.getFeedbackPanelComboBoxModel().clearFilter();

      if (feedbackItemComboBox.getSelectedItem() != null)
         feedbackItemComboBoxEditor.setItem(feedbackItemComboBox.getSelectedItem());
      else
         feedbackItemComboBoxEditor.setItem("");
   }


   private void handleApplyFilter(final boolean isNarrowingSearch)
   {
      if (isEditingFeedbackItemFilter)
      {
         feedbackItemComboBoxEditor.setApplyingFilter(true);
         final JTextComponent textComponent = (JTextComponent) feedbackItemComboBoxEditor.getEditorComponent();
         final boolean haveContentsChanged = feedbackItemComboBox.getFeedbackPanelComboBoxModel().applyFilter(textComponent.getText().trim(), isNarrowingSearch);
         feedbackItemComboBoxEditor.setApplyingFilter(false);

         if (haveContentsChanged)
         {
            // Need to temporarily hide the popup to refresh it to the correct size.
            feedbackItemComboBox.setPopupVisible(false);
         }

         feedbackItemComboBox.setPopupVisible(true);

         isEditingFeedbackItemFilter = false;
      }
   }


   private void handleFeedbackItemFocusLost()
   {
      /* Reset the editing flag to false as soon as the focus is lost, to prevent the possibility of firing further applyFilter calls
       * if/when the combo box text is changed by means other than the user. If the isEditingFeedbackItemFilter is still true (eg. from
       * the user last pressing an editing key or even a home/end arrow key) this will ultimately lead to the setPopupVisible(true)
       * being called, which will produce an error if the combobox itself is not currently visible. This is possible when for example
       * the user is viewing items on the recent feedback panel.
       */
      isEditingFeedbackItemFilter = false;
   }


   private void handleFeedbackItemComboBoxPopUpOpened()
   {
      if (! isEditingFeedbackItemFilter)
      {
         final JTextComponent editorComponent = ((JTextComponent) feedbackItemComboBoxEditor.getEditorComponent());
         editorComponent.setCaretPosition(editorComponent.getDocument().getLength());
         editorComponent.moveCaretPosition(0);
      }
   }


   private void handleFeedbackItemComboBoxPopUpClosed()
   {
      if (! isEditingFeedbackItemFilter)
         revertFeedbackItemComboBoxToSelection();
   }


   private void handleFeedbackItemSelectionChanged(final ItemEvent itemEvent)
   {
      if ((selectedUndoItemProfile != null) && (feedbackItemComboBox.getSelectedItem() != null) &&
          selectedUndoItemProfile.getItem().equals(feedbackItemComboBox.getSelectedItem().getItem()))
      {
         /* This case will only occur when the selected item's profile details have been updated since first being browsed, eg. the display name.
          * Since the item profile is no longer 'equal', the item selection changed event is fired.
          * In this circumstance, no action should be taken since the item hasn't really been changed, however the selectedUndoItemProfile should
          * be updated to reference the updated item profile.
          */
         if (itemEvent.getStateChange() == ItemEvent.SELECTED)
            selectedUndoItemProfile = feedbackItemComboBox.getSelectedItem();
      }
      else if (unsavedFeedbackCheckState == UnsavedFeedbackCheckState.Normal)
      {
         /* Set a flag unsavedFeedbackCheckState when the user has been prompted to decide whether or not to discard changed feedback for 'selectedUndoItemProfile'.
          * The flag is also set when the pad requests a check for whether or not feedback has changed (eg. on shutdown or sign out), for which I need to report
          * the user's decision back to the pad. However it would be bad to have multiple identical prompts stacked on top of one another, so the panel uses the
          * same prompt, and the unsavedFeedbackCheckState flag to indicate the scope for reporting the user selection.
          *
          * Possible values:
          * UnsavedFeedbackCheckState.Normal means that no 'confirm/cancel discard changed feedback' notification is pending.
          * UnsavedFeedbackCheckState.UnsavedFeedbackOnCheckAtItemChange means that a local confirm/cancel (ie. undo) notification is pending.
          * and UnsavedFeedbackCheckState.UnsavedFeedbackOnCheckByPad means that a confirm/cancel discard changed feedback notification is pending and the result must
          * be reported back to the parent FeedbactoryPad.
          *
          * In any case once the user's decision has been made, and the feedback item combo box set, the flag is reset to Normal. When the
          * feedback item combo box is being set (or reset to the original item), I need to suspend repeated checks on the changed feedback since this is
          * really part of an undo or confirm mechanism. A state of non-normal means that an unsaved feedback decision prompt is already active, and I should
          * suppress any further checks and prompts here.
          */
         if (itemEvent.getStateChange() == ItemEvent.SELECTED)
         {
            final FeedbackItemProfile itemProfile = feedbackItemComboBox.getSelectedItem();
            updateForSelectedItem(itemProfile);
         }
         else if (itemEvent.getStateChange() == ItemEvent.DESELECTED)
            promptIfUnsavedFeedback(UnsavedFeedbackCheckState.UnsavedFeedbackOnCheckAtItemChange);
      }
   }


   private void handleRefreshItemFeedbackButtonActionPerformed()
   {
      if (activeFeedbackComponentUI != null)
         activeFeedbackComponentUI.refreshItem();
   }


   private void handlePadShown()
   {
      feedbackItemComboBox.flushPendingNotifications();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private boolean hasFeedbackBeenUpdated()
   {
      return (activeFeedbackComponentUI != null) ? activeFeedbackComponentUI.hasItemFeedbackSubmissionBeenUpdated() : false;
   }


   private boolean promptIfUnsavedFeedback(final UnsavedFeedbackCheckState userSelectionReportingLevel)
   {
      if (hasFeedbackBeenUpdated())
      {
         unsavedFeedbackCheckState = userSelectionReportingLevel;

         // High priority, to ensure that the dialog will be placed before any other active message dialogs.
         feedbactoryPad.showHighPriorityMessageDialog(createDiscardFeedbackConfirmationDialog(selectedUndoItemProfile.getFullName()), PresetOptionSelection.Cancel, true);

         return true;
      }
      else
         return false;
   }


   private boolean canApplicationChangeItemSelection()
   {
      return (! (lockFeedbackItemButton.isSelected() || hasFeedbackBeenUpdated()));
   }


   private void updateForSelectedItem(final FeedbackItemProfile itemProfile)
   {
      if (itemProfile != null)
      {
         /* A new feedback item has been selected, and here is where the panel selects the FeedbackPanelUIComponent that must be used to render
          * both the item and its feedback. If the category of the item is the same as the previous item, there is no work to except to call
          * the feedback component UI's showItem() method. Otherwise I need to place a call to the category UI manager to fetch (maybe allocate anew)
          * the feedback component, which can then be snapped into this parent panel.
          * Note that when the UI components change, I must assume initial statelessness so as well as telling it to render an item I also need to tell
          * it whether or not the user is signed into a Feedbactory account.
          */
         final FeedbackItem feedbackItem = itemProfile.getItem();

         if ((activeFeedbackComponentUI == null) || (activeFeedbackComponentUI.getFeedbackCategory() != feedbackItem.getFeedbackCategory()))
         {
            deactivateActiveFeedbackComponentUI();

            final FeedbackPanelUIComponent componentToActivate = activateFeedbackCategoryComponentUI(feedbackItem);

            if (signedInUserAccount != null)
               componentToActivate.signedInToUserAccount(signedInUserAccount);

            componentToActivate.showItem(feedbackItem);

            displayFeedbackPanelComponent(componentToActivate.getDelegate());

            activeFeedbackComponentUI = componentToActivate;
         }
         else
            activeFeedbackComponentUI.showItem(feedbackItem);

         feedbackItemComboBox.setEnabled(true);
         refreshItemFeedbackButton.setEnabled(true);
         lockFeedbackItemButton.setEnabled(true);
      }
      else if (activeFeedbackComponentUI != null)
      {
         deactivateActiveFeedbackComponentUI();

         // No item is selected - revert to the default feedback display.
         displayFeedbackPanelComponent(createDefaultFeedbackPanel());

         activeFeedbackComponentUI = null;

         refreshItemFeedbackButton.setEnabled(false);
         lockFeedbackItemButton.setEnabled(false);
      }

      selectedUndoItemProfile = itemProfile;
   }


   private FeedbackPanelUIComponent activateFeedbackCategoryComponentUI(final FeedbackItem feedbackItem)
   {
      final FeedbackCategoryUIRegistry feedbackUIRegistry = feedbackUIManager.getFeedbackCategoryUIRegistry();
      final FeedbackCategoryUIManager feedbackCategoryUIManager = feedbackUIRegistry.getFeedbackCategoryUIHandler(feedbackItem.getFeedbackCategory());
      return feedbackCategoryUIManager.activateFeedbackPanelComponent(this);
   }


   private void deactivateActiveFeedbackComponentUI()
   {
      if (activeFeedbackComponentUI != null)
      {
         final FeedbackCategoryUIRegistry feedbackUIRegistry = feedbackUIManager.getFeedbackCategoryUIRegistry();
         final FeedbackCategoryUIManager feedbackCategoryUIManager = feedbackUIRegistry.getFeedbackCategoryUIHandler(activeFeedbackComponentUI.getFeedbackCategory());
         feedbackCategoryUIManager.deactivateFeedbackPanelComponent(activeFeedbackComponentUI);
      }
   }


   private void displayFeedbackPanelComponent(final JComponent componentToActivate)
   {
      final GroupLayout panelLayout = (GroupLayout) delegatePanel.getLayout();
      panelLayout.replace(activeFeedbackComponent, componentToActivate);

      activeFeedbackComponent = componentToActivate;

      feedbactoryPad.requestRepack();
   }


   private JComponent createDefaultFeedbackPanel()
   {
      final String[] dialogMessage = new String[] {"To get started with Feedbactory, simply browse",
                                                   "for products, services, or other items from any of",
                                                   "the popular supported websites.",
                                                   "",
                                                   "When a browsed item is recognised, it will appear",
                                                   "on the toolbar and in the dropdown list above,",
                                                   "allowing you to view feedback from others as well",
                                                   "as submit your own.",
                                                   "",
                                                   "Use the " + (ConfigurationManager.isRunningMacOSX ? "Option" : "Alt") + " key to hide or show this window."
                                                  };

      final JButton recentFeedbackButton = new JButton("Browse recent feedback");
      recentFeedbackButton.setFont(UIConstants.RegularFont);

      recentFeedbackButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            /* This is one instance where I'm referencing a specific feedback category and type, in this case for the sake of providing
             * the user with a link to example recent feedback. This feature is fairly important, so happy to sacrifice the purity
             * of this otherwise general package for the sake of it.
             */
            final FeedbackMenuItem viewPhotographyFeedbackMenuItem = new FeedbackMenuItem(FeedbackCategory.Personal, "", Byte.toString(PersonalFeedbackCriteriaType.Photography.value));
            feedbackUIManager.invokeFeedbackMenuItem(viewPhotographyFeedbackMenuItem);
         }
      });

      final JButton helpButton = new JButton("More help");
      helpButton.setFont(UIConstants.RegularFont);
      helpButton.setIconTextGap(8);

      try
      {
         final BufferedImage buttonImage = ImageIO.read(FeedbackPanel.class.getResourceAsStream(PadResources.HelpButtonImagePath));
         helpButton.setIcon(new ImageIcon(UIUtilities.getScaledImage(buttonImage, UIConstants.PreferredIconWidth,
                                                                      UIConstants.PreferredIconHeight,
                                                                      RenderingHints.VALUE_INTERPOLATION_BICUBIC)));
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }

      helpButton.addActionListener(new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            feedbactoryPad.showHelpPanel();
         }
      });

      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Information, dialogMessage);
      builder.setInputComponents(new JComponent[] {recentFeedbackButton, helpButton});

      return new MessageDialog(builder).getDelegate();
   }


   private MessageDialog createDiscardFeedbackConfirmationDialog(final String displayName)
   {
      final String[] message = new String[] {"Discard changed feedback for", displayName + '?'};
      final MessageDialog.Builder builder = new MessageDialog.Builder(MessageDialog.MessageType.Question, message, MessageDialog.PresetOptionConfiguration.OKCancel);

      final MessageDialog dialog = new MessageDialog(builder);

      dialog.addActionListener(new MessageDialog.ActionListener()
      {
         @Override
         final public void actionPerformed(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection optionSelection, final int optionSelectionIndex)
         {
            handleDiscardChangedFeedbackActionPerformed(optionSelection);
         }
      });

      return dialog;
   }


   private void handleDiscardChangedFeedbackActionPerformed(final MessageDialog.PresetOptionSelection optionSelection)
   {
      if (optionSelection == MessageDialog.PresetOptionSelection.OK)
         handleConfirmDiscardChangedFeedback();
      else if (optionSelection == MessageDialog.PresetOptionSelection.Cancel)
         handleCancelDiscardChangedFeedback();

      unsavedFeedbackCheckState = UnsavedFeedbackCheckState.Normal;
   }


   private void handleCancelDiscardChangedFeedback()
   {
      // Undo the selection on the combo box, and reselect the previously active item.
      feedbackItemComboBox.setSelectedItemByItemID(selectedUndoItemProfile.getItem());

      if (unsavedFeedbackCheckState == UnsavedFeedbackCheckState.UnsavedFeedbackOnCheckByPad)
         feedbactoryPad.cancelDiscardUnsavedFeedback();
   }


   private void handleConfirmDiscardChangedFeedback()
   {
      // Go ahead with the handling of the new selection on the combo box.
      updateForSelectedItem(feedbackItemComboBox.getSelectedItem());

      if (unsavedFeedbackCheckState == UnsavedFeedbackCheckState.UnsavedFeedbackOnCheckByPad)
         feedbactoryPad.confirmDiscardUnsavedFeedback();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private boolean handleReportBrowsedItem(final FeedbackItemProfile itemProfile, final boolean requestSelectItem)
   {
      // NullPointerException and no action taken if itemProfile is null.
      final boolean forceSelectItem = (feedbackItemComboBox.getSelectedItem() == null) || (requestSelectItem && canApplicationChangeItemSelection());
      return feedbackItemComboBox.addIfAbsent(itemProfile, forceSelectItem);
   }


   private void handleApplicationSetSelectedItemByItemID(final FeedbackItem itemID)
   {
      if (canApplicationChangeItemSelection())
         feedbackItemComboBox.setSelectedItemByItemID(itemID);
   }


   private void handleUserSetSelectedItemByItemID(final FeedbackItem itemID)
   {
      feedbackItemComboBox.setSelectedItemByItemID(itemID);
   }


   private boolean handlePromptIfUnsavedFeedback()
   {
      if (unsavedFeedbackCheckState == UnsavedFeedbackCheckState.UnsavedFeedbackOnCheckByPad)
         return true;
      else if (unsavedFeedbackCheckState == UnsavedFeedbackCheckState.UnsavedFeedbackOnCheckAtItemChange)
      {
         /* Elevate the unsaved feedback check flag to indicate that the user's decision on the message prompt must now be reported to the parent pad.
          * ie. it's no longer a local cancel dropdown selection and reselect previous item operation.
          */
         unsavedFeedbackCheckState = UnsavedFeedbackCheckState.UnsavedFeedbackOnCheckByPad;
         return true;
      }
      else
         return promptIfUnsavedFeedback(UnsavedFeedbackCheckState.UnsavedFeedbackOnCheckByPad);
   }


   private void handleShutdown()
   {
      deactivateActiveFeedbackComponentUI();
      savePreferences();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final boolean reportBrowsedItem(final FeedbackItemProfile itemProfile, final boolean requestSelectItem)
   {
      return handleReportBrowsedItem(itemProfile, requestSelectItem);
   }


   final void applicationRequestSelectFeedbackItem(final FeedbackItem itemID)
   {
      handleApplicationSetSelectedItemByItemID(itemID);
   }


   final void userRequestSelectFeedbackItem(final FeedbackItem itemID)
   {
      handleUserSetSelectedItemByItemID(itemID);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public ImageLoader getImageLoader()
   {
      return feedbactoryPad.getImageLoader();
   }


   final public void requestRepack()
   {
      feedbactoryPad.requestRepack();
   }


   final public void showMessageDialog(final MessageDialog messageDialog, final MessageDialog.PresetOptionSelection defaultAction, final boolean actionOnDialogHidden)
   {
      feedbactoryPad.showMessageDialog(messageDialog, defaultAction, actionOnDialogHidden);
   }


   final public void showAccountPanel()
   {
      feedbactoryPad.showAccountPanel();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public JComponent getDelegate()
   {
      return delegatePanel;
   }


   final public boolean promptIfUnsavedFeedback()
   {
      return handlePromptIfUnsavedFeedback();
   }


   final public void shutdown()
   {
      handleShutdown();
   }
}