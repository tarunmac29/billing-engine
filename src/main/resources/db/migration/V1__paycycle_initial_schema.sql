-- =============================================================
-- PayCycle Billing Engine - Initial Schema
-- Flyway migration: V1__paycycle_initial_schema.sql
-- Engine   : InnoDB
-- Charset  : utf8mb4
-- =============================================================

SET NAMES utf8mb4;
SET foreign_key_checks = 0;

-- =============================================================
-- TENANT
-- =============================================================
CREATE TABLE IF NOT EXISTS tenant (
    id           CHAR(36)      NOT NULL,
    name         VARCHAR(100)  NOT NULL,
    domain       VARCHAR(100)  NOT NULL,
    api_key_hash VARCHAR(128)  NOT NULL,
    is_active    TINYINT(1)    NOT NULL DEFAULT 1,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_tenant PRIMARY KEY (id),
    CONSTRAINT uq_tenant_domain UNIQUE (domain),
    CONSTRAINT uq_tenant_api_key UNIQUE (api_key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- CUSTOMER
-- =============================================================
CREATE TABLE IF NOT EXISTS customer (
    id          CHAR(36)     NOT NULL,
    tenant_id   CHAR(36)     NOT NULL,
    email       VARCHAR(200) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    phone       VARCHAR(30)  NULL,
    metadata    JSON         NULL,
    is_active   TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_customer PRIMARY KEY (id),
    CONSTRAINT fk_customer_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT uq_customer_tenant_email UNIQUE (tenant_id, email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_customer_tenant_active ON customer (tenant_id, is_active);


-- =============================================================
-- PLAN
-- =============================================================
CREATE TABLE IF NOT EXISTS plan (
    id                CHAR(36)       NOT NULL,
    tenant_id         CHAR(36)       NOT NULL,
    name              VARCHAR(100)   NOT NULL,
    description       TEXT           NULL,
    price             DECIMAL(12, 2) NOT NULL,
    currency          CHAR(3)        NOT NULL DEFAULT 'USD',
    billing_interval  ENUM('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY') NOT NULL,
    trial_days        SMALLINT       NOT NULL DEFAULT 0,
    grace_period_days SMALLINT       NOT NULL DEFAULT 3,
    max_retry_count   TINYINT        NOT NULL DEFAULT 3,
    is_active         TINYINT(1)     NOT NULL DEFAULT 1,
    created_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_plan PRIMARY KEY (id),
    CONSTRAINT fk_plan_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_plan_tenant_active ON plan (tenant_id, is_active);


-- =============================================================
-- SUBSCRIPTION
-- version column = JPA @Version optimistic locking
-- =============================================================
CREATE TABLE IF NOT EXISTS subscription (
    id                   CHAR(36)  NOT NULL,
    tenant_id            CHAR(36)  NOT NULL,
    customer_id          CHAR(36)  NOT NULL,
    plan_id              CHAR(36)  NOT NULL,
    status               ENUM('TRIALING','ACTIVE','PAST_DUE','PAUSED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'TRIALING',
    current_period_start TIMESTAMP NOT NULL,
    current_period_end   TIMESTAMP NOT NULL,
    trial_end            TIMESTAMP NULL,
    cancelled_at         TIMESTAMP NULL,
    cancel_at_period_end TINYINT(1) NOT NULL DEFAULT 0,
    retry_count          TINYINT   NOT NULL DEFAULT 0,
    next_retry_at        TIMESTAMP NULL,
    metadata             JSON      NULL,
    version              BIGINT    NOT NULL DEFAULT 0,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_subscription PRIMARY KEY (id),
    CONSTRAINT fk_sub_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenant(id),
    CONSTRAINT fk_sub_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_sub_plan     FOREIGN KEY (plan_id)     REFERENCES plan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Billing harvester hot query
CREATE INDEX idx_sub_billing_harvest ON subscription (tenant_id, status, current_period_end);
-- Customer portal query
CREATE INDEX idx_sub_customer ON subscription (customer_id, status);
-- Retry scheduler query (no partial index - MySQL 8.0 does not support it)
CREATE INDEX idx_sub_retry ON subscription (status, next_retry_at);


-- =============================================================
-- INVOICE
-- =============================================================
CREATE TABLE IF NOT EXISTS invoice (
    id              CHAR(36)       NOT NULL,
    tenant_id       CHAR(36)       NOT NULL,
    subscription_id CHAR(36)       NOT NULL,
    invoice_number  VARCHAR(30)    NOT NULL,
    amount_due      DECIMAL(12, 2) NOT NULL,
    amount_paid     DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    currency        CHAR(3)        NOT NULL DEFAULT 'USD',
    status          ENUM('DRAFT','OPEN','PAID','VOID','UNCOLLECTIBLE') NOT NULL DEFAULT 'DRAFT',
    due_date        DATE           NOT NULL,
    paid_at         TIMESTAMP      NULL,
    voided_at       TIMESTAMP      NULL,
    description     TEXT           NULL,
    period_start    TIMESTAMP      NOT NULL,
    period_end      TIMESTAMP      NOT NULL,
    metadata        JSON           NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_invoice PRIMARY KEY (id),
    CONSTRAINT fk_invoice_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_invoice_sub    FOREIGN KEY (subscription_id) REFERENCES subscription(id),
    CONSTRAINT uq_invoice_number UNIQUE (tenant_id, invoice_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_invoice_open         ON invoice (tenant_id, status, due_date);
CREATE INDEX idx_invoice_subscription ON invoice (subscription_id, status);


-- =============================================================
-- PAYMENT
-- =============================================================
CREATE TABLE IF NOT EXISTS payment (
    id              CHAR(36)       NOT NULL,
    tenant_id       CHAR(36)       NOT NULL,
    invoice_id      CHAR(36)       NOT NULL,
    amount          DECIMAL(12, 2) NOT NULL,
    currency        CHAR(3)        NOT NULL DEFAULT 'USD',
    status          ENUM('PENDING','PROCESSING','SUCCEEDED','FAILED','REFUNDED','PARTIALLY_REFUNDED') NOT NULL DEFAULT 'PENDING',
    gateway         VARCHAR(30)    NOT NULL,
    gateway_ref     VARCHAR(100)   NULL,
    failure_code    VARCHAR(50)    NULL,
    failure_message TEXT           NULL,
    refunded_amount DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    metadata        JSON           NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment PRIMARY KEY (id),
    CONSTRAINT fk_payment_tenant  FOREIGN KEY (tenant_id)  REFERENCES tenant(id),
    CONSTRAINT fk_payment_invoice FOREIGN KEY (invoice_id) REFERENCES invoice(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_payment_gateway_ref ON payment (gateway, gateway_ref);
CREATE INDEX idx_payment_invoice     ON payment (invoice_id, status);


-- =============================================================
-- WALLET
-- version = optimistic locking; SELECT FOR UPDATE for debits
-- =============================================================
CREATE TABLE IF NOT EXISTS wallet (
    id          CHAR(36)       NOT NULL,
    tenant_id   CHAR(36)       NOT NULL,
    customer_id CHAR(36)       NOT NULL,
    balance     DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    currency    CHAR(3)        NOT NULL DEFAULT 'USD',
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_wallet          PRIMARY KEY (id),
    CONSTRAINT fk_wallet_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenant(id),
    CONSTRAINT fk_wallet_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT uq_wallet_customer UNIQUE (customer_id),
    CONSTRAINT chk_wallet_balance CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- WALLET_TRANSACTION - append only ledger
-- =============================================================
CREATE TABLE IF NOT EXISTS wallet_transaction (
    id              CHAR(36)       NOT NULL,
    wallet_id       CHAR(36)       NOT NULL,
    tenant_id       CHAR(36)       NOT NULL,
    amount          DECIMAL(14, 2) NOT NULL,
    type            ENUM('CREDIT', 'DEBIT') NOT NULL,
    reference_type  VARCHAR(50)    NULL,
    reference_id    CHAR(36)       NULL,
    description     VARCHAR(255)   NOT NULL,
    running_balance DECIMAL(14, 2) NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_wallet_txn        PRIMARY KEY (id),
    CONSTRAINT fk_wallet_txn_wallet FOREIGN KEY (wallet_id) REFERENCES wallet(id),
    CONSTRAINT fk_wallet_txn_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_wallet_txn_wallet ON wallet_transaction (wallet_id, created_at);


-- =============================================================
-- IDEMPOTENCY_KEY
-- =============================================================
CREATE TABLE IF NOT EXISTS idempotency_key (
    id                CHAR(36)     NOT NULL,
    tenant_id         CHAR(36)     NOT NULL,
    key_hash          CHAR(64)     NOT NULL,
    operation         VARCHAR(100) NOT NULL,
    http_status       SMALLINT     NULL,
    response_snapshot JSON         NULL,
    request_hash      CHAR(64)     NULL,
    expires_at        TIMESTAMP    NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_idempotency        PRIMARY KEY (id),
    CONSTRAINT fk_idempotency_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT uq_idempotency_key    UNIQUE (key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_idempotency_expires ON idempotency_key (expires_at);


-- =============================================================
-- OUTBOX_EVENT - Transactional Outbox Pattern
-- =============================================================
CREATE TABLE IF NOT EXISTS outbox_event (
    id             CHAR(36)     NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   CHAR(36)     NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    kafka_topic    VARCHAR(200) NOT NULL,
    payload        JSON         NOT NULL,
    published      TINYINT(1)   NOT NULL DEFAULT 0,
    published_at   TIMESTAMP    NULL,
    retry_count    TINYINT      NOT NULL DEFAULT 0,
    error_message  TEXT         NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_outbox PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Relay poller hot query
CREATE INDEX idx_outbox_unpublished ON outbox_event (published, created_at);


-- =============================================================
-- AUDIT_LOG - append only
-- =============================================================
CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id      CHAR(36)      NOT NULL,
    actor_id       CHAR(36)      NULL,
    actor_type     VARCHAR(30)   NOT NULL,
    aggregate_type VARCHAR(50)   NOT NULL,
    aggregate_id   CHAR(36)      NOT NULL,
    action         VARCHAR(100)  NOT NULL,
    old_state      JSON          NULL,
    new_state      JSON          NULL,
    ip_address     VARCHAR(45)   NULL,
    trace_id       VARCHAR(64)   NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_audit_aggregate ON audit_log (tenant_id, aggregate_type, aggregate_id, created_at);
CREATE INDEX idx_audit_actor     ON audit_log (actor_id, created_at);


-- =============================================================
-- PLAN_FEATURE
-- =============================================================
CREATE TABLE IF NOT EXISTS plan_feature (
    id            CHAR(36)     NOT NULL,
    plan_id       CHAR(36)     NOT NULL,
    feature_key   VARCHAR(100) NOT NULL,
    feature_value VARCHAR(200) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_plan_feature PRIMARY KEY (id),
    CONSTRAINT fk_pf_plan      FOREIGN KEY (plan_id) REFERENCES plan(id),
    CONSTRAINT uq_plan_feature UNIQUE (plan_id, feature_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


SET foreign_key_checks = 1;

-- =============================================================
-- SEED: default tenant for local development
-- =============================================================
INSERT IGNORE INTO tenant (id, name, domain, api_key_hash, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'PayCycle Dev Tenant',
    'dev.paycycle.local',
    'e1b849f2a1bb6d2c5a15a83c2f3d97ec91d64bbe3be0e48c7f20f0b73f4e52a1',
    1
);
