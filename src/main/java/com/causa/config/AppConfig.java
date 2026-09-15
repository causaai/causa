package com.causa.config;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application Configuration Cache
 *
 * <p>In-memory cache for runtime-configurable application settings, backed by a
 * {@link ConcurrentHashMap}. Values are loaded from the database at startup and
 * synchronized across all pods via PostgreSQL LISTEN/NOTIFY.
 *
 * <p>This cache is the fast-access layer for typed configuration snapshots.
 * Database writes trigger invalidation events that reload this cache on all pods.
 *
 * <p>Thread-safe for concurrent reads and writes.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class AppConfig {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Retrieves a configuration value by key.
     *
     * @param key the configuration key
     * @return the value, or empty if not present
     */
    public Optional<String> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    /**
     * Stores or updates a configuration value.
     * If the value is null or blank, the key is removed from the cache.
     *
     * @param key   the configuration key
     * @param value the configuration value (null/blank = remove)
     */
    public void put(String key, String value) {
        if (value == null || value.isBlank()) {
            cache.remove(key);
        } else {
            cache.put(key, value);
        }
    }

    /**
     * Clears all cached configuration values.
     * Called when the cache is invalidated via LISTEN/NOTIFY.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Returns an unmodifiable view of the entire configuration map.
     *
     * @return immutable map of all cached config entries
     */
    public Map<String, String> asMap() {
        return Map.copyOf(cache);
    }

    /**
     * Creates a typed snapshot of LLM configuration.
     *
     * @return immutable LLM config snapshot
     */
    public LlmConfigSnapshot getLlmConfig() {
        return new LlmConfigSnapshot(cache);
    }

    /**
     * Creates a typed snapshot of Alert configuration.
     *
     * @return immutable alert config snapshot
     */
    public AlertConfigSnapshot getAlertConfig() {
        return new AlertConfigSnapshot(cache);
    }

    /**
     * Creates a typed snapshot of Cluster configuration.
     *
     * @return immutable cluster config snapshot
     */
    public ClusterConfigSnapshot getClusterConfig() {
        return new ClusterConfigSnapshot(cache);
    }
}
