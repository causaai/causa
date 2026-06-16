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
}
