package com.causa.core.services;

import com.causa.core.domain.Alert;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Alert Service - Primary Port
 *
 * <p>Primary port for alert ingestion use cases.
 * <p>Framework-agnostic interface with no JAX-RS or Quarkus annotations.
 *
 * @since 0.0.1
 */
public interface AlertService {

    /**
     * Processes a batch of alerts from an incoming webhook payload.
     *
     * <p>Applies severity filtering, namespace filtering, and cooldown deduplication.
     * <p><b>All alerts are persisted</b> — accepted with {@code ACCEPTED} status,
     * filtered ones with {@code REJECTED} status and a rejection reason stored in
     * {@code alert_metadata.rejection_reason}.
     *
     * @param alerts the list of domain Alert objects to process
     * @return {@link ProcessedAlerts} containing accepted and rejected alert lists
     */
    ProcessedAlerts processAlerts(List<Alert> alerts);

    /**
     * Checks whether a specific alert is within its cooldown window.
     *
     * @param alert the alert to check
     * @return true if the alert is still cooling down and should be skipped
     */
    boolean isInCooldown(Alert alert);

    /**
     * Retrieves a single alert by its ID.
     *
     * @param alertId the alert ID
     * @return an Optional containing the Alert if found, empty otherwise
     */
    Optional<Alert> getAlert(String alertId);

    /**
     * Retrieves all alerts, optionally filtered by container name.
     *
     * @param containerName optional container name filter (null or blank = return all)
     * @return list of matching alerts
     */
    List<Alert> getAlerts(String containerName);

    /**
     * Result of processing a batch of incoming alerts.
     *
     * @param accepted alerts that passed all filters — persisted with {@code ACCEPTED} status
     * @param rejected alerts paired with their rejection reason — persisted with {@code REJECTED} status
     */
    record ProcessedAlerts(List<Alert> accepted, Map<Alert, String> rejected) {

        /** Total alerts in this batch (accepted + rejected). */
        public int total() {
            return accepted.size() + rejected.size();
        }
    }
}
