package com.causa.core.services;

import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.JiraTicketOptions;
import com.causa.core.domain.JiraUser;
import com.causa.core.domain.PageRequest;
import com.causa.core.domain.PageResult;

import java.util.List;
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
     * Returns a paginated list of diagnostics ordered by creation time descending,
     * optionally filtered by container name and/or namespace.
     *
     * @param filter      optional container/namespace filter
     * @param pageRequest page and size
     * @return paginated result of diagnostics
     */
    PageResult<Diagnostic> listDiagnostics(Diagnostic.Filter filter, PageRequest pageRequest);

    /**
     * Returns a single diagnostic by its ID.
     */
    Optional<Diagnostic> getDiagnosticById(String diagnosticId);

    /**
     * Creates a Jira ticket for the alert behind the diagnostic, or returns the one that already
     * exists for that alert. Throws {@link com.causa.common.exceptions.DiagnosticException} with
     * errorType {@code JIRA_NOT_CONFIGURED}, {@code INVALID_REQUEST}, {@code NOT_FOUND} or
     * {@code JIRA_API_ERROR}. The options only apply when a new ticket is created.
     */
    JiraTicket createOrGetJiraTicket(String diagnosticId, JiraTicketOptions options);

    /**
     * Returns Jira users assignable in the configured project matching {@code query} (all when blank).
     * Throws {@link com.causa.common.exceptions.DiagnosticException} with errorType
     * {@code JIRA_NOT_CONFIGURED} or {@code JIRA_API_ERROR}.
     */
    List<JiraUser> searchJiraUsers(String query);

    /** Jira ticket URL and whether it existed before this call. */
    record JiraTicket(String url, boolean alreadyExisted) {}
}
