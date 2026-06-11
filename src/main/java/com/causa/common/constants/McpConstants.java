package com.causa.common.constants;

/**
 * MCP (Model Context Protocol) Constants
 *
 * <p>Centralized constants for MCP server integration.
 *
 * @since 0.0.1
 */
public final class McpConstants {

    private McpConstants() {
        // Prevent instantiation
    }

    /**
     * MCP Protocol version
     */
    public static final String PROTOCOL_VERSION = "2025-03-26";

    /**
     * Client information
     */
    public static final String CLIENT_NAME = "causa-backend";
    public static final String CLIENT_VERSION = "0.0.1";

    /**
     * MCP JSON-RPC constants
     */
    public static final class JsonRpc {
        private JsonRpc() {}

        public static final String VERSION = "2.0";
        public static final String METHOD_INITIALIZE = "initialize";
        public static final String METHOD_TOOLS_CALL = "tools/call";
        public static final String METHOD_NOTIFICATIONS_INITIALIZED = "notifications/initialized";

        public static final String PARAM_PROTOCOL_VERSION = "protocolVersion";
        public static final String PARAM_CLIENT_INFO = "clientInfo";
        public static final String PARAM_CAPABILITIES = "capabilities";
        public static final String PARAM_NAME = "name";
        public static final String PARAM_ARGUMENTS = "arguments";

        public static final String FIELD_JSONRPC = "jsonrpc";
        public static final String FIELD_ID = "id";
        public static final String FIELD_METHOD = "method";
        public static final String FIELD_PARAMS = "params";
        public static final String FIELD_RESULT = "result";
        public static final String FIELD_ERROR = "error";
        public static final String FIELD_CONTENT = "content";
        public static final String FIELD_TEXT = "text";
    }

    /**
     * HTTP Headers
     */
    public static final class Headers {
        private Headers() {}

        public static final String CONTENT_TYPE = "Content-Type";
        public static final String ACCEPT = "Accept";
        public static final String MCP_SESSION_ID = "Mcp-Session-Id";

        public static final String CONTENT_TYPE_JSON = "application/json";
        public static final String ACCEPT_VALUE = "application/json, text/event-stream";
    }

    /**
     * MCP Tool names
     */
    public static final class Tools {
        private Tools() {}

        // Kubernetes MCP tools
        public static final String PODS_GET = "pods_get";
        public static final String PODS_LOG = "pods_log";
        public static final String EVENTS_LIST = "events_list";
    }

    /**
     * MCP Tool arguments
     */
    public static final class Arguments {
        private Arguments() {}

        public static final String NAME = "name";
        public static final String NAMESPACE = "namespace";
        public static final String CONTAINER = "container";
        public static final String TAIL_LINES = "tailLines";
        public static final String FIELD_SELECTOR = "fieldSelector";
    }

    /**
     * Output section headers
     */
    public static final class OutputHeaders {
        private OutputHeaders() {}

        public static final String POD_STATUS = "\n=== POD STATUS ===";
        public static final String KUBERNETES_EVENTS = "\n=== KUBERNETES EVENTS (for pod: %s) ===";
        public static final String POD_LOGS = "\n=== POD LOGS (last 5 lines) ===";
    }

    /**
     * Error messages
     */
    public static final class Errors {
        private Errors() {}

        public static final String UNABLE_TO_GET_POD_STATUS = "Unable to retrieve pod status: %s";
        public static final String UNABLE_TO_GET_EVENTS = "Unable to retrieve events: %s";
        public static final String UNABLE_TO_GET_LOGS = "Unable to retrieve logs: %s";

        public static final String MCP_INITIALIZE_FAILED = "MCP initialize failed with status: %d, body: %s";
        public static final String MCP_TOOL_CALL_FAILED = "MCP tool call failed with status: %d, body: %s";
        public static final String MCP_TOOL_ERROR = "MCP tool error: %s";
    }

    /**
     * Default values
     */
    public static final class Defaults {
        private Defaults() {}

        public static final int DEFAULT_TAIL_LINES = 25;
        public static final String NO_EVENTS_FOUND = "No events found";
        public static final String NO_LOGS_AVAILABLE = "No logs available";
        public static final String UNKNOWN_STATUS = "Unknown";
    }

    /**
     * SSE (Server-Sent Events) parsing
     */
    public static final class SSE {
        private SSE() {}

        public static final String DATA_PREFIX = "data: ";
        public static final String LINE_SEPARATOR = "\n";
    }
}
