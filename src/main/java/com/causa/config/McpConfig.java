package com.causa.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import java.util.Optional;

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
     * Quarkus MCP server configuration (cluster platform).
     *
     * @return the Quarkus MCP config
     */
    @WithName("quarkus")
    QuarkusConfig quarkus();

    /**
     * Async Profiler MCP server configuration (cluster platform).
     *
     * @return the Async Profiler MCP config
     */
    @WithName("async-profiler")
    AsyncProfilerConfig asyncProfiler();

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

    /**
     * Quarkus MCP Configuration (cluster platform)
     */
    interface QuarkusConfig {
        /**
         * Quarkus MCP server endpoint URL.
         *
         * <p>SmallRye Config maps an empty string value to {@code Optional.empty()},
         * so {@code @WithDefault("")} here means the property is treated as absent
         * (i.e. {@code Optional.empty()}) when {@code CAUSA_MCP_QUARKUS_ENDPOINT}
         * is not set or is explicitly set to {@code ""}, without triggering
         * SRCFG00040 at boot time.
         *
         * @return the endpoint URL, or {@code Optional.empty()} when not configured
         */
        @WithName("endpoint")
        @WithDefault("")
        Optional<String> endpoint();

        /**
         * Health check path for the Quarkus MCP server.
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
         * When set, passed as {@code baseUrl} to {@code fetch_raw_metrics_from_endpoint}.
         *
         * <p>Same {@code @WithDefault("")} / {@code Optional<String>} convention as
         * {@link #endpoint()}: an unset or blank property resolves to
         * {@code Optional.empty()}.
         *
         * @return the app base URL, or {@code Optional.empty()} if not configured
         */
        @WithName("metrics-base-url")
        @WithDefault("")
        Optional<String> metricsBaseUrl();

    }

    /**
     * Async Profiler MCP Configuration (cluster platform)
     */
    interface AsyncProfilerConfig {
        /**
         * Async Profiler MCP server endpoint URL.
         *
         * <p>Same {@code @WithDefault("")} / {@code Optional<String>} opt-in convention
         * as {@link QuarkusConfig#endpoint()}: blank or unset resolves to
         * {@code Optional.empty()}, disabling the integration without triggering SRCFG00040.
         *
         * @return the endpoint URL, or {@code Optional.empty()} when not configured
         */
        @WithName("endpoint")
        @WithDefault("")
        Optional<String> endpoint();

        /**
         * Health check path for the Async Profiler MCP server.
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
