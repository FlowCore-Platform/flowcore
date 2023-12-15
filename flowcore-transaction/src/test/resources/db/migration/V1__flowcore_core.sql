-- =============================================================================
-- V1__flowcore_core.sql
-- Core workflow engine tables: instances, tokens, step executions, timers.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS flowcore;

-- Enable uuid generation if not already available
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ---------------------------------------------------------------------------
-- workflow_instance
-- ---------------------------------------------------------------------------
CREATE TABLE flowcore.workflow_instance (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_type   TEXT        NOT NULL,
    business_key    TEXT        NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'RUNNING'
                                CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELED')),
    current_state   TEXT,
    version         BIGINT      NOT NULL DEFAULT 0,
    context_json    JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_workflow_instance_type_bizkey
    ON flowcore.workflow_instance (workflow_type, business_key);

CREATE INDEX idx_workflow_instance_status
    ON flowcore.workflow_instance (status);

-- ---------------------------------------------------------------------------
-- workflow_token
-- ---------------------------------------------------------------------------
CREATE TABLE flowcore.workflow_token (
    id                    UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_instance_id  UUID        NOT NULL REFERENCES flowcore.workflow_instance (id) ON DELETE CASCADE,
    token_name            TEXT        NOT NULL,
    active_node           TEXT,
    status                TEXT        NOT NULL DEFAULT 'ACTIVE'
                                      CHECK (status IN ('ACTIVE', 'WAITING', 'JOINED', 'CLOSED')),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_workflow_token_instance_name
    ON flowcore.workflow_token (workflow_instance_id, token_name);

-- ---------------------------------------------------------------------------
-- workflow_step_execution
-- ---------------------------------------------------------------------------
CREATE TABLE flowcore.workflow_step_execution (
    id                    UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_instance_id  UUID        NOT NULL REFERENCES flowcore.workflow_instance (id) ON DELETE CASCADE,
    token_id              UUID        NOT NULL REFERENCES flowcore.workflow_token (id) ON DELETE CASCADE,
    step_id               TEXT        NOT NULL,
    attempt               INT         NOT NULL DEFAULT 1,
    status                TEXT        NOT NULL DEFAULT 'STARTED'
                                      CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'COMPENSATED', 'SKIPPED')),
    started_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at           TIMESTAMPTZ,
    error_code            TEXT,
    error_detail          TEXT,
    result_json           JSONB
);

CREATE INDEX idx_workflow_step_execution_instance_step
    ON flowcore.workflow_step_execution (workflow_instance_id, step_id);

-- ---------------------------------------------------------------------------
-- workflow_timer
-- ---------------------------------------------------------------------------
CREATE TABLE flowcore.workflow_timer (
    id                    UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_instance_id  UUID        NOT NULL REFERENCES flowcore.workflow_instance (id) ON DELETE CASCADE,
    token_id              UUID        NOT NULL REFERENCES flowcore.workflow_token (id) ON DELETE CASCADE,
    timer_name            TEXT        NOT NULL,
    due_at                TIMESTAMPTZ NOT NULL,
    payload_json          JSONB,
    status                TEXT        NOT NULL DEFAULT 'SCHEDULED'
                                      CHECK (status IN ('SCHEDULED', 'FIRED', 'CANCELED')),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_workflow_timer_status_due
    ON flowcore.workflow_timer (status, due_at);
