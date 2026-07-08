package com.causa.core.ports;

import com.causa.core.domain.Alert;

import java.util.Optional;

/**
 * Alert Repository - Secondary Port
 *
 * <p>Repository interface for alert persistence operations.
 * <p>Framework-agnostic interface with no JPA or database-specific annotations.
 *
 * @since 0.0.1
 */
public interface AlertRepository {

    /**
     * Saves an alert to the persistence layer.
     *
     * @param alert the alert to save
     * @return the saved alert
     */
    Alert save(Alert alert);

    /**
     * Updates the has_diagnostics flag for an alert.
     *
     * @param alertId the alert ID
     * @param hasDiagnostics the new value
     */
    void updateHasDiagnostics(String alertId, boolean hasDiagnostics);

    /**
     * Finds an alert by its application-generated ID.
     *
     * @param alertId the alert ID
     * @return an Optional containing the domain Alert if found, empty otherwise
     */
    Optional<Alert> findById(String alertId);
}
