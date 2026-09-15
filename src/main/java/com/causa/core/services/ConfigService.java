package com.causa.core.services;

import com.causa.core.ports.ConfigurationRepository;

import java.util.List;
import java.util.Optional;

/**
 * Configuration Service
 *
 * <p>Core service for managing runtime-configurable application settings.
 * Handles loading from database + environment, caching, and updates.
 *
 * @since 0.0.1
 */
public interface ConfigService {

    /**
     * Retrieves a configuration value by key from the cache.
     *
     * @param key the configuration key
     * @return the value, or empty if not found
     */
    Optional<String> get(String key);

    /**
     * Retrieves all configuration entries for a given category.
     *
     * @param category the category (llm, alerts, cluster)
     * @return list of config entries in that category
     */
    List<ConfigurationRepository.ConfigEntry> getByCategory(String category);

    /**
     * Retrieves all configuration entries.
     *
     * @return list of all config entries
     */
    List<ConfigurationRepository.ConfigEntry> getAll();

    /**
     * Updates a configuration value.
     * Encrypts sensitive values, persists to database, and updates the cache.
     *
     * @param key   the configuration key
     * @param value the new value
     */
    void update(String key, String value);

    /**
     * Loads configuration from database and environment at startup.
     * Seeds missing known keys from MicroProfile Config (ENV → system props → application.yml).
     */
    void loadFromDbAndEnv();

    /**
     * Refreshes the cache by reloading from the database.
     * Called when a LISTEN/NOTIFY event is received.
     *
     * @return summary of the cache refresh operation
     */
    CacheRefreshResult refreshCache();

    /**
     * Cache refresh result summary.
     *
     * @param keysFromDb   number of keys loaded from database
     * @param keysFromEnv  number of keys seeded from environment
     * @param keysNull     number of keys with null values (not cached)
     */
    record CacheRefreshResult(int keysFromDb, int keysFromEnv, int keysNull) {}
}
