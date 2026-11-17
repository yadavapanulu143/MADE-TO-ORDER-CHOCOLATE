-- Fulfilment service schema.
--
-- Owned exclusively by the fulfilment service. No other service reads or writes these tables. It
-- holds the made-to-order kitchen (production jobs), the optional ingredient stock, the seller's
-- delivery coverage, and the deliveries themselves.
--
-- Every monetary column is a BIGINT count of paise. Stock amounts are BIGINT thousandths of the
-- ingredient's unit, so grams, millilitres and pieces are all exact and no column is a float.
--
-- The delivery address and charge are copied onto the production job when the order is confirmed,
-- and copied again onto the delivery when it is opened. They are never read back from the order
-- service: the fulfilment service must be able to make and deliver exactly what was agreed even if
-- the order service is briefly unavailable or the customer later edits their address book.

CREATE TABLE production_jobs (
    id                        UUID         PRIMARY KEY,
    order_id                  UUID         NOT NULL,
    order_number              VARCHAR(40)  NOT NULL,
    customer_id               UUID         NOT NULL,
    product_id                UUID         NOT NULL,
    variant_id                UUID         NOT NULL,
    variant_label             VARCHAR(80)  NOT NULL,
    quantity                  INTEGER      NOT NULL,

    -- The delivery details frozen at checkout, carried so the delivery can be opened later without a
    -- synchronous call back into the order service.
    address_recipient_name    VARCHAR(150),
    address_mobile            VARCHAR(20),
    address_line1             VARCHAR(200),
    address_line2             VARCHAR(200),
    address_city              VARCHAR(100),
    address_state             VARCHAR(100),
    address_pincode           VARCHAR(6),
    address_instructions      VARCHAR(500),
    delivery_charge_minor_units BIGINT     NOT NULL DEFAULT 0,
    currency                  VARCHAR(3)   NOT NULL DEFAULT 'INR',

    status                    VARCHAR(24)  NOT NULL,
    hold_reason               VARCHAR(500),
    -- Whether the recipe's ingredients have already been taken out of stock for this job. Held here
    -- so a restart after a hold does not consume twice, and a cancellation can return exactly once.
    ingredients_consumed      BOOLEAN      NOT NULL DEFAULT FALSE,

    received_at               TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL,
    version                   BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT production_jobs_quantity_positive CHECK (quantity > 0),
    CONSTRAINT production_jobs_charge_non_negative CHECK (delivery_charge_minor_units >= 0),
    CONSTRAINT production_jobs_currency_inr CHECK (currency = 'INR'),
    -- One job per order line: the same variant on one order is a single job, so a duplicate would be
    -- a bug. This is also the dedupe key for a redelivered order.confirmed event.
    CONSTRAINT production_jobs_order_variant_unique UNIQUE (order_id, variant_id)
);

-- The kitchen queue: jobs by status, oldest first.
CREATE INDEX production_jobs_status ON production_jobs (status, received_at);
CREATE INDEX production_jobs_order ON production_jobs (order_id);

CREATE TABLE production_notes (
    id           UUID         PRIMARY KEY,
    job_id       UUID         NOT NULL,
    text         VARCHAR(500) NOT NULL,
    author_id    VARCHAR(64),
    created_at   TIMESTAMPTZ  NOT NULL,
    display_order INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT production_notes_job_fk
        FOREIGN KEY (job_id) REFERENCES production_jobs (id) ON DELETE CASCADE,
    CONSTRAINT production_notes_text_not_blank CHECK (length(trim(text)) > 0)
);

CREATE INDEX production_notes_job ON production_notes (job_id, created_at);

CREATE TABLE ingredients (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    unit          VARCHAR(16)  NOT NULL,
    -- Thousandths of the unit, so grams and millilitres are exact without a float.
    on_hand_milli_units   BIGINT NOT NULL DEFAULT 0,
    minimum_milli_units   BIGINT NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ingredients_name_not_blank CHECK (length(trim(name)) > 0),
    -- A negative level is not a real state; the aggregate already refuses it, and the constraint
    -- makes it impossible even if a future code path forgets.
    CONSTRAINT ingredients_on_hand_non_negative CHECK (on_hand_milli_units >= 0),
    CONSTRAINT ingredients_minimum_non_negative CHECK (minimum_milli_units >= 0)
);

-- Names are compared case-insensitively for the "already exists" check, so the index is too.
CREATE UNIQUE INDEX ingredients_name_unique ON ingredients (lower(name));

CREATE TABLE recipe_lines (
    id                    UUID   PRIMARY KEY,
    variant_id            UUID   NOT NULL,
    ingredient_id         UUID   NOT NULL,
    -- Thousandths of the unit consumed per one unit of the variant.
    per_unit_milli_units  BIGINT NOT NULL,

    CONSTRAINT recipe_lines_ingredient_fk
        FOREIGN KEY (ingredient_id) REFERENCES ingredients (id),
    CONSTRAINT recipe_lines_variant_unique UNIQUE (variant_id, ingredient_id),
    CONSTRAINT recipe_lines_per_unit_positive CHECK (per_unit_milli_units > 0)
);

CREATE INDEX recipe_lines_variant ON recipe_lines (variant_id);

-- Append-only stock history. Never updated or deleted: its whole purpose is to answer "why is the
-- level where it is", so a mutable entry would defeat it.
CREATE TABLE stock_ledger_entries (
    id                UUID        PRIMARY KEY,
    ingredient_id     UUID        NOT NULL,
    reason            VARCHAR(16) NOT NULL,
    -- Signed: a receipt is positive, a consumption negative.
    delta_milli_units BIGINT      NOT NULL,
    balance_after_milli_units BIGINT NOT NULL,
    production_job_id UUID,
    note              VARCHAR(500),
    occurred_at       TIMESTAMPTZ NOT NULL,
    display_order     BIGSERIAL,

    CONSTRAINT stock_ledger_ingredient_fk
        FOREIGN KEY (ingredient_id) REFERENCES ingredients (id),
    CONSTRAINT stock_ledger_delta_non_zero CHECK (delta_milli_units <> 0),
    CONSTRAINT stock_ledger_balance_non_negative CHECK (balance_after_milli_units >= 0)
);

CREATE INDEX stock_ledger_ingredient ON stock_ledger_entries (ingredient_id, occurred_at DESC);

CREATE TABLE serviceable_areas (
    id                         UUID         PRIMARY KEY,
    -- A six-digit PIN code or a prefix of one. Longest prefix wins, so an exact PIN overrides the
    -- city-wide entry that contains it.
    pincode_prefix             VARCHAR(6)   NOT NULL,
    area_name                  VARCHAR(120) NOT NULL,
    delivery_charge_minor_units BIGINT      NOT NULL DEFAULT 0,
    currency                   VARCHAR(3)   NOT NULL DEFAULT 'INR',
    active                     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                 TIMESTAMPTZ  NOT NULL,
    updated_at                 TIMESTAMPTZ  NOT NULL,
    version                    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT serviceable_areas_prefix_format CHECK (pincode_prefix ~ '^[1-9][0-9]{0,5}$'),
    CONSTRAINT serviceable_areas_name_not_blank CHECK (length(trim(area_name)) > 0),
    CONSTRAINT serviceable_areas_charge_non_negative CHECK (delivery_charge_minor_units >= 0),
    CONSTRAINT serviceable_areas_currency_inr CHECK (currency = 'INR')
);

CREATE UNIQUE INDEX serviceable_areas_prefix_unique ON serviceable_areas (pincode_prefix);

CREATE TABLE deliveries (
    id                        UUID         PRIMARY KEY,
    order_id                  UUID         NOT NULL,
    order_number              VARCHAR(40)  NOT NULL,
    customer_id               UUID         NOT NULL,

    address_recipient_name    VARCHAR(150) NOT NULL,
    address_mobile            VARCHAR(20)  NOT NULL,
    address_line1             VARCHAR(200) NOT NULL,
    address_line2             VARCHAR(200),
    address_city              VARCHAR(100) NOT NULL,
    address_state             VARCHAR(100) NOT NULL,
    address_pincode           VARCHAR(6)   NOT NULL,
    address_instructions      VARCHAR(500),

    delivery_charge_minor_units BIGINT     NOT NULL DEFAULT 0,
    currency                  VARCHAR(3)   NOT NULL DEFAULT 'INR',

    status                    VARCHAR(24)  NOT NULL,
    delivery_person_name      VARCHAR(120),
    delivery_person_phone     VARCHAR(20),
    dispatched_at             TIMESTAMPTZ,
    delivered_at              TIMESTAMPTZ,
    -- A reference to proof of delivery when the seller has turned that setting on. Never a credential.
    proof_reference           VARCHAR(500),

    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL,
    version                   BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT deliveries_charge_non_negative CHECK (delivery_charge_minor_units >= 0),
    CONSTRAINT deliveries_currency_inr CHECK (currency = 'INR')
);

-- One delivery per order.
CREATE UNIQUE INDEX deliveries_order_unique ON deliveries (order_id);
CREATE INDEX deliveries_status ON deliveries (status, created_at);
CREATE INDEX deliveries_customer ON deliveries (customer_id, created_at DESC);

CREATE TABLE delivery_attempts (
    id             UUID         PRIMARY KEY,
    delivery_id    UUID         NOT NULL,
    attempt_number INTEGER      NOT NULL,
    outcome        VARCHAR(24)  NOT NULL,
    failure_reason VARCHAR(500),
    notes          VARCHAR(500),
    attempted_at   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT delivery_attempts_delivery_fk
        FOREIGN KEY (delivery_id) REFERENCES deliveries (id) ON DELETE CASCADE,
    CONSTRAINT delivery_attempts_number_positive CHECK (attempt_number > 0)
);

CREATE INDEX delivery_attempts_delivery ON delivery_attempts (delivery_id, attempted_at);

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

-- Fulfilment audit trail. Records the administrative acts around production and delivery — stock
-- corrections, coverage changes, recipe edits, assignment and dispatch — so an operator can answer
-- "who did this". Only identifiers are stored.
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
