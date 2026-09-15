package com.causa.api.controllers;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.api.dto.response.WebhookResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.api.mappers.AlertMapper;
import com.causa.api.validators.AlertWebhookValidator;
import com.causa.common.constants.ApiConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.services.AlertService;
import com.causa.core.services.DiagnosticService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Webhook Controller
 *
 * <p>POST /api/v1/webhooks/alerts — receives Prometheus Alertmanager webhooks.
 *
 * @since 0.0.1
 */
@Path(ApiConstants.Paths.Webhooks.ALERTS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WebhookController {

    private static final CausaLogger log = CausaLogger.getLogger(WebhookController.class);

    private final AlertService alertService;
    private final DiagnosticService diagnosticService;
    private final AlertMapper alertMapper;
    private final AlertWebhookValidator validator;

    @Inject
    public WebhookController(AlertService alertService,
                              DiagnosticService diagnosticService,
                              AlertMapper alertMapper,
                              AlertWebhookValidator validator) {
        this.alertService = alertService;
        this.diagnosticService = diagnosticService;
        this.alertMapper = alertMapper;
        this.validator = validator;
    }

    @POST
    public Response receiveAlerts(AlertWebhookRequest request) {
        int alertCount = (request != null && request.getAlerts() != null)
            ? request.getAlerts().size() : 0;

        log.info(LogMessages.Alert.WEBHOOK_RECEIVED)
            .field("alertCount", alertCount)
            .field("status",   request != null ? request.getStatus()   : "null")
            .field("receiver", request != null ? request.getReceiver() : "null")
            .log();

        List<String> validationErrors = validator.validate(request);
        if (!validationErrors.isEmpty()) {
            log.warn(LogMessages.Alert.ALERT_VALIDATION_FAILED)
                .field("errors", validationErrors)
                .log();
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(400, "Validation Failed", String.join("; ", validationErrors)))
                .build();
        }

        List<Alert> domainAlerts = alertMapper.toDomainList(request);

        // Process — filter, persist accepted + rejected
        Map<String, String> rejectedReasons = new LinkedHashMap<>();
        List<Alert> accepted = alertService.processAlerts(domainAlerts, rejectedReasons);

        // Trigger diagnostics for accepted alerts
        Map<String, String> acceptedEntries = new LinkedHashMap<>();
        for (Alert alert : accepted) {
            Diagnostic diagnostic = diagnosticService.triggerDiagnostics(alert);
            acceptedEntries.put(alert.getAlertId(), diagnostic.getDiagnosticId());
        }

        WebhookResponse body = WebhookResponse.of(acceptedEntries, rejectedReasons);

        log.info(LogMessages.Alert.WEBHOOK_PROCESSED)
            .field("totalReceived", body.totalReceived())
            .field("totalAccepted", body.totalAccepted())
            .field("totalRejected", body.totalRejected())
            .field("status", body.status())
            .log();

        return Response.ok(body).build();
    }
}
