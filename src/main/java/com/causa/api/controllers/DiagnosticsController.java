package com.causa.api.controllers;

import com.causa.api.dto.response.DiagnosticDetailResponse;
import com.causa.api.dto.response.DiagnosticListItemResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.api.dto.response.JiraTicketResponse;
import com.causa.common.constants.ApiConstants;
import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.common.exceptions.DiagnosticException;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.ExternalConfigCache;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.PageRequest;
import com.causa.core.domain.PageResult;
import com.causa.core.ports.AlertRepository;
import com.causa.core.services.DiagnosticService;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

/**
 * Diagnostics Controller
 *
 * <p>GET /api/v1/diagnostics        — paginated list of diagnostics (summary)
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
    private final ExternalConfigCache externalConfigCache;
    private final String clusterName;

    @Inject
    public DiagnosticsController(DiagnosticService diagnosticService,
                                  AlertRepository alertRepository,
                                  ExternalConfigCache externalConfigCache,
                                  @ConfigProperty(name = "causa.cluster.name", defaultValue = "default")
                                  String clusterName) {
        this.diagnosticService = diagnosticService;
        this.alertRepository   = alertRepository;
        this.externalConfigCache = externalConfigCache;
        this.clusterName       = clusterName;
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/diagnostics
    // -------------------------------------------------------------------------

    @GET
    public Response listDiagnostics(
            @QueryParam(ApiConstants.Paths.Pagination.QUERY_PAGE)      @DefaultValue("1")                                              int page,
            @QueryParam(ApiConstants.Paths.Pagination.QUERY_PAGE_SIZE) @DefaultValue(ApiConstants.Paths.Pagination.DEFAULT_PAGE_SIZE) int pageSize,
            @QueryParam(ApiConstants.Paths.Diagnostics.QUERY_WORKLOAD)  @Size(max = 255) String workloadName,
            @QueryParam(ApiConstants.Paths.Diagnostics.QUERY_NAMESPACE) @Size(max = 255) String namespace) {

        log.info(LogMessages.Diagnostic.DIAGNOSTICS_LIST_REQUEST)
            .field("page", page)
            .field("page_size", pageSize)
            .log();

        Diagnostic.Filter filter = new Diagnostic.Filter(workloadName, namespace);
        PageResult<Diagnostic> result = diagnosticService.listDiagnostics(filter, PageRequest.of(page, pageSize));

        boolean jiraConfigured = isJiraConfigured();
        List<DiagnosticListItemResponse> items = result.items().stream()
            .map(d -> {
                Alert alert = alertRepository.findById(d.getAlertId()).orElse(null);
                return DiagnosticListItemResponse.from(d, alert, clusterName, jiraConfigured);
            })
            .toList();

        PageResult<DiagnosticListItemResponse> response = PageResult.of(
            items,
            result.page(),
            result.pageSize(),
            result.total()
        );

        log.info(LogMessages.Diagnostic.DIAGNOSTICS_LIST_RETURNED)
            .field("count", items.size())
            .field("total", result.total())
            .field("page", result.page())
            .log();

        return Response.ok(response).build();
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

        return Response.ok(DiagnosticDetailResponse.from(diagnostic, alert, clusterName, isJiraConfigured())).build();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/diagnostics/{id}/ticket
    // -------------------------------------------------------------------------

    @POST
    @Path(ApiConstants.Paths.Diagnostics.TICKET)
    public Response createJiraTicket(
            @PathParam(ApiConstants.Paths.Diagnostics.PATH_PARAM) String diagnosticId) {

        log.info(LogMessages.Diagnostic.JIRA_TICKET_REQUEST)
            .field("diagnosticId", diagnosticId)
            .log();

        try {
            DiagnosticService.JiraTicket ticket = diagnosticService.createOrGetJiraTicket(diagnosticId);

            return ticket.alreadyExisted()
                ? Response.ok(JiraTicketResponse.existing(ticket.url())).build()
                : Response.status(Response.Status.CREATED).entity(JiraTicketResponse.created(ticket.url())).build();

        } catch (DiagnosticException e) {
            return switch (e.getErrorType()) {
                case "JIRA_NOT_CONFIGURED" -> {
                    log.warn(LogMessages.Diagnostic.JIRA_NOT_CONFIGURED)
                        .field("diagnosticId", diagnosticId)
                        .log();
                    yield Response.status(422)
                        .entity(ErrorResponse.of(422, "Unprocessable Entity", e.getMessage()))
                        .build();
                }
                case "NOT_FOUND" -> Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.of(404, "Not Found", e.getMessage()))
                    .build();
                default -> {
                    log.warn(LogMessages.Diagnostic.JIRA_TICKET_FAILED)
                        .field("diagnosticId", diagnosticId)
                        .field("errorType", e.getErrorType())
                        .field("message", e.getMessage())
                        .log();
                    yield Response.status(502)
                        .entity(ErrorResponse.of(502, "Bad Gateway", e.getMessage()))
                        .build();
                }
            };
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** True when an active JIRA integration exists; read from the in-memory cache, no DB call. */
    private boolean isJiraConfigured() {
        return externalConfigCache
            .getByCategory(PlatformCategory.INTEGRATION)
            .stream()
            .anyMatch(c -> "JIRA".equals(c.getPlatform()) && c.isActive());
    }
}
