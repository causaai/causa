package com.causa.mcp;

import com.causa.api.dto.ComponentHealthDto;
import com.causa.common.constants.AppConstants;
import com.causa.common.constants.HealthCheckConstants;
import com.causa.common.constants.McpConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.mcp.config.McpSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Plain (non-CDI) per-server MCP client — health checks and JSON-RPC 2.0/SSE tool calls.
 *
 * @since 0.0.1
 */
public class McpClient {

    private static final CausaLogger log = CausaLogger.getLogger(McpClient.class);

    private final String serverName;
    private final McpSettings.ServerConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

            ComponentHealthDto.Builder builder = ComponentHealthDto.builder()
                    .status(isHealthy
                            ? AppConstants.HealthStatus.UP.getValue()
                            : AppConstants.HealthStatus.DOWN.getValue())
                    .message(isHealthy
                            ? HealthCheckConstants.Messages.MCP_CONNECTED
                            : "Unexpected status code: " + response.statusCode())
                    .optional(config.optional() ? Boolean.TRUE : null);
            if (isHealthy) {
                builder.latencyMs(latency);
            }
            return builder.build();

        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("MCP health check failed")
                    .field("server", serverName)
                    .field("url", healthCheck.url())
                    .field("error", reason)
                    .log();
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .message("Unable to connect to the server.")
                    .optional(config.optional() ? Boolean.TRUE : null)
                    .build();
        }
    }

    /**
     * Calls an MCP tool via JSON-RPC 2.0/SSE and returns its extracted text payload, or
     * {@code null} on any failure.
     */
    public String callTool(String toolName, Map<String, String> arguments) {
        try {
            String sessionId = initializeMcpSession();
            ObjectNode args = objectMapper.createObjectNode();
            arguments.forEach((key, value) -> {
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                    args.put(key, Boolean.parseBoolean(value));
                } else {
                    args.put(key, value);
                }
            });
            JsonNode result = callMcpTool(sessionId, toolName, args);
            return extractTextFromContent(result);
        } catch (Exception e) {
            // TODO: we need catch and notify user
            log.warn("MCP tool call failed")
                    .field("server", serverName)
                    .field("tool", toolName)
                    .field("error", e.getMessage())
                    .log();
            return null;
        }
    }

    private String initializeMcpSession() throws Exception {
        ObjectNode initRequest = objectMapper.createObjectNode();
        initRequest.put(McpConstants.JsonRpc.FIELD_JSONRPC, McpConstants.JsonRpc.VERSION);
        initRequest.put(McpConstants.JsonRpc.FIELD_ID, 1);
        initRequest.put(McpConstants.JsonRpc.FIELD_METHOD, McpConstants.JsonRpc.METHOD_INITIALIZE);

        ObjectNode params = objectMapper.createObjectNode();
        params.put(McpConstants.JsonRpc.PARAM_PROTOCOL_VERSION, McpConstants.PROTOCOL_VERSION);

        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put(McpConstants.Arguments.NAME, McpConstants.CLIENT_NAME);
        clientInfo.put(McpConstants.Format.VERSION, McpConstants.CLIENT_VERSION);
        params.set(McpConstants.JsonRpc.PARAM_CLIENT_INFO, clientInfo);
        params.set(McpConstants.JsonRpc.PARAM_CAPABILITIES, objectMapper.createObjectNode());
        initRequest.set(McpConstants.JsonRpc.FIELD_PARAMS, params);

        int timeoutMs = config.timeoutMs();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.url()))
                .header(McpConstants.Headers.CONTENT_TYPE, McpConstants.Headers.CONTENT_TYPE_JSON)
                .header(McpConstants.Headers.ACCEPT, McpConstants.Headers.ACCEPT_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(initRequest.toString()))
                .timeout(Duration.ofMillis(timeoutMs))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException(String.format(McpConstants.Errors.MCP_INITIALIZE_FAILED,
                    response.statusCode(), response.body()));
        }

        String sessionId = response.headers().firstValue(McpConstants.Headers.MCP_SESSION_ID)
                .orElse(UUID.randomUUID().toString());
        sendInitializedNotification(sessionId, timeoutMs);
        return sessionId;
    }

    private void sendInitializedNotification(String sessionId, int timeoutMs) throws Exception {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put(McpConstants.JsonRpc.FIELD_JSONRPC, McpConstants.JsonRpc.VERSION);
        notification.put(McpConstants.JsonRpc.FIELD_METHOD, McpConstants.JsonRpc.METHOD_NOTIFICATIONS_INITIALIZED);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.url()))
                .header(McpConstants.Headers.CONTENT_TYPE, McpConstants.Headers.CONTENT_TYPE_JSON)
                .header(McpConstants.Headers.ACCEPT, McpConstants.Headers.ACCEPT_VALUE)
                .header(McpConstants.Headers.MCP_SESSION_ID, sessionId)
                .POST(HttpRequest.BodyPublishers.ofString(notification.toString()))
                .timeout(Duration.ofMillis(timeoutMs))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode callMcpTool(String sessionId, String toolName, ObjectNode arguments) throws Exception {
        ObjectNode toolRequest = objectMapper.createObjectNode();
        toolRequest.put(McpConstants.JsonRpc.FIELD_JSONRPC, McpConstants.JsonRpc.VERSION);
        toolRequest.put(McpConstants.JsonRpc.FIELD_ID, 2);
        toolRequest.put(McpConstants.JsonRpc.FIELD_METHOD, McpConstants.JsonRpc.METHOD_TOOLS_CALL);

        ObjectNode params = objectMapper.createObjectNode();
        params.put(McpConstants.JsonRpc.PARAM_NAME, toolName);
        params.set(McpConstants.JsonRpc.PARAM_ARGUMENTS, arguments);
        toolRequest.set(McpConstants.JsonRpc.FIELD_PARAMS, params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.url()))
                .header(McpConstants.Headers.CONTENT_TYPE, McpConstants.Headers.CONTENT_TYPE_JSON)
                .header(McpConstants.Headers.ACCEPT, McpConstants.Headers.ACCEPT_VALUE)
                .header(McpConstants.Headers.MCP_SESSION_ID, sessionId)
                .POST(HttpRequest.BodyPublishers.ofString(toolRequest.toString()))
                .timeout(Duration.ofMillis(config.timeoutMs()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException(String.format(McpConstants.Errors.MCP_TOOL_CALL_FAILED,
                    response.statusCode(), response.body()));
        }

        JsonNode responseNode = objectMapper.readTree(parseSSEResponse(response.body()));
        if (responseNode.has(McpConstants.JsonRpc.FIELD_ERROR)) {
            throw new RuntimeException(String.format(McpConstants.Errors.MCP_TOOL_ERROR,
                    responseNode.get(McpConstants.JsonRpc.FIELD_ERROR).toString()));
        }
        return responseNode.get(McpConstants.JsonRpc.FIELD_RESULT);
    }

    private String parseSSEResponse(String sseResponse) {
        for (String line : sseResponse.split(McpConstants.SSE.LINE_SEPARATOR)) {
            if (line.startsWith(McpConstants.SSE.DATA_PREFIX)) {
                return line.substring(McpConstants.SSE.DATA_PREFIX.length()).trim();
            }
        }
        return sseResponse;
    }

    private String extractTextFromContent(JsonNode result) {
        if (result == null || !result.has(McpConstants.JsonRpc.FIELD_CONTENT)
                || !result.get(McpConstants.JsonRpc.FIELD_CONTENT).isArray()) {
            return null;
        }
        ArrayNode content = (ArrayNode) result.get(McpConstants.JsonRpc.FIELD_CONTENT);
        if (content.isEmpty() || !content.get(0).has(McpConstants.JsonRpc.FIELD_TEXT)) {
            return null;
        }
        String text = content.get(0).get(McpConstants.JsonRpc.FIELD_TEXT).asText();
        if (text != null && (text.contains(McpConstants.ErrorMarkers.ERROR_CALLING_TOOL)
                || text.contains(McpConstants.ErrorMarkers.LIST_INDEX_OUT_OF_RANGE))) {
            return McpConstants.Defaults.NO_DATA_AVAILABLE;
        }
        return text;
    }

    public String getServerName() {
        return serverName;
    }

    public McpSettings.ServerConfig getConfig() {
        return config;
    }
}
