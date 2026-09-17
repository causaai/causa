package com.causa.core.ports;

import com.causa.infrastructure.persistence.entity.LlmConfigEntity;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for {@code llm_configs} persistence.
 *
 * <p>The single-active-provider invariant (at most one {@code is_active = true} row)
 * is maintained by calling {@link #deactivateAll()} and {@link #save(LlmConfigEntity)}
 * within the same transaction in the service layer.
 *
 * @since 0.0.3
 */
public interface LlmConfigRepository {

    /** Returns all LLM provider configs. */
    List<LlmConfigEntity> findAll();

    /** Returns the config for the given provider name, or empty if not found. */
    Optional<LlmConfigEntity> findByProvider(String provider);

    /** Returns the currently active provider config, or empty if none is active. */
    Optional<LlmConfigEntity> findActive();

    /** Persists a new or updated LLM config entity. */
    void save(LlmConfigEntity entity);

    /** Sets {@code is_active = false} on every row. Called before activating a new provider. */
    void deactivateAll();

    /**
     * Deletes the config row for the given provider.
     *
     * @return {@code true} if a row was deleted, {@code false} if none existed
     */
    boolean deleteByProvider(String provider);
}
