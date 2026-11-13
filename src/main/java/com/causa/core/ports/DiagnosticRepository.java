package com.causa.core.ports;

import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.PageRequest;
import com.causa.core.domain.PageResult;

import java.util.List;
import java.util.Optional;

/**
 * Diagnostic Repository — Secondary Port
 *
 * @since 0.0.1
 */
public interface DiagnosticRepository {

    /** Persists a new diagnostic row. */
    Diagnostic save(Diagnostic diagnostic);

    /** Merges an existing diagnostic row (status updates, RCA, validation). */
    Diagnostic update(Diagnostic diagnostic);

    /**
     * Returns one bounded page of diagnostics whose alert still needs finalization.
     *
     * <p>The result includes active diagnostics and terminal diagnostics whose alert
     * status is still {@code PROCESSING}, so restart recovery can complete both paths.
     */
    List<Diagnostic> findUnfinished(PageRequest pageRequest);

    /** Updates a diagnostic only when its current status still matches the observed status. */
    boolean updateStatusIfCurrent(String diagnosticId, DiagnosticStatus currentStatus, DiagnosticStatus newStatus);

    /** Finds a diagnostic by its application-generated ID. */
    Optional<Diagnostic> findById(String diagnosticId);

    /**
     * Returns all diagnostics ordered by {@code created_at} descending, paginated
     * according to {@code pageRequest}, optionally filtered by container and/or namespace.
     *
     * @param filter      optional container/namespace filter; use {@link Diagnostic.Filter#empty()} for no filtering
     * @param pageRequest page and size
     * @return a paginated result containing diagnostics and total count
     */
    PageResult<Diagnostic> search(Diagnostic.Filter filter, PageRequest pageRequest);
}
