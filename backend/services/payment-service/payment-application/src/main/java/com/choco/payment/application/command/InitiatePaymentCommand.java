package com.choco.payment.application.command;

import java.util.UUID;

/**
 * A request to start a payment for an order.
 *
 * <p>Raised by the order service, which is the authority on what the order costs. The amount is
 * passed in rather than re-derived here, so the figure the customer agreed to pay at checkout is the
 * figure charged, and a later catalogue change cannot move it.
 *
 * <p>Carries an idempotency key for the same reason checkout does: creating a provider order is
 * exactly the call a client retries after a timeout, and it must not produce two provider orders.
 */
public record InitiatePaymentCommand(
        UUID orderId,
        UUID customerId,
        long amountMinorUnits,
        String currency,
        String receipt,
        String idempotencyKey) {
}
