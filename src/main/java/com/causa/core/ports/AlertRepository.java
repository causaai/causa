package com.causa.core.ports;

import com.causa.core.domain.Alert;
import com.causa.core.domain.PageRequest;
import com.causa.core.domain.PageResult;

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

    /**
     * Returns alerts matching all non-null/non-blank filter fields (AND logic),
     * paginated according to {@code pageRequest}.
     *
     * @param filter      filter criteria — use {@link Alert.Filter#empty()} for no filtering
     * @param pageRequest page and size — use {@link PageRequest#of(int, int)} to construct
     * @return a paginated result containing matching alerts and total count
     */
    PageResult<Alert> search(Alert.Filter filter, PageRequest pageRequest);
}
