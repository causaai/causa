package com.causa.core.ports;

import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.infrastructure.persistence.entity.ExternalConfigEntity;

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
    List<ExternalConfigEntity> findByCategory(PlatformCategory category);

    /** Returns the config for a specific platform within a category, or empty if not found. */
    Optional<ExternalConfigEntity> findByCategoryAndPlatform(PlatformCategory category, String platform);

    /** Persists a new or updated config entity. */
    void save(ExternalConfigEntity entity);

    /**
     * Deletes the config row for the given category and platform.
     *
     * @return {@code true} if a row was deleted, {@code false} if none existed
     */
    boolean deleteByCategoryAndPlatform(PlatformCategory category, String platform);
}
