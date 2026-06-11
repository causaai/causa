package com.causa.api.controllers;

import com.causa.api.dto.response.AlertDetailsResponse;
import com.causa.api.dto.response.AlertListResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.api.mappers.AlertResponseMapper;
import com.causa.api.validators.PathParamValidator;
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
 * Container Alert Controller
 *
 * <p>REST endpoints for querying alerts by container name.
 * <p>Provides container-scoped access to alert data.
 *
 * @since 0.0.1
 */
@Path("/api/v1/containers")
@Produces(MediaType.APPLICATION_JSON)
public class ContainerAlertController {

    private static final CausaLogger log = CausaLogger.getLogger(ContainerAlertController.class);

    private final AlertRepository alertRepository;

    @Inject
    public ContainerAlertController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
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
    @Path("/{containerName}/alerts")
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
