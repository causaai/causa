package com.causa.config;

import com.causa.common.logging.CausaLogger;
import com.causa.common.utils.EncryptionUtils;
import com.causa.core.domain.AuthConfig;
import com.causa.core.domain.LlmConfig;
import com.causa.core.ports.LlmConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory cache for {@code llm_configs} table rows.
 *
 * <p>Holds the full list of LLM provider configs and a direct reference to the
 * currently active one. Populated at startup by {@link com.causa.config.ConfigStartup}
 * and refreshed whenever {@link com.causa.infrastructure.persistence.ConfigCacheListener}
 * receives a {@code config_cache_channel} notification (fired by
 * {@code trg_llm_config_notify} after any write to {@code llm_configs}).
 *
 * <p>Reads ({@link #getAll()}, {@link #getActive()}) never touch the database.
 * Writes go through the service layer directly to the repository; the subsequent
 * NOTIFY round-trip refreshes this cache on all pods.
 *
 * <p>Thread-safe: the list is a {@link CopyOnWriteArrayList} and the active reference
 * is an {@link AtomicReference}, so concurrent reads during a refresh are safe.
 *
 * @since 0.0.4
 */
@ApplicationScoped
public class LlmConfigCache {

    private static final CausaLogger log = CausaLogger.getLogger(LlmConfigCache.class);

    private final LlmConfigRepository repository;

    /** Snapshot of all provider rows — replaced atomically on refresh. */
    private final AtomicReference<List<LlmConfig>> allConfigs =
            new AtomicReference<>(List.of());

    /** Currently active provider — null when none is active. */
    private final AtomicReference<LlmConfig> activeConfig =
            new AtomicReference<>(null);

    @Inject
    public LlmConfigCache(LlmConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns all LLM provider configs from cache (no DB call).
     *
     * @return immutable snapshot of all configs
     */
    public List<LlmConfig> getAll() {
        return allConfigs.get();
    }

    /**
     * Returns the currently active LLM provider config from cache (no DB call).
     *
     * @return the active config, or empty if none is active
     */
    public Optional<LlmConfig> getActive() {
        return Optional.ofNullable(activeConfig.get());
    }

    /**
     * Reloads the cache from the database.
     *
     * <p>Called at startup and on every {@code config_cache_channel} NOTIFY event.
     * {@code @ActivateRequestContext} ensures a CDI request context is active so
     * Panache/Hibernate can use the EntityManager even when called from the bare
     * virtual thread that runs the PG LISTEN/NOTIFY loop (which has no request context).
     *
     * <p>A failure here is non-fatal — the stale cache is preserved until the next
     * successful refresh.
     */
    @ActivateRequestContext
    public void refresh() {
        try {
            List<LlmConfig> fresh = List.copyOf(
                repository.findAll().stream()
                    .map(LlmConfigCache::decryptSensitiveFields)
                    .toList()
            );
            LlmConfig active = fresh.stream()
                    .filter(LlmConfig::isActive)
                    .findFirst()
                    .orElse(null);

            allConfigs.set(fresh);
            activeConfig.set(active);

            log.info("LlmConfigCache refreshed")
                    .field("total", fresh.size())
                    .field("activeProvider", active != null ? active.getProvider().name() : "none")
                    .log();
        } catch (Exception e) {
            log.warn("LlmConfigCache refresh failed — stale cache preserved")
                    .field("error", e.getMessage())
                    .log();
        }
    }

    /**
     * Decrypts sensitive {@link AuthConfig} fields so the cache holds plaintext values
     * ready for use by {@link com.causa.llm.ChatModelFactory} and other internal consumers.
     *
     * <p>The repository stores AES-256-GCM ciphertext; only the cache-internal copy is
     * decrypted — the encrypted values remain in the database unchanged.
     */
    private static LlmConfig decryptSensitiveFields(LlmConfig config) {
        AuthConfig auth = config.getAuthConfig();
        if (auth == null) return config;

        AuthConfig decrypted = new AuthConfig(
            auth.authType(),
            tryDecrypt(auth.apiKey()),
            tryDecrypt(auth.appKey()),
            tryDecrypt(auth.token()),
            tryDecrypt(auth.credentialsJson()),
            auth.headers() != null
                ? auth.headers().entrySet().stream().collect(
                    java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey,
                        e -> { try { return EncryptionUtils.decrypt(e.getValue()); } catch (Exception ex) { return e.getValue(); } }
                    ))
                : auth.headers(),
            auth.username(),
            tryDecrypt(auth.password())
        );

        return LlmConfig.builder()
            .id(config.getId())
            .provider(config.getProvider())
            .url(config.getUrl())
            .models(config.getModels())
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .timeoutMs(config.getTimeoutMs())
            .isActive(config.isActive())
            .authConfig(decrypted)
            .additionalConfig(config.getAdditionalConfig())
            .createdAt(config.getCreatedAt())
            .updatedAt(config.getUpdatedAt())
            .build();
    }

    /** Decrypts a single field value; returns the original value unchanged if it is null or decryption fails. */
    private static String tryDecrypt(String value) {
        if (value == null || value.isBlank()) return value;
        try {
            return EncryptionUtils.decrypt(value);
        } catch (Exception e) {
            // Value was not encrypted (e.g. already plaintext) — return as-is
            return value;
        }
    }
}
