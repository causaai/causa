package com.causa.api.dto.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigUpdateRequest}.
 *
 * @since 0.0.1
 */
@DisplayName("ConfigUpdateRequest Tests")
class ConfigUpdateRequestTest {

    @Nested
    @DisplayName("Record Construction Tests")
    class RecordConstructionTests {

        @Test
        @DisplayName("Should hold the configs map")
        void shouldHoldConfigsMap() {
            Map<String, String> configs = Map.of("LLM_PROVIDER", "anthropic");
            ConfigUpdateRequest request = new ConfigUpdateRequest(configs);

            assertNotNull(request.configs());
            assertEquals("anthropic", request.configs().get("LLM_PROVIDER"));
        }

        @Test
        @DisplayName("Should allow null configs map (validated at controller level)")
        void shouldAllowNullConfigs() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(null);

            assertNull(request.configs());
        }

        @Test
        @DisplayName("Should allow empty configs map")
        void shouldAllowEmptyConfigsMap() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(Map.of());

            assertNotNull(request.configs());
            assertTrue(request.configs().isEmpty());
        }

        @Test
        @DisplayName("Should hold multiple key-value pairs")
        void shouldHoldMultipleKeyValuePairs() {
            Map<String, String> configs = Map.of(
                    "LLM_PROVIDER", "ollama",
                    "LLM_TEMPERATURE", "0.7",
                    "CLUSTER_NAME", "dev"
            );
            ConfigUpdateRequest request = new ConfigUpdateRequest(configs);

            assertEquals(3, request.configs().size());
            assertEquals("ollama", request.configs().get("LLM_PROVIDER"));
            assertEquals("0.7", request.configs().get("LLM_TEMPERATURE"));
            assertEquals("dev", request.configs().get("CLUSTER_NAME"));
        }
    }
}
