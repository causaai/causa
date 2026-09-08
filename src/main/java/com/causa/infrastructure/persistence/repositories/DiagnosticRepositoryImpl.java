package com.causa.infrastructure.persistence.repositories;

import com.causa.common.exceptions.DiagnosticException;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.PageRequest;
import com.causa.core.domain.PageResult;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.infrastructure.persistence.entity.DiagnosticEntity;
import com.causa.infrastructure.persistence.mappers.DiagnosticEntityMapper;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Diagnostic Repository Implementation
 *
 * <p>Panache-based implementation of {@link DiagnosticRepository}.
 *
 * <p>The diagnostics table has no JSONB filter columns, so Panache JPQL is safe to use
 * for all queries. Pagination is applied via {@link io.quarkus.panache.common.Page};
 * the count is obtained from the same {@link io.quarkus.hibernate.orm.panache.PanacheQuery}
 * in a single extra round-trip.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DiagnosticRepositoryImpl implements DiagnosticRepository {

    @Override
    @Transactional
    public Diagnostic save(Diagnostic diagnostic) {
        try {
            DiagnosticEntityMapper.toEntity(diagnostic).persist();
            return diagnostic;
        } catch (Exception e) {
            throw new DiagnosticException(
                LogMessages.Diagnostic.DIAGNOSTIC_PERSIST_FAILED + ": " + diagnostic.getDiagnosticId(),
                "PersistenceError", e);
        }
    }

    @Override
    @Transactional
    public Diagnostic update(Diagnostic diagnostic) {
        try {
            DiagnosticEntity.getEntityManager().merge(DiagnosticEntityMapper.toEntity(diagnostic));
            return diagnostic;
        } catch (Exception e) {
            throw new DiagnosticException(
                LogMessages.Diagnostic.DIAGNOSTIC_UPDATE_FAILED + ": " + diagnostic.getDiagnosticId(),
                "UpdateError", e);
        }
    }

    @Override
    public Optional<Diagnostic> findById(String diagnosticId) {
        return DiagnosticEntity.<DiagnosticEntity>findByIdOptional(diagnosticId)
            .map(DiagnosticEntityMapper::toDomain);
    }

    /** Paginated search ordered by {@code created_at} descending. */
    @Override
    public PageResult<Diagnostic> search(PageRequest pageRequest) {
        // Fixed ordering — created_at DESC. Sorting is not user-controllable.
        Sort sort = Sort.by("createdAt").descending();

        // No filter fields today — always fetch all
        var query = DiagnosticEntity.<DiagnosticEntity>findAll(sort)
            .page(Page.of(pageRequest.panachePage(), pageRequest.size()));

        List<Diagnostic> items = query.list()
            .stream()
            .map(DiagnosticEntityMapper::toDomain)
            .toList();

        // Panache reuses the same predicate for COUNT — one extra round-trip
        long total = DiagnosticEntity.count();

        return PageResult.of(items, total, pageRequest);
    }
}
