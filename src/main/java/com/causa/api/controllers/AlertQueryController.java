package com.causa.api.controllers;

import com.causa.api.dto.response.AlertDetailsResponse;
import com.causa.api.dto.response.AlertListResponse;
import com.causa.api.dto.response.DiagnosticDetailsResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.api.mappers.AlertResponseMapper;
import com.causa.api.mappers.DiagnosticResponseMapper;
import com.causa.api.validators.PathParamValidator;
import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.ports.AlertRepository;
import com.causa.core.ports.DiagnosticRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;

/**
 * Alert Query Controller
 *
 * <p>REST endpoints for querying historical alerts and their diagnostics.
 * <p>Provides read-only access to stored alert data.
 *
 * @since 0.0.1
 */
@Path("/api/v1/alerts")
@Produces(MediaType.APPLICATION_JSON)
public class AlertQueryController {

    private static final CausaLogger log = CausaLogger.getLogger(AlertQueryController.class);

    private final AlertRepository alertRepository;
    private final DiagnosticRepository diagnosticRepository;

    @Inject
    public AlertQueryController(AlertRepository alertRepository,
                                 DiagnosticRepository diagnosticRepository) {
        this.alertRepository = alertRepository;
        this.diagnosticRepository = diagnosticRepository;
    }

    /**
     * Fetches all historical alerts.
     *
     * <p>GET /api/v1/alerts
     *
     * @return HTTP response with AlertListResponse
     */
    @GET
    public Response getAllAlerts() {
        log.info("Fetching all alerts").log();

        List<Alert> alerts = alertRepository.findAll();
        List<AlertDetailsResponse> responseList = AlertResponseMapper.toResponseList(alerts);

        AlertListResponse response = AlertListResponse.of(responseList);

        log.info("Successfully fetched all alerts")
            .field("totalCount", response.totalCount())
            .log();

        return Response.ok(response).build();
    }

    /**
     * Fetches the diagnostic analysis for a specific alert.
     *
     * <p>GET /api/v1/alerts/{alertId}/diagnostics
     *
     * @param alertId the alert ID
     * @return HTTP response with DiagnosticDetailsResponse or ErrorResponse
     */
    @GET
    @Path("/{alertId}/diagnostics")
    public Response getDiagnosticsByAlertId(@PathParam("alertId") String alertId) {
        log.info("Fetching diagnostics for alert")
            .field("alertId", alertId)
            .log();

        // Validate path parameter
        List<String> validationErrors = PathParamValidator.validateAlertId(alertId);
        if (!validationErrors.isEmpty()) {
            log.warn("Invalid alert ID parameter")
                .field("alertId", alertId)
                .field("errors", validationErrors)
                .log();

            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(400, "Validation Failed",
                    String.join("; ", validationErrors)))
                .build();
        }

        // Fetch diagnostic by alert ID
        Optional<Diagnostic> diagnosticOpt = diagnosticRepository.findByAlertId(alertId);

        if (diagnosticOpt.isEmpty()) {
            log.warn("Diagnostic not found for alert")
                .field("alertId", alertId)
                .log();

            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.of(404, "Not Found",
                    "No diagnostic analysis found for alert: " + alertId))
                .build();
        }

        Diagnostic diagnostic = diagnosticOpt.get();
        DiagnosticDetailsResponse response = DiagnosticResponseMapper.toResponse(diagnostic);

        log.info("Successfully fetched diagnostics for alert")
            .field("alertId", alertId)
            .field("diagnosticId", response.diagnosticId())
            .field("status", response.status())
            .log();

        return Response.ok(response).build();
    }
}
