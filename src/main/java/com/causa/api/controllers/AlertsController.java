package com.causa.api.controllers;

import com.causa.api.dto.response.AlertDetailResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.common.constants.ApiConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.services.AlertService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Alerts Controller
 *
 * <p>GET /api/v1/alerts
 * <p>Supported query parameters:
 * <ul>
 *   <li>{@code id}            — return a single alert by its ID (404 if not found)</li>
 *   <li>{@code workload_name} — filter all alerts by workload (container) name</li>
 *   <li>{@code namespace}     — filter all alerts by Kubernetes namespace</li>
 * </ul>
 * When no query param is provided all alerts are returned.
 *
 * @since 0.0.1
 */
@Path(ApiConstants.Paths.Alerts.BASE)
@Produces(MediaType.APPLICATION_JSON)
public class AlertsController {

    private static final CausaLogger log = CausaLogger.getLogger(AlertsController.class);

    private final AlertService alertService;

    @Inject
    public AlertsController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GET
    public Response getAlerts(
            @QueryParam(ApiConstants.Paths.Alerts.QUERY_ID)       String id,
            @QueryParam(ApiConstants.Paths.Alerts.QUERY_WORKLOAD)  String workloadName,
            @QueryParam(ApiConstants.Paths.Alerts.QUERY_NAMESPACE) String namespace) {

        log.info(LogMessages.Alert.ALERTS_GET_REQUEST)
            .field("id", id)
            .field("workload_name", workloadName)
            .field("namespace", namespace)
            .log();

        // Single alert by ID — apply remaining filters as AND conditions
        if (id != null && !id.isBlank()) {
            return alertService.getAlert(id)
                .filter(alert -> matchesWorkloadName(alert, workloadName))
                .filter(alert -> matchesNamespace(alert, namespace))
                .map(alert -> {
                    log.info(LogMessages.Alert.ALERTS_GET_FOUND).field("id", id).log();
                    return Response.ok(AlertDetailResponse.from(alert)).build();
                })
                .orElseGet(() -> {
                    log.warn(LogMessages.Alert.ALERTS_GET_NOT_FOUND).field("id", id).log();
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(ErrorResponse.of(404, "Not Found", "No alert found matching the given filters"))
                        .build();
                });
        }

        // List — all non-blank params applied with AND in the repository
        List<AlertDetailResponse> results = alertService.getAlerts(workloadName, namespace)
            .stream()
            .map(AlertDetailResponse::from)
            .toList();

        log.info(LogMessages.Alert.ALERTS_GET_FOUND)
            .field("count", results.size())
            .field("workload_name", workloadName)
            .field("namespace", namespace)
            .log();

        return Response.ok(results).build();
    }

    // -------------------------------------------------------------------------
    // AND filter helpers — used when id is combined with other params
    // -------------------------------------------------------------------------

    private boolean matchesWorkloadName(com.causa.core.domain.Alert alert, String workloadName) {
        if (workloadName == null || workloadName.isBlank()) return true;
        return workloadName.equals(alert.getWorkloadName());
    }

    private boolean matchesNamespace(com.causa.core.domain.Alert alert, String namespace) {
        if (namespace == null || namespace.isBlank()) return true;
        return namespace.equals(alert.getWorkloadInfo().namespace());
    }
}
