package com.causa.api.mappers;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.config.AlertConfig;
import com.causa.core.domain.Alert;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AlertMapper}.
 *
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertMapper Tests")
class AlertMapperTest {

    @Mock
    private AlertConfig alertConfig;

    private AlertMapper alertMapper;

    @BeforeEach
    void setUp() {
        when(alertConfig.filterSeverity()).thenReturn("warning");
        alertMapper = new AlertMapper(alertConfig);
    }

    private AlertWebhookRequest.AlertItem buildItem(Map<String, String> labels, Map<String, String> annotations) {
        AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
        item.setStatus("firing");
        item.setLabels(labels);
        item.setAnnotations(annotations);
        item.setStartsAt("2025-01-01T00:00:00Z");
        item.setFingerprint("fp-abc123");
        return item;
    }

    private AlertWebhookRequest buildRequest(AlertWebhookRequest.AlertItem... items) {
        AlertWebhookRequest req = new AlertWebhookRequest();
        req.setVersion("4");
        req.setStatus("firing");
        req.setReceiver("receiver");
        req.setAlerts(List.of(items));
        return req;
    }

    // -------------------------------------------------------------------------
    // toDomainList
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("toDomainList Tests")
    class ToDomainListTests {

        @Test
        @DisplayName("Should return empty list when request is null")
        void shouldReturnEmptyListForNullRequest() {
            List<Alert> result = alertMapper.toDomainList(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when alerts list is null")
        void shouldReturnEmptyListForNullAlerts() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setAlerts(null);
            List<Alert> result = alertMapper.toDomainList(req);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should map one alert item")
        void shouldMapOneAlertItem() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "CPUHigh", "severity", "critical",
                           "container", "my-container", "namespace", "default"),
                    Map.of()
            );
            List<Alert> result = alertMapper.toDomainList(buildRequest(item));

            assertEquals(1, result.size());
            assertEquals("CPUHigh", result.get(0).getAlertName());
        }

        @Test
        @DisplayName("Should map multiple alert items")
        void shouldMapMultipleAlertItems() {
            AlertWebhookRequest.AlertItem item1 = buildItem(
                    Map.of("alertname", "Alert1", "container", "c1", "namespace", "ns1"),
                    Map.of()
            );
            AlertWebhookRequest.AlertItem item2 = buildItem(
                    Map.of("alertname", "Alert2", "container", "c2", "namespace", "ns2"),
                    Map.of()
            );
            List<Alert> result = alertMapper.toDomainList(buildRequest(item1, item2));

            assertEquals(2, result.size());
        }
    }

    // -------------------------------------------------------------------------
    // toDomain — field mapping
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("toDomain Field Mapping Tests")
    class ToDomainFieldMappingTests {

        @Test
        @DisplayName("Should extract alertname from labels")
        void shouldExtractAlertNameFromLabels() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "HighMemory", "container", "app", "namespace", "prod"),
                    Map.of()
            );
            Alert alert = alertMapper.toDomain(item);

            assertEquals("HighMemory", alert.getAlertName());
        }

        @Test
        @DisplayName("Should extract severity from labels")
        void shouldExtractSeverityFromLabels() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "severity", "critical", "container", "c", "namespace", "ns"),
                    Map.of()
            );
            Alert alert = alertMapper.toDomain(item);

            assertEquals(AlertSeverity.CRITICAL, alert.getSeverity());
        }

        @Test
        @DisplayName("Should use default severity when severity label is missing")
        void shouldUseDefaultSeverityWhenMissing() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "c", "namespace", "ns"),
                    Map.of()
            );
            Alert alert = alertMapper.toDomain(item);

            // Default from alertConfig.filterSeverity() = "warning"
            assertEquals(AlertSeverity.WARNING, alert.getSeverity());
        }

        @Test
        @DisplayName("Should extract container name from annotation (higher priority)")
        void shouldExtractContainerNameFromAnnotation() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "label-container", "namespace", "ns"),
                    Map.of("container_name", "annotation-container")
            );
            Alert alert = alertMapper.toDomain(item);

            assertEquals("annotation-container", alert.getWorkloadInfo().containerName());
        }

        @Test
        @DisplayName("Should fall back to label container when annotation is absent")
        void shouldFallBackToLabelContainer() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "label-container", "namespace", "ns"),
                    Map.of()
            );
            Alert alert = alertMapper.toDomain(item);

            assertEquals("label-container", alert.getWorkloadInfo().containerName());
        }

        @Test
        @DisplayName("Should extract namespace from labels")
        void shouldExtractNamespaceFromLabels() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "c", "namespace", "production"),
                    Map.of()
            );
            Alert alert = alertMapper.toDomain(item);

            assertEquals("production", alert.getWorkloadInfo().namespace());
        }

        @Test
        @DisplayName("Should extract pod name from annotation (higher priority)")
        void shouldExtractPodNameFromAnnotation() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "c", "namespace", "ns", "pod", "label-pod"),
                    Map.of("pod_name", "annotation-pod")
            );
            Alert alert = alertMapper.toDomain(item);

            assertEquals("annotation-pod", alert.getWorkloadInfo().podName());
        }

        @Test
        @DisplayName("Should set fingerprint as sourceAlertId")
        void shouldSetFingerprintAsSourceAlertId() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "c", "namespace", "ns"),
                    Map.of()
            );
            item.setFingerprint("fingerprint-xyz");
            Alert alert = alertMapper.toDomain(item);

            assertEquals("fingerprint-xyz", alert.getSourceAlertId());
        }

        @Test
        @DisplayName("Should set null sourceAlertId when fingerprint is blank")
        void shouldSetNullSourceAlertIdWhenFingerprintBlank() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "c", "namespace", "ns"),
                    Map.of()
            );
            item.setFingerprint("  ");
            Alert alert = alertMapper.toDomain(item);

            assertNull(alert.getSourceAlertId());
        }

        @Test
        @DisplayName("Should parse valid ISO timestamp")
        void shouldParseValidTimestamp() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "c", "namespace", "ns"),
                    Map.of()
            );
            item.setStartsAt("2025-06-01T12:00:00Z");
            Alert alert = alertMapper.toDomain(item);

            assertEquals(Instant.parse("2025-06-01T12:00:00Z"), alert.getAlertTimestamp());
        }

        @Test
        @DisplayName("Should fallback to now for null timestamp")
        void shouldFallbackToNowForNullTimestamp() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "c", "namespace", "ns"),
                    Map.of()
            );
            item.setStartsAt(null);
            Instant before = Instant.now();
            Alert alert = alertMapper.toDomain(item);
            Instant after = Instant.now();

            assertNotNull(alert.getAlertTimestamp());
            assertTrue(alert.getAlertTimestamp().isAfter(before.minusSeconds(1)));
            assertTrue(alert.getAlertTimestamp().isBefore(after.plusSeconds(1)));
        }

        @Test
        @DisplayName("Should fallback to now for invalid timestamp")
        void shouldFallbackToNowForInvalidTimestamp() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "c", "namespace", "ns"),
                    Map.of()
            );
            item.setStartsAt("not-a-date");
            Instant before = Instant.now();
            Alert alert = alertMapper.toDomain(item);

            assertNotNull(alert.getAlertTimestamp());
            assertTrue(alert.getAlertTimestamp().isAfter(before.minusSeconds(1)));
        }

        @Test
        @DisplayName("Should set PROCESSING status for all mapped alerts")
        void shouldSetProcessingStatusForMappedAlerts() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "c", "namespace", "ns"),
                    Map.of()
            );
            Alert alert = alertMapper.toDomain(item);

            assertEquals(com.causa.common.constants.AlertConstants.AlertStatus.PROCESSING, alert.getStatus());
        }

        @Test
        @DisplayName("Should use workload_name annotation as workloadName")
        void shouldUseWorkloadNameAnnotation() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "c", "namespace", "ns"),
                    Map.of("workload_name", "my-workload")
            );
            Alert alert = alertMapper.toDomain(item);

            assertEquals("my-workload", alert.getWorkloadName());
        }

        @Test
        @DisplayName("Should fallback to containerName as workloadName when workload_name absent")
        void shouldFallbackToContainerNameAsWorkloadName() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "my-container", "namespace", "ns"),
                    Map.of()
            );
            Alert alert = alertMapper.toDomain(item);

            assertEquals("my-container", alert.getWorkloadName());
        }

        @Test
        @DisplayName("Should extract alert_source from annotations")
        void shouldExtractAlertSourceFromAnnotations() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "c", "namespace", "ns"),
                    Map.of("alert_source", "custom-source")
            );
            Alert alert = alertMapper.toDomain(item);

            assertEquals("custom-source", alert.getAlertMetadata().alertSource());
        }

        @Test
        @DisplayName("Should default alert_source to prometheus when not in annotations")
        void shouldDefaultAlertSourceToPrometheus() {
            AlertWebhookRequest.AlertItem item = buildItem(
                    Map.of("alertname", "Test", "container", "c", "namespace", "ns"),
                    Map.of()
            );
            Alert alert = alertMapper.toDomain(item);

            assertEquals("prometheus", alert.getAlertMetadata().alertSource());
        }

        @Test
        @DisplayName("Should handle null annotations by falling back to empty map")
        void shouldHandleNullAnnotations() {
            // Null annotations: the mapper treats them as empty map and falls back to labels
            AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
            item.setStatus("firing");
            item.setLabels(Map.of("alertname", "Test", "container", "c", "namespace", "ns"));
            item.setAnnotations(null); // null annotations — should fall back to labels gracefully

            Alert alert = alertMapper.toDomain(item);

            assertNotNull(alert);
            assertEquals("Test", alert.getAlertName());
            // alert_source defaults to "prometheus" when annotations are null
            assertEquals("prometheus", alert.getAlertMetadata().alertSource());
        }
    }
}
