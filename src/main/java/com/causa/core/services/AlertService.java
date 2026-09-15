package com.causa.core.services;

import com.causa.core.domain.Alert;
import com.causa.core.domain.PageRequest;
import com.causa.core.domain.PageResult;

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
     * Returns a paginated, filtered list of alerts.
     *
     * @param filter      filter criteria — use {@link Alert.Filter#empty()} for no filtering
     * @param pageRequest page and size
     * @return paginated result of matching alerts
     */
    PageResult<Alert> listAlerts(Alert.Filter filter, PageRequest pageRequest);
}
