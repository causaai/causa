package com.causa.api.controllers;

import com.causa.api.dto.response.AlertResponse;
import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.core.domain.Alert;
import com.causa.core.services.AlertService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AlertsController}.
 *
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertsController Tests")
class AlertsControllerTest {

    @Mock
    private AlertService alertService;

    private AlertsController controller;

    private static Alert buildAlert(String id) {
        return Alert.builder()
                .alertId(id)
                .alertName("TestAlert")
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.ACCEPTED)
                .workloadInfo(Alert.WorkloadInfo.of("pod-1", "container-1", "default", "cluster-1", "Deployment"))
                .workloadName("container-1")
                .build();
    }

    @BeforeEach
    void setUp() {
        controller = new AlertsController(alertService);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/alerts/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/alerts/{id}")
    class GetAlertByIdTests {

        @Test
        @DisplayName("Should return 200 with alert when found")
        void shouldReturn200WhenAlertFound() {
            Alert alert = buildAlert("alert-001");
            when(alertService.getAlert("alert-001")).thenReturn(Optional.of(alert));

            Response response = controller.getAlertById("alert-001", null, null);

            assertEquals(200, response.getStatus());
            AlertResponse body = (AlertResponse) response.getEntity();
            assertEquals("alert-001", body.alertId());
            verify(alertService).getAlert("alert-001");
        }

        @Test
        @DisplayName("Should return 404 when alert not found")
        void shouldReturn404WhenAlertNotFound() {
            when(alertService.getAlert("missing")).thenReturn(Optional.empty());

            Response response = controller.getAlertById("missing", null, null);

            assertEquals(404, response.getStatus());
        }

        @Test
        @DisplayName("Should return 400 when workload_name query param is provided with id")
        void shouldReturn400WhenWorkloadNameProvidedWithId() {
            Response response = controller.getAlertById("alert-001", "my-workload", null);

            assertEquals(400, response.getStatus());
            verifyNoInteractions(alertService);
        }

        @Test
        @DisplayName("Should return 400 when namespace query param is provided with id")
        void shouldReturn400WhenNamespaceProvidedWithId() {
            Response response = controller.getAlertById("alert-001", null, "default");

            assertEquals(400, response.getStatus());
            verifyNoInteractions(alertService);
        }

        @Test
        @DisplayName("Should return 400 when both query params are provided with id")
        void shouldReturn400WhenBothQueryParamsProvidedWithId() {
            Response response = controller.getAlertById("alert-001", "workload", "ns");

            assertEquals(400, response.getStatus());
            verifyNoInteractions(alertService);
        }

        @Test
        @DisplayName("Should allow blank workload_name without triggering 400")
        void shouldAllowBlankWorkloadName() {
            Alert alert = buildAlert("alert-001");
            when(alertService.getAlert("alert-001")).thenReturn(Optional.of(alert));

            Response response = controller.getAlertById("alert-001", "  ", null);

            assertEquals(200, response.getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/alerts
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/alerts")
    class GetAlertsTests {

        @Test
        @DisplayName("Should return 200 with all alerts when no filters")
        void shouldReturnAllAlertsWithNoFilters() {
            Alert a1 = buildAlert("a1");
            Alert a2 = buildAlert("a2");
            when(alertService.getAlerts(null, null)).thenReturn(List.of(a1, a2));

            Response response = controller.getAlerts(null, null);

            assertEquals(200, response.getStatus());
            @SuppressWarnings("unchecked")
            List<AlertResponse> body = (List<AlertResponse>) response.getEntity();
            assertEquals(2, body.size());
        }

        @Test
        @DisplayName("Should return 200 with empty list when no alerts found")
        void shouldReturnEmptyListWhenNoAlertsFound() {
            when(alertService.getAlerts(null, null)).thenReturn(List.of());

            Response response = controller.getAlerts(null, null);

            assertEquals(200, response.getStatus());
            @SuppressWarnings("unchecked")
            List<AlertResponse> body = (List<AlertResponse>) response.getEntity();
            assertTrue(body.isEmpty());
        }

        @Test
        @DisplayName("Should delegate workload_name filter to service")
        void shouldDelegateWorkloadNameFilter() {
            when(alertService.getAlerts("my-app", null)).thenReturn(List.of());

            controller.getAlerts("my-app", null);

            verify(alertService).getAlerts("my-app", null);
        }

        @Test
        @DisplayName("Should delegate namespace filter to service")
        void shouldDelegateNamespaceFilter() {
            when(alertService.getAlerts(null, "production")).thenReturn(List.of());

            controller.getAlerts(null, "production");

            verify(alertService).getAlerts(null, "production");
        }

        @Test
        @DisplayName("Should apply both filters when both are provided")
        void shouldApplyBothFilters() {
            Alert a = buildAlert("a1");
            when(alertService.getAlerts("my-app", "production")).thenReturn(List.of(a));

            Response response = controller.getAlerts("my-app", "production");

            assertEquals(200, response.getStatus());
            verify(alertService).getAlerts("my-app", "production");
        }
    }

    @Nested
    @DisplayName("Alert Response Mapping Tests")
    class AlertResponseMappingTests {

        @Test
        @DisplayName("Should map alert fields correctly to AlertResponse")
        void shouldMapAlertFieldsCorrectly() {
            Alert alert = Alert.builder()
                    .alertId("id-123")
                    .alertName("CPUThrottling")
                    .severity(AlertSeverity.WARNING)
                    .status(AlertStatus.PROCESSING)
                    .alertTimestamp(Instant.parse("2025-01-01T00:00:00Z"))
                    .workloadInfo(Alert.WorkloadInfo.of("pod-x", "container-x", "ns-x", "cluster-x", "StatefulSet"))
                    .workloadName("container-x")
                    .alertMetadata(Alert.AlertMetadata.of(
                            Map.of("severity", "warning"),
                            Map.of("summary", "CPU is high"),
                            "prometheus"))
                    .build();

            when(alertService.getAlert("id-123")).thenReturn(Optional.of(alert));

            Response response = controller.getAlertById("id-123", null, null);

            AlertResponse body = (AlertResponse) response.getEntity();
            assertEquals("id-123", body.alertId());
            assertEquals("CPUThrottling", body.alertName());
            assertEquals("warning", body.severity());
            assertEquals("PROCESSING", body.status());
            assertEquals("ns-x", body.workloadInfo().namespace());
            assertEquals("cluster-x", body.workloadInfo().clusterName());
            assertEquals("prometheus", body.alertSource());
        }
    }
}
