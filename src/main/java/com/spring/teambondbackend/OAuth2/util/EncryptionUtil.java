package com.spring.teambondbackend.OAuth2.util;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class EncryptionUtil {

    public static String encrypt(String plaintext, String secret) {
        try {
            // Generate 256-bit key from secret
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));

            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            // Generate random IV
            byte[] ivBytes = new byte[16];
            new SecureRandom().nextBytes(ivBytes);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            // Encrypt
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes());

            // Combine IV + encrypted data
            byte[] combined = new byte[ivBytes.length + encrypted.length];
            System.arraycopy(ivBytes, 0, combined, 0, ivBytes.length);
            System.arraycopy(encrypted, 0, combined, ivBytes.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public static String decrypt(String ciphertext, String secret) {
        try {
            // Decode Base64
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            // Extract IV (first 16 bytes)
            byte[] ivBytes = new byte[16];
            System.arraycopy(combined, 0, ivBytes, 0, ivBytes.length);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            // Extract encrypted data
            byte[] encrypted = new byte[combined.length - ivBytes.length];
            System.arraycopy(combined, ivBytes.length, encrypted, 0, encrypted.length);

            // Generate key
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            // Decrypt
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
