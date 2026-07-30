package com.causa.common.utils;

import com.causa.common.utils.EncryptionUtils.EncryptionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("EncryptionUtils Tests")
class EncryptionUtilsTest {

    @Nested
    @DisplayName("encrypt() Tests")
    class EncryptTests {

        @Test
        void encrypt_producesBase64Output() {
            String result = EncryptionUtils.encrypt("hello");
            assertThat(result).isNotBlank();
            // Should be valid Base64
            assertThatCode(() -> java.util.Base64.getDecoder().decode(result)).doesNotThrowAnyException();
        }

        @Test
        void encrypt_differentCallsProduceDifferentCiphertext() {
            // Random IV means same plaintext encrypts differently each time
            String c1 = EncryptionUtils.encrypt("same");
            String c2 = EncryptionUtils.encrypt("same");
            assertThat(c1).isNotEqualTo(c2);
        }

        @Test
        void encrypt_nullThrows() {
            assertThatThrownBy(() -> EncryptionUtils.encrypt(null))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("null or blank");
        }

        @Test
        void encrypt_blankThrows() {
            assertThatThrownBy(() -> EncryptionUtils.encrypt("   "))
                .isInstanceOf(EncryptionException.class);
        }
    }

    @Nested
    @DisplayName("decrypt() Tests")
    class DecryptTests {

        @Test
        void roundTrip_encryptThenDecrypt() {
            String plaintext = "super-secret-value";
            String ciphertext = EncryptionUtils.encrypt(plaintext);
            assertThat(EncryptionUtils.decrypt(ciphertext)).isEqualTo(plaintext);
        }

        @Test
        void roundTrip_specialCharacters() {
            String plaintext = "p@$$w0rd! with ünïcödé and 日本語";
            assertThat(EncryptionUtils.decrypt(EncryptionUtils.encrypt(plaintext))).isEqualTo(plaintext);
        }

        @Test
        void decrypt_nullThrows() {
            assertThatThrownBy(() -> EncryptionUtils.decrypt(null))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("null or blank");
        }

        @Test
        void decrypt_blankThrows() {
            assertThatThrownBy(() -> EncryptionUtils.decrypt("   "))
                .isInstanceOf(EncryptionException.class);
        }

        @Test
        void decrypt_tamperedCiphertextThrows() {
            String ciphertext = EncryptionUtils.encrypt("value");
            // Tamper with the last byte — GCM authentication will fail
            byte[] bytes = java.util.Base64.getDecoder().decode(ciphertext);
            bytes[bytes.length - 1] ^= 0xFF;
            String tampered = java.util.Base64.getEncoder().encodeToString(bytes);
            assertThatThrownBy(() -> EncryptionUtils.decrypt(tampered))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("Decryption failed");
        }
    }

    @Nested
    @DisplayName("EncryptionException Tests")
    class ExceptionTests {

        @Test
        void exception_messageOnly() {
            EncryptionException ex = new EncryptionException("msg");
            assertThat(ex.getMessage()).isEqualTo("msg");
            assertThat(ex.getCause()).isNull();
        }

        @Test
        void exception_messageAndCause() {
            RuntimeException cause = new RuntimeException("root");
            EncryptionException ex = new EncryptionException("msg", cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }
}
