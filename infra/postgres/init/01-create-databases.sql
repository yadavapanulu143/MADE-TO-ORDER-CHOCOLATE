-- ---------------------------------------------------------------------------
-- Per-service logical databases and roles.
--
-- Each service gets its own database AND its own role. Postgres cannot join across
-- databases, so a service cannot read another service's tables even by accident, and
-- a leaked credential exposes exactly one service's data. This is the isolation
-- decision from the architecture plan; sharing one database with separate schemas
-- would not give the same guarantee.
--
-- The script runs only on first initialisation of an empty data volume.
-- ---------------------------------------------------------------------------

\set ON_ERROR_STOP on

CREATE ROLE choco_identity  LOGIN PASSWORD 'choco_identity_pw';
CREATE ROLE choco_catalog   LOGIN PASSWORD 'choco_catalog_pw';
CREATE ROLE choco_order     LOGIN PASSWORD 'choco_order_pw';
CREATE ROLE choco_payment   LOGIN PASSWORD 'choco_payment_pw';
CREATE ROLE choco_fulfilment LOGIN PASSWORD 'choco_fulfilment_pw';
CREATE ROLE choco_notification LOGIN PASSWORD 'choco_notification_pw';
CREATE ROLE choco_reporting LOGIN PASSWORD 'choco_reporting_pw';

CREATE DATABASE choco_identity   OWNER choco_identity;
CREATE DATABASE choco_catalog    OWNER choco_catalog;
CREATE DATABASE choco_order      OWNER choco_order;
CREATE DATABASE choco_payment    OWNER choco_payment;
CREATE DATABASE choco_fulfilment OWNER choco_fulfilment;
CREATE DATABASE choco_notification OWNER choco_notification;
CREATE DATABASE choco_reporting  OWNER choco_reporting;

-- Flyway needs to create its schema-history table, so each role owns its database.
GRANT ALL PRIVILEGES ON DATABASE choco_identity   TO choco_identity;
GRANT ALL PRIVILEGES ON DATABASE choco_catalog    TO choco_catalog;
GRANT ALL PRIVILEGES ON DATABASE choco_order      TO choco_order;
GRANT ALL PRIVILEGES ON DATABASE choco_payment    TO choco_payment;
GRANT ALL PRIVILEGES ON DATABASE choco_fulfilment TO choco_fulfilment;
GRANT ALL PRIVILEGES ON DATABASE choco_notification TO choco_notification;
GRANT ALL PRIVILEGES ON DATABASE choco_reporting  TO choco_reporting;

-- PostgreSQL grants CONNECT on a new database to PUBLIC by default, which would let any role
-- reach every service database. Revoke that, then grant CONNECT back only to the owning role,
-- so a leaked credential exposes exactly one service's data.
REVOKE CONNECT ON DATABASE choco_identity     FROM PUBLIC;
REVOKE CONNECT ON DATABASE choco_catalog      FROM PUBLIC;
REVOKE CONNECT ON DATABASE choco_order        FROM PUBLIC;
REVOKE CONNECT ON DATABASE choco_payment      FROM PUBLIC;
REVOKE CONNECT ON DATABASE choco_fulfilment   FROM PUBLIC;
REVOKE CONNECT ON DATABASE choco_notification FROM PUBLIC;
REVOKE CONNECT ON DATABASE choco_reporting    FROM PUBLIC;

GRANT CONNECT ON DATABASE choco_identity     TO choco_identity;
GRANT CONNECT ON DATABASE choco_catalog      TO choco_catalog;
GRANT CONNECT ON DATABASE choco_order        TO choco_order;
GRANT CONNECT ON DATABASE choco_payment      TO choco_payment;
GRANT CONNECT ON DATABASE choco_fulfilment   TO choco_fulfilment;
GRANT CONNECT ON DATABASE choco_notification TO choco_notification;
GRANT CONNECT ON DATABASE choco_reporting    TO choco_reporting;
