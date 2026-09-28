package com.causa.infrastructure.persistence.repositories;

import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.common.utils.IdUtils;
import com.causa.core.domain.ExternalConfig;
import com.causa.core.ports.ExternalConfigRepository;
import com.causa.infrastructure.persistence.entity.ExternalConfigEntity;
import com.causa.infrastructure.persistence.mappers.ExternalConfigEntityMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * ExternalConfig Repository Implementation
 *
 * <p>Panache-based implementation of {@link ExternalConfigRepository}.
 * Handles both Observability and Integration configs stored in the {@code external_configs} table,
 * discriminated by the {@code category} column.
 *
 * <p>ID generation is delegated to {@link IdUtils#generateExternalConfigId()}; a new ID is only
 * assigned on insert. On update (existing platform+name row), the existing row's ID is preserved.
 *
 * @since 0.0.3
 */
@ApplicationScoped
public class ExternalConfigRepositoryImpl implements ExternalConfigRepository {

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ExternalConfig> findByCategory(PlatformCategory category) {
        return ExternalConfigEntity
            .<ExternalConfigEntity>find("category", category)
            .stream()
            .map(ExternalConfigEntityMapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<ExternalConfig> findByPlatformAndName(String platform, String name) {
        return ExternalConfigEntity
            .<ExternalConfigEntity>find("platform = ?1 and name = ?2", platform, name)
            .firstResultOptional()
            .map(ExternalConfigEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public ExternalConfig save(ExternalConfig externalConfig) {
        Optional<ExternalConfigEntity> existing = ExternalConfigEntity
            .<ExternalConfigEntity>find("platform = ?1 and name = ?2",
                externalConfig.getPlatform(), externalConfig.getName())
            .firstResultOptional();

        if (existing.isPresent()) {
            // Update in-place — Hibernate flushes the managed entity at transaction commit
            ExternalConfigEntity entity = existing.get();
            entity.setCategory(externalConfig.getCategory());
            entity.setUrl(externalConfig.getUrl());
            entity.setIsActive(externalConfig.isActive());
            entity.setAuthConfig(ExternalConfigEntityMapper.serialiseAuthConfig(externalConfig.getAuthConfig()));
            entity.setAdditionalConfig(
                externalConfig.getAdditionalConfig() != null && !externalConfig.getAdditionalConfig().isEmpty()
                    ? ExternalConfigEntityMapper.serialiseAdditionalConfig(externalConfig.getAdditionalConfig())
                    : null);
            return ExternalConfigEntityMapper.toDomain(entity);
        } else {
            // Insert new row — generate a fresh ID since the caller does not supply one
            ExternalConfig toInsert = ExternalConfig.builder()
                .id(IdUtils.generateExternalConfigId())
                .category(externalConfig.getCategory())
                .platform(externalConfig.getPlatform())
                .name(externalConfig.getName())
                .url(externalConfig.getUrl())
                .isActive(externalConfig.isActive())
                .authConfig(externalConfig.getAuthConfig())
                .additionalConfig(externalConfig.getAdditionalConfig())
                .build();
            ExternalConfigEntity entity = ExternalConfigEntityMapper.toEntity(toInsert);
            entity.persist();
            return ExternalConfigEntityMapper.toDomain(entity);
        }
    }

    @Override
    @Transactional
    public boolean deleteByPlatformAndName(String platform, String name) {
        long deleted = ExternalConfigEntity.delete("platform = ?1 and name = ?2", platform, name);
        return deleted > 0;
    }
}
