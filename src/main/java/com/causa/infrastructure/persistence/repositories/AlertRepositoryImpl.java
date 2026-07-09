package com.causa.infrastructure.persistence.repositories;

import com.causa.common.exceptions.AlertException;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
import com.causa.core.ports.AlertRepository;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.causa.infrastructure.persistence.mappers.AlertEntityMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Alert Repository Implementation
 *
 * <p>Panache-based implementation of the AlertRepository port.
 * <p>Uses Panache Repository pattern for cleaner, type-safe queries.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class AlertRepositoryImpl implements AlertRepository {

    @Override
    @Transactional
    public Alert save(Alert alert) {
        try {
            AlertEntity entity = AlertEntityMapper.toEntityWithStatus(alert, AlertEntityMapper.STATUS_ACCEPTED, null);
            entity.persist();
            return alert;
        } catch (Exception e) {
            throw new AlertException(LogMessages.Alert.ALERT_PERSIST_FAILED + ": " + alert.getAlertId(), "PersistenceError", e);
        }
    }

    @Override
    @Transactional
    public Alert saveRejected(Alert alert, String reason) {
        try {
            AlertEntity entity = AlertEntityMapper.toEntityWithStatus(alert, AlertEntityMapper.STATUS_REJECTED, reason);
            entity.persist();
            return alert;
        } catch (Exception e) {
            throw new AlertException(LogMessages.Alert.ALERT_PERSIST_FAILED + ": " + alert.getAlertId(), "PersistenceError", e);
        }
    }

    @Override
    @Transactional
    public void updateHasDiagnostics(String alertId, boolean hasDiagnostics) {
        try {
            // AlertEntity has no hasDiagnostics column — update status to PROCESSING/PROCESSED
            // to reflect that diagnostics are in progress or complete.
            String newStatus = hasDiagnostics ? "PROCESSED" : "ACCEPTED";
            int updated = AlertEntity.update(
                AlertEntity.Fields.STATUS + " = ?1 where " + AlertEntity.Fields.ALERT_ID + " = ?2",
                newStatus,
                alertId);

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

    @Override
    public List<Alert> findByContainerName(String containerName) {
        return AlertEntity.<AlertEntity>list(
                AlertEntity.Fields.CONTAINER_NAME + " = ?1", containerName)
            .stream()
            .map(AlertEntityMapper::toDomain)
            .toList();
    }
}
