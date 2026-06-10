package com.causa.api.controllers;

import com.causa.api.dto.response.DiagnosticDetailsResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.api.mappers.DiagnosticResponseMapper;
import com.causa.api.validators.PathParamValidator;
import com.causa.common.constants.ApiConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.Diagnostic;
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
 * Diagnostic Query Controller
 *
 * <p>REST endpoints for querying diagnostic analyses.
 * <p>Provides read-only access to LLM-generated root cause analyses.
 *
 * @since 0.0.1
 */
@Path(ApiConstants.Paths.Alerts.BASE)
@Produces(MediaType.APPLICATION_JSON)
public class DiagnosticQueryController {

    private static final CausaLogger log = CausaLogger.getLogger(DiagnosticQueryController.class);

    private final DiagnosticRepository diagnosticRepository;

    @Inject
    public DiagnosticQueryController(DiagnosticRepository diagnosticRepository) {
        this.diagnosticRepository = diagnosticRepository;
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
