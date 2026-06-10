package com.causa.core.services;

import com.causa.core.domain.Alert;

import java.util.List;

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
     *
     * @param alerts the list of domain Alert objects to process
     * @return the list of alerts that were actually accepted for processing (after filtering)
     */
    List<Alert> processAlerts(List<Alert> alerts);

    /**
     * Checks whether a specific alert is within its cooldown window.
     *
     * <p>Alerts in cooldown should be skipped to prevent duplicate processing.
     *
     * @param alert the alert to check
     * @return true if the alert is still cooling down and should be skipped
     */
    boolean isInCooldown(Alert alert);
}
