package com.causa.core.ports;

import com.causa.infrastructure.persistence.entity.SkillConfigurationEntity;

import java.util.List;
import java.util.Optional;

/**
 * Skill Configuration Repository Port
 *
 * <p>Hexagonal architecture outbound port for skill configuration persistence.
 * Defines the contract for reading and managing skill configurations.
 *
 * @since 0.0.1
 */
public interface SkillConfigurationRepository {

    /**
     * Finds a skill configuration by ID.
     *
     * @param id the skill configuration ID
     * @return the skill configuration entity, or empty if not found
     */
    Optional<SkillConfigurationEntity> findById(String id);

    /**
     * Finds all skill configurations for a given MCP server.
     *
     * @param mcpConfigurationId the MCP configuration ID
     * @return list of skill configuration entities for the MCP server
     */
    List<SkillConfigurationEntity> findByMcpConfigurationId(String mcpConfigurationId);

    /**
     * Finds all active skill configurations for a given MCP server.
     *
     * @param mcpConfigurationId the MCP configuration ID
     * @return list of active skill configuration entities for the MCP server
     */
    List<SkillConfigurationEntity> findActiveByMcpConfigurationId(String mcpConfigurationId);

    /**
     * Finds all skill configurations.
     *
     * @return list of all skill configuration entities
     */
    List<SkillConfigurationEntity> findAll();

    /**
     * Persists a new skill configuration or updates an existing one.
     *
     * @param entity the skill configuration entity to save
     */
    void save(SkillConfigurationEntity entity);

    /**
     * Deletes a skill configuration by ID.
     *
     * @param id the skill configuration ID
     */
    void deleteById(String id);
}
