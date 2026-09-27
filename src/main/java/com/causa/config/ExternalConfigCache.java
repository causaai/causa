package com.causa.config;

import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.ExternalConfig;
import com.causa.core.ports.ExternalConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory cache for {@code external_configs} table rows.
 *
 * <p>Holds a list of configs per {@link PlatformCategory} (OBSERVABILITY and INTEGRATION).
 * Populated at startup by {@link com.causa.config.ConfigStartup} and refreshed whenever
 * {@link com.causa.infrastructure.persistence.ConfigCacheListener} receives a
 * {@code config_cache_channel} notification (fired by {@code trg_external_config_notify}
 * after any write to {@code external_configs}).
 *
 * <p>Reads ({@link #getByCategory(PlatformCategory)}) never touch the database.
 * Writes go through the service layer directly to the repository; the subsequent
 * NOTIFY round-trip refreshes this cache on all pods.
 *
 * <p>Thread-safe: the category map snapshot is replaced atomically on each refresh.
 *
 * @since 0.0.4
 */
@ApplicationScoped
public class ExternalConfigCache {

    private static final CausaLogger log = CausaLogger.getLogger(ExternalConfigCache.class);

    private final ExternalConfigRepository repository;

    /**
     * Atomic snapshot — maps each PlatformCategory to an immutable list of its configs.
     * Replaced wholesale on each refresh so readers always see a consistent map.
     */
    private final AtomicReference<Map<PlatformCategory, List<ExternalConfig>>> snapshot =
            new AtomicReference<>(Map.of());

    @Inject
    public ExternalConfigCache(ExternalConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns all external configs for the given category from cache (no DB call).
     *
     * @param category OBSERVABILITY or INTEGRATION
     * @return immutable list of configs for that category; empty list if none cached
     */
    public List<ExternalConfig> getByCategory(PlatformCategory category) {
        return snapshot.get().getOrDefault(category, List.of());
    }

    /**
     * Reloads the cache from the database for all categories.
     *
     * <p>Called at startup and on every {@code config_cache_channel} NOTIFY event.
     * Annotated with {@code ActivateRequestContext} so Panache/Hibernate can use the
     * EntityManager from the bare pg-config-listener virtual thread which has no
     * CDI request context of its own.
     *
     * <p>A failure here is non-fatal — the stale cache is preserved until the next
     * successful refresh.
     */
    @ActivateRequestContext
    public void refresh() {
        try {
            Map<PlatformCategory, List<ExternalConfig>> fresh = new EnumMap<>(PlatformCategory.class);
            for (PlatformCategory category : PlatformCategory.values()) {
                fresh.put(category, List.copyOf(repository.findByCategory(category)));
            }

            snapshot.set(Map.copyOf(fresh));

            log.info("ExternalConfigCache refreshed")
                    .field("observability", fresh.getOrDefault(PlatformCategory.OBSERVABILITY, List.of()).size())
                    .field("integration", fresh.getOrDefault(PlatformCategory.INTEGRATION, List.of()).size())
                    .log();
        } catch (Exception e) {
            log.warn("ExternalConfigCache refresh failed — stale cache preserved")
                    .field("error", e.getMessage())
                    .log();
        }
    }
}
