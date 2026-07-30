package com.causa.api.controllers;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.api.dto.response.WebhookResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.api.mappers.AlertMapper;
import com.causa.api.validators.AlertWebhookValidator;
import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.services.AlertService;
import com.causa.core.services.DiagnosticService;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WebhookController}.
 *
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookController Tests")
class WebhookControllerTest {

    @Mock
    private AlertService alertService;

    @Mock
    private DiagnosticService diagnosticService;

    @Mock
    private AlertMapper alertMapper;

    @Mock
    private AlertWebhookValidator validator;

    private WebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new WebhookController(alertService, diagnosticService, alertMapper, validator);
    }

    private AlertWebhookRequest buildRequest(int alertCount) {
        AlertWebhookRequest req = new AlertWebhookRequest();
        req.setVersion("4");
        req.setStatus("firing");
        req.setReceiver("causa-receiver");
        List<AlertWebhookRequest.AlertItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < alertCount; i++) {
            AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
            item.setStatus("firing");
            item.setLabels(Map.of("alertname", "TestAlert_" + i, "container", "c-" + i, "namespace", "default"));
            item.setFingerprint("fp-" + i);
            items.add(item);
        }
        req.setAlerts(items);
        return req;
    }

    private Alert buildDomainAlert(String id) {
        return Alert.builder()
                .alertId(id)
                .alertName("TestAlert")
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.ACCEPTED)
                .workloadInfo(Alert.WorkloadInfo.of("pod-1", "container-1", "default", "cluster-1", "Deployment"))
                .workloadName("container-1")
                .build();
    }

    private Diagnostic buildDiagnostic(String diagId, String alertId) {
        return Diagnostic.builder()
                .diagnosticId(diagId)
                .alertId(alertId)
                .status(DiagnosticStatus.PENDING)
                .generatedAt(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // Validation Failures
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Request Validation Tests")
    class RequestValidationTests {

        @Test
        @DisplayName("Should return 400 when validation errors exist")
        void shouldReturn400WhenValidationFails() {
            AlertWebhookRequest req = buildRequest(0);
            when(validator.validate(req)).thenReturn(List.of("Alerts array is null or empty"));

            Response response = controller.receiveAlerts(req);

            assertEquals(400, response.getStatus());
            verifyNoInteractions(alertMapper, alertService, diagnosticService);
        }

        @Test
        @DisplayName("Should return 400 for multiple validation errors")
        void shouldReturn400ForMultipleValidationErrors() {
            AlertWebhookRequest req = buildRequest(1);
            when(validator.validate(req)).thenReturn(
                    List.of("alerts[0].status is required", "alerts[0].labels must contain 'alertname'"));

            Response response = controller.receiveAlerts(req);

            assertEquals(400, response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("alerts[0]"));
        }
    }

    // -------------------------------------------------------------------------
    // Successful processing
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Successful Processing Tests")
    class SuccessfulProcessingTests {

        @Test
        @DisplayName("Should return 200 when all alerts are accepted")
        void shouldReturn200WhenAllAlertsAccepted() {
            AlertWebhookRequest req = buildRequest(2);
            when(validator.validate(req)).thenReturn(List.of());

            Alert a1 = buildDomainAlert("alert-1");
            Alert a2 = buildDomainAlert("alert-2");
            when(alertMapper.toDomainList(req)).thenReturn(List.of(a1, a2));

            when(alertService.processAlerts(anyList(), anyMap())).thenAnswer(inv -> {
                // Both accepted, nothing added to rejectedReasons
                return List.of(a1, a2);
            });

            Diagnostic d1 = buildDiagnostic("diag-1", "alert-1");
            Diagnostic d2 = buildDiagnostic("diag-2", "alert-2");
            when(diagnosticService.triggerDiagnostics(a1)).thenReturn(d1);
            when(diagnosticService.triggerDiagnostics(a2)).thenReturn(d2);

            Response response = controller.receiveAlerts(req);

            assertEquals(200, response.getStatus());
            WebhookResponse body = (WebhookResponse) response.getEntity();
            assertEquals(2, body.totalAccepted());
            assertEquals(0, body.totalRejected());
            assertEquals("accepted", body.status());
        }

        @Test
        @DisplayName("Should return 200 with partial status when some alerts rejected")
        void shouldReturn200WhenPartiallyAccepted() {
            AlertWebhookRequest req = buildRequest(2);
            when(validator.validate(req)).thenReturn(List.of());

            Alert a1 = buildDomainAlert("alert-1");
            when(alertMapper.toDomainList(req)).thenReturn(List.of(a1, buildDomainAlert("alert-2")));

            when(alertService.processAlerts(anyList(), anyMap())).thenAnswer(inv -> {
                Map<String, String> rejected = inv.getArgument(1);
                rejected.put("alert-2", "Severity too low");
                return List.of(a1);
            });

            Diagnostic d1 = buildDiagnostic("diag-1", "alert-1");
            when(diagnosticService.triggerDiagnostics(a1)).thenReturn(d1);

            Response response = controller.receiveAlerts(req);

            assertEquals(200, response.getStatus());
            WebhookResponse body = (WebhookResponse) response.getEntity();
            assertEquals(1, body.totalAccepted());
            assertEquals(1, body.totalRejected());
            assertEquals("partial", body.status());
        }

        @Test
        @DisplayName("Should return 200 with rejected status when all alerts rejected")
        void shouldReturn200WhenAllRejected() {
            AlertWebhookRequest req = buildRequest(1);
            when(validator.validate(req)).thenReturn(List.of());

            Alert a1 = buildDomainAlert("alert-1");
            when(alertMapper.toDomainList(req)).thenReturn(List.of(a1));

            when(alertService.processAlerts(anyList(), anyMap())).thenAnswer(inv -> {
                Map<String, String> rejected = inv.getArgument(1);
                rejected.put("alert-1", "Severity too low");
                return List.of();
            });

            Response response = controller.receiveAlerts(req);

            assertEquals(200, response.getStatus());
            WebhookResponse body = (WebhookResponse) response.getEntity();
            assertEquals(0, body.totalAccepted());
            assertEquals(1, body.totalRejected());
            assertEquals("rejected", body.status());
            verifyNoInteractions(diagnosticService);
        }

        @Test
        @DisplayName("Should return 200 with empty status when request has no alerts but passes validation")
        void shouldReturn200WithEmptyStatus() {
            AlertWebhookRequest req = buildRequest(0);
            when(validator.validate(req)).thenReturn(List.of());
            when(alertMapper.toDomainList(req)).thenReturn(List.of());
            when(alertService.processAlerts(anyList(), anyMap())).thenReturn(List.of());

            Response response = controller.receiveAlerts(req);

            assertEquals(200, response.getStatus());
            WebhookResponse body = (WebhookResponse) response.getEntity();
            assertEquals("empty", body.status());
        }

        @Test
        @DisplayName("Should trigger diagnostics once per accepted alert")
        void shouldTriggerDiagnosticsForEachAcceptedAlert() {
            AlertWebhookRequest req = buildRequest(3);
            when(validator.validate(req)).thenReturn(List.of());

            Alert a1 = buildDomainAlert("alert-1");
            Alert a2 = buildDomainAlert("alert-2");
            Alert a3 = buildDomainAlert("alert-3");
            when(alertMapper.toDomainList(req)).thenReturn(List.of(a1, a2, a3));
            when(alertService.processAlerts(anyList(), anyMap())).thenReturn(List.of(a1, a2, a3));
            when(diagnosticService.triggerDiagnostics(any())).thenReturn(buildDiagnostic("d", "a"));

            controller.receiveAlerts(req);

            verify(diagnosticService, times(3)).triggerDiagnostics(any(Alert.class));
        }

        @Test
        @DisplayName("Should include accepted alert-to-diagnostic mapping in response")
        void shouldIncludeAlertToDiagnosticMappingInResponse() {
            AlertWebhookRequest req = buildRequest(1);
            when(validator.validate(req)).thenReturn(List.of());

            Alert a1 = buildDomainAlert("alert-abc");
            when(alertMapper.toDomainList(req)).thenReturn(List.of(a1));
            when(alertService.processAlerts(anyList(), anyMap())).thenReturn(List.of(a1));
            when(diagnosticService.triggerDiagnostics(a1)).thenReturn(buildDiagnostic("diag-xyz", "alert-abc"));

            Response response = controller.receiveAlerts(req);

            WebhookResponse body = (WebhookResponse) response.getEntity();
            assertTrue(body.accepted().containsKey("alert-abc"));
            assertEquals("diag-xyz", body.accepted().get("alert-abc"));
        }
    }

    // -------------------------------------------------------------------------
    // Null / edge cases
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Null and Edge Case Tests")
    class NullEdgeCaseTests {

        @Test
        @DisplayName("Should handle null request body gracefully")
        void shouldHandleNullRequest() {
            when(validator.validate(null)).thenReturn(List.of("Request body is null"));

            Response response = controller.receiveAlerts(null);

            assertEquals(400, response.getStatus());
        }

        @Test
        @DisplayName("Should log alert count of 0 for null request")
        void shouldLogAlertCountZeroForNull() {
            when(validator.validate(null)).thenReturn(List.of("Request body is null"));

            // Just verify no NPE thrown
            assertDoesNotThrow(() -> controller.receiveAlerts(null));
        }
    }
}
