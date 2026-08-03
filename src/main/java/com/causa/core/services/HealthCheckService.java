package com.causa.core.services;

import com.causa.api.dto.ComponentHealthDto;
import com.causa.api.dto.HealthCheckResponseDto;
import com.causa.common.constants.ApiConstants;
import com.causa.common.constants.AppConstants;
import com.causa.common.constants.DatabaseConstants;
import com.causa.common.constants.HealthCheckConstants;
import com.causa.common.constants.LLMConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.AppConfig;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.ports.llm.PromptSender;
import com.causa.infrastructure.persistence.DatabaseConnectionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;

/**
 * Health Check Service
 *
 * <p>Aggregates health status from all system components and provides
 * a comprehensive health check response. This service is designed to be
 * extensible for future component additions (LLM providers, MCP servers, etc.).
 *
 * <p>The overall system status is determined by:
 * <ul>
 *   <li>UP - All components are healthy</li>
 *   <li>DEGRADED - Some non-critical components are down (future use)</li>
 *   <li>DOWN - Critical components (like database) are down</li>
 * </ul>
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class HealthCheckService {

    private static final CausaLogger log = CausaLogger.getLogger(HealthCheckService.class);

    private static final String PLATFORM_VM = "vm";

    private final DatabaseConnectionService databaseConnectionService;
    private final DataSource dataSource;
    private final String applicationVersion;
    private final String platform;
    private final String mcpK8sEndpoint;
    private final String mcpK8sHealthPath;
    private final int mcpK8sTimeout;
    private final String mcpKruizeEndpoint;
    private final String mcpKruizeHealthPath;
    private final int mcpKruizeTimeout;
    private final String mcpCryostatHealthEndpoint;
    private final String mcpCryostatHealthPath;
    private final int mcpCryostatTimeout;
    private final String mcpFilesystemEndpoint;
    private final String mcpFilesystemHealthPath;
    private final int mcpFilesystemTimeout;
    private final PromptSender llmPromptSender;
    private final AppConfig appConfig;

    @Inject
    public HealthCheckService(
            DatabaseConnectionService databaseConnectionService,
            DataSource dataSource,
            @ConfigProperty(name = "quarkus.application.version") String applicationVersion,
            @ConfigProperty(name = "causa.cluster.target-cluster-type", defaultValue = "cluster") String platform,
            @ConfigProperty(name = "causa.mcp.kubernetes.endpoint") String mcpK8sEndpoint,
            @ConfigProperty(name = "causa.mcp.kubernetes.health-path") String mcpK8sHealthPath,
            @ConfigProperty(name = "causa.mcp.kubernetes.timeout-ms") int mcpK8sTimeout,
            @ConfigProperty(name = "causa.mcp.kruize.endpoint") String mcpKruizeEndpoint,
            @ConfigProperty(name = "causa.mcp.kruize.health-path") String mcpKruizeHealthPath,
            @ConfigProperty(name = "causa.mcp.kruize.timeout-ms") int mcpKruizeTimeout,
            @ConfigProperty(name = "causa.mcp.cryostat.health-endpoint") String mcpCryostatHealthEndpoint,
            @ConfigProperty(name = "causa.mcp.cryostat.health-path") String mcpCryostatHealthPath,
            @ConfigProperty(name = "causa.mcp.cryostat.timeout-ms") int mcpCryostatTimeout,
            @ConfigProperty(name = "causa.mcp.filesystem.endpoint") String mcpFilesystemEndpoint,
            @ConfigProperty(name = "causa.mcp.filesystem.health-path") String mcpFilesystemHealthPath,
            @ConfigProperty(name = "causa.mcp.filesystem.timeout-ms") int mcpFilesystemTimeout,
            PromptSender llmPromptSender,
            AppConfig appConfig) {
        this.databaseConnectionService = databaseConnectionService;
        this.dataSource = dataSource;
        this.applicationVersion = applicationVersion;
        this.platform = platform != null ? platform.trim().toLowerCase() : "cluster";
        this.mcpK8sEndpoint = mcpK8sEndpoint;
        this.mcpK8sHealthPath = mcpK8sHealthPath;
        this.mcpK8sTimeout = mcpK8sTimeout;
        this.mcpKruizeEndpoint = mcpKruizeEndpoint;
        this.mcpKruizeHealthPath = mcpKruizeHealthPath;
        this.mcpKruizeTimeout = mcpKruizeTimeout;
        this.mcpCryostatHealthEndpoint = mcpCryostatHealthEndpoint;
        this.mcpCryostatHealthPath = mcpCryostatHealthPath;
        this.mcpCryostatTimeout = mcpCryostatTimeout;
        this.mcpFilesystemEndpoint = mcpFilesystemEndpoint;
        this.mcpFilesystemHealthPath = mcpFilesystemHealthPath;
        this.mcpFilesystemTimeout = mcpFilesystemTimeout;
        this.llmPromptSender = llmPromptSender;
        this.appConfig = appConfig;
    }

    /**
     * Get comprehensive health status of all system components.
     *
     * <p>Checks the health of all monitored components and aggregates
     * them into a single response. Currently checks:
     * <ul>
     *   <li>Database connectivity and latency</li>
     * </ul>
     *
     * <p>Future components to be added:
     * <ul>
     *   <li>LLM provider (gpt-4-turbo via LangChain4J)</li>
     *   <li>MCP Kubernetes server</li>
     *   <li>MCP Cryostat server</li>
     *   <li>MCP Kruize server</li>
     * </ul>
     *
     * @return comprehensive health check response with all component statuses
     */
    public HealthCheckResponseDto getSystemHealth() {
        log.debug(LogMessages.HealthCheck.SYSTEM_CHECK_STARTED).log();

        HealthCheckResponseDto.Builder responseBuilder = HealthCheckResponseDto.builder()
                .version(applicationVersion)
                .timestampNow();

        // Check database health (always)
        ComponentHealthDto databaseHealth = checkDatabaseHealth();
        responseBuilder.addComponent(HealthCheckConstants.ComponentNames.DATABASE, databaseHealth);

        // Check LLM provider health (always)
        ComponentHealthDto llmHealth = checkLlmProviderHealth();
        responseBuilder.addComponent(HealthCheckConstants.ComponentNames.LLM_PROVIDER, llmHealth);

        ComponentHealthDto mcpK8sHealth = null;
        ComponentHealthDto mcpKruizeHealth = null;
        ComponentHealthDto mcpCryostatHealth = null;
        ComponentHealthDto mcpFilesystemHealth = null;

        if (PLATFORM_VM.equals(platform)) {
            // VM mode: only check filesystem MCP
            mcpFilesystemHealth = checkMcpFilesystemHealth();
            responseBuilder.addComponent(HealthCheckConstants.ComponentNames.MCP_FILESYSTEM, mcpFilesystemHealth);
        } else {
            // Cluster mode: check Kubernetes, Kruize, and Cryostat MCP servers
            mcpK8sHealth = checkMcpKubernetesHealth();
            responseBuilder.addComponent(HealthCheckConstants.ComponentNames.MCP_KUBERNETES, mcpK8sHealth);

            mcpKruizeHealth = checkMcpKruizeHealth();
            responseBuilder.addComponent(HealthCheckConstants.ComponentNames.MCP_KRUIZE, mcpKruizeHealth);

            mcpCryostatHealth = checkMcpCryostatHealth();
            responseBuilder.addComponent(HealthCheckConstants.ComponentNames.MCP_CRYOSTAT, mcpCryostatHealth);
        }

        // Determine overall system status
        AppConstants.HealthStatus overallStatus = determineOverallStatus(
            databaseHealth, mcpK8sHealth, llmHealth, mcpKruizeHealth, mcpCryostatHealth, mcpFilesystemHealth);
        responseBuilder.status(overallStatus.getValue());

        HealthCheckResponseDto response = responseBuilder.build();

        log.info(LogMessages.HealthCheck.SYSTEM_CHECK_COMPLETED)
                .field(ApiConstants.LogFields.STATUS, overallStatus.getValue())
                .log();

        return response;
    }

    /**
     * Check database health and measure latency.
     *
     * <p>Verifies database connectivity using the DatabaseConnectionService
     * and measures the latency of a simple validation query using the connection pool.
     *
     * <p><strong>Connection Pool Usage:</strong> This method uses the Agroal connection pool
     * managed by Quarkus. It does not create new connections; instead, it borrows a connection
     * from the pool, measures latency, and returns it to the pool via try-with-resources.
     *
     * @return component health DTO with database status and latency
     */
    private ComponentHealthDto checkDatabaseHealth() {
        boolean isReady = databaseConnectionService.isReady();

        if (!isReady) {
            log.warn(LogMessages.HealthCheck.DB_CHECK_FAILED).log();
            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .message(DatabaseConstants.Health.DB_NOT_AVAILABLE_MESSAGE)
                    .latencyMs(0L)
                    .build();
        }

        // Measure database latency using connection pool
        long startTime = System.currentTimeMillis();
        boolean connectionSuccessful = false;

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(DatabaseConstants.VALIDATION_QUERY);
            connectionSuccessful = true;
        } catch (Exception e) {
            log.error(LogMessages.HealthCheck.DB_LATENCY_MEASUREMENT_FAILED)
                    .exception(e)
                    .log();
        }

        long latency = System.currentTimeMillis() - startTime;

        if (connectionSuccessful) {
            log.debug(LogMessages.HealthCheck.DB_CHECK_PASSED)
                    .field(ApiConstants.LogFields.LATENCY_MS, latency)
                    .log();

            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message(DatabaseConstants.Health.DB_CONNECTED_MESSAGE)
                    .latencyMs(latency)
                    .build();
        } else {
            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .message(DatabaseConstants.Health.DB_CONNECTION_FAILED_MESSAGE)
                    .latencyMs(latency)
                    .build();
        }
    }

    /**
     * Check MCP Kubernetes server health and measure latency.
     *
     * <p>Sends an HTTP GET request to the MCP Kubernetes server health endpoint
     * and measures the response time. Uses Java's built-in HttpClient with
     * configured timeout.
     *
     * @return component health DTO with MCP Kubernetes status and latency
     */
    private ComponentHealthDto checkMcpKubernetesHealth() {
        log.debug(LogMessages.HealthCheck.MCP_K8S_CHECK_STARTED).log();

        String healthUrl = mcpK8sEndpoint + mcpK8sHealthPath;
        long startTime = System.currentTimeMillis();
        boolean isHealthy = false;
        int statusCode = 0;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(mcpK8sTimeout))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofMillis(mcpK8sTimeout))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            isHealthy = (statusCode >= 200 && statusCode < 300);

        } catch (IOException | InterruptedException e) {
            log.error(LogMessages.HealthCheck.MCP_K8S_CHECK_FAILED)
                    .field("endpoint", healthUrl)
                    .exception(e)
                    .log();
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        long latency = System.currentTimeMillis() - startTime;

        if (isHealthy) {
            log.debug(LogMessages.HealthCheck.MCP_K8S_CHECK_PASSED)
                    .field(ApiConstants.LogFields.LATENCY_MS, latency)
                    .field("status_code", statusCode)
                    .log();

            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message(HealthCheckConstants.Messages.MCP_CONNECTED)
                    .latencyMs(latency)
                    .build();
        } else {
            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .message(HealthCheckConstants.Messages.MCP_NOT_AVAILABLE)
                    .latencyMs(latency)
                    .build();
        }
    }

    /**
     * Checks the health of the LLM provider.
     * Verifies LLM readiness and measures response latency.
     *
     * @return Component health DTO with LLM status and latency
     */
    private ComponentHealthDto checkLlmProviderHealth() {
        long startTime = System.currentTimeMillis();

        try {
            log.info(LogMessages.HealthCheck.LLM_CHECK_STARTED);

            // Check if LLM is ready
            boolean isReady = llmPromptSender.isReady();

            if (!isReady) {
                log.warn(LogMessages.HealthCheck.LLM_CHECK_FAILED);
                return ComponentHealthDto.builder()
                        .status(AppConstants.HealthStatus.DOWN.getValue())
                        .message(LLMConstants.Messages.LLM_NOT_READY)
                        .latencyMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // Send a test prompt to verify connectivity
            LLMRequest testRequest = LLMRequest.builder(LLMConstants.TestData.CONNECTIVITY_TEST_PROMPT)
                    .maxTokens(LLMConstants.TestData.CONNECTIVITY_TEST_MAX_TOKENS)
                    .build();
            
            LLMResponse testResponse = llmPromptSender.send(testRequest);

            if (testResponse == null || testResponse.responseText() == null || testResponse.responseText().trim().isEmpty()) {
                log.warn(LogMessages.HealthCheck.LLM_CHECK_FAILED);
                return ComponentHealthDto.builder()
                        .status(AppConstants.HealthStatus.DOWN.getValue())
                        .message(LLMConstants.Messages.LLM_CONNECTIVITY_FAILED)
                        .latencyMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            long latency = System.currentTimeMillis() - startTime;
            String provider = appConfig.getLlmConfig().getProvider();
            String modelName = appConfig.getLlmConfig().getModelName();
            String displayName = (modelName != null && !modelName.isBlank()) ? modelName : "unknown";
            String message = String.format(LLMConstants.Messages.LLM_CONNECTED_FORMAT,
                    provider + " / " + displayName);

            log.info(LogMessages.HealthCheck.LLM_CHECK_PASSED)
                .field(ApiConstants.LogFields.LATENCY_MS, latency)
                .log();

            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message(message)
                    .latencyMs(latency)
                    .build();

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error(LogMessages.HealthCheck.LLM_CHECK_FAILED)
                .exception(e)
                .log();

            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .message(String.format(LLMConstants.Messages.LLM_ERROR_FORMAT, e.getMessage()))
                    .latencyMs(latency)
                    .build();
        }
    }

    /**
     * Check MCP Kruize server health and measure latency.
     *
     * <p>Sends an HTTP GET request to the MCP Kruize server health endpoint
     * and measures the response time.
     *
     * @return component health DTO with MCP Kruize status and latency
     */
    private ComponentHealthDto checkMcpKruizeHealth() {
        log.debug(LogMessages.HealthCheck.MCP_KRUIZE_CHECK_STARTED).log();

        String healthUrl = mcpKruizeEndpoint + mcpKruizeHealthPath;
        long startTime = System.currentTimeMillis();
        boolean isHealthy = false;
        int statusCode = 0;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(mcpKruizeTimeout))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofMillis(mcpKruizeTimeout))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            isHealthy = (statusCode >= 200 && statusCode < 300);

        } catch (IOException | InterruptedException e) {
            log.error(LogMessages.HealthCheck.MCP_KRUIZE_CHECK_FAILED)
                    .field("endpoint", healthUrl)
                    .exception(e)
                    .log();
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        long latency = System.currentTimeMillis() - startTime;

        if (isHealthy) {
            log.debug(LogMessages.HealthCheck.MCP_KRUIZE_CHECK_PASSED)
                    .field(ApiConstants.LogFields.LATENCY_MS, latency)
                    .field("status_code", statusCode)
                    .log();

            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message(HealthCheckConstants.Messages.MCP_CONNECTED)
                    .latencyMs(latency)
                    .build();
        } else {
            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .message(HealthCheckConstants.Messages.MCP_NOT_AVAILABLE)
                    .latencyMs(latency)
                    .build();
        }
    }

    /**
     * Check MCP Cryostat server health and measure latency.
     *
     * <p>Sends an HTTP GET request to the MCP Cryostat server health endpoint
     * and measures the response time. Note that Cryostat health is on a separate
     * endpoint from the MCP endpoint (different port).
     *
     * @return component health DTO with MCP Cryostat status and latency
     */
    private ComponentHealthDto checkMcpCryostatHealth() {
        log.debug(LogMessages.HealthCheck.MCP_CRYOSTAT_CHECK_STARTED).log();

        String healthUrl = mcpCryostatHealthEndpoint + mcpCryostatHealthPath;
        long startTime = System.currentTimeMillis();
        boolean isHealthy = false;
        int statusCode = 0;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(mcpCryostatTimeout))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofMillis(mcpCryostatTimeout))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            isHealthy = (statusCode >= 200 && statusCode < 300);

        } catch (IOException | InterruptedException e) {
            log.error(LogMessages.HealthCheck.MCP_CRYOSTAT_CHECK_FAILED)
                    .field("endpoint", healthUrl)
                    .exception(e)
                    .log();
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        long latency = System.currentTimeMillis() - startTime;

        if (isHealthy) {
            log.debug(LogMessages.HealthCheck.MCP_CRYOSTAT_CHECK_PASSED)
                    .field(ApiConstants.LogFields.LATENCY_MS, latency)
                    .field("status_code", statusCode)
                    .log();

            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message(HealthCheckConstants.Messages.MCP_CONNECTED)
                    .latencyMs(latency)
                    .build();
        } else {
            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .message(HealthCheckConstants.Messages.MCP_NOT_AVAILABLE)
                    .latencyMs(latency)
                    .build();
        }
    }

    /**
     * Determine overall system status based on component health.
     *
     * <p>The database is considered a critical component - if it's down, the entire system is DOWN.
     * All other components (MCP servers, LLM) are non-critical - if they're down but database is UP,
     * the system status is DEGRADED.
     *
     * @param databaseHealth the database component health
     * @param mcpK8sHealth the MCP Kubernetes component health
     * @param llmHealth the LLM provider component health
     * @param mcpKruizeHealth the MCP Kruize component health
     * @param mcpCryostatHealth the MCP Cryostat component health
     * @return overall system status (UP, DOWN, or DEGRADED)
     */
    private AppConstants.HealthStatus determineOverallStatus(
            ComponentHealthDto databaseHealth,
            ComponentHealthDto mcpK8sHealth,
            ComponentHealthDto llmHealth,
            ComponentHealthDto mcpKruizeHealth,
            ComponentHealthDto mcpCryostatHealth,
            ComponentHealthDto mcpFilesystemHealth) {

        // Database is a critical component
        if (!AppConstants.HealthStatus.UP.getValue().equals(databaseHealth.getStatus())) {
            return AppConstants.HealthStatus.DOWN;
        }

        // If database is UP but any non-critical component is DOWN -> DEGRADED
        if ((mcpK8sHealth != null &&
             !AppConstants.HealthStatus.UP.getValue().equals(mcpK8sHealth.getStatus())) ||
            (llmHealth != null &&
             !AppConstants.HealthStatus.UP.getValue().equals(llmHealth.getStatus())) ||
            (mcpKruizeHealth != null &&
             !AppConstants.HealthStatus.UP.getValue().equals(mcpKruizeHealth.getStatus())) ||
            (mcpCryostatHealth != null &&
             !AppConstants.HealthStatus.UP.getValue().equals(mcpCryostatHealth.getStatus())) ||
            (mcpFilesystemHealth != null &&
             !AppConstants.HealthStatus.UP.getValue().equals(mcpFilesystemHealth.getStatus()))) {
            return AppConstants.HealthStatus.DEGRADED;
        }

        // All components are UP
        return AppConstants.HealthStatus.UP;
    }

    /**
     * Check MCP Filesystem server health and measure latency.
     *
     * <p>Hits {@code causa.mcp.filesystem.endpoint + health-path}. The filesystem MCP
     * server is non-critical — if it is down but the database is up, the overall status
     * is DEGRADED rather than DOWN.
     *
     * @return component health DTO with MCP Filesystem status and latency
     */
    private ComponentHealthDto checkMcpFilesystemHealth() {
        log.debug(LogMessages.HealthCheck.MCP_FILESYSTEM_CHECK_STARTED).log();

        String healthUrl = mcpFilesystemEndpoint + mcpFilesystemHealthPath;
        long startTime = System.currentTimeMillis();
        boolean isHealthy = false;
        int statusCode = 0;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(mcpFilesystemTimeout))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofMillis(mcpFilesystemTimeout))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            isHealthy = (statusCode >= 200 && statusCode < 300);

        } catch (IOException | InterruptedException e) {
            log.error(LogMessages.HealthCheck.MCP_FILESYSTEM_CHECK_FAILED)
                    .field("endpoint", healthUrl)
                    .exception(e)
                    .log();
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        long latency = System.currentTimeMillis() - startTime;

        if (isHealthy) {
            log.debug(LogMessages.HealthCheck.MCP_FILESYSTEM_CHECK_PASSED)
                    .field(ApiConstants.LogFields.LATENCY_MS, latency)
                    .field("status_code", statusCode)
                    .log();

            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message(HealthCheckConstants.Messages.MCP_CONNECTED)
                    .latencyMs(latency)
                    .build();
        } else {
            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .message(HealthCheckConstants.Messages.MCP_NOT_AVAILABLE)
                    .latencyMs(latency)
                    .build();
        }
    }
}

