package com.causa.api.controllers;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.api.dto.response.AlertDetailResponse;
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
import com.causa.core.services.AlertService.ProcessedAlerts;
import com.causa.core.services.DiagnosticService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Alert Controller
 *
 * <p>REST endpoints for Prometheus Alertmanager webhooks and alert retrieval.
 * <ul>
 *   <li>POST /api/v1/webhooks/alerts — ingest webhook payload</li>
 *   <li>GET  /api/v1/alerts?id={alertId} — retrieve alert by ID</li>
 * </ul>
 *
 * @since 0.0.1
 */
@Path("/")
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

    // -------------------------------------------------------------------------
    // POST /api/v1/webhooks/alerts
    // -------------------------------------------------------------------------

    /**
     * Receives and processes alert webhooks from Prometheus Alertmanager.
     *
     * @param request the webhook request payload
     * @return HTTP response with AlertResponse or ErrorResponse
     */
    @POST
    @Path(ApiConstants.Paths.Webhooks.ALERTS)
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

        // Process all alerts — accepted get ACCEPTED status, rejected get REJECTED + reason
        ProcessedAlerts processed = alertService.processAlerts(domainAlerts);

        // Trigger diagnostics for each accepted alert.
        // LLM/MCP failures are swallowed inside triggerDiagnostics — they never fail this request.
        Map<String, String> acceptedEntries = new LinkedHashMap<>();
        for (Alert alert : processed.accepted()) {
            Diagnostic diagnostic = diagnosticService.triggerDiagnostics(alert);
            alertRepository.updateHasDiagnostics(alert.getAlertId(), true);
            acceptedEntries.put(alert.getAlertId(), diagnostic.getDiagnosticId());
        }

        // Build rejected entries: alertId → reason (carried directly from processAlerts)
        Map<String, String> rejectedEntries = new LinkedHashMap<>();
        processed.rejected().forEach((alert, reason) ->
            rejectedEntries.put(alert.getAlertId(), reason));

        AlertResponse response = AlertResponse.of(acceptedEntries, rejectedEntries);

        log.info(LogMessages.Alert.WEBHOOK_PROCESSED)
            .field("totalReceived", response.totalReceived())
            .field("totalAccepted", response.totalAccepted())
            .field("totalRejected", response.totalRejected())
            .field("status", response.status())
            .log();

        return Response.ok(response).build();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/alerts
    // Optional query params:
    //   ?id={alertId}          — return a single alert by ID
    //   ?container={name}      — filter all alerts by container name
    //   (no params)            — return all alerts
    // -------------------------------------------------------------------------

    /**
     * Retrieves alerts with optional filtering.
     *
     * <ul>
     *   <li>If {@code id} is provided — returns a single {@link AlertDetailResponse} or 404.</li>
     *   <li>If {@code container} is provided — returns all alerts for that container.</li>
     *   <li>If neither is provided — returns all alerts.</li>
     * </ul>
     *
     * @param alertId       optional alert ID filter (query param {@code id})
     * @param containerName optional container name filter (query param {@code container})
     * @return 200 with AlertDetailResponse or List of AlertDetailResponse; 404 if id not found
     */
    @GET
    @Path(ApiConstants.Paths.Alerts.BASE)
    public Response getAlerts(
            @QueryParam(ApiConstants.Paths.Alerts.QUERY_ID)        String alertId,
            @QueryParam(ApiConstants.Paths.Alerts.QUERY_CONTAINER)  String containerName) {

        // --- Single alert by ID ---
        if (alertId != null && !alertId.isBlank()) {
            log.info(LogMessages.Alert.ALERT_GET_REQUEST)
                .field("alertId", alertId)
                .log();

            Optional<Alert> found = alertService.getAlert(alertId);

            if (found.isEmpty()) {
                log.warn(LogMessages.Alert.ALERT_GET_NOT_FOUND)
                    .field("alertId", alertId)
                    .log();
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.of(404, "Not Found", "Alert not found: " + alertId))
                    .build();
            }

            log.info(LogMessages.Alert.ALERT_GET_FOUND)
                .field("alertId", alertId)
                .log();

            return Response.ok(AlertDetailResponse.from(found.get())).build();
        }

        // --- List (all or filtered by container) ---
        log.info(LogMessages.Alert.ALERTS_LIST_REQUEST)
            .field("container", containerName)
            .log();

        List<AlertDetailResponse> results = alertService.getAlerts(containerName)
            .stream()
            .map(AlertDetailResponse::from)
            .collect(Collectors.toList());

        log.info(LogMessages.Alert.ALERTS_LIST_FOUND)
            .field("count", results.size())
            .field("container", containerName)
            .log();

        return Response.ok(results).build();
    }
}
