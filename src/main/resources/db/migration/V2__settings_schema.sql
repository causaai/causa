-- =============================================================================
-- Causa Backend - Settings API Schema
-- Flyway Migration: V2
-- PostgreSQL 14+
-- =============================================================================


-- =============================================================================
-- 1. PLATFORM CONFIGS TABLE
--    Covers both Observability (DATADOG | INSTANA | OTHER) and
--    Integration (SLACK | JIRA | GITHUB) platforms via the category discriminator.
--    UNIQUE(category, platform) — one config per platform per category.
-- =============================================================================

CREATE TABLE IF NOT EXISTS platform_configs (
    id                VARCHAR(21)              NOT NULL,   -- pltf_<16-alphanumeric>
    category          VARCHAR(32)              NOT NULL,   -- OBSERVABILITY | INTEGRATION
    platform          VARCHAR(64)              NOT NULL,   -- DATADOG | INSTANA | OTHER | SLACK | JIRA | GITHUB
    url               VARCHAR(512), 
    auth_type         VARCHAR(32)              NOT NULL,   -- API_KEY | API_TOKEN | WEBHOOK | PAT
    is_active         BOOLEAN                  NOT NULL DEFAULT FALSE,
    auth_config       JSONB                    NOT NULL,
    additional_config JSONB,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_platform_configs           PRIMARY KEY (id),
    CONSTRAINT uq_platform_configs_cat_plat  UNIQUE (category, platform)
);

CREATE INDEX IF NOT EXISTS idx_platform_configs_category ON platform_configs (category);
CREATE INDEX IF NOT EXISTS idx_platform_configs_active   ON platform_configs (category, is_active);


-- =============================================================================
-- 2. LLM CONFIGS TABLE
--    One row per provider. UNIQUE(provider).
--    Only one is_active=true at a time — enforced by the service layer.
-- =============================================================================

CREATE TABLE IF NOT EXISTS llm_configs (
    id                VARCHAR(21)              NOT NULL,   -- llmc_<16-alphanumeric>
    name              VARCHAR(128)             NOT NULL,
    provider          VARCHAR(64)              NOT NULL,   -- OPENAI | ANTHROPIC | AZURE_OPENAI | WATSONX | VERTEX_AI | CUSTOM
    model             VARCHAR(128)             NOT NULL,
    auth_type         VARCHAR(32)              NOT NULL,   -- API_KEY | VERTEX_AI | CUSTOM_HEADERS
    temperature       NUMERIC(4,2),
    max_tokens        INTEGER,
    timeout_ms        INTEGER,
    is_active         BOOLEAN                  NOT NULL DEFAULT FALSE,
    auth_config       JSONB                    NOT NULL,
    additional_config JSONB,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_llm_configs      PRIMARY KEY (id),
    CONSTRAINT uq_llm_configs_prov UNIQUE (provider)
);
