package com.causa.infrastructure.persistence.repositories;

import com.causa.common.utils.IdUtils;
import com.causa.core.ports.ConfigurationRepository;
import com.causa.infrastructure.persistence.entity.ConfigurationEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Configuration Repository Implementation
 *
 * <p>Panache-based implementation of {@link ConfigurationRepository}.
 * Uses the active record pattern for simple CRUD operations on {@link ConfigurationEntity}.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class ConfigurationRepositoryImpl implements ConfigurationRepository {

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<String> findByKey(String key) {
        return ConfigurationEntity.<ConfigurationEntity>find("configKey", key)
            .firstResultOptional()
            .map(ConfigurationEntity::getConfigValue);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ConfigEntry> findAll() {
        return ConfigurationEntity.<ConfigurationEntity>listAll()
            .stream()
            .map(entity -> new ConfigEntry(
                entity.getConfigKey(),
                entity.getConfigValue(),
                entity.isEncrypted()
            ))
            .toList();
    }

    @Override
    @Transactional
    public void upsert(String key, String value) {
        upsert(key, value, false);
    }

    @Override
    @Transactional
    public void upsert(String key, String value, boolean encrypted) {
        Optional<ConfigurationEntity> existing = ConfigurationEntity
            .<ConfigurationEntity>find("configKey", key)
            .firstResultOptional();

        if (existing.isPresent()) {
            // Managed entity — mutations are flushed automatically at transaction commit
            ConfigurationEntity entity = existing.get();
            entity.setConfigValue(value);
            entity.setEncrypted(encrypted);
        } else {
            // Insert new entry
            ConfigurationEntity entity = new ConfigurationEntity();
            entity.setId(IdUtils.generateConfigurationId());
            entity.setConfigKey(key);
            entity.setConfigValue(value);
            entity.setEncrypted(encrypted);
            entity.persist();
        }
    }
}
