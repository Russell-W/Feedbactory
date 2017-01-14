
package com.feedbactory.server.network.application;


import com.feedbactory.server.network.component.buffer.ReadableByteBuffer;
import com.feedbactory.server.network.component.buffer.WritableByteBuffer;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


final public class SessionEncryption
{
   final public SecretKeySpec secretKeySpec;
   final public ReadableByteBuffer decryptedRequestBuffer;
   final public IvParameterSpec encryptedResponseInitialisationVector;
   final public WritableByteBuffer toBeEncryptedResponseBuffer;


   SessionEncryption(final SecretKeySpec secretKeySpec, final ReadableByteBuffer decryptedRequestBuffer, final IvParameterSpec encryptedResponseInitialisationVector,
                     final WritableByteBuffer toBeEncryptedResponseBuffer)
   {
      this.secretKeySpec = secretKeySpec;
      this.decryptedRequestBuffer = decryptedRequestBuffer;
      this.encryptedResponseInitialisationVector = encryptedResponseInitialisationVector;
      this.toBeEncryptedResponseBuffer = toBeEncryptedResponseBuffer;
   }
}