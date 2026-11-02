-- =============================================================================
-- Causa Backend - Diagnostics Ticket Link Column
-- Flyway Migration: V3
-- PostgreSQL 14+
-- =============================================================================

-- =============================================================================
-- 1. DIAGNOSTICS — ticket_link COLUMN
--    Stores the full Jira browse URL created for the parent alert.
--    Nullable — null until the user triggers Jira ticket creation via the API.
--    One alert maps to one ticket: the service layer enforces this invariant
--    by checking all diagnostics for the same alert_id before calling Jira.
--    Written once; never overwritten (UPDATE uses WHERE ticket_link IS NULL).
-- =============================================================================

ALTER TABLE diagnostics
    ADD COLUMN IF NOT EXISTS ticket_link TEXT;

COMMENT ON COLUMN diagnostics.ticket_link
    IS 'Full Jira browse URL (e.g. https://acme.atlassian.net/browse/OPS-123). '
       'Shared across all diagnostics for the same alert — written once via '
       'POST /api/v1/diagnostics/{id}/ticket, never overwritten.';
