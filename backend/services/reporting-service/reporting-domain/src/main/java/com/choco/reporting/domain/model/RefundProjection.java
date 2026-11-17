package com.choco.reporting.domain.model;

import java.time.Instant;

/**
 * The reporting view of a refund, projected from the order and payment services' events.
 *
 * <p>A refund is requested (order service) and then processed (payment service). The projection keeps
 * both, so the seller's report distinguishes a request the customer is waiting on from money actually
 * returned — the two are not the same, and treating a request as a completed refund would overstate
 * what was paid back.
 */
public record RefundProjection(
        String refundId,
        String orderId,
        String orderNumber,
        String customerId,
        long amountMinorUnits,
        String currency,
        String status,
        String reason,
        boolean fullRefund,
        Instant requestedAt,
        Instant processedAt) {

    public static final String REQUESTED = "REQUESTED";
    public static final String PROCESSED = "PROCESSED";
}
