package com.causa.infrastructure.persistence.repositories;

import com.causa.common.exceptions.AlertException;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
import com.causa.core.ports.AlertRepository;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.causa.infrastructure.persistence.mappers.AlertEntityMapper;
import jakarta.enterprise.context.ApplicationScoped;
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

    @Override
    @Transactional
    public Alert save(Alert alert) {
        try {
            AlertEntityMapper.toEntityWithStatus(alert, AlertEntityMapper.STATUS_PROCESSING, null)
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
     * AND query: builds conditions dynamically — only non-blank params are included.
     *
     * <ul>
     *   <li>{@code workload_name} is a plain VARCHAR column — HQL equality works directly.</li>
     *   <li>{@code namespace} lives inside the {@code workload_info} JSONB column —
     *       accessed via the PostgreSQL {@code ->>} operator in a native fragment.</li>
     * </ul>
     *
     * When both params are supplied the query becomes:
     * {@code workloadName = ?1 AND workload_info->>'namespace' = ?2}
     */
    @Override
    public List<Alert> findByFilters(String workloadName, String namespace) {
        boolean hasWorkload   = workloadName != null && !workloadName.isBlank();
        boolean hasNamespace  = namespace    != null && !namespace.isBlank();

        if (!hasWorkload && !hasNamespace) {
            return findAll();
        }

        List<String> clauses = new ArrayList<>();
        List<Object> params  = new ArrayList<>();

        if (hasWorkload) {
            clauses.add(AlertEntity.Fields.WORKLOAD_NAME + " = ?" + (params.size() + 1));
            params.add(workloadName);
        }
        if (hasNamespace) {
            // JSONB ->> operator: native HQL fragment supported by Hibernate + PostgreSQL
            clauses.add("workload_info->>'namespace' = ?" + (params.size() + 1));
            params.add(namespace);
        }

        String query = String.join(" and ", clauses);

        return AlertEntity.<AlertEntity>find(query, params.toArray())
            .stream()
            .map(AlertEntityMapper::toDomain)
            .toList();
    }
}
