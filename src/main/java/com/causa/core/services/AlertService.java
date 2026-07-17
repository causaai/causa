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
     * Processes a batch of incoming alerts — applies severity, namespace and cooldown
     * filtering, persists every alert, and returns accepted alerts.
     */
    List<Alert> processAlerts(List<Alert> alerts, Map<String, String> rejectedReasons);

    boolean isInCooldown(Alert alert);

    Optional<Alert> getAlert(String alertId);

    /**
     * Returns alerts filtered by the given parameters using AND logic.
     * Pass {@code null} / blank for any param to skip that filter.
     * All non-blank params must match simultaneously.
     */
    List<Alert> getAlerts(String workloadName, String namespace);
}
