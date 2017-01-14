
package com.feedbactory.shared.network;


import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import javax.xml.bind.DatatypeConverter;


abstract public class FeedbactorySessionConstants
{
   static final public String PublicKeyEncryptionType = "RSA";
   static final public String PublicKeyEncryptionAlgorithm = "RSA/ECB/PKCS1Padding";
   static final public int PublicKeyEncryptionKeyLengthBytes = 256;
   static final public PublicKey FeedbactoryPublicKey;

   static
   {
      // It's not the real public key.
      final String base64PublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsAHZcrI/V7ptbybhh76aStNRHK0ZYYqnrkDZfR3XgJ6QxB7sKZ8aC/dnnCvZ4GpYYKem0KV1LFxXSPCUs2drjUB/xUpIa6ir9+tuvE3vdztOtMZqp30TU3Hnv1pGWfTYWpHE7F57xY2Ky2EU+RkyuZXDsR/b1M5SgXGN1tVv6KBH5c32SRpEp2283TblV8UtWvdmxXp1kcBsOQcIU184QBni1wRPF3yWXAeWLTQno/SYjuUsZvVlhm4p92wyOYf4OdPUeTtxPm/Ko2TqF78UVj9GkX/pjA0FiIvvKQLwZEl0ItIu8gp0PQpy1KfBvaDmR/MxVB5UxsdwxWJ++Ot2XQIDAQAB";
      final byte[] encodedPublicKeyBytes = DatatypeConverter.parseBase64Binary(base64PublicKey);
      final X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(encodedPublicKeyBytes);

      try
      {
         final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
         FeedbactoryPublicKey = keyFactory.generatePublic(publicKeySpec);
      }
      catch (final Exception anyException)
      {
         throw new RuntimeException(anyException);
      }
   }

   static final public String SecretKeyEncryptionType = "AES";
   static final public String SecretKeyEncryptionAlgorithm = "AES/CBC/PKCS5Padding";
   static final public int SecretKeyEncryptionKeyLengthBytes = 16;
   static final public int SecretKeyEncryptionBlockSizeBytes = 16;
   static final public int SecretKeyEncryptionNonceLengthBytes = 16;

   static
   {
      /* If the client generated secret key which is encrypted using the public key algorithm is smaller than the length of the public keys,
       * I can assume in the implementation that the encrypted secret key will be padded out to exactly the size of the public key.
       */
      assert PublicKeyEncryptionKeyLengthBytes >= SecretKeyEncryptionKeyLengthBytes;
   }

   static final public int SessionIDLengthBytes = 32;


   private FeedbactorySessionConstants()
   {
   }
}