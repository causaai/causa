package com.causa.mcp;

import com.causa.common.constants.McpConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.McpConfig;
import com.causa.core.domain.Alert;
import com.causa.core.domain.DiagnosticContext;
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
     * Collects diagnostic context from all MCP servers (Kubernetes, Kruize, Cryostat).
     *
     * <p>Aggregates pod status, events, logs, resource recommendations, and JFR analysis
     * into a single {@link DiagnosticContext} object for LLM consumption.
     *
     * @param alert the alert to collect context for
     * @return diagnostic context with all collected data (fields are nullable on failure)
     */
    public DiagnosticContext collectContext(Alert alert) {
        log.info(LogMessages.Mcp.MCP_CONTEXT_COLLECTION_START)
            .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
            .field(McpConstants.LogFields.POD_NAME, alert.getPodName())
            .field(McpConstants.LogFields.NAMESPACE, alert.getNamespace())
            .log();

        DiagnosticContext.Builder contextBuilder = DiagnosticContext.builder()
            .podName(alert.getPodName())
            .containerName(alert.getContainerName())
            .namespace(alert.getNamespace());

        String resolvedContainerName = alert.getContainerName();
        String fullPodYaml = null; // Keep full YAML for container name extraction

        // Kubernetes context collection
        if (alert.getPodName() != null && !alert.getPodName().isBlank()) {
            fullPodYaml = collectKubernetesPodStatus(alert, contextBuilder);

            // Try to extract container name from pod status if not in alert
            if (resolvedContainerName == null || resolvedContainerName.isBlank()) {
                resolvedContainerName = extractContainerNameFromPodStatus(fullPodYaml);
                contextBuilder.containerName(resolvedContainerName);
            }

            contextBuilder.podEvents(collectKubernetesPodEvents(alert));
            contextBuilder.podLogs(collectKubernetesPodLogs(alert));
        } else {
            log.info(LogMessages.Mcp.MCP_SKIPPED_NO_POD)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .log();
        }

        // Kruize context collection (requires container name)
        if (resolvedContainerName != null && !resolvedContainerName.isBlank()) {
            collectKruizeContext(contextBuilder, alert, resolvedContainerName);
        } else {
            log.info(LogMessages.Mcp.MCP_KRUIZE_SKIPPED_NO_CONTAINER)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .log();
        }

        // Cryostat context collection (requires pod name)
        if (alert.getPodName() != null && !alert.getPodName().isBlank()) {
            collectCryostatContext(contextBuilder, alert);
        }

        DiagnosticContext context = contextBuilder.build();

        log.info(LogMessages.Mcp.MCP_CONTEXT_COLLECTION_COMPLETE)
            .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
            .field(McpConstants.LogFields.HAS_K8S_CONTEXT, context.hasKubernetesContext())
            .field(McpConstants.LogFields.HAS_KRUIZE_CONTEXT, context.hasKruizeContext())
            .field(McpConstants.LogFields.HAS_CRYOSTAT_CONTEXT, context.hasCryostatContext())
            .log();

        return context;
    }

    /**
     * Calls Kubernetes MCP pods_get tool to retrieve pod status.
     * Sets the formatted summary in contextBuilder and returns full YAML for container extraction.
     *
     * @param alert the alert
     * @param contextBuilder the context builder to populate with formatted summary
     * @return full pod YAML for container name extraction, or null on failure
     */
    private String collectKubernetesPodStatus(Alert alert, DiagnosticContext.Builder contextBuilder) {
        try {
            String sessionId = initializeMcpSession(
                mcpConfig.kubernetes().endpoint() + McpConstants.Paths.MCP_ENDPOINT,
                mcpConfig.kubernetes().timeoutMs()
            );

            ObjectNode arguments = objectMapper.createObjectNode();
            arguments.put(McpConstants.Arguments.NAME, alert.getPodName());
            arguments.put(McpConstants.Arguments.NAMESPACE, alert.getNamespace());

            JsonNode result = callMcpTool(
                mcpConfig.kubernetes().endpoint() + McpConstants.Paths.MCP_ENDPOINT,
                sessionId,
                McpConstants.Tools.PODS_GET,
                arguments,
                mcpConfig.kubernetes().timeoutMs()
            );

            String podStatusYaml = extractTextFromContent(result);
            String podPhase = extractPodStatus(podStatusYaml);

            log.info(LogMessages.Mcp.MCP_K8S_POD_STATUS)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .field(McpConstants.LogFields.POD_NAME, alert.getPodName())
                .field(McpConstants.LogFields.NAMESPACE, alert.getNamespace())
                .field(McpConstants.LogFields.STATUS, podPhase)
                .log();

            // Extract and set summary in context
            String summary = extractPodStatusSummary(podStatusYaml);
            contextBuilder.podStatus(summary);

            // Return full YAML for container name extraction
            return podStatusYaml;

        } catch (Exception e) {
            log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                .field(McpConstants.LogFields.TOOL, McpConstants.Tools.PODS_GET)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .field(McpConstants.LogFields.ERROR, e.getMessage())
                .log();
            return null;
        }
    }

    /**
     * Calls Kubernetes MCP events_list tool to retrieve pod events.
     *
     * @return formatted events text, or null on failure
     */
    private String collectKubernetesPodEvents(Alert alert) {
        try {
            String sessionId = initializeMcpSession(
                mcpConfig.kubernetes().endpoint() + McpConstants.Paths.MCP_ENDPOINT,
                mcpConfig.kubernetes().timeoutMs()
            );

            ObjectNode arguments = objectMapper.createObjectNode();
            arguments.put(McpConstants.Arguments.FIELD_SELECTOR, McpConstants.Format.INVOLVED_OBJECT_NAME_PREFIX + alert.getPodName());
            arguments.put(McpConstants.Arguments.NAMESPACE, alert.getNamespace());

            JsonNode result = callMcpTool(
                mcpConfig.kubernetes().endpoint() + McpConstants.Paths.MCP_ENDPOINT,
                sessionId,
                McpConstants.Tools.EVENTS_LIST,
                arguments,
                mcpConfig.kubernetes().timeoutMs()
            );

            String eventsText = extractEventsText(result);

            log.info(LogMessages.Mcp.MCP_K8S_POD_EVENTS)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .field(McpConstants.LogFields.POD_NAME, alert.getPodName())
                .log();

            return eventsText;

        } catch (Exception e) {
            log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                .field(McpConstants.LogFields.TOOL, McpConstants.Tools.EVENTS_LIST)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .field(McpConstants.LogFields.ERROR, e.getMessage())
                .log();
            return null;
        }
    }

    /**
     * Calls Kubernetes MCP pods_log tool to retrieve pod logs.
     *
     * @return formatted log text (last 25 lines), or null on failure
     */
    private String collectKubernetesPodLogs(Alert alert) {
        try {
            String sessionId = initializeMcpSession(
                mcpConfig.kubernetes().endpoint() + McpConstants.Paths.MCP_ENDPOINT,
                mcpConfig.kubernetes().timeoutMs()
            );

            ObjectNode arguments = objectMapper.createObjectNode();
            arguments.put(McpConstants.Arguments.NAME, alert.getPodName());
            arguments.put(McpConstants.Arguments.NAMESPACE, alert.getNamespace());
            arguments.put(McpConstants.Arguments.TAIL_LINES, McpConstants.Defaults.DEFAULT_TAIL_LINES);
            if (alert.getContainerName() != null && !alert.getContainerName().isBlank()) {
                arguments.put(McpConstants.Arguments.CONTAINER, alert.getContainerName());
            }

            JsonNode result = callMcpTool(
                mcpConfig.kubernetes().endpoint() + McpConstants.Paths.MCP_ENDPOINT,
                sessionId,
                McpConstants.Tools.PODS_LOG,
                arguments,
                mcpConfig.kubernetes().timeoutMs()
            );

            String logsText = extractLogsText(result);

            log.info(LogMessages.Mcp.MCP_K8S_POD_LOGS)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .field(McpConstants.LogFields.POD_NAME, alert.getPodName())
                .field(McpConstants.LogFields.CONTAINER, alert.getContainerName())
                .log();

            return logsText;

        } catch (Exception e) {
            log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                .field(McpConstants.LogFields.TOOL, McpConstants.Tools.PODS_LOG)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .field(McpConstants.LogFields.ERROR, e.getMessage())
                .log();
            return null;
        }
    }


    /**
     * Initializes MCP session and returns session ID.
     *
     * @param endpoint the MCP endpoint URL
     * @param timeoutMs HTTP timeout in milliseconds
     * @return the session ID
     */
    private String initializeMcpSession(String endpoint, int timeoutMs) throws Exception {
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

        ObjectNode capabilities = objectMapper.createObjectNode();
        params.set(McpConstants.JsonRpc.PARAM_CAPABILITIES, capabilities);

        initRequest.set(McpConstants.JsonRpc.FIELD_PARAMS, params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
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

        // Parse SSE response (Server-Sent Events format)
        String responseBody = response.body();
        String jsonData = parseSSEResponse(responseBody);

        JsonNode responseNode = objectMapper.readTree(jsonData);

        // Extract session ID from Mcp-Session-Id header or generate one
        String sessionId = response.headers().firstValue(McpConstants.Headers.MCP_SESSION_ID)
            .orElse(UUID.randomUUID().toString());

        // Send notifications/initialized as per MCP protocol
        sendInitializedNotification(endpoint, sessionId, timeoutMs);

        return sessionId;
    }

    /**
     * Sends the initialized notification after successful initialize.
     * Required by MCP protocol before calling tools.
     */
    private void sendInitializedNotification(String endpoint, String sessionId, int timeoutMs) throws Exception {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put(McpConstants.JsonRpc.FIELD_JSONRPC, McpConstants.JsonRpc.VERSION);
        notification.put(McpConstants.JsonRpc.FIELD_METHOD, McpConstants.JsonRpc.METHOD_NOTIFICATIONS_INITIALIZED);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header(McpConstants.Headers.CONTENT_TYPE, McpConstants.Headers.CONTENT_TYPE_JSON)
            .header(McpConstants.Headers.ACCEPT, McpConstants.Headers.ACCEPT_VALUE)
            .header(McpConstants.Headers.MCP_SESSION_ID, sessionId)
            .POST(HttpRequest.BodyPublishers.ofString(notification.toString()))
            .timeout(Duration.ofMillis(timeoutMs))
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
     * Extracts pod status/phase from pods_get text (YAML format).
     *
     * @param text the raw YAML text from pods_get
     * @return the pod phase + container state, or "Unknown"
     */
    private String extractPodStatus(String text) {
        if (text == null || text.isBlank()) {
            return McpConstants.Defaults.UNKNOWN_STATUS;
        }

        try {
            // Parse YAML/JSON response
            if (text.contains(McpConstants.Yaml.PHASE_PREFIX)) {
                // Extract phase from YAML
                String[] lines = text.split(McpConstants.SSE.LINE_SEPARATOR);
                for (String line : lines) {
                    if (line.trim().startsWith(McpConstants.Yaml.PHASE_PREFIX)) {
                        String phase = line.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();

                        // Also check for container state
                        String containerState = extractContainerState(lines);
                        if (containerState != null) {
                            return phase + McpConstants.Format.PARENTHESIS_FORMAT.formatted(containerState);
                        }
                        return phase;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse pod status").field(McpConstants.LogFields.ERROR, e.getMessage()).log();
        }

        return McpConstants.Defaults.UNKNOWN_STATUS;
    }

    /**
     * Extracts container state (CrashLoopBackOff, etc.) from YAML lines.
     */
    private String extractContainerState(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith(McpConstants.Yaml.REASON_PREFIX) && i > 0) {
                String prevLine = lines[i-1].trim();
                if (prevLine.equals(McpConstants.Yaml.WAITING_STATE) || prevLine.equals(McpConstants.Yaml.TERMINATED_STATE)) {
                    return line.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
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
            if (trimmed.startsWith(McpConstants.Yaml.TYPE_PREFIX)) {
                currentType = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
            } else if (trimmed.startsWith(McpConstants.Yaml.REASON_FIELD)) {
                currentReason = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
            } else if (trimmed.startsWith(McpConstants.Yaml.MESSAGE_FIELD)) {
                currentMessage = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
            } else if (trimmed.startsWith(McpConstants.Yaml.TIMESTAMP_FIELD)) {
                currentTimestamp = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();

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
     * Returns "No Data Available" if response contains errors.
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
                    String text = firstContent.get(McpConstants.JsonRpc.FIELD_TEXT).asText();

                    // Check if the text contains error messages from MCP server
                    if (text != null && (text.contains(McpConstants.ErrorMarkers.ERROR_CALLING_TOOL) ||
                                         text.contains(McpConstants.ErrorMarkers.LIST_INDEX_OUT_OF_RANGE))) {
                        log.debug(LogMessages.Mcp.MCP_ERROR_DETECTED)
                            .field(McpConstants.LogFields.ERROR_TEXT, text)
                            .log();
                        return McpConstants.Defaults.NO_DATA_AVAILABLE;
                    }

                    return text;
                }
            }
        }

        return null;
    }

    /**
     * Extracts essential pod status information from full YAML.
     * Returns: state, startedAt, restartCount, resources (requests/limits)
     *
     * @param podStatusYaml full pod YAML from pods_get
     * @return formatted summary string
     */
    private String extractPodStatusSummary(String podStatusYaml) {
        if (podStatusYaml == null || podStatusYaml.isBlank()) {
            return "Pod status not available";
        }

        StringBuilder summary = new StringBuilder();
        String[] lines = podStatusYaml.split("\n");

        // Extract fields
        String state = null;
        String startedAt = null;
        String restartCount = null;
        String cpuLimit = null;
        String memoryLimit = null;
        String cpuRequest = null;
        String memoryRequest = null;

        // State machine for parsing
        boolean inStatus = false;
        boolean inContainerStatuses = false;
        boolean readingFirstContainerStatus = false;
        boolean inStateSection = false;

        boolean inSpec = false;
        boolean inSpecContainers = false;
        boolean readingFirstSpecContainer = false;
        boolean inResources = false;
        boolean inLimits = false;
        boolean inRequests = false;

        int currentIndent = 0;
        int containerStatusBaseIndent = 0;
        int specContainerBaseIndent = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // Calculate indent level
            int indent = line.length() - line.replaceAll("^\\s+", "").length();

            // Track status section
            if (trimmed.equals(McpConstants.Yaml.STATUS_SECTION)) {
                inStatus = true;
                inSpec = false;
                continue;
            }

            if (trimmed.equals(McpConstants.Yaml.SPEC_SECTION)) {
                inSpec = true;
                inStatus = false;
                continue;
            }

            // === Parse status.containerStatuses (for runtime state) ===
            if (inStatus && trimmed.equals(McpConstants.Yaml.CONTAINER_STATUSES_SECTION)) {
                inContainerStatuses = true;
                containerStatusBaseIndent = indent;
                continue;
            }

            if (inContainerStatuses) {
                // Found start of first container in containerStatuses
                if (trimmed.startsWith(McpConstants.Yaml.ITEM_PREFIX) && indent == containerStatusBaseIndent + 2) {
                    readingFirstContainerStatus = true;
                    currentIndent = indent;
                    continue;
                }

                // Stop if we hit another top-level key at same indent as containerStatuses
                if (indent <= containerStatusBaseIndent && !trimmed.isEmpty() && !trimmed.startsWith(McpConstants.Yaml.ITEM_PREFIX)) {
                    inContainerStatuses = false;
                    readingFirstContainerStatus = false;
                }

                if (readingFirstContainerStatus) {
                    if (trimmed.startsWith(McpConstants.Yaml.RESTART_COUNT_FIELD)) {
                        restartCount = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
                    } else if (trimmed.equals(McpConstants.Yaml.STATE_FIELD)) {
                        inStateSection = true;
                    } else if (inStateSection && trimmed.equals(McpConstants.Yaml.RUNNING_STATE)) {
                        state = "Running";
                    } else if (inStateSection && trimmed.equals(McpConstants.Yaml.WAITING_STATE)) {
                        state = "Waiting";
                    } else if (inStateSection && trimmed.equals(McpConstants.Yaml.TERMINATED_STATE)) {
                        state = "Terminated";
                    } else if (inStateSection && trimmed.startsWith(McpConstants.Yaml.STARTED_AT_FIELD)) {
                        startedAt = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim().replace("\"", "");
                        inStateSection = false;
                    }
                }
            }

            // === Parse spec.containers (for resources) ===
            if (inSpec && trimmed.equals(McpConstants.Yaml.CONTAINERS_SECTION)) {
                inSpecContainers = true;
                specContainerBaseIndent = indent;
                continue;
            }

            if (inSpec && trimmed.equals(McpConstants.Yaml.INIT_CONTAINER_STATUSES_SECTION)) {
                // Stop reading spec.containers when we hit initContainers
                inSpecContainers = false;
                readingFirstSpecContainer = false;
            }

            if (inSpecContainers) {
                // Found start of first container in spec.containers
                if (trimmed.startsWith(McpConstants.Yaml.ITEM_PREFIX) && indent == specContainerBaseIndent + 2) {
                    readingFirstSpecContainer = true;
                    currentIndent = indent;
                    continue;
                }

                if (readingFirstSpecContainer) {
                    if (trimmed.equals(McpConstants.Yaml.RESOURCES_SECTION)) {
                        inResources = true;
                    } else if (inResources && trimmed.equals(McpConstants.Yaml.LIMITS_SECTION)) {
                        inLimits = true;
                        inRequests = false;
                    } else if (inResources && trimmed.equals(McpConstants.Yaml.REQUESTS_SECTION)) {
                        inRequests = true;
                        inLimits = false;
                    } else if (inLimits && trimmed.startsWith(McpConstants.Yaml.CPU_FIELD)) {
                        cpuLimit = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
                    } else if (inLimits && trimmed.startsWith(McpConstants.Yaml.MEMORY_FIELD)) {
                        memoryLimit = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
                    } else if (inRequests && trimmed.startsWith(McpConstants.Yaml.CPU_FIELD)) {
                        cpuRequest = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
                    } else if (inRequests && trimmed.startsWith(McpConstants.Yaml.MEMORY_FIELD)) {
                        memoryRequest = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
                        // Done with first container
                        readingFirstSpecContainer = false;
                        inSpecContainers = false;
                        inResources = false;
                    }
                }
            }
        }

        // Format summary
        summary.append("State: ").append(state != null ? state : "Unknown").append("\n");
        if (startedAt != null) {
            summary.append("Started At: ").append(startedAt).append("\n");
        }
        summary.append("Restart Count: ").append(restartCount != null ? restartCount : "0").append("\n");
        summary.append("\nResource Limits:\n");
        summary.append("  CPU: ").append(cpuLimit != null ? cpuLimit : "not set").append("\n");
        summary.append("  Memory: ").append(memoryLimit != null ? memoryLimit : "not set").append("\n");
        summary.append("Resource Requests:\n");
        summary.append("  CPU: ").append(cpuRequest != null ? cpuRequest : "not set").append("\n");
        summary.append("  Memory: ").append(memoryRequest != null ? memoryRequest : "not set").append("\n");

        return summary.toString();
    }

    /**
     * Extracts the first container name from Kubernetes pod status YAML.
     *
     * @param podStatusText YAML-formatted pod status from pods_get
     * @return the first container name, or null if not found
     */
    private String extractContainerNameFromPodStatus(String podStatusText) {
        if (podStatusText == null || podStatusText.isBlank()) {
            return null;
        }
        try {
            // Parse YAML to find: containers: - name: <value>
            String[] lines = podStatusText.split(McpConstants.SSE.LINE_SEPARATOR);
            boolean inContainers = false;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.equals(McpConstants.Yaml.CONTAINERS_SECTION)) {
                    inContainers = true;
                    continue;
                }
                if (inContainers && trimmed.startsWith(McpConstants.Yaml.ITEM_PREFIX + McpConstants.Arguments.NAME + McpConstants.Yaml.COLON_SEPARATOR)) {
                    return trimmed.substring((McpConstants.Yaml.ITEM_PREFIX + McpConstants.Arguments.NAME + McpConstants.Yaml.COLON_SEPARATOR).length()).trim();
                }
                // Exit containers section if we leave the indentation
                if (inContainers && !trimmed.startsWith(McpConstants.Yaml.ITEM_PREFIX) && !trimmed.startsWith(McpConstants.Arguments.NAME + McpConstants.Yaml.COLON_SEPARATOR)
                    && !line.startsWith(" ") && !line.startsWith("\t")) {
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract container name from pod status")
                .field(McpConstants.LogFields.ERROR, e.getMessage())
                .log();
        }
        return null;
    }

    /**
     * Collects Kruize MCP context (cost and performance recommendations).
     *
     * @param builder the context builder to populate
     * @param alert the alert
     * @param containerName the resolved container name
     */
    private void collectKruizeContext(DiagnosticContext.Builder builder, Alert alert, String containerName) {
        // Cost recommendations
        try {
            String sessionId = initializeMcpSession(
                mcpConfig.kruize().endpoint() + McpConstants.Paths.MCP_ENDPOINT_SLASH,
                mcpConfig.kruize().timeoutMs()
            );

            ObjectNode arguments = objectMapper.createObjectNode();
            arguments.put(McpConstants.Arguments.CONTAINER_NAME, containerName);
            if (alert.getNamespace() != null) {
                arguments.put(McpConstants.Arguments.NAMESPACE, alert.getNamespace());
            }

            JsonNode result = callMcpTool(
                mcpConfig.kruize().endpoint() + McpConstants.Paths.MCP_ENDPOINT_SLASH,
                sessionId,
                McpConstants.Tools.KRUIZE_GET_COST_RECOMMENDATIONS,
                arguments,
                mcpConfig.kruize().timeoutMs()
            );

            String costText = extractTextFromContent(result);
            builder.costRecommendations(costText);

            log.info(LogMessages.Mcp.MCP_KRUIZE_COST_RECOMMENDATIONS)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .field(McpConstants.LogFields.CONTAINER_NAME, containerName)
                .log();
        } catch (Exception e) {
            log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                .field(McpConstants.LogFields.TOOL, McpConstants.Tools.KRUIZE_GET_COST_RECOMMENDATIONS)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .field(McpConstants.LogFields.ERROR, e.getMessage())
                .log();
        }

        // Performance recommendations
        try {
            String sessionId = initializeMcpSession(
                mcpConfig.kruize().endpoint() + McpConstants.Paths.MCP_ENDPOINT_SLASH,
                mcpConfig.kruize().timeoutMs()
            );

            ObjectNode arguments = objectMapper.createObjectNode();
            arguments.put(McpConstants.Arguments.CONTAINER_NAME, containerName);
            if (alert.getNamespace() != null) {
                arguments.put(McpConstants.Arguments.NAMESPACE, alert.getNamespace());
            }

            JsonNode result = callMcpTool(
                mcpConfig.kruize().endpoint() + McpConstants.Paths.MCP_ENDPOINT_SLASH,
                sessionId,
                McpConstants.Tools.KRUIZE_GET_PERF_RECOMMENDATIONS,
                arguments,
                mcpConfig.kruize().timeoutMs()
            );

            String perfText = extractTextFromContent(result);
            builder.performanceRecommendations(perfText);

            log.info(LogMessages.Mcp.MCP_KRUIZE_PERF_RECOMMENDATIONS)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .field(McpConstants.LogFields.CONTAINER_NAME, containerName)
                .log();
        } catch (Exception e) {
            log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                .field(McpConstants.LogFields.TOOL, McpConstants.Tools.KRUIZE_GET_PERF_RECOMMENDATIONS)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .field(McpConstants.LogFields.ERROR, e.getMessage())
                .log();
        }
    }

    /**
     * Collects Cryostat MCP context (JFR analysis from 5 tools).
     *
     * @param builder the context builder to populate
     * @param alert the alert
     */
    private void collectCryostatContext(DiagnosticContext.Builder builder, Alert alert) {
        String podName = alert.getPodName();

        // GC analysis
        String gcResult = callCryostatToolWithRetry(
            McpConstants.Tools.CRYOSTAT_GET_GC_ANALYSIS,
            podName,
            alert.getAlertId()
        );
        builder.gcAnalysis(gcResult);
        if (gcResult != null) {
            log.info(LogMessages.Mcp.MCP_CRYOSTAT_GC_ANALYSIS)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .log();
        }

        // Memory analysis
        String memResult = callCryostatToolWithRetry(
            McpConstants.Tools.CRYOSTAT_GET_MEMORY_ANALYSIS,
            podName,
            alert.getAlertId()
        );
        builder.memoryAnalysis(memResult);
        if (memResult != null) {
            log.info(LogMessages.Mcp.MCP_CRYOSTAT_MEMORY_ANALYSIS)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .log();
        }

        // Thread analysis
        String threadResult = callCryostatToolWithRetry(
            McpConstants.Tools.CRYOSTAT_GET_THREAD_ANALYSIS,
            podName,
            alert.getAlertId()
        );
        builder.threadAnalysis(threadResult);
        if (threadResult != null) {
            log.info(LogMessages.Mcp.MCP_CRYOSTAT_THREAD_ANALYSIS)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .log();
        }

        // Exception analysis
        String exceptionResult = callCryostatToolWithRetry(
            McpConstants.Tools.CRYOSTAT_GET_EXCEPTION_ANALYSIS,
            podName,
            alert.getAlertId()
        );
        builder.exceptionAnalysis(exceptionResult);
        if (exceptionResult != null) {
            log.info(LogMessages.Mcp.MCP_CRYOSTAT_EXCEPTION_ANALYSIS)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .log();
        }

        // Container analysis
        String containerResult = callCryostatToolWithRetry(
            McpConstants.Tools.CRYOSTAT_GET_CONTAINER_ANALYSIS,
            podName,
            alert.getAlertId()
        );
        builder.containerAnalysis(containerResult);
        if (containerResult != null) {
            log.info(LogMessages.Mcp.MCP_CRYOSTAT_CONTAINER_ANALYSIS)
                .field(McpConstants.LogFields.ALERT_ID, alert.getAlertId())
                .log();
        }
    }

    /**
     * Calls a Cryostat MCP tool with retry logic for RECORDING_CREATED responses.
     *
     * @param toolName the Cryostat tool name
     * @param podName the pod name argument
     * @param alertId the alert ID (for logging)
     * @return the tool response text, or null on failure
     */
    private String callCryostatToolWithRetry(String toolName, String podName, String alertId) {
        int maxRetries = mcpConfig.cryostat().maxRetries();
        long retryDelay = mcpConfig.cryostat().retryDelayMs();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String sessionId = initializeMcpSession(
                    mcpConfig.cryostat().endpoint() + McpConstants.Paths.MCP_ENDPOINT,
                    mcpConfig.cryostat().timeoutMs()
                );

                ObjectNode arguments = objectMapper.createObjectNode();
                arguments.put(McpConstants.Arguments.POD_NAME, podName);

                JsonNode result = callMcpTool(
                    mcpConfig.cryostat().endpoint() + McpConstants.Paths.MCP_ENDPOINT,
                    sessionId,
                    toolName,
                    arguments,
                    mcpConfig.cryostat().timeoutMs()
                );

                String text = extractTextFromContent(result);

                // Check for RECORDING_CREATED status
                if (text != null && text.contains(McpConstants.Cryostat.RECORDING_CREATED_STATUS)) {
                    if (attempt < maxRetries) {
                        log.info(LogMessages.Mcp.MCP_CRYOSTAT_RECORDING_CREATED)
                            .field(McpConstants.LogFields.TOOL, toolName)
                            .field("attempt", attempt + 1)
                            .field("retryDelayMs", retryDelay)
                            .log();
                        Thread.sleep(retryDelay);
                        continue;
                    } else {
                        log.warn(LogMessages.Mcp.MCP_CRYOSTAT_MAX_RETRIES)
                            .field(McpConstants.LogFields.TOOL, toolName)
                            .field(McpConstants.LogFields.ALERT_ID, alertId)
                            .log();
                        return null;
                    }
                }

                return text;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                    .field(McpConstants.LogFields.TOOL, toolName)
                    .field(McpConstants.LogFields.ALERT_ID, alertId)
                    .field("attempt", attempt + 1)
                    .field(McpConstants.LogFields.ERROR, e.getMessage())
                    .log();
                return null;
            }
        }
        return null;
    }
}
