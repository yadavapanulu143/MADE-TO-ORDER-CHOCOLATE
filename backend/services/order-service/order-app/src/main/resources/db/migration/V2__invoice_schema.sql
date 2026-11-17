-- Invoice schema.
--
-- An invoice is a financial record, not a workflow: once issued it is never edited, so there is no
-- updated_at and no version column. A correction is a credit note (a future document), never a
-- rewrite of the original — the row is the evidence of what the customer was charged.
--
-- Everything the document shows is copied from the order at issuance and never joined back, so a
-- product renamed, repriced or withdrawn later cannot change a past invoice.
--
-- Every monetary column is a BIGINT count of paise. No column is a floating-point type.

CREATE TABLE invoices (
    id                          UUID         PRIMARY KEY,
    -- Human-readable reference printed on the document. Unique on its own and never reused.
    invoice_number              VARCHAR(40)  NOT NULL,
    -- One invoice per order, enforced by a unique index below. This column is the link back.
    order_id                    UUID         NOT NULL,
    order_number                VARCHAR(40)  NOT NULL,
    customer_id                 UUID         NOT NULL,

    -- The customer's details, frozen at issuance. Copied rather than referenced so the document can
    -- be rendered even if the account is later changed or closed.
    customer_name               VARCHAR(150) NOT NULL,
    customer_email              VARCHAR(254) NOT NULL,
    customer_mobile             VARCHAR(20)  NOT NULL,

    -- The billing address, frozen at issuance. For this business it is the delivery address; it is a
    -- separate column set so a distinct billing address is a field addition, not a redesign.
    billing_recipient_name      VARCHAR(150) NOT NULL,
    billing_mobile              VARCHAR(20)  NOT NULL,
    billing_line1               VARCHAR(200) NOT NULL,
    billing_line2               VARCHAR(200),
    billing_city                VARCHAR(100) NOT NULL,
    billing_state               VARCHAR(100) NOT NULL,
    billing_pincode             VARCHAR(6)   NOT NULL,

    items_total_minor_units     BIGINT       NOT NULL,
    delivery_charge_minor_units BIGINT       NOT NULL,
    total_minor_units           BIGINT       NOT NULL,
    currency                    VARCHAR(3)   NOT NULL DEFAULT 'INR',

    issued_at                   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT invoices_number_not_blank CHECK (length(trim(invoice_number)) > 0),
    CONSTRAINT invoices_totals_non_negative CHECK (
        items_total_minor_units >= 0 AND delivery_charge_minor_units >= 0 AND total_minor_units >= 0),
    -- The stored total must equal its parts, so an invoice total can never drift from what was billed.
    CONSTRAINT invoices_total_is_sum CHECK (
        total_minor_units = items_total_minor_units + delivery_charge_minor_units),
    CONSTRAINT invoices_currency_inr CHECK (currency = 'INR')
);

CREATE UNIQUE INDEX invoices_number_unique ON invoices (invoice_number);
-- One invoice per order: a second issuance is a duplicate, refused by the database as well as the code.
CREATE UNIQUE INDEX invoices_order_unique ON invoices (order_id);
-- The customer's invoice list: newest first, scoped to the owner.
CREATE INDEX invoices_customer ON invoices (customer_id, issued_at DESC);

CREATE TABLE invoice_lines (
    id                     UUID         PRIMARY KEY,
    invoice_id             UUID         NOT NULL,
    product_id             UUID         NOT NULL,
    variant_id             UUID         NOT NULL,
    -- Snapshot of the order at issuance. Never re-read.
    product_name           VARCHAR(150) NOT NULL,
    variant_label          VARCHAR(80)  NOT NULL,
    sku                    VARCHAR(64),
    unit_price_minor_units BIGINT       NOT NULL,
    currency               VARCHAR(3)   NOT NULL DEFAULT 'INR',
    quantity               INTEGER      NOT NULL,
    display_order          INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT invoice_lines_invoice_fk
        FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT invoice_lines_name_not_blank CHECK (length(trim(product_name)) > 0),
    CONSTRAINT invoice_lines_price_positive CHECK (unit_price_minor_units > 0),
    CONSTRAINT invoice_lines_quantity_positive CHECK (quantity > 0),
    CONSTRAINT invoice_lines_variant_unique UNIQUE (invoice_id, variant_id)
);

CREATE INDEX invoice_lines_invoice ON invoice_lines (invoice_id);

-- Source of the human-readable invoice number. A sequence rather than a count of rows, so two
-- concurrent issuances cannot be handed the same number and deleting an invoice cannot free its number.
CREATE SEQUENCE invoice_number_seq START WITH 1000 INCREMENT BY 1;
