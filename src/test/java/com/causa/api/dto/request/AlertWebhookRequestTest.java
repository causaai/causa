package com.causa.api.dto.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AlertWebhookRequest} and its nested {@link AlertWebhookRequest.AlertItem}.
 *
 * @since 0.0.1
 */
@DisplayName("AlertWebhookRequest Tests")
class AlertWebhookRequestTest {

    @Nested
    @DisplayName("AlertWebhookRequest Field Tests")
    class AlertWebhookRequestFieldTests {

        @Test
        @DisplayName("Should store and retrieve version")
        void shouldStoreVersion() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setVersion("4");

            assertEquals("4", req.getVersion());
        }

        @Test
        @DisplayName("Should store and retrieve status")
        void shouldStoreStatus() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setStatus("firing");

            assertEquals("firing", req.getStatus());
        }

        @Test
        @DisplayName("Should store and retrieve receiver")
        void shouldStoreReceiver() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setReceiver("causa-webhook");

            assertEquals("causa-webhook", req.getReceiver());
        }

        @Test
        @DisplayName("Should store and retrieve groupKey")
        void shouldStoreGroupKey() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setGroupKey("test-group-key");

            assertEquals("test-group-key", req.getGroupKey());
        }

        @Test
        @DisplayName("Should store and retrieve truncatedAlerts count")
        void shouldStoreTruncatedAlerts() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setTruncatedAlerts(3);

            assertEquals(3, req.getTruncatedAlerts());
        }

        @Test
        @DisplayName("Should store and retrieve groupLabels")
        void shouldStoreGroupLabels() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setGroupLabels(Map.of("alertname", "HighCPU"));

            assertNotNull(req.getGroupLabels());
            assertEquals("HighCPU", req.getGroupLabels().get("alertname"));
        }

        @Test
        @DisplayName("Should store and retrieve commonLabels")
        void shouldStoreCommonLabels() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setCommonLabels(Map.of("env", "production"));

            assertEquals("production", req.getCommonLabels().get("env"));
        }

        @Test
        @DisplayName("Should store and retrieve commonAnnotations")
        void shouldStoreCommonAnnotations() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setCommonAnnotations(Map.of("summary", "CPU is high"));

            assertEquals("CPU is high", req.getCommonAnnotations().get("summary"));
        }

        @Test
        @DisplayName("Should store and retrieve externalURL")
        void shouldStoreExternalURL() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setExternalURL("http://alertmanager:9093");

            assertEquals("http://alertmanager:9093", req.getExternalURL());
        }

        @Test
        @DisplayName("Should store and retrieve alerts list")
        void shouldStoreAlertsList() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
            item.setStatus("firing");
            req.setAlerts(List.of(item));

            assertNotNull(req.getAlerts());
            assertEquals(1, req.getAlerts().size());
        }

        @Test
        @DisplayName("Defaults: all fields null except truncatedAlerts (int default 0)")
        void defaultsAllNull() {
            AlertWebhookRequest req = new AlertWebhookRequest();

            assertNull(req.getVersion());
            assertNull(req.getStatus());
            assertNull(req.getReceiver());
            assertNull(req.getAlerts());
            assertEquals(0, req.getTruncatedAlerts());
        }
    }

    @Nested
    @DisplayName("AlertItem Field Tests")
    class AlertItemFieldTests {

        @Test
        @DisplayName("Should store and retrieve status")
        void shouldStoreStatus() {
            AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
            item.setStatus("resolved");

            assertEquals("resolved", item.getStatus());
        }

        @Test
        @DisplayName("Should store and retrieve labels")
        void shouldStoreLabels() {
            AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
            item.setLabels(Map.of("alertname", "CPUHigh", "namespace", "default"));

            assertNotNull(item.getLabels());
            assertEquals("CPUHigh", item.getLabels().get("alertname"));
            assertEquals("default", item.getLabels().get("namespace"));
        }

        @Test
        @DisplayName("Should store and retrieve annotations")
        void shouldStoreAnnotations() {
            AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
            item.setAnnotations(Map.of("container_name", "my-app"));

            assertNotNull(item.getAnnotations());
            assertEquals("my-app", item.getAnnotations().get("container_name"));
        }

        @Test
        @DisplayName("Should store and retrieve startsAt timestamp")
        void shouldStoreStartsAt() {
            AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
            item.setStartsAt("2025-06-01T12:00:00Z");

            assertEquals("2025-06-01T12:00:00Z", item.getStartsAt());
        }

        @Test
        @DisplayName("Should store and retrieve endsAt timestamp")
        void shouldStoreEndsAt() {
            AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
            item.setEndsAt("2025-06-01T13:00:00Z");

            assertEquals("2025-06-01T13:00:00Z", item.getEndsAt());
        }

        @Test
        @DisplayName("Should store and retrieve generatorURL")
        void shouldStoreGeneratorURL() {
            AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
            item.setGeneratorURL("http://prometheus/graph?expr=...");

            assertEquals("http://prometheus/graph?expr=...", item.getGeneratorURL());
        }

        @Test
        @DisplayName("Should store and retrieve fingerprint")
        void shouldStoreFingerprint() {
            AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
            item.setFingerprint("abc123def456");

            assertEquals("abc123def456", item.getFingerprint());
        }

        @Test
        @DisplayName("Defaults: all fields null")
        void defaultsAllNull() {
            AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();

            assertNull(item.getStatus());
            assertNull(item.getLabels());
            assertNull(item.getAnnotations());
            assertNull(item.getStartsAt());
            assertNull(item.getFingerprint());
        }
    }
}
