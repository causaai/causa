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
import com.causa.mcp.McpClient;
import com.causa.mcp.McpRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health Check Service
 *
 * <p>Aggregates health status from all system components and provides
 * a comprehensive health check response.
 *
 * <p>The overall system status is determined by:
 * <ul>
 *   <li>UP - All components are healthy</li>
 *   <li>DEGRADED - Some non-critical components are down</li>
 *   <li>DOWN - Critical components (database, or a required MCP server) are down</li>
 * </ul>
 *
 * <p>MCP servers are checked dynamically via {@link McpRegistry} — every server declared in mcp.json
 * A server marked {@code optional} in its config being down never affects the overall status; a
 * non-optional (required) server being down — or the MCP config failing to load at all — makes the
 * overall status DOWN.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class HealthCheckService {

    private static final CausaLogger log = CausaLogger.getLogger(HealthCheckService.class);

    private final DatabaseConnectionService databaseConnectionService;
    private final DataSource dataSource;
    private final String applicationVersion;
    private final McpRegistry mcpRegistry;
    private final PromptSender llmPromptSender;
    private final AppConfig appConfig;

    @Inject
    public HealthCheckService(
            DatabaseConnectionService databaseConnectionService,
            DataSource dataSource,
            @ConfigProperty(name = "quarkus.application.version") String applicationVersion,
            McpRegistry mcpRegistry,
            PromptSender llmPromptSender,
            AppConfig appConfig) {
        this.databaseConnectionService = databaseConnectionService;
        this.dataSource = dataSource;
        this.applicationVersion = applicationVersion;
        this.mcpRegistry = mcpRegistry;
        this.llmPromptSender = llmPromptSender;
        this.appConfig = appConfig;
    }

    /**
     * Get comprehensive health status of all system components.
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

        // Check every MCP server declared in mcp.json, dynamically
        boolean anyRequiredMcpDown = collectMcpHealth(responseBuilder);

        // Determine overall system status
        AppConstants.HealthStatus overallStatus =
                determineOverallStatus(databaseHealth, llmHealth, anyRequiredMcpDown);
        responseBuilder.status(overallStatus.getValue());

        HealthCheckResponseDto response = responseBuilder.build();

        log.info(LogMessages.HealthCheck.SYSTEM_CHECK_COMPLETED)
                .field(ApiConstants.LogFields.STATUS, overallStatus.getValue())
                .log();

        return response;
    }

    /**
     * Checks every MCP server currently in the registry and rolls them into one {@code mcp_config}
     * component, keyed by plain server name under {@code servers} — not one {@code mcp_<name>}
     * component per server. If the registry itself failed to initialize (e.g. {@code mcp.json} was
     * missing or invalid at startup), the same {@code mcp_config} component reports the exact
     * failure reason instead.
     *
     * <p>An empty {@code mcpServers} map is rejected by {@code @NotEmpty} validation in
     * {@link com.causa.mcp.config.McpSettings} before {@link McpRegistry#init} is ever called, so
     * that case always surfaces here as "registry not initialized", never as "initialized with
     * zero clients" — there's deliberately no separate branch for the latter.
     *
     * @param responseBuilder the in-progress health response to append components to
     * @return {@code true} if any non-optional MCP server is unhealthy, or the config itself
     *         failed to load — either case makes the overall system status DOWN
     */
    private boolean collectMcpHealth(HealthCheckResponseDto.Builder responseBuilder) {
        if (!mcpRegistry.isInitialized()) {
            log.warn(LogMessages.HealthCheck.MCP_CONFIG_NOT_INITIALIZED)
                    .field("error", mcpRegistry.getInitializationError().orElse("unknown error"))
                    .log();
            responseBuilder.addComponent(HealthCheckConstants.ComponentNames.MCP_CONFIG,
                    ComponentHealthDto.builder()
                            .status(AppConstants.HealthStatus.DOWN.getValue())
                            .message("MCP configuration failed to load: "
                                    + mcpRegistry.getInitializationError().orElse("unknown error"))
                            .build());
            return true;
        }

        boolean anyRequiredMcpDown = false;
        Map<String, ComponentHealthDto> servers = new LinkedHashMap<>();
        for (McpClient client : mcpRegistry.allClients()) {
            ComponentHealthDto health = client.checkHealth();
            servers.put(client.getServerName(), health);
            if (!client.getConfig().optional()
                    && !AppConstants.HealthStatus.UP.getValue().equals(health.getStatus())) {
                anyRequiredMcpDown = true;
            }
        }

        responseBuilder.addComponent(HealthCheckConstants.ComponentNames.MCP_CONFIG,
                ComponentHealthDto.builder()
                        .status(anyRequiredMcpDown
                                ? AppConstants.HealthStatus.DOWN.getValue()
                                : AppConstants.HealthStatus.UP.getValue())
                        .servers(servers)
                        .build());
        return anyRequiredMcpDown;
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
     * Determine overall system status based on component health.
     *
     * <p>The database is critical: if it's down, the entire system is DOWN. So is any non-optional
     * ("required") MCP server, or the MCP config itself failing to load — both make the system
     * DOWN. The LLM provider remains non-critical — if it's down but everything else is fine, the
     * system status is DEGRADED.
     *
     * @param databaseHealth     the database component health
     * @param llmHealth          the LLM provider component health
     * @param anyRequiredMcpDown whether any non-optional MCP server is unhealthy (or config load failed)
     * @return overall system status (UP, DOWN, or DEGRADED)
     */
    private AppConstants.HealthStatus determineOverallStatus(
            ComponentHealthDto databaseHealth,
            ComponentHealthDto llmHealth,
            boolean anyRequiredMcpDown) {

        if (!AppConstants.HealthStatus.UP.getValue().equals(databaseHealth.getStatus())) {
            return AppConstants.HealthStatus.DOWN;
        }

        if (anyRequiredMcpDown) {
            return AppConstants.HealthStatus.DOWN;
        }

        if (!AppConstants.HealthStatus.UP.getValue().equals(llmHealth.getStatus())) {
            return AppConstants.HealthStatus.DEGRADED;
        }

        return AppConstants.HealthStatus.UP;
    }
}
