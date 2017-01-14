/* Memos:
 * - If Java enums allowed parameterised types, I could supply a custom fromValueToPersonalFeedbackCriteria() method for each enum value,
 *   but in any case I'm not sure that this class is the appropriate place to put that method.
 *
 * - The attributes field is used to encapsulate information about the criteria type, including some type safety. This is needed when for example
 *   creating an EnumMap to contain criteria feedback for a given type of criteria - note that the EnumMap constructor requires an argument
 *   specifying the key's enum class. Unfortunately this type safety can't be attached directly to this class, eg.
 *   enum PersonalFeedbackCriteriaType<E extends Enum<E> & PersonalFeedbackCriteria>, since enums won't allow parameterisation. So, the
 *   information needs to be stored in an external object which can still be accessed via the criteria type variable.
 *   For examples of the attributes field being used, particularly where a typesafe helper method is required, check out the server's PersonalFeedbackNetworkGateway,
 *   specifically the readProfileCriteriaFeedbackSubmission() method.
 */

package com.feedbactory.shared.feedback.personal;


public enum PersonalFeedbackCriteriaType
{
   None((byte) 0, "NoCriteria", NoCriteriaAttributes.instance),
   Profile((byte) 1, "Profile", ProfileCriteriaAttributes.instance),
   Personal((byte) 2, "Personal", PersonalCriteriaAttributes.instance),
   Professional((byte) 3, "Professional", ProfessionalCriteriaAttributes.instance),
   Photography((byte) 4, "Photography", PhotographyCriteriaAttributes.instance);


   final public byte value;
   final public String displayName;
   final public PersonalFeedbackCriteriaAttributes<?> attributes;


   private PersonalFeedbackCriteriaType(final byte value, final String displayName, final PersonalFeedbackCriteriaAttributes<?> attributes)
   {
      this.value = value;
      this.displayName = displayName;
      this.attributes = attributes;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public PersonalFeedbackCriteriaType fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return None;
         case 1:
            return Profile;
         case 2:
            return Personal;
         case 3:
            return Professional;
         case 4:
            return Photography;

         /* No exception thrown here - let the caller decide how to react to a null value, whether it be throwing a security exception (eg. reading
          * a record on the server), or using it as an indicator to skip the remainder of an unknown record type on the client.
          */
         default:
            return null;
      }
   }
}