package com.causa.infrastructure.persistence.repositories;

import com.causa.common.exceptions.DiagnosticException;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.PageRequest;
import com.causa.core.domain.PageResult;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.infrastructure.persistence.entity.DiagnosticEntity;
import com.causa.infrastructure.persistence.mappers.DiagnosticEntityMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
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

    @Inject
    EntityManager em;

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

    /** Paginated search ordered by {@code created_at} descending, with optional container/namespace filters. */
    @Override
    public PageResult<Diagnostic> search(Diagnostic.Filter filter, PageRequest pageRequest) {
        boolean hasContainer = !isBlank(filter.container());
        boolean hasNamespace = !isBlank(filter.namespace());

        List<String> clauses = new ArrayList<>();
        List<Object> params  = new ArrayList<>();

        if (hasContainer) {
            clauses.add("a.workload_info->>'container_name' = ?" + (params.size() + 1));
            params.add(filter.container());
        }
        if (hasNamespace) {
            clauses.add("a.workload_info->>'namespace' = ?" + (params.size() + 1));
            params.add(filter.namespace());
        }

        String where  = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        int    offset = Math.multiplyExact(pageRequest.panachePage(), pageRequest.size());

        String dataSql = "SELECT d.* FROM diagnostics d"
            + " JOIN alerts a ON d.alert_id = a.id"
            + where
            + " ORDER BY d.created_at DESC"
            + " LIMIT ?"  + (params.size() + 1)
            + " OFFSET ?" + (params.size() + 2);

        Query dataQ = em.createNativeQuery(dataSql, DiagnosticEntity.class);
        for (int i = 0; i < params.size(); i++) dataQ.setParameter(i + 1, params.get(i));
        dataQ.setParameter(params.size() + 1, pageRequest.size());
        dataQ.setParameter(params.size() + 2, offset);

        String countSql = "SELECT COUNT(*) FROM diagnostics d"
            + " JOIN alerts a ON d.alert_id = a.id"
            + where;
        Query countQ = em.createNativeQuery(countSql);
        for (int i = 0; i < params.size(); i++) countQ.setParameter(i + 1, params.get(i));

        List<Diagnostic> items = ((List<DiagnosticEntity>) dataQ.getResultList())
            .stream().map(DiagnosticEntityMapper::toDomain).toList();
        long total = ((Number) countQ.getSingleResult()).longValue();

        return PageResult.of(items, total, pageRequest);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
