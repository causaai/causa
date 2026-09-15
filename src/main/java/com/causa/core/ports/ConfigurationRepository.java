package com.causa.core.ports;

import java.util.List;
import java.util.Optional;

/**
 * Configuration Repository Port
 *
 * <p>Hexagonal architecture outbound port for configuration persistence.
 * Defines the contract for reading and updating configuration key-value pairs.
 *
 * @since 0.0.1
 */
public interface ConfigurationRepository {

    /**
     * Finds a configuration value by key.
     *
     * @param key the configuration key
     * @return the value, or empty if not found
     */
    Optional<String> findByKey(String key);

    /**
     * Finds all configuration entries.
     *
     * @return list of all config entries (key, value, encrypted flag)
     */
    List<ConfigEntry> findAll();

    /**
     * Upserts a configuration value (plain text).
     * Inserts if the key doesn't exist, updates if it does.
     *
     * @param key   the configuration key
     * @param value the configuration value
     */
    void upsert(String key, String value);

    /**
     * Upserts a configuration value with explicit encryption flag.
     *
     * @param key       the configuration key
     * @param value     the configuration value (encrypted if encrypted=true)
     * @param encrypted whether the value is already encrypted
     */
    void upsert(String key, String value, boolean encrypted);

    /**
     * Lightweight projection of a configuration entry.
     *
     * @param key       the configuration key
     * @param value     the configuration value
     * @param encrypted whether the value is encrypted in the database
     */
    record ConfigEntry(String key, String value, boolean encrypted) {}
}
