package com.causa.mcp;

import com.causa.common.constants.McpConstants;
import com.causa.common.logging.CausaLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * MCP Client
 *
 * <p>Reusable client for communicating with MCP servers via JSON-RPC 2.0.
 * Handles session initialization, tool calls, and SSE response parsing.
 *
 * <p>Used by both McpContextCollector and LangChain4j skills.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class McpClient {

    private static final CausaLogger log = CausaLogger.getLogger(McpClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public McpClient() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    /**
     * Initializes MCP session and returns session ID.
     *
     * @param endpoint MCP server endpoint (e.g., "http://server:8080/mcp")
     * @param timeoutMs Timeout in milliseconds
     * @return Session ID for subsequent tool calls
     * @throws Exception if initialization fails
     */
    public String initializeSession(String endpoint, int timeoutMs) throws Exception {
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
            .timeout(Duration.ofMillis(timeoutMs))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(String.format(McpConstants.Errors.MCP_INITIALIZE_FAILED,
                response.statusCode(), response.body()));
        }

        String responseBody = response.body();
        String jsonData = parseSSEResponse(responseBody);
        JsonNode responseNode = objectMapper.readTree(jsonData);

        String sessionId = response.headers().firstValue(McpConstants.Headers.MCP_SESSION_ID)
            .orElse(UUID.randomUUID().toString());

        sendInitializedNotification(endpoint, sessionId, timeoutMs);

        return sessionId;
    }

    /**
     * Calls an MCP tool via JSON-RPC 2.0.
     *
     * @param endpoint MCP server endpoint
     * @param sessionId Session ID from initializeSession
     * @param toolName Name of the tool to call
     * @param arguments Tool arguments as JSON object
     * @param timeoutMs Timeout in milliseconds
     * @return Tool result as JsonNode
     * @throws Exception if tool call fails
     */
    public JsonNode callTool(String endpoint, String sessionId, String toolName,
                            ObjectNode arguments, int timeoutMs) throws Exception {
        ObjectNode toolRequest = objectMapper.createObjectNode();
        toolRequest.put(McpConstants.JsonRpc.FIELD_JSONRPC, McpConstants.JsonRpc.VERSION);
        toolRequest.put(McpConstants.JsonRpc.FIELD_ID, 2);
        toolRequest.put(McpConstants.JsonRpc.FIELD_METHOD, McpConstants.JsonRpc.METHOD_TOOLS_CALL);

        ObjectNode params = objectMapper.createObjectNode();
        params.put(McpConstants.JsonRpc.PARAM_NAME, toolName);
        params.set(McpConstants.JsonRpc.PARAM_ARGUMENTS, arguments);
        toolRequest.set(McpConstants.JsonRpc.FIELD_PARAMS, params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header(McpConstants.Headers.CONTENT_TYPE, McpConstants.Headers.CONTENT_TYPE_JSON)
            .header(McpConstants.Headers.ACCEPT, McpConstants.Headers.ACCEPT_VALUE)
            .header(McpConstants.Headers.MCP_SESSION_ID, sessionId)
            .POST(HttpRequest.BodyPublishers.ofString(toolRequest.toString()))
            .timeout(Duration.ofMillis(timeoutMs))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(String.format(McpConstants.Errors.MCP_TOOL_CALL_FAILED,
                response.statusCode(), response.body()));
        }

        String responseBody = response.body();
        String jsonData = parseSSEResponse(responseBody);
        JsonNode responseNode = objectMapper.readTree(jsonData);

        if (responseNode.has(McpConstants.JsonRpc.FIELD_ERROR)) {
            JsonNode error = responseNode.get(McpConstants.JsonRpc.FIELD_ERROR);
            throw new RuntimeException(String.format(McpConstants.Errors.MCP_TOOL_ERROR, error.toString()));
        }

        return responseNode.get(McpConstants.JsonRpc.FIELD_RESULT);
    }

    /**
     * Extracts text content from MCP response structure.
     *
     * @param result MCP tool result
     * @return Text content or null if not found
     */
    public String extractTextFromContent(JsonNode result) {
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

    /**
     * Creates a new ObjectMapper instance for JSON operations.
     * Provided for backward compatibility with existing code.
     *
     * @return ObjectMapper instance
     */
    public ObjectMapper createObjectMapper() {
        return new ObjectMapper();
    }

    // ==================== Private Helper Methods ====================

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
    }

    /**
     * Parses SSE (Server-Sent Events) response format.
     * SSE format: "event: message\ndata: {json}\n\n"
     */
    private String parseSSEResponse(String sseResponse) {
        String[] lines = sseResponse.split(McpConstants.SSE.LINE_SEPARATOR);
        for (String line : lines) {
            if (line.startsWith(McpConstants.SSE.DATA_PREFIX)) {
                return line.substring(McpConstants.SSE.DATA_PREFIX.length()).trim();
            }
        }
        return sseResponse;
    }
}
