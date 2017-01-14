
package com.feedbactory.client.core.feedback.personal;


import com.feedbactory.shared.feedback.personal.PersonalFeedbackConstants;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;


abstract public class PersonalFeedbackUtilities
{
   private PersonalFeedbackUtilities()
   {
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static private Set<String> handleGetProcessedTags(final String rawTagsString)
   {
      final Set<String> tags = new HashSet<String>();
      processRawTag(tags, rawTagsString);
      return tags;
   }


   static private void processRawTag(final Set<String> tags, final String rawTagString)
   {
      int characterIndex = 0;

      while ((characterIndex = seekTagStart(rawTagString, characterIndex)) < rawTagString.length())
      {
         characterIndex = processTag(rawTagString, characterIndex, tags);

         // Bail out if the maximum legal number of tags has been reached, or if the end of the input string has been reached.
         if ((tags.size() == PersonalFeedbackConstants.MaximumPersonProfileTags) || (characterIndex >= rawTagString.length()))
            break;
      }
   }


   static private int seekTagStart(final String rawTagString, final int characterIndex)
   {
      int tagStartSearchIndex = characterIndex;
      char currentCharacter;

      while (tagStartSearchIndex < rawTagString.length())
      {
         currentCharacter = rawTagString.charAt(tagStartSearchIndex);
         if (Character.isLetterOrDigit(currentCharacter))
            break;
         else if (PersonalFeedbackConstants.PermittedTagDelimiterCharacters.indexOf(currentCharacter) == -1)
            tagStartSearchIndex = seekWhitespace(rawTagString, tagStartSearchIndex + 1);

         tagStartSearchIndex ++;
      }

      return tagStartSearchIndex;
   }


   static private int seekWhitespace(final String rawTagString, final int characterIndex)
   {
      int whitespaceSearchIndex = characterIndex;

      while ((whitespaceSearchIndex < rawTagString.length()) && (! Character.isWhitespace(rawTagString.charAt(whitespaceSearchIndex))))
         whitespaceSearchIndex ++;

      return whitespaceSearchIndex;
   }


   static private int processTag(final String rawTagString, final int tagStartIndex, final Set<String> tags)
   {
      int tagEndSearchIndex = tagStartIndex + 1;
      char tagCharacter;
      boolean wasPreviousALetterOrDigit = true;

      while (tagEndSearchIndex < rawTagString.length())
      {
         tagCharacter = rawTagString.charAt(tagEndSearchIndex);
         if (Character.isLetterOrDigit(tagCharacter))
            wasPreviousALetterOrDigit = true;
         else if (PersonalFeedbackConstants.PermittedTagNonAlphaNumericCharacters.indexOf(tagCharacter) != -1)
         {
            if (wasPreviousALetterOrDigit)
               wasPreviousALetterOrDigit = false;
            else
            {
               // More than one successive non-alphanumeric character - bail out of processing the remainder of the token.
               return seekWhitespace(rawTagString, tagEndSearchIndex + 1);
            }
         }
         else if (PersonalFeedbackConstants.PermittedTagDelimiterCharacters.indexOf(tagCharacter) != -1)
            break;
         else
         {
            // Invalid tag characters - bail out of processing the remainder of the token.
            return seekWhitespace(rawTagString, tagEndSearchIndex + 1);
         }

         tagEndSearchIndex ++;
      }

      addProcessedTag(rawTagString, tagStartIndex, tagEndSearchIndex, tags);

      return tagEndSearchIndex;
   }


   static private void addProcessedTag(final String rawTagString, final int startIndex, final int endIndex, final Set<String> tags)
   {
      int adjustedEndIndex = endIndex;
      if (PersonalFeedbackConstants.PermittedTagNonAlphaNumericCharacters.indexOf(rawTagString.charAt(endIndex - 1)) != -1)
         adjustedEndIndex --;

      final int tagLength = (adjustedEndIndex - startIndex);

      /* I could also weed out common word tags here, however it doesn't feel right embedding the set of excluded words anywhere in
       * the clienr or shared codebase, eg. within PersonalFeedbackConstants. I think it's partly the fact that it could eventually be a locale-
       * specific thing, even though the server is only applying the English filter on incoming keywords from any client. Also I'm not
       * comfortable with allowing the clients access to a complete list of excluded tag words; what if I want to add more exclusions,
       * eg. offensive words? Why lay them all out there for the clients to examine. The server has to check them regardless.
       * So for now I'm content to allow clients to send a set of tags, which may or may not be accepted at the server's discretion.
       */
      if ((tagLength >= PersonalFeedbackConstants.MinimumPersonProfileTagLength) &&
          (tagLength <= PersonalFeedbackConstants.MaximumPersonProfileTagLength))
      {
         /* To avoid fragmentation of the item profiles on the server, the same locale for lowercasing the tags should be applied on
          * all clients no matter their default locale.
          */
         tags.add(rawTagString.substring(startIndex, adjustedEndIndex).toLowerCase(Locale.ENGLISH));
      }
   }


   static private Set<String> handleGetProcessedTags(final Object[] rawTagsArray)
   {
      if (rawTagsArray.length == 0)
         return Collections.emptySet();

      final Set<String> tags = new HashSet<String>(rawTagsArray.length);

      for (final Object rawTagObject : rawTagsArray)
      {
         processRawTag(tags, ((String) rawTagObject));

         // Cap the size of the set of tags to PersonalFeedbackConstants.MaximumPersonProfileTags.
         if (tags.size() == PersonalFeedbackConstants.MaximumPersonProfileTags)
            break;
      }

      return tags;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public Set<String> getProcessedTags(final String rawTagsString)
   {
      return handleGetProcessedTags(rawTagsString);
   }


   static public Set<String> getProcessedTags(final Object[] rawTagsArray)
   {
      return handleGetProcessedTags(rawTagsArray);
   }
}