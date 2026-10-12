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
                    new ConfigurationRepository.ConfigEntry("ALERT_FILTER_SEVERITY", "critical", false),
                    new ConfigurationRepository.ConfigEntry("ALERT_IGNORE_NAMESPACES", "kube-system", false)
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
            when(configService.getByCategory("alerts")).thenReturn(List.of(
                    new ConfigurationRepository.ConfigEntry("ALERT_FILTER_SEVERITY", "critical", false)
            ));

            Response response = controller.listGeneric("alerts");

            assertEquals(200, response.getStatus());
            verify(configService).getByCategory("alerts");
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
            when(configService.get("ALERT_FILTER_SEVERITY")).thenReturn(java.util.Optional.of("critical"));

            Response response = controller.getGenericConfig("ALERT_FILTER_SEVERITY");

            assertEquals(200, response.getStatus());
            ConfigResponse body = (ConfigResponse) response.getEntity();
            assertEquals("ALERT_FILTER_SEVERITY", body.key());
            assertEquals("critical", body.value());
        }

        @Test
        @DisplayName("Should return 200 with null value when key known but not set")
        void shouldReturn200WhenKeyKnownButNotSet() {
            when(configService.get("ALERT_FILTER_SEVERITY")).thenReturn(java.util.Optional.empty());

            Response response = controller.getGenericConfig("ALERT_FILTER_SEVERITY");

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
            ConfigUpdateRequest request = new ConfigUpdateRequest(Map.of("ALERT_FILTER_SEVERITY", "warning"));
            doNothing().when(configService).update("ALERT_FILTER_SEVERITY", "warning");

            Response response = controller.upsertGenericConfigs(request);

            assertEquals(200, response.getStatus());
            ConfigUpdateResponse body = (ConfigUpdateResponse) response.getEntity();
            assertEquals(1, body.updated().size());
            assertTrue(body.rejected().isEmpty());
            verify(configService).update("ALERT_FILTER_SEVERITY", "warning");
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
                    new ConfigUpdateRequest(Map.of("ALERT_FILTER_SEVERITY", "   "))).getEntity();

            assertEquals(1, body.rejected().size());
            assertEquals("ALERT_FILTER_SEVERITY", body.rejected().get(0).key());
        }

        @Test
        @DisplayName("Should reject invalid integer values")
        void shouldRejectInvalidIntegerValues() {
            ConfigUpdateResponse body = (ConfigUpdateResponse) controller.upsertGenericConfigs(
                    new ConfigUpdateRequest(Map.of("ALERT_COOLDOWN_MINUTES", "not-a-number"))).getEntity();

            assertEquals(1, body.rejected().size());
            assertTrue(body.rejected().get(0).reason().contains("integer"));
        }

        @Test
        @DisplayName("Should accept valid integer values")
        void shouldAcceptValidIntegerValues() {
            doNothing().when(configService).update("ALERT_COOLDOWN_MINUTES", "4096");

            ConfigUpdateResponse body = (ConfigUpdateResponse) controller.upsertGenericConfigs(
                    new ConfigUpdateRequest(Map.of("ALERT_COOLDOWN_MINUTES", "4096"))).getEntity();

            assertEquals(1, body.updated().size());
        }

        @Test
        @DisplayName("Should process valid and invalid keys independently")
        void shouldProcessValidAndInvalidKeysSeparately() {
            Map<String, String> configs = new java.util.LinkedHashMap<>();
            configs.put("ALERT_FILTER_SEVERITY", "warning");
            configs.put("UNKNOWN_KEY", "value");
            doNothing().when(configService).update("ALERT_FILTER_SEVERITY", "warning");

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
            doNothing().when(configService).delete("ALERT_FILTER_SEVERITY");

            Response response = controller.deleteGenericConfig("ALERT_FILTER_SEVERITY");

            assertEquals(204, response.getStatus());
            verify(configService).delete("ALERT_FILTER_SEVERITY");
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
