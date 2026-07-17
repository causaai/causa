package com.causa.api.controllers;

import com.causa.api.dto.response.AlertResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.common.constants.ApiConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.services.AlertService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Alerts Controller
 *
 * <p>GET /api/v1/alerts/{id}  — single alert by ID (path param); no other params accepted
 * <p>GET /api/v1/alerts       — list alerts; optionally filter by {@code workload_name} and/or {@code namespace}
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

    /**
     * GET /api/v1/alerts/{id}
     *
     * <p>Returns a single alert by its ID. If {@code workload_name} or {@code namespace}
     * query params are also present, the request is rejected with 400.
     */
    @GET
    @Path(ApiConstants.Paths.Alerts.BY_ID)
    public Response getAlertById(
            @PathParam(ApiConstants.Paths.Alerts.PATH_PARAM_ID)        String id,
            @QueryParam(ApiConstants.Paths.Alerts.QUERY_WORKLOAD)      String workloadName,
            @QueryParam(ApiConstants.Paths.Alerts.QUERY_NAMESPACE)     String namespace) {

        log.info(LogMessages.Alert.ALERTS_GET_REQUEST)
            .field("id", id)
            .log();

        if ((workloadName != null && !workloadName.isBlank())
                || (namespace != null && !namespace.isBlank())) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(400, "Bad Request",
                    "Use GET /api/v1/alerts/{id} with only the path param id. " +
                    "To filter by workload_name or namespace use GET /api/v1/alerts?workload_name=&namespace="))
                .build();
        }

        return alertService.getAlert(id)
            .map(alert -> {
                log.info(LogMessages.Alert.ALERTS_GET_FOUND).field("id", id).log();
                return Response.ok(AlertResponse.from(alert)).build();
            })
            .orElseGet(() -> {
                log.warn(LogMessages.Alert.ALERTS_GET_NOT_FOUND).field("id", id).log();
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.of(404, "Not Found", "No alert found with id: " + id))
                    .build();
            });
    }

    /**
     * GET /api/v1/alerts
     *
     * <p>Returns all alerts. Optionally filter by {@code workload_name} and/or {@code namespace}
     * using AND logic. Pass neither to return all alerts.
     */
    @GET
    public Response getAlerts(
            @QueryParam(ApiConstants.Paths.Alerts.QUERY_WORKLOAD)  String workloadName,
            @QueryParam(ApiConstants.Paths.Alerts.QUERY_NAMESPACE) String namespace) {

        log.info(LogMessages.Alert.ALERTS_GET_REQUEST)
            .field("workload_name", workloadName)
            .field("namespace", namespace)
            .log();

        List<AlertResponse> results = alertService.getAlerts(workloadName, namespace)
            .stream()
            .map(AlertResponse::from)
            .toList();

        log.info(LogMessages.Alert.ALERTS_GET_FOUND)
            .field("count", results.size())
            .field("workload_name", workloadName)
            .field("namespace", namespace)
            .log();

        return Response.ok(results).build();
    }
}
