package com.causa.api.controllers;

import com.causa.api.dto.request.AlertTriggerRequest;
import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.api.dto.response.AlertResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.common.constants.AlertConstants;
import com.causa.common.constants.ApiConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.common.utils.AlertNameUtils;
import com.causa.core.services.AlertService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Alerts Controller
 *
 * <p>GET  /api/v1/alerts/{id}  — single alert by ID (path param); no other params accepted
 * <p>GET  /api/v1/alerts       — list alerts; optionally filter by {@code workload_name} and/or {@code namespace}
 * <p>POST /api/v1/alerts       — manually create a synthetic alert to trigger diagnosis
 *
 * @since 0.0.1
 */
@Path(ApiConstants.Paths.Alerts.BASE)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AlertsController {

    private static final CausaLogger log = CausaLogger.getLogger(AlertsController.class);

    private final AlertService alertService;
    private final WebhookController webhookController;

    @Inject
    public AlertsController(AlertService alertService,
                            WebhookController webhookController) {
        this.alertService = alertService;
        this.webhookController = webhookController;
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

    @POST
    public Response createManualAlert(AlertTriggerRequest request) {
        log.info(LogMessages.Alert.ALERTS_TRIGGER_REQUEST).log();

        AlertWebhookRequest webhookRequest = toWebhookRequest(request);

        log.info(LogMessages.Alert.ALERTS_TRIGGER_ACCEPTED)
            .field("namespace", request != null ? request.getNamespace() : null)
            .field("container", request != null ? request.getContainer() : null)
            .field("podName", request != null ? request.getPodName() : null)
            .field("workloadName", request != null ? request.getWorkloadName() : null)
            .log();

        return webhookController.receiveAlerts(webhookRequest);
    }

    private AlertWebhookRequest toWebhookRequest(AlertTriggerRequest request) {
        String alertName = AlertNameUtils.generateManualAlertName();
        String severity = request.getSeverity() != null && !request.getSeverity().isBlank()
            ? request.getSeverity()
            : AlertConstants.ManualTrigger.DEFAULT_SEVERITY;

        Map<String, String> labels = new HashMap<>();
        labels.put(AlertConstants.Labels.ALERT_NAME, alertName);
        labels.put(AlertConstants.Labels.SEVERITY, severity);
        labels.put(AlertConstants.Labels.NAMESPACE, request.getNamespace() != null ? request.getNamespace() : "");
        labels.put(AlertConstants.Labels.CONTAINER, request.getContainer() != null ? request.getContainer() : "");
        labels.put(AlertConstants.Labels.POD, request.getPodName() != null ? request.getPodName() : "");
        labels.put(AlertConstants.Labels.CLUSTER_NAME, request.getClusterName() != null ? request.getClusterName() : "");
        labels.put(AlertConstants.Labels.WORKLOAD_TYPE, request.getWorkloadType() != null ? request.getWorkloadType() : "");

        Map<String, String> annotations = new HashMap<>();
        annotations.put(AlertConstants.Labels.ALERT_SOURCE, AlertConstants.ManualTrigger.ALERT_SOURCE);
        if (request.getWorkloadName() != null) annotations.put(AlertConstants.Labels.WORKLOAD_NAME, request.getWorkloadName());

        AlertWebhookRequest.AlertItem item = new AlertWebhookRequest.AlertItem();
        item.setStatus(AlertConstants.Webhook.STATUS_FIRING);
        item.setLabels(labels);
        item.setAnnotations(annotations);
        item.setStartsAt(Instant.now().toString());

        AlertWebhookRequest webhookRequest = new AlertWebhookRequest();
        webhookRequest.setVersion(AlertConstants.Webhook.ALERTMANAGER_VERSION);
        webhookRequest.setStatus(AlertConstants.Webhook.STATUS_FIRING);
        webhookRequest.setReceiver(AlertConstants.ManualTrigger.WEBHOOK_RECEIVER);
        webhookRequest.setAlerts(List.of(item));

        return webhookRequest;
    }
}
