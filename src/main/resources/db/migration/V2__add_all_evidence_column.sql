-- =============================================================================
-- V2: Add all_evidence column to diagnostics table
-- =============================================================================
-- Purpose: Store complete EvidenceItem instances (11-field model) from validation pipeline
-- for debugging and audit. The top 3-5 are transformed to Evidence (5-field model) for API.
--
-- Existing evidence column: LLM-generated evidences from RCA (backward compatible)
-- New all_evidence column: Structured validation evidences from PATH A + PATH B
-- =============================================================================

ALTER TABLE diagnostics
    ADD COLUMN all_evidence JSONB;

COMMENT ON COLUMN diagnostics.all_evidence IS 'Complete evidence items from validation pipeline. Shape: [{"id": "...", "source": "...", "type": "...", "strength": "...", ...}, ...]. Stores all EvidenceItem instances (11-field model) for debugging and audit.';
