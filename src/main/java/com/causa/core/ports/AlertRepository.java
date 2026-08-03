package com.causa.core.ports;

import com.causa.core.domain.Alert;

import java.util.List;
import java.util.Optional;

/**
 * Alert Repository — Secondary Port
 *
 * @since 0.0.1
 */
public interface AlertRepository {

    Alert save(Alert alert);

    Alert saveRejected(Alert alert, String reason);

    void updateHasDiagnostics(String alertId, boolean hasDiagnostics);

    void updateProcessingStatus(String alertId, String status);

    Optional<Alert> findById(String alertId);

    List<Alert> findAll();

    /**
     * Returns all alerts that match ALL non-null/non-blank parameters (AND logic).
     * A null or blank value for any parameter means that filter is skipped.
     *
     * @param workloadName filter by {@code workload_name} column (exact match); null = skip
     * @param namespace    filter by {@code workload_info->>'namespace'} (exact match); null = skip
     */
    List<Alert> findByFilters(String workloadName, String namespace);
}
