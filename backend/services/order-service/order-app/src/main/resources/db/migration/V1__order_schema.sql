-- Order service schema.
--
-- Owned exclusively by the order service. No other service reads or writes these tables. The
-- catalogue's price for a variant is copied into order_lines at checkout and never joined back, so
-- a later price change in the catalogue cannot rewrite what a customer already agreed to pay.
--
-- Every monetary column is a BIGINT count of paise. No column is a floating-point type, so a stored
-- total is exact and re-reading it cannot drift.

CREATE TABLE orders (
    id                        UUID         PRIMARY KEY,
    -- Human-readable reference shown to the customer. Unique so it can be searched unambiguously;
    -- it is a convenience for support, and the id remains the identifier everything keys on.
    order_number              VARCHAR(40)  NOT NULL,
    customer_id               UUID         NOT NULL,

    -- The customer's contact details, frozen at purchase. Copied rather than referenced so an order
    -- can still be rendered, notified about and invoiced if the account is later changed or closed.
    customer_name             VARCHAR(150) NOT NULL,
    customer_email            VARCHAR(254) NOT NULL,
    customer_mobile           VARCHAR(20)  NOT NULL,

    -- The delivery address, frozen at purchase. The seller can edit their address book afterwards
    -- without altering where a past order was actually sent.
    address_recipient_name    VARCHAR(150) NOT NULL,
    address_mobile            VARCHAR(20)  NOT NULL,
    address_line1             VARCHAR(200) NOT NULL,
    address_line2             VARCHAR(200),
    address_city              VARCHAR(100) NOT NULL,
    address_state             VARCHAR(100) NOT NULL,
    address_pincode           VARCHAR(6)   NOT NULL,
    address_instructions      VARCHAR(500),

    items_total_minor_units   BIGINT       NOT NULL,
    delivery_charge_minor_units BIGINT     NOT NULL,
    total_minor_units         BIGINT       NOT NULL,
    currency                  VARCHAR(3)   NOT NULL DEFAULT 'INR',

    status                    VARCHAR(32)  NOT NULL,
    payment_status            VARCHAR(24)  NOT NULL,
    -- Null until the order is confirmed and enters preparation. Kept separate from status so "paid,
    -- not yet started" is representable.
    preparation_status        VARCHAR(24),
    delivery_status           VARCHAR(24)  NOT NULL,

    -- The payment service's identifiers. Never set from a browser redirect; only a verified
    -- payment.captured event moves the order to a paid state.
    payment_id                VARCHAR(64),
    provider_order_id         VARCHAR(64),

    delivery_person_name      VARCHAR(150),
    delivery_person_phone     VARCHAR(20),

    dispatched_at             TIMESTAMPTZ,
    delivered_at              TIMESTAMPTZ,

    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL,
    -- Optimistic locking: two concurrent status updates on one order cannot silently overwrite.
    version                   BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT orders_number_not_blank CHECK (length(trim(order_number)) > 0),
    CONSTRAINT orders_totals_non_negative CHECK (
        items_total_minor_units >= 0 AND delivery_charge_minor_units >= 0 AND total_minor_units >= 0),
    -- The stored total must equal its parts, so a total can never drift from what was quoted.
    CONSTRAINT orders_total_is_sum CHECK (
        total_minor_units = items_total_minor_units + delivery_charge_minor_units),
    CONSTRAINT orders_currency_inr CHECK (currency = 'INR')
);

CREATE UNIQUE INDEX orders_number_unique ON orders (order_number);
-- The customer's order history: newest first, scoped to the owner.
CREATE INDEX orders_customer ON orders (customer_id, created_at DESC);
-- The seller's dashboard filters by status; a partial-per-status scan stays small.
CREATE INDEX orders_status ON orders (status, created_at DESC);
-- The payment service reconciles by its own identifiers, so they are indexed for lookup.
CREATE INDEX orders_payment_id ON orders (payment_id) WHERE payment_id IS NOT NULL;

CREATE TABLE order_lines (
    id                    UUID         PRIMARY KEY,
    order_id              UUID         NOT NULL,
    product_id            UUID         NOT NULL,
    variant_id            UUID         NOT NULL,
    -- Snapshot of the catalogue at purchase time. Never re-read.
    product_name          VARCHAR(150) NOT NULL,
    variant_label         VARCHAR(80)  NOT NULL,
    sku                   VARCHAR(64),
    unit_price_minor_units BIGINT      NOT NULL,
    currency              VARCHAR(3)   NOT NULL DEFAULT 'INR',
    quantity              INTEGER      NOT NULL,
    display_order         INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT order_lines_order_fk
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT order_lines_name_not_blank CHECK (length(trim(product_name)) > 0),
    CONSTRAINT order_lines_price_positive CHECK (unit_price_minor_units > 0),
    CONSTRAINT order_lines_quantity_positive CHECK (quantity > 0),
    -- One row per variant within an order: the same variant added twice is one line with a larger
    -- quantity, so a duplicate row would be a bug.
    CONSTRAINT order_lines_variant_unique UNIQUE (order_id, variant_id)
);

CREATE INDEX order_lines_order ON order_lines (order_id);

CREATE TABLE order_status_history (
    id           UUID        PRIMARY KEY,
    order_id     UUID        NOT NULL,
    from_status  VARCHAR(32),
    to_status    VARCHAR(32) NOT NULL,
    reason       VARCHAR(500),
    actor_id     VARCHAR(64),
    occurred_at  TIMESTAMPTZ NOT NULL,
    display_order INTEGER    NOT NULL DEFAULT 0,

    CONSTRAINT order_status_history_order_fk
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX order_status_history_order ON order_status_history (order_id, occurred_at);

CREATE TABLE order_notes (
    id         UUID         PRIMARY KEY,
    order_id   UUID         NOT NULL,
    text       VARCHAR(1000) NOT NULL,
    author_id  VARCHAR(64),
    created_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT order_notes_order_fk
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT order_notes_text_not_blank CHECK (length(trim(text)) > 0)
);

CREATE INDEX order_notes_order ON order_notes (order_id, created_at);

CREATE TABLE carts (
    id          UUID        PRIMARY KEY,
    -- One active cart per customer, enforced by the database so no code path can create a second.
    customer_id UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT carts_customer_unique UNIQUE (customer_id)
);

CREATE TABLE cart_lines (
    id                     UUID        PRIMARY KEY,
    cart_id                UUID        NOT NULL,
    product_id             UUID        NOT NULL,
    variant_id             UUID        NOT NULL,
    product_name           VARCHAR(150) NOT NULL,
    variant_label          VARCHAR(80) NOT NULL,
    unit_price_minor_units BIGINT      NOT NULL,
    currency               VARCHAR(3)  NOT NULL DEFAULT 'INR',
    quantity               INTEGER     NOT NULL,
    display_order          INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT cart_lines_cart_fk
        FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT cart_lines_quantity_positive CHECK (quantity > 0),
    CONSTRAINT cart_lines_variant_unique UNIQUE (cart_id, variant_id)
);

CREATE INDEX cart_lines_cart ON cart_lines (cart_id);

-- Idempotency records for requests that must not happen twice: placing an order and initiating a
-- payment. Scoped by operation as well as key, so the same key used for two operations does not
-- collide. The unique constraint is what makes a concurrent double-submit safe: the second insert
-- fails rather than creating a second order.
CREATE TABLE idempotency_record (
    id                  UUID         PRIMARY KEY,
    scope               VARCHAR(40)  NOT NULL,
    key                 VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(255),
    resource_id         VARCHAR(64),
    response_body       TEXT,
    response_status     INTEGER,
    created_at          TIMESTAMPTZ  NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,

    CONSTRAINT idempotency_scope_key_unique UNIQUE (scope, key),
    CONSTRAINT idempotency_expiry_after_creation CHECK (expires_at > created_at)
);

-- The cleanup job deletes expired rows; the index keeps that scan off the live set.
CREATE INDEX idempotency_expires_at ON idempotency_record (expires_at);

-- Transactional outbox, shared shape across every service. The relay publishes rows after the
-- business transaction commits, so a broker outage delays events without losing them.
-- Table name and columns must match com.choco.events.outbox.OutboxEvent.
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

-- Order audit trail. A separate table from every other service's, because each service owns its
-- data. Records the administrative acts around an order — cancellations, refund decisions, delivery
-- assignments — so an operator can answer "who did this" without reading the order tables.
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

CREATE INDEX order_audit_log_occurred_at ON audit_log (occurred_at DESC);
CREATE INDEX order_audit_log_actor ON audit_log (actor_type, actor_id, occurred_at DESC);
CREATE INDEX order_audit_log_action ON audit_log (action, occurred_at DESC);

-- Source of the human-readable order number. A sequence rather than a count of rows, so two
-- concurrent checkouts cannot be handed the same number and a deleted order does not free its
-- number for reuse. The number is a support convenience; the order id remains the real identifier.
CREATE SEQUENCE order_number_seq START WITH 1000 INCREMENT BY 1;
