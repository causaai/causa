package com.causa.infrastructure.persistence.repositories;

import com.causa.core.ports.McpConfigurationRepository;
import com.causa.infrastructure.persistence.entity.McpConfigurationEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * MCP Configuration Repository Implementation
 *
 * <p>Panache-based implementation of {@link McpConfigurationRepository}.
 * Uses the active record pattern for CRUD operations on {@link McpConfigurationEntity}.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class McpConfigurationRepositoryImpl implements McpConfigurationRepository {

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<McpConfigurationEntity> findById(String id) {
        return McpConfigurationEntity.findByIdOptional(id);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<McpConfigurationEntity> findByName(String name) {
        return McpConfigurationEntity.<McpConfigurationEntity>find("name", name)
            .firstResultOptional();
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<McpConfigurationEntity> findAllActive() {
        return McpConfigurationEntity.<McpConfigurationEntity>find("active", true)
            .list();
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<McpConfigurationEntity> findAll() {
        return McpConfigurationEntity.listAll();
    }

    @Override
    @Transactional
    public void save(McpConfigurationEntity entity) {
        entity.persist();
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        McpConfigurationEntity.deleteById(id);
    }
}
