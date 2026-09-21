package com.causa.core.ports;

import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.core.domain.ExternalConfig;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for {@code external_configs} persistence.
 *
 * <p>Used for both Observability and Integration categories, discriminated by
 * the {@code category} parameter.
 *
 * @since 0.0.3
 */
public interface ExternalConfigRepository {

    /** Returns all configs for the given category. */
    List<ExternalConfig> findByCategory(PlatformCategory category);

    /** Returns the config for a specific platform and name, or empty if not found. */
    Optional<ExternalConfig> findByPlatformAndName(String platform, String name);

    /** Persists a new or updated config. */
    ExternalConfig save(ExternalConfig externalConfig);

    /**
     * Deletes the config for the given platform and name.
     *
     * @return {@code true} if a row was deleted, {@code false} if none existed
     */
    boolean deleteByPlatformAndName(String platform, String name);
}
