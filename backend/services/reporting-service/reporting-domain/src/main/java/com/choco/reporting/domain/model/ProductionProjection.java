package com.choco.reporting.domain.model;

import java.time.Instant;

/**
 * The reporting view of a production job, projected from the fulfilment service's events.
 *
 * <p>The status mirrors the fulfilment service's own production track. The reporting service does not
 * own the state machine; it copies the value each event carried, so the two cannot disagree.
 */
public record ProductionProjection(
        String jobId,
        String orderId,
        String orderNumber,
        String customerId,
        String variantLabel,
        int quantity,
        String status,
        Instant startedAt,
        Instant completedAt) {

    /**
     * The fulfilment service's own production statuses, copied verbatim.
     *
     * <p>These are the values the events actually carry. A projection that invented its own names
     * would report every job as "in progress" and count no completion, because no row would ever equal
     * the constant the report compares against.
     */
    public static final String RECEIVED = "RECEIVED";
    public static final String AWAITING_PREPARATION = "AWAITING_PREPARATION";
    public static final String PREPARATION_STARTED = "PREPARATION_STARTED";
    public static final String PREPARATION_COMPLETED = "PREPARATION_COMPLETED";
    public static final String PACKAGING = "PACKAGING";
    public static final String READY_FOR_DISPATCH = "READY_FOR_DISPATCH";
    public static final String ON_HOLD = "ON_HOLD";
    public static final String CANCELLED = "CANCELLED";

    /** Whether the job has left the kitchen: prepared, packed or handed to delivery. */
    public boolean isMade() {
        return PREPARATION_COMPLETED.equals(status) || PACKAGING.equals(status)
                || READY_FOR_DISPATCH.equals(status);
    }
}
