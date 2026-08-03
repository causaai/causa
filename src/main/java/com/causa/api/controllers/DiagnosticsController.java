package com.causa.api.controllers;

import com.causa.api.dto.response.DiagnosticDetailResponse;
import com.causa.api.dto.response.DiagnosticListItemResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.common.constants.ApiConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.ports.AlertRepository;
import com.causa.core.services.DiagnosticService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

/**
 * Diagnostics Controller
 *
 * <p>GET /api/v1/diagnostics        — list all diagnostics (summary)
 * <p>GET /api/v1/diagnostics/{id}   — full diagnostic detail
 *
 * @since 0.0.1
 */
@Path(ApiConstants.Paths.Diagnostics.BASE)
@Produces(MediaType.APPLICATION_JSON)
public class DiagnosticsController {

    private static final CausaLogger log = CausaLogger.getLogger(DiagnosticsController.class);

    private final DiagnosticService diagnosticService;
    private final AlertRepository alertRepository;
    private final String clusterName;

    @Inject
    public DiagnosticsController(DiagnosticService diagnosticService,
                                  AlertRepository alertRepository,
                                  @ConfigProperty(name = "causa.cluster.name", defaultValue = "default")
                                  String clusterName) {
        this.diagnosticService = diagnosticService;
        this.alertRepository   = alertRepository;
        this.clusterName       = clusterName;
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/diagnostics
    // -------------------------------------------------------------------------

    @GET
    public Response listDiagnostics() {
        log.info(LogMessages.Diagnostic.DIAGNOSTICS_LIST_REQUEST).log();

        List<Diagnostic> diagnostics = diagnosticService.listDiagnostics();

        List<DiagnosticListItemResponse> items = diagnostics.stream()
            .map(d -> {
                Alert alert = alertRepository.findById(d.getAlertId()).orElse(null);
                return DiagnosticListItemResponse.from(d, alert, clusterName);
            })
            .toList();

        log.info(LogMessages.Diagnostic.DIAGNOSTICS_LIST_RETURNED)
            .field("count", items.size())
            .log();

        return Response.ok(items).build();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/diagnostics/{id}
    // -------------------------------------------------------------------------

    @GET
    @Path(ApiConstants.Paths.Diagnostics.BY_ID)
    public Response getDiagnostic(
            @PathParam(ApiConstants.Paths.Diagnostics.PATH_PARAM) String diagnosticId) {

        log.info(LogMessages.Diagnostic.DIAGNOSTIC_GET_REQUEST)
            .field("diagnosticId", diagnosticId)
            .log();

        Optional<Diagnostic> found = diagnosticService.getDiagnosticById(diagnosticId);

        if (found.isEmpty()) {
            log.warn(LogMessages.Diagnostic.DIAGNOSTIC_GET_NOT_FOUND)
                .field("diagnosticId", diagnosticId)
                .log();
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.of(404, "Not Found", "No diagnostic found with id: " + diagnosticId))
                .build();
        }

        Diagnostic diagnostic = found.get();
        Alert alert = alertRepository.findById(diagnostic.getAlertId()).orElse(null);

        log.info(LogMessages.Diagnostic.DIAGNOSTIC_GET_FOUND)
            .field("diagnosticId", diagnosticId)
            .log();

        return Response.ok(DiagnosticDetailResponse.from(diagnostic, alert, clusterName)).build();
    }
}
