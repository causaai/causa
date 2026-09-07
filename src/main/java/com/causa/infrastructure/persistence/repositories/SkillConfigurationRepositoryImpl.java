package com.causa.infrastructure.persistence.repositories;

import com.causa.core.ports.SkillConfigurationRepository;
import com.causa.infrastructure.persistence.entity.SkillConfigurationEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Skill Configuration Repository Implementation
 *
 * <p>Panache-based implementation of {@link SkillConfigurationRepository}.
 * Uses the active record pattern for CRUD operations on {@link SkillConfigurationEntity}.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class SkillConfigurationRepositoryImpl implements SkillConfigurationRepository {

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<SkillConfigurationEntity> findById(String id) {
        return SkillConfigurationEntity.findByIdOptional(id);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<SkillConfigurationEntity> findByMcpConfigurationId(String mcpConfigurationId) {
        return SkillConfigurationEntity.<SkillConfigurationEntity>find("mcpConfigurationId", mcpConfigurationId)
            .list();
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<SkillConfigurationEntity> findActiveByMcpConfigurationId(String mcpConfigurationId) {
        return SkillConfigurationEntity.<SkillConfigurationEntity>find(
            "mcpConfigurationId = ?1 and active = ?2",
            mcpConfigurationId,
            true
        ).list();
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<SkillConfigurationEntity> findAll() {
        return SkillConfigurationEntity.listAll();
    }

    @Override
    @Transactional
    public void save(SkillConfigurationEntity entity) {
        entity.persist();
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        SkillConfigurationEntity.deleteById(id);
    }
}
