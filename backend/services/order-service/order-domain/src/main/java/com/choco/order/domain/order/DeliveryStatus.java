package com.choco.order.domain.order;

/**
 * Seller-managed delivery progress.
 *
 * <p>Delivery is performed by the seller, not a courier integration, so there is no provider
 * tracking number here and none is fabricated. The seller records each step, and the customer sees
 * the same values the seller entered.
 */
public enum DeliveryStatus {

    /** Not yet handed to delivery. */
    PENDING,

    /** The seller has decided who will deliver it. */
    ASSIGNED,

    /** On its way to the customer. */
    OUT_FOR_DELIVERY,

    /** Handed over and accepted. Terminal for delivery. */
    DELIVERED,

    /** A delivery attempt did not succeed. */
    ATTEMPT_FAILED,

    /** A new attempt has been scheduled after a failure. */
    RESCHEDULED,

    /** Delivery was called off. */
    CANCELLED,

    /** The item came back, e.g. the customer refused it. */
    RETURNED
}
