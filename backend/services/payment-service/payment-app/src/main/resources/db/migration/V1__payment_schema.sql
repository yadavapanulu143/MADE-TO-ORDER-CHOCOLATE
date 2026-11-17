-- Payment service schema.
--
-- Owned exclusively by the payment service. No other service reads or writes these tables. The
-- amount charged is copied from the order at initiation and never joined back, so a later order or
-- catalogue change cannot rewrite what the provider was asked to collect.
--
-- Every monetary column is a BIGINT count of paise. No column is a floating-point type. No column
-- holds a card number, a CVV, a UPI PIN or a bank credential: the provider's own checkout collects
-- those and this service only ever stores identifiers and a coarse method classification.

CREATE TABLE payments (
    id                  UUID         PRIMARY KEY,
    -- The order this payment settles. Unique, because one order has at most one payment: a retry
    -- reuses the same payment and its provider order rather than creating a second charge.
    order_id            UUID         NOT NULL,
    customer_id         UUID         NOT NULL,

    amount_minor_units  BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL DEFAULT 'INR',
    -- The provider receipt, derived from the order, so a payment can be traced from the provider's
    -- dashboard back to the order without a join.
    receipt             VARCHAR(64),

    provider            VARCHAR(32)  NOT NULL,
    -- The provider's order reference. Unique: two local payments must never share a provider order,
    -- which would make webhook routing ambiguous.
    provider_order_id   VARCHAR(64)  NOT NULL,
    -- The provider's payment reference, set once a payment attempt is reported. Nullable because it
    -- does not exist until the customer actually attempts to pay.
    provider_payment_id VARCHAR(64),

    status              VARCHAR(24)  NOT NULL,
    -- Coarse classification only: UPI, CARD, NETBANKING and so on. Never a card number or a UPI id.
    method              VARCHAR(24),
    failure_reason      VARCHAR(500),

    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    -- Optimistic locking: a webhook and a browser verification arriving together cannot both write
    -- and silently lose one of the two facts.
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT payments_amount_positive CHECK (amount_minor_units > 0),
    CONSTRAINT payments_currency_inr CHECK (currency = 'INR')
);

-- One payment per order, enforced by the database rather than by application code alone, so a race
-- between two initiation calls cannot create two payments for one order.
CREATE UNIQUE INDEX payments_order_unique ON payments (order_id);
-- The provider's order id is how a webhook finds its payment; unique so the lookup is unambiguous.
CREATE UNIQUE INDEX payments_provider_order_unique ON payments (provider_order_id);
-- The provider's payment id is the idempotency key for a repeated capture webhook.
CREATE UNIQUE INDEX payments_provider_payment_unique
    ON payments (provider_payment_id) WHERE provider_payment_id IS NOT NULL;
-- The customer's payment history: newest first, scoped to the owner.
CREATE INDEX payments_customer ON payments (customer_id, created_at DESC);
-- The seller's dashboard filters by status.
CREATE INDEX payments_status ON payments (status, created_at DESC);

-- Every attempt against a payment. A customer may fail a UPI collect and then pay by card against
-- the same provider order; each attempt is kept so the seller can see what actually happened.
CREATE TABLE payment_attempts (
    id                  UUID         PRIMARY KEY,
    payment_id          UUID         NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    provider_payment_id VARCHAR(64),
    amount_minor_units  BIGINT       NOT NULL,
    method              VARCHAR(24),
    outcome             VARCHAR(24)  NOT NULL,
    failure_reason      VARCHAR(500),
    attempted_at        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT payment_attempts_amount_positive CHECK (amount_minor_units > 0)
);

CREATE INDEX payment_attempts_payment ON payment_attempts (payment_id, attempted_at);

-- Refunds against a payment. The amount is always positive in the customer's favour; the direction
-- is carried by the fact that this is a refund, not by a sign.
CREATE TABLE refunds (
    id                 UUID         PRIMARY KEY,
    payment_id         UUID         NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    amount_minor_units BIGINT       NOT NULL,
    currency           VARCHAR(3)   NOT NULL DEFAULT 'INR',
    status             VARCHAR(16)  NOT NULL,
    reason             VARCHAR(500),
    requested_by       VARCHAR(64),
    provider_refund_id VARCHAR(64),
    failure_reason     VARCHAR(500),
    requested_at       TIMESTAMPTZ  NOT NULL,
    processed_at       TIMESTAMPTZ,

    CONSTRAINT refunds_amount_positive CHECK (amount_minor_units > 0),
    CONSTRAINT refunds_currency_inr CHECK (currency = 'INR')
);

CREATE INDEX refunds_payment ON refunds (payment_id, requested_at);
-- A refund the provider reports is matched by its own id, so it is unique when present.
CREATE UNIQUE INDEX refunds_provider_refund_unique
    ON refunds (provider_refund_id) WHERE provider_refund_id IS NOT NULL;

-- Provider disputes and chargebacks. A record of a fact the provider owns, kept so the seller is
-- told and so the money stops being treated as safely settled while a dispute is open.
CREATE TABLE disputes (
    id                  UUID         PRIMARY KEY,
    payment_id          UUID         NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    provider_dispute_id VARCHAR(64),
    amount_minor_units  BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL DEFAULT 'INR',
    reason              VARCHAR(500),
    status              VARCHAR(24)  NOT NULL,
    raised_at           TIMESTAMPTZ  NOT NULL,
    resolved_at         TIMESTAMPTZ,

    CONSTRAINT disputes_amount_positive CHECK (amount_minor_units > 0),
    CONSTRAINT disputes_currency_inr CHECK (currency = 'INR')
);

CREATE INDEX disputes_payment ON disputes (payment_id, raised_at);
CREATE UNIQUE INDEX disputes_provider_dispute_unique
    ON disputes (provider_dispute_id) WHERE provider_dispute_id IS NOT NULL;

-- Idempotency records. Creating a provider order is the call a client retries after a timeout, and
-- it must not produce two provider orders. The key is unique per scope.
CREATE TABLE idempotency_record (
    id                  UUID         PRIMARY KEY,
    scope               VARCHAR(64)  NOT NULL,
    idempotency_key     VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(128),
    resource_id         VARCHAR(64),
    response_body       VARCHAR(4000),
    response_status     INTEGER      NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,

    CONSTRAINT idempotency_scope_key_unique UNIQUE (scope, idempotency_key)
);

-- The cleanup job deletes expired rows; the index keeps that scan off the live set.
CREATE INDEX idempotency_expires_at ON idempotency_record (expires_at);

-- Transactional outbox, shared shape across every service. Table name and columns must match
-- com.choco.events.outbox.OutboxEvent.
CREATE TABLE outbox_event (
    id             UUID         PRIMARY KEY,
    event_type     VARCHAR(120) NOT NULL,
    aggregate_id   VARCHAR(120) NOT NULL,
    schema_version INTEGER      NOT NULL DEFAULT 1,
    payload        TEXT         NOT NULL,
    correlation_id VARCHAR(120),
    occurred_at    TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_event (created_at)
    WHERE published_at IS NULL;

-- Payment audit trail. Records the money-moving acts — refunds, disputes — so an operator can answer
-- "who did this" without reading the payment tables. Only identifiers are stored.
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

CREATE INDEX payment_audit_log_occurred_at ON audit_log (occurred_at DESC);
CREATE INDEX payment_audit_log_actor ON audit_log (actor_type, actor_id, occurred_at DESC);
CREATE INDEX payment_audit_log_action ON audit_log (action, occurred_at DESC);
