package com.causa.api.controllers;

import com.causa.api.dto.response.AlertDetailsResponse;
import com.causa.api.dto.response.AlertListResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.api.mappers.AlertResponseMapper;
import com.causa.api.validators.PathParamValidator;
import com.causa.common.constants.ApiConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.Alert;
import com.causa.core.ports.AlertRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Alert Query Controller
 *
 * <p>REST endpoints for querying historical alerts.
 * <p>Provides read-only access to stored alert data.
 *
 * @since 0.0.1
 */
@Path(ApiConstants.Paths.Alerts.BASE)
@Produces(MediaType.APPLICATION_JSON)
public class AlertQueryController {

    private static final CausaLogger log = CausaLogger.getLogger(AlertQueryController.class);

    private final AlertRepository alertRepository;

    @Inject
    public AlertQueryController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * Fetches all historical alerts.
     *
     * <p>GET /api/v1/alerts
     *
     * @return HTTP response with AlertListResponse or ErrorResponse
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
     * Fetches historical alerts for a specific container.
     *
     * <p>GET /api/v1/containers/{containerName}/alerts
     *
     * @param containerName the container name
     * @return HTTP response with AlertListResponse or ErrorResponse
     */
    @GET
    @Path("../containers/{containerName}/alerts")
    public Response getAlertsByContainer(@PathParam("containerName") String containerName) {
        log.info("Fetching alerts for container")
            .field("containerName", containerName)
            .log();

        // Validate path parameter
        List<String> validationErrors = PathParamValidator.validateContainerName(containerName);
        if (!validationErrors.isEmpty()) {
            log.warn("Invalid container name parameter")
                .field("containerName", containerName)
                .field("errors", validationErrors)
                .log();

            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(400, "Validation Failed",
                    String.join("; ", validationErrors)))
                .build();
        }

        List<Alert> alerts = alertRepository.findByContainerName(containerName);
        List<AlertDetailsResponse> responseList = AlertResponseMapper.toResponseList(alerts);

        AlertListResponse response = AlertListResponse.forContainer(responseList, containerName);

        log.info("Successfully fetched alerts for container")
            .field("containerName", containerName)
            .field("totalCount", response.totalCount())
            .log();

        return Response.ok(response).build();
    }

}
