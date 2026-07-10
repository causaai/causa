package com.causa.core.services;

import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;

import java.util.List;
import java.util.Optional;

/**
 * Diagnostic Service - Primary Port
 *
 * <p>Primary port for diagnostic pipeline operations.
 * <p>Framework-agnostic interface with no JAX-RS or Quarkus annotations.
 *
 * @since 0.0.1
 */
public interface DiagnosticService {

    /**
     * Triggers the diagnostic pipeline for a given alert.
     *
     * <p>This initiates the full diagnostic workflow including:
     * <ul>
     *   <li>Context collection from MCP servers</li>
     *   <li>LLM-based root cause analysis</li>
     *   <li>Validation of LLM output</li>
     *   <li>Generation of recommendations</li>
     * </ul>
     *
     * @param alert the alert to analyze
     * @return the diagnostic result (initially in PENDING status)
     */
    Diagnostic triggerDiagnostics(Alert alert);

    /**
     * Returns all diagnostics (summary list).
     *
     * @return list of all diagnostics
     */
    List<Diagnostic> listDiagnostics();

    /**
     * Returns a single diagnostic by its ID.
     *
     * @param diagnosticId the diagnostic ID
     * @return Optional containing the diagnostic if found
     */
    Optional<Diagnostic> getDiagnosticById(String diagnosticId);
}
