package com.causa.infrastructure.persistence.repositories;

import com.causa.common.exceptions.AlertException;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
import com.causa.core.domain.PageRequest;
import com.causa.core.domain.PageResult;
import com.causa.core.ports.AlertRepository;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.causa.infrastructure.persistence.mappers.AlertEntityMapper;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * <p>Uses JPQL via Panache. The {@code namespace} field is a Hibernate
     * {@code @Formula} that expands to {@code workload_info->>'namespace'} at
     * query time, so no native SQL or {@code EntityManager} injection is needed.
     */
    @Override
    public PageResult<Alert> search(Alert.Filter filter, PageRequest pageRequest) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> args = new HashMap<>();

        if (!isBlank(filter.workloadName())) {
            clauses.add(AlertEntity.Fields.WORKLOAD_NAME + " = :workloadName");
            args.put("workloadName", filter.workloadName());
        }
        if (!isBlank(filter.namespace())) {
            clauses.add(AlertEntity.Fields.NAMESPACE + " = :namespace");
            args.put("namespace", filter.namespace());
        }
        if (!isBlank(filter.status())) {
            clauses.add(AlertEntity.Fields.STATUS + " = :status");
            args.put("status", filter.status());
        }

        Sort sort = Sort.by("createdAt").descending();
        PanacheQuery<AlertEntity> query = clauses.isEmpty()
            ? AlertEntity.findAll(sort)
            : AlertEntity.find(String.join(" AND ", clauses), sort, args);

        long total = query.count();
        List<Alert> items = query
            .page(Page.of(pageRequest.panachePage(), pageRequest.size()))
            .list()
            .stream()
            .map(AlertEntityMapper::toDomain)
            .toList();

        return PageResult.of(items, total, pageRequest);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
