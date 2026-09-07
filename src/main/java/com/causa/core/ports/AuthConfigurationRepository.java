package com.causa.core.ports;

import com.causa.infrastructure.persistence.entity.AuthConfigurationEntity;

import java.util.List;
import java.util.Optional;

/**
 * Auth Configuration Repository Port
 *
 * <p>Hexagonal architecture outbound port for authentication configuration persistence.
 * Defines the contract for reading and managing auth configurations.
 *
 * @since 0.0.1
 */
public interface AuthConfigurationRepository {

    /**
     * Finds an auth configuration by ID.
     *
     * @param id the auth configuration ID
     * @return the auth configuration entity, or empty if not found
     */
    Optional<AuthConfigurationEntity> findById(String id);

    /**
     * Finds an auth configuration by name.
     *
     * @param name the auth configuration name
     * @return the auth configuration entity, or empty if not found
     */
    Optional<AuthConfigurationEntity> findByName(String name);

    /**
     * Finds all auth configurations.
     *
     * @return list of all auth configuration entities
     */
    List<AuthConfigurationEntity> findAll();

    /**
     * Persists a new auth configuration or updates an existing one.
     *
     * @param entity the auth configuration entity to save
     */
    void save(AuthConfigurationEntity entity);

    /**
     * Deletes an auth configuration by ID.
     *
     * @param id the auth configuration ID
     */
    void deleteById(String id);
}
