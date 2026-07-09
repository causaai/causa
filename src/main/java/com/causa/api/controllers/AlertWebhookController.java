package com.causa.api.controllers;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.api.dto.response.AlertResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.api.mappers.AlertMapper;
import com.causa.api.validators.AlertWebhookValidator;
import com.causa.common.constants.ApiConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.ports.AlertRepository;
import com.causa.core.services.AlertService;
import com.causa.core.services.DiagnosticService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Alert Webhook Controller
 *
 * <p>REST endpoint for receiving Prometheus Alertmanager webhooks.
 * <p>Endpoint: POST /api/v1/webhooks/alerts
 *
 * @since 0.0.1
 */
@Path(ApiConstants.Paths.Webhooks.ALERTS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AlertWebhookController {

    private static final CausaLogger log = CausaLogger.getLogger(AlertWebhookController.class);

    private final AlertService alertService;
    private final DiagnosticService diagnosticService;
    private final AlertRepository alertRepository;
    private final AlertMapper alertMapper;

    @Inject
    public AlertWebhookController(AlertService alertService,
                                   DiagnosticService diagnosticService,
                                   AlertRepository alertRepository,
                                   AlertMapper alertMapper) {
        this.alertService = alertService;
        this.diagnosticService = diagnosticService;
        this.alertRepository = alertRepository;
        this.alertMapper = alertMapper;
    }

    /**
     * Receives and processes alert webhooks from Prometheus Alertmanager.
     *
     * @param request the webhook request payload
     * @return HTTP response with AlertResponse or ErrorResponse
     */
    @POST
    public Response receiveAlerts(AlertWebhookRequest request) {
        int alertCount = (request != null && request.getAlerts() != null)
            ? request.getAlerts().size()
            : 0;

        log.info(LogMessages.Alert.WEBHOOK_RECEIVED)
            .field("alertCount", alertCount)
            .field("status", request != null ? request.getStatus() : "null")
            .field("receiver", request != null ? request.getReceiver() : "null")
            .field("rawPayload", request)
            .log();

        // Validate request
        List<String> validationErrors = AlertWebhookValidator.validate(request);
        if (!validationErrors.isEmpty()) {
            log.warn(LogMessages.Alert.ALERT_VALIDATION_FAILED)
                .field("errors", validationErrors)
                .log();

            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(400, "Validation Failed",
                    String.join("; ", validationErrors)))
                .build();
        }

        // Map DTO to domain
        List<Alert> domainAlerts = alertMapper.toDomainList(request);

        // Process through service (filtering, cooldown, persistence)
        List<Alert> accepted = alertService.processAlerts(domainAlerts);

        // Trigger diagnostics for each accepted alert
        List<Diagnostic> diagnostics = accepted.stream()
            .map(alert -> {
                Diagnostic diagnostic = diagnosticService.triggerDiagnostics(alert);

                // Update alert to mark it has diagnostics
                alertRepository.updateHasDiagnostics(alert.getAlertId(), true);

                return diagnostic;
            })
            .toList();

        // Build response
        List<String> acceptedIds = accepted.stream()
            .map(Alert::getAlertId)
            .toList();

        List<String> diagnosticIds = diagnostics.stream()
            .map(Diagnostic::getDiagnosticId)
            .toList();

        AlertResponse response = AlertResponse.accepted(acceptedIds, diagnosticIds, domainAlerts.size());

        log.info(LogMessages.Alert.WEBHOOK_PROCESSED)
            .field("totalReceived", response.totalReceived())
            .field("totalAccepted", response.totalAccepted())
            .field("totalFiltered", response.totalFiltered())
            .field("diagnosticsTriggered", diagnosticIds.size())
            .field("status", response.status())
            .log();

        return Response.ok(response).build();
    }
}
