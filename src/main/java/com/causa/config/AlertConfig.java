package com.causa.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.List;
import java.util.Optional;

/**
 * Alert Configuration
 *
 * <p>Configuration properties for alert ingestion and processing.
 * <p>Maps to the {@code causa.alerts.*} configuration namespace.
 *
 * @since 0.0.1
 */
@ConfigMapping(prefix = "causa.alerts")
public interface AlertConfig {

    /**
     * Minimum severity level for alerts to trigger diagnostic pipeline.
     *
     * <p>Possible values: critical, warning, info
     * <p>Default: critical
     *
     * @return the minimum severity filter
     */
    @WithName("filter-severity")
    @WithDefault("critical")
    String filterSeverity();

    /**
     * Cooldown period in minutes before processing repeat alerts for the same pod.
     *
     * <p>Default: 15 minutes
     *
     * @return the cooldown period in minutes
     */
    @WithName("cooldown-minutes")
    @WithDefault("15")
    int cooldownMinutes();

    /**
     * Cooldown cache cleanup interval.
     *
     * <p>Determines how often expired cooldown entries are purged from memory.
     * <p>Default: 5m (5 minutes)
     * <p>Valid formats: 5m, 10m, 1h, etc.
     *
     * @return cleanup interval as a duration string
     */
    @WithName("cooldown-cleanup-interval")
    @WithDefault("5m")
    String cooldownCleanupInterval();

    /**
     * Comma-separated list of namespaces to ignore.
     *
     * <p>Alerts from these namespaces will be filtered out.
     * <p>Default: kube-system,istio-system
     *
     * @return optional list of ignored namespaces
     */
    @WithName("ignore-namespaces")
    @WithDefault("kube-system,istio-system")
    Optional<List<String>> ignoreNamespaces();
}
