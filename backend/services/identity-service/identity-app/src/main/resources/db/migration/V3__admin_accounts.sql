-- Administrator accounts and their sessions.
--
-- Administrators are a separate table from customers on purpose. They have no mobile number and no
-- OTP verification, they are provisioned rather than self-registered, and keeping them apart means a
-- bug in customer self-service cannot reach an administrator row. There is no role column on
-- customers and no customer can be promoted: the only way to hold admin authority is to exist here.
--
-- No column can hold a plaintext password or a usable token value. Both are stored as hashes.

CREATE TABLE admin_accounts (
    id                    UUID         PRIMARY KEY,
    full_name             VARCHAR(120) NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    -- Argon2id or BCrypt-15 encoded hash, including algorithm prefix and parameters.
    password_hash         VARCHAR(255) NOT NULL,
    role                  VARCHAR(16)  NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    -- Optimistic locking: two concurrent edits must not silently overwrite each other.
    version               BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT admin_accounts_email_not_blank CHECK (length(trim(email)) > 0),
    CONSTRAINT admin_accounts_role_valid CHECK (role IN ('ADMIN', 'SUPER_ADMIN')),
    CONSTRAINT admin_accounts_failed_attempts_non_negative CHECK (failed_login_attempts >= 0)
);

-- Case-insensitive uniqueness, matching the customer table. The value is normalised to lower case by
-- EmailAddress, but the index is on lower(email) so any other insert path still cannot collide.
CREATE UNIQUE INDEX admin_accounts_email_unique ON admin_accounts (lower(email));

-- Refresh tokens for administrator sessions, mirroring the customer table. Kept separate so revoking
-- a customer's sessions can never touch an administrator's, and vice versa.
CREATE TABLE admin_refresh_tokens (
    id          UUID         PRIMARY KEY,
    admin_id    UUID         NOT NULL,
    -- SHA-256 of an opaque random value. A database dump yields no usable session.
    token_hash  VARCHAR(128) NOT NULL,
    issued_at   TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,

    CONSTRAINT admin_refresh_tokens_admin_fk
        FOREIGN KEY (admin_id) REFERENCES admin_accounts (id) ON DELETE CASCADE,
    CONSTRAINT admin_refresh_tokens_hash_unique UNIQUE (token_hash),
    CONSTRAINT admin_refresh_tokens_expiry_after_issue CHECK (expires_at > issued_at)
);

CREATE INDEX admin_refresh_tokens_active_by_admin
    ON admin_refresh_tokens (admin_id)
    WHERE revoked_at IS NULL;
