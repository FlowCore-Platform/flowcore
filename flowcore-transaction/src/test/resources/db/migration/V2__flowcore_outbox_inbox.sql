-- =============================================================================
-- V2__flowcore_outbox_inbox.sql
-- Outbox / Inbox pattern tables + idempotency key store.
-- =============================================================================

-- Schema already created in V1; keep the guard for standalone runs.
CREATE SCHEMA IF NOT EXISTS flowcore;

-- ---------------------------------------------------------------------------
-- outbox_event
-- ---------------------------------------------------------------------------
CREATE TABLE flowcore.outbox_event (
    id               UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type   TEXT        NOT NULL,
    aggregate_id     TEXT        NOT NULL,
    event_type       TEXT        NOT NULL,
    event_key        TEXT        NOT NULL,
    payload_json     JSONB,
    headers_json     JSONB,
    status           TEXT        NOT NULL DEFAULT 'PENDING'
                                 CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD')),
    publish_attempts INT         NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at     TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_outbox_event_event_key
    ON flowcore.outbox_event (event_key);

CREATE INDEX idx_outbox_event_status_next_attempt
    ON flowcore.outbox_event (status, next_attempt_at);

-- ---------------------------------------------------------------------------
-- inbox_message
-- ---------------------------------------------------------------------------
CREATE TABLE flowcore.inbox_message (
    id            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    source        TEXT        NOT NULL,
    message_id    TEXT        NOT NULL,
    consumer_group TEXT       NOT NULL,
    received_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_inbox_message_source_msgid_group
    ON flowcore.inbox_message (source, message_id, consumer_group);

-- ---------------------------------------------------------------------------
-- idempotency_key
-- ---------------------------------------------------------------------------
CREATE TABLE flowcore.idempotency_key (
    id            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    scope         TEXT        NOT NULL,
    key           TEXT        NOT NULL,
    request_hash  TEXT,
    response_json JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_idempotency_key_scope_key
    ON flowcore.idempotency_key (scope, key);
