
package com.feedbactory.shared.feedback.personal;


public enum NoCriteria implements PersonalFeedbackCriteria
{
   ;


   @Override
   final public String getDisplayName()
   {
      return "";
   }


   @Override
   final public byte getValue()
   {
      return 0;
   }
}