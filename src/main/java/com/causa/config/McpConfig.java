package com.causa.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * MCP Configuration
 *
 * <p>Type-safe configuration for MCP (Model Context Protocol) server integration.
 * Maps to {@code causa.mcp.*} in application.yml, which reads from {@code CAUSA_MCP_*} environment variables.
 *
 * @since 0.0.1
 */
@ConfigMapping(prefix = "causa.mcp")
public interface McpConfig {

    /**
     * Kubernetes MCP server configuration.
     *
     * @return the Kubernetes MCP config
     */
    @WithName("kubernetes")
    KubernetesConfig kubernetes();

    /**
     * Kruize MCP server configuration.
     *
     * @return the Kruize MCP config
     */
    @WithName("kruize")
    KruizeConfig kruize();

    /**
     * Cryostat MCP server configuration.
     *
     * @return the Cryostat MCP config
     */
    @WithName("cryostat")
    CryostatConfig cryostat();

    /**
     * Filesystem MCP server configuration (VM platform).
     *
     * @return the Filesystem MCP config
     */
    @WithName("filesystem")
    FilesystemConfig filesystem();

    /**
     * JMX MCP server configuration (VM platform).
     *
     * @return the JMX MCP config
     */
    @WithName("jmx")
    JmxConfig jmx();

    /**
     * Kubernetes MCP Configuration
     */
    interface KubernetesConfig {
        /**
         * Kubernetes MCP server endpoint URL.
         *
         * @return the endpoint URL
         */
        @WithName("endpoint")
        String endpoint();

        /**
         * Health check path for the Kubernetes MCP server.
         *
         * @return the health check path
         */
        @WithName("health-path")
        @WithDefault("/healthz")
        String healthPath();

        /**
         * HTTP request timeout in milliseconds.
         *
         * @return the timeout in ms
         */
        @WithName("timeout-ms")
        @WithDefault("5000")
        int timeoutMs();
    }

    /**
     * Kruize MCP Configuration
     */
    interface KruizeConfig {
        /**
         * Kruize MCP server endpoint URL.
         *
         * @return the endpoint URL
         */
        @WithName("endpoint")
        String endpoint();

        /**
         * Health check path for the Kruize MCP server.
         *
         * @return the health check path
         */
        @WithName("health-path")
        @WithDefault("/q/health/ready")
        String healthPath();

        /**
         * HTTP request timeout in milliseconds.
         *
         * @return the timeout in ms
         */
        @WithName("timeout-ms")
        @WithDefault("10000")
        int timeoutMs();
    }

    /**
     * Cryostat MCP Configuration
     */
    interface CryostatConfig {
        /**
         * Cryostat MCP server endpoint URL (port 8000).
         *
         * @return the MCP endpoint URL
         */
        @WithName("endpoint")
        String endpoint();

        /**
         * Cryostat health check endpoint URL (port 8080).
         * This is separate from the MCP endpoint because health runs on a different port.
         *
         * @return the health endpoint URL
         */
        @WithName("health-endpoint")
        String healthEndpoint();

        /**
         * Health check path for the Cryostat server.
         *
         * @return the health check path
         */
        @WithName("health-path")
        @WithDefault("/healthz")
        String healthPath();

        /**
         * HTTP request timeout in milliseconds.
         *
         * @return the timeout in ms
         */
        @WithName("timeout-ms")
        @WithDefault("15000")
        int timeoutMs();

        /**
         * Delay in milliseconds before retrying when Cryostat returns RECORDING_CREATED status.
         *
         * @return the retry delay in ms
         */
        @WithName("retry-delay-ms")
        @WithDefault("5000")
        int retryDelayMs();

        /**
         * Maximum number of retry attempts for Cryostat tool calls.
         *
         * @return the max retries
         */
        @WithName("max-retries")
        @WithDefault("3")
        int maxRetries();
    }

    /**
     * Filesystem MCP Configuration (VM platform)
     */
    interface FilesystemConfig {
        @WithName("endpoint")
        String endpoint();

        @WithName("health-path")
        @WithDefault("/healthz")
        String healthPath();

        @WithName("timeout-ms")
        @WithDefault("10000")
        int timeoutMs();
    }

    /**
     * JMX MCP Configuration (VM platform)
     */
    interface JmxConfig {
        @WithName("endpoint")
        String endpoint();

        @WithName("health-path")
        @WithDefault("/healthz")
        String healthPath();

        @WithName("timeout-ms")
        @WithDefault("10000")
        int timeoutMs();
    }
}
