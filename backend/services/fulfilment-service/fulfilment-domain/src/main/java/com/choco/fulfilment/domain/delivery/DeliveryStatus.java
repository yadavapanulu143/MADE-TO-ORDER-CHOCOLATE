package com.choco.fulfilment.domain.delivery;

/**
 * Seller-managed delivery progress for one order.
 *
 * <p>Delivery is performed by the seller, not a courier integration, so there is no provider tracking
 * number here and none is fabricated. The seller records each step and the customer sees the same
 * values. {@link #canTransitionTo} encodes the edges: a delivery can fail and be retried, but it
 * cannot be delivered before it went out, and a cancelled or delivered delivery is finished.
 *
 * <p>{@link #PENDING} is the entry state, reached once production has marked the order ready for
 * dispatch. Production's own "preparation completed" and "ready for dispatch" are production states,
 * not delivery ones, so they are not repeated here.
 */
public enum DeliveryStatus {

    /** Ready to hand over; nobody has been assigned yet. */
    PENDING,

    /** The seller has decided who will deliver it. */
    ASSIGNED,

    /** On its way to the customer. */
    OUT_FOR_DELIVERY,

    /** Handed over and accepted. Terminal. */
    DELIVERED,

    /** A delivery attempt did not succeed. */
    ATTEMPT_FAILED,

    /** A new attempt has been scheduled after a failure. */
    RESCHEDULED,

    /** Delivery was called off. Terminal. */
    CANCELLED,

    /** The item came back, e.g. the customer refused it. Terminal. */
    RETURNED;

    public boolean canTransitionTo(DeliveryStatus next) {
        if (next == null || next == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> next == ASSIGNED || next == CANCELLED;
            case ASSIGNED -> next == OUT_FOR_DELIVERY || next == PENDING || next == CANCELLED;
            case OUT_FOR_DELIVERY -> next == DELIVERED || next == ATTEMPT_FAILED;
            case ATTEMPT_FAILED -> next == RESCHEDULED || next == RETURNED;
            case RESCHEDULED -> next == OUT_FOR_DELIVERY || next == RETURNED;
            case DELIVERED, CANCELLED, RETURNED -> false;
        };
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == RETURNED;
    }
}
