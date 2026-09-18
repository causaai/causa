package com.causa.core.ports;

import com.causa.core.domain.LlmConfig;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for {@code llm_configs} persistence.
 *
 * <p>The single-active-provider invariant (at most one {@code isActive = true} row)
 * is maintained by calling {@link #deactivateAll()} and {@link #save(LlmConfig)}
 * within the same transaction in the service layer.
 *
 * @since 0.0.3
 */
public interface LlmConfigRepository {

    /** Returns all LLM provider configs. */
    List<LlmConfig> findAll();

    /** Returns the config for the given provider name, or empty if not found. */
    Optional<LlmConfig> findByProvider(String provider);

    /** Returns the currently active provider config, or empty if none is active. */
    Optional<LlmConfig> findActive();

    /** Persists a new or updated LLM config. */
    LlmConfig save(LlmConfig llmConfig);

    /** Sets {@code isActive = false} on every row. Called before activating a new provider. */
    void deactivateAll();

    /**
     * Deletes the config for the given provider.
     *
     * @return {@code true} if a row was deleted, {@code false} if none existed
     */
    boolean deleteByProvider(String provider);
}
