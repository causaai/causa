package com.causa.api.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigUpdateResponse}.
 *
 * @since 0.0.1
 */
@DisplayName("ConfigUpdateResponse Tests")
class ConfigUpdateResponseTest {

    @Nested
    @DisplayName("Record Construction Tests")
    class RecordConstructionTests {

        @Test
        @DisplayName("Should hold updated and rejected lists")
        void shouldHoldUpdatedAndRejectedLists() {
            ConfigResponse updated = ConfigResponse.of("LLM_PROVIDER", "anthropic");
            ConfigUpdateResponse.RejectedConfig rejected =
                    new ConfigUpdateResponse.RejectedConfig("UNKNOWN_KEY", "Unknown config key");

            ConfigUpdateResponse response = new ConfigUpdateResponse(
                    List.of(updated),
                    List.of(rejected)
            );

            assertEquals(1, response.updated().size());
            assertEquals(1, response.rejected().size());
        }

        @Test
        @DisplayName("Should return empty lists when no updates or rejections")
        void shouldReturnEmptyListsWhenNone() {
            ConfigUpdateResponse response = new ConfigUpdateResponse(List.of(), List.of());

            assertTrue(response.updated().isEmpty());
            assertTrue(response.rejected().isEmpty());
        }

        @Test
        @DisplayName("Should store multiple updated entries")
        void shouldStoreMultipleUpdatedEntries() {
            ConfigUpdateResponse response = new ConfigUpdateResponse(
                    List.of(
                            ConfigResponse.of("LLM_PROVIDER", "anthropic"),
                            ConfigResponse.of("CLUSTER_NAME", "prod")
                    ),
                    List.of()
            );

            assertEquals(2, response.updated().size());
        }

        @Test
        @DisplayName("Should store multiple rejected entries")
        void shouldStoreMultipleRejectedEntries() {
            ConfigUpdateResponse response = new ConfigUpdateResponse(
                    List.of(),
                    List.of(
                            new ConfigUpdateResponse.RejectedConfig("KEY_A", "Unknown key"),
                            new ConfigUpdateResponse.RejectedConfig("KEY_B", "Value must not be blank")
                    )
            );

            assertEquals(2, response.rejected().size());
        }
    }

    @Nested
    @DisplayName("RejectedConfig Record Tests")
    class RejectedConfigTests {

        @Test
        @DisplayName("Should hold key and reason")
        void shouldHoldKeyAndReason() {
            ConfigUpdateResponse.RejectedConfig rejected =
                    new ConfigUpdateResponse.RejectedConfig("LLM_MAX_TOKENS", "Expected an integer value");

            assertEquals("LLM_MAX_TOKENS", rejected.key());
            assertEquals("Expected an integer value", rejected.reason());
        }

        @Test
        @DisplayName("Should allow null reason")
        void shouldAllowNullReason() {
            ConfigUpdateResponse.RejectedConfig rejected =
                    new ConfigUpdateResponse.RejectedConfig("LLM_PROVIDER", null);

            assertEquals("LLM_PROVIDER", rejected.key());
            assertNull(rejected.reason());
        }
    }
}
