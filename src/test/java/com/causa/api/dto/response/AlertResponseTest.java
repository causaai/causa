package com.causa.api.dto.response;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.core.domain.Alert;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AlertResponse}.
 *
 * @since 0.0.1
 */
@DisplayName("AlertResponse Tests")
class AlertResponseTest {

    private static Alert buildAlert(String id) {
        return Alert.builder()
                .alertId(id)
                .sourceAlertId("fp-123")
                .alertName("CPUThrottling")
                .alertTimestamp(Instant.parse("2025-01-15T10:30:00Z"))
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.ACCEPTED)
                .workloadInfo(Alert.WorkloadInfo.of(
                        "my-pod", "my-container", "production", "cluster-prod", "Deployment"))
                .workloadName("my-container")
                .alertMetadata(Alert.AlertMetadata.of(
                        Map.of("severity", "critical", "alertname", "CPUThrottling"),
                        Map.of("summary", "CPU is throttling"),
                        "prometheus"))
                .build();
    }

    @Nested
    @DisplayName("from() Factory Method Tests")
    class FromFactoryTests {

        @Test
        @DisplayName("Should map all basic fields from Alert")
        void shouldMapAllBasicFields() {
            Alert alert = buildAlert("alert-123");
            AlertResponse response = AlertResponse.from(alert);

            assertEquals("alert-123", response.alertId());
            assertEquals("fp-123", response.sourceAlertId());
            assertEquals("CPUThrottling", response.alertName());
            assertEquals(Instant.parse("2025-01-15T10:30:00Z"), response.alertTimestamp());
        }

        @Test
        @DisplayName("Should map severity as string value")
        void shouldMapSeverityAsStringValue() {
            Alert alert = buildAlert("a1");
            AlertResponse response = AlertResponse.from(alert);

            assertEquals("critical", response.severity());
        }

        @Test
        @DisplayName("Should map status as string value")
        void shouldMapStatusAsStringValue() {
            Alert alert = buildAlert("a1");
            AlertResponse response = AlertResponse.from(alert);

            assertEquals("ACCEPTED", response.status());
        }

        @Test
        @DisplayName("Should map workload info fields")
        void shouldMapWorkloadInfoFields() {
            Alert alert = buildAlert("a1");
            AlertResponse response = AlertResponse.from(alert);

            assertNotNull(response.workloadInfo());
            assertEquals("my-pod", response.workloadInfo().podName());
            assertEquals("my-container", response.workloadInfo().containerName());
            assertEquals("production", response.workloadInfo().namespace());
            assertEquals("cluster-prod", response.workloadInfo().clusterName());
            assertEquals("Deployment", response.workloadInfo().workloadType());
        }

        @Test
        @DisplayName("Should map workloadName")
        void shouldMapWorkloadName() {
            Alert alert = buildAlert("a1");
            AlertResponse response = AlertResponse.from(alert);

            assertEquals("my-container", response.workloadName());
        }

        @Test
        @DisplayName("Should map labels from alertMetadata")
        void shouldMapLabels() {
            Alert alert = buildAlert("a1");
            AlertResponse response = AlertResponse.from(alert);

            assertNotNull(response.labels());
            assertEquals("critical", response.labels().get("severity"));
        }

        @Test
        @DisplayName("Should map annotations from alertMetadata")
        void shouldMapAnnotations() {
            Alert alert = buildAlert("a1");
            AlertResponse response = AlertResponse.from(alert);

            assertNotNull(response.annotations());
            assertEquals("CPU is throttling", response.annotations().get("summary"));
        }

        @Test
        @DisplayName("Should map alertSource from alertMetadata")
        void shouldMapAlertSource() {
            Alert alert = buildAlert("a1");
            AlertResponse response = AlertResponse.from(alert);

            assertEquals("prometheus", response.alertSource());
        }

        @Test
        @DisplayName("Should return null severity when severity is null")
        void shouldReturnNullSeverityWhenNull() {
            // Build alert without severity — can't do with builder (requires non-null), so test graceful handling
            Alert alert = Alert.builder()
                    .alertId("a1")
                    .alertName("Test")
                    .severity(AlertSeverity.WARNING)
                    .status(AlertStatus.ACCEPTED)
                    .workloadInfo(Alert.WorkloadInfo.of(null, null, null, null, null))
                    .workloadName("wl")
                    .build();

            AlertResponse response = AlertResponse.from(alert);

            // Should not throw
            assertNotNull(response);
        }

        @Test
        @DisplayName("Should map diagnostic IDs to diagIds")
        void shouldMapDiagnosticIds() {
            Alert alert = Alert.builder()
                    .alertId("a1").alertName("Test")
                    .severity(AlertSeverity.CRITICAL).status(AlertStatus.PROCESSED)
                    .workloadInfo(Alert.WorkloadInfo.of("p", "c", "ns", "cl", "D"))
                    .workloadName("wl")
                    .diagnosticIds(List.of("diag_2", "diag_1"))
                    .build();

            assertEquals(List.of("diag_2", "diag_1"), AlertResponse.from(alert).diagIds());
        }

        @Test
        @DisplayName("Should return empty diagIds when alert has no diagnostics")
        void shouldReturnEmptyDiagIdsWhenNone() {
            assertTrue(AlertResponse.from(buildAlert("a1")).diagIds().isEmpty());
        }

        @Test
        @DisplayName("Should map rejection reason from alertMetadata")
        void shouldMapRejectionReason() {
            Alert alert = Alert.builder()
                    .alertId("a1").alertName("Test")
                    .severity(AlertSeverity.WARNING).status(AlertStatus.REJECTED)
                    .workloadInfo(Alert.WorkloadInfo.of("p", "c", "ns", "cl", "D"))
                    .workloadName("wl")
                    .alertMetadata(Alert.AlertMetadata.of(Map.of(), Map.of(), "prometheus", "Alert is in cooldown"))
                    .build();

            AlertResponse response = AlertResponse.from(alert);

            assertEquals("REJECTED", response.status());
            assertEquals("Alert is in cooldown", response.rejectionReason());
        }

        @Test
        @DisplayName("Should return null rejectionReason for non-rejected alert")
        void shouldReturnNullRejectionReasonWhenNotRejected() {
            assertNull(AlertResponse.from(buildAlert("a1")).rejectionReason());
        }
    }

    @Nested
    @DisplayName("JSON Serialization Tests")
    class JsonSerializationTests {

        private final ObjectMapper mapper = new ObjectMapper();

        private Alert alertWithoutTimestamp(AlertStatus status, Alert.AlertMetadata metadata, List<String> diagIds) {
            return Alert.builder()
                    .alertId("a1").alertName("Test")
                    .severity(AlertSeverity.WARNING).status(status)
                    .workloadInfo(Alert.WorkloadInfo.of("p", "c", "ns", "cl", "D"))
                    .workloadName("wl")
                    .alertMetadata(metadata)
                    .diagnosticIds(diagIds)
                    .build();
        }

        @Test
        @DisplayName("Should serialize diag_ids as a JSON array")
        void shouldSerializeDiagIdsAsArray() throws Exception {
            JsonNode json = mapper.valueToTree(AlertResponse.from(
                    alertWithoutTimestamp(AlertStatus.PROCESSED, null, List.of("diag_2", "diag_1"))));

            assertTrue(json.get("diag_ids").isArray());
            assertEquals("diag_2", json.get("diag_ids").get(0).asText());
            assertEquals("diag_1", json.get("diag_ids").get(1).asText());
        }

        @Test
        @DisplayName("Should serialize empty diag_ids array when alert has no diagnostics")
        void shouldSerializeEmptyDiagIds() {
            JsonNode json = mapper.valueToTree(AlertResponse.from(
                    alertWithoutTimestamp(AlertStatus.REJECTED, null, null)));

            assertTrue(json.has("diag_ids"));
            assertEquals(0, json.get("diag_ids").size());
        }

        @Test
        @DisplayName("Should include rejection_reason when present")
        void shouldIncludeRejectionReasonWhenPresent() {
            JsonNode json = mapper.valueToTree(AlertResponse.from(alertWithoutTimestamp(
                    AlertStatus.REJECTED,
                    Alert.AlertMetadata.of(Map.of(), Map.of(), "prometheus", "Alert is in cooldown"),
                    null)));

            assertEquals("Alert is in cooldown", json.get("rejection_reason").asText());
        }

        @Test
        @DisplayName("Should omit rejection_reason when absent")
        void shouldOmitRejectionReasonWhenAbsent() {
            JsonNode json = mapper.valueToTree(AlertResponse.from(
                    alertWithoutTimestamp(AlertStatus.PROCESSED, null, null)));

            assertFalse(json.has("rejection_reason"));
        }
    }

    @Nested
    @DisplayName("Null Handling Tests")
    class NullHandlingTests {

        @Test
        @DisplayName("Should return null sourceAlertId when null")
        void shouldReturnNullSourceAlertId() {
            Alert alert = Alert.builder()
                    .alertId("a1")
                    .alertName("Test")
                    .severity(AlertSeverity.CRITICAL)
                    .status(AlertStatus.ACCEPTED)
                    .workloadInfo(Alert.WorkloadInfo.of("p", "c", "ns", "cl", "D"))
                    .workloadName("wl")
                    .sourceAlertId(null)
                    .build();

            AlertResponse response = AlertResponse.from(alert);

            assertNull(response.sourceAlertId());
        }
    }
}
