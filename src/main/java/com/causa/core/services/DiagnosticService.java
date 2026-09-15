package com.causa.core.services;

import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.PageRequest;
import com.causa.core.domain.PageResult;

import java.util.Optional;

/**
 * Diagnostic Service — Primary Port
 *
 * @since 0.0.1
 */
public interface DiagnosticService {

    /**
     * Persists a PENDING diagnostic stub for the given alert and immediately returns it.
     * The full MCP + LLM analysis pipeline is dispatched on a background thread — the caller
     * is never blocked.
     *
     * @param alert the accepted alert to analyse
     * @return the PENDING diagnostic (id + alertId + status only)
     */
    Diagnostic triggerDiagnostics(Alert alert);

    /**
     * Returns a paginated list of diagnostics ordered by creation time descending.
     *
     * @param pageRequest page and size
     * @return paginated result of diagnostics
     */
    PageResult<Diagnostic> listDiagnostics(PageRequest pageRequest);

    /**
     * Returns a single diagnostic by its ID.
     */
    Optional<Diagnostic> getDiagnosticById(String diagnosticId);
}
