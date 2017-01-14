
package com.feedbactory.shared.feedback.personal.service;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackCriteriaType;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackPersonProfile;
import com.feedbactory.shared.feedback.personal.PersonalFeedbackWebsite;
import java.util.Collections;
import java.util.Set;


final public class OneX extends PersonalFeedbackWebsite
{
   static final public OneX instance = new OneX();

   static final private Set<String> hostSuffixes = Collections.singleton("1x.com");
   static final private Set<PersonalFeedbackCriteriaType> criteriaTypes = Collections.singleton(PersonalFeedbackCriteriaType.Photography);


   private OneX()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   private String handleGetResolvedShortNameFor(final String nameElements)
   {
      final int nameEndIndex = nameElements.indexOf('\0');
      if (nameEndIndex == 0)
         return "Untitled";
      else
         return nameElements.substring(0, nameEndIndex);
   }


   private String handleGetResolvedFullNameFor(final String nameElements)
   {
      final int titleEndIndex = nameElements.indexOf('\0');
      final String title;
      final String photographerName;

      if (titleEndIndex != 0)
         title = nameElements.substring(0, titleEndIndex);
      else
         title = "Untitled";

      if (titleEndIndex != (nameElements.length() - 1))
         photographerName = nameElements.substring(titleEndIndex + 1);
      else
         photographerName = "Unknown";

      return title + " by " + photographerName;
   }


   private StringBuilder generateBaseImageURL(final String imageURLElements)
   {
      final StringBuilder urlBuilder = new StringBuilder(100);

      /* JRE 6 (and maybe earlier versions of JRE 7) will fail when making SSL connections to 1x, meaning no thumbnail photo will be shown in Feedbactory.
       * When making an isolated HTTPSUrlConnection to the photo URL, the error produced is 'Could not generate DH keypair',
       * which is apparently caused by a bug in the JRE: http://stackoverflow.com/questions/6851461/java-why-does-ssl-handshake-give-could-not-generate-dh-keypair-exception .
       * For future reference I also had to set a replacement HostnameVerifier on HTTPSUrlConnection which unconditionally verified all connections, otherwise I was getting
       * a different error: javax.net.ssl.SSLHandshakeException: java.security.cert.CertificateException: No subject alternative DNS name matching 1x.com found.
       */
      urlBuilder.append("https://1x.com/images/user/");
      urlBuilder.append(imageURLElements);

      return urlBuilder;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public short getID()
   {
      return 2;
   }


   @Override
   final public String getName()
   {
      return "1x";
   }


   @Override
   final public String getBaseURL()
   {
      return "http://www.1x.com";
   }


   @Override
   final public String getItemBrowseURL()
   {
      return "https://1x.com/critique";
   }


   @Override
   final public Set<String> getHostSuffixes()
   {
      return hostSuffixes;
   }


   @Override
   final public Set<PersonalFeedbackCriteriaType> getCriteriaTypes()
   {
      return criteriaTypes;
   }


   @Override
   final public boolean showFeedbackLessThanMinimumThreshold()
   {
      return true;
   }


   @Override
   final public String getResolvedShortNameFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return handleGetResolvedShortNameFor(personProfile.nameElements);
   }


   @Override
   final public String getResolvedFullNameFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return handleGetResolvedFullNameFor(personProfile.nameElements);
   }


   @Override
   final public String getResolvedThumbnailImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      // 200 x 200.
      return generateBaseImageURL(personProfile.imageURLElements).append("-sq.jpg").toString();
   }


   @Override
   final public String getResolvedLargeImageURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      // Longer dimension has 1000 pixels, shorter dimension is proportional.
      return generateBaseImageURL(personProfile.imageURLElements).append("-sd.jpg").toString();
   }


   @Override
   final public String getResolvedURLFor(final PersonalFeedbackPersonProfile personProfile)
   {
      return "http://1x.com/photo/" + personProfile.person.getItemID();
   }
}