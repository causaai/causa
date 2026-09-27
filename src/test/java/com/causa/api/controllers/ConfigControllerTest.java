package com.causa.api.controllers;

import com.causa.api.dto.request.ConfigUpdateRequest;
import com.causa.api.dto.response.ConfigResponse;
import com.causa.api.dto.response.ConfigSettingsResponse;
import com.causa.api.dto.response.ConfigUpdateResponse;
import com.causa.core.ports.ConfigurationRepository;
import com.causa.core.services.ConfigService;
import com.causa.core.services.ExternalConfigService;
import com.causa.core.services.LlmConfigService;
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
    @Mock
    private ExternalConfigService externalConfigService;
    @Mock
    private LlmConfigService llmConfigService;

    private ConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new ConfigController(configService, externalConfigService, llmConfigService);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/configs  — combined snapshot
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/configs (combined snapshot)")
    class GetAllConfigsTests {

        @Test
        @DisplayName("Should return 200 with all four categories populated")
        void shouldReturn200WithCombinedSnapshot() {
            when(externalConfigService.listByCategory(any())).thenReturn(List.of());
            when(llmConfigService.listAll()).thenReturn(List.of());
            when(configService.getAll()).thenReturn(List.of());

            Response response = controller.getAllConfigs();

            assertEquals(200, response.getStatus());
            assertInstanceOf(ConfigSettingsResponse.class, response.getEntity());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/configs/generic
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/configs/generic")
    class ListGenericTests {

        @Test
        @DisplayName("Should return 200 with all generic configs when no category filter")
        void shouldReturn200WithAllGenericConfigs() {
            List<ConfigurationRepository.ConfigEntry> entries = List.of(
                    new ConfigurationRepository.ConfigEntry("LLM_PROVIDER", "ollama", false),
                    new ConfigurationRepository.ConfigEntry("LLM_API_KEY", "enc_secret", true)
            );
            when(configService.getAll()).thenReturn(entries);

            Response response = controller.listGeneric(null);

            assertEquals(200, response.getStatus());
            @SuppressWarnings("unchecked")
            List<ConfigResponse> body = (List<ConfigResponse>) response.getEntity();
            assertEquals(2, body.size());
            verify(configService).getAll();
        }

        @Test
        @DisplayName("Should return 200 with filtered configs when category is provided")
        void shouldReturn200WithFilteredConfigs() {
            when(configService.getByCategory("llm")).thenReturn(List.of(
                    new ConfigurationRepository.ConfigEntry("LLM_PROVIDER", "ollama", false)
            ));

            Response response = controller.listGeneric("llm");

            assertEquals(200, response.getStatus());
            verify(configService).getByCategory("llm");
            verify(configService, never()).getAll();
        }

        @Test
        @DisplayName("Should return 400 for unknown category")
        void shouldReturn400ForUnknownCategory() {
            Response response = controller.listGeneric("unknown_xyz");

            assertEquals(400, response.getStatus());
            verifyNoInteractions(configService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/configs/generic/{key}")
    class GetGenericConfigTests {

        @Test
        @DisplayName("Should return 200 with config value when key exists")
        void shouldReturn200WhenKeyExists() {
            when(configService.get("LLM_PROVIDER")).thenReturn(java.util.Optional.of("ollama"));

            Response response = controller.getGenericConfig("LLM_PROVIDER");

            assertEquals(200, response.getStatus());
            ConfigResponse body = (ConfigResponse) response.getEntity();
            assertEquals("LLM_PROVIDER", body.key());
            assertEquals("ollama", body.value());
        }

        @Test
        @DisplayName("Should return 200 with null value when key known but not set")
        void shouldReturn200WhenKeyKnownButNotSet() {
            when(configService.get("LLM_PROVIDER")).thenReturn(java.util.Optional.empty());

            Response response = controller.getGenericConfig("LLM_PROVIDER");

            assertEquals(200, response.getStatus());
        }

        @Test
        @DisplayName("Should return 400 for unknown key")
        void shouldReturn400ForUnknownKey() {
            Response response = controller.getGenericConfig("UNKNOWN_KEY_XYZ");

            assertEquals(400, response.getStatus());
            verifyNoInteractions(configService);
        }
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/configs/generic
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/v1/configs/generic")
    class UpsertGenericConfigsTests {

        @Test
        @DisplayName("Should return 400 when configs map is null")
        void shouldReturn400WhenConfigsNull() {
            Response response = controller.upsertGenericConfigs(new ConfigUpdateRequest(null));

            assertEquals(400, response.getStatus());
            verifyNoInteractions(configService);
        }

        @Test
        @DisplayName("Should return 400 when configs map is empty")
        void shouldReturn400WhenConfigsEmpty() {
            Response response = controller.upsertGenericConfigs(new ConfigUpdateRequest(Map.of()));

            assertEquals(400, response.getStatus());
        }

        @Test
        @DisplayName("Should return 200 with updated keys on valid request")
        void shouldReturn200WithUpdatedKeys() {
            ConfigUpdateRequest request = new ConfigUpdateRequest(Map.of("LLM_PROVIDER", "anthropic"));
            doNothing().when(configService).update("LLM_PROVIDER", "anthropic");

            Response response = controller.upsertGenericConfigs(request);

            assertEquals(200, response.getStatus());
            ConfigUpdateResponse body = (ConfigUpdateResponse) response.getEntity();
            assertEquals(1, body.updated().size());
            assertTrue(body.rejected().isEmpty());
            verify(configService).update("LLM_PROVIDER", "anthropic");
        }

        @Test
        @DisplayName("Should reject unknown config keys")
        void shouldRejectUnknownConfigKeys() {
            Response response = controller.upsertGenericConfigs(
                    new ConfigUpdateRequest(Map.of("UNKNOWN_KEY", "value")));

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
            ConfigUpdateResponse body = (ConfigUpdateResponse) controller.upsertGenericConfigs(
                    new ConfigUpdateRequest(Map.of("LLM_PROVIDER", "   "))).getEntity();

            assertEquals(1, body.rejected().size());
            assertEquals("LLM_PROVIDER", body.rejected().get(0).key());
        }

        @Test
        @DisplayName("Should reject invalid integer values")
        void shouldRejectInvalidIntegerValues() {
            ConfigUpdateResponse body = (ConfigUpdateResponse) controller.upsertGenericConfigs(
                    new ConfigUpdateRequest(Map.of("LLM_MAX_TOKENS", "not-a-number"))).getEntity();

            assertEquals(1, body.rejected().size());
            assertTrue(body.rejected().get(0).reason().contains("integer"));
        }

        @Test
        @DisplayName("Should reject invalid double values")
        void shouldRejectInvalidDoubleValues() {
            ConfigUpdateResponse body = (ConfigUpdateResponse) controller.upsertGenericConfigs(
                    new ConfigUpdateRequest(Map.of("LLM_TEMPERATURE", "abc"))).getEntity();

            assertEquals(1, body.rejected().size());
            assertTrue(body.rejected().get(0).reason().toLowerCase().contains("numeric") ||
                       body.rejected().get(0).reason().toLowerCase().contains("double"));
        }

        @Test
        @DisplayName("Should reject invalid boolean values")
        void shouldRejectInvalidBooleanValues() {
            ConfigUpdateResponse body = (ConfigUpdateResponse) controller.upsertGenericConfigs(
                    new ConfigUpdateRequest(Map.of("LLM_SKILLS_ENABLED", "yes"))).getEntity();

            assertEquals(1, body.rejected().size());
            assertTrue(body.rejected().get(0).reason().toLowerCase().contains("boolean"));
        }

        @Test
        @DisplayName("Should accept valid boolean values")
        void shouldAcceptValidBooleanValues() {
            doNothing().when(configService).update("LLM_SKILLS_ENABLED", "true");

            ConfigUpdateResponse body = (ConfigUpdateResponse) controller.upsertGenericConfigs(
                    new ConfigUpdateRequest(Map.of("LLM_SKILLS_ENABLED", "true"))).getEntity();

            assertEquals(1, body.updated().size());
        }

        @Test
        @DisplayName("Should accept valid double values")
        void shouldAcceptValidDoubleValues() {
            doNothing().when(configService).update("LLM_TEMPERATURE", "0.7");

            ConfigUpdateResponse body = (ConfigUpdateResponse) controller.upsertGenericConfigs(
                    new ConfigUpdateRequest(Map.of("LLM_TEMPERATURE", "0.7"))).getEntity();

            assertEquals(1, body.updated().size());
            assertTrue(body.rejected().isEmpty());
        }

        @Test
        @DisplayName("Should accept valid integer values")
        void shouldAcceptValidIntegerValues() {
            doNothing().when(configService).update("LLM_MAX_TOKENS", "4096");

            ConfigUpdateResponse body = (ConfigUpdateResponse) controller.upsertGenericConfigs(
                    new ConfigUpdateRequest(Map.of("LLM_MAX_TOKENS", "4096"))).getEntity();

            assertEquals(1, body.updated().size());
        }

        @Test
        @DisplayName("Should process valid and invalid keys independently")
        void shouldProcessValidAndInvalidKeysSeparately() {
            Map<String, String> configs = new java.util.LinkedHashMap<>();
            configs.put("LLM_PROVIDER", "anthropic");
            configs.put("UNKNOWN_KEY", "value");
            doNothing().when(configService).update("LLM_PROVIDER", "anthropic");

            ConfigUpdateResponse body = (ConfigUpdateResponse) controller.upsertGenericConfigs(
                    new ConfigUpdateRequest(configs)).getEntity();

            assertEquals(1, body.updated().size());
            assertEquals(1, body.rejected().size());
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/configs/generic/{key}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/configs/generic/{key}")
    class DeleteGenericConfigTests {

        @Test
        @DisplayName("Should return 204 on successful delete")
        void shouldReturn204OnSuccess() {
            doNothing().when(configService).delete("LLM_PROVIDER");

            Response response = controller.deleteGenericConfig("LLM_PROVIDER");

            assertEquals(204, response.getStatus());
            verify(configService).delete("LLM_PROVIDER");
        }

        @Test
        @DisplayName("Should return 400 for unknown key")
        void shouldReturn400ForUnknownKey() {
            Response response = controller.deleteGenericConfig("UNKNOWN_KEY_XYZ");

            assertEquals(400, response.getStatus());
            verifyNoInteractions(configService);
        }
    }
}
