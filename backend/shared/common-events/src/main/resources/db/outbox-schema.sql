-- Transactional outbox, same shape in every producing service's own database.
-- Each service's Flyway migration includes this file so the table exists locally.
CREATE TABLE IF NOT EXISTS outbox_event (
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

-- The relay's hot path is "oldest unpublished first". A partial index stays small because the
-- published rows, which are the vast majority over time, are excluded entirely.
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON outbox_event (created_at)
    WHERE published_at IS NULL;
