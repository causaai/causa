package com.causa.api.validators;

import com.causa.api.dto.request.AlertWebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AlertWebhookValidator}.
 *
 * @since 0.0.1
 */
@DisplayName("AlertWebhookValidator Tests")
class AlertWebhookValidatorTest {

    private AlertWebhookValidator clusterValidator;
    private AlertWebhookValidator vmValidator;

    @BeforeEach
    void setUp() {
        clusterValidator = new AlertWebhookValidator("cluster");
        vmValidator      = new AlertWebhookValidator("vm");
    }

    private AlertWebhookRequest buildValidClusterRequest() {
        AlertWebhookRequest req = new AlertWebhookRequest();
        req.setVersion("4");
        req.setStatus("firing");
        req.setReceiver("receiver");

        AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
        item.setStatus("firing");
        item.setLabels(Map.of("alertname", "CPUHigh", "container", "my-container", "namespace", "default", "pod", "my-pod-abc"));
        item.setAnnotations(Map.of());
        req.setAlerts(List.of(item));
        return req;
    }

    private AlertWebhookRequest buildValidVmRequest() {
        AlertWebhookRequest req = new AlertWebhookRequest();
        req.setVersion("4");
        req.setStatus("firing");
        req.setReceiver("receiver");

        AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
        item.setStatus("firing");
        item.setLabels(Map.of("alertname", "HighCPU"));
        item.setAnnotations(Map.of("workload_name", "my-vm-workload"));
        req.setAlerts(List.of(item));
        return req;
    }

    // -------------------------------------------------------------------------
    // Null / empty request
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Null / Empty Request Tests")
    class NullEmptyRequestTests {

        @Test
        @DisplayName("Should return error for null request")
        void shouldReturnErrorForNullRequest() {
            List<String> errors = clusterValidator.validate(null);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).contains("null"));
        }

        @Test
        @DisplayName("Should return error when alerts list is null")
        void shouldReturnErrorWhenAlertsListNull() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setAlerts(null);

            List<String> errors = clusterValidator.validate(req);

            assertFalse(errors.isEmpty());
            assertTrue(errors.get(0).contains("Alerts array"));
        }

        @Test
        @DisplayName("Should return error when alerts list is empty")
        void shouldReturnErrorWhenAlertsListEmpty() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setAlerts(List.of());

            List<String> errors = clusterValidator.validate(req);

            assertFalse(errors.isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // Version validation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Version Validation Tests")
    class VersionValidationTests {

        @Test
        @DisplayName("Should return error for unsupported Alertmanager version")
        void shouldReturnErrorForUnsupportedVersion() {
            AlertWebhookRequest req = buildValidClusterRequest();
            req.setVersion("3");

            List<String> errors = clusterValidator.validate(req);

            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("Unsupported Alertmanager version")));
        }

        @Test
        @DisplayName("Should pass validation for supported version 4")
        void shouldPassForSupportedVersion4() {
            AlertWebhookRequest req = buildValidClusterRequest();
            req.setVersion("4");

            List<String> errors = clusterValidator.validate(req);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should skip version check when version is null")
        void shouldSkipVersionCheckWhenVersionNull() {
            AlertWebhookRequest req = buildValidClusterRequest();
            req.setVersion(null);

            List<String> errors = clusterValidator.validate(req);

            assertTrue(errors.isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // Alert item validation — cluster platform
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Alert Item Validation (cluster platform)")
    class AlertItemClusterValidationTests {

        @Test
        @DisplayName("Should pass for valid cluster alert item")
        void shouldPassForValidClusterAlertItem() {
            List<String> errors = clusterValidator.validate(buildValidClusterRequest());

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should return error when alert item is null")
        void shouldReturnErrorForNullAlertItem() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setAlerts(java.util.Arrays.asList((AlertWebhookRequest.AlertItem) null));
            req.setVersion("4");

            List<String> errors = clusterValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("is null")));
        }

        @Test
        @DisplayName("Should return error when status is missing")
        void shouldReturnErrorWhenStatusMissing() {
            AlertWebhookRequest req = buildValidClusterRequest();
            req.getAlerts().get(0).setStatus(null);

            List<String> errors = clusterValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("status is required")));
        }

        @Test
        @DisplayName("Should return error when status is blank")
        void shouldReturnErrorWhenStatusBlank() {
            AlertWebhookRequest req = buildValidClusterRequest();
            req.getAlerts().get(0).setStatus("  ");

            List<String> errors = clusterValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("status is required")));
        }

        @Test
        @DisplayName("Should return error when labels are null")
        void shouldReturnErrorWhenLabelsNull() {
            AlertWebhookRequest req = buildValidClusterRequest();
            req.getAlerts().get(0).setLabels(null);

            List<String> errors = clusterValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("labels is required")));
        }

        @Test
        @DisplayName("Should return error when labels are empty")
        void shouldReturnErrorWhenLabelsEmpty() {
            AlertWebhookRequest req = buildValidClusterRequest();
            req.getAlerts().get(0).setLabels(Map.of());

            List<String> errors = clusterValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("labels is required")));
        }

        @Test
        @DisplayName("Should return error when alertname label is missing")
        void shouldReturnErrorWhenAlertnamesMissing() {
            AlertWebhookRequest req = buildValidClusterRequest();
            req.getAlerts().get(0).setLabels(Map.of("container", "c", "namespace", "ns"));

            List<String> errors = clusterValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("alertname")));
        }

        @Test
        @DisplayName("Should return error when container label is missing and not in annotations (cluster platform)")
        void shouldReturnErrorWhenContainerMissing() {
            AlertWebhookRequest req = buildValidClusterRequest();
            req.getAlerts().get(0).setLabels(Map.of("alertname", "Test", "namespace", "ns"));
            req.getAlerts().get(0).setAnnotations(Map.of("pod_name", "my-pod"));

            List<String> errors = clusterValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("container")));
        }

        @Test
        @DisplayName("Should return error when namespace is missing from both labels and annotations (cluster platform)")
        void shouldReturnErrorWhenNamespaceMissing() {
            AlertWebhookRequest req = buildValidClusterRequest();
            req.getAlerts().get(0).setLabels(Map.of("alertname", "Test", "container", "c"));
            req.getAlerts().get(0).setAnnotations(Map.of("pod_name", "my-pod"));

            List<String> errors = clusterValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("namespace")));
        }

        @Test
        @DisplayName("Should return error when pod missing from labels (cluster platform)")
        void shouldReturnErrorWhenPodNameMissing() {
            AlertWebhookRequest req = buildValidClusterRequest();
            req.getAlerts().get(0).setLabels(Map.of("alertname", "Test", "container", "c", "namespace", "ns"));

            List<String> errors = clusterValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("pod")));
        }

        @Test
        @DisplayName("Should return error when container is only in annotations, not labels (cluster platform)")
        void shouldPassWhenContainerInAnnotations() {
            AlertWebhookRequest req = buildValidClusterRequest();
            req.getAlerts().get(0).setLabels(Map.of("alertname", "Test", "namespace", "default", "pod", "my-pod"));
            req.getAlerts().get(0).setAnnotations(Map.of("container_name", "my-container"));

            List<String> errors = clusterValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("container")));
        }
    }

    // -------------------------------------------------------------------------
    // Alert item validation — vm platform
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Alert Item Validation (vm platform)")
    class AlertItemVmValidationTests {

        @Test
        @DisplayName("Should pass for valid VM alert item")
        void shouldPassForValidVmAlertItem() {
            List<String> errors = vmValidator.validate(buildValidVmRequest());

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should return error when workload_name annotation missing (vm platform)")
        void shouldReturnErrorWhenWorkloadNameMissing() {
            AlertWebhookRequest req = buildValidVmRequest();
            req.getAlerts().get(0).setAnnotations(Map.of());

            List<String> errors = vmValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("workload_name")));
        }

        @Test
        @DisplayName("Should return error when annotations are null (vm platform)")
        void shouldReturnErrorWhenAnnotationsNull() {
            AlertWebhookRequest req = buildValidVmRequest();
            req.getAlerts().get(0).setAnnotations(null);

            List<String> errors = vmValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("workload_name")));
        }

        @Test
        @DisplayName("Should NOT require container label for VM platform")
        void shouldNotRequireContainerForVmPlatform() {
            AlertWebhookRequest req = buildValidVmRequest();
            // No container in labels — should still pass
            List<String> errors = vmValidator.validate(req);

            assertTrue(errors.stream().noneMatch(e -> e.contains("container")));
        }

        @Test
        @DisplayName("Should pass when workload_name exists only in annotations (vm platform)")
        void shouldPassWhenWorkloadNameFromAnnotations() {
            AlertWebhookRequest req = buildValidVmRequest();
            req.getAlerts().get(0).setLabels(Map.of("alertname", "Test"));
            req.getAlerts().get(0).setAnnotations(Map.of("workload_name", "my-workload"));

            List<String> errors = vmValidator.validate(req);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should return error when workload_name absent from annotations but present as label only (vm platform)")
        void shouldPassWhenWorkloadNameFromLabel() {
            AlertWebhookRequest req = buildValidVmRequest();
            req.getAlerts().get(0).setLabels(Map.of("alertname", "Test", "workload_name", "my-workload"));
            req.getAlerts().get(0).setAnnotations(Map.of("other_key", "value"));

            List<String> errors = vmValidator.validate(req);

            // workload_name must be in annotations; label-only is not accepted
            assertTrue(errors.stream().anyMatch(e -> e.contains("workload_name")));
        }

        @Test
        @DisplayName("Should return error when workload_name missing from both annotations and labels (vm platform)")
        void shouldReturnErrorWhenWorkloadNameMissingFromBoth() {
            AlertWebhookRequest req = buildValidVmRequest();
            req.getAlerts().get(0).setLabels(Map.of("alertname", "Test"));
            req.getAlerts().get(0).setAnnotations(Map.of("other_key", "value"));

            List<String> errors = vmValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("workload_name")));
        }

        @Test
        @DisplayName("Should return error when workload_name is blank in annotations (vm platform)")
        void shouldReturnErrorWhenWorkloadNameIsBlank() {
            AlertWebhookRequest req = buildValidVmRequest();
            req.getAlerts().get(0).setLabels(Map.of("alertname", "Test"));
            // Note: Map.of() does not allow blank values; use HashMap
            java.util.Map<String, String> annotations = new java.util.HashMap<>();
            annotations.put("workload_name", "  ");
            req.getAlerts().get(0).setAnnotations(annotations);

            List<String> errors = vmValidator.validate(req);

            assertTrue(errors.stream().anyMatch(e -> e.contains("workload_name")));
        }
    }

    // -------------------------------------------------------------------------
    // Platform normalization
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Platform Normalization Tests")
    class PlatformNormalizationTests {

        @Test
        @DisplayName("Should treat 'VM' the same as 'vm' (case-insensitive)")
        void shouldNormalizePlatformCase() {
            AlertWebhookValidator upperCaseVm = new AlertWebhookValidator("VM");

            List<String> errors = upperCaseVm.validate(buildValidVmRequest());

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should use cluster rules for null platform")
        void shouldUseClusterRulesForNullPlatform() {
            AlertWebhookValidator nullPlatform = new AlertWebhookValidator(null);

            // cluster rules apply: container + namespace required
            List<String> errors = nullPlatform.validate(buildValidClusterRequest());

            assertTrue(errors.isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // Multiple errors
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Multiple Errors Tests")
    class MultipleErrorsTests {

        @Test
        @DisplayName("Should accumulate multiple errors for multiple invalid items")
        void shouldAccumulateMultipleErrors() {
            AlertWebhookRequest req = new AlertWebhookRequest();
            req.setVersion("4");
            AlertWebhookRequest.AlertItem item1 = new AlertWebhookRequest.AlertItem();
            item1.setStatus("firing");
            item1.setLabels(Map.of("alertname", "A1", "container", "c", "namespace", "ns", "pod", "pod-1"));
            item1.setAnnotations(Map.of());

            AlertWebhookRequest.AlertItem item2 = new AlertWebhookRequest.AlertItem();
            item2.setStatus(null);  // missing status
            item2.setLabels(Map.of("alertname", "A2", "container", "c", "namespace", "ns", "pod", "pod-2"));
            item2.setAnnotations(Map.of());

            req.setAlerts(List.of(item1, item2));

            List<String> errors = clusterValidator.validate(req);

            // At least one error for the second item's missing status
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("alerts[1]")));
        }
    }
}
