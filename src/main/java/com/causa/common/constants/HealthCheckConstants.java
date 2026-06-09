package com.causa.common.constants;

/**
 * Health Check Constants
 *
 * <p>Contains constants specific to health check components and monitoring.
 *
 * @since 0.0.1
 */
public final class HealthCheckConstants {

    private HealthCheckConstants() {
        // Prevent instantiation
    }

    /**
     * Component names used in health check responses.
     */
    public static final class ComponentNames {
        private ComponentNames() {}

        public static final String DATABASE = "database";
        public static final String LLM_PROVIDER = "llm_provider";
        public static final String MCP_KUBERNETES = "mcp_kubernetes";
        public static final String MCP_CRYOSTAT = "mcp_cryostat";
        public static final String MCP_KRUIZE = "mcp_kruize";
    }

    /**
     * Messages for future MCP and LLM components.
     */
    public static final class Messages {
        private Messages() {}

        // LLM provider messages (future use)
        public static final String LLM_CONNECTED = "LangChain4J connected to gpt-4-turbo";
        public static final String LLM_NOT_AVAILABLE = "LLM provider not available";

        // MCP messages (future use)
        public static final String MCP_CONNECTED = "Connected successfully";
        public static final String MCP_NOT_AVAILABLE = "MCP server not available";
    }
}

