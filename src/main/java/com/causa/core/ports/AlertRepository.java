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
     * Saves an accepted alert with {@code ACCEPTED} processing status.
     *
     * @param alert the alert to save
     * @return the saved alert
     */
    Alert save(Alert alert);

    /**
     * Saves a rejected alert with {@code REJECTED} status and stores the rejection
     * reason in {@code alert_metadata.rejection_reason}.
     *
     * @param alert  the filtered alert to persist
     * @param reason human-readable reason (e.g. "severity", "namespace", "cooldown")
     * @return the saved alert
     */
    Alert saveRejected(Alert alert, String reason);

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

    /**
     * Returns all alerts ordered by timestamp descending.
     *
     * @return list of all alerts
     */
    List<Alert> findAll();

    /**
     * Returns all alerts for a given container name, ordered by timestamp descending.
     *
     * @param containerName the container name to filter by
     * @return list of matching alerts
     */
    List<Alert> findByContainerName(String containerName);
}
