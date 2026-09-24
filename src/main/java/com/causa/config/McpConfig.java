package com.causa.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * MCP Configuration
 *
 * <p>Only the Filesystem server's config remains here — every other server is now
 * driven dynamically by {@code mcp.json} via {@link com.causa.mcp.McpRegistry}.
 * Filesystem stays because {@code LibertyLogsContextCollector} needs its directory
 * discovery logic, which isn't expressible as a static {@code mcp.json} tool entry.
 *
 * @since 0.0.1
 */
@ConfigMapping(prefix = "causa.mcp")
public interface McpConfig {

    /**
     * Filesystem MCP server configuration (VM platform).
     *
     * @return the Filesystem MCP config
     */
    @WithName("filesystem")
    FilesystemConfig filesystem();

    /**
     * Filesystem MCP Configuration (VM platform)
     */
    interface FilesystemConfig {
        /**
         * Filesystem MCP server base URL (e.g. http://localhost:8808).
         *
         * @return the base URL
         */
        @WithName("endpoint")
        String endpoint();

        /**
         * Health check path for the Filesystem MCP server.
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
        @WithDefault("10000")
        int timeoutMs();

        /**
         * Root directory path where Liberty log files are located.
         *
         * @return the Liberty logs root directory path
         */
        @WithName("liberty-logs-dir")
        @WithDefault("/logs")
        String libertyLogsDir();

        /**
         * Time window in minutes before the alert timestamp used to filter log files.
         * Files with timestamps older than {@code alertTimestamp - alertWindowMinutes} are skipped.
         * Default is 5 minutes. Reduce to 2-3 to narrow collection to the immediate incident.
         *
         * @return the alert window in minutes
         */
        @WithName("alert-window-minutes")
        @WithDefault("5")
        int alertWindowMinutes();

    }
}