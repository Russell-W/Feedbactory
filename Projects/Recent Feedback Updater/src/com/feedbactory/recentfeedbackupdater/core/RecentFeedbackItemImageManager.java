/* Memos:
 * - Some code snippets taken almost verbatim or closely adapted from the Feedbactory server's PersonalFeedbackManager.
 */

package com.feedbactory.recentfeedbackupdater.core;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPerson;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;


final class RecentFeedbackItemImageManager
{
   static final private File RegistryDataFile = new File("Data", "Recent feedback images.feedbactory");
   static final private int ImageFilenameLength = 8;

   final private Map<PersonalFeedbackPerson, ManagedItemImage> imageRegistry = new HashMap<>();
   final private Set<String> imageFileNameKeys = new HashSet<>();


   RecentFeedbackItemImageManager()
   {
      initialise();
   }


   private void initialise()
   {
      restoreRegistry();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private void restoreRegistry()
   {
      try
      (
         final DataInputStream registryDataStream = new DataInputStream(new BufferedInputStream(new FileInputStream(RegistryDataFile)));
      )
      {
         imageRegistry.clear();

         PersonalFeedbackPerson item;
         String imageFileNameKey;
         int imageWidth;
         int imageHeight;
         boolean imageStatus;

         final int imageEntryCount = registryDataStream.readInt();
         for (int entryNumber = 0; entryNumber < imageEntryCount; entryNumber ++)
         {
            item = readPersonalFeedbackPerson(registryDataStream);
            imageFileNameKey = registryDataStream.readUTF();
            imageWidth = registryDataStream.readInt();
            imageHeight = registryDataStream.readInt();
            imageStatus = registryDataStream.readBoolean();
            imageRegistry.put(item, new ManagedItemImage(imageFileNameKey, imageWidth, imageHeight, imageStatus));
            imageFileNameKeys.add(imageFileNameKey);
         }
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }
   }


   private PersonalFeedbackPerson readPersonalFeedbackPerson(final DataInputStream dataInputStream) throws IOException
   {
      final short websiteValue = dataInputStream.readShort();
      final PersonalFeedbackWebsite website = PersonalFeedbackWebsite.fromValue(websiteValue);
      if (website == null)
         throw new IllegalArgumentException("Invalid personal feedback website value: " + websiteValue);

      final String personID = dataInputStream.readUTF();
      final byte criteriaTypeValue = dataInputStream.readByte();
      final PersonalFeedbackCriteriaType criteriaType = PersonalFeedbackCriteriaType.fromValue(criteriaTypeValue);

      if (criteriaType == null)
         throw new IllegalArgumentException("Invalid personal feedback criteria type value: " + criteriaTypeValue);
      else if (! website.getCriteriaTypes().contains(criteriaType))
         throw new IllegalArgumentException("Website " + website.getName() + " does not support criteria feedback type: " + criteriaType);

      validateFieldLength("Person ID", personID, PersonalFeedbackConstants.MaximumPersonIDLength);

      return new PersonalFeedbackPerson(website, personID, criteriaType);
   }


   private void validateFieldLength(final String fieldName, final String stringField, final int maximumAllowableLength)
   {
      if ((stringField != null) && (stringField.length() > maximumAllowableLength))
      {
         final String exceptionMessage = "Personal feedback profile length exceeded for field: " + fieldName + ", length was: " + stringField.length();
         throw new IllegalArgumentException(exceptionMessage);
      }
   }


   private void handleSaveRegistry()
   {
      try
      (
         final DataOutputStream registryDataStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(RegistryDataFile)));
      )
      {
         registryDataStream.writeInt(imageRegistry.size());

         ManagedItemImage registeredImage;

         for (final Entry<PersonalFeedbackPerson, ManagedItemImage> registeredImageEntry : imageRegistry.entrySet())
         {
            writePersonalFeedbackPerson(registeredImageEntry.getKey(), registryDataStream);

            registeredImage = registeredImageEntry.getValue();
            registryDataStream.writeUTF(registeredImage.imageFileNameKey);
            registryDataStream.writeInt(registeredImage.imageWidth);
            registryDataStream.writeInt(registeredImage.imageHeight);
            registryDataStream.writeBoolean(registeredImage.isActive);
         }
      }
      catch (final IOException iOException)
      {
         throw new RuntimeException(iOException);
      }
   }


   private void writePersonalFeedbackPerson(final PersonalFeedbackPerson person, final DataOutputStream dataOutputStream) throws IOException
   {
      dataOutputStream.writeShort(person.getWebsite().getID());
      dataOutputStream.writeUTF(person.getItemID());
      dataOutputStream.writeByte(person.getCriteriaType().value);
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private String handleGenerateUniqueImageFileNameKey()
   {
      final String fileNameAlphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
      final StringBuilder fileNameBuilder = new StringBuilder(ImageFilenameLength);

      for (;;)
      {
         for (int characterIndex = 0; characterIndex < ImageFilenameLength; characterIndex ++)
            fileNameBuilder.append(fileNameAlphabet.charAt(ThreadLocalRandom.current().nextInt(fileNameAlphabet.length())));

         if (! imageFileNameKeys.contains(fileNameBuilder.toString()))
            break;
         else
            fileNameBuilder.setLength(0);
      }

      return fileNameBuilder.toString();
   }


   private ManagedItemImage handleAddItemImage(final PersonalFeedbackPerson item, final String imageFileNameKey, final int imageWidth, final int imageHeight, final boolean isActive)
   {
      final ManagedItemImage registeredImage = new ManagedItemImage(imageFileNameKey, imageWidth, imageHeight, isActive);
      imageRegistry.put(item, registeredImage);
      imageFileNameKeys.add(imageFileNameKey);

      return registeredImage;
   }


   private List<ManagedItemImage> handleRemoveInactiveItems()
   {
      final List<ManagedItemImage> matchingItems = new ArrayList<>(imageRegistry.size());

      Iterator<ManagedItemImage> itemImageIterator = imageRegistry.values().iterator();
      ManagedItemImage itemImage;

      while (itemImageIterator.hasNext())
      {
         itemImage = itemImageIterator.next();
         if (! itemImage.isActive)
         {
            matchingItems.add(itemImage);
            itemImageIterator.remove();
            imageFileNameKeys.remove(itemImage.imageFileNameKey);
         }
      }

      return matchingItems;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void saveRegistry()
   {
      handleSaveRegistry();
   }


   final ManagedItemImage getRegisteredItemImage(final PersonalFeedbackPerson item)
   {
      return imageRegistry.get(item);
   }


   final String generateUniqueImageFileNameKey()
   {
      return handleGenerateUniqueImageFileNameKey();
   }


   final ManagedItemImage addItemImage(final PersonalFeedbackPerson item, final String imageFileNameKey, final int imageWidth, final int imageHeight, final boolean isActive)
   {
      return handleAddItemImage(item, imageFileNameKey, imageWidth, imageHeight, isActive);
   }


   final List<ManagedItemImage> removeInactiveItems()
   {
      return handleRemoveInactiveItems();
   }


   final Map<PersonalFeedbackPerson, ManagedItemImage> getItems()
   {
      return new HashMap<>(imageRegistry);
   }
}