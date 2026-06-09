package com.causa.api.controllers;

import com.causa.api.dto.HealthCheckResponseDto;
import com.causa.common.constants.ApiConstants;
import com.causa.common.constants.AppConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.services.HealthCheckService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Health Check Controller
 *
 * <p>REST API controller for system health monitoring. Provides a comprehensive
 * health check endpoint that reports the status of all system components.
 *
 * <p>This endpoint is designed for:
 * <ul>
 *   <li>Monitoring systems to track application health</li>
 *   <li>Load balancers to determine if the instance should receive traffic</li>
 *   <li>Operations teams to diagnose system issues</li>
 *   <li>Automated health checks in CI/CD pipelines</li>
 * </ul>
 *
 * <h2>Endpoint</h2>
 * <pre>
 * GET /api/health
 * </pre>
 *
 * <h2>Response Format</h2>
 * <pre>
 * {
 *   "status": "UP",
 *   "timestamp": "2026-06-09T06:45:00Z",
 *   "version": "0.0.1",
 *   "components": {
 *     "database": {
 *       "status": "UP",
 *       "message": "Connected to PostgreSQL",
 *       "latency_ms": 12
 *     }
 *   }
 * }
 * </pre>
 *
 * <h2>Status Codes</h2>
 * <ul>
 *   <li>200 OK - System is healthy (status: UP)</li>
 *   <li>503 Service Unavailable - System is unhealthy (status: DOWN)</li>
 * </ul>
 *
 * @since 0.0.1
 */
@Path(ApiConstants.Endpoints.HEALTH)
@Produces(MediaType.APPLICATION_JSON)
public class HealthCheckController {

    private static final CausaLogger log = CausaLogger.getLogger(HealthCheckController.class);

    private final HealthCheckService healthCheckService;
    private final String applicationVersion;

    @Inject
    public HealthCheckController(
            HealthCheckService healthCheckService,
            @ConfigProperty(name = "quarkus.application.version") String applicationVersion) {
        this.healthCheckService = healthCheckService;
        this.applicationVersion = applicationVersion;
    }

    /**
     * Get comprehensive system health status.
     *
     * <p>Returns the health status of all monitored components including:
     * <ul>
     *   <li>Database connectivity and latency</li>
     *   <li>LLM provider status (future)</li>
     *   <li>MCP server connectivity (future)</li>
     * </ul>
     *
     * <p>The HTTP status code reflects the overall system health:
     * <ul>
     *   <li>200 OK - All critical components are healthy</li>
     *   <li>503 Service Unavailable - One or more critical components are down</li>
     *   <li>500 Internal Server Error - Unexpected error during health check execution</li>
     * </ul>
     *
     * @return Response containing the health check data and appropriate HTTP status
     */
    @GET
    public Response getHealth() {
        log.debug(LogMessages.HealthCheck.ENDPOINT_CALLED).log();

        try {
            HealthCheckResponseDto healthResponse = healthCheckService.getSystemHealth();

            // Determine HTTP status code based on overall system status
            // 200 OK if system is UP, 503 Service Unavailable if DOWN or DEGRADED
            Response.Status httpStatus = AppConstants.HealthStatus.UP.getValue().equals(healthResponse.getStatus())
                    ? Response.Status.OK
                    : Response.Status.SERVICE_UNAVAILABLE;

            log.debug(LogMessages.HealthCheck.ENDPOINT_RESPONSE_PREPARED)
                    .field(ApiConstants.LogFields.STATUS, healthResponse.getStatus())
                    .field(ApiConstants.LogFields.HTTP_STATUS, httpStatus.getStatusCode())
                    .log();

            return Response.status(httpStatus)
                    .entity(healthResponse)
                    .build();

        } catch (Exception e) {
            log.error(LogMessages.HealthCheck.ENDPOINT_FAILED)
                    .exception(e)
                    .log();

            // Return 500 Internal Server Error for unexpected failures during health check execution
            // This indicates a problem with the health check mechanism itself, not the monitored components
            HealthCheckResponseDto errorResponse = HealthCheckResponseDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .timestampNow()
                    .version(applicationVersion)
                    .build();

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponse)
                    .build();
        }
    }
}
