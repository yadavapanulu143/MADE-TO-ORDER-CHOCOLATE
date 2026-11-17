-- Notification service schema.
--
-- Owned exclusively by the notification service. No other service reads or writes these tables. It
-- holds three things: the notification log, the local customer-contact projection, and the audit
-- trail for administrative resends.
--
-- Nothing here is a source of truth for the business. An order lives in the order service, a payment
-- in the payment service, a customer in the identity service. This service observes their events and
-- records what it sent about them; every table below could be rebuilt from the event stream, and none
-- of it is consulted to decide whether an order is paid or a delivery happened.

CREATE TABLE notifications (
    id             UUID         PRIMARY KEY,
    type           VARCHAR(48)  NOT NULL,
    channel        VARCHAR(16)  NOT NULL,
    recipient_type VARCHAR(16)  NOT NULL,
    -- The resolved destination (an email address or a mobile number). Stored because a retry must send
    -- to the address the message was written for, not one the customer changed afterwards. The
    -- administrative API masks it; it is never returned in full.
    recipient      VARCHAR(320) NOT NULL,
    subject        VARCHAR(300),
    -- The rendered message, stored as sent. A later template change cannot rewrite what a customer
    -- already received, and a retry re-sends exactly this.
    body           VARCHAR(4000) NOT NULL,

    -- The idempotency guarantee. A key derived from the event that produced this message; the unique
    -- index below is what makes a redelivered event unable to create a second notification, even when
    -- two consumers race past the application's own lookup.
    dedupe_key     VARCHAR(160) NOT NULL,
    -- The order, payment or delivery this message is about, for the seller to correlate.
    reference_id   VARCHAR(64),

    status         VARCHAR(16)  NOT NULL,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    -- A short, provider-neutral failure summary. Never the provider's raw response, which can echo the
    -- destination address back.
    last_error     VARCHAR(300),
    sent_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,

    -- Optimistic locking, so the retry scheduler and an immediate send cannot both advance the same
    -- row and lose one outcome.
    version        BIGINT       NOT NULL DEFAULT 0
);

-- The uniqueness that enforces "one message per event". Not a plain index: the constraint is the
-- point, and the database is the only place it can be enforced under concurrency.
CREATE UNIQUE INDEX notifications_dedupe_key ON notifications (dedupe_key);

-- The retry scheduler's query: pending notifications whose last update is old enough to try again,
-- oldest first. Partial, because the pending set is small and the sent set grows without bound.
CREATE INDEX notifications_pending
    ON notifications (updated_at)
    WHERE status = 'PENDING';

-- The seller's log filters by status and type, newest first.
CREATE INDEX notifications_status_created ON notifications (status, created_at DESC);
CREATE INDEX notifications_type_created ON notifications (type, created_at DESC);

-- Customer-contact projection. A local, derived copy of where each customer can be reached, fed by
-- the identity service's events. It exists so the delivery path has no synchronous dependency on the
-- identity service: a message can go out even while identity is briefly unavailable.
--
-- The primary key is the identity service's customer id, not a value generated here.
CREATE TABLE customer_contacts (
    customer_id UUID         PRIMARY KEY,
    email       VARCHAR(254),
    mobile      VARCHAR(20),
    full_name   VARCHAR(120),
    updated_at  TIMESTAMPTZ  NOT NULL
);

-- Notification-service audit trail. Records the administrative acts on the log — a resend — so an
-- operator can answer "who re-sent this, and when". Only identifiers are stored.
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

-- Transactional outbox. This service is mostly a consumer, but it still publishes two events of its
-- own: a notification was sent, or it was permanently failed. Those are written here in the same
-- transaction as the status change, and the shared relay publishes them afterwards, so a broker
-- outage delays the seller's alert but never loses it and never rolls back a delivery outcome.
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

