package com.causa.mcp;

import com.causa.common.constants.McpConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.McpConfig;
import com.causa.core.domain.Alert;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * MCP Context Collector
 *
 * <p>Collects diagnostic context from Kubernetes and Kruize MCP servers.
 * Calls MCP tools and logs results for diagnostic analysis.
 *
 * <p>This is an MVP implementation that prints context to logs without storing it.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class McpContextCollector {

    private static final CausaLogger log = CausaLogger.getLogger(McpContextCollector.class);

    private final McpConfig mcpConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Inject
    public McpContextCollector(McpConfig mcpConfig) {
        this.mcpConfig = mcpConfig;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    /**
     * Collects context from MCP servers and logs results.
     *
     * <p>Calls Kubernetes MCP for pod status, events, and logs.
     * Calls Kruize MCP for cost optimization recommendations.
     *
     * @param alert the alert to collect context for
     */
    public void collectAndLogContext(Alert alert) {
        log.info(LogMessages.Mcp.MCP_CONTEXT_COLLECTION_START)
            .field("alertId", alert.getAlertId())
            .field("podName", alert.getPodName())
            .field("namespace", alert.getNamespace())
            .log();

        // Skip Kubernetes calls if no pod name
        if (alert.getPodName() == null || alert.getPodName().isBlank()) {
            log.info(LogMessages.Mcp.MCP_SKIPPED_NO_POD)
                .field("alertId", alert.getAlertId())
                .log();
        } else {
            collectKubernetesPodStatus(alert);
            collectKubernetesPodEvents(alert);
            collectKubernetesPodLogs(alert);
        }
    }

    /**
     * Calls Kubernetes MCP pods_get tool to retrieve pod status.
     */
    private void collectKubernetesPodStatus(Alert alert) {
        try {
            String sessionId = initializeMcpSession(mcpConfig.kubernetes().endpoint() + "/mcp");

            ObjectNode arguments = objectMapper.createObjectNode();
            arguments.put(McpConstants.Arguments.NAME, alert.getPodName());
            arguments.put(McpConstants.Arguments.NAMESPACE, alert.getNamespace());

            JsonNode result = callMcpTool(
                mcpConfig.kubernetes().endpoint() + "/mcp",
                sessionId,
                McpConstants.Tools.PODS_GET,
                arguments,
                mcpConfig.kubernetes().timeoutMs()
            );

            String podStatus = extractPodStatus(result);

            log.info(LogMessages.Mcp.MCP_K8S_POD_STATUS)
                .field("alertId", alert.getAlertId())
                .field("podName", alert.getPodName())
                .field("namespace", alert.getNamespace())
                .field("status", podStatus)
                .log();

            // Print formatted pod status
            System.out.println(McpConstants.OutputHeaders.POD_STATUS);
            System.out.println("Pod: " + alert.getPodName());
            System.out.println("Namespace: " + alert.getNamespace());
            System.out.println("Status: " + podStatus);
            System.out.println();

        } catch (Exception e) {
            log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                .field("tool", "pods_get")
                .field("alertId", alert.getAlertId())
                .field("error", e.getMessage())
                .log();
            System.out.println(McpConstants.OutputHeaders.POD_STATUS);
            System.out.println(String.format(McpConstants.Errors.UNABLE_TO_GET_POD_STATUS, e.getMessage()));
            System.out.println();
        }
    }

    /**
     * Calls Kubernetes MCP events_list tool to retrieve pod events.
     */
    private void collectKubernetesPodEvents(Alert alert) {
        try {
            String sessionId = initializeMcpSession(mcpConfig.kubernetes().endpoint() + "/mcp");

            ObjectNode arguments = objectMapper.createObjectNode();
            arguments.put(McpConstants.Arguments.FIELD_SELECTOR, "involvedObject.name=" + alert.getPodName());
            arguments.put(McpConstants.Arguments.NAMESPACE, alert.getNamespace());

            JsonNode result = callMcpTool(
                mcpConfig.kubernetes().endpoint() + "/mcp",
                sessionId,
                McpConstants.Tools.EVENTS_LIST,
                arguments,
                mcpConfig.kubernetes().timeoutMs()
            );

            String eventsText = extractEventsText(result);

            log.info(LogMessages.Mcp.MCP_K8S_POD_EVENTS)
                .field("alertId", alert.getAlertId())
                .field("podName", alert.getPodName())
                .log();

            // Print formatted events
            System.out.println(String.format(McpConstants.OutputHeaders.KUBERNETES_EVENTS, alert.getPodName()));
            System.out.println(eventsText);
            System.out.println();

        } catch (Exception e) {
            log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                .field("tool", "events_list")
                .field("alertId", alert.getAlertId())
                .field("error", e.getMessage())
                .log();
            System.out.println(String.format(McpConstants.OutputHeaders.KUBERNETES_EVENTS, alert.getPodName()));
            System.out.println(String.format(McpConstants.Errors.UNABLE_TO_GET_EVENTS, e.getMessage()));
            System.out.println();
        }
    }

    /**
     * Calls Kubernetes MCP pods_log tool to retrieve pod logs.
     */
    private void collectKubernetesPodLogs(Alert alert) {
        try {
            String sessionId = initializeMcpSession(mcpConfig.kubernetes().endpoint() + "/mcp");

            ObjectNode arguments = objectMapper.createObjectNode();
            arguments.put(McpConstants.Arguments.NAME, alert.getPodName());
            arguments.put(McpConstants.Arguments.NAMESPACE, alert.getNamespace());
            arguments.put(McpConstants.Arguments.TAIL_LINES, McpConstants.Defaults.DEFAULT_TAIL_LINES);
            if (alert.getContainerName() != null && !alert.getContainerName().isBlank()) {
                arguments.put(McpConstants.Arguments.CONTAINER, alert.getContainerName());
            }

            JsonNode result = callMcpTool(
                mcpConfig.kubernetes().endpoint() + "/mcp",
                sessionId,
                McpConstants.Tools.PODS_LOG,
                arguments,
                mcpConfig.kubernetes().timeoutMs()
            );

            String logsText = extractLogsText(result);

            log.info(LogMessages.Mcp.MCP_K8S_POD_LOGS)
                .field("alertId", alert.getAlertId())
                .field("podName", alert.getPodName())
                .field("container", alert.getContainerName())
                .log();

            // Print formatted logs
            System.out.println(McpConstants.OutputHeaders.POD_LOGS);
            System.out.println("Pod: " + alert.getPodName() + " | Container: " + alert.getContainerName());
            System.out.println(logsText);
            System.out.println();

        } catch (Exception e) {
            log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                .field("tool", "pods_log")
                .field("alertId", alert.getAlertId())
                .field("error", e.getMessage())
                .log();
            System.out.println(McpConstants.OutputHeaders.POD_LOGS);
            System.out.println(String.format(McpConstants.Errors.UNABLE_TO_GET_LOGS, e.getMessage()));
            System.out.println();
        }
    }


    /**
     * Initializes MCP session and returns session ID.
     */
    private String initializeMcpSession(String endpoint) throws Exception {
        ObjectNode initRequest = objectMapper.createObjectNode();
        initRequest.put(McpConstants.JsonRpc.FIELD_JSONRPC, McpConstants.JsonRpc.VERSION);
        initRequest.put(McpConstants.JsonRpc.FIELD_ID, 1);
        initRequest.put(McpConstants.JsonRpc.FIELD_METHOD, McpConstants.JsonRpc.METHOD_INITIALIZE);

        ObjectNode params = objectMapper.createObjectNode();
        params.put(McpConstants.JsonRpc.PARAM_PROTOCOL_VERSION, McpConstants.PROTOCOL_VERSION);

        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put(McpConstants.Arguments.NAME, McpConstants.CLIENT_NAME);
        clientInfo.put("version", McpConstants.CLIENT_VERSION);
        params.set(McpConstants.JsonRpc.PARAM_CLIENT_INFO, clientInfo);

        ObjectNode capabilities = objectMapper.createObjectNode();
        params.set(McpConstants.JsonRpc.PARAM_CAPABILITIES, capabilities);

        initRequest.set(McpConstants.JsonRpc.FIELD_PARAMS, params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header(McpConstants.Headers.CONTENT_TYPE, McpConstants.Headers.CONTENT_TYPE_JSON)
            .header(McpConstants.Headers.ACCEPT, McpConstants.Headers.ACCEPT_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(initRequest.toString()))
            .timeout(Duration.ofMillis(mcpConfig.kubernetes().timeoutMs()))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(String.format(McpConstants.Errors.MCP_INITIALIZE_FAILED,
                response.statusCode(), response.body()));
        }

        // Parse SSE response (Server-Sent Events format)
        String responseBody = response.body();
        String jsonData = parseSSEResponse(responseBody);

        JsonNode responseNode = objectMapper.readTree(jsonData);

        // Extract session ID from Mcp-Session-Id header or generate one
        String sessionId = response.headers().firstValue(McpConstants.Headers.MCP_SESSION_ID)
            .orElse(UUID.randomUUID().toString());

        // Send notifications/initialized as per MCP protocol
        sendInitializedNotification(endpoint, sessionId);

        return sessionId;
    }

    /**
     * Sends the initialized notification after successful initialize.
     * Required by MCP protocol before calling tools.
     */
    private void sendInitializedNotification(String endpoint, String sessionId) throws Exception {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put(McpConstants.JsonRpc.FIELD_JSONRPC, McpConstants.JsonRpc.VERSION);
        notification.put(McpConstants.JsonRpc.FIELD_METHOD, McpConstants.JsonRpc.METHOD_NOTIFICATIONS_INITIALIZED);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header(McpConstants.Headers.CONTENT_TYPE, McpConstants.Headers.CONTENT_TYPE_JSON)
            .header(McpConstants.Headers.ACCEPT, McpConstants.Headers.ACCEPT_VALUE)
            .header(McpConstants.Headers.MCP_SESSION_ID, sessionId)
            .POST(HttpRequest.BodyPublishers.ofString(notification.toString()))
            .timeout(Duration.ofMillis(mcpConfig.kubernetes().timeoutMs()))
            .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        // Note: notifications don't expect a response, just send and continue
    }

    /**
     * Calls an MCP tool via JSON-RPC 2.0.
     */
    private JsonNode callMcpTool(String endpoint, String sessionId, String toolName,
                                  ObjectNode arguments, int timeoutMs) throws Exception {
        ObjectNode toolRequest = objectMapper.createObjectNode();
        toolRequest.put(McpConstants.JsonRpc.FIELD_JSONRPC, McpConstants.JsonRpc.VERSION);
        toolRequest.put(McpConstants.JsonRpc.FIELD_ID, 2);
        toolRequest.put(McpConstants.JsonRpc.FIELD_METHOD, McpConstants.JsonRpc.METHOD_TOOLS_CALL);

        ObjectNode params = objectMapper.createObjectNode();
        params.put(McpConstants.JsonRpc.PARAM_NAME, toolName);
        params.set(McpConstants.JsonRpc.PARAM_ARGUMENTS, arguments);
        toolRequest.set(McpConstants.JsonRpc.FIELD_PARAMS, params);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header(McpConstants.Headers.CONTENT_TYPE, McpConstants.Headers.CONTENT_TYPE_JSON)
            .header(McpConstants.Headers.ACCEPT, McpConstants.Headers.ACCEPT_VALUE)
            .header(McpConstants.Headers.MCP_SESSION_ID, sessionId)
            .POST(HttpRequest.BodyPublishers.ofString(toolRequest.toString()))
            .timeout(Duration.ofMillis(timeoutMs));

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(String.format(McpConstants.Errors.MCP_TOOL_CALL_FAILED,
                response.statusCode(), response.body()));
        }

        // Parse SSE response
        String responseBody = response.body();
        String jsonData = parseSSEResponse(responseBody);

        JsonNode responseNode = objectMapper.readTree(jsonData);

        // Check for JSON-RPC error
        if (responseNode.has(McpConstants.JsonRpc.FIELD_ERROR)) {
            JsonNode error = responseNode.get(McpConstants.JsonRpc.FIELD_ERROR);
            throw new RuntimeException(String.format(McpConstants.Errors.MCP_TOOL_ERROR, error.toString()));
        }

        // Return the result field
        return responseNode.get(McpConstants.JsonRpc.FIELD_RESULT);
    }

    /**
     * Parses SSE (Server-Sent Events) response format.
     * SSE format: "event: message\ndata: {json}\n\n"
     */
    private String parseSSEResponse(String sseResponse) {
        // SSE format has "event: message" followed by "data: {json}"
        String[] lines = sseResponse.split(McpConstants.SSE.LINE_SEPARATOR);
        for (String line : lines) {
            if (line.startsWith(McpConstants.SSE.DATA_PREFIX)) {
                return line.substring(McpConstants.SSE.DATA_PREFIX.length()).trim();
            }
        }
        // If no SSE format detected, assume it's plain JSON
        return sseResponse;
    }

    /**
     * Extracts pod status/phase from pods_get result.
     */
    private String extractPodStatus(JsonNode result) {
        if (result == null) {
            return McpConstants.Defaults.UNKNOWN_STATUS;
        }

        String text = extractTextFromContent(result);
        if (text == null) {
            return "Unknown";
        }

        try {
            // Parse YAML/JSON response
            if (text.contains("phase:")) {
                // Extract phase from YAML
                String[] lines = text.split("\n");
                for (String line : lines) {
                    if (line.trim().startsWith("phase:")) {
                        String phase = line.split(":", 2)[1].trim();

                        // Also check for container state
                        String containerState = extractContainerState(lines);
                        if (containerState != null) {
                            return phase + " (" + containerState + ")";
                        }
                        return phase;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse pod status").field("error", e.getMessage()).log();
        }

        return McpConstants.Defaults.UNKNOWN_STATUS;
    }

    /**
     * Extracts container state (CrashLoopBackOff, etc.) from YAML lines.
     */
    private String extractContainerState(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("reason:") && i > 0) {
                String prevLine = lines[i-1].trim();
                if (prevLine.equals("waiting:") || prevLine.equals("terminated:")) {
                    return line.split(":", 2)[1].trim();
                }
            }
        }
        return null;
    }

    /**
     * Extracts events text from MCP response.
     */
    private String extractEventsText(JsonNode result) {
        String text = extractTextFromContent(result);
        if (text == null || text.isEmpty()) {
            return McpConstants.Defaults.NO_EVENTS_FOUND;
        }

        // Parse YAML events and format them nicely
        StringBuilder formatted = new StringBuilder();
        String[] lines = text.split("\n");

        String currentType = null;
        String currentReason = null;
        String currentMessage = null;
        String currentTimestamp = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Type:")) {
                currentType = trimmed.split(":", 2)[1].trim();
            } else if (trimmed.startsWith("Reason:")) {
                currentReason = trimmed.split(":", 2)[1].trim();
            } else if (trimmed.startsWith("Message:")) {
                currentMessage = trimmed.split(":", 2)[1].trim();
            } else if (trimmed.startsWith("Timestamp:")) {
                currentTimestamp = trimmed.split(":", 2)[1].trim();

                // Print event when we have all fields
                if (currentType != null && currentReason != null) {
                    formatted.append(String.format("[%s] %s: %s - %s\n",
                        currentType, currentTimestamp, currentReason, currentMessage));
                    currentType = null;
                    currentReason = null;
                    currentMessage = null;
                    currentTimestamp = null;
                }
            }
        }

        return formatted.length() > 0 ? formatted.toString() : text;
    }

    /**
     * Extracts logs text from MCP response and returns only last lines.
     */
    private String extractLogsText(JsonNode result) {
        String text = extractTextFromContent(result);
        if (text == null || text.isEmpty()) {
            return McpConstants.Defaults.NO_LOGS_AVAILABLE;
        }

        // Extract only last lines
        String[] lines = text.split(McpConstants.SSE.LINE_SEPARATOR);
        int totalLines = lines.length;
        int startIndex = Math.max(0, totalLines - McpConstants.Defaults.DEFAULT_TAIL_LINES);

        StringBuilder lastLines = new StringBuilder();
        for (int i = startIndex; i < totalLines; i++) {
            if (lines[i] != null && !lines[i].trim().isEmpty()) {
                lastLines.append(lines[i]).append(McpConstants.SSE.LINE_SEPARATOR);
            }
        }

        String result_str = lastLines.toString();
        return result_str.isEmpty() ? McpConstants.Defaults.NO_LOGS_AVAILABLE : result_str.trim();
    }


    /**
     * Extracts text content from MCP response structure.
     */
    private String extractTextFromContent(JsonNode result) {
        if (result == null) {
            return null;
        }

        if (result.has(McpConstants.JsonRpc.FIELD_CONTENT) && result.get(McpConstants.JsonRpc.FIELD_CONTENT).isArray()) {
            ArrayNode content = (ArrayNode) result.get(McpConstants.JsonRpc.FIELD_CONTENT);
            if (content.size() > 0) {
                JsonNode firstContent = content.get(0);
                if (firstContent.has(McpConstants.JsonRpc.FIELD_TEXT)) {
                    return firstContent.get(McpConstants.JsonRpc.FIELD_TEXT).asText();
                }
            }
        }

        return null;
    }
}
