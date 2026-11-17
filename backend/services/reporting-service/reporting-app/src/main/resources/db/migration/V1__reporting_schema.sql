-- Reporting service schema.
--
-- Owned exclusively by the reporting service. No other service reads or writes these tables, and the
-- reporting service writes nothing any other service reads. Every table here is a derived read model
-- or an audit trail: it could be rebuilt by replaying the event stream, and none of it is consulted to
-- decide whether an order is paid or a delivery happened.
--
-- The projections are keyed on the identifier the owning service generated, not a value created here.
-- That is what makes applying an event twice an update rather than a second row — the idempotency the
-- consumer relies on, enforced by the primary key rather than by a check the application could forget.

CREATE TABLE order_projection (
    order_id                     VARCHAR(64)  PRIMARY KEY,
    order_number                 VARCHAR(40),
    customer_id                  VARCHAR(64),
    status                       VARCHAR(32)  NOT NULL,
    total_minor_units            BIGINT       NOT NULL,
    delivery_charge_minor_units  BIGINT       NOT NULL DEFAULT 0,
    currency                     VARCHAR(3)   NOT NULL,
    placed_at                    TIMESTAMPTZ  NOT NULL,
    confirmed_at                 TIMESTAMPTZ,
    cancelled_at                 TIMESTAMPTZ,
    cancellation_reason          VARCHAR(300)
);

-- The report's range query and its ordering both go through placed_at, so the index is on that column
-- descending; a report almost always wants the most recent window first.
CREATE INDEX order_projection_placed_at ON order_projection (placed_at DESC);
CREATE INDEX order_projection_customer ON order_projection (customer_id);

CREATE TABLE payment_projection (
    payment_id          VARCHAR(64)  PRIMARY KEY,
    order_id            VARCHAR(64),
    customer_id         VARCHAR(64),
    status              VARCHAR(32)  NOT NULL,
    amount_minor_units  BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    method              VARCHAR(32),
    failure_reason      VARCHAR(300),
    occurred_at         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX payment_projection_occurred_at ON payment_projection (occurred_at DESC);
CREATE INDEX payment_projection_order ON payment_projection (order_id);

CREATE TABLE production_projection (
    job_id         VARCHAR(64)  PRIMARY KEY,
    order_id       VARCHAR(64),
    order_number   VARCHAR(40),
    customer_id    VARCHAR(64),
    variant_label  VARCHAR(120),
    quantity       INTEGER      NOT NULL DEFAULT 0,
    status         VARCHAR(32)  NOT NULL,
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ
);

CREATE INDEX production_projection_started_at ON production_projection (started_at DESC);
CREATE INDEX production_projection_order ON production_projection (order_id);

CREATE TABLE delivery_projection (
    delivery_id          VARCHAR(64)  PRIMARY KEY,
    order_id             VARCHAR(64),
    order_number         VARCHAR(40),
    customer_id          VARCHAR(64),
    status               VARCHAR(32)  NOT NULL,
    delivery_person_name VARCHAR(120),
    failure_reason       VARCHAR(300),
    attempt_number       INTEGER      NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX delivery_projection_updated_at ON delivery_projection (updated_at DESC);
CREATE INDEX delivery_projection_order ON delivery_projection (order_id);

CREATE TABLE refund_projection (
    refund_id           VARCHAR(64)  PRIMARY KEY,
    order_id            VARCHAR(64),
    order_number        VARCHAR(40),
    customer_id         VARCHAR(64),
    amount_minor_units  BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    reason              VARCHAR(300),
    full_refund         BOOLEAN      NOT NULL DEFAULT false,
    requested_at        TIMESTAMPTZ,
    processed_at        TIMESTAMPTZ
);

CREATE INDEX refund_projection_requested_at ON refund_projection (requested_at DESC);
CREATE INDEX refund_projection_order ON refund_projection (order_id);

-- Customer read model. There is deliberately no column for a password, a hash, an OTP or a token: the
-- table records who a customer is for reporting, and nothing that could be used to authenticate as
-- them. The email and mobile are masked before any export.
CREATE TABLE customer_projection (
    customer_id      VARCHAR(64)  PRIMARY KEY,
    full_name        VARCHAR(150),
    email            VARCHAR(254),
    mobile           VARCHAR(20),
    email_verified   BOOLEAN      NOT NULL DEFAULT false,
    mobile_verified  BOOLEAN      NOT NULL DEFAULT false,
    registered_at    TIMESTAMPTZ  NOT NULL,
    verified_at      TIMESTAMPTZ
);

CREATE INDEX customer_projection_registered_at ON customer_projection (registered_at DESC);

CREATE TABLE invoice_projection (
    invoice_id          VARCHAR(64)  PRIMARY KEY,
    order_id            VARCHAR(64),
    order_number        VARCHAR(40),
    invoice_number      VARCHAR(40),
    customer_id         VARCHAR(64),
    total_minor_units   BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    issued_at           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX invoice_projection_issued_at ON invoice_projection (issued_at DESC);
CREATE INDEX invoice_projection_order ON invoice_projection (order_id);

-- Reporting-service audit trail. Records who pulled which dataset out of the platform, so an export —
-- a security-relevant act — is attributable after the fact and survives log rotation. Only
-- identifiers and a row count are stored, never the exported content.
CREATE TABLE audit_log (
    id             BIGSERIAL    PRIMARY KEY,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    actor_type     VARCHAR(32)  NOT NULL,
    actor_id       VARCHAR(64),
    action         VARCHAR(64)  NOT NULL,
    target_type    VARCHAR(32),
    target_id      VARCHAR(64),
    outcome        VARCHAR(16)  NOT NULL,
    detail         VARCHAR(512),
    correlation_id VARCHAR(64)
);

CREATE INDEX audit_log_occurred_at ON audit_log (occurred_at DESC);
CREATE INDEX audit_log_actor ON audit_log (actor_id, occurred_at DESC);

-- Transactional outbox. The reporting service publishes no events of its own, but the shared outbox
-- relay is on the classpath and its scheduler queries this table on a timer; without it the service
-- would log a failing pass every couple of seconds. Keeping the table here — empty and unused — matches
-- every other service's schema and lets a future reporting event be published with no migration.
CREATE TABLE outbox_event (
    id              UUID         PRIMARY KEY,
    event_type      VARCHAR(120) NOT NULL,
    aggregate_id    VARCHAR(120) NOT NULL,
    schema_version  INTEGER      NOT NULL DEFAULT 1,
    payload         TEXT         NOT NULL,
    correlation_id  VARCHAR(120),
    occurred_at     TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_error      VARCHAR(500)
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_event (created_at)
    WHERE published_at IS NULL;
