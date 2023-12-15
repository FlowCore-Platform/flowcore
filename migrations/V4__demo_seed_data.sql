-- =============================================================================
-- V4__demo_seed_data.sql
-- Seed data for local development / demo environments.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS flowcore;

-- ---------------------------------------------------------------------------
-- Demo users  (stored as a lightweight key-value table so the engine can
--               resolve subject → roles without an external IdP during dev.)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS flowcore.demo_user (
    id        UUID  PRIMARY KEY DEFAULT uuid_generate_v4(),
    username  TEXT  NOT NULL UNIQUE,
    role      TEXT  NOT NULL CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER')),
    tenant    TEXT  NOT NULL DEFAULT 'demo'
);

INSERT INTO flowcore.demo_user (username, role, tenant) VALUES
    ('demo_admin',    'ADMIN',    'demo'),
    ('demo_operator', 'OPERATOR', 'demo'),
    ('demo_viewer',   'VIEWER',   'demo');

-- ---------------------------------------------------------------------------
-- Mock provider configurations
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS flowcore.provider_config (
    id            UUID  PRIMARY KEY DEFAULT uuid_generate_v4(),
    provider_name TEXT  NOT NULL UNIQUE,
    provider_type TEXT  NOT NULL,
    base_url      TEXT,
    api_key_ref   TEXT,                       -- reference (e.g. vault path), never raw secret
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    config_json   JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO flowcore.provider_config (provider_name, provider_type, base_url, api_key_ref, config_json) VALUES
    (
        'KYC Provider',
        'KYC',
        'https://mock-kyc.example.com/api/v1',
        'vault:secret/providers/kyc#api_key',
        '{"timeout_ms": 5000, "retry_count": 3, "sandbox": true}'::jsonb
    ),
    (
        'Card Issuer',
        'CARD_ISSUANCE',
        'https://mock-cards.example.com/api/v2',
        'vault:secret/providers/card-issuer#api_key',
        '{"timeout_ms": 5000, "retry_count": 3, "card_networks": ["VISA", "MASTERCARD"], "sandbox": true}'::jsonb
    ),
    (
        'Payment Gateway',
        'PAYMENT',
        'https://mock-payments.example.com/api/v1',
        'vault:secret/providers/payment-gw#api_key',
        '{"timeout_ms": 3000, "retry_count": 5, "supported_currencies": ["USD", "EUR", "GBP"], "sandbox": true}'::jsonb
    ),
    (
        'Wallet Provider',
        'WALLET',
        'https://mock-wallet.example.com/api/v1',
        'vault:secret/providers/wallet#api_key',
        '{"timeout_ms": 4000, "retry_count": 3, "ledger_mode": "double_entry", "sandbox": true}'::jsonb
    );
