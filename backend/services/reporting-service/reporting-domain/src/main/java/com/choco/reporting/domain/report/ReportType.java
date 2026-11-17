package com.choco.reporting.domain.report;

/**
 * The reports the seller can run.
 *
 * <p>Each one answers a question the business actually asks: what did we sell, what came in, what is
 * still in the kitchen, what is on the road, what did we refund, who signed up. Keeping them a closed
 * set rather than an open query string means a report cannot be widened into "give me every column of
 * every table", which is the shape of an accidental data leak.
 */
public enum ReportType {

    /** Sales and revenue over a window, grouped by day. */
    SALES,
    /** Every order placed in a window, with its current state. */
    ORDERS,
    /** Payments captured, failed and pending over a window. */
    PAYMENTS,
    /** Production jobs and how far each has advanced. */
    PRODUCTION,
    /** Deliveries and their outcomes. */
    DELIVERY,
    /** Refunds requested and processed. */
    REFUNDS,
    /** Customer registrations over a window. */
    CUSTOMERS
}
