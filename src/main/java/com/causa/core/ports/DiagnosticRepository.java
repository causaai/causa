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
     * according to {@code pageRequest}, optionally filtered by container and/or namespace.
     *
     * @param filter      optional container/namespace filter; use {@link Diagnostic.Filter#empty()} for no filtering
     * @param pageRequest page and size
     * @return a paginated result containing diagnostics and total count
     */
    PageResult<Diagnostic> search(Diagnostic.Filter filter, PageRequest pageRequest);

    /**
     * Takes a transaction-scoped lock for the alert, blocking until available. Must be called inside
     * a transaction; released on commit/rollback. Serialises ticket creation per alert.
     */
    void lockAlertForTicketCreation(String alertId);

    /** Returns the first non-null {@code ticket_link} across all diagnostics of the alert. */
    Optional<String> findTicketLinkByAlertId(String alertId);

    /** Stores the Jira URL on the diagnostic row only if {@code ticket_link} is still NULL. */
    void updateTicketLink(String diagnosticId, String ticketLink);
}
