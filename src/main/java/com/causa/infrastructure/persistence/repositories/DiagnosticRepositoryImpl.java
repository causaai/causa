package com.causa.infrastructure.persistence.repositories;

import com.causa.core.domain.Diagnostic;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.infrastructure.persistence.entity.DiagnosticEntity;
import com.causa.infrastructure.persistence.mappers.DiagnosticEntityMapper;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;

/**
 * Diagnostic Repository Implementation
 *
 * <p>Panache-based implementation of the DiagnosticRepository port.
 * <p>Uses Panache Repository pattern for cleaner, type-safe queries.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DiagnosticRepositoryImpl implements DiagnosticRepository {

    @Override
    @Transactional
    public Diagnostic save(Diagnostic diagnostic) {
        DiagnosticEntity entity = DiagnosticEntityMapper.toEntity(diagnostic);
        entity.persist();
        return diagnostic;
    }

    @Override
    public Optional<Diagnostic> findById(String diagnosticId) {
        return DiagnosticEntity.findByIdOptional(diagnosticId)
            .map(entity -> DiagnosticEntityMapper.toDomain((DiagnosticEntity) entity));
    }

    @Override
    public Optional<Diagnostic> findByAlertId(String alertId) {
        return DiagnosticEntity.<DiagnosticEntity>find(
                DiagnosticEntity.Fields.ALERT_ID,
                Sort.descending(DiagnosticEntity.Fields.GENERATED_AT),
                alertId)
            .firstResultOptional()
            .map(DiagnosticEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public Diagnostic update(Diagnostic diagnostic) {
        DiagnosticEntity entity = DiagnosticEntityMapper.toEntity(diagnostic);
        DiagnosticEntity.getEntityManager().merge(entity);
        return diagnostic;
    }
}
