package com.choco.order.domain.order;

/**
 * Made-to-order preparation progress.
 *
 * <p>Chocolate is made after a paid order arrives, so this tracks a physical process the seller
 * performs rather than stock leaving a shelf. The order service holds this as a projection: the
 * fulfilment service owns the production records, and its events move this value. Until that service
 * exists the seller drives it through the admin API, which is the same command the event will invoke
 * later.
 */
public enum PreparationStatus {

    /** Paid, waiting for the seller to begin. */
    AWAITING_PREPARATION,

    /** Being prepared. */
    STARTED,

    /** Preparation finished; the product is complete. */
    COMPLETED,

    /** Being packed for dispatch. */
    PACKAGING,

    /** Packed and waiting for the seller to hand it to delivery. */
    READY_FOR_DISPATCH,

    /** Deliberately paused by the seller, e.g. an ingredient shortage. */
    ON_HOLD
}
