/* Memos:
 * - I've cut the Terms of Service from the sign up form, since at this early stage there are no terms to agree to!
 *   The wording was: "I accept the Feedbactory Terms of Service, including the fair and reasonable use of providing feedback."
 *
 * - Capturing of country & region is worth considering, but then it's also capturable via IP tracing on the server side, and fairly reliably so at least for users
 *      who are not using proxies, etc.
 *
 * - Consider populating the DOB's year dropdown using the server handshake time, rather than hardcoding it or using local time. Or is that taking things too far?
 *
 * - The DOB dropdowns are each allocated a low maximum (visible) row count because on some Java setups (definitely Win 32 Java 7 on my HP laptop) there is a bug displaying
 *   heavyweight items against lightweight components. The dropdowns will only be rendered as heavyweight if they're long enough to extend off the bottom of the window.
 */

package com.feedbactory.client.ui.useraccount;


import com.feedbactory.client.core.useraccount.AccountUtilities;
import com.feedbactory.client.ui.UIConstants;
import com.feedbactory.client.ui.component.MessageDialog;
import com.feedbactory.client.ui.component.MessageDialog.MessageType;
import com.feedbactory.client.ui.component.MessageDialog.PresetOptionSelection;
import com.feedbactory.client.ui.component.NimbusBorderPanel;
import com.feedbactory.client.ui.component.RoundedPanel;
import com.feedbactory.client.ui.component.SelfLabelledComboBox;
import com.feedbactory.client.ui.component.SelfLabelledTextField;
import com.feedbactory.shared.FeedbactoryConstants;
import com.feedbactory.shared.useraccount.Gender;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.LayoutStyle;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


final class AccountSignUpPanel
{
   final private AccountUIManager parentPanel;

   final private NimbusBorderPanel delegatePanel = createDelegatePanel();

   final private RoundedPanel contentPanel = new RoundedPanel(UIConstants.ContentPanelGradient);

   final private JLabel signUpPromptLineOneLabel = new JLabel();
   final private JLabel signUpPromptLineTwoLabel = new JLabel();

   final private SelfLabelledTextField emailTextField = new SelfLabelledTextField();

   final private SelfLabelledComboBox genderComboBox = new SelfLabelledComboBox();

   final private SelfLabelledComboBox dateOfBirthDayField = new SelfLabelledComboBox();
   final private SelfLabelledComboBox dateOfBirthMonthField = new SelfLabelledComboBox();
   final private SelfLabelledComboBox dateOfBirthYearField = new SelfLabelledComboBox();

   final private JCheckBox sendAnnouncementsCheckBox = new JCheckBox();

   final private JButton signUpButton = new JButton();
   final private JButton cancelButton = new JButton();


   AccountSignUpPanel(final AccountUIManager parent)
   {
      this.parentPanel = parent;

      initialise();
   }


   private NimbusBorderPanel createDelegatePanel()
   {
      final NimbusBorderPanel.Builder builder = new NimbusBorderPanel.Builder("Sign Up");
      builder.setRadialGradientPaint(UIConstants.LighterPanelGradient);
      return new NimbusBorderPanel(builder);
   }


   private void initialise()
   {
      initialiseContentPanel();
      initialiseDelegatePanel();
   }


   private void initialiseContentPanel()
   {
      contentPanel.setBackground(UIConstants.ContentPanelColour);

      initialiseInputFields();
      initialiseContentPanelLayout();
   }


   private void initialiseInputFields()
   {
      signUpPromptLineOneLabel.setFont(UIConstants.RegularFont);
      signUpPromptLineOneLabel.setText("Please enter some basic details to get started");

      signUpPromptLineTwoLabel.setFont(UIConstants.RegularFont);
      signUpPromptLineTwoLabel.setText("with Feedbactory:");

      final DocumentListener documentListener = new DocumentListener()
      {
         @Override
         final public void insertUpdate(final DocumentEvent documentEvent)
         {
            handleInputComponentUpdated();
         }


         @Override
         final public void removeUpdate(final DocumentEvent documentEvent)
         {
            handleInputComponentUpdated();
         }


         @Override
         final public void changedUpdate(final DocumentEvent documentEvent)
         {
            handleInputComponentUpdated();
         }
      };

      final ActionListener signUpActionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleSignUpButtonActionPerformed();
         }
      };

      emailTextField.setFont(UIConstants.RegularFont);
      emailTextField.setLabel("Email");
      emailTextField.getDocument().addDocumentListener(documentListener);
      emailTextField.addActionListener(signUpActionListener);

      initialiseGenderComboBox();

      initialiseDateOfBirthComboBoxes();

      sendAnnouncementsCheckBox.setFont(UIConstants.RegularFont);
      sendAnnouncementsCheckBox.setText("Send important announcements");
      sendAnnouncementsCheckBox.setSelected(true);

      signUpButton.setFont(UIConstants.RegularFont);
      signUpButton.setText("Sign Up");
      signUpButton.setEnabled(false);
      signUpButton.addActionListener(signUpActionListener);

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


   private void initialiseGenderComboBox()
   {
      genderComboBox.addItem(Gender.Male);
      genderComboBox.addItem(Gender.Female);
      genderComboBox.setSelectedIndex(-1);

      genderComboBox.setFont(UIConstants.RegularFont);
      genderComboBox.setLabel("Gender");

      final ActionListener actionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleInputComponentUpdated();
         }
      };

      genderComboBox.addActionListener(actionListener);
   }


   private void initialiseDateOfBirthComboBoxes()
   {
      for (int day = 1; day < 32; day ++)
         dateOfBirthDayField.addItem(day);

      dateOfBirthDayField.setSelectedIndex(-1);

      dateOfBirthDayField.setFont(UIConstants.RegularFont);
      dateOfBirthDayField.setLabel("Date");
      dateOfBirthDayField.setPrototypeDisplayValue("Date");

      // See comment at the top of the class regarding the maximum visible row count.
      dateOfBirthDayField.setMaximumRowCount(7);

      final DateFormatSymbols dateFormatSymbols = DateFormatSymbols.getInstance();
      final String[] months = dateFormatSymbols.getMonths();

      /* Manually restricting the iteration to twelve since there's the possibility that a Locale might force the Calendar to use 13 months.
       * We could also use a GregorianCalendar instance, which forces 12 months only, with SimpleDateFormat. Why don't the DateFormat.getInstance()
       * methods allow you to pass in an initial Calendar to use?
       */
      for (int month = 0; month < 12; month ++)
          dateOfBirthMonthField.addItem(months[month]);

      dateOfBirthMonthField.setSelectedIndex(-1);

      dateOfBirthMonthField.setFont(UIConstants.RegularFont);
      dateOfBirthMonthField.setLabel("Of");
      dateOfBirthMonthField.setMaximumRowCount(7);

      /* For now the years are hardcoded rather than using a local calendar to avoid the annoying possibility that a client could tweak their
       * local time to try to submit a new account with an invalid DOB (it will fail on the server end).
       * Down the track I might consider grabbing the time from the server handshake and using that to populate the DOB year list.
       */
      for (int year = 2014; year >= 1900; year --)
         dateOfBirthYearField.addItem(year);

      dateOfBirthYearField.setSelectedIndex(-1);

      dateOfBirthYearField.setFont(UIConstants.RegularFont);
      dateOfBirthYearField.setLabel("Birth");
      dateOfBirthYearField.setMaximumRowCount(7);

      final ActionListener actionListener = new ActionListener()
      {
         @Override
         final public void actionPerformed(final ActionEvent actionEvent)
         {
            handleInputComponentUpdated();
         }
      };

      dateOfBirthDayField.addActionListener(actionListener);
      dateOfBirthMonthField.addActionListener(actionListener);
      dateOfBirthYearField.addActionListener(actionListener);
   }


   private void initialiseContentPanelLayout()
   {
      final GroupLayout panelLayout = new GroupLayout(contentPanel);
      contentPanel.setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(signUpPromptLineOneLabel)
            .addComponent(signUpPromptLineTwoLabel)
            .addComponent(emailTextField, GroupLayout.PREFERRED_SIZE, 225, GroupLayout.PREFERRED_SIZE)
            .addComponent(genderComboBox, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addGroup(panelLayout.createSequentialGroup()
               .addComponent(dateOfBirthDayField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
               .addPreferredGap(ComponentPlacement.RELATED)
               .addComponent(dateOfBirthMonthField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
               .addPreferredGap(ComponentPlacement.RELATED)
               .addComponent(dateOfBirthYearField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            )
            .addComponent(sendAnnouncementsCheckBox)
         )
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
         .addComponent(signUpPromptLineOneLabel)
         .addPreferredGap(ComponentPlacement.RELATED)
         .addComponent(signUpPromptLineTwoLabel)
         .addPreferredGap(ComponentPlacement.UNRELATED)
         .addComponent(emailTextField)
         .addPreferredGap(ComponentPlacement.RELATED)
         .addComponent(genderComboBox)
         .addPreferredGap(ComponentPlacement.RELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(dateOfBirthDayField)
            .addComponent(dateOfBirthMonthField)
            .addComponent(dateOfBirthYearField)
         )
         .addPreferredGap(ComponentPlacement.UNRELATED)
         .addComponent(sendAnnouncementsCheckBox)
         .addContainerGap(UIConstants.MediumContainerGapSize, Integer.MAX_VALUE)
      );

      panelLayout.linkSize(SwingConstants.HORIZONTAL, genderComboBox, dateOfBirthMonthField);
      panelLayout.linkSize(SwingConstants.HORIZONTAL, dateOfBirthDayField, dateOfBirthYearField);
   }


   private void initialiseDelegatePanel()
   {
      final GroupLayout panelLayout = new GroupLayout(delegatePanel.getContentPane());
      delegatePanel.getContentPane().setLayout(panelLayout);

      panelLayout.setHorizontalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addGroup(panelLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(contentPanel)
            .addGroup(panelLayout.createSequentialGroup()
               .addComponent(signUpButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
               .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
               .addComponent(cancelButton, UIConstants.MinimumButtonWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
            )
         )
         .addContainerGap()
      );

      panelLayout.setVerticalGroup(panelLayout.createSequentialGroup()
         .addContainerGap()
         .addComponent(contentPanel)
         .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
         .addGroup(panelLayout.createParallelGroup()
            .addComponent(signUpButton)
            .addComponent(cancelButton)
          )
         .addContainerGap()
      );
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private void handleInputComponentUpdated()
   {
      signUpButton.setEnabled(areRequiredFieldsFilledIn());
   }


   private boolean areRequiredFieldsFilledIn()
   {
      return (! emailTextField.getText().trim().isEmpty()) &&
             (genderComboBox.getSelectedIndex() != 0) &&
             (dateOfBirthDayField.getSelectedIndex() != 0) &&
             (dateOfBirthMonthField.getSelectedIndex() != 0) &&
             (dateOfBirthYearField.getSelectedIndex() != 0);
   }


   private void handleSignUpButtonActionPerformed()
   {
      if (! areRequiredFieldsFilledIn())
         return;

      final String signUpEmailAddress = emailTextField.getText().trim();

      final long dateOfBirth = getEnteredDateOfBirthAsTimeMilliseconds();

      if (! AccountUtilities.isValidEmail(signUpEmailAddress))
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, new String[] {"Invalid account email address."}, MessageDialog.PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK, true);
      }
      else if (dateOfBirth == FeedbactoryConstants.NoTime)
      {
         final MessageDialog.Builder builder = new MessageDialog.Builder(MessageType.Error, new String[] {"Invalid date of birth."}, MessageDialog.PresetOptionConfiguration.OK);
         parentPanel.showMessageDialog(new MessageDialog(builder), PresetOptionSelection.OK, true);
      }
      else
         parentPanel.signUp(signUpEmailAddress, (Gender) genderComboBox.getSelectedItem(), dateOfBirth, sendAnnouncementsCheckBox.isSelected());
   }


   private long getEnteredDateOfBirthAsTimeMilliseconds()
   {
      final Calendar calendar = Calendar.getInstance();
      calendar.setLenient(false);
      calendar.set(Integer.parseInt(dateOfBirthYearField.getSelectedItem().toString()),
                                    dateOfBirthMonthField.getSelectedIndex() - 1,
                                    Integer.parseInt(dateOfBirthDayField.getSelectedItem().toString()),
                                    0, 0, 0);

      try
      {
         return calendar.getTimeInMillis();
      }
      catch (final Exception illegalDateException)
      {
         return FeedbactoryConstants.NoTime;
      }
   }


   private void handleCancelButtonActionPerformed()                                             
   {
      parentPanel.dismissActiveAccountComponent();
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final JComponent getDelegate()
   {
      return delegatePanel.getDelegate();
   }
}