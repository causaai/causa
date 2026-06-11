package com.causa.infrastructure.persistence.repositories;

import com.causa.common.exceptions.DiagnosticException;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Diagnostic;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.infrastructure.persistence.entity.DiagnosticEntity;
import com.causa.infrastructure.persistence.mappers.DiagnosticEntityMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

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
        try {
            DiagnosticEntity entity = DiagnosticEntityMapper.toEntity(diagnostic);
            entity.persist();
            return diagnostic;
        } catch (Exception e) {
            throw new DiagnosticException(LogMessages.Diagnostic.DIAGNOSTIC_PERSIST_FAILED + ": " + diagnostic.getDiagnosticId(), "PersistenceError", e);
        }
    }

    @Override
    @Transactional
    public Diagnostic update(Diagnostic diagnostic) {
        try {
            DiagnosticEntity entity = DiagnosticEntityMapper.toEntity(diagnostic);
            DiagnosticEntity.getEntityManager().merge(entity);
            return diagnostic;
        } catch (Exception e) {
            throw new DiagnosticException(LogMessages.Diagnostic.DIAGNOSTIC_UPDATE_FAILED + ": " + diagnostic.getDiagnosticId(), "UpdateError", e);
        }
    }
}
