package com.causa.api.controllers;

import com.causa.api.dto.response.DiagnosticDetailResponse;
import com.causa.api.dto.response.DiagnosticListItemResponse;
import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.ports.AlertRepository;
import com.causa.core.services.DiagnosticService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DiagnosticsController}.
 *
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DiagnosticsController Tests")
class DiagnosticsControllerTest {

    @Mock
    private DiagnosticService diagnosticService;

    @Mock
    private AlertRepository alertRepository;

    private DiagnosticsController controller;

    private static final String CLUSTER_NAME = "test-cluster";

    @BeforeEach
    void setUp() {
        controller = new DiagnosticsController(diagnosticService, alertRepository, CLUSTER_NAME);
    }

    private static Diagnostic buildDiagnostic(String id, String alertId) {
        return Diagnostic.builder()
                .diagnosticId(id)
                .alertId(alertId)
                .status(DiagnosticStatus.COMPLETED)
                .generatedAt(Instant.now())
                .build();
    }

    private static Alert buildAlert(String id) {
        return Alert.builder()
                .alertId(id)
                .alertName("TestAlert")
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.PROCESSED)
                .workloadInfo(Alert.WorkloadInfo.of("pod-1", "container-1", "default", CLUSTER_NAME, "Deployment"))
                .workloadName("container-1")
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/diagnostics
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/diagnostics")
    class ListDiagnosticsTests {

        @Test
        @DisplayName("Should return 200 with empty list when no diagnostics")
        void shouldReturn200WithEmptyList() {
            when(diagnosticService.listDiagnostics()).thenReturn(List.of());

            Response response = controller.listDiagnostics();

            assertEquals(200, response.getStatus());
            @SuppressWarnings("unchecked")
            List<DiagnosticListItemResponse> body = (List<DiagnosticListItemResponse>) response.getEntity();
            assertTrue(body.isEmpty());
        }

        @Test
        @DisplayName("Should return 200 with list of diagnostics")
        void shouldReturn200WithDiagnosticList() {
            Diagnostic d1 = buildDiagnostic("diag-1", "alert-1");
            Diagnostic d2 = buildDiagnostic("diag-2", "alert-2");
            when(diagnosticService.listDiagnostics()).thenReturn(List.of(d1, d2));

            Alert a1 = buildAlert("alert-1");
            Alert a2 = buildAlert("alert-2");
            when(alertRepository.findById("alert-1")).thenReturn(Optional.of(a1));
            when(alertRepository.findById("alert-2")).thenReturn(Optional.of(a2));

            Response response = controller.listDiagnostics();

            assertEquals(200, response.getStatus());
            @SuppressWarnings("unchecked")
            List<DiagnosticListItemResponse> body = (List<DiagnosticListItemResponse>) response.getEntity();
            assertEquals(2, body.size());
        }

        @Test
        @DisplayName("Should handle missing alert gracefully (alert deleted)")
        void shouldHandleMissingAlertGracefully() {
            Diagnostic d1 = buildDiagnostic("diag-1", "alert-orphan");
            when(diagnosticService.listDiagnostics()).thenReturn(List.of(d1));
            when(alertRepository.findById("alert-orphan")).thenReturn(Optional.empty());

            Response response = controller.listDiagnostics();

            assertEquals(200, response.getStatus());
            @SuppressWarnings("unchecked")
            List<DiagnosticListItemResponse> body = (List<DiagnosticListItemResponse>) response.getEntity();
            assertEquals(1, body.size());
            // workloadName should be null when alert is missing
            assertNull(body.get(0).workloadName());
        }

        @Test
        @DisplayName("Should lookup alert for each diagnostic")
        void shouldLookupAlertForEachDiagnostic() {
            Diagnostic d1 = buildDiagnostic("diag-1", "alert-1");
            Diagnostic d2 = buildDiagnostic("diag-2", "alert-2");
            when(diagnosticService.listDiagnostics()).thenReturn(List.of(d1, d2));
            when(alertRepository.findById(anyString())).thenReturn(Optional.empty());

            controller.listDiagnostics();

            verify(alertRepository).findById("alert-1");
            verify(alertRepository).findById("alert-2");
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/diagnostics/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/diagnostics/{id}")
    class GetDiagnosticByIdTests {

        @Test
        @DisplayName("Should return 200 with detail when diagnostic found")
        void shouldReturn200WhenDiagnosticFound() {
            Diagnostic d = buildDiagnostic("diag-abc", "alert-abc");
            Alert a = buildAlert("alert-abc");
            when(diagnosticService.getDiagnosticById("diag-abc")).thenReturn(Optional.of(d));
            when(alertRepository.findById("alert-abc")).thenReturn(Optional.of(a));

            Response response = controller.getDiagnostic("diag-abc");

            assertEquals(200, response.getStatus());
            DiagnosticDetailResponse body = (DiagnosticDetailResponse) response.getEntity();
            assertEquals("diag-abc", body.id());
            assertEquals("COMPLETED", body.status());
        }

        @Test
        @DisplayName("Should return 404 when diagnostic not found")
        void shouldReturn404WhenDiagnosticNotFound() {
            when(diagnosticService.getDiagnosticById("nonexistent")).thenReturn(Optional.empty());

            Response response = controller.getDiagnostic("nonexistent");

            assertEquals(404, response.getStatus());
        }

        @Test
        @DisplayName("Should include alert data in detail response")
        void shouldIncludeAlertDataInDetailResponse() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-1");
            Alert a = buildAlert("alert-1");
            when(diagnosticService.getDiagnosticById("diag-1")).thenReturn(Optional.of(d));
            when(alertRepository.findById("alert-1")).thenReturn(Optional.of(a));

            Response response = controller.getDiagnostic("diag-1");

            DiagnosticDetailResponse body = (DiagnosticDetailResponse) response.getEntity();
            assertNotNull(body.workloadInfo());
            assertEquals("default", body.workloadInfo().namespace());
        }

        @Test
        @DisplayName("Should handle missing alert for found diagnostic")
        void shouldHandleMissingAlertForFoundDiagnostic() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-orphan");
            when(diagnosticService.getDiagnosticById("diag-1")).thenReturn(Optional.of(d));
            when(alertRepository.findById("alert-orphan")).thenReturn(Optional.empty());

            Response response = controller.getDiagnostic("diag-1");

            assertEquals(200, response.getStatus());
            DiagnosticDetailResponse body = (DiagnosticDetailResponse) response.getEntity();
            assertNull(body.alertName());
            assertNull(body.workloadInfo());
        }

        @Test
        @DisplayName("Should use injected clusterName in response")
        void shouldUseInjectedClusterName() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-1");
            Alert a = buildAlert("alert-1");
            when(diagnosticService.getDiagnosticById("diag-1")).thenReturn(Optional.of(d));
            when(alertRepository.findById("alert-1")).thenReturn(Optional.of(a));

            Response response = controller.getDiagnostic("diag-1");

            DiagnosticDetailResponse body = (DiagnosticDetailResponse) response.getEntity();
            assertEquals(CLUSTER_NAME, body.workloadInfo().clusterName());
        }
    }
}
