-- =============================================================================
-- Causa Backend - Initial Database Schema
-- Flyway Migration: V1
-- PostgreSQL 14+ with pgvector extension
--
-- ID convention: {4-letter-prefix}_{16-char-alphanumeric}  →  VARCHAR(21)
--   alerts        → alrt_<16>
--   diagnostics   → diag_<16>
--   context_data  → ctxd_<16>
--   feedback      → fdbk_<16>
--   configurations → cnfg_<16>
--   integrations  → intg_<16>
--   health_checks → hchk_<16>
--
-- IDs are generated and validated by the application layer.
-- =============================================================================


-- =============================================================================
-- 1. ALERTS TABLE (Prometheus Webhook Ingestion)
-- =============================================================================

CREATE TABLE IF NOT EXISTS alerts (
    id               VARCHAR(21)              NOT NULL,
    source_alert_id  VARCHAR(255),
    alert_name       VARCHAR(255)             NOT NULL,
    alert_timestamp  TIMESTAMP WITH TIME ZONE,
    severity         VARCHAR(32),
    status           VARCHAR(32)              NOT NULL,   -- ACCEPTED, REJECTED, PROCESSING, PROCESSED
    workload_info    JSONB                    NOT NULL,
    workload_name    VARCHAR(255)             NOT NULL,
    alert_metadata   JSONB,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_alerts PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_alerts_lookup          ON alerts (alert_name);
CREATE INDEX IF NOT EXISTS idx_alerts_lookup_workload ON alerts (workload_name);
CREATE INDEX IF NOT EXISTS idx_alerts_status           ON alerts (status);
CREATE INDEX IF NOT EXISTS idx_alerts_created_at       ON alerts (created_at DESC);


-- =============================================================================
-- 2. DIAGNOSTICS TABLE (Main Diagnostic & Token Analytics Output)
-- =============================================================================

CREATE TABLE IF NOT EXISTS diagnostics (
    id                  VARCHAR(21)  NOT NULL,
    alert_id            VARCHAR(21)  NOT NULL,
    status              VARCHAR(32)  NOT NULL,   -- PENDING, PROCESSING, COMPLETED, FAILED

    issue_title         VARCHAR(255),
    issue_description   TEXT,
    issue_type          VARCHAR(64),
    root_cause_summary  TEXT,

    confidence_info     JSONB,

    -- Diagnostic Provider Tracking & Token Analytics
    llm_info            JSONB,

    -- Structured array of actionable remediation steps
    -- Each element: { solution, justification, success_probability, implementation_notes }
    recommendations           JSONB,

    -- Supporting evidence: logs, metric citations, and confidence explanation
    -- Shape: { supporting_logs: [...], evidences: [...], confidence_summary: "..." }
    evidence            JSONB,

    -- Validation data
    validation_result   VARCHAR(64),
    validation_data     JSONB,

    -- Catch-all for LLM runtime notes and any future diagnostic fields
    -- Shape: { llm_notes: "...", <extensible> }
    diagnostics_metadata JSONB,
    remarks             TEXT,
    created_by          VARCHAR(255) DEFAULT 'AUTOMATIC',

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_diagnostics       PRIMARY KEY (id),
    CONSTRAINT fk_diagnostics_alert FOREIGN KEY (alert_id) REFERENCES alerts (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_diagnostics_alert      ON diagnostics (alert_id);
CREATE INDEX IF NOT EXISTS idx_diagnostics_status     ON diagnostics (status);
CREATE INDEX IF NOT EXISTS idx_diagnostics_created_at ON diagnostics (created_at DESC);


-- =============================================================================
-- 3. CONTEXT DATA TABLE (Logs, Events, and Vector Embeddings)
-- =============================================================================

CREATE TABLE IF NOT EXISTS context_data (
    id               VARCHAR(21)  NOT NULL,
    alert_id         VARCHAR(21)  NOT NULL,
    container_name   VARCHAR(255) NOT NULL,
    context_type     VARCHAR(64)  NOT NULL,   -- K8S_LOGS, JFR_REPORT, KRUIZE_METRICS
    content          TEXT         NOT NULL,

    -- Vector Storage: Defaulting to 1536 dimensions (OpenAI standard)
    embedding        vector(1536),

    context_metadata JSONB,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_context_data       PRIMARY KEY (id),
    CONSTRAINT fk_context_data_alert FOREIGN KEY (alert_id) REFERENCES alerts (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_context_alert          ON context_data (alert_id, context_type);
CREATE INDEX IF NOT EXISTS idx_context_container_name ON context_data (container_name);

-- Production-grade HNSW Vector index for rapid similarity searches
CREATE INDEX IF NOT EXISTS idx_context_vector ON context_data
    USING hnsw (embedding vector_cosine_ops);


-- =============================================================================
-- 4. FEEDBACK TABLE (SRE Validations and Comments)
-- =============================================================================

CREATE TABLE IF NOT EXISTS feedback (
    id             VARCHAR(21)  NOT NULL,
    diagnostics_id VARCHAR(21)  NOT NULL,
    alert_id       VARCHAR(21)  NOT NULL,
    rating         INT          CHECK (rating BETWEEN 1 AND 5),
    remarks        TEXT,
    analysis_liked BOOLEAN      DEFAULT FALSE,
    solution_liked BOOLEAN      DEFAULT FALSE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_feedback             PRIMARY KEY (id),
    CONSTRAINT fk_feedback_diagnostics FOREIGN KEY (diagnostics_id) REFERENCES diagnostics (id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_alert       FOREIGN KEY (alert_id)       REFERENCES alerts (id)      ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_feedback_diagnostics ON feedback (diagnostics_id);
CREATE INDEX IF NOT EXISTS idx_feedback_alert       ON feedback (alert_id);


-- =============================================================================
-- 5. CONFIGURATIONS TABLE (Global Cluster Run Parameters & Microservice States)
-- =============================================================================

CREATE TABLE IF NOT EXISTS configurations (
    id           VARCHAR(21)  NOT NULL,
    config_key   VARCHAR(255) NOT NULL,
    config_value TEXT         NOT NULL,
    is_encrypted BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_configurations PRIMARY KEY (id),
    CONSTRAINT uq_configurations_key UNIQUE (config_key)
);


-- =============================================================================
-- 6. INTEGRATIONS TABLE (Slack, Jira, Datadog Target Configurations)
-- =============================================================================

CREATE TABLE IF NOT EXISTS integrations (
    id              VARCHAR(21)  NOT NULL,
    target_platform VARCHAR(64)  NOT NULL,   -- JIRA, SLACK, DATADOG, GITHUB
    target_details  JSONB        NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_integrations       PRIMARY KEY (id),
    CONSTRAINT uq_integrations_platform UNIQUE (target_platform)
);


-- =============================================================================
-- 7. HEALTH CHECKS TABLE (Causa Engine Metrics & Downstream Heartbeats)
-- ID convention: hchk_<16-char-alphanumeric>
-- =============================================================================

CREATE TABLE IF NOT EXISTS health_checks (
    id             VARCHAR(21)              NOT NULL,
    overall_status VARCHAR(32)              NOT NULL,   -- UP, DEGRADED, DOWN
    component_info JSONB                    NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_health_checks PRIMARY KEY (id)
);

-- Crucial for the 15-day pruning process execution window
CREATE INDEX IF NOT EXISTS idx_health_cleanup ON health_checks (created_at);
