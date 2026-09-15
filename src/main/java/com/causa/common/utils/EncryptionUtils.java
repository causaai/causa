package com.causa.common.utils;

import org.eclipse.microprofile.config.ConfigProvider;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM Encryption Utility
 *
 * <p>Provides stateless, thread-safe encryption/decryption for sensitive configuration values.
 * Uses AES-256-GCM with 12-byte random IV per encryption and 128-bit authentication tag.
 *
 * <p>Output format: {@code Base64(IV[12] + ciphertext + GCM_TAG[16])}
 *
 * <p>Master key is read from MicroProfile Config property {@code causa.encryption.key}
 * (env var {@code CAUSA_ENCRYPTION_KEY}). The value must be a Base64-encoded 32-byte
 * (256-bit) random key. Generate one with: {@code openssl rand -base64 32}
 *
 * <p>The master key is resolved and validated once at class initialisation. The application
 * will refuse to start if the key is missing, not valid Base64, or does not decode to exactly
 * 32 bytes — preventing silent key weakening.
 *
 * @since 0.0.1
 */
public final class EncryptionUtils {

    private EncryptionUtils() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;  // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits auth tag
    private static final int AES_KEY_SIZE = 32;    // 256 bits

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final SecretKey MASTER_KEY = resolveMasterKey();

    /**
     * Encrypts a plaintext string using AES-256-GCM.
     *
     * @param plaintext the plaintext to encrypt
     * @return Base64-encoded ciphertext with embedded IV and GCM tag
     * @throws EncryptionException if encryption fails
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new EncryptionException("Cannot encrypt null or blank plaintext");
        }

        try {
            byte[] iv = generateIV();

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, MASTER_KEY, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext + tag (tag is appended by GCM automatically)
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a Base64-encoded ciphertext string using AES-256-GCM.
     *
     * @param ciphertext the Base64-encoded ciphertext (with embedded IV and GCM tag)
     * @return the decrypted plaintext
     * @throws EncryptionException if decryption fails (wrong key, tampered data, etc.)
     */
    public static String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new EncryptionException("Cannot decrypt null or blank ciphertext");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] ciphertextBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertextBytes);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, MASTER_KEY, gcmSpec);

            byte[] plaintext = cipher.doFinal(ciphertextBytes);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new EncryptionException("Decryption failed", e);
        }
    }

    /**
     * Resolves and validates the AES master key from MicroProfile Config at class load time.
     *
     * <p>Expects a Base64-encoded 32-byte (256-bit) key set via {@code causa.encryption.key}
     * (env var {@code CAUSA_ENCRYPTION_KEY}). Fails fast on any misconfiguration — missing key,
     * invalid Base64, or wrong decoded length — so bad configuration is never silently masked.
     *
     * @return the 256-bit AES secret key
     * @throws EncryptionException if the key is absent, not valid Base64, or not exactly 32 bytes
     */
    private static SecretKey resolveMasterKey() {
        String rawKey = ConfigProvider.getConfig()
            .getOptionalValue("causa.encryption.key", String.class)
            .orElseThrow(() -> new EncryptionException(
                "Encryption key not found. Set CAUSA_ENCRYPTION_KEY to a Base64-encoded 32-byte key. " +
                "Generate one with: openssl rand -base64 32"));

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(rawKey.trim());
        } catch (IllegalArgumentException e) {
            throw new EncryptionException(
                "Invalid encryption key format: value is not valid Base64. " +
                "Generate a valid key with: openssl rand -base64 32", e);
        }

        if (keyBytes.length != AES_KEY_SIZE) {
            throw new EncryptionException(
                "Invalid encryption key length: expected " + AES_KEY_SIZE +
                " bytes (256 bits) after Base64 decoding, but got " + keyBytes.length + " bytes. " +
                "Generate a valid key with: openssl rand -base64 32");
        }

        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Generates a cryptographically secure random 12-byte IV for GCM.
     *
     * @return 12-byte initialization vector
     */
    private static byte[] generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    /**
     * Custom exception for encryption/decryption failures.
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message) {
            super(message);
        }

        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
