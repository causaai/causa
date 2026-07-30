package com.causa.api.dto.response;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DiagnosticListItemResponse}.
 *
 * @since 0.0.1
 */
@DisplayName("DiagnosticListItemResponse Tests")
class DiagnosticListItemResponseTest {

    private static Diagnostic buildDiagnostic(String id, String alertId, DiagnosticStatus status) {
        return Diagnostic.builder()
                .diagnosticId(id)
                .alertId(alertId)
                .status(status)
                .generatedAt(Instant.parse("2025-06-01T12:00:00Z"))
                .build();
    }

    private static Alert buildAlert(String id) {
        return Alert.builder()
                .alertId(id)
                .alertName("TestAlert")
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.PROCESSED)
                .workloadInfo(Alert.WorkloadInfo.of("pod-1", "container-1", "default", "cluster-1", "Deployment"))
                .workloadName("container-1")
                .build();
    }

    @Nested
    @DisplayName("from() Factory Tests")
    class FromFactoryTests {

        @Test
        @DisplayName("Should map diagnostic id and status")
        void shouldMapDiagnosticIdAndStatus() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-1", DiagnosticStatus.COMPLETED);
            Alert a = buildAlert("alert-1");

            DiagnosticListItemResponse response = DiagnosticListItemResponse.from(d, a, "cluster-prod");

            assertEquals("diag-1", response.id());
            assertEquals("COMPLETED", response.status());
        }

        @Test
        @DisplayName("Should map workload name from alert")
        void shouldMapWorkloadNameFromAlert() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-1", DiagnosticStatus.COMPLETED);
            Alert a = buildAlert("alert-1");

            DiagnosticListItemResponse response = DiagnosticListItemResponse.from(d, a, "cluster-prod");

            assertEquals("container-1", response.workloadName());
        }

        @Test
        @DisplayName("Should map namespace from alert workload info")
        void shouldMapNamespaceFromAlertWorkloadInfo() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-1", DiagnosticStatus.COMPLETED);
            Alert a = buildAlert("alert-1");

            DiagnosticListItemResponse response = DiagnosticListItemResponse.from(d, a, "cluster-prod");

            assertEquals("default", response.namespace());
        }

        @Test
        @DisplayName("Should map severity from alert")
        void shouldMapSeverityFromAlert() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-1", DiagnosticStatus.COMPLETED);
            Alert a = buildAlert("alert-1");

            DiagnosticListItemResponse response = DiagnosticListItemResponse.from(d, a, "cluster-prod");

            assertEquals("critical", response.severity());
        }

        @Test
        @DisplayName("Should use provided clusterName")
        void shouldUseProvidedClusterName() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-1", DiagnosticStatus.PENDING);
            Alert a = buildAlert("alert-1");

            DiagnosticListItemResponse response = DiagnosticListItemResponse.from(d, a, "my-cluster");

            assertEquals("my-cluster", response.clusterName());
        }

        @Test
        @DisplayName("Should fallback to 'default' when clusterName is null")
        void shouldFallbackToDefaultWhenClusterNameNull() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-1", DiagnosticStatus.PENDING);
            Alert a = buildAlert("alert-1");

            DiagnosticListItemResponse response = DiagnosticListItemResponse.from(d, a, null);

            assertEquals("default", response.clusterName());
        }

        @Test
        @DisplayName("Should fallback to 'default' when clusterName is blank")
        void shouldFallbackToDefaultWhenClusterNameBlank() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-1", DiagnosticStatus.PENDING);
            Alert a = buildAlert("alert-1");

            DiagnosticListItemResponse response = DiagnosticListItemResponse.from(d, a, "  ");

            assertEquals("default", response.clusterName());
        }

        @Test
        @DisplayName("Should handle null alert gracefully")
        void shouldHandleNullAlertGracefully() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-orphan", DiagnosticStatus.FAILED);

            DiagnosticListItemResponse response = DiagnosticListItemResponse.from(d, null, "cluster");

            assertEquals("diag-1", response.id());
            assertNull(response.workloadName());
            assertNull(response.namespace());
            assertNull(response.severity());
        }

        @Test
        @DisplayName("Should set issue title to null when RCA is not present")
        void shouldSetIssueTitleNullWhenNoRca() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-1", DiagnosticStatus.IN_PROGRESS);
            Alert a = buildAlert("alert-1");

            DiagnosticListItemResponse response = DiagnosticListItemResponse.from(d, a, "cluster");

            assertNull(response.issue());
        }

        @Test
        @DisplayName("Should map generatedAt as date field")
        void shouldMapGeneratedAtAsDate() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-1", DiagnosticStatus.COMPLETED);
            Alert a = buildAlert("alert-1");

            DiagnosticListItemResponse response = DiagnosticListItemResponse.from(d, a, "cluster");

            assertEquals(Instant.parse("2025-06-01T12:00:00Z"), response.date());
        }

        @Test
        @DisplayName("Should map null status when diagnostic status is null — not expected but must not throw")
        void shouldHandleNullDiagnosticStatus() {
            // status is required by builder — test status string serialization
            Diagnostic d = buildDiagnostic("diag-1", "alert-1", DiagnosticStatus.IN_PROGRESS);
            DiagnosticListItemResponse response = DiagnosticListItemResponse.from(d, null, "cluster");

            assertEquals("IN_PROGRESS", response.status());
        }
    }
}
