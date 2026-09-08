package com.causa.core.ports;

import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.PageRequest;
import com.causa.core.domain.PageResult;

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

    /** Finds a diagnostic by its application-generated ID. */
    Optional<Diagnostic> findById(String diagnosticId);

    /**
     * Returns all diagnostics ordered by {@code created_at} descending, paginated
     * according to {@code pageRequest}.
     *
     * @param pageRequest page and size
     * @return a paginated result containing diagnostics and total count
     */
    PageResult<Diagnostic> search(PageRequest pageRequest);
}
