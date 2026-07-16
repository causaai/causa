package com.causa.core.services;

import com.causa.core.domain.Alert;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Alert Service — Primary Port
 *
 * @since 0.0.1
 */
public interface AlertService {

    /**
     * Processes a batch of incoming alerts — applies severity, namespace and cooldown
     * filtering, persists every alert, and returns the split result.
     */
    ProcessedAlerts processAlerts(List<Alert> alerts);

    boolean isInCooldown(Alert alert);

    Optional<Alert> getAlert(String alertId);

    /**
     * Returns alerts filtered by the given parameters using AND logic.
     * Pass {@code null} / blank for any param to skip that filter.
     * All non-blank params must match simultaneously.
     */
    List<Alert> getAlerts(String workloadName, String namespace);

    /**
     * Result of processing a batch of incoming alerts.
     *
     * @param accepted alerts that passed all filters — persisted with {@code PROCESSING} status
     * @param rejected alerts paired with their rejection reason — persisted with {@code REJECTED} status
     */
    record ProcessedAlerts(List<Alert> accepted, Map<Alert, String> rejected) {
        public int total() { return accepted.size() + rejected.size(); }
    }
}
