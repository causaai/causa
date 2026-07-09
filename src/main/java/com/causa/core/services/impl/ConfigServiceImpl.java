package com.causa.core.services.impl;

import com.causa.common.constants.ConfigConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.common.utils.EncryptionUtils;
import com.causa.config.AppConfig;
import com.causa.core.ports.ConfigurationRepository;
import com.causa.core.ports.ConfigurationRepository.ConfigEntry;
import com.causa.core.services.ConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Configuration Service Implementation
 *
 * <p>{@link AppConfig} is the single owner of the in-memory cache.
 * This service handles persistence (DB reads/writes) and the API surface
 * ({@code get}, {@code getAll}, {@code update}, {@code refresh}).
 *
 * <p>Startup sequence (called by {@code ConfigStartup}):
 * <ol>
 *   <li>Read every row from the {@code configurations} DB table into {@link AppConfig}.</li>
 *   <li>For each known key absent from {@link AppConfig}, resolve from ENV / application.yml
 *       and upsert to DB so it survives the next boot.</li>
 *   <li>Keys not found anywhere are left absent — callers get {@code Optional.empty()}.</li>
 * </ol>
 *
 * <p><strong>Null-value handling:</strong> {@link AppConfig} uses a {@code ConcurrentHashMap}
 * which forbids null values. Keys with no value are simply absent; {@link #get} returns
 * {@code Optional.empty()}. API responses use {@code null} for the value field.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class ConfigServiceImpl implements ConfigService {

    private static final CausaLogger log = CausaLogger.getLogger(ConfigServiceImpl.class);

    private final AppConfig appConfig;
    private final ConfigurationRepository repository;

    /**
     * MicroProfile Config — reads from all sources in priority order:
     * OS ENV vars → System properties → application.yml defaults.
     * Used during bootstrap to resolve defaults for keys absent from the DB.
     */
    private final Config mpConfig;

    @Inject
    public ConfigServiceImpl(AppConfig appConfig, ConfigurationRepository repository, Config mpConfig) {
        this.appConfig  = appConfig;
        this.repository = repository;
        this.mpConfig   = mpConfig;
    }

    // -------------------------------------------------------------------------
    // ConfigService implementation
    // -------------------------------------------------------------------------

    @Override
    public Optional<String> get(String key) {
        validateKey(key);
        // AppConfig always holds the plaintext value — masking is only for API responses.
        return appConfig.get(key);
    }

    @Override
    public List<ConfigEntry> getByCategory(String category) {
        return ConfigConstants.ALL_KNOWN_KEYS.stream()
                .filter(k -> category != null && category.equals(ConfigConstants.categoryOf(k)))
                .map(this::toEntry)
                .toList();
    }

    @Override
    public List<ConfigEntry> getAll() {
        return ConfigConstants.ALL_KNOWN_KEYS.stream()
                .map(this::toEntry)
                .toList();
    }

    @Override
    public void update(String key, String value) {
        validateKey(key);
        boolean sensitive = ConfigConstants.isSensitive(key);
        String storedValue = sensitive ? EncryptionUtils.encrypt(value) : value;
        // 1. Persist to DB
        repository.upsert(key, storedValue, sensitive);
        // 2. Update AppConfig cache — it is the live source of truth
        appConfig.put(key, value);  // put() handles null/blank → remove internally
        log.info(LogMessages.Config.UPDATED)
                .field(ConfigConstants.LogFields.CONFIG_KEY, key)
                .field(ConfigConstants.LogFields.CONFIG_CATEGORY, ConfigConstants.categoryOf(key))
                .log();
    }

    @Override
    public void loadFromDbAndEnv() {
        log.info(LogMessages.Config.STARTUP_BEGIN).log();
        CacheRefreshResult result = doLoad();
        log.info(LogMessages.Config.STARTUP_DONE)
                .field(ConfigConstants.LogFields.KEYS_LOADED,   result.keysFromDb())
                .field(ConfigConstants.LogFields.KEYS_FROM_ENV, result.keysFromEnv())
                .field(ConfigConstants.LogFields.KEYS_NULL,     result.keysNull())
                .log();
    }

    @Override
    public CacheRefreshResult refreshCache() {
        log.info(LogMessages.Config.CACHE_REFRESH_START).log();
        // Clear AppConfig cache so stale values do not survive
        appConfig.clear();
        CacheRefreshResult result = doLoad();
        log.info(LogMessages.Config.CACHE_REFRESH_DONE)
                .field(ConfigConstants.LogFields.KEYS_LOADED,   result.keysFromDb())
                .field(ConfigConstants.LogFields.KEYS_FROM_ENV, result.keysFromEnv())
                .field(ConfigConstants.LogFields.KEYS_NULL,     result.keysNull())
                .log();
        return result;
    }

    // -------------------------------------------------------------------------
    // Shared load logic
    // -------------------------------------------------------------------------

    /**
     * Loads all config values into {@link AppConfig} from DB, then yml/ENV for absent keys.
     * Does NOT clear the cache first — callers are responsible for that if needed.
     *
     * @return counts of keys resolved from each source
     */
    private CacheRefreshResult doLoad() {
        int fromDb  = 0;
        int fromEnv = 0;
        int nulled  = 0;

        // --- Step 1: load every non-null row from the DB into AppConfig ---
        // Encrypted values are decrypted here; AppConfig always holds plaintext.
        List<ConfigEntry> dbEntries = repository.findAll();
        for (ConfigEntry entry : dbEntries) {
            if (entry.value() != null && !entry.value().isBlank()) {
                String plainValue = entry.encrypted()
                        ? safeDecrypt(entry.key(), entry.value())
                        : entry.value();
                if (plainValue != null) {
                    appConfig.put(entry.key(), plainValue);
                    fromDb++;
                }
            }
        }

        // --- Step 2: for every known key absent from AppConfig, try application.yml / ENV ---
        List<String> missingKeys = ConfigConstants.ALL_KNOWN_KEYS.stream()
                .filter(k -> appConfig.get(k).isEmpty())
                .collect(Collectors.toList());

        List<String> keysStoredAsNull = new ArrayList<>();

        for (String key : missingKeys) {
            String resolvedValue = resolveFromConfig(key);

            if (resolvedValue != null && !resolvedValue.isBlank()) {
                // Persist to DB — encrypt sensitive keys
                boolean sensitive = ConfigConstants.isSensitive(key);
                String storedValue = sensitive ? EncryptionUtils.encrypt(resolvedValue) : resolvedValue;
                repository.upsert(key, storedValue, sensitive);
                appConfig.put(key, resolvedValue);  // AppConfig is the live source of truth
                fromEnv++;
                log.info(LogMessages.Config.LOADED_FROM_ENV)
                        .field(ConfigConstants.LogFields.CONFIG_KEY, key)
                        .field(ConfigConstants.LogFields.CONFIG_SOURCE, ConfigConstants.LogFields.SOURCE_ENV)
                        .log();
            } else {
                // No value in DB, yml, or ENV — leave absent from AppConfig
                nulled++;
                keysStoredAsNull.add(key);
            }
        }

        if (!keysStoredAsNull.isEmpty()) {
            log.info(LogMessages.Config.STORED_AS_NULL)
                    .field(ConfigConstants.LogFields.KEYS_NULL, keysStoredAsNull)
                    .log();
        }

        return new CacheRefreshResult(fromDb, fromEnv, nulled);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link ConfigEntry} for the given key suitable for API responses.
     * Sensitive keys have their value replaced with {@link ConfigConstants#MASKED_VALUE}.
     */
    private ConfigEntry toEntry(String key) {
        boolean sensitive = ConfigConstants.isSensitive(key);
        String  value     = appConfig.get(key).orElse(null);
        String  displayed = (sensitive && value != null) ? ConfigConstants.MASKED_VALUE : value;
        return new ConfigEntry(key, displayed, sensitive);
    }

    /**
     * Decrypts a value read from the DB, logging a warning on failure — the key
     * is simply left absent from AppConfig.
     */
    private String safeDecrypt(String key, String encryptedValue) {
        try {
            return EncryptionUtils.decrypt(encryptedValue);
        } catch (Exception e) {
            log.warn(LogMessages.Config.LOAD_FAILED)
                    .field(ConfigConstants.LogFields.CONFIG_KEY, key)
                    .exception(e)
                    .log();
            return null;
        }
    }

    private static void validateKey(String key) {
        if (!ConfigConstants.isValidKey(key)) {
            throw new IllegalArgumentException(
                    String.format(LogMessages.Config.UNKNOWN_KEY + ": %s", key));
        }
    }

    /**
     * Resolves the value for a key using the MicroProfile Config API.
     *
     * <p>Lookup order (handled transparently by the MP Config runtime):
     * <ol>
     *   <li>OS environment variable matching the MP property name</li>
     *   <li>Java system property</li>
     *   <li>{@code application.yml} — including {@code ${ENV_VAR:default}} defaults</li>
     * </ol>
     */
    private String resolveFromConfig(String key) {
        String mpProperty = ConfigConstants.mpPropertyFor(key);
        if (mpProperty == null) {
            // No MP property mapping defined — fall back to raw ENV lookup
            String envValue = System.getenv(key);
            return (envValue != null && !envValue.isBlank()) ? envValue : null;
        }
        return mpConfig.getOptionalValue(mpProperty, String.class)
                .filter(v -> !v.isBlank())
                .orElse(null);
    }
}
