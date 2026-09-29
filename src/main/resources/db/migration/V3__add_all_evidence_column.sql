-- =============================================================================
-- V3: Add all_evidence column to diagnostics table
-- =============================================================================
-- Purpose: Store complete EvidenceItem instances (13-field model) from the validation
-- pipeline for debugging and audit. The top 3-5 are selected into the evidence column
-- and transformed to the Evidence UI model (5 fields) for the API response.
--
-- The evidence column is repurposed here. V1 reserved it for
-- { supporting_logs, evidences, confidence_summary }, a shape nothing ever wrote —
-- those fields live inside root_cause_summary. It now holds the selected EvidenceItem
-- subset, so selection runs once when the diagnostic completes rather than per request.
-- =============================================================================

ALTER TABLE diagnostics
    ADD COLUMN all_evidence JSONB;

COMMENT ON COLUMN diagnostics.all_evidence IS 'Complete evidence items from validation pipeline. Shape: [{"id": "...", "source": "...", "type": "...", "strength": "...", ...}, ...]. Stores all EvidenceItem instances (13-field model) for debugging and audit.';

COMMENT ON COLUMN diagnostics.evidence IS 'User-facing evidence subset. Shape: [{"id": "...", "source": "...", "type": "...", "strength": "...", ...}, ...]. The EvidenceItems chosen from all_evidence for the API response, selected once when the diagnostic completes.';
