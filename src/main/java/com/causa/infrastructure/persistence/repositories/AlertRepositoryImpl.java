package com.causa.infrastructure.persistence.repositories;

import com.causa.common.exceptions.AlertException;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
import com.causa.core.domain.PageRequest;
import com.causa.core.domain.PageResult;
import com.causa.core.ports.AlertRepository;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.causa.infrastructure.persistence.mappers.AlertEntityMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Alert Repository Implementation
 *
 * <p>Panache-based implementation of {@link AlertRepository}.
 *
 * <p>All list queries use native SQL because the {@code workload_info->>'namespace'} JSONB
 * operator is not valid in JPQL — Panache's {@code find()} would throw at parse time.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class AlertRepositoryImpl implements AlertRepository {

    @jakarta.inject.Inject
    EntityManager em;

    @Override
    @Transactional
    public Alert save(Alert alert) {
        try {
            AlertEntityMapper.toEntityWithStatus(alert, AlertEntityMapper.STATUS_ACCEPTED, null)
                .persist();
            return alert;
        } catch (Exception e) {
            throw new AlertException(LogMessages.Alert.ALERT_PERSIST_FAILED + ": " + alert.getAlertId(), "PersistenceError", e);
        }
    }

    @Override
    @Transactional
    public Alert saveRejected(Alert alert, String reason) {
        try {
            AlertEntityMapper.toEntityWithStatus(alert, AlertEntityMapper.STATUS_REJECTED, reason)
                .persist();
            return alert;
        } catch (Exception e) {
            throw new AlertException(LogMessages.Alert.ALERT_PERSIST_FAILED + ": " + alert.getAlertId(), "PersistenceError", e);
        }
    }

    @Override
    @Transactional
    public void updateHasDiagnostics(String alertId, boolean hasDiagnostics) {
        updateProcessingStatus(alertId,
            hasDiagnostics ? AlertEntityMapper.STATUS_PROCESSED : AlertEntityMapper.STATUS_PROCESSING);
    }

    @Override
    @Transactional
    public void updateProcessingStatus(String alertId, String status) {
        try {
            int updated = AlertEntity.update(
                AlertEntity.Fields.STATUS + " = ?1 where " + AlertEntity.Fields.ALERT_ID + " = ?2",
                status, alertId);
            if (updated == 0) {
                throw new AlertException(LogMessages.Alert.ALERT_NOT_FOUND + ": " + alertId, "NotFound");
            }
        } catch (AlertException e) {
            throw e;
        } catch (Exception e) {
            throw new AlertException(LogMessages.Alert.ALERT_UPDATE_FAILED + ": " + alertId, "UpdateError", e);
        }
    }

    @Override
    public Optional<Alert> findById(String alertId) {
        return AlertEntity.<AlertEntity>findByIdOptional(alertId)
            .map(AlertEntityMapper::toDomain);
    }

    /**
     * Paginated search with optional AND-logic filters.
     *
     * <p>Uses native SQL throughout because the {@code workload_info->>'namespace'} JSONB
     * operator is illegal in JPQL. Parameters are positional to prevent SQL injection.
     * The ORDER BY clause is fixed to {@code created_at DESC} — sorting is not exposed
     * as a user-controlled parameter.
     */
    @Override
    @SuppressWarnings("unchecked")
    public PageResult<Alert> search(Alert.Filter filter, PageRequest pageRequest) {
        boolean hasWorkload  = !isBlank(filter.workloadName());
        boolean hasNamespace = !isBlank(filter.namespace());
        boolean hasStatus    = !isBlank(filter.status());

        List<String> clauses = new ArrayList<>();
        List<Object> params  = new ArrayList<>();

        if (hasWorkload) {
            clauses.add("workload_name = ?" + (params.size() + 1));
            params.add(filter.workloadName());
        }
        if (hasNamespace) {
            // ->> is a PostgreSQL JSONB operator — only valid in native SQL, not JPQL
            clauses.add("workload_info->>'namespace' = ?" + (params.size() + 1));
            params.add(filter.namespace());
        }
        if (hasStatus) {
            clauses.add("status = ?" + (params.size() + 1));
            params.add(filter.status());
        }

        String where  = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        int    offset = Math.multiplyExact(pageRequest.panachePage(), pageRequest.size());

        // Data query — fixed ORDER BY, LIMIT/OFFSET for pagination
        String dataSql = "SELECT * FROM alerts" + where
            + " ORDER BY created_at DESC"
            + " LIMIT ?"  + (params.size() + 1)
            + " OFFSET ?" + (params.size() + 2);

        Query dataQ = em.createNativeQuery(dataSql, AlertEntity.class);
        for (int i = 0; i < params.size(); i++) {
            dataQ.setParameter(i + 1, params.get(i));
        }
        dataQ.setParameter(params.size() + 1, pageRequest.size());
        dataQ.setParameter(params.size() + 2, offset);

        // Count query — same WHERE, no ORDER BY / LIMIT
        String countSql = "SELECT COUNT(*) FROM alerts" + where;
        Query countQ = em.createNativeQuery(countSql);
        for (int i = 0; i < params.size(); i++) {
            countQ.setParameter(i + 1, params.get(i));
        }

        List<Alert> items = ((List<AlertEntity>) dataQ.getResultList())
            .stream()
            .map(AlertEntityMapper::toDomain)
            .toList();
        long total = ((Number) countQ.getSingleResult()).longValue();

        return PageResult.of(items, total, pageRequest);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
