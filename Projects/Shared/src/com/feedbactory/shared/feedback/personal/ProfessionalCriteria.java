
package com.feedbactory.shared.feedback.personal;


/* Culled or to be reconsidered:
 * - Considerate (too much overlap with Helpful, Friendly, Respectful, and Ethical).
 * - Ambitious (doesn't have much bearing on someone's capability to perform their existing job).
 * - Good initiative? Strong initiative?
 */
public enum ProfessionalCriteria implements PersonalFeedbackCriteria
{
   Adaptable((byte) 0, "Adaptable"),
   Capable((byte) 1, "Capable"),
   Disciplined((byte) 2, "Disciplined"),
   EasyGoing((byte) 3, "Easy-going"),
   Equitable((byte) 4, "Equitable"),
   Ethical((byte) 5, "Ethical"),
   Friendly((byte) 6, "Friendly"),
   Fun((byte) 7, "Fun"),
   GoodAttentionToDetail((byte) 8, "Good attention to detail"),
   GoodCommunicator((byte) 9, "Good communicator"),
   GoodConflictResolution((byte) 10, "Good conflict resolution"),
   GoodLeadershipSkills((byte) 11, "Good leadership skills"),
   GoodListener((byte) 12, "Good listener"),
   GoodMultitasker((byte) 13, "Good multitasker"),
   GoodTaskPrioritisation((byte) 14, "Good task prioritisation"),
   GoodTeamWorker((byte) 15, "Good team worker"),
   GoodTimeManagement((byte) 16, "Good time management"),
   HardWorking((byte) 17, "Hard working"),
   Helpful((byte) 18, "Helpful"),
   Honest((byte) 19, "Honest"),
   Knowledgeable((byte) 20, "Knowledgeable"),
   Loyal((byte) 21, "Loyal"),
   Motivated((byte) 22, "Motivated"),
   NonDisruptive((byte) 23, "Non-disruptive"),
   NonSleazy((byte) 24, "Non-sleazy"),
   Organised((byte) 25, "Organised"),
   Positive((byte) 26, "Positive"),
   Practical((byte) 27, "Practical"),
   Punctual((byte) 28, "Punctual"),
   QuickLearner((byte) 29, "Quick learner"),
   Reliable((byte) 30, "Reliable"),
   Respectful((byte) 31, "Respectful");

   static
   {
      assert checkForDuplicateValues();
      assert checkForMismatchedValues();
   }


   final public byte value;
   final public String displayName;


   private ProfessionalCriteria(final byte value, final String displayName)
   {
      this.value = value;
      this.displayName = displayName;
   }

   /****************************************************************************
    *
    ***************************************************************************/


   static private boolean checkForDuplicateValues()
   {
      final boolean[] duplicateValueCheck = new boolean[Byte.MAX_VALUE];

      for (final ProfessionalCriteria criteria : values())
      {
         if (duplicateValueCheck[criteria.value])
            return false;

         duplicateValueCheck[criteria.value] = true;
      }

      return true;
   }


   static private boolean checkForMismatchedValues()
   {
      for (final ProfessionalCriteria criteria : values())
      {
         if (fromValue(criteria.value) != criteria)
            return false;
      }

      return true;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   @Override
   final public byte getValue()
   {
      return value;
   }


   @Override
   final public String getDisplayName()
   {
      return displayName;
   }


   @Override
   final public String toString()
   {
      return displayName;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   static public ProfessionalCriteria fromValue(final byte value)
   {
      switch (value)
      {
         case 0:
            return Adaptable;
         case 1:
            return Capable;
         case 2:
            return Disciplined;
         case 3:
            return EasyGoing;
         case 4:
            return Equitable;
         case 5:
            return Ethical;
         case 6:
            return Friendly;
         case 7:
            return Fun;
         case 8:
            return GoodAttentionToDetail;
         case 9:
            return GoodCommunicator;
         case 10:
            return GoodConflictResolution;
         case 11:
            return GoodLeadershipSkills;
         case 12:
            return GoodListener;
         case 13:
            return GoodMultitasker;
         case 14:
            return GoodTaskPrioritisation;
         case 15:
            return GoodTeamWorker;
         case 16:
            return GoodTimeManagement;
         case 17:
            return HardWorking;
         case 18:
            return Helpful;
         case 19:
            return Honest;
         case 20:
            return Knowledgeable;
         case 21:
            return Loyal;
         case 22:
            return Motivated;
         case 23:
            return NonDisruptive;
         case 24:
            return NonSleazy;
         case 25:
            return Organised;
         case 26:
            return Positive;
         case 27:
            return Practical;
         case 28:
            return Punctual;
         case 29:
            return QuickLearner;
         case 30:
            return Reliable;
         case 31:
            return Respectful;

         /* No exception thrown here - let the caller decide how to react to a null value, whether it be throwing a security exception (eg. reading
          * a record on the server), or throwing an illegal state exception or similar on the client.
          */
         default:
            return null;
      }
   }
}