package com.causa.core.ports;

import com.causa.infrastructure.persistence.entity.McpConfigurationEntity;

import java.util.List;
import java.util.Optional;

/**
 * MCP Configuration Repository Port
 *
 * <p>Hexagonal architecture outbound port for MCP server configuration persistence.
 * Defines the contract for reading and managing MCP server configurations.
 *
 * @since 0.0.1
 */
public interface McpConfigurationRepository {

    /**
     * Finds an MCP configuration by ID.
     *
     * @param id the MCP configuration ID
     * @return the MCP configuration entity, or empty if not found
     */
    Optional<McpConfigurationEntity> findById(String id);

    /**
     * Finds an MCP configuration by name.
     *
     * @param name the MCP configuration name
     * @return the MCP configuration entity, or empty if not found
     */
    Optional<McpConfigurationEntity> findByName(String name);

    /**
     * Finds all active MCP configurations.
     *
     * @return list of active MCP configuration entities
     */
    List<McpConfigurationEntity> findAllActive();

    /**
     * Finds all MCP configurations.
     *
     * @return list of all MCP configuration entities
     */
    List<McpConfigurationEntity> findAll();

    /**
     * Persists a new MCP configuration or updates an existing one.
     *
     * @param entity the MCP configuration entity to save
     */
    void save(McpConfigurationEntity entity);

    /**
     * Deletes an MCP configuration by ID.
     *
     * @param id the MCP configuration ID
     */
    void deleteById(String id);
}
