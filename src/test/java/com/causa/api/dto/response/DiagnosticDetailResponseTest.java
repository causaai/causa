package com.causa.api.dto.response;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.RootCauseAnalysis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DiagnosticDetailResponse}.
 *
 * @since 0.0.1
 */
@DisplayName("DiagnosticDetailResponse Tests")
class DiagnosticDetailResponseTest {

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
                .alertName("HighMemory")
                .alertTimestamp(Instant.parse("2025-06-01T11:55:00Z"))
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.PROCESSED)
                .workloadInfo(Alert.WorkloadInfo.of("pod-x", "container-x", "staging", "cluster-x", "StatefulSet"))
                .workloadName("container-x")
                .build();
    }

    @Nested
    @DisplayName("from() Factory Tests")
    class FromFactoryTests {

        @Test
        @DisplayName("Should map diagnostic id and status")
        void shouldMapDiagnosticIdAndStatus() {
            Diagnostic d = buildDiagnostic("diag-abc", "alert-abc", DiagnosticStatus.COMPLETED);
            Alert a = buildAlert("alert-abc");

            DiagnosticDetailResponse response = DiagnosticDetailResponse.from(d, a, "my-cluster");

            assertEquals("diag-abc", response.id());
            assertEquals("COMPLETED", response.status());
        }

        @Test
        @DisplayName("Should map alertId from diagnostic")
        void shouldMapAlertId() {
            Diagnostic d = buildDiagnostic("diag-abc", "alert-abc", DiagnosticStatus.COMPLETED);
            DiagnosticDetailResponse response = DiagnosticDetailResponse.from(d, null, "cluster");

            assertEquals("alert-abc", response.alertId());
        }

        @Test
        @DisplayName("Should map alert name from alert")
        void shouldMapAlertName() {
            Diagnostic d = buildDiagnostic("diag-abc", "alert-abc", DiagnosticStatus.COMPLETED);
            Alert a = buildAlert("alert-abc");

            DiagnosticDetailResponse response = DiagnosticDetailResponse.from(d, a, "cluster");

            assertEquals("HighMemory", response.alertName());
        }

        @Test
        @DisplayName("Should map severity from alert")
        void shouldMapSeverityFromAlert() {
            Diagnostic d = buildDiagnostic("d", "a", DiagnosticStatus.COMPLETED);
            Alert a = buildAlert("a");

            DiagnosticDetailResponse response = DiagnosticDetailResponse.from(d, a, "cluster");

            assertEquals("critical", response.severity());
        }

        @Test
        @DisplayName("Should map alertReceivedAt from alert timestamp")
        void shouldMapAlertReceivedAt() {
            Diagnostic d = buildDiagnostic("d", "a", DiagnosticStatus.COMPLETED);
            Alert a = buildAlert("a");

            DiagnosticDetailResponse response = DiagnosticDetailResponse.from(d, a, "cluster");

            assertEquals(Instant.parse("2025-06-01T11:55:00Z"), response.alertReceivedAt());
        }

        @Test
        @DisplayName("Should map workload info from alert")
        void shouldMapWorkloadInfoFromAlert() {
            Diagnostic d = buildDiagnostic("d", "a", DiagnosticStatus.COMPLETED);
            Alert a = buildAlert("a");

            DiagnosticDetailResponse response = DiagnosticDetailResponse.from(d, a, "my-cluster");

            assertNotNull(response.workloadInfo());
            assertEquals("pod-x", response.workloadInfo().podName());
            assertEquals("container-x", response.workloadInfo().workloadName());
            assertEquals("staging", response.workloadInfo().namespace());
            assertEquals("my-cluster", response.workloadInfo().clusterName());
            assertEquals("StatefulSet", response.workloadInfo().workloadType());
        }

        @Test
        @DisplayName("Should handle null alert gracefully")
        void shouldHandleNullAlertGracefully() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-orphan", DiagnosticStatus.FAILED);

            DiagnosticDetailResponse response = DiagnosticDetailResponse.from(d, null, "cluster");

            assertEquals("diag-1", response.id());
            assertNull(response.alertName());
            assertNull(response.severity());
            assertNull(response.workloadInfo());
            assertNull(response.alertReceivedAt());
        }

        @Test
        @DisplayName("Should set diagnosis to null when no RCA")
        void shouldSetDiagnosisNullWhenNoRca() {
            Diagnostic d = buildDiagnostic("diag-1", "alert-1", DiagnosticStatus.IN_PROGRESS);
            Alert a = buildAlert("alert-1");

            DiagnosticDetailResponse response = DiagnosticDetailResponse.from(d, a, "cluster");

            assertNull(response.diagnosis());
        }

        @Test
        @DisplayName("Should fallback to 'default' cluster name when blank")
        void shouldFallbackToDefaultClusterName() {
            Diagnostic d = buildDiagnostic("d", "a", DiagnosticStatus.PENDING);
            Alert a = buildAlert("a");

            DiagnosticDetailResponse response = DiagnosticDetailResponse.from(d, a, "");

            assertEquals("default", response.workloadInfo().clusterName());
        }

        @Test
        @DisplayName("Should map RCA fields into DiagnosisInfo when RCA is present")
        void shouldMapRcaFieldsWhenRcaPresent() {
            RootCauseAnalysis rca = new RootCauseAnalysis(
                    "CPU Resource Exhaustion",
                    "Container is consistently CPU throttled",
                    "Detailed description",
                    "Technical root cause details",
                    RootCauseAnalysis.AnomalyType.POSSIBLE_GC_PAUSE,
                    "Garbage collection pauses",
                    List.of("Log evidence 1"),
                    List.of("Evidence 1"),
                    List.of(new RootCauseAnalysis.Recommendation(
                            "Immediate Mitigation",
                            "Increase CPU limits",
                            "Increase by 2x",
                            "Apply kubectl patch",
                            0.9,
                            List.of("Monitor after change")
                    )),
                    new RootCauseAnalysis.ConfidenceSummary(0.85, "High confidence based on evidence"),
                    "Additional LLM notes"
            );

            Diagnostic d = Diagnostic.builder()
                    .diagnosticId("diag-1")
                    .alertId("alert-1")
                    .status(DiagnosticStatus.COMPLETED)
                    .generatedAt(Instant.now())
                    .rca(rca)
                    .build();

            Alert a = buildAlert("alert-1");
            DiagnosticDetailResponse response = DiagnosticDetailResponse.from(d, a, "cluster");

            assertNotNull(response.diagnosis());
            assertEquals("CPU Resource Exhaustion", response.diagnosis().issueTitle());
            assertEquals("Container is consistently CPU throttled", response.diagnosis().issueSummary());
            assertEquals("POSSIBLE_GC_PAUSE", response.diagnosis().anomalyType());
            assertEquals(0.85, response.diagnosis().rcaConfidenceScore());
            assertEquals("High confidence based on evidence", response.diagnosis().confidenceSummaryText());
            assertEquals("Additional LLM notes", response.diagnosis().llmNotes());
        }

        @Test
        @DisplayName("Should map recommendations from RCA")
        void shouldMapRecommendationsFromRca() {
            RootCauseAnalysis rca = new RootCauseAnalysis(
                    "Issue Title", null, null, null, null, null, null, null,
                    List.of(
                            new RootCauseAnalysis.Recommendation(
                                    "Root Cause Fix", "Fix the JVM heap",
                                    "Increase heap size", "Apply -Xmx2g", 0.9, List.of()),
                            new RootCauseAnalysis.Recommendation(
                                    "Validate & Monitor", "Monitor GC",
                                    "Watch GC metrics", null, 0.7, null)
                    ),
                    new RootCauseAnalysis.ConfidenceSummary(0.8, "summary"),
                    null
            );

            Diagnostic d = Diagnostic.builder()
                    .diagnosticId("d1")
                    .alertId("a1")
                    .status(DiagnosticStatus.COMPLETED)
                    .generatedAt(Instant.now())
                    .rca(rca)
                    .build();

            DiagnosticDetailResponse response = DiagnosticDetailResponse.from(d, null, "cluster");

            assertNotNull(response.diagnosis().recommendations());
            assertEquals(2, response.diagnosis().recommendations().size());
            assertEquals("Root Cause Fix", response.diagnosis().recommendations().get(0).solutionType());
        }
    }
}
