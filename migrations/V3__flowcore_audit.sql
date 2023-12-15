-- =============================================================================
-- V3__flowcore_audit.sql
-- Audit / compliance log for every authorisation decision.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS flowcore;

-- ---------------------------------------------------------------------------
-- audit_log
-- ---------------------------------------------------------------------------
CREATE TABLE flowcore.audit_log (
    id            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    actor_sub     TEXT,
    actor_roles   TEXT[],
    actor_tenant  TEXT,
    action        TEXT        NOT NULL,
    object_type   TEXT        NOT NULL,
    object_id     TEXT        NOT NULL,
    decision      TEXT        NOT NULL CHECK (decision IN ('ALLOW', 'DENY')),
    reason        TEXT,
    trace_id      TEXT,
    span_id       TEXT,
    request_ip    INET,
    user_agent    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    details_json  JSONB
);

CREATE INDEX idx_audit_log_object_type_id
    ON flowcore.audit_log (object_type, object_id);

CREATE INDEX idx_audit_log_created_at
    ON flowcore.audit_log (created_at);
