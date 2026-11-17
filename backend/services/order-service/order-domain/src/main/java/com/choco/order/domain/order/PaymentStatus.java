package com.choco.order.domain.order;

/**
 * Where an order stands with respect to money.
 *
 * <p>Kept separate from the fulfilment {@link OrderStatus} because the two advance on different
 * clocks and are driven by different actors. Payment moves when the payment provider confirms or
 * refuses a charge; fulfilment moves when the seller prepares and delivers. Collapsing them into one
 * enum would force a single ordering on two independent processes and make "paid but not yet started"
 * unrepresentable.
 *
 * <p>This service never decides that a payment succeeded. It records what the payment service has
 * already verified, so the value here is a projection of a fact proven elsewhere — which is why a
 * frontend redirect can never move it to {@link #PAID}.
 */
public enum PaymentStatus {

    /** Order created; no verified payment yet. Preparation must not begin. */
    PENDING,

    /** A payment attempt is in flight and the provider's answer has not been verified. */
    VERIFICATION_PENDING,

    /** The payment service verified a successful capture. The only state that unlocks preparation. */
    PAID,

    /** The provider reported the attempt failed, or verification failed. */
    FAILED,

    /** A refund has been requested and is being reviewed by the seller. */
    REFUND_UNDER_REVIEW,

    /** The payment service confirmed a refund was processed. Terminal. */
    REFUNDED
}
