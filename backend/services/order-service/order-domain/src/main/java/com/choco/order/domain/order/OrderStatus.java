package com.choco.order.domain.order;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The order's overall lifecycle, and the single place that decides which moves are legal.
 *
 * <p>The value is a projection that folds the three independent tracks — payment, preparation and
 * delivery — into the one status a customer or the seller reads at a glance. The tracks themselves
 * are held separately on the {@link Order}, because "paid" and "preparing" advance on different
 * clocks; this enum names the points at which they line up.
 *
 * <p>Transitions are declared as data rather than as a chain of {@code if}s spread through the
 * aggregate. A scattered rule is how an order ends up delivered before it was paid, or cancelled
 * after dispatch; here the whole graph is one readable table, and
 * {@link Order#transitionTo(OrderStatus, String, String, java.time.Instant)} is the only way the
 * status can move.
 */
public enum OrderStatus {

    /** Created, but no verified payment yet. Preparation must not begin. */
    PAYMENT_PENDING,

    /** A payment attempt is in flight and the provider's answer has not been verified. */
    PAYMENT_VERIFICATION_PENDING,

    /** The provider reported the attempt failed, or verification failed. */
    PAYMENT_FAILED,

    /** The payment service verified a successful capture. The only state that unlocks preparation. */
    CONFIRMED,

    /** The seller is making the chocolate. */
    PREPARATION_STARTED,

    /** The seller paused preparation, e.g. an ingredient is unavailable. */
    PREPARATION_ON_HOLD,

    /** Preparation is finished. */
    PREPARATION_COMPLETED,

    /** Being packed for dispatch. */
    PACKAGING,

    /** Packed and waiting for the seller to hand it to delivery. */
    READY_FOR_DISPATCH,

    /** On its way to the customer. */
    OUT_FOR_DELIVERY,

    /** Handed over and accepted. */
    DELIVERED,

    /** A delivery attempt did not succeed. */
    DELIVERY_ATTEMPT_FAILED,

    /** A new attempt has been scheduled after a failure. */
    DELIVERY_RESCHEDULED,

    /** The item came back, e.g. the customer refused it. */
    DELIVERY_RETURNED,

    /** Cancelled before it was delivered. */
    CANCELLED,

    /** A refund has been requested and is under review by the seller. */
    REFUND_UNDER_REVIEW,

    /** A refund was processed. Terminal. */
    REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.ofEntries(
            Map.entry(PAYMENT_PENDING, EnumSet.of(PAYMENT_VERIFICATION_PENDING, PAYMENT_FAILED,
                    CONFIRMED, CANCELLED)),
            Map.entry(PAYMENT_VERIFICATION_PENDING, EnumSet.of(CONFIRMED, PAYMENT_FAILED,
                    PAYMENT_PENDING)),
            // A failed payment may be retried, which returns the order to PAYMENT_PENDING.
            Map.entry(PAYMENT_FAILED, EnumSet.of(PAYMENT_PENDING, CANCELLED)),
            Map.entry(CONFIRMED, EnumSet.of(PREPARATION_STARTED, PREPARATION_ON_HOLD, CANCELLED,
                    REFUND_UNDER_REVIEW)),
            Map.entry(PREPARATION_ON_HOLD, EnumSet.of(PREPARATION_STARTED, CANCELLED,
                    REFUND_UNDER_REVIEW)),
            Map.entry(PREPARATION_STARTED, EnumSet.of(PREPARATION_COMPLETED, PREPARATION_ON_HOLD, CANCELLED,
                    REFUND_UNDER_REVIEW)),
            Map.entry(PREPARATION_COMPLETED, EnumSet.of(PACKAGING, REFUND_UNDER_REVIEW)),
            Map.entry(PACKAGING, EnumSet.of(READY_FOR_DISPATCH, REFUND_UNDER_REVIEW)),
            Map.entry(READY_FOR_DISPATCH, EnumSet.of(OUT_FOR_DELIVERY, CANCELLED, REFUND_UNDER_REVIEW)),
            Map.entry(OUT_FOR_DELIVERY, EnumSet.of(DELIVERED, DELIVERY_ATTEMPT_FAILED)),
            Map.entry(DELIVERY_ATTEMPT_FAILED, EnumSet.of(DELIVERY_RESCHEDULED, DELIVERY_RETURNED)),
            Map.entry(DELIVERY_RESCHEDULED, EnumSet.of(OUT_FOR_DELIVERY, DELIVERY_RETURNED)),
            Map.entry(DELIVERY_RETURNED, EnumSet.of(REFUND_UNDER_REVIEW, REFUNDED)),
            Map.entry(DELIVERED, EnumSet.of(REFUND_UNDER_REVIEW)),
            Map.entry(REFUND_UNDER_REVIEW, EnumSet.of(REFUNDED, DELIVERED)),
            Map.entry(CANCELLED, EnumSet.noneOf(OrderStatus.class)),
            Map.entry(REFUNDED, EnumSet.noneOf(OrderStatus.class)));

    /** Statuses from which no further transition is possible. */
    private static final Set<OrderStatus> TERMINAL = Set.of(CANCELLED, REFUNDED);

    public boolean canTransitionTo(OrderStatus next) {
        return next != null && ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    public Set<OrderStatus> allowedNext() {
        return Collections.unmodifiableSet(ALLOWED.getOrDefault(this, Set.of()));
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** True once the seller may begin making the chocolate: the order is paid and not called off. */
    public boolean isPaid() {
        return this == CONFIRMED || this == PREPARATION_STARTED || this == PREPARATION_ON_HOLD
                || this == PREPARATION_COMPLETED || this == PACKAGING || this == READY_FOR_DISPATCH
                || this == OUT_FOR_DELIVERY || this == DELIVERED || this == DELIVERY_ATTEMPT_FAILED
                || this == DELIVERY_RESCHEDULED || this == DELIVERY_RETURNED || this == REFUND_UNDER_REVIEW
                || this == REFUNDED;
    }
}
