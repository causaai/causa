package com.causa.api.controllers;

import com.causa.api.dto.HealthCheckResponseDto;
import com.causa.api.dto.ComponentHealthDto;
import com.causa.common.constants.AppConstants;
import com.causa.core.services.HealthCheckService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HealthCheckController}.
 *
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HealthCheckController Tests")
class HealthCheckControllerTest {

    @Mock
    private HealthCheckService healthCheckService;

    private HealthCheckController controller;

    private static final String APP_VERSION = "0.0.1-TEST";

    @BeforeEach
    void setUp() {
        controller = new HealthCheckController(healthCheckService, APP_VERSION);
    }

    private HealthCheckResponseDto buildHealthResponse(String status) {
        return HealthCheckResponseDto.builder()
                .status(status)
                .timestampNow()
                .version(APP_VERSION)
                .addComponent("database", ComponentHealthDto.builder()
                        .status(status)
                        .message("test")
                        .latencyMs(10L)
                        .build())
                .build();
    }

    @Nested
    @DisplayName("HTTP Status Code Tests")
    class HttpStatusCodeTests {

        @Test
        @DisplayName("Should return 200 OK when system status is UP")
        void shouldReturn200WhenSystemIsUp() {
            when(healthCheckService.getSystemHealth()).thenReturn(buildHealthResponse("UP"));

            Response response = controller.getHealth();

            assertEquals(200, response.getStatus());
        }

        @Test
        @DisplayName("Should return 503 Service Unavailable when system status is DOWN")
        void shouldReturn503WhenSystemIsDown() {
            when(healthCheckService.getSystemHealth()).thenReturn(buildHealthResponse("DOWN"));

            Response response = controller.getHealth();

            assertEquals(503, response.getStatus());
        }

        @Test
        @DisplayName("Should return 503 Service Unavailable when system status is DEGRADED")
        void shouldReturn503WhenSystemIsDegraded() {
            when(healthCheckService.getSystemHealth()).thenReturn(buildHealthResponse("DEGRADED"));

            Response response = controller.getHealth();

            assertEquals(503, response.getStatus());
        }

        @Test
        @DisplayName("Should return 500 when health check service throws exception")
        void shouldReturn500WhenServiceThrowsException() {
            when(healthCheckService.getSystemHealth()).thenThrow(new RuntimeException("Unexpected error"));

            Response response = controller.getHealth();

            assertEquals(500, response.getStatus());
        }
    }

    @Nested
    @DisplayName("Response Body Tests")
    class ResponseBodyTests {

        @Test
        @DisplayName("Should return health response body from service")
        void shouldReturnHealthResponseBody() {
            HealthCheckResponseDto dto = buildHealthResponse("UP");
            when(healthCheckService.getSystemHealth()).thenReturn(dto);

            Response response = controller.getHealth();

            HealthCheckResponseDto body = (HealthCheckResponseDto) response.getEntity();
            assertNotNull(body);
            assertEquals("UP", body.getStatus());
            assertEquals(APP_VERSION, body.getVersion());
        }

        @Test
        @DisplayName("Should return error response body on exception with DOWN status")
        void shouldReturnErrorBodyOnException() {
            when(healthCheckService.getSystemHealth()).thenThrow(new RuntimeException("Internal error"));

            Response response = controller.getHealth();

            HealthCheckResponseDto body = (HealthCheckResponseDto) response.getEntity();
            assertNotNull(body);
            assertEquals("DOWN", body.getStatus());
            assertEquals(APP_VERSION, body.getVersion());
            assertNotNull(body.getTimestamp());
        }

        @Test
        @DisplayName("Should delegate to health check service")
        void shouldDelegateToHealthCheckService() {
            when(healthCheckService.getSystemHealth()).thenReturn(buildHealthResponse("UP"));

            controller.getHealth();

            verify(healthCheckService).getSystemHealth();
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should not return 200 for null or unknown status")
        void shouldReturn503ForUnknownStatus() {
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .status("UNKNOWN")
                    .timestampNow()
                    .version(APP_VERSION)
                    .build();
            when(healthCheckService.getSystemHealth()).thenReturn(dto);

            Response response = controller.getHealth();

            // Any non-UP status should cause 503
            assertEquals(503, response.getStatus());
        }

        @Test
        @DisplayName("Should include all component health data in response")
        void shouldIncludeAllComponentHealthData() {
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .status("UP")
                    .timestampNow()
                    .version(APP_VERSION)
                    .addComponent("database", ComponentHealthDto.builder().status("UP").latencyMs(5L).build())
                    .addComponent("llm_provider", ComponentHealthDto.builder().status("UP").latencyMs(200L).build())
                    .build();
            when(healthCheckService.getSystemHealth()).thenReturn(dto);

            Response response = controller.getHealth();

            HealthCheckResponseDto body = (HealthCheckResponseDto) response.getEntity();
            assertEquals(2, body.getComponents().size());
        }
    }
}
