package com.causa.core.ports;

import com.causa.core.domain.Diagnostic;

import java.util.List;
import java.util.Optional;

/**
 * Diagnostic Repository - Secondary Port
 *
 * <p>Repository interface for diagnostic persistence operations.
 * <p>Framework-agnostic interface with no JPA or database-specific annotations.
 *
 * @since 0.0.1
 */
public interface DiagnosticRepository {

    /**
     * Saves a diagnostic to the persistence layer.
     *
     * @param diagnostic the diagnostic to save
     * @return the saved diagnostic
     */
    Diagnostic save(Diagnostic diagnostic);

    /**
     * Updates an existing diagnostic.
     *
     * @param diagnostic the diagnostic to update
     * @return the updated diagnostic
     */
    Diagnostic update(Diagnostic diagnostic);

    /**
     * Returns all diagnostics ordered by creation time descending.
     *
     * @return list of all diagnostics
     */
    List<Diagnostic> findAll();

    /**
     * Finds a diagnostic by its application-generated ID.
     *
     * @param diagnosticId the diagnostic ID
     * @return Optional containing the diagnostic if found
     */
    Optional<Diagnostic> findById(String diagnosticId);
}
