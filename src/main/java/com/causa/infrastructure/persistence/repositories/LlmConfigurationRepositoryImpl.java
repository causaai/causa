package com.causa.infrastructure.persistence.repositories;

import com.causa.core.ports.LlmConfigurationRepository;
import com.causa.infrastructure.persistence.entity.LlmConfigurationEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * LLM Configuration Repository Implementation
 *
 * <p>Panache-based implementation of {@link LlmConfigurationRepository}.
 * Uses the active record pattern for CRUD operations on {@link LlmConfigurationEntity}.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class LlmConfigurationRepositoryImpl implements LlmConfigurationRepository {

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<LlmConfigurationEntity> findById(String id) {
        return LlmConfigurationEntity.findByIdOptional(id);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<LlmConfigurationEntity> findByName(String name) {
        return LlmConfigurationEntity.<LlmConfigurationEntity>find("name", name)
            .firstResultOptional();
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<LlmConfigurationEntity> findActive() {
        return LlmConfigurationEntity.<LlmConfigurationEntity>find("active", true)
            .firstResultOptional();
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<LlmConfigurationEntity> findAll() {
        return LlmConfigurationEntity.listAll();
    }

    @Override
    @Transactional
    public void save(LlmConfigurationEntity entity) {
        entity.persist();
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        LlmConfigurationEntity.deleteById(id);
    }

    @Override
    @Transactional
    public void deactivateAll() {
        LlmConfigurationEntity.update("active = false where active = true");
    }
}
