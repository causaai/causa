package com.causa.core.ports;

import com.causa.core.domain.Alert;

import java.util.List;
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
     * Finds an alert by its ID.
     *
     * @param alertId the alert ID
     * @return Optional containing the alert if found
     */
    Optional<Alert> findById(String alertId);

    /**
     * Finds all alerts for a specific container.
     *
     * @param containerName the container name
     * @return list of alerts
     */
    List<Alert> findByContainerName(String containerName);

    /**
     * Finds all alerts.
     *
     * @return list of all alerts
     */
    List<Alert> findAll();

    /**
     * Updates the has_diagnostics flag for an alert.
     *
     * @param alertId the alert ID
     * @param hasDiagnostics the new value
     */
    void updateHasDiagnostics(String alertId, boolean hasDiagnostics);
}
