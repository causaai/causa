-- =============================================================================
-- Causa Backend - Settings API Schema + Diagnostics Evidence Column
-- Flyway Migration: V2
-- PostgreSQL 14+
-- =============================================================================


-- =============================================================================
-- 1. EXTERNAL CONFIGS TABLE
--    Covers both Observability (DATADOG | INSTANA | OTHER) and
--    Integration (SLACK | JIRA | GITHUB) platforms via the category discriminator.
--    UNIQUE(platform, name) — multiple named configs per platform allowed.
-- =============================================================================

CREATE TABLE IF NOT EXISTS external_configs (
    id                VARCHAR(24)              NOT NULL,   -- ext_cnf_<16-alphanumeric>
    category          VARCHAR(32)              NOT NULL,   -- OBSERVABILITY | INTEGRATION
    platform          VARCHAR(64)              NOT NULL,   -- DATADOG | INSTANA | OTHER | SLACK | JIRA | GITHUB
    name              VARCHAR(128)             NOT NULL,   -- user-defined label, e.g. "instana-prod"
    url               TEXT,
    is_active         BOOLEAN                  NOT NULL DEFAULT TRUE,
    auth_config       JSONB                    NOT NULL,
    additional_config JSONB,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_external_configs           PRIMARY KEY (id),
    CONSTRAINT uq_external_configs_plat_name UNIQUE (platform, name)
);

CREATE INDEX IF NOT EXISTS idx_external_configs_category ON external_configs (category);
CREATE INDEX IF NOT EXISTS idx_external_configs_active   ON external_configs (category, is_active);


-- =============================================================================
-- 2. LLM CONFIGS TABLE
--    One row per provider. UNIQUE(provider).
--    Only one is_active=true at a time — enforced at DB level via partial unique index.
-- =============================================================================

CREATE TABLE IF NOT EXISTS llm_configs (
    id                VARCHAR(24)              NOT NULL,   -- llm_cnf_<16-alphanumeric>
    provider          VARCHAR(64)              NOT NULL,   -- OPENAI | ANTHROPIC | AZURE_OPENAI | WATSONX | VERTEX_AI
    url               TEXT                     NOT NULL,   -- LLM API endpoint URL
    models            TEXT[]                   NOT NULL,   -- e.g. {gpt-4o, gpt-4o-mini}
    temperature       NUMERIC(4,2),
    max_tokens        INTEGER,
    timeout_ms        INTEGER,
    is_active         BOOLEAN                  NOT NULL DEFAULT TRUE,
    auth_config       JSONB                    NOT NULL,
    additional_config JSONB,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_llm_configs      PRIMARY KEY (id),
    CONSTRAINT uq_llm_configs_prov UNIQUE (provider)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_llm_configs_single_active ON llm_configs (is_active) WHERE is_active = TRUE;


-- =============================================================================
-- 3. DIAGNOSTICS — all_evidence COLUMN
--    Stores complete EvidenceItem instances (11-field model) from the validation
--    pipeline for debugging and audit. The top 3-5 are transformed to Evidence
--    (5-field model) for API responses.
--
--    Existing evidence column: LLM-generated evidences from RCA (backward compatible)
--    New all_evidence column:  Structured validation evidences from PATH A + PATH B
-- =============================================================================

ALTER TABLE diagnostics
    ADD COLUMN IF NOT EXISTS all_evidence JSONB;

COMMENT ON COLUMN diagnostics.all_evidence IS 'Complete evidence items from validation pipeline. Shape: [{"id": "...", "source": "...", "type": "...", "strength": "...", ...}, ...]. Stores all EvidenceItem instances (11-field model) for debugging and audit.';
