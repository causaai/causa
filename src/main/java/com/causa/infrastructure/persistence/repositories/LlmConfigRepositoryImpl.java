package com.causa.infrastructure.persistence.repositories;

import com.causa.common.utils.IdUtils;
import com.causa.core.domain.LlmConfig;
import com.causa.core.ports.LlmConfigRepository;
import com.causa.infrastructure.persistence.entity.LlmConfigEntity;
import com.causa.infrastructure.persistence.mappers.LlmConfigEntityMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * LlmConfig Repository Implementation
 *
 * <p>Panache-based implementation of {@link LlmConfigRepository}.
 * One row per LLM provider in the {@code llm_configs} table.
 *
 * <p>The single-active-provider invariant (at most one row with {@code isActive = true})
 * is maintained by the service layer calling {@link #deactivateAll()} before {@link #save(LlmConfig)}
 * within the same transaction. The repository itself does not enforce this constraint.
 *
 * <p>ID generation is delegated to {@link IdUtils#generateLlmConfigId()}; a new ID is only
 * assigned on insert. On update (existing provider row), the existing row's ID is preserved.
 *
 * @since 0.0.3
 */
@ApplicationScoped
public class LlmConfigRepositoryImpl implements LlmConfigRepository {

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<LlmConfig> findAll() {
        return LlmConfigEntity.<LlmConfigEntity>listAll()
            .stream()
            .map(LlmConfigEntityMapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<LlmConfig> findByProvider(String provider) {
        return LlmConfigEntity
            .<LlmConfigEntity>find("provider", provider)
            .firstResultOptional()
            .map(LlmConfigEntityMapper::toDomain);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<LlmConfig> findActive() {
        return LlmConfigEntity
            .<LlmConfigEntity>find("isActive", true)
            .firstResultOptional()
            .map(LlmConfigEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public LlmConfig save(LlmConfig llmConfig) {
        Optional<LlmConfigEntity> existing = LlmConfigEntity
            .<LlmConfigEntity>find("provider", llmConfig.getProvider())
            .firstResultOptional();

        if (existing.isPresent()) {
            // Update in-place — Hibernate flushes the managed entity at transaction commit
            LlmConfigEntity entity = existing.get();
            entity.setUrl(llmConfig.getUrl());
            entity.setModels(llmConfig.getModels() != null ? llmConfig.getModels().toArray(new String[0]) : new String[0]);
            entity.setTemperature(llmConfig.getTemperature());
            entity.setMaxTokens(llmConfig.getMaxTokens());
            entity.setTimeoutMs(llmConfig.getTimeoutMs());
            entity.setIsActive(llmConfig.isActive());
            entity.setAuthConfig(LlmConfigEntityMapper.serialiseAuthConfig(llmConfig.getAuthConfig()));
            entity.setAdditionalConfig(
                llmConfig.getAdditionalConfig() != null && !llmConfig.getAdditionalConfig().isEmpty()
                    ? LlmConfigEntityMapper.serialiseAdditionalConfig(llmConfig.getAdditionalConfig())
                    : null);
            return LlmConfigEntityMapper.toDomain(entity);
        } else {
            // Insert new row — generate a fresh ID since the caller does not supply one
            LlmConfig toInsert = LlmConfig.builder()
                .id(IdUtils.generateLlmConfigId())
                .provider(llmConfig.getProvider())
                .url(llmConfig.getUrl())
                .models(llmConfig.getModels())
                .temperature(llmConfig.getTemperature())
                .maxTokens(llmConfig.getMaxTokens())
                .timeoutMs(llmConfig.getTimeoutMs())
                .isActive(llmConfig.isActive())
                .authConfig(llmConfig.getAuthConfig())
                .additionalConfig(llmConfig.getAdditionalConfig())
                .build();
            LlmConfigEntity entity = LlmConfigEntityMapper.toEntity(toInsert);
            entity.persist();
            return LlmConfigEntityMapper.toDomain(entity);
        }
    }

    @Override
    @Transactional
    public void deactivateAll() {
        LlmConfigEntity.update("isActive = false");
    }

    @Override
    @Transactional
    public boolean deleteByProvider(String provider) {
        long deleted = LlmConfigEntity.delete("provider", provider);
        return deleted > 0;
    }
}
