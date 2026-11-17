package com.choco.reporting.domain.model;

import java.time.Instant;

/**
 * The reporting view of a delivery, projected from the fulfilment service's events.
 *
 * <p>Mirrors the fulfilment service's delivery track. A delivery is seller-managed, so the only facts
 * here are the ones the events carried — there is no courier tracking number or position, because the
 * business has none and the reporting service must not invent one.
 */
public record DeliveryProjection(
        String deliveryId,
        String orderId,
        String orderNumber,
        String customerId,
        String status,
        String deliveryPersonName,
        String failureReason,
        int attemptNumber,
        Instant updatedAt) {

    /**
     * The fulfilment service's own delivery statuses, copied verbatim.
     *
     * <p>A failed attempt is {@code ATTEMPT_FAILED}, not {@code FAILED}; a report comparing against a
     * name the events never carry would count no failures at all.
     */
    public static final String PENDING = "PENDING";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY";
    public static final String DELIVERED = "DELIVERED";
    public static final String ATTEMPT_FAILED = "ATTEMPT_FAILED";
    public static final String RESCHEDULED = "RESCHEDULED";
    public static final String CANCELLED = "CANCELLED";
    public static final String RETURNED = "RETURNED";

    /** Whether the delivery is still with the seller: not yet delivered, failed, cancelled or returned. */
    public boolean isInFlight() {
        return PENDING.equals(status) || ASSIGNED.equals(status)
                || OUT_FOR_DELIVERY.equals(status) || RESCHEDULED.equals(status);
    }
}
