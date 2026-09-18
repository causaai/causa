package com.causa.mcp;

import com.causa.api.dto.ComponentHealthDto;
import com.causa.common.constants.AppConstants;
import com.causa.common.constants.HealthCheckConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.mcp.config.McpSettings;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * MCP Client
 *
 * <p>Plain (non-CDI) per-server client, one instance per configured server, constructed by
 * {@link McpRegistry#init(McpSettings)}. This first pass only implements the health check —
 * tool-calling (JSON-RPC/SSE) is a later phase.
 *
 * @since 0.0.1
 */
public class McpClient {

    private static final CausaLogger log = CausaLogger.getLogger(McpClient.class);

    private final String serverName;
    private final McpSettings.ServerConfig config;
    private final HttpClient httpClient;

    public McpClient(String serverName, McpSettings.ServerConfig config) {
        this.serverName = serverName;
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.healthCheck().timeoutMs()))
                .build();
    }

    /**
     * GETs this server's declared health-check URL and reports UP/DOWN based on a 2xx response.
     *
     * @return the component health, including measured latency
     */
    public ComponentHealthDto checkHealth() {
        McpSettings.HealthCheckConfig healthCheck = config.healthCheck();
        long startTime = System.currentTimeMillis();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthCheck.url()))
                    .timeout(Duration.ofMillis(healthCheck.timeoutMs()))
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long latency = System.currentTimeMillis() - startTime;
            boolean isHealthy = response.statusCode() >= 200 && response.statusCode() < 300;

            return ComponentHealthDto.builder()
                    .status(isHealthy
                            ? AppConstants.HealthStatus.UP.getValue()
                            : AppConstants.HealthStatus.DOWN.getValue())
                    .message(isHealthy
                            ? HealthCheckConstants.Messages.MCP_CONNECTED
                            : HealthCheckConstants.Messages.MCP_NOT_AVAILABLE)
                    .latencyMs(latency)
                    .optional(config.optional())
                    .build();

        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            long latency = System.currentTimeMillis() - startTime;
            log.warn("MCP health check failed")
                    .field("server", serverName)
                    .field("url", healthCheck.url())
                    .field("error", e.getMessage())
                    .log();
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .message(HealthCheckConstants.Messages.MCP_NOT_AVAILABLE)
                    .latencyMs(latency)
                    .optional(config.optional())
                    .build();
        }
    }

    public String getServerName() {
        return serverName;
    }

    public McpSettings.ServerConfig getConfig() {
        return config;
    }
}
