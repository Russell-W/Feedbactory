/* Memos:
 * - The common words here have been borrowed from an old project.
 */

package com.feedbactory.server.feedback.personal;


import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


final class PersonalFeedbackServerConstants
{
   static Set<String> FeaturedItemsExcludedTags;

   static
   {
      final String[] featuredItemsExcludedKeywordsBuilder = new String[]
      {
         "a", "about", "above", "across", "afore", "after", "again", "against", "all", "almost", "along", "also", "although", "always", "am",
         "amid", "amidst", "among", "amongst", "an", "and", "another", "any", "anybody", "anyhow", "anyone", "anything", "anywhere", "are", "around", 
         "art", "as", "at", "back", "be", "because", "been", "beest", "before", "behind", "being", "below", "beneath", "beside", "besides", 
         "between", "beyond", "both", "but", "by", "can", "cannot", "canst", "could", "couldst", "dare", "dared", "dares", "dareth", "despite", 
         "did", "didst", "do", "does", "doing", "done", "dost", "doth", "down", "during", "durst", "each", "either", "enough", "ere", 
         "even", "ever", "every", "everybody", "everyone", "everything", "everywhere", "few", "for", "from", "hadst", "hast", "hath", "have", "he", 
         "hence", "her", "here", "him", "himself", "his", "how", "however", "i", "if", "in", "inside", "instead", "is", "it", 
         "its", "itself", "just", "least", "less", "lest", "like", "many", "may", "mayst", "me", "might", "mightst", "mine", "more", 
         "most", "mought", "mowe", "much", "must", "my", "myself", "neither", "never", "nevetheless", "nill", "no", "nobody", "none", "no-one", 
         "nor", "not", "nothing", "now", "nowhere", "o", "of", "off", "often", "on", "one", "only ", "onto", "opposite", "or", 
         "other", "ought", "oughtst", "our", "ours", "ourself", "ourselves", "out", "outside", "over", "own", "past", "perhaps", "quite", "rather", 
         "round", "same", "self", "selves", "several", "shall", "shalt", "she", "should", "shouldst", "since", "sith", "so", "some", "somebody", 
         "somehow", "someone", "something", "somewhat", "somewhere", "still", "such", "than", "that", "the", "thee", "their", "theirs", "them", "themselves", 
         "then", "there", "thereupon", "these", "they", "thine", "this", "those", "thou", "though", "through", "throughout", "thus", "thy", "thyself", 
         "till", "to", "too", "towards", "under", "underneath", "unless", "until", "unto", "up", "upon", "us", "used", "very", "was", 
         "wast", "we", "well", "were", "wert", "what", "whatever", "when", "whenever", "where", "wherever", "which", "while", "whiles", "whilst", 
         "who", "whom", "whose", "why", "will", "with", "withal", "within", "without", "ye", "yet", "you", "your", "yours", "yourself", 
         "yourselves"
      };

      // "All literal strings and string-valued constant expressions are interned."
      final List<String> featuredItemsExcludedKeywordsList = Arrays.asList(featuredItemsExcludedKeywordsBuilder);
      FeaturedItemsExcludedTags = Collections.unmodifiableSet(new HashSet<>(featuredItemsExcludedKeywordsList));
   }

   private PersonalFeedbackServerConstants()
   {
   }
}