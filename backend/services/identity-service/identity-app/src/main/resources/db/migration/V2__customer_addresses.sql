-- Delivery addresses belonging to a customer.
--
-- Owned by the identity service, like the rest of the customer's data. The order service will hold
-- a snapshot of the chosen address on the order itself, so an order keeps the address it was placed
-- with even if the customer later edits or deletes the saved one.

CREATE TABLE customer_addresses (
    id                    UUID         PRIMARY KEY,
    customer_id           UUID         NOT NULL,
    -- The recipient is not necessarily the account holder: chocolates are often sent as gifts, so
    -- the name and mobile are stored per address rather than read from the customer.
    recipient_name        VARCHAR(120) NOT NULL,
    mobile                VARCHAR(10)  NOT NULL,
    line1                 VARCHAR(200) NOT NULL,
    line2                 VARCHAR(200),
    city                  VARCHAR(200) NOT NULL,
    state                 VARCHAR(200) NOT NULL,
    pincode               VARCHAR(6)   NOT NULL,
    delivery_instructions VARCHAR(500),
    is_default            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,

    CONSTRAINT customer_addresses_customer_fk
        FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT customer_addresses_mobile_format CHECK (mobile ~ '^[6-9][0-9]{9}$'),
    CONSTRAINT customer_addresses_pincode_format CHECK (pincode ~ '^[1-9][0-9]{5}$'),
    CONSTRAINT customer_addresses_recipient_not_blank CHECK (length(trim(recipient_name)) > 0),
    CONSTRAINT customer_addresses_line1_not_blank CHECK (length(trim(line1)) > 0),
    CONSTRAINT customer_addresses_city_not_blank CHECK (length(trim(city)) > 0),
    CONSTRAINT customer_addresses_state_not_blank CHECK (length(trim(state)) > 0)
);

-- Listing a customer's addresses, and counting them against the per-customer cap.
CREATE INDEX customer_addresses_by_customer
    ON customer_addresses (customer_id, is_default DESC, updated_at DESC);

-- At most one default address per customer. A partial unique index is the only constraint that can
-- express this: a plain unique index on (customer_id, is_default) would forbid a second non-default
-- address, since every such row has is_default = false. Enforcing it in the database rather than only
-- in the service means two concurrent "make this the default" requests cannot both succeed and leave
-- the customer with two defaults.
CREATE UNIQUE INDEX customer_addresses_one_default
    ON customer_addresses (customer_id)
    WHERE is_default;
