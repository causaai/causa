package com.causa.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Alert Configuration Snapshot
 *
 * <p>Immutable typed view of alert-related configuration from the runtime cache.
 * Reads 4 alert config keys from AppConfig and provides typed getters with sensible defaults.
 *
 * @since 0.0.1
 */
public final class AlertConfigSnapshot {

    private final Map<String, String> config;

    public AlertConfigSnapshot(Map<String, String> config) {
        this.config = Map.copyOf(config);
    }

    public String getFilterSeverity() {
        return config.getOrDefault("ALERT_FILTER_SEVERITY", "critical");
    }

    public int getCooldownMinutes() {
        String value = config.get("ALERT_COOLDOWN_MINUTES");
        if (value == null || value.isBlank()) {
            return 15;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 15;
        }
    }

    public List<String> getIgnoreNamespaces() {
        String value = config.get("ALERT_IGNORE_NAMESPACES");
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    public String getCooldownCleanupInterval() {
        return config.getOrDefault("ALERT_COOLDOWN_CLEANUP_INTERVAL", "5m");
    }
}
