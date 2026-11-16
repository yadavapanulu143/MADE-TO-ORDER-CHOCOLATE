-- Identity service schema.
--
-- Owned exclusively by the identity service. No other service may read or write these tables;
-- cross-service data is exchanged over the API or through events, never by joining across databases.
--
-- Passwords and tokens are stored only as hashes. No column in this schema can hold a plaintext
-- password, a card detail, a CVV, a PIN or an OTP in the clear.

CREATE TABLE customers (
    id                   UUID         PRIMARY KEY,
    full_name            VARCHAR(120) NOT NULL,
    email                VARCHAR(254) NOT NULL,
    mobile               VARCHAR(10)  NOT NULL,
    -- Argon2id or BCrypt-15 encoded hash, including algorithm prefix and parameters.
    password_hash        VARCHAR(255) NOT NULL,
    registered_at        TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    failed_login_attempts INTEGER     NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Optimistic locking: two concurrent profile updates must not silently overwrite each other.
    version              BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT customers_email_not_blank CHECK (length(trim(email)) > 0),
    CONSTRAINT customers_mobile_format CHECK (mobile ~ '^[6-9][0-9]{9}$'),
    CONSTRAINT customers_failed_attempts_non_negative CHECK (failed_login_attempts >= 0)
);

-- Case-insensitive uniqueness. The value is already normalised to lower case by EmailAddress, but
-- the index is declared on lower(email) so a row inserted by any other path still cannot collide.
CREATE UNIQUE INDEX customers_email_unique ON customers (lower(email));
CREATE UNIQUE INDEX customers_mobile_unique ON customers (mobile);

-- Verified channels. A row per verified channel rather than boolean columns, so a third channel
-- needs no schema change.
CREATE TABLE customer_verified_channels (
    customer_id UUID        NOT NULL,
    channel     VARCHAR(16) NOT NULL,

    PRIMARY KEY (customer_id, channel),
    CONSTRAINT customer_verified_channels_customer_fk
        FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT customer_verified_channels_channel_valid CHECK (channel IN ('EMAIL', 'MOBILE'))
);

CREATE TABLE otp_challenges (
    id           UUID         PRIMARY KEY,
    customer_id  UUID         NOT NULL,
    channel      VARCHAR(16)  NOT NULL,
    destination  VARCHAR(254) NOT NULL,
    -- Digest of the code, never the code itself.
    code_hash    VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    resend_count INTEGER      NOT NULL DEFAULT 0,
    consumed_at  TIMESTAMPTZ,

    CONSTRAINT otp_challenges_customer_fk
        FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT otp_challenges_channel_valid CHECK (channel IN ('EMAIL', 'MOBILE')),
    CONSTRAINT otp_challenges_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT otp_challenges_expiry_after_creation CHECK (expires_at > created_at)
);

-- Supports the "latest active challenge for this customer and channel" lookup, which runs on every
-- verification and resend.
CREATE INDEX otp_challenges_lookup
    ON otp_challenges (customer_id, channel, created_at DESC)
    WHERE consumed_at IS NULL;

CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY,
    customer_id UUID         NOT NULL,
    -- SHA-256 of an opaque random value. A database dump yields no usable session.
    token_hash  VARCHAR(128) NOT NULL,
    issued_at   TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,

    CONSTRAINT refresh_tokens_customer_fk
        FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT refresh_tokens_hash_unique UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_expiry_after_issue CHECK (expires_at > issued_at)
);

-- Revoking every session for a customer is a bulk update, so the index leads with customer_id and
-- is partial on the rows that can still be revoked.
CREATE INDEX refresh_tokens_active_by_customer
    ON refresh_tokens (customer_id)
    WHERE revoked_at IS NULL;

CREATE TABLE password_reset_tokens (
    id          UUID         PRIMARY KEY,
    customer_id UUID         NOT NULL,
    token_hash  VARCHAR(128) NOT NULL,
    issued_at   TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ,

    CONSTRAINT password_reset_tokens_customer_fk
        FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT password_reset_tokens_hash_unique UNIQUE (token_hash),
    CONSTRAINT password_reset_tokens_expiry_after_issue CHECK (expires_at > issued_at)
);

CREATE INDEX password_reset_tokens_active_by_customer
    ON password_reset_tokens (customer_id)
    WHERE consumed_at IS NULL;

-- Security audit trail. Written in its own transaction so a deliberately rolled-back action (a
-- rejected login) still leaves a record. Holds identifiers only, never credential material.
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
CREATE INDEX audit_log_actor ON audit_log (actor_type, actor_id, occurred_at DESC);
CREATE INDEX audit_log_action ON audit_log (action, occurred_at DESC);

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

-- The relay scans unpublished rows oldest first; a partial index keeps that scan off the published
-- history, which grows without bound.
CREATE INDEX idx_outbox_unpublished
    ON outbox_event (created_at)
    WHERE published_at IS NULL;
