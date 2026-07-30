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
        @DisplayName("Should mask sensitive key value with '********'")
        void shouldMaskSensitiveKey() {
            ConfigResponse response = ConfigResponse.of("LLM_API_KEY", "my-secret");

            assertEquals("LLM_API_KEY", response.key());
            assertEquals(ConfigConstants.MASKED_VALUE, response.value());
            assertTrue(response.encrypted());
        }

        @Test
        @DisplayName("Should return plain value for non-sensitive key")
        void shouldReturnPlainValueForNonSensitiveKey() {
            ConfigResponse response = ConfigResponse.of("LLM_PROVIDER", "anthropic");

            assertEquals("LLM_PROVIDER", response.key());
            assertEquals("anthropic", response.value());
            assertFalse(response.encrypted());
        }

        @Test
        @DisplayName("Should detect 'llm' category for LLM keys")
        void shouldDetectLlmCategory() {
            ConfigResponse response = ConfigResponse.of("LLM_PROVIDER", "ollama");

            assertEquals("llm", response.category());
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
        @DisplayName("Should handle null value for known non-sensitive key")
        void shouldHandleNullValue() {
            ConfigResponse response = ConfigResponse.of("LLM_PROVIDER", null);

            assertEquals("LLM_PROVIDER", response.key());
            assertNull(response.value());
            assertFalse(response.encrypted());
        }

        @Test
        @DisplayName("Should also mask VERTEX_PROJECT_ID as sensitive")
        void shouldMaskVertexProjectId() {
            ConfigResponse response = ConfigResponse.of("VERTEX_PROJECT_ID", "my-gcp-project");

            assertEquals(ConfigConstants.MASKED_VALUE, response.value());
            assertTrue(response.encrypted());
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
            ConfigResponse response = ConfigResponse.of("LLM_PROVIDER", "stored-encrypted", true);

            assertEquals(ConfigConstants.MASKED_VALUE, response.value());
            assertTrue(response.encrypted());
        }

        @Test
        @DisplayName("Should return plain value when encrypted flag is false")
        void shouldReturnPlainWhenEncryptedFalse() {
            ConfigResponse response = ConfigResponse.of("LLM_PROVIDER", "ollama", false);

            assertEquals("ollama", response.value());
            assertFalse(response.encrypted());
        }

        @Test
        @DisplayName("Category is always derived from key, not from encrypted flag")
        void categoryDerivedFromKey() {
            ConfigResponse r1 = ConfigResponse.of("LLM_PROVIDER", "val", false);
            ConfigResponse r2 = ConfigResponse.of("LLM_PROVIDER", "val", true);

            assertEquals("llm", r1.category());
            assertEquals("llm", r2.category());
        }
    }
}
