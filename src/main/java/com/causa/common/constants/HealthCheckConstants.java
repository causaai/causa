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
     * Messages for MCP and LLM components.
     * Note: LLM-specific messages are defined in LLMConstants.Messages
     */
    public static final class Messages {
        private Messages() {}

        // MCP messages
        public static final String MCP_CONNECTED = "Connected successfully";
        public static final String MCP_NOT_AVAILABLE = "MCP server not available";
    }
}

