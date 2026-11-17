package com.choco.reporting.domain.model;

import java.time.Instant;

/**
 * The reporting view of an order, projected from the order service's events.
 *
 * <p>This is not the order. It is a local, derived copy of the facts the seller reports on, fed only
 * by events, so the reporting service never reads the order service's database and can answer a
 * question while that service is briefly down. Every field is a fact some event carried; nothing here
 * is computed from a live call.
 *
 * <p>An order starts as {@code PLACED} (from {@code order.placed}) and advances as later events
 * arrive: {@code CONFIRMED}, {@code CANCELLED}. The total is the frozen checkout figure, so a later
 * price change cannot move a past sale.
 */
public record OrderProjection(
        String orderId,
        String orderNumber,
        String customerId,
        String status,
        long totalMinorUnits,
        long deliveryChargeMinorUnits,
        String currency,
        Instant placedAt,
        Instant confirmedAt,
        Instant cancelledAt,
        String cancellationReason) {

    public static final String PLACED = "PLACED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String CANCELLED = "CANCELLED";
    public static final String REFUND_REQUESTED = "REFUND_REQUESTED";

    /** Whether this order's revenue should be counted: a cancelled order is not a sale. */
    public boolean countsAsRevenue() {
        return CONFIRMED.equals(status);
    }
}
