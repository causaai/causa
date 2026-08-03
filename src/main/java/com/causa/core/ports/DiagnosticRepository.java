package com.causa.core.ports;

import com.causa.core.domain.Diagnostic;

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

    /** Returns all diagnostics ordered by {@code created_at} descending. */
    List<Diagnostic> findAll();

    /** Finds a diagnostic by its application-generated ID. */
    Optional<Diagnostic> findById(String diagnosticId);
}
