-- =============================================================================
-- Causa Backend - Settings API Schema
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
    id                VARCHAR(21)              NOT NULL,   -- extc_<16-alphanumeric>
    category          VARCHAR(32)              NOT NULL,   -- OBSERVABILITY | INTEGRATION
    platform          VARCHAR(64)              NOT NULL,   -- DATADOG | INSTANA | OTHER | SLACK | JIRA | GITHUB
    name              VARCHAR(128)             NOT NULL,   -- user-defined label, e.g. "instana-prod"
    url               TEXT,
    auth_type         VARCHAR(32)              NOT NULL,   -- API_KEY | API_TOKEN | WEBHOOK | PAT
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
--    Only one is_active=true at a time — enforced by the service layer.
-- =============================================================================

CREATE TABLE IF NOT EXISTS llm_configs (
    id                VARCHAR(21)              NOT NULL,   -- llmc_<16-alphanumeric>
    provider          VARCHAR(64)              NOT NULL,   -- OPENAI | ANTHROPIC | AZURE_OPENAI | WATSONX
    models            TEXT[]                   NOT NULL,   -- e.g. {gpt-4o, gpt-4o-mini}
    auth_type         VARCHAR(32)              NOT NULL,   -- API_KEY | VERTEX_AI | CUSTOM_HEADERS
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
