package com.choco.fulfilment.domain.production;

/**
 * Where a made-to-order job stands in the kitchen.
 *
 * <p>The chocolate does not exist when the order is paid for, so this is not a stock level going
 * down — it is a physical process the seller performs. {@link #canTransitionTo} encodes the edges the
 * seller may actually take.
 *
 * <p>{@link #READY_FOR_DISPATCH} is deliberately not the last value: delivery is a separate track
 * owned by the delivery part of this service, and a job that is ready to hand over is finished from
 * production's point of view even though the order is not yet with the customer.
 */
public enum ProductionStatus {

    /** The paid order has been handed to the kitchen; nobody has looked at it yet. */
    RECEIVED,

    /** The seller has acknowledged it and it is queued for preparation. */
    AWAITING_PREPARATION,

    /** Chocolate is being made. */
    PREPARATION_STARTED,

    /** Preparation finished; the product is complete and has not been packed. */
    PREPARATION_COMPLETED,

    /** Being packed for dispatch. */
    PACKAGING,

    /** Packed and waiting for the seller to hand it to delivery. Production is done. */
    READY_FOR_DISPATCH,

    /** Deliberately paused, e.g. an ingredient ran out. Not a failure; work resumes. */
    ON_HOLD,

    /** Abandoned before the chocolate was made. */
    CANCELLED;

    /**
     * Whether the seller may move from this status to {@code next}.
     *
     * <p>Written as an explicit table rather than an ordinal comparison, because the process has
     * branches an ordinal cannot express: a hold can happen from two places, a resume only returns to
     * preparation, and cancellation is allowed from anywhere before the product exists but nowhere
     * after. An ordinal comparison would also silently allow a jump from {@code RECEIVED} straight to
     * {@code READY_FOR_DISPATCH}, which would let the seller skip making the chocolate.
     */
    public boolean canTransitionTo(ProductionStatus next) {
        if (next == null || next == this) {
            return false;
        }
        return switch (this) {
            case RECEIVED -> next == AWAITING_PREPARATION || next == CANCELLED;
            case AWAITING_PREPARATION -> next == PREPARATION_STARTED || next == ON_HOLD
                    || next == CANCELLED;
            case PREPARATION_STARTED -> next == PREPARATION_COMPLETED || next == ON_HOLD
                    || next == CANCELLED;
            case ON_HOLD -> next == PREPARATION_STARTED || next == CANCELLED;
            case PREPARATION_COMPLETED -> next == PACKAGING;
            case PACKAGING -> next == READY_FOR_DISPATCH;
            case READY_FOR_DISPATCH, CANCELLED -> false;
        };
    }

    /** Whether the job is finished and no further transition is possible. */
    public boolean isTerminal() {
        return this == READY_FOR_DISPATCH || this == CANCELLED;
    }

    /**
     * Whether the units on this job still occupy the kitchen.
     *
     * <p>Used by the capacity check. Once a job is ready for dispatch the chocolate is packed and out
     * of the way, so it no longer counts against what the seller can prepare at once.
     */
    public boolean occupiesCapacity() {
        return switch (this) {
            case RECEIVED, AWAITING_PREPARATION, PREPARATION_STARTED, ON_HOLD, PREPARATION_COMPLETED,
                 PACKAGING -> true;
            case READY_FOR_DISPATCH, CANCELLED -> false;
        };
    }
}
