package com.choco.reporting.domain.export;

/**
 * The datasets the seller can export.
 *
 * <p>A closed set, each with a declared column list, so an export cannot be widened by a caller. The
 * customer and invoice exports are the sensitive ones and are permission-gated; the others are
 * operational.
 */
public enum ExportType {

    /** Customers and their verification state. Contact details are masked. */
    CUSTOMERS,
    /** Orders with their state and totals. */
    ORDERS,
    /** Payments received, failed and pending. */
    PAYMENTS,
    /** Issued invoices with their billed totals. */
    INVOICES
}
