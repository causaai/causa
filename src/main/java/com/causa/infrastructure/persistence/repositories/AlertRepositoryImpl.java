package com.causa.infrastructure.persistence.repositories;

import com.causa.common.exceptions.AlertException;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
import com.causa.core.ports.AlertRepository;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.causa.infrastructure.persistence.mappers.AlertEntityMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

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
            AlertEntity entity = AlertEntityMapper.toEntity(alert);
            entity.persist();
            return alert;
        } catch (Exception e) {
            throw new AlertException(LogMessages.Alert.ALERT_PERSIST_FAILED + ": " + alert.getAlertId(), "PersistenceError", e);
        }
    }

    @Override
    @Transactional
    public void updateHasDiagnostics(String alertId, boolean hasDiagnostics) {
        // Note: The new entity structure doesn't have a hasDiagnostics field
        // This is now derived from the existence of related diagnostics
        // For now, this is a no-op, but we keep the method for interface compatibility
        try {
            AlertEntity entity = AlertEntity.findById(alertId);
            if (entity == null) {
                throw new AlertException(LogMessages.Alert.ALERT_NOT_FOUND + ": " + alertId, "NotFound");
            }
            // The relationship is now managed through the DiagnosticEntity.alert foreign key
        } catch (AlertException e) {
            throw e;
        } catch (Exception e) {
            throw new AlertException(LogMessages.Alert.ALERT_UPDATE_FAILED + ": " + alertId, "UpdateError", e);
        }
    }
}
