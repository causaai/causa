package com.causa.core.ports;

import com.causa.core.domain.Diagnostic;

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
}
