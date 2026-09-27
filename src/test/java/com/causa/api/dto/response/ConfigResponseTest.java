package com.causa.api.dto.response;

import com.causa.common.constants.ConfigConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigResponse}.
 *
 * @since 0.0.1
 */
@DisplayName("ConfigResponse Tests")
class ConfigResponseTest {

    // -------------------------------------------------------------------------
    // of(key, value) — auto-detects category and sensitivity
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("of(key, value) Factory Tests")
    class OfKeyValueTests {

        @Test
        @DisplayName("Should return plain value for non-sensitive key")
        void shouldReturnPlainValueForNonSensitiveKey() {
            ConfigResponse response = ConfigResponse.of("CLUSTER_NAME", "prod-cluster");

            assertEquals("CLUSTER_NAME", response.key());
            assertEquals("prod-cluster", response.value());
            assertFalse(response.encrypted());
        }

        @Test
        @DisplayName("Should detect 'alerts' category for alert keys")
        void shouldDetectAlertsCategory() {
            ConfigResponse response = ConfigResponse.of("ALERT_COOLDOWN_MINUTES", "15");

            assertEquals("alerts", response.category());
        }

        @Test
        @DisplayName("Should detect 'cluster' category for cluster keys")
        void shouldDetectClusterCategory() {
            ConfigResponse response = ConfigResponse.of("CLUSTER_NAME", "prod-cluster");

            assertEquals("cluster", response.category());
        }

        @Test
        @DisplayName("Should return null category for unknown key (LLM keys removed from generic_configs)")
        void shouldReturnNullCategoryForRemovedLlmKey() {
            ConfigResponse response = ConfigResponse.of("LLM_PROVIDER", "ollama");

            // LLM keys no longer exist in generic_configs — category is null, value is not masked
            assertNull(response.category());
            assertEquals("ollama", response.value());
            assertFalse(response.encrypted());
        }

        @Test
        @DisplayName("Should handle null value for known non-sensitive key")
        void shouldHandleNullValue() {
            ConfigResponse response = ConfigResponse.of("CLUSTER_NAME", null);

            assertEquals("CLUSTER_NAME", response.key());
            assertNull(response.value());
            assertFalse(response.encrypted());
        }
    }

    // -------------------------------------------------------------------------
    // of(key, value, encrypted) — explicit encrypted flag
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("of(key, value, encrypted) Factory Tests")
    class OfKeyValueEncryptedTests {

        @Test
        @DisplayName("Should mask value when encrypted flag is true")
        void shouldMaskWhenEncryptedTrue() {
            ConfigResponse response = ConfigResponse.of("CLUSTER_NAME", "stored-encrypted", true);

            assertEquals(ConfigConstants.MASKED_VALUE, response.value());
            assertTrue(response.encrypted());
        }

        @Test
        @DisplayName("Should return plain value when encrypted flag is false")
        void shouldReturnPlainWhenEncryptedFalse() {
            ConfigResponse response = ConfigResponse.of("CLUSTER_NAME", "prod-cluster", false);

            assertEquals("prod-cluster", response.value());
            assertFalse(response.encrypted());
        }

        @Test
        @DisplayName("Category is always derived from key, not from encrypted flag")
        void categoryDerivedFromKey() {
            ConfigResponse r1 = ConfigResponse.of("CLUSTER_NAME", "val", false);
            ConfigResponse r2 = ConfigResponse.of("CLUSTER_NAME", "val", true);

            assertEquals("cluster", r1.category());
            assertEquals("cluster", r2.category());
        }
    }
}
