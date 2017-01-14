
package com.feedbactory.server.network.application;


final public class ValidationResult<V extends Object>
{
   final public String failedValidationMessage;
   final public V data;


   public ValidationResult(final String failedValidationMessage)
   {
      this.failedValidationMessage = failedValidationMessage;
      data = null;
   }


   public ValidationResult(final V data)
   {
      failedValidationMessage = null;
      this.data = data;
   }


   /****************************************************************************
    *
    ***************************************************************************/


   // Recast an existing failed validation result with message to a different return type, which is safe to do because failed validation results have null data.
   @SuppressWarnings("unchecked")
   static public <E> ValidationResult<E> failedValidationResult(final ValidationResult<?> requestStatus)
   {
      if (! requestStatus.isValid())
         return (ValidationResult<E>) requestStatus;
      else
         throw new IllegalArgumentException();
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final public boolean isValid()
   {
      return (failedValidationMessage == null);
   }
}