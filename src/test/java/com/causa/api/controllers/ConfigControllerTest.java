package com.causa.api.controllers;

import com.causa.api.dto.request.ConfigUpdateRequest;
import com.causa.api.dto.response.ConfigResponse;
import com.causa.api.dto.response.ConfigUpdateResponse;
import com.causa.core.ports.ConfigurationRepository;
import com.causa.core.services.ConfigService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConfigController}.
 *
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigController Tests")
class ConfigControllerTest {

    @Mock
    private ConfigService configService;

    private ConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new ConfigController(configService);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/configs
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/configs (list all)")
    class ListConfigsTests {

        @Test
        @DisplayName("Should return 200 with all configs when no category filter")
        void shouldReturn200WithAllConfigsNoFilter() {
            List<ConfigurationRepository.ConfigEntry> entries = List.of(
                    new ConfigurationRepository.ConfigEntry("LLM_PROVIDER", "ollama", false),
                    new ConfigurationRepository.ConfigEntry("LLM_API_KEY", "enc_secret", true)
            );
            when(configService.getAll()).thenReturn(entries);

            Response response = controller.listConfigs(null);

            assertEquals(200, response.getStatus());
            @SuppressWarnings("unchecked")
            List<ConfigResponse> body = (List<ConfigResponse>) response.getEntity();
            assertEquals(2, body.size());
            verify(configService).getAll();
        }

        @Test
        @DisplayName("Should return 200 with filtered configs when category is provided")
        void shouldReturn200WithFilteredConfigs() {
            List<ConfigurationRepository.ConfigEntry> entries = List.of(
                    new ConfigurationRepository.ConfigEntry("LLM_PROVIDER", "ollama", false)
            );
            when(configService.getByCategory("llm")).thenReturn(entries);

            Response response = controller.listConfigs("llm");

            assertEquals(200, response.getStatus());
            verify(configService).getByCategory("llm");
            verify(configService, never()).getAll();
        }

        @Test
        @DisplayName("Should return 400 for unknown category")
        void shouldReturn400ForUnknownCategory() {
            Response response = controller.listConfigs("unknown_category");

            assertEquals(400, response.getStatus());
            verifyNoInteractions(configService);
        }

        @Test
        @DisplayName("Should accept known category case-insensitively")
        void shouldAcceptKnownCategoryCaseInsensitively() {
            when(configService.getByCategory("llm")).thenReturn(List.of());

            Response response = controller.listConfigs("LLM");

            assertEquals(200, response.getStatus());
            verify(configService).getByCategory("llm");
        }

        @Test
        @DisplayName("Should return all configs when category is blank")
        void shouldReturnAllConfigsWhenCategoryIsBlank() {
            when(configService.getAll()).thenReturn(List.of());

            Response response = controller.listConfigs("  ");

            assertEquals(200, response.getStatus());
            verify(configService).getAll();
        }

        @Test
        @DisplayName("Should return all configs for alerts category")
        void shouldReturnAllConfigsForAlertsCategory() {
            when(configService.getByCategory("alerts")).thenReturn(List.of());

            Response response = controller.listConfigs("alerts");

            assertEquals(200, response.getStatus());
            verify(configService).getByCategory("alerts");
        }

        @Test
        @DisplayName("Should return all configs for cluster category")
        void shouldReturnAllConfigsForClusterCategory() {
            when(configService.getByCategory("cluster")).thenReturn(List.of());

            Response response = controller.listConfigs("cluster");

            assertEquals(200, response.getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/configs/{key}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/configs/{key}")
    class GetConfigByKeyTests {

        @Test
        @DisplayName("Should return 200 with config value when key exists")
        void shouldReturn200WhenKeyExists() {
            when(configService.get("LLM_PROVIDER")).thenReturn(Optional.of("ollama"));

            Response response = controller.getConfig("LLM_PROVIDER");

            assertEquals(200, response.getStatus());
            ConfigResponse body = (ConfigResponse) response.getEntity();
            assertEquals("LLM_PROVIDER", body.key());
        }

        @Test
        @DisplayName("Should return 200 with null value when key known but not set")
        void shouldReturn200WithNullValueWhenKeyKnownButNotSet() {
            when(configService.get("LLM_PROVIDER")).thenReturn(Optional.empty());

            Response response = controller.getConfig("LLM_PROVIDER");

            assertEquals(200, response.getStatus());
        }

        @Test
        @DisplayName("Should return 400 for unknown config key")
        void shouldReturn400ForUnknownKey() {
            Response response = controller.getConfig("UNKNOWN_KEY_XYZ");

            assertEquals(400, response.getStatus());
            verifyNoInteractions(configService);
        }

        @Test
        @DisplayName("Should mask sensitive key values")
        void shouldMaskSensitiveValues() {
            when(configService.get("LLM_API_KEY")).thenReturn(Optional.of("my-secret-key"));

            Response response = controller.getConfig("LLM_API_KEY");

            assertEquals(200, response.getStatus());
            ConfigResponse body = (ConfigResponse) response.getEntity();
            assertEquals("********", body.value());
            assertTrue(body.encrypted());
        }

        @Test
        @DisplayName("Should return unmasked value for non-sensitive key")
        void shouldReturnUnmaskedValueForNonSensitiveKey() {
            when(configService.get("LLM_PROVIDER")).thenReturn(Optional.of("anthropic"));

            Response response = controller.getConfig("LLM_PROVIDER");

            ConfigResponse body = (ConfigResponse) response.getEntity();
            assertEquals("anthropic", body.value());
            assertFalse(body.encrypted());
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/configs
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/configs")
    class UpdateConfigsTests {

        @Test
        @DisplayName("Should return 400 when configs map is null")
        void shouldReturn400WhenConfigsNull() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(null);

            Response response = controller.updateConfigs(request);

            assertEquals(400, response.getStatus());
            verifyNoInteractions(configService);
        }

        @Test
        @DisplayName("Should return 400 when configs map is empty")
        void shouldReturn400WhenConfigsEmpty() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(Map.of());

            Response response = controller.updateConfigs(request);

            assertEquals(400, response.getStatus());
        }

        @Test
        @DisplayName("Should return 200 with updated keys on valid request")
        void shouldReturn200WithUpdatedKeys() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(Map.of("LLM_PROVIDER", "anthropic"));
            doNothing().when(configService).update("LLM_PROVIDER", "anthropic");

            Response response = controller.updateConfigs(request);

            assertEquals(200, response.getStatus());
            ConfigUpdateResponse body = (ConfigUpdateResponse) response.getEntity();
            assertEquals(1, body.updated().size());
            assertTrue(body.rejected().isEmpty());
            verify(configService).update("LLM_PROVIDER", "anthropic");
        }

        @Test
        @DisplayName("Should reject unknown config keys")
        void shouldRejectUnknownConfigKeys() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(Map.of("UNKNOWN_KEY", "value"));

            Response response = controller.updateConfigs(request);

            assertEquals(200, response.getStatus());
            ConfigUpdateResponse body = (ConfigUpdateResponse) response.getEntity();
            assertEquals(0, body.updated().size());
            assertEquals(1, body.rejected().size());
            assertEquals("UNKNOWN_KEY", body.rejected().get(0).key());
            verifyNoInteractions(configService);
        }

        @Test
        @DisplayName("Should reject blank values")
        void shouldRejectBlankValues() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(Map.of("LLM_PROVIDER", "   "));

            Response response = controller.updateConfigs(request);

            assertEquals(200, response.getStatus());
            ConfigUpdateResponse body = (ConfigUpdateResponse) response.getEntity();
            assertEquals(1, body.rejected().size());
            assertEquals("LLM_PROVIDER", body.rejected().get(0).key());
        }

        @Test
        @DisplayName("Should reject invalid integer values for integer-typed keys")
        void shouldRejectInvalidIntegerValues() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(Map.of("LLM_MAX_TOKENS", "not-a-number"));

            Response response = controller.updateConfigs(request);

            assertEquals(200, response.getStatus());
            ConfigUpdateResponse body = (ConfigUpdateResponse) response.getEntity();
            assertEquals(1, body.rejected().size());
            assertTrue(body.rejected().get(0).reason().contains("integer"));
        }

        @Test
        @DisplayName("Should reject invalid double values for double-typed keys")
        void shouldRejectInvalidDoubleValues() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(Map.of("LLM_TEMPERATURE", "abc"));

            Response response = controller.updateConfigs(request);

            assertEquals(200, response.getStatus());
            ConfigUpdateResponse body = (ConfigUpdateResponse) response.getEntity();
            assertEquals(1, body.rejected().size());
            assertTrue(body.rejected().get(0).reason().toLowerCase().contains("numeric") ||
                       body.rejected().get(0).reason().toLowerCase().contains("double"));
        }

        @Test
        @DisplayName("Should reject invalid boolean values for boolean-typed keys")
        void shouldRejectInvalidBooleanValues() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(Map.of("LLM_SKILLS_ENABLED", "yes"));

            Response response = controller.updateConfigs(request);

            assertEquals(200, response.getStatus());
            ConfigUpdateResponse body = (ConfigUpdateResponse) response.getEntity();
            assertEquals(1, body.rejected().size());
            assertTrue(body.rejected().get(0).reason().toLowerCase().contains("boolean"));
        }

        @Test
        @DisplayName("Should accept valid boolean values")
        void shouldAcceptValidBooleanValues() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(Map.of("LLM_SKILLS_ENABLED", "true"));
            doNothing().when(configService).update("LLM_SKILLS_ENABLED", "true");

            Response response = controller.updateConfigs(request);

            assertEquals(200, response.getStatus());
            ConfigUpdateResponse body = (ConfigUpdateResponse) response.getEntity();
            assertEquals(1, body.updated().size());
        }

        @Test
        @DisplayName("Should accept valid double values")
        void shouldAcceptValidDoubleValues() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(Map.of("LLM_TEMPERATURE", "0.7"));
            doNothing().when(configService).update("LLM_TEMPERATURE", "0.7");

            Response response = controller.updateConfigs(request);

            ConfigUpdateResponse body = (ConfigUpdateResponse) response.getEntity();
            assertEquals(1, body.updated().size());
            assertTrue(body.rejected().isEmpty());
        }

        @Test
        @DisplayName("Should accept valid integer values")
        void shouldAcceptValidIntegerValues() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(Map.of("LLM_MAX_TOKENS", "4096"));
            doNothing().when(configService).update("LLM_MAX_TOKENS", "4096");

            Response response = controller.updateConfigs(request);

            ConfigUpdateResponse body = (ConfigUpdateResponse) response.getEntity();
            assertEquals(1, body.updated().size());
        }

        @Test
        @DisplayName("Should process valid and invalid keys independently")
        void shouldProcessValidAndInvalidKeysSeparately() {
            Map<String, String> configs = new java.util.LinkedHashMap<>();
            configs.put("LLM_PROVIDER", "anthropic");
            configs.put("UNKNOWN_KEY", "value");
            ConfigUpdateRequest request = new ConfigUpdateRequest(configs);
            doNothing().when(configService).update("LLM_PROVIDER", "anthropic");

            Response response = controller.updateConfigs(request);

            ConfigUpdateResponse body = (ConfigUpdateResponse) response.getEntity();
            assertEquals(1, body.updated().size());
            assertEquals(1, body.rejected().size());
        }
    }
}
