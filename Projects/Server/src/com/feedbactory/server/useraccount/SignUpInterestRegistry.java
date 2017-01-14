
package com.feedbactory.server.useraccount;


import com.feedbactory.server.core.FeedbactoryServerConstants;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;


final class SignUpInterestRegistry
{
   static final private File InterestRegistryFile = FeedbactoryServerConstants.BaseDataPath.resolve("SignUpInterestRegistry" + FeedbactoryServerConstants.DataFileExtension).toFile();

   final private PrintWriter registryFileWriter = initialiseRegistryFileWriter();


   private PrintWriter initialiseRegistryFileWriter()
   {
      try
      {
         final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(InterestRegistryFile, true), StandardCharsets.UTF_8);
         final BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter, 512);
         return new PrintWriter(bufferedWriter, false);
      }
      catch (final IOException ioException)
      {
         throw new RuntimeException(ioException);
      }
   }


   /****************************************************************************
    *
    ***************************************************************************/


   final void registerSignUpInterest(final String email)
   {
      registryFileWriter.println(email);
   }


   final void shutdown()
   {
      registryFileWriter.close();
   }
}