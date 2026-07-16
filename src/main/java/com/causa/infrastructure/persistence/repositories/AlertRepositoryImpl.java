package com.causa.infrastructure.persistence.repositories;

import com.causa.common.exceptions.AlertException;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
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

    @Override
    public List<Alert> findAll() {
        return AlertEntity.<AlertEntity>listAll()
            .stream()
            .map(AlertEntityMapper::toDomain)
            .toList();
    }

    /**
     * AND query using a native SQL statement so PostgreSQL JSONB operators ({@code ->>'namespace'})
     * are valid. Panache {@code find()} only accepts JPQL which does not support JSONB syntax.
     *
     * <p>Conditions added only for non-blank parameters — all present conditions are AND-ed.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<Alert> findByFilters(String workloadName, String namespace) {
        boolean hasWorkload  = workloadName != null && !workloadName.isBlank();
        boolean hasNamespace = namespace    != null && !namespace.isBlank();

        if (!hasWorkload && !hasNamespace) {
            return findAll();
        }

        List<String> clauses = new ArrayList<>();
        List<Object> params  = new ArrayList<>();

        if (hasWorkload) {
            clauses.add("workload_name = ?" + (params.size() + 1));
            params.add(workloadName);
        }
        if (hasNamespace) {
            // ->> is a PostgreSQL JSONB operator — only valid in native SQL, not JPQL
            clauses.add("workload_info->>'namespace' = ?" + (params.size() + 1));
            params.add(namespace);
        }

        String sql = "SELECT * FROM alerts WHERE " + String.join(" AND ", clauses);

        Query q = em.createNativeQuery(sql, AlertEntity.class);
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }

        return ((List<AlertEntity>) q.getResultList())
            .stream()
            .map(AlertEntityMapper::toDomain)
            .toList();
    }
}
