package com.causa.infrastructure.persistence.repositories;

import com.causa.core.ports.AuthConfigurationRepository;
import com.causa.infrastructure.persistence.entity.AuthConfigurationEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Auth Configuration Repository Implementation
 *
 * <p>Panache-based implementation of {@link AuthConfigurationRepository}.
 * Uses the active record pattern for CRUD operations on {@link AuthConfigurationEntity}.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class AuthConfigurationRepositoryImpl implements AuthConfigurationRepository {

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<AuthConfigurationEntity> findById(String id) {
        return AuthConfigurationEntity.findByIdOptional(id);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<AuthConfigurationEntity> findByName(String name) {
        return AuthConfigurationEntity.<AuthConfigurationEntity>find("name", name)
            .firstResultOptional();
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<AuthConfigurationEntity> findAll() {
        return AuthConfigurationEntity.listAll();
    }

    @Override
    @Transactional
    public void save(AuthConfigurationEntity entity) {
        entity.persist();
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        AuthConfigurationEntity.deleteById(id);
    }
}
