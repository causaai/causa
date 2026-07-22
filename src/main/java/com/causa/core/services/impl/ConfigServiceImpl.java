package com.causa.core.services.impl;

import com.causa.common.constants.ConfigConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.utils.EncryptionUtils;
import com.causa.config.AppConfig;
import com.causa.core.ports.ConfigurationRepository;
import com.causa.core.services.ConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration Service Implementation
 *
 * <p>Manages the lifecycle of runtime configuration:
 * <ul>
 *   <li>Loads config from DB at startup, seeds missing keys from ENV/application.yml</li>
 *   <li>Caches values in AppConfig for fast access</li>
 *   <li>Updates DB and cache on writes</li>
 *   <li>Refreshes cache when LISTEN/NOTIFY events are received</li>
 * </ul>
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class ConfigServiceImpl implements ConfigService {

    private static final CausaLogger log = CausaLogger.getLogger(ConfigServiceImpl.class);

    private final AppConfig appConfig;
    private final ConfigurationRepository repository;
    private final Config mpConfig;

    @Inject
    public ConfigServiceImpl(AppConfig appConfig, ConfigurationRepository repository, Config mpConfig) {
        this.appConfig = appConfig;
        this.repository = repository;
        this.mpConfig = mpConfig;
    }

    @Override
    public Optional<String> get(String key) {
        return appConfig.get(key);
    }

    @Override
    public List<ConfigurationRepository.ConfigEntry> getByCategory(String category) {
        return ConfigConstants.ALL_KNOWN_KEYS.stream()
            .filter(key -> category.equals(ConfigConstants.categoryOf(key)))
            .map(key -> {
                String value = appConfig.get(key).orElse("");
                boolean encrypted = ConfigConstants.isSensitive(key);
                return new ConfigurationRepository.ConfigEntry(key, value, encrypted);
            })
            .toList();
    }

    @Override
    public List<ConfigurationRepository.ConfigEntry> getAll() {
        return ConfigConstants.ALL_KNOWN_KEYS.stream()
            .map(key -> {
                String value = appConfig.get(key).orElse("");
                boolean encrypted = ConfigConstants.isSensitive(key);
                return new ConfigurationRepository.ConfigEntry(key, value, encrypted);
            })
            .toList();
    }

    @Override
    public void update(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Config key must not be null or blank");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Config value must not be null or blank for key: " + key);
        }

        boolean isSensitive = ConfigConstants.isSensitive(key);
        String valueToStore = isSensitive ? EncryptionUtils.encrypt(value) : value;

        // Persist to DB (triggers PG LISTEN/NOTIFY which invalidates all pods' caches)
        repository.upsert(key, valueToStore, isSensitive);

        // Update local cache immediately (before LISTEN/NOTIFY round-trip)
        appConfig.put(key, value);

        log.info("Config updated")
            .field(ConfigConstants.LogFields.CONFIG_KEY, key)
            .field("encrypted", isSensitive)
            .log();
    }

    @Override
    public void loadFromDbAndEnv() {
        log.info("Loading configuration from DB and environment").log();
        CacheRefreshResult result = doLoad();
        log.info("Configuration loaded")
            .field(ConfigConstants.LogFields.KEYS_FROM_DB, result.keysFromDb())
            .field(ConfigConstants.LogFields.KEYS_FROM_ENV, result.keysFromEnv())
            .field(ConfigConstants.LogFields.KEYS_NULL, result.keysNull())
            .log();
    }

    @Override
    public CacheRefreshResult refreshCache() {
        log.info("Refreshing config cache from DB").log();
        CacheRefreshResult result = doLoad();
        log.info("Config cache refreshed")
            .field(ConfigConstants.LogFields.KEYS_FROM_DB, result.keysFromDb())
            .log();
        return result;
    }

    /**
     * Core loading logic: reads DB, seeds missing keys from MP Config, updates cache.
     *
     * <p>Decryption is attempted per-entry. A failure on one entry logs a warning and
     * skips that key — it does not abort the load or leave the cache empty.
     *
     * <p>The live cache is only replaced after all entries are processed, so the cache
     * is never left in a partial or empty state mid-refresh.
     *
     * @return summary of the load operation
     */
    private CacheRefreshResult doLoad() {
        int fromDb = 0;
        int fromEnv = 0;
        int nullValues = 0;

        // Decrypt all DB entries into a staging map first — one bad entry skips, never aborts
        List<ConfigurationRepository.ConfigEntry> dbEntries = repository.findAll();
        Map<String, String> staging = new HashMap<>();
        for (ConfigurationRepository.ConfigEntry entry : dbEntries) {
            try {
                String value = entry.encrypted() ? EncryptionUtils.decrypt(entry.value()) : entry.value();
                staging.put(entry.key(), value);
                fromDb++;
            } catch (Exception e) {
                log.warn("Failed to decrypt config entry, skipping")
                    .field(ConfigConstants.LogFields.CONFIG_KEY, entry.key())
                    .field("error", e.getMessage())
                    .log();
            }
        }

        // Atomically replace live cache with staged values
        appConfig.clear();
        staging.forEach(appConfig::put);

        // Seed missing keys from MicroProfile Config (ENV → system props → application.yml)
        for (ConfigConstants.KeyDef keyDef : ConfigConstants.allKeys()) {
            if (appConfig.get(keyDef.name()).isEmpty()) {
                // Key not in DB, try to resolve from environment
                Optional<String> envValue = resolveFromMpConfig(keyDef);
                if (envValue.isPresent() && !envValue.get().isBlank()) {
                    String value = envValue.get();
                    boolean isSensitive = keyDef.sensitive();
                    String valueToStore = isSensitive ? EncryptionUtils.encrypt(value) : value;

                    // Persist to DB so subsequent boots don't need the ENV var
                    repository.upsert(keyDef.name(), valueToStore, isSensitive);

                    // Update cache
                    appConfig.put(keyDef.name(), value);
                    fromEnv++;

                    log.info("Config key seeded from environment")
                        .field(ConfigConstants.LogFields.CONFIG_KEY, keyDef.name())
                        .field(ConfigConstants.LogFields.CATEGORY, keyDef.category())
                        .log();
                } else {
                    nullValues++;
                }
            }
        }

        return new CacheRefreshResult(fromDb, fromEnv, nullValues);
    }

    /**
     * Resolves a config value from MicroProfile Config.
     * Uses the MP Config property path from ConfigConstants.
     *
     * @param keyDef the key definition
     * @return the value from MP Config, or empty if not found
     */
    private Optional<String> resolveFromMpConfig(ConfigConstants.KeyDef keyDef) {
        String mpProperty = keyDef.mpConfigPath();
        if (mpProperty == null || mpProperty.isBlank()) {
            return Optional.empty();
        }
        return mpConfig.getOptionalValue(mpProperty, String.class);
    }
}
