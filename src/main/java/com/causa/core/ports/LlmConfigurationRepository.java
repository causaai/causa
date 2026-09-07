package com.causa.core.ports;

import com.causa.infrastructure.persistence.entity.LlmConfigurationEntity;

import java.util.List;
import java.util.Optional;

/**
 * LLM Configuration Repository Port
 *
 * <p>Hexagonal architecture outbound port for LLM provider configuration persistence.
 * Defines the contract for reading and managing LLM provider configurations.
 *
 * @since 0.0.1
 */
public interface LlmConfigurationRepository {

    /**
     * Finds an LLM configuration by ID.
     *
     * @param id the LLM configuration ID
     * @return the LLM configuration entity, or empty if not found
     */
    Optional<LlmConfigurationEntity> findById(String id);

    /**
     * Finds an LLM configuration by name.
     *
     * @param name the LLM configuration name
     * @return the LLM configuration entity, or empty if not found
     */
    Optional<LlmConfigurationEntity> findByName(String name);

    /**
     * Finds the active LLM configuration.
     *
     * @return the active LLM configuration entity, or empty if none active
     */
    Optional<LlmConfigurationEntity> findActive();

    /**
     * Finds all LLM configurations.
     *
     * @return list of all LLM configuration entities
     */
    List<LlmConfigurationEntity> findAll();

    /**
     * Persists a new LLM configuration or updates an existing one.
     *
     * @param entity the LLM configuration entity to save
     */
    void save(LlmConfigurationEntity entity);

    /**
     * Deletes an LLM configuration by ID.
     *
     * @param id the LLM configuration ID
     */
    void deleteById(String id);

    /**
     * Deactivates all LLM configurations.
     * Used before activating a new provider to ensure only one is active.
     */
    void deactivateAll();
}
